package info.guardianproject.nearby.bluetooth.roles.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import de.greenrobot.event.EventBus;
import info.guardianproject.nearby.bluetooth.bus.BluetoothCommunicator;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionFail;
import info.guardianproject.nearby.bluetooth.bus.ServeurConnectionSuccess;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothServer implements Runnable {

    private boolean CONTINUE_READ_WRITE = true;
    private boolean KEEP_RUNNING = true;

    private UUID mUUID;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStreamWriter;

    public BluetoothServer(BluetoothAdapter bluetoothAdapter){
        mBluetoothAdapter = bluetoothAdapter;

        mUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
        try {
            mServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BLTServer", mUUID);
        }
        catch (IOException ioe)
        {
            Log.e("", "ERROR : " + ioe.getMessage());
            EventBus.getDefault().post(new ServeurConnectionFail("ERROR : " + ioe.getMessage()));
        }
    }

    @Override
    public void run() {

        while (KEEP_RUNNING) {
            try {
                // mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("BLTServer", mUUID);

                mSocket = mServerSocket.accept();
                mInputStream = mSocket.getInputStream();
                mOutputStreamWriter = new DataOutputStream(mSocket.getOutputStream());

                String currentClient = mSocket.getRemoteDevice().getName() + ":" + mSocket.getRemoteDevice().getAddress();
                EventBus.getDefault().post(new ServeurConnectionSuccess(currentClient));

                int bufferSize = 1024;
                int bytesRead = -1;
                byte[] buffer = new byte[bufferSize];

                CONTINUE_READ_WRITE = true;

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
                Log.e("", "ERROR : " + e.getMessage());
                EventBus.getDefault().post(new ServeurConnectionFail(""));
            }
        }
    }

    public void write(String message) {
        try {
            if(mOutputStreamWriter != null) {
                mOutputStreamWriter.write(message.getBytes());
                mOutputStreamWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(byte[] message) {
        try {
            if(mOutputStreamWriter != null) {
                mOutputStreamWriter.write(message);
                mOutputStreamWriter.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection(){
        if(mSocket != null){
            try{
                KEEP_RUNNING = false;
                mInputStream.close();
                mInputStream = null;
                mOutputStreamWriter.close();
                mOutputStreamWriter = null;
                mSocket.close();
                mSocket = null;
                mServerSocket.close();
                mServerSocket = null;
                CONTINUE_READ_WRITE = false;
            }catch(Exception e){}
            CONTINUE_READ_WRITE = false;
        }
    }
}
