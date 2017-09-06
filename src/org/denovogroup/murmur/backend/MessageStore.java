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

import org.denovogroup.murmur.objects.MobyMessage;
import org.whispersystems.libsignal.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for Moby messages that uses StorageBase underneath. If
 * instantiated as such, automatically encrypts and decrypts data before storing
 * in Android.
 */
public class MessageStore extends SQLiteOpenHelper {

    public static final String NEW_MESSAGE = "new message";

    private static String storeVersion;

    private static MessageStore instance;
    private static final String TAG = "MessageStore";

    //messages properties
    private static final int MAX_MESSAGE_SIZE = 1000;

    private static final String DATABASE_NAME = "MessageStore.db";
    private static final int DATABASE_VERSION = 2;

    private static final String TABLE = "Messages";


    public static final String COL_TIMESTAMP   = "timestamp";
    public static final String COL_SOURCE      = "source";
    public static final String COL_DESTINATION = "destination";
    public static final String COL_PAYLOAD     = "payload";

    private Context mContext;

    /** Get the current instance of MessageStore and create one if necessary.
     * Implemented as a singleton */
    public synchronized static MessageStore getInstance(Context context){
        if(instance == null && context != null){
            instance = new MessageStore(context);
        }
        return instance;
    }

    /** Get the current instance of MessageStore or null if none already created */
    public synchronized static MessageStore getInstance(){
        return getInstance(null);
    }

    /** private constructor for forcing singleton pattern for MessageStore */
    private MessageStore(Context context){
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        instance = this;
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + COL_TIMESTAMP   + " INTEGER NOT NULL,"
                + COL_SOURCE      + " VARCHAR(255) NOT NULL,"
                + COL_DESTINATION + " VARCHAR(255) NOT NULL,"
                + COL_PAYLOAD     + " VARCHAR(" + MAX_MESSAGE_SIZE + ") NOT NULL"
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

    /** convert cursor data returned from SQL queries into Message objects that can be returned to
     * query supplier. This implementation does not close the supplied cursor when done
     * @param cursor Cursor data returned from SQLite database
     * @return list of Message items contained by the cursor or an empty list if cursor was empty
     */
    private List<MobyMessage> convertToMessages(Cursor cursor){

        List<MobyMessage> messages = new ArrayList<>();
        cursor.moveToFirst();

        int timestampColumn   = cursor.getColumnIndex(COL_TIMESTAMP);
        int sourceColumn      = cursor.getColumnIndex(COL_SOURCE);
        int destinationColumn = cursor.getColumnIndex(COL_DESTINATION);
        int payloadColumn     = cursor.getColumnIndex(COL_PAYLOAD);
        if (cursor.getCount() > 0) {
            while (!cursor.isAfterLast()){
                messages.add(new MobyMessage(
                        cursor.getLong(timestampColumn),
                        cursor.getString(sourceColumn),
                        cursor.getString(destinationColumn),
                        cursor.getString(payloadColumn)
                ));
                cursor.moveToNext();
            }
        }

        return messages;
    }

    /** Return a single message matching supplied text or null if no match can be found.
     * @param getDeleted whether or not results should include deleted items
     * @return A Message item based on database item matching conditions or null
     */
    private Cursor getMessageCursor(String message, boolean getDeleted) throws IllegalArgumentException{
        if(message == null || message.isEmpty()) throw new IllegalArgumentException("Message cannot be empty or null ["+message+"].");

        SQLiteDatabase db = getWritableDatabase();
        if(db != null){
            String query = "SELECT * FROM " + TABLE + " WHERE " + COL_PAYLOAD + "='" + Utils.makeTextSafeForSQL(message) + "'"
                    +" LIMIT 1;";
            return db.rawQuery(query, null);
        }
        return null;
    }

    /** Return a single message matching supplied text or null if no match can be found.
     * @param getDeleted whether or not results should include deleted items
     * @return A Message item based on database item matching conditions or null
     */
    private MobyMessage getMessage(String message, boolean getDeleted) throws IllegalArgumentException{
        if(message == null || message.isEmpty()) throw new IllegalArgumentException("Message cannot be empty or null ["+message+"].");

        SQLiteDatabase db = getWritableDatabase();
        if(db != null){
            Cursor cursor = getMessageCursor(message, getDeleted);
            if(cursor.getCount() > 0){
                MobyMessage result = convertToMessages(cursor).get(0);
                cursor.close();
                return result;
            }
            cursor.close();
        }
        return null;
    }

    /** return if message exists in database and is not in removed state **/
    public boolean contains(String message){
        return getMessage(message, false) != null;
    }

    /** return if message exists in database, even if is in removed state **/
    public boolean containsOrRemoved(String message){
        return getMessage(message, true) != null;
    }

    /**
     * Adds the given message with the given priority.
     *
     * @param timestamp The timestamp of the
     * @return Returns true if the message was added. If message already exists, update its values
     */
    public boolean addMessage(long timestamp, String source, String destination, String payload) {
        SQLiteDatabase db = getWritableDatabase();
        if(db != null && timestamp != -1L && source != null && destination != null && payload != null){
            ContentValues contentValues = new ContentValues();
            contentValues.put(MobyMessage.TIMESTAMP, timestamp);
            contentValues.put(MobyMessage.SOURCE, source);
            contentValues.put(MobyMessage.DESTINATION, destination);
            contentValues.put(MobyMessage.PAYLOAD, payload);
            db.insert(TABLE, null, contentValues);
            return true;
        }
        Log.d(TAG, "Message not added to store.");
        return false;
    }

    public boolean addMessage(MobyMessage message) {
        return addMessage(message.getTimestamp(),
                message.getSource(),
                message.getDestination(),
                message.getPayload());
    }


    /** Return the current version of the store */
    public String getStoreVersion(){
        if(storeVersion == null) updateStoreVersion();

        return  storeVersion;
    }

    /** Randomize a version code for the store and set it*/
    public void updateStoreVersion(){
        storeVersion = UUID.randomUUID().toString();
    }

    public void purgeStore(){
        SQLiteDatabase db = getWritableDatabase();
        if (db != null) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE);
            onCreate(db);
        }
    }

    public void logMessages() {
        SQLiteDatabase db = getReadableDatabase();
        if(db != null) {
            Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE, null);
            cursor.moveToFirst();

            int timestampColumn   = cursor.getColumnIndex(COL_TIMESTAMP);
            int sourceColumn      = cursor.getColumnIndex(COL_SOURCE);
            int destinationColumn = cursor.getColumnIndex(COL_DESTINATION);
            int payloadColumn     = cursor.getColumnIndex(COL_PAYLOAD);

            while (!cursor.isAfterLast()) {
                Log.d(TAG,
                        "timestamp: "    + cursor.getLong(timestampColumn) +
                        " source: "      + cursor.getString(sourceColumn) +
                        " destination: " + cursor.getString(destinationColumn) +
                        " payload: "     + cursor.getString(payloadColumn));
                cursor.moveToNext();
            }

        }
    }

    public List<MobyMessage> getMessagesForExchange(int sharedContacts){
        SQLiteDatabase db = getReadableDatabase();
        if(db != null){
            return convertToMessages(db.rawQuery("SELECT * FROM " + TABLE, null));
        }
        return new ArrayList<MobyMessage>();
    }
}