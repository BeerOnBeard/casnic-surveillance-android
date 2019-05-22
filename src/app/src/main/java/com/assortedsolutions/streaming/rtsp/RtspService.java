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
    public final static String EXTRA_KEY_USERNAME = "USERNAME";
    public final static String EXTRA_KEY_PASSWORD = "PASSWORD";

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
     *  Server methods                    *
     **************************************/

    /**
     * Starts the RTSP server.
     */
    public void start()
    {
        if (requestListener == null)
        {
            try
            {
                Log.d(TAG, "Starting request listener with username:password " + username + ":" + password);
                requestListener = new RequestListener(requestListenerPort, username, password);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Creating request listener failed", e);
                requestListener = null;
            }
        }
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
