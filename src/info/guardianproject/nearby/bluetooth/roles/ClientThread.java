package info.guardianproject.nearby.bluetooth.roles;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.UUID;

public class ClientThread extends Thread {
    private final String TAG = "btxfr";
    private BluetoothSocket socket;
    private final Handler handler;
    public Handler incomingHandler;
    private boolean secure;
    private BluetoothDevice device;
    private DataTransferThread dataTransferThread;

    public ClientThread(BluetoothDevice device, Handler handler, boolean secure) {
        BluetoothSocket tempSocket = null;
        this.handler = handler;
        this.secure = secure;
        this.device = device;

        try {
            if (secure)
                tempSocket = device.createRfcommSocketToServiceRecord(UUID.fromString(Constants.UUID_STRING));
            else
                tempSocket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(Constants.UUID_STRING));

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        this.socket = tempSocket;
    }

    public void run() {

        int connectRetry = 0;
        boolean connected = false;

        while (connectRetry < 3)
        {
            try {
                Log.d(TAG, "Opening client socket to: " + device.getName());
                socket.connect();
                Log.d(TAG, "Connection established: " + device.getName());
                connected = true;
                break;

            } catch (Exception e) {
                Log.e(TAG, "Connection issue",e);
                try { Thread.sleep(500);}catch(Exception e1){}
                connectRetry++;
            }
        }

        if (!connected) {

            //try the fallback
            try {
                Class<?> clazz = socket.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};

                String methodName = "createInsecureRfcommSocket";
                if (secure)
                    methodName = "createRfcommSocket";

                Method m = clazz.getMethod(methodName, paramTypes);
                Object[] params = new Object[]{Integer.valueOf(1)};
                socket = (BluetoothSocket) m.invoke(socket.getRemoteDevice(), params);

                Thread.sleep(500);
                socket.connect();

                connected = true;

            } catch (Exception e1) {
                Log.e("BT", "Fallback failed. Cancelling connection to: " + device.getName(), e1);
                handler.sendEmptyMessage(Constants.MessageType.COULD_NOT_CONNECT);


            }
        }

        if (connected) {
            Looper.prepare();

            try {
                Log.d(TAG, "Got connection from server.  Spawning new data transfer thread.");
                dataTransferThread = new DataTransferThread(socket, handler);
                dataTransferThread.start();
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }

            handler.sendEmptyMessage(Constants.MessageType.READY_FOR_DATA);
            Looper.loop();
        }
        else
        {
            handler.sendEmptyMessage(Constants.MessageType.COULD_NOT_CONNECT);

        }
    }



    public void cancel() {
        try {
            if (socket != null) {
                socket.close();
            }

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}