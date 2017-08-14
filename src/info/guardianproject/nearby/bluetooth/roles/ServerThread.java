package info.guardianproject.nearby.bluetooth.roles;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import info.guardianproject.nearby.NearbyMedia;

public class ServerThread extends Thread {
    private final String TAG = "btxfr/ServerThread";
    private final BluetoothServerSocket serverSocket;
    private final Handler handler;
    private Handler incomingHandler;

    private ServerListener serverListener;

    private NearbyMedia mMedia;

    public interface ServerListener {
        public void onDeviceConnected(BluetoothDevice device);
    }

    public ServerThread(BluetoothAdapter adapter, Handler handler, boolean secure) {
        this.handler = handler;
        BluetoothServerSocket tempSocket = null;
        try {
            if (secure)
                tempSocket = adapter.listenUsingRfcommWithServiceRecord(Constants.NAME, UUID.fromString(Constants.UUID_STRING));
            else
                tempSocket = adapter.listenUsingInsecureRfcommWithServiceRecord(Constants.NAME, UUID.fromString(Constants.UUID_STRING));


        } catch (IOException ioe) {
            Log.e(TAG, ioe.toString());
        }
        serverSocket = tempSocket;

    }

    public void setShareMedia (NearbyMedia media)
    {
        mMedia = media;
    }

    public void setServerListener (ServerListener sl)
    {
        this.serverListener = sl;
    }

    public void run() {
        BluetoothSocket socket = null;
        if (serverSocket == null)
        {
            Log.d(TAG, "Server socket is null - something went wrong with Bluetooth stack initialization?");
            return;
        }
        while (true) {
            try {
                Log.v(TAG, "Waiting to open new server socket");
                socket = serverSocket.accept();

                try {
                    Log.v(TAG, "Got connection from client.  Spawning new data transfer thread.");
                    DataTransferThread dataTransferThread = new DataTransferThread(socket, handler);
                    dataTransferThread.setData(mMedia);
                    dataTransferThread.start();

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }


            } catch (IOException ioe) {
                Log.v(TAG, "Server socket was closed - likely due to cancel method on server thread");
                break;
            }
        }
    }



    public void cancel() {
        try {
            Log.v(TAG, "Trying to close the server socket");
            serverSocket.close();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}
