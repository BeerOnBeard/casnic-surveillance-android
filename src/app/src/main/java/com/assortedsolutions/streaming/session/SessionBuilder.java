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

package com.assortedsolutions.streaming.session;

import java.io.IOException;
import com.assortedsolutions.streaming.audio.AACStream;
import com.assortedsolutions.streaming.audio.AudioQuality;
import com.assortedsolutions.streaming.video.H264Stream;
import com.assortedsolutions.streaming.video.VideoQuality;
import android.content.Context;
import android.hardware.Camera.CameraInfo;
import android.preference.PreferenceManager;
import android.view.SurfaceView;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public final class SessionBuilder
{
    public final static String TAG = "SessionBuilder";

    // Default configuration
    private VideoQuality videoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
    private AudioQuality audioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;
    private int camera = CameraInfo.CAMERA_FACING_BACK;
    private int timeToLive = 64;
    private int orientation = 0;
    private SurfaceView surfaceView = null;
    private String origin = null;
    private String destination = null;
    private Callback callback = null;
    private Context context;

    // Removes the default public constructor
    private SessionBuilder() {}

    // The SessionManager implements the singleton pattern
    private static SessionBuilder builder = new SessionBuilder();

    /**
     * Returns a reference to the {@link SessionBuilder}.
     * @return The reference to the {@link SessionBuilder}
     */
    public static SessionBuilder getInstance()
    {
        return builder;
    }

    /*****************************
     * Fluent builder methods    *
     *****************************/

    /**
     * Access to the context is needed for the H264Stream class to store some stuff in the SharedPreferences.
     * Note that you should pass the Application context, not the context of an Activity.
     **/
    public SessionBuilder setContext(Context context)
    {
        this.context = context;
        return this;
    }

    /** Sets the video stream quality. */
    public SessionBuilder setVideoQuality(VideoQuality quality)
    {
        videoQuality = quality.clone();
        return this;
    }

    /** Sets the audio quality. */
    public SessionBuilder setAudioQuality(AudioQuality quality)
    {
        audioQuality = quality.clone();
        return this;
    }

    /** Sets the SurfaceView required to preview the video stream. */
    public SessionBuilder setSurfaceView(SurfaceView surfaceView)
    {
        this.surfaceView = surfaceView;
        return this;
    }

    public SessionBuilder setCallback(Callback callback)
    {
        this.callback = callback;
        return this;
    }

    /*******************************
     * Instance methods            *
     *******************************/

    /**
     * Creates a new {@link Session}.
     * @return The new Session
     * @throws IOException
     */
    public Session build()
    {
        Session session;
        session = new Session();
        session.setOrigin(origin);
        session.setDestination(destination);
        session.setTimeToLive(timeToLive);
        session.setCallback(callback);

        AACStream aacStream = new AACStream();
        aacStream.setAudioQuality(audioQuality);
        aacStream.setDestinationPorts(5004);
        session.addAudioTrack(aacStream); // TODO: Hard-coded port?

        H264Stream h264Stream = new H264Stream(camera);
        h264Stream.setVideoQuality(videoQuality);
        h264Stream.setSurfaceView(surfaceView);
        h264Stream.setPreviewOrientation(orientation);
        h264Stream.setDestinationPorts(5006);

        if (context != null)
        {
            // TODO: Get rid of preferences? Try not setting context
            h264Stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(context));
        }

        session.addVideoTrack(h264Stream);

        return session;
    }
}
