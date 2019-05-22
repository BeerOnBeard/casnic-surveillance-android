/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.assortedsolutions.streaming.rtsp;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
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
public class RtspServer extends Service
{
    public final static String TAG = "RtspServer";

    /** Port used by default. */
    public static final int DEFAULT_RTSP_PORT = 8086;

    protected boolean isEnabled = true;
    protected int requestListenerPort = DEFAULT_RTSP_PORT;

    private RequestListener requestListener;
    private final IBinder mBinder = new LocalBinder();
    private boolean shouldRestart = false;

    /** Credentials for Basic Auth */
    private String mUsername;
    private String mPassword;

    public RtspServer() {}

    /****************************************
     *  android.Service implementation      *
     ****************************************/

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate()
    {
        Log.d(TAG, "Creating...");

        start();
    }

    @Override
    public void onDestroy()
    {
        Log.d(TAG, "Destroying...");

        stop();
    }

    /** The Binder you obtain when a connection with the Service is established. */
    public class LocalBinder extends Binder
    {
        public RtspServer getService()
        {
            return RtspServer.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.d(TAG, "Intent bound");
        return mBinder;
    }

    /**************************************
     *  Server methods                    *
     **************************************/

    /**
     * Set Basic authorization to access RTSP Stream
     * @param username username
     * @param password password
     */
    public void setAuthorization(String username, String password)
    {
        mUsername = username;
        mPassword = password;
    }
    /**
     * Starts (or restart if needed, if for example the configuration
     * of the server has been modified) the RTSP server.
     */
    public void start()
    {
        if (!isEnabled || shouldRestart)
        {
            stop();
        }

        if (isEnabled && requestListener == null)
        {
            try
            {
                requestListener = new RequestListener(requestListenerPort);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Creating request listener failed", e);
                requestListener = null;
            }
        }

        shouldRestart = false;
    }

    /**
     * Stops the RTSP server but not the Android Service.
     * To stop the Android Service you need to call {@link android.content.Context#stopService(Intent)};
     */
    public void stop()
    {
        if (requestListener != null)
        {
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
}
