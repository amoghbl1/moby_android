package info.guardianproject.nearby.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

import java.io.File;
import java.util.HashMap;

import de.greenrobot.event.EventBus;
import info.guardianproject.nearby.NearbyListener;
import info.guardianproject.nearby.NearbyMedia;
import info.guardianproject.nearby.Neighbor;
import info.guardianproject.nearby.bluetooth.bus.BluetoothCommunicator;
import info.guardianproject.nearby.bluetooth.bus.BondedDevice;
import info.guardianproject.nearby.bluetooth.bus.ClientConnectionFail;
import info.guardianproject.nearby.bluetooth.bus.ClientConnectionSuccess;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionFail;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionSuccess;
import info.guardianproject.nearby.bluetooth.roles.ClientThread;
import info.guardianproject.nearby.bluetooth.roles.Constants;
import info.guardianproject.nearby.bluetooth.roles.ProgressData;
import info.guardianproject.nearby.bluetooth.roles.mananger.BluetoothManager;
import io.scal.secureshare.libnearbynsd.R;

/**
 * Created by n8fr8 on 9/2/16.
 */
public class BluetoothReceiver {

    protected BluetoothManager mBluetoothManager;

    private static HashMap<String,BluetoothDevice> mFoundDevices = new HashMap<String,BluetoothDevice>();
    private HashMap<String, ClientThread> clientThreads = new HashMap<String, ClientThread>();

    private boolean mPairedDevicesOnly = false;

    private NearbyListener mNearbyListener;

    public BluetoothReceiver(Activity context)
    {
        mBluetoothManager = new BluetoothManager(context);
        mBluetoothManager.selectClientMode();
    }

    public void setPairedDevicesOnly (boolean pairedDevicesOnly)
    {
        mPairedDevicesOnly = pairedDevicesOnly;
    }

    public void setNearbyListener (NearbyListener nearbyListener)
    {
        mNearbyListener = nearbyListener;
    }

    public boolean isNetworkEnabled ()
    {
        return mBluetoothManager.getAdapter().isEnabled();
    }

    public void start ()
    {
        EventBus.getDefault().register(this);

        new Thread () {

            public void run() {

                boolean foundPairedDevice = false;

                //first check for paired devices
                for (BluetoothDevice device : mBluetoothManager.getAdapter().getBondedDevices()) {
                    if (device != null && device.getName() != null &&
                            (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA ||
                                    device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA ||
                                    device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART)) {

                        mFoundDevices.put(device.getAddress(), device);
                        //for previously found devices, try to automatically connect
                        ClientThread clientThread = new ClientThread(device, mHandler, mPairedDevicesOnly);
                        clientThreads.put(device.getAddress(), clientThread);

                        clientThread.start();

                        foundPairedDevice = true;
                    }
                }

                if (mPairedDevicesOnly && (!foundPairedDevice))
                    noPairedDevices(); //no paired device? Prompt user to add!

                if (!mPairedDevicesOnly) {

                    //start scanning
                    mBluetoothManager.scanAllBluetoothDevice();

                    //connecting to previously connected devices
                    if (clientThreads.isEmpty()) {
                        for (BluetoothDevice device : mFoundDevices.values()) {

                            if (!clientThreads.containsKey(device.getAddress())) {
                                //for previously found devices, try to automatically connect
                                ClientThread clientThread = new ClientThread(device, mHandler, mPairedDevicesOnly);
                                clientThread.start();

                                clientThreads.put(device.getAddress(), clientThread);
                            }
                        }
                    }
                }
            }
        }.start();
    }

    public void onEventMainThread(BluetoothDevice device) {

        foundDevice(device);

    }

    public void onEventMainThread(ClientConnectionSuccess event){
        mBluetoothManager.isConnected = true;
    }

    public void onEventMainThread(ClientConnectionFail event){
        mBluetoothManager.isConnected = false;
    }

    public void onEventMainThread(BluetoothCommunicator event){
    }

    public void onEventMainThread(BondedDevice event){
        //mBluetoothManager.sendMessage("BondedDevice");
    }

    public void cancel ()
    {
        for (ClientThread clientThread : clientThreads.values()) {

            if (clientThread != null && clientThread.isAlive())
                clientThread.cancel();

        }

        clientThreads.clear();

        mBluetoothManager.stopScanningBluetoothDevices();
        mBluetoothManager.disconnectClient();

        EventBus.getDefault().unregister(this);

    }

    private void noPairedDevices ()
    {
        if (mNearbyListener != null)
            mNearbyListener.noNeighborsFound();
    }

    public boolean foundDevice (BluetoothDevice device)
    {
        boolean resultIsNew = false;

        if (device != null && device.getName() != null &&
                (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA ||
                        device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA ||
                        device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART)) {


            if (mPairedDevicesOnly && device.getBondState() == BluetoothDevice.BOND_NONE)
                return false; //we can only support paired devices


            if (!mFoundDevices.containsKey(device.getAddress())) {
                mFoundDevices.put(device.getAddress(), device);
                resultIsNew = true;

                if (mNearbyListener != null) {
                    Neighbor neighbor = new Neighbor(device.getAddress(),device.getName(),Neighbor.TYPE_BLUETOOTH);
                    mNearbyListener.foundNeighbor(neighbor);
                }
            }

            if (clientThreads.containsKey(device.getAddress()))
                if (clientThreads.get(device.getAddress()).isAlive())
                    return false; //we have a running thread here people!

            log("Found device: " + device.getName() + ":" + device.getAddress());

            ClientThread clientThread = new ClientThread(device, mHandler, mPairedDevicesOnly);
            clientThread.start();

            clientThreads.put(device.getAddress(), clientThread);

        }

        return resultIsNew;
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

                    if (message.obj instanceof File) {

                        NearbyMedia media = new NearbyMedia();

                        media.mFileMedia = (File) message.obj;

                        String deviceName = message.getData().getString("deviceName");
                        String deviceAddress = message.getData().getString("deviceAddress");
                        Neighbor neighbor = new Neighbor(deviceAddress, deviceName, Neighbor.TYPE_BLUETOOTH);


                        media.mMimeType = message.getData().getString("type");
                        media.mTitle = message.getData().getString("name");
                        media.mMetadataJson = message.getData().getString("metadataJson");

                        if (mNearbyListener != null)
                            mNearbyListener.transferComplete(neighbor, media);

                        return;
                    }

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
