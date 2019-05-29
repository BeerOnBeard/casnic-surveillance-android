package com.assortedsolutions.streaming.rtsp;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Implementation of a subset of the RTSP protocol (RFC 2326).
 *
 * It allows remote control of an android device cameras & microphone.
 * For each connected client, a Session is instantiated.
 * The Session will start or stop streams according to what the client wants.
 *
 */
public class RtspService extends Service
{
    public final static String TAG = "RtspService";
    public final static String EXTRA_KEY_USERNAME = "com.assortedsolutions.streaming.username";
    public final static String EXTRA_KEY_PASSWORD = "com.assortedsolutions.streaming.password";

    protected int requestListenerPort = 8086;
    private RequestListener requestListener;

    private String username = null;
    private String password = null;

    public RtspService() {}

    /****************************************
     *  android.Service implementation      *
     ****************************************/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        username = intent.getStringExtra(EXTRA_KEY_USERNAME);
        password = intent.getStringExtra(EXTRA_KEY_PASSWORD);

        start();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDestroy()
    {
        stop();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        // Binding is not supported
        return null;
    }

    /**************************************
     *  Service methods                    *
     **************************************/

    /**
     * Starts the service that listens for requests.
     */
    public void start()
    {
        if (requestListener != null)
        {
            Log.i(TAG, "Start was called, but the service is already running");
            return;
        }

        try
        {
            requestListener = new RequestListener(requestListenerPort, username, password);
        }
        catch (Exception e)
        {
            Log.e(TAG, "Creating request listener failed", e);
            requestListener = null;
        }
    }

    /**
     * Stops the service that listens for requests.
     * To stop the Android Service you need to call {@link android.content.Context#stopService(Intent)};
     */
    public void stop()
    {
        if (requestListener == null)
        {
            Log.i(TAG, "Stop was called, but there is no running service");
            return;
        }

        try
        {
            requestListener.kill();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Stopping the session threw", e);
        }
        finally
        {
            requestListener = null;
        }
    }
}
