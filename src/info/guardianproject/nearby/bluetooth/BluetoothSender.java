package info.guardianproject.nearby.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.File;

import info.guardianproject.nearby.NearbyListener;
import info.guardianproject.nearby.NearbyMedia;
import info.guardianproject.nearby.NearbySender;
import info.guardianproject.nearby.Neighbor;
import info.guardianproject.nearby.bluetooth.roles.ClientThread;
import info.guardianproject.nearby.bluetooth.roles.Constants;
import info.guardianproject.nearby.bluetooth.roles.ProgressData;
import info.guardianproject.nearby.bluetooth.roles.ServerThread;
import info.guardianproject.nearby.bluetooth.roles.mananger.BluetoothManager;

/**
 * Created by n8fr8 on 9/2/16.
 */
public class BluetoothSender implements NearbySender {

    protected BluetoothManager mBluetoothManager;
    private boolean mPairedDevicesOnly = false;

    private ServerThread mServerThread;

    private NearbyListener mNearbyListener;

    public BluetoothSender(Activity context)
    {
        mBluetoothManager = new BluetoothManager(context);
        mBluetoothManager.selectServerMode();
    }

    public void setNearbyListener (NearbyListener nearbyListener)
    {
        mNearbyListener = nearbyListener;
    }

    public void setPairedDevicesOnly (boolean pairedDevicesOnly)
    {
        mPairedDevicesOnly = pairedDevicesOnly;
    }

    public boolean isNetworkEnabled ()
    {
        return mBluetoothManager.getAdapter().isEnabled();
    }

    public void setTimeDiscoverable(int timeInSec){
        mBluetoothManager.setTimeDiscoverable(timeInSec);
    }

    public void setShareFile (File fileMedia, byte[] digest, String title, String mimeType, String metadata) {


        mServerThread = new ServerThread(mBluetoothManager.getAdapter(), mHandler, mPairedDevicesOnly);

        NearbyMedia nearbyMedia = new NearbyMedia();
        nearbyMedia.mTitle = title;
        nearbyMedia.mDigest = digest;
        nearbyMedia.mFileMedia = fileMedia;
        nearbyMedia.mLength = fileMedia.length();
        nearbyMedia.mMimeType = mimeType;
        nearbyMedia.mMetadataJson = metadata;

        mServerThread.setShareMedia(nearbyMedia);
    }

    public void setShareFile (NearbyMedia nearbyMedia) {

        mServerThread = new ServerThread(mBluetoothManager.getAdapter(), mHandler, mPairedDevicesOnly);
        mServerThread.setShareMedia(nearbyMedia);
    }

    public void startSharing ()
    {
        boolean isDiscoverable = !mPairedDevicesOnly;

        if (isDiscoverable) {
            if (!mBluetoothManager.isDiscoverable()) {
                setTimeDiscoverable(BluetoothManager.BLUETOOTH_TIME_DICOVERY_3600_SEC);
            }
        }

        mBluetoothManager.selectServerMode();
        if (isDiscoverable)
            mBluetoothManager.makeDiscoverable();

        mServerThread.start();

    }

    public void stopSharing ()
    {

            new Thread ()
            {
                public void run ()
                {
                    if (mServerThread != null)
                        mServerThread.cancel();

                    mBluetoothManager.disconnectServer();
                }
            }.start();


    }


    private Handler mHandler = new Handler ()
    {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case Constants.MessageType.READY_FOR_DATA: {
                    log("ready for data");

                    break;
                }

                case Constants.MessageType.COULD_NOT_CONNECT: {
                    log("could not connect");

                    break;
                }

                case Constants.MessageType.SENDING_DATA: {
                    log("sending data");

                    break;
                }

                case Constants.MessageType.DATA_SENT_OK: {
                    log("data sent ok");

                    break;
                }

                case Constants.MessageType.DATA_RECEIVED: {
                    log("data received");



                    break;
                }

                case Constants.MessageType.DATA_PROGRESS_UPDATE: {
                    log("data progress update");

                    break;
                }

                case Constants.MessageType.DIGEST_DID_NOT_MATCH: {
                    log("digest did not match");

                    break;
                }
            }

            if (message.obj != null) {
                if (message.obj instanceof byte[])
                    log(new String((byte[])message.obj));
                else if (message.obj instanceof ProgressData)
                {
                    ProgressData pd = (ProgressData)message.obj;

                    long remaining = pd.totalSize-pd.remainingSize;



                    int perComplete = -1;

                    perComplete = (int) ((((float) remaining) / ((float) pd.totalSize)) * 100f);
                    log("progress: " + (pd.totalSize - pd.remainingSize) + "/" + pd.totalSize);

                    if (mNearbyListener != null) {
                        String deviceName = message.getData().getString("deviceName");
                        String deviceAddress = message.getData().getString("deviceAddress");
                        String mimeType = message.getData().getString("type");
                        String mediaName = message.getData().getString("name");

                        Neighbor neighbor = new Neighbor(deviceAddress, deviceName, Neighbor.TYPE_BLUETOOTH);
                        mNearbyListener.transferProgress(neighbor, null, mediaName,mimeType, pd.remainingSize, pd.totalSize);
                    }
                }
                else
                    log(message.obj.toString());
            }
        }
    };


    private void log (String msg)
    {

        Log.d("BluetoothServer",msg);
    }


}
