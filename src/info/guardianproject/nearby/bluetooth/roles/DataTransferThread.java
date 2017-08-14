package info.guardianproject.nearby.bluetooth.roles;

import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;

import info.guardianproject.nearby.NearbyMedia;

class DataTransferThread extends Thread {

    private final String TAG = "btxfr";
    private final BluetoothSocket socket;
    private Handler handler;

    private NearbyMedia mMedia;


    public DataTransferThread(BluetoothSocket socket, Handler handler) {
        this.socket = socket;
        this.handler = handler;
    }

    public void run() {

        if (this.mMedia != null)
            sendData();
       else
            receiveData();

    }

    public void setData (NearbyMedia media) {

        mMedia = media;
    }

    private void receiveData ()
    {
        try {

            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            OutputStream outputStream = socket.getOutputStream();
            boolean waitingForHeader = true;

            File dirDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File fileOut = null;
            String fileName = null;
            String fileType = null;
            String metadataJson = null;

            OutputStream dataOutputStream = null;

            byte[] headerBytes = new byte[22];
            byte[] digest = new byte[16];
            int headerIndex = 0;
            ProgressData progressData = new ProgressData();

            while (true) {
                if (waitingForHeader) {
                   // Log.v(TAG, "Waiting for Header...");
;
                    while (inputStream.available()< headerBytes.length)
                    {
                        try { Thread.sleep(50);} catch (Exception e){}
                    }

                    int byteMsb = (int)inputStream.readByte();
                    int byteLsb = (int)inputStream.readByte();

                    if ((byteMsb == Constants.HEADER_MSB) && (byteLsb == Constants.HEADER_LSB)) {
                        Log.v(TAG, "Header Received.  Now obtaining length");

                        progressData.totalSize = inputStream.readLong();
                        progressData.remainingSize = progressData.totalSize;
                        Log.v(TAG, "Data size: " + progressData.totalSize);

                        int digestLength = inputStream.readInt();
                        digest = new byte[digestLength];
                        inputStream.read(digest);

                        fileName = inputStream.readUTF();
                        fileType = inputStream.readUTF();
                        metadataJson = inputStream.readUTF();

                        String fileExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(fileType);

                        fileOut = new File(dirDownloads,new Date().getTime()+"."+fileExt);
                        dataOutputStream = new DataOutputStream(new FileOutputStream(fileOut));

                        waitingForHeader = false;
                        sendProgress(progressData, fileName, fileType);
                    } else {
                        Log.e(TAG, "Did not receive correct header.  Closing socket");
                        socket.close();
                        handler.sendEmptyMessage(Constants.MessageType.INVALID_HEADER);
                        break;
                    }


                }

                if (!waitingForHeader) {
                    // Read the data from the stream in chunks
                    byte[] buffer = new byte[Constants.CHUNK_SIZE];


                   // Log.v(TAG, "Waiting for data.  Expecting " + progressData.remainingSize + " more bytes.");

                    while (progressData.remainingSize > 0) {

                        int bytesRead = inputStream.read(buffer);
              //          Log.v(TAG, "Read " + bytesRead + " bytes into buffer");
                        dataOutputStream.write(buffer, 0, bytesRead);
                        progressData.remainingSize -= bytesRead;
                        sendProgress(progressData, fileName, fileType);
                    }

                    dataOutputStream.flush();
                    dataOutputStream.close();

                    break;
                }
            }

            if (Utils.checkDigest(digest,fileOut)) {
                Log.v(TAG, "Digest matches OK.");
                Message message = new Message();
                message.obj = fileOut;
                message.what = Constants.MessageType.DATA_RECEIVED;

                message.getData().putString("deviceAddress",socket.getRemoteDevice().getAddress());
                message.getData().putString("deviceName",socket.getRemoteDevice().getName());
                message.getData().putString("name",fileName);
                message.getData().putString("type",fileType);
                message.getData().putString("metadataJson",metadataJson);
                handler.sendMessage(message);

                // Send the digest back to the client as a confirmation
                Log.v(TAG, "Sending back digest for confirmation");
                outputStream.write(digest);


            } else {
                Log.e(TAG, "Digest did not match.  Corrupt transfer?");
                handler.sendEmptyMessage(Constants.MessageType.DIGEST_DID_NOT_MATCH);
            }

          //  Log.v(TAG, "Closing server socket");
            socket.close();

        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
        }
    }

    private void sendProgress(ProgressData progressData, String fileName, String fileType) {
        Message message = new Message();
        message.obj = progressData;
        message.what = Constants.MessageType.DATA_PROGRESS_UPDATE;
        message.getData().putString("deviceAddress",socket.getRemoteDevice().getAddress());
        message.getData().putString("deviceName",socket.getRemoteDevice().getName());
        message.getData().putString("name",fileName);
        message.getData().putString("type",fileType);
        handler.sendMessage(message);
    }


    private void sendData ()
    {
        Log.v(TAG, "Handle received data to send");

        try {
            handler.sendEmptyMessage(Constants.MessageType.SENDING_DATA);

            InputStream is = new FileInputStream(mMedia.mFileMedia);
            DataOutputStream outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Send the header control first
            outputStream.writeByte(Constants.HEADER_MSB);
            outputStream.writeByte(Constants.HEADER_LSB);

            // write size
            outputStream.writeLong(mMedia.mLength);

            ProgressData progressData = new ProgressData();
            progressData.totalSize = mMedia.mLength;
            progressData.remainingSize = progressData.totalSize;

            // write digest
            outputStream.writeInt(mMedia.mDigest.length);
            outputStream.write(mMedia.mDigest);

            outputStream.writeUTF(mMedia.mTitle);
            outputStream.writeUTF(mMedia.mMimeType);
            outputStream.writeUTF(mMedia.mMetadataJson);

            byte[] buffer = new byte[Constants.CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
            //    Log.v(TAG, "Read " + bytesRead + " bytes into buffer; Writing to output...");
                outputStream.write(buffer, 0, bytesRead);
                progressData.remainingSize -= bytesRead;
                sendProgress(progressData,mMedia.mTitle, mMedia.mMimeType);
            }

            outputStream.flush();

            Log.v(TAG, "Data sent.  Waiting for return digest as confirmation");
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            byte[] incomingDigest = new byte[16];
            int incomingIndex = 0;

            try {
                while (true) {
                    byte[] header = new byte[1];
                    inputStream.read(header, 0, 1);
                    incomingDigest[incomingIndex++] = header[0];
                    if (incomingIndex == 16) {
                        if (Utils.digestMatch(mMedia.mDigest,incomingDigest)) {
                            Log.d(TAG, "Digest matched OK.  Data was received OK.");
                            handler.sendEmptyMessage(Constants.MessageType.DATA_SENT_OK);
                        } else {
                            Log.d(TAG, "Digest did not match.  Might want to resend.");
                            handler.sendEmptyMessage(Constants.MessageType.DIGEST_DID_NOT_MATCH);
                        }

                        break;
                    }
                }
            } catch (Exception ex) {
                Log.e(TAG, ex.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "error reading stream", e);
        }


    }
}
