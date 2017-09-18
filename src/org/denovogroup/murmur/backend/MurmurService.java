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

import android.app.ActivityManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.denovogroup.murmur.objects.MobyMessage;
import org.thoughtcrime.securesms.jobs.PushContentReceiveJob;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;


/**
 * Core service of the Rangzen app. Started at startup, remains alive
 * indefinitely to perform the background tasks of Rangzen.
 */
public class MurmurService extends Service {
    /**
     * The running instance of MurmurService.
     */
    protected static MurmurService sRangzenServiceInstance;

    /**
     * For app-local broadcast and broadcast reception.
     */
    private LocalBroadcastManager mLocalBroadcastManager;

    /**
     * Executes the background thread periodically.
     */
    private ScheduledExecutorService mScheduleTaskExecutor;

    /**
     * Cancellable scheduling of backgroundTasks.
     */
    private ScheduledFuture mBackgroundExecution;

    /**
     * Set WifiDirectName task.
     */
    private ScheduledFuture mWifiDirectExecutor;

    /**
     * Cancellable scheduling of cleanup.
     */
    private ScheduledFuture mCleanupExecution;

    /**
     * Handle to app's PeerManager.
     */
    private PeerManager mPeerManager;

    /**
     * The time at which this instance of the service was started.
     */
    private Date mStartTime;

    /**
     * Random number generator for picking random peers.
     */
    private Random mRandom = new Random();

    /**
     * The number of times that backgroundTasks() has been called.
     */
    private int mBackgroundTaskRunCount = 0;

    /**
     * Handle to Rangzen key-value storage provider.
     */
    private StorageBase mStorageBase;

    /**
     * Storage for friends.
     */
    private FriendStore mFriendStore;

    /**
     * Wifi Direct Speaker used for Wifi Direct name based RSVP.
     */
    private WifiDirectSpeaker mWifiDirectSpeaker;

    /**
     * The Peer address we're attempting a connection to over BT or null.
     */
    private String connecting = null;

    /**
     * The BluetoothSpeaker for the app.
     */
    private static BluetoothSpeaker mBluetoothSpeaker;

    /**
     * Message store.
     */
    private MessageStore mMessageStore;
    /**
     * Ongoing exchange.
     */
    private Exchange mExchange;

    private ServiceWatchDog mServiceWatchDog;

    private ExchangeHistoryTracker mExchangeHistoryTracker;

    /**
     * Socket over which the ongoing exchange is taking place.
     */
    private BluetoothSocket mSocket;

    private BroadcastReceiver errorHandler = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_ONBT.equals(action)) {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (mBluetoothAdapter != null) mBluetoothAdapter.enable();
            } else if (ACTION_ONWIFI.equals(action)) {
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null) wifiManager.setWifiEnabled(true);
            } else {
                SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE, MODE_PRIVATE);
                sharedPreferences.edit().putBoolean(AppConstants.IS_APP_ENABLED, false).commit();
                MurmurService.this.stopSelf();
            }
        }
    };
    public static final String SERVICE_ERROR_HANDLER_FILTER = "org.denovogroup.error";
    public static final String ACTION_TURNOFF = SERVICE_ERROR_HANDLER_FILTER + ".turn_off_service";
    public static final String ACTION_ONWIFI = SERVICE_ERROR_HANDLER_FILTER + ".turn_on_widi";
    public static final String ACTION_ONBT = SERVICE_ERROR_HANDLER_FILTER + ".turn_on_BT";

    /**
     * When announcing our address over Wifi Direct name, prefix this string to our MAC.
     */
    public final static String RSVP_PREFIX = "MOBY-";

    /**
     * Key into storage to store the last time we had an exchange.
     */
    private static final String LAST_EXCHANGE_TIME_KEY =
            "org.denovogroup.murmur.LAST_EXCHANGE_TIME_KEY";

    /**
     * Time to wait between exchanges, in milliseconds.
     */
    public static int TIME_BETWEEN_EXCHANGES_MILLIS;

    /**
     * Android Log Tag.
     */
    private final static String TAG = "MurmurService";

    private static final int RENAME_DELAY = 1000;
    private static final String DUMMY_MAC_ADDRESS = "02:00:00:00:00:00";
    public static final int BACKOFF_FOR_ATTEMPT_MILLIS = 10 * 1000;
    public static final int BACKOFF_MAX = BACKOFF_FOR_ATTEMPT_MILLIS * (int) Math.pow(2, 5);

    private static final boolean USE_MINIMAL_LOGGING = false;

    public static final boolean USE_BACKOFF = true;

    public static final boolean CONSOLIDATE_ERRORS = true;

    public static int direction = 0;
    public static String remoteAddress;

    private final static boolean clean = false;
    private static long lastLogWrite;

    /**
     * Called whenever the service is requested to start. If the service is
     * already running, this does /not/ create a new instance of the service.
     * Rather, onStartCommand is called again on the existing instance.
     *
     * @param intent  The intent passed to startService to start this service.
     * @param flags   Flags about the request to start the service.
     * @param startid A unique integer representing this request to start.
     * @see android.app.Service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        Log.i(TAG, "MurmurService onStartCommand.");

        // Returning START_STICKY causes Android to leave the service running
        // even when the foreground activity is closed.
        return START_STICKY;
    }

    /**
     * this is cheap way of getting service reference without using binder pattern, the
     * returned reference must never be saved, this call is for debug purposes only
     *
     * @return
     */
    public static MurmurService getInstance() {
        return sRangzenServiceInstance;
    }

    /**
     * Called the first time the service is started.
     *
     * @see android.app.Service
     */
    @Override
    public void onCreate() {

        mServiceWatchDog = ServiceWatchDog.getInstance();
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        sRangzenServiceInstance = this;
        mPeerManager = PeerManager.getInstance();
        mFriendStore = FriendStore.getInstance(this);
        mMessageStore = MessageStore.getInstance(this);
        mWifiDirectSpeaker = WifiDirectSpeaker.getInstance();
        mStorageBase = new StorageBase(this, StorageBase.ENCRYPTION_DEFAULT);
        mExchangeHistoryTracker = ExchangeHistoryTracker.getInstance(this);

        if (errorHandler != null) {
            IntentFilter filter = new IntentFilter(SERVICE_ERROR_HANDLER_FILTER);
            filter.addAction(ACTION_TURNOFF);
            filter.addAction(ACTION_ONBT);
            filter.addAction(ACTION_ONWIFI);
            registerReceiver(errorHandler, filter);
        }

        if (clean) {
            mFriendStore.purgeStore();
            mMessageStore.purgeStore();
        }

        mServiceWatchDog.init(this);

        mBluetoothSpeaker = new BluetoothSpeaker(this, mFriendStore, mMessageStore);
        mPeerManager.setBluetoothSpeaker(mBluetoothSpeaker);

        mStartTime = new Date();


        mWifiDirectSpeaker.init(this,
                mPeerManager,
                mBluetoothSpeaker,
                new WifiDirectFrameworkGetter());

        saveLogcatToFile();
        lastLogWrite = System.currentTimeMillis();
        Log.i(TAG, "MurmurService onCreate.");

        // Schedule the background task thread to run occasionally.
        mScheduleTaskExecutor = Executors.newScheduledThreadPool(1);
        // TODO amoghbl1: Make this executor some kind of a build up back off thing, constant time is a bad idea.
        mBackgroundExecution = mScheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    backgroundTasks();
                } catch (Exception e) {
                    Log.e(TAG, "Unhandled exception during backgroundTasks:" + e.getMessage());
                }
            }
        }, 0, 5, TimeUnit.SECONDS);

        mCleanupExecution = mScheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    cleanupMessageStore();
                    if (System.currentTimeMillis() - lastLogWrite > 3600000) {
                        lastLogWrite = System.currentTimeMillis();
                        saveLogcatToFile();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception during cleanup message store scheduled task" + e.getMessage());
                }
            }
        }, 0, 1, TimeUnit.MINUTES);

        mWifiDirectExecutor = mScheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                setWifiDirectFriendlyName();
                mWifiDirectSpeaker.setmSeekingDesired(true);
            }
        }, 0, 2, TimeUnit.MINUTES);

        TIME_BETWEEN_EXCHANGES_MILLIS = SecurityManager.getCurrentProfile(this).getCooldown() * 1000;
        Log.i(TAG, "MurmurService created.");

        //TODO this is a test to see if service is really being killed, setting startForeground
        // prevent service from being killed by the system
        /*NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentTitle("Murmur is running in background")
                .setContentText("please do not dismiss this message")
                .setContentInfo("without it Murmur might not work")
                .setWhen(System.currentTimeMillis()).setAutoCancel(false);

        Notification notice = builder.build();

        startForeground(R.id.transparentHolder, notice);*/
    }

    public void saveLogcatToFile() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                String uploadURL = "https://f-droid.dedis.ch/upload.php";
                HttpsURLConnection conn = null;
                DataOutputStream dos = null;
                String lineEnd = "\r\n";
                String twoHyphens = "--";
                String boundary = "*****";
                int bytesRead, bytesAvailable, bufferSize;
                byte[] buffer;
                int maxBufferSize = 1 * 1024 * 1024;
                int serverResponseCode = -1;

                String id = mBluetoothSpeaker.getAddress();
                String fileName = id + "_moby_" + System.currentTimeMillis() + ".txt";
                File outputFile = new File(Environment.getExternalStorageDirectory(), fileName);
                try {
                    Process process = Runtime.getRuntime().exec("logcat -df " + outputFile.getAbsolutePath());
                    process.waitFor();

                    // open a URL connection to the Servlet
                    FileInputStream fileInputStream = new FileInputStream(outputFile);
                    URL url = new URL(uploadURL);

                    // Open a HTTP  connection to  the URL
                    conn = (HttpsURLConnection) url.openConnection();
                    conn.setDoInput(true); // Allow Inputs
                    conn.setDoOutput(true); // Allow Outputs
                    conn.setUseCaches(false); // Don't use a Cached Copy
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Connection", "Keep-Alive");
                    conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                    conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                    conn.setRequestProperty("uploaded_file", fileName);

                    dos = new DataOutputStream(conn.getOutputStream());

                    dos.writeBytes(twoHyphens + boundary + lineEnd);
                    dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                            + fileName + "\"" + lineEnd);

                    dos.writeBytes(lineEnd);

                    // create a buffer of  maximum size
                    bytesAvailable = fileInputStream.available();

                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    buffer = new byte[bufferSize];

                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    while (bytesRead > 0) {

                        dos.write(buffer, 0, bufferSize);
                        bytesAvailable = fileInputStream.available();
                        bufferSize = Math.min(bytesAvailable, maxBufferSize);
                        bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                    }
                    dos.writeBytes(lineEnd);
                    dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                    serverResponseCode = conn.getResponseCode();
                    String serverResponseMessage = conn.getResponseMessage();

                    Log.i(TAG, "HTTP Response is : "
                            + serverResponseMessage + ": " + serverResponseCode);

                    if (serverResponseCode == 200) {
                        Log.d(TAG, "Upload successful!!");
                    }
                    fileInputStream.close();
                    dos.flush();
                    dos.close();

                } catch (MalformedURLException e) {
                    Log.d(TAG, e.getMessage());
                } catch (Exception e) {
                    Log.d(TAG, "Error trying to upload log!!");
                    e.printStackTrace();
                }
                return null;
            }
        }.execute();
    }


    /**
     * Called when the service is destroyed.
     */
    public void onDestroy() {
        mServiceWatchDog.notifyServiceDestroy();
        if (errorHandler != null) {
            try {
                unregisterReceiver(errorHandler);
            } catch (Exception e) {
            }
        }
        Log.d(TAG, "MurmurService onDestroy");
        mBackgroundExecution.cancel(true);
        SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE, Context.MODE_PRIVATE);
        if (sharedPreferences.contains(AppConstants.WIFI_NAME) && mWifiDirectSpeaker != null) {
            Log.d(TAG, "Restoring wifi name");
            mWifiDirectSpeaker.setWifiDirectUserFriendlyName(sharedPreferences.getString(AppConstants.WIFI_NAME, ""));
        }

        mPeerManager.forgetAllPeers();
        mWifiDirectSpeaker.dismissNoWifiNotification();
        mBluetoothSpeaker.unregisterReceiver(this);
        mBluetoothSpeaker.dismissNoBluetoothNotification();
        Log.d(TAG, "MurmurService destroyed");
        saveLogcatToFile();
    }


    /**
     * Check whether we can connect, according to our policies.
     * Currently, checks that we've waited TIME_BETWEEN_EXCHANGES_MILLIS
     * milliseconds since the last exchange and that we're not already connecting.
     *
     * @return Whether or not we're ready to connect to a peer.
     */
    private boolean readyToConnect() {
        long now = System.currentTimeMillis();
        long lastExchangeMillis = mStorageBase.getLong(LAST_EXCHANGE_TIME_KEY, -1);

        boolean timeSinceLastOK;
        if (lastExchangeMillis == -1) {
            timeSinceLastOK = true;
        } else if (now - lastExchangeMillis < TIME_BETWEEN_EXCHANGES_MILLIS) {
            timeSinceLastOK = false;
        } else {
            timeSinceLastOK = true;
        }
        if (!USE_MINIMAL_LOGGING) {
            Log.i(TAG, "Ready to connect? " + (timeSinceLastOK && (getConnecting() == null)));
            Log.i(TAG, "Connecting: " + getConnecting());
            Log.i(TAG, "timeSinceLastOK: " + timeSinceLastOK);
        }
        return timeSinceLastOK && (getConnecting() == null);
    }

    /**
     * Set the time of the last exchange, kept in storage, to the current time.
     */
    private void setLastExchangeTime() {
        if (!USE_MINIMAL_LOGGING) Log.i(TAG, "Setting last exchange time");
        long now = System.currentTimeMillis();
        mStorageBase.putLong(LAST_EXCHANGE_TIME_KEY, now);
    }

    /**
     * Method called periodically on a background thread to perform Murmur's
     * background tasks.
     */
    public void backgroundTasks() {
        if (!USE_MINIMAL_LOGGING) Log.i(TAG, "Background Tasks Started");

        /*
        if(isAppInForeground()){
            cancelUnreadMessagesNotification();
        }
        */

        // TODO(lerner): Why not just use mPeerManager?
        mPeerManager.tasks();
        // peerManager.tasks();
        if (!mBluetoothSpeaker.tasks()) return;
        if (!mWifiDirectSpeaker.tasks()) return;
        mMessageStore.tasks();

        List<Peer> peers = mPeerManager.getPeers();
        // TODO(lerner): Don't just connect all willy-nilly every time we have
        // an opportunity. Have some kind of policy for when to connect.
        if (peers.size() > 0 && readyToConnect()) {
            Log.i(TAG, String.format("Can connect with %d peers", peers.size()));
            if (SecurityManager.getCurrentProfile(this).isRandomExchange()) {
                Log.i(TAG, "Current security profile state that we should pick one random peer to interact with");
                Peer selectedPeer = pickBestPeer(peers);//peers.get(mRandom.nextInt(peers.size()));
                peers.clear();
                peers.add(selectedPeer);
                ExchangeHistoryTracker.ExchangeHistoryItem historyItem
                        = mExchangeHistoryTracker.getHistoryItem(selectedPeer.address);
                if (historyItem != null) {
                    mExchangeHistoryTracker.updatePickHistory(selectedPeer.address);
                } else {
                    mExchangeHistoryTracker.updateHistory(this, selectedPeer.address);
                }
            }
            Log.i(TAG, String.format("Checking %d peers", peers.size()));
            for (Peer peer : peers) {
                Log.d(TAG, "Checking peer:" + peer);
                try {
                    if (mPeerManager.thisDeviceSpeaksTo(peer)) {
                        Log.d(TAG, "This device is in charge of starting conversation");
                        // Connect to the peer, starting an exchange with the peer once
                        // connected. We only do this if thisDeviceSpeaksTo(peer), which
                        // checks whether we initiate conversations with this peer or
                        // it initiates with us (a function of our respective addresses).


                        //optimize connection using history tracker
                        if (USE_BACKOFF) {
                            ExchangeHistoryTracker.ExchangeHistoryItem historyItem = mExchangeHistoryTracker.getHistoryItem(peer.address);
                            boolean hasHistory = historyItem != null;
                            boolean storeVersionChanged = false;
                            boolean waitedMuch = false;

                            if (hasHistory) {
                                storeVersionChanged = !historyItem.storeVersion.equals(mMessageStore.getStoreVersion());
                                waitedMuch = historyItem.lastExchangeTime + Math.min(
                                        Math.pow(2, historyItem.attempts) * BACKOFF_FOR_ATTEMPT_MILLIS, BACKOFF_MAX) < System.currentTimeMillis();
                            }

                            if (!hasHistory || storeVersionChanged || waitedMuch) {
                                Log.d(TAG, "Can connect with peer: " + peer);
                                connectTo(peer);
                            } else {
                                Log.d(TAG, "Backoff from peer: " + peer +
                                        " [previously interacted:" + hasHistory + ", store ready:" + storeVersionChanged + " ,backoff timeout:" + waitedMuch + "]");
                            }
                        } else {
                            connectTo(peer);
                        }
                    } else {
                        Log.d(TAG, "Other device is in charge of starting conversation");
                    }
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "No such algorithm for hashing in thisDeviceSpeaksTo!? ", e);
                    return;
                } catch (UnsupportedEncodingException e) {
                    Log.e(TAG, "Unsupported encoding exception in thisDeviceSpeaksTo!?", e);
                    return;
                }
            }
        } else {
            Log.i(TAG, String.format("Not connecting (%d peers, ready to connect is %s)", peers.size(), readyToConnect()));
        }
        mBackgroundTaskRunCount++;

    }

    /**
     * Connect to the peer via Bluetooth. Upon success, start an exchange with
     * the peer. If we're already connecting to someone, this method returns
     * without doing anything.
     *
     * @param peer The peer we want to talk to.
     */
    public void connectTo(Peer peer) {
        if (getConnecting() != null) {
            Log.w(TAG, "connectTo() not connecting to " + peer + " -- already connecting to (" + getConnecting() + ")");
            return;
        }

        Log.i(TAG, "connecting to " + peer);
        // This gets reset to false once an exchange is complete or when the
        // connect call below fails. Until then, no more connections will be
        // attempted. (One at a time now!)
        setConnecting(peer.address);

        Log.i(TAG, "Starting to connect to " + peer.toString());
        // The peer connection callback (defined elsewhere in the class) takes
        // the connect bluetooth socket and uses it to create a new Exchange.
        if (mPeerConnectionCallback == null)
            Log.i(TAG, "Was starting to connect to " + peer.toString()
                    + " but PeerConnectionCallback was null");
        mBluetoothSpeaker.connect(peer, mPeerConnectionCallback);
    }

    /**
     * Handles connection to a peer by taking the connected bluetooth socket and
     * using it in an Exchange.
     */
    /*package*/ PeerConnectionCallback mPeerConnectionCallback = new PeerConnectionCallback() {
        @Override
        public void success(BluetoothSocket socket) {
            Log.i(TAG, "Callback says we're connected to " + socket.getRemoteDevice().toString());
            if (socket.isConnected()) {
                mSocket = socket;
                Log.i(TAG, "Socket connected, attempting exchange");
                try {
                    direction = 1;
                    remoteAddress = socket.getRemoteDevice().getAddress();
                    mExchange = new CryptographicExchange(
                            MurmurService.this,
                            socket.getRemoteDevice().getAddress(),
                            socket.getInputStream(),
                            socket.getOutputStream(),
                            true,
                            mFriendStore,
                            mMessageStore,
                            MurmurService.this.mExchangeCallback);
                    (new Thread(mExchange)).start();
                } catch (IOException e) {
                    Log.e(TAG, "Getting input/output stream from socket failed: ", e);
                    Log.e(TAG, "Exchange not happening.");
                    MurmurService.this.cleanupAfterExchange();
                }
            } else {
                Log.w(TAG, "But the socket claims not to be connected!");
                MurmurService.this.cleanupAfterExchange();
            }
        }

        @Override
        public void failure(String reason) {
            Log.i(TAG, "Callback says we failed to connect: " + reason);
            MurmurService.this.cleanupAfterExchange();
        }
    };

    /**
     * Cleans up sockets and connecting state after an exchange, including recording
     * that an exchange was just attempted, that we're no longer currently connecting,
     * closing sockets and setting socket variables to null, etc.
     * <p>
     * Is also used after a Bluetooth connection failure to cleanup.
     */
    /* package */ void cleanupAfterExchange() {
        setConnecting(null);
        setLastExchangeTime();
        try {
            if (mSocket != null) {
                mSocket.close();
                Log.i(TAG, "bluetooth socket closed");
            }
        } catch (IOException e) {
            Log.w(TAG, "Couldn't close bt socket: ", e);
        }
        try {
            if (mBluetoothSpeaker.mSocket != null) {
                mBluetoothSpeaker.mSocket.close();
                Log.i(TAG, "bluetooth speaker socket closed");
            }
        } catch (IOException e) {
            Log.w(TAG, "Couldn't close bt socket in BTSpeaker: ", e);
        }
        mSocket = null;
        mBluetoothSpeaker.mSocket = null;
        Log.d(TAG, "socket and BluetoothSpeaker socket has been set to null");

        direction = 0;
        remoteAddress = null;
    }


    /**
     * Passed to an Exchange to be called back to when the exchange completes.
     * Performs the integration of the information received from the exchange -
     * adds new messages to the message store, weighting their priorities
     * based upon the friends in common.
     */
    /* package */ ExchangeCallback mExchangeCallback = new ExchangeCallback() {

        private void handleMessage(MobyMessage m, String sender) {
            // TODO amoghbl1: Figure out if we need to decrypt inside a Job or not.
            String encodedMessage = m.getPayload();
            byte[] decodedContent = null;
            try {
                decodedContent = Base64.decode(encodedMessage);
            } catch (IOException e) {

            }

            // NOTE: This is going to break for multi device encryption :)
            SignalServiceEnvelope envelope = new SignalServiceEnvelope(SignalServiceProtos.Envelope.Type.CIPHERTEXT_VALUE, sender,
                    SignalServiceAddress.DEFAULT_DEVICE_ID, "",
                    m.getTimestamp(), null,
                    decodedContent);

            PushContentReceiveJob receiveJob = new PushContentReceiveJob(MurmurService.this);
            receiveJob.handle(envelope, false);
        }

        @Override
        public void success(Exchange exchange) {
            mServiceWatchDog.notifyLastExchange();
            boolean hasNew = false;
            List<MobyMessage> newMessages = exchange.getReceivedMessages();
            int friendOverlap = exchange.getCommonFriends();


            // Handling received Messages.
            Log.i(TAG, "Got " + newMessages.size() + " new messages in exchangeCallback");
            Log.i(TAG, "Got " + friendOverlap + " common friends in exchangeCallback");
            for (MobyMessage message : newMessages) {
                if (mMessageStore.containsOrRemoved(message.getPayload())) {
                    //update existing message priority unless its marked as removed by user
                    // TODO amoghbl1: Figure out what to do in this case.
                } else {
                    hasNew = true;

                    Log.d(TAG, "MobyTag: " + message.getMobyTag());
                    String sender = mFriendStore.verifyMobyTag(message.getMobyTag(), message.getPayload());
                    if (sender != null)
                        handleMessage(message, sender);

                    mMessageStore.addMessage(message);
                }
            }

            if (hasNew) {
                mMessageStore.updateStoreVersion();
                mExchangeHistoryTracker.incrementExchangeCount();
                mExchangeHistoryTracker.updateHistory(MurmurService.this, exchange.getPeerAddress());
                if (isAppInForeground()) {
                    Intent intent = new Intent();
                    intent.setAction(MessageStore.NEW_MESSAGE);
                    getApplicationContext().sendBroadcast(intent);
                } else {
                    // TODO: Add message handling logic
                    // Here is where we need to decide if the message is for us or not and
                    // put it into a packet queue or add it to a conversation.

                    // showUnreadMessagesNotification();
                }
            } else if (mExchangeHistoryTracker.getHistoryItem(exchange.getPeerAddress()) != null) {
                // Has history, should increment the attempts counter
                mExchangeHistoryTracker.updateAttemptsHistory(exchange.getPeerAddress());
                if (USE_BACKOFF)
                    Log.d(TAG, "Exchange finished without receiving new messages, back-off timeout increased to:" +
                            Math.min(BACKOFF_MAX, Math.pow(2, mExchangeHistoryTracker.getHistoryItem(exchange.getPeerAddress()).attempts) * BACKOFF_FOR_ATTEMPT_MILLIS));
            } else {
                // No history file, create one
                Log.d(TAG, "Exchange finished without receiving new messages from new peer, creating history track");
                mExchangeHistoryTracker.updateHistory(MurmurService.this, exchange.getPeerAddress());
            }

            MurmurService.this.cleanupAfterExchange();
        }

        @Override
        public void failure(Exchange exchange, String reason) {
            Log.e(TAG, "Exchange failed, reason: " + reason);
            MurmurService.this.cleanupAfterExchange();
        }

        @Override
        public void recover(Exchange exchange, String reason) {
            mServiceWatchDog.notifyLastExchange();
            Log.e(TAG, "Exchange failed but data can be recovered, reason: " + reason);
            boolean hasNew = false;
            List<MobyMessage> newMessages = exchange.getReceivedMessages();
            int friendOverlap = Math.max(exchange.getCommonFriends(), 0);
            Log.i(TAG, "Got " + newMessages.size() + " messages in exchangeCallback");
            Log.i(TAG, "Got " + friendOverlap + " common friends in exchangeCallback");
            if (newMessages != null) {
                for (MobyMessage message : newMessages) {
                    if (mMessageStore.containsOrRemoved(message.getPayload())) {
                        //update existing message priority unless its marked as removed by user
                        // TODO amoghbl1: figure out what to do in this case
                    } else {
                        hasNew = true;
                        mMessageStore.addMessage(message);
                    }
                }
            }

            if (hasNew) {
                mMessageStore.updateStoreVersion();
                mExchangeHistoryTracker.incrementExchangeCount();
                mExchangeHistoryTracker.updateHistory(MurmurService.this, exchange.getPeerAddress());
                if (isAppInForeground()) {
                    Intent intent = new Intent();
                    intent.setAction(MessageStore.NEW_MESSAGE);
                    getApplicationContext().sendBroadcast(intent);
                } else {
                    // TODO: Add message handling logic
                    // Here is where we need to decide if the message is for us or not and
                    // put it into a packet queue or add it to a conversation.

                    // showUnreadMessagesNotification();
                }
            } else {
                mExchangeHistoryTracker.updateAttemptsHistory(exchange.getPeerAddress());
            }

            MurmurService.this.cleanupAfterExchange();
        }
    };

    /**
     * Check whether any network connection (Wifi/Cell) is available according
     * to the OS's connectivity service.
     *
     * @return True if any network connection seems to be available, false
     * otherwise.
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Return true if Bluetooth is turned on, false otherwise.
     *
     * @return Whether Bluetooth is enabled.
     */
    private boolean isBluetoothOn() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return false;
        } else {
            return adapter.isEnabled();
        }
    }

    /**
     * Return the number of times that background tasks have been executed since
     * the service was started.
     *
     * @return The number of times backgroundTasks() has been called.
     */
    public int getBackgroundTasksRunCount() {
        return mBackgroundTaskRunCount;
    }

    /**
     * Get the time at which this instance of the service was started.
     *
     * @return A Date representing the time at which the service was started.
     */
    public Date getServiceStartTime() {
        return mStartTime;
    }

    /**
     * Synchronized accessor for connecting.
     */
    public synchronized String getConnecting() {
        return connecting;
    }

    /**
     * Synchronized setter for connecting.
     */
    private synchronized void setConnecting(String connecting) {
        Log.d(TAG, "connection was set to:" + connecting);
        this.connecting = connecting;
    }

    /**
     * This method has to be implemented on a service, but I haven't written the
     * service with binding in mind. Unsure what would happen if it were used
     * this way.
     *
     * @param intent The intent used to bind the service (passed to
     *               Context.bindService(). Extras included in the intent will not
     *               be visible here.
     * @return A communication channel to the service. This implementation just
     * returns null.
     * @see android.app.Service
     */
    @Override
    public IBinder onBind(Intent intent) {
        // This service is not meant to be used through binding.
        return null;
    }

    /**
     * Check if the app have a living instance in the foreground
     *
     * @return true if the app is active and in the foreground, false otherwise
     */
    public boolean isAppInForeground() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> runningTaskInfo = manager.getRunningTasks(1);
        ComponentName componentInfo = runningTaskInfo.get(0).topActivity;
        return componentInfo.getPackageName().contains("org.denovogroup.murmur");
    }

    /**
     * retrieve the bluetooth MAC address from the bluetooth speaker and set the WifiDirectSpeaker
     * friendly name accordingly.
     */
    private void setWifiDirectFriendlyName() {
        String btAddress = mBluetoothSpeaker.getAddress();
        if (mWifiDirectSpeaker != null) {
            String oldName = BluetoothAdapter.getDefaultAdapter().getName();
            String ourName = RSVP_PREFIX + btAddress;

            if (oldName.equals(ourName))
                return;

            SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.PREF_FILE, Context.MODE_PRIVATE);
            if (!sharedPreferences.contains(AppConstants.WIFI_NAME)) {
                if (BluetoothAdapter.getDefaultAdapter() != null) {
                    sharedPreferences.edit().putString(AppConstants.WIFI_NAME, oldName).commit();
                }
            }
            mWifiDirectSpeaker.setWifiDirectUserFriendlyName(ourName);
            if (btAddress != null && (btAddress.equals(DUMMY_MAC_ADDRESS) || btAddress.equals(""))) {
                Log.w(TAG, "Bluetooth speaker provided a dummy/blank bluetooth" +
                        " MAC address (" + btAddress + ") scheduling device name change.");
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setWifiDirectFriendlyName();
                    }
                }, RENAME_DELAY);
            }
        } else {
            Log.w(TAG, "setWifiDirectFriendlyName was called with null wifiDirectSpeaker");
        }
    }

    private void cleanupMessageStore() {
        SecurityProfile currentProfile = SecurityManager.getCurrentProfile(this);

        // TODO amoghbl1: Write something to drop messages here.
    }

    private Peer pickBestPeer(List<Peer> peers) {
        ExchangeHistoryTracker tracker = mExchangeHistoryTracker;
        Peer bestMatch = null;
        long bestMatchLastPicked = 0;
        for (Peer peer : peers) {
            if (bestMatch == null) {
                //no better match yet, this will be it
                bestMatch = peer;
                ExchangeHistoryTracker.ExchangeHistoryItem history = tracker.getHistoryItem(peer.address);
                if (history != null) {
                    bestMatchLastPicked = history.getLastPicked();
                }
            } else {
                ExchangeHistoryTracker.ExchangeHistoryItem history = tracker.getHistoryItem(peer.address);

                if (history == null) {
                    // no history regarding this peer, must be new
                    bestMatch = peer;
                    break;
                } else {
                    //has history, compare pick time
                    if (bestMatchLastPicked > history.getLastPicked()) {
                        //was not picked in a long time, pick him
                        bestMatch = peer;
                        bestMatchLastPicked = history.getLastPicked();
                    }
                }
            }
        }

        return bestMatch;
    }
}
