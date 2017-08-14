package info.guardianproject.nearby.nsd;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import info.guardianproject.nearby.NearbyListener;
import info.guardianproject.nearby.NearbyMedia;
import info.guardianproject.nearby.NearbySender;
import info.guardianproject.nearby.Neighbor;

/**
 * Created by n8fr8 on 8/31/16.
 */

@TargetApi(16)
@SuppressLint("NewApi")
public class NSDSender implements Runnable, NearbySender {

    public final static String SERVICE_NAME_DEFAULT = "NSDNearbyShare";
    public final static String SERVICE_TYPE = "_http._tcp.";
    public final static String SERVICE_DOWNLOAD_FILE_PATH = "/nearby/file";
    public final static String SERVICE_DOWNLOAD_METADATA_PATH = "/nearby/meta";

    private int mLocalPort;
    private Context mContext;
    private String mServiceName;
    private NsdManager.RegistrationListener mRegistrationListener;
    private NsdManager mNsdManager;

    private WebServer mWebServer;
    private NearbyMedia mShareMedia;

    private NearbyListener mNearbyListener;

    public NSDSender(Context context)
    {
        mContext = context;
    }

    public void startSharing ()
    {
        new Thread (this).start();
    }

    public void setShareFile (NearbyMedia shareMedia)
    {
        mShareMedia = shareMedia;
    }

    public void setNearbyListener (NearbyListener nearbyListener)
    {
        mNearbyListener = nearbyListener;
    }

    public void run ()
    {
        try {
            int port = registerService(mContext);
            mWebServer = new WebServer(port);
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
        }
    }

    private int registerService(Context context) throws java.io.IOException {

        int port = findOpenSocket();

        // Create the NsdServiceInfo object, and populate it.
        NsdServiceInfo serviceInfo  = new NsdServiceInfo();

        // The name is subject to change based on conflicts
        // with other services advertised on the same network.
        serviceInfo.setServiceName(SERVICE_NAME_DEFAULT);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        mNsdManager = (NsdManager)context.getSystemService(Context.NSD_SERVICE);

        if (mRegistrationListener == null)
            initializeRegistrationListener();

        mNsdManager.registerService(
                serviceInfo, NsdManager.PROTOCOL_DNS_SD, mRegistrationListener);

        return port;
    }

    private int findOpenSocket() throws java.io.IOException {
        // Initialize a server socket on the next available port.
        ServerSocket serverSocket = new ServerSocket(0);

        // Store the chosen port.
        mLocalPort =  serverSocket.getLocalPort();
        serverSocket.close();

        return mLocalPort;
    }

    private void initializeRegistrationListener() {
        mRegistrationListener = new NsdManager.RegistrationListener() {

            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                // Save the service name.  Android may have changed it in order to
                // resolve a conflict, so update the name you initially requested
                // with the name Android actually used.
                mServiceName = NsdServiceInfo.getServiceName();
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Registration failed!  Put debugging code here to determine why.
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                // Service has been unregistered.  This only happens when you call
                // NsdManager.unregisterService() and pass in this listener.
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Unregistration failed.  Put debugging code here to determine why.
            }
        };
    }

    public String getServiceName ()
    {
        return mServiceName;
    }

    public synchronized void stopSharing() {

        if (mNsdManager != null && mRegistrationListener != null) {
            mNsdManager.unregisterService(mRegistrationListener);
            mRegistrationListener = null;
        }

        if (mWebServer != null)
            mWebServer.stop();
    }

    private class WebServer extends NanoHTTPD {

        public WebServer(int port) throws java.io.IOException {
            super(port);
            start();
        }

        @Override
        public Response serve(IHTTPSession session) {

            if (mNearbyListener != null)
            {
                //Neighbor neighbor = new Neighbor(session.
                //mNearbyListener.foundNeighbor(neighbor);
            }

            if (session.getUri().endsWith(SERVICE_DOWNLOAD_FILE_PATH))
            {
                try {
                    return NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mShareMedia.mMimeType, new FileInputStream(mShareMedia.mFileMedia));
                }
                catch (IOException ioe)
                {
                    return NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR,"text/plain",ioe.getLocalizedMessage());
                }
            }
            else if (session.getUri().endsWith(SERVICE_DOWNLOAD_METADATA_PATH))
            {
                return NanoHTTPD.newFixedLengthResponse(Response.Status.OK,"text/plain",mShareMedia.mMetadataJson);

            }
            else {
                String msg = "<html><body><h1>Hello server</h1>\n";
                Map<String, String> parms = session.getParms();
                if (parms.get("username") == null) {
                    msg += "<form action='?' method='get'>\n  <p>Your name: <input type='text' name='username'></p>\n" + "</form>\n";
                } else {
                    msg += "<p>Hello, " + parms.get("username") + "!</p>";
                }
                return NanoHTTPD.newFixedLengthResponse(msg + "</body></html>\n");
            }
        }


    }


}
