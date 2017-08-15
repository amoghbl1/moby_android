package info.guardianproject.nearby.bluetooth.roles.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import de.greenrobot.event.EventBus;
import info.guardianproject.nearby.bluetooth.bus.BluetoothCommunicator;
import info.guardianproject.nearby.bluetooth.bus.*;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothClient implements Runnable {

    private boolean CONTINUE_READ_WRITE = true;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private UUID mUuid;
    private String mAdressMac;

    private BluetoothSocket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    private BluetoothConnector mBluetoothConnector;

    public BluetoothClient(BluetoothAdapter bluetoothAdapter, String adressMac) {
        mBluetoothAdapter = bluetoothAdapter;
        mAdressMac = adressMac;
      //  mUuid = UUID.fromString("e0917680-d427-11e4-8830-" + bluetoothAdapter.getAddress().replace(":", ""));
        mUuid = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    }

    @Override
    public void run() {

        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mAdressMac);
//        List<UUID> uuidCandidates = new ArrayList<UUID>();
//        uuidCandidates.add(mUuid);

        boolean isSecure = false;// mBluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE;

        while(mInputStream == null){
            mBluetoothConnector = new BluetoothConnector(mBluetoothDevice, isSecure, mBluetoothAdapter, mUuid);

            try {
                mSocket = mBluetoothConnector.connect().getUnderlyingSocket();
                mInputStream = mSocket.getInputStream();
            } catch (IOException e1) {
                Log.e("", "===> mSocket IOException", e1);
                EventBus.getDefault().post(new ClientConnectionFail());
                e1.printStackTrace();
            }
        }

        if (mSocket == null) {
            Log.e("", "===> mSocket == Null");
            return;
        }

        try {

            mOutputStream = new DataOutputStream(mSocket.getOutputStream());

            int bufferSize = 1024;
            int bytesRead = -1;
            byte[] buffer = new byte[bufferSize];

            EventBus.getDefault().post(new ClientConnectionSuccess());

            while (CONTINUE_READ_WRITE) {

                final StringBuilder sb = new StringBuilder();
                bytesRead = mInputStream.read(buffer);
                if (bytesRead != -1) {
                    String result = "";
                    while ((bytesRead == bufferSize) && (buffer[bufferSize] != 0)) {
                        result = result + new String(buffer, 0, bytesRead);
                        bytesRead = mInputStream.read(buffer);
                    }
                    result = result + new String(buffer, 0, bytesRead);
                    sb.append(result);
                }

                 EventBus.getDefault().post(new BluetoothCommunicator(sb.toString()));

            }
        } catch (IOException e) {
            Log.e("", "===> Client run");
            e.printStackTrace();
            EventBus.getDefault().post(new ClientConnectionFail());
        }
    }

    public void write(String message) {
        write(message.getBytes());
    }

    public void write(byte[] message) {
        try {
            mOutputStream.write(message);
            mOutputStream.flush();
        } catch (IOException e) {
            Log.e("", "===> Client write");
            e.printStackTrace();
        }
    }

    public void closeConnexion() {
        if (mSocket != null) {
            try {
                mInputStream.close();
                mInputStream = null;
                mOutputStream.close();
                mOutputStream = null;
                mSocket.close();
                mSocket = null;
                mBluetoothConnector.close();
            } catch (Exception e) {
                Log.e("", "===> Client closeConnexion");
            }
            CONTINUE_READ_WRITE = false;
        }
    }
}
