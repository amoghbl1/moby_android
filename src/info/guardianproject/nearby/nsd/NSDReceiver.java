package info.guardianproject.nearby.nsd;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Date;

import info.guardianproject.nearby.NearbyListener;
import info.guardianproject.nearby.NearbyMedia;
import info.guardianproject.nearby.Neighbor;
import okio.BufferedSink;
import okio.Okio;



/**
 * Created by n8fr8 on 8/31/16.
 */
@TargetApi(16)
@SuppressLint("NewApi")
public class NSDReceiver {

    private final static String TAG = "NearbyNSD";

    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdManager.ResolveListener mResolveListener;
    private NsdManager mNsdManager;

    private NearbyListener mNearbyListener;

    private String mClientId = "";

    public NSDReceiver(Context context) {
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);

        initializeDiscoveryListener();

        mClientId = getWifiAddress(context);
    }

    private String getWifiAddress (Context context)
    {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();
        return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));

    }

    public void startDiscovery() {
        mNsdManager.discoverServices(
                NSDSender.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);

    }

    public void stopDiscovery() {
        mNsdManager.stopServiceDiscovery(mDiscoveryListener);

    }

    private void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(NSDSender.SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    //    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());

                } else if (service.getServiceName().contains(NSDSender.SERVICE_NAME_DEFAULT)) {

                    mNsdManager.resolveService(service, new NsdManager.ResolveListener() {

                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                            // Called when the resolve fails.  Use the error code to debug.
                            Log.e(TAG, "Resolve failed" + errorCode);
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo serviceInfo) {
                            Log.e(TAG, "Resolve Succeeded. " + serviceInfo);

                            int port = serviceInfo.getPort();
                            InetAddress host = serviceInfo.getHost();

                            Neighbor neighbor = new Neighbor(host.getHostAddress(),host.getHostName(),Neighbor.TYPE_WIFI_NSD);
                            if (mNearbyListener != null)
                                mNearbyListener.foundNeighbor(neighbor);

                            connect(host, port);
                        }
                    });
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost" + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    private void connect(InetAddress host, int port) {
        //connect via HTTP to the host and port and download the file

        StringBuilder sbUrl = new StringBuilder();
        sbUrl.append("http://");
        sbUrl.append(host.getHostName());
        sbUrl.append(":").append(port);
        sbUrl.append(NSDSender.SERVICE_DOWNLOAD_FILE_PATH);

        final ProgressListener progressListener = new ProgressListener() {
            @Override public void update(long bytesRead, long contentLength, boolean done) {
                System.out.println(bytesRead);
                System.out.println(contentLength);
                System.out.println(done);
                System.out.format("%d%% done\n", (100 * bytesRead) / contentLength);

             //   if (mNearbyListener != null)
               //     mNearbyListener.transferProgress(neighbor, fileOut, fileName, mimeType, 0, Long.parseLong(response.header("Content-Length","0")));

            }
        };

        OkHttpClient client = new OkHttpClient();
        /**
        client.addNetworkInterceptor(new Interceptor() {
            @Override public Response intercept(Chain chain) throws IOException {
                Response originalResponse = chain.proceed(chain.request());
                return originalResponse.newBuilder()
                        .body(new ProgressResponseBody(originalResponse.body(), progressListener))
                        .build();
            }
        })*/

        Request request = new Request.Builder().url(sbUrl.toString())
                .addHeader("NearbyClientId",mClientId)

                .build();



        try {
            Response response = client.newCall(request).execute();

            NearbyMedia media = new NearbyMedia();

            media.mMimeType = response.header("Content-Type", "text/plain");

            media.mTitle = new Date().getTime() + "";

            String fileExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(media.mMimeType);

            if (fileExt == null)
            {
                if (media.mMimeType.startsWith("image"))
                    fileExt = "jpg";
                else if (media.mMimeType.startsWith("video"))
                    fileExt = "mp4";
                else if (media.mMimeType.startsWith("audio"))
                    fileExt = "m4a";

            }

            media.mTitle += "." + fileExt;

            File dirDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File fileOut = new File(dirDownloads, new Date().getTime() + "." + media.mTitle);

            media.mFileMedia = fileOut;

            Neighbor neighbor = new Neighbor(host.getHostAddress(),host.getHostName(),Neighbor.TYPE_WIFI_NSD);

            if (mNearbyListener != null)
                mNearbyListener.transferProgress(neighbor, fileOut, media.mTitle, media.mMimeType, 50, Long.parseLong(response.header("Content-Length","0")));

            InputStream inputStream = response.body().byteStream();

            BufferedSink sink = Okio.buffer(Okio.sink(fileOut));
            sink.writeAll(response.body().source());
            sink.close();

            //now get the metadata
            sbUrl = new StringBuilder();
            sbUrl.append("http://");
            sbUrl.append(host.getHostName());
            sbUrl.append(":").append(port);
            sbUrl.append(NSDSender.SERVICE_DOWNLOAD_METADATA_PATH);

            request = new Request.Builder().url(sbUrl.toString())
                    .addHeader("NearbyClientId",mClientId)
                    .build();
            response = client.newCall(request).execute();
            inputStream = response.body().byteStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            sink = Okio.buffer(Okio.sink(baos));
            sink.writeAll(response.body().source());
            sink.close();

            media.mMetadataJson = new String(baos.toByteArray());


            if (mNearbyListener != null)
                mNearbyListener.transferComplete(neighbor, media);


        } catch (IOException ioe) {
            Log.e(TAG, "Unable to connect to url: " + sbUrl.toString(), ioe);
        }
    }


    public void setListener(NearbyListener nearbyListener)
    {
        mNearbyListener = nearbyListener;
    }

    /**
    private static class ProgressResponseBody extends ResponseBody {

        private final ResponseBody responseBody;
        private final ProgressListener progressListener;
        private BufferedSource bufferedSource;

        public ProgressResponseBody(ResponseBody responseBody, ProgressListener progressListener) {
            this.responseBody = responseBody;
            this.progressListener = progressListener;
        }

        @Override public MediaType contentType() {
            return responseBody.contentType();
        }

        @Override public long contentLength() {
            return responseBody.contentLength();
        }

        @Override public BufferedSource source() {
            if (bufferedSource == null) {
                bufferedSource = Okio.buffer(source(responseBody.source()));
            }
            return bufferedSource;
        }

        private Source source(Source source) {
            return new ForwardingSource(source) {
                long totalBytesRead = 0L;

                @Override public long read(Buffer sink, long byteCount) throws IOException {
                    long bytesRead = super.read(sink, byteCount);
                    // read() returns the number of bytes read, or -1 if this source is exhausted.
                    totalBytesRead += bytesRead != -1 ? bytesRead : 0;
                    progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1);
                    return bytesRead;
                }
            };
        }
    }*/

    interface ProgressListener {
        void update(long bytesRead, long contentLength, boolean done);
    }

}
