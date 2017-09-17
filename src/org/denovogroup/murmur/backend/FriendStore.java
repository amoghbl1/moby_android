/*
* Copyright (c) 2016, De Novo Group
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
*
* 1. Redistributions of source code must retain the above copyright notice,
* this list of conditions and the following disclaimer.
*
* 2. Redistributions in binary form must reproduce the above copyright notice,
* this list of conditions and the following disclaimer in the documentation
* and/or other materials provided with the distribution.
*
* 3. Neither the name of the copyright holder nor the names of its
* contributors may be used to endorse or promote products derived from this
* software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRES S OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/
package org.denovogroup.murmur.backend;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import org.spongycastle.crypto.AsymmetricCipherKeyPair;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Storage for friends that uses StorageBase underneath.
 */
public class FriendStore extends SQLiteOpenHelper{
  /** A handle for the underlying store */
  private StorageBase store;

  /** The internal key used in the underlying store for Murmur friend data. */
  private static final String FRIENDS_STORE_KEY = "MurmurFriend-";

  /** The internal keys used in the underlying store for the public device ID (keys). */
  private static final String DEVICE_PUBLIC_ID_KEY = "PublicDeviceIDKey";

  /** The internal keys used in the underlying store for the private device ID (keys). */
  private static final String DEVICE_PRIVATE_ID_KEY = "PrivateDeviceIDKey";

  /** URI scheme for Murmur friending. */
  public static final String QR_FRIENDING_SCHEME = "moby://";

  /** Tag for Android log messages. */
  private static final String TAG = "FriendStore";


    private static FriendStore instance;

    private static final String DATABASE_NAME = "FriendStore.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "Friends";
    private static final String COL_ROWID = "_id";
    public static final String COL_MOBY_ID = "moby_id";
    public static final String COL_MOBY_SHARED_SECRET = "moby_shared_secret";
    public static final String COL_ADDED_VIA = "added_via";
    public static final String COL_NUMBER = "number";

    public static final int ADDED_VIA_QR = 0;
    public static final int ADDED_VIA_PHONE = 1;
    public static final int ADDED_VIA_HERD_HANDSHAKE = 2;

    //readable true/false operators since SQLite does not support boolean values
    public static final int TRUE = 1;
    public static final int FALSE = 0;

  /**
   * Encode a byte array as a base64 string.
   * This method should be used to convert from byte[]s accepted by Crypto.java
   * and Strings stored in FriendStore.
   *
   * @param bytes The bytes to be converted.
   * @return A base64 encoded string of the bytes given, or null if bytes was null.
   */
  public static String bytesToBase64(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    return Base64.encodeToString(bytes, Base64.NO_WRAP);
  }

  /**
   * Encode a byte array as a base64 string.
   * This method should be used to convert from Strings stored by FriendStore
   * to byte[]s accepted by Crypto.java.
   *
   * @param base64 The string to be converted.
   * @return A byte[] of the bytes represented in base64 by the given string, or
   * null if the string was null or wasn't well formed base64.
   */
  public static byte[] base64ToBytes(String base64) throws IllegalArgumentException {
    if (base64 == null) {
      return null;
    }

    try {
      return Base64.decode(base64, Base64.NO_WRAP);
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Returning null on attempt to decode badly formed base64 string: " + base64);
      return null;
    }
  }

  /**
   * Return all friends stored as byte[], decoded from their base64 stored representations.
   *
   * This doesn't take arguments but throws an underlying IllegalArgumentException.
   * Which is awkward, but it needs to be some kind of exception, and the underlying
   * exception has the most information. The exception might be thrown by
   * base64ToBytes().
   *
   * @return The set of all stored friend IDs, as byte[].
   */
  public ArrayList<byte[]> getAllValidFriendsBytes() throws IllegalArgumentException {
    Set<String> base64s = getAllValidFriends();
    ArrayList<byte[]> byteArrays = new ArrayList<byte[]>();
    for (String base64 : base64s) {
      byte[] bytes = base64ToBytes(base64);
      byteArrays.add(bytes);
    }
    return byteArrays;
  }

  /**
   * If the device has not previously generated and persisted its device ID (aka
   * public/private keypair for PSI), generates and stores said ID.
   *
   * If the ID is already stored, this harmlessly does nothing.
   */
  private void generateAndStoreDeviceID(Context context, int encryption) {
      if(store == null) store = new StorageBase(context, encryption);
    String privateDeviceID = store.get(DEVICE_PRIVATE_ID_KEY);
    String publicDeviceID = store.get(DEVICE_PUBLIC_ID_KEY);
    if (privateDeviceID == null || publicDeviceID == null) {
      // This would be very strange, if only half the ID was stored.
      if (privateDeviceID != publicDeviceID) {
        if (privateDeviceID == null) {
          Log.e(TAG, "Only one of private and public ID are stored! Public is stored, private is null.");
        } else {
          Log.e(TAG, "Only one of private and public ID are stored! Private is stored, public is null.");
        }
      }

      AsymmetricCipherKeyPair keypair = Crypto.generateUserID();
      privateDeviceID = bytesToBase64(Crypto.generatePrivateID(keypair));
      publicDeviceID = bytesToBase64(Crypto.generatePublicID(keypair));

        store.put(DEVICE_PRIVATE_ID_KEY, privateDeviceID);
      store.put(DEVICE_PUBLIC_ID_KEY, publicDeviceID);
    }
  }

  /**
   * Return the device's public device ID as a base64 encoded string, ready to be
   * shared with another device (e.g. as part of a QR code).
   *
   * @return A base64 encoded string representing the local device's public ID,
   * or null if something went wrong.
   */
  public String getPublicDeviceIDString(Context context, int encryption) {
    generateAndStoreDeviceID(context, encryption);
    return store.get(DEVICE_PUBLIC_ID_KEY);
  }

  /**
   * Extract the public ID from the contents of a QR code.
   *
   * TODO(lerner): Validate the ID more than cursorily.
   *
   * @param qrContents The full contents of a QR code (e.g. murmur://<publicid>)
   * @return A byte[] representing the public ID stored in that QR code, or null
   * if the code was malformed/null/didn't contain an ID.
   */
  public static byte[] getPublicIDFromQR(String qrContents) {
    if (qrContents == null) {
      return null;
    } else if (!qrContents.startsWith(QR_FRIENDING_SCHEME)) {
      return null;
    } else {
      // TODO(lerner): Perform more aggressive validation of the ID.
      // For now we're willing to return any thing after murmur://, which might
      // be various bad things, like...
      //   --nothing
      //   --tons of data
      //   --not the right size/format/number of bytes/parity to form a key
      // But I don't know what the spec is for these keys, so I can't verify more now.
      return base64ToBytes(qrContents.substring(QR_FRIENDING_SCHEME.length()));
    }

  }

    /** Get the current instance of FriendStore and create one if necessary.
     * Implemented as a singleton */
    public synchronized static FriendStore getInstance(Context context){
        if(instance == null && context != null){
            instance = new FriendStore(context);
        }
        return instance;
    }

    private FriendStore(Context context){
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        instance = this;
    }

    /** Create the table for storing friends, only called for first run of the database */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COL_ROWID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COL_ADDED_VIA + " INT NOT NULL,"
                + COL_MOBY_ID + " TEXT NOT NULL,"
                + COL_MOBY_SHARED_SECRET + " DATA BLOB,"
                + COL_NUMBER + " TEXT"
                + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        /*recreate table on upgrade, this should be better implemented once final data base structure
          is reached*/
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

    /**
     * Adds the given friend.
     *
     * @param mobyID the public key the friend is identified by in exchanges
     * @param via how the friend's key was retrieved (either ADDED_VIA_PHONE or ADDED_VIA_QR)
     * @param number optional real phone number to display when user is in edit mode
     * @param sharedSecret the shared secret that is used as a moby tag key for HmacSHA256
     *
     * @return Returns true if the friend was stored, false if the friend was already
     * stored.
     */
    public boolean addFriend(String mobyID, int via, String number, byte[] sharedSecret){
        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return false;

        String currentMobyID = getFriendMobyIDfromNumber(number);

        if(currentMobyID != null) {
            if(currentMobyID.equals("") && !mobyID.equals("")) {
                Log.d(TAG, "Someone installed Moby :D");
            } else if(!currentMobyID.equals("") && !mobyID.equals("")){
                // TODO amoghbl1: figure out what to do when overwritten with new vals.
                Log.d(TAG, number + " has generated a new Moby key! Notify user?");

            } else if(mobyID.equals("")) {
                Log.d(TAG, "Overwriting a valid MobyID! ");
                return false;
            }
        }

        // TODO amoghbl1: Maybe we should make this an update if exists op?
        deleteFriendWithNumber(number);

        ContentValues values = new ContentValues();
        values.put(COL_MOBY_ID, mobyID);
        values.put(COL_ADDED_VIA, via);
        values.put(COL_NUMBER, Utils.makeTextSafeForSQL(number.replaceAll("\\s","")));
        values.put(COL_MOBY_SHARED_SECRET, sharedSecret);

        long id = db.insert(TABLE, null, values);
        Log.d(TAG, number + " Added to store, " + id + " mobyID: " + mobyID + " mobyTagKey: " + sharedSecret);
        return true;
    }


    public String generateMobyTag(String mobileNumber, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] macKey = getFriendMobyTagKeyfromNumber(mobileNumber);
            SecretKeySpec secretKeySpec = new SecretKeySpec(macKey, "HmacSHA256");
            mac.init(secretKeySpec);
            return Base64.encodeToString(mac.doFinal(base64ToBytes(payload)), Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.d(TAG, e.getMessage());
        }

        return null;
    }

    public String verifyMobyTag(String tag, String payload) {
        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return null;

        Cursor c = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_MOBY_ID + " != '';", null);
        int mobyKeyColumn = c.getColumnIndex(COL_MOBY_SHARED_SECRET);
        int numberColumn  = c.getColumnIndex(COL_NUMBER);

        c.moveToFirst();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec;
            String ourTag;
            while(!c.isAfterLast()) {
                secretKeySpec = new SecretKeySpec(c.getBlob(mobyKeyColumn), "HmacSHA256");
                mac.init(secretKeySpec);
                ourTag = Base64.encodeToString(mac.doFinal(base64ToBytes(payload)), Base64.NO_WRAP);
                Log.d(TAG, "Our: " + ourTag);
                Log.d(TAG, "Thr: " + tag);
                if(ourTag.equals(tag))
                    return c.getString(numberColumn);
                c.moveToNext();
            }
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.d(TAG, e.getMessage());
        }
        return null;
    }

    public boolean hasFriend(String number){
        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return false;

        number = number.replaceAll("\\s", "");
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_NUMBER + " = '" + number + "';", null);

        if(cursor.getCount() == 0) return false;

        cursor.moveToFirst();
        int mobyIDColumn = cursor.getColumnIndex(COL_MOBY_ID);
        String key = cursor.getString(mobyIDColumn);

        Log.d(TAG, number + " hasKey: " + key);

        if(key.equals("")) return false;

        return true;
    }

    public boolean sentHandshake(String number) {
        SQLiteDatabase db = getReadableDatabase();
        if(db == null)
            return false;

        number = number.replaceAll("\\s", "");
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_NUMBER + " = '" + number + "';", null);

        if(cursor.getCount() == 0)
            return false;

        return true;
    }


    public boolean deleteFriendWithNumber(String number){
        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return false;

        number = number.replaceAll("\\s","");
        db.execSQL("DELETE FROM " + TABLE + " WHERE " + COL_NUMBER+ " = '" + number + "';");
        return true;
    }

    /**
     * Delete the given friend from the friend store, if it exists.
     *
     * @param mobyID The friend public ID to delete.
     *
     * @return True if the friend existed and was deleted, false otherwise.
     */
    public boolean deleteFriend(String mobyID){
        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return false;

        if(getFriendWithMobyID(mobyID) == null){
            Log.d(TAG, "Friend was not in the store");
            return false;
        }

        db.execSQL("DELETE FROM " + TABLE + " WHERE " + COL_MOBY_ID + " = '" + mobyID + "';");
        return true;
    }

    /**
     * Get a list of all friends stored on this device.
     *
     * @return A set of friends ids.
     */
    public Set<String> getAllValidFriends(){
        Set<String> friends = new HashSet<>();

        SQLiteDatabase db = getWritableDatabase();
        if(db == null) return friends;

        Cursor cursor = db.rawQuery("SELECT " + COL_MOBY_ID + " FROM " + TABLE + ";", null);

        cursor.moveToFirst();

        int keyColIndex = cursor.getColumnIndex(COL_MOBY_ID);
        String key = "";

        while (!cursor.isAfterLast()){
            key = cursor.getString(keyColIndex);
            if(key != null && !key.equals(""))
                friends.add(key);
            cursor.moveToNext();
        }
        return friends;
    }

    private Cursor getFriendWithMobyID(String key) {
        SQLiteDatabase db = getWritableDatabase();
        if(db == null)
            return null;

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_MOBY_ID + " = '" + key + "';", null);

        if(cursor.getCount() == 0) return null;

        return cursor;
    }

    private byte[] getFriendMobyTagKeyfromNumber(String number) {
        SQLiteDatabase db = getReadableDatabase();
        if(db == null)
            return null;

        Cursor c = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_NUMBER + " = '" + number +"';", null);
        c.moveToFirst();

        if(c.getCount() == 0)
            return null;
        int keyColumn = c.getColumnIndex(COL_MOBY_SHARED_SECRET);

        return c.getBlob(keyColumn);
    }

    public String getFriendMobyIDfromNumber(String number) {
        SQLiteDatabase db = getReadableDatabase();
        if(db == null)
            return null;

        Cursor c = db.rawQuery("SELECT * FROM " + TABLE + " WHERE " + COL_NUMBER + " = '" + number + "';", null);

        c.moveToFirst();

        int keyColumn = c.getColumnIndex(COL_MOBY_ID);

        if(c.getCount() == 0)
            return null;

        return c.getString(keyColumn);
    }

    public void purgeStore(){
        SQLiteDatabase db = getWritableDatabase();
        if (db != null) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }

}
