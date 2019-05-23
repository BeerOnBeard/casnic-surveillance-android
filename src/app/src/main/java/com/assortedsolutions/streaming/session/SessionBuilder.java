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
import com.assortedsolutions.streaming.audio.AudioStream;
import com.assortedsolutions.streaming.video.H264Stream;
import com.assortedsolutions.streaming.video.VideoQuality;
import com.assortedsolutions.streaming.video.VideoStream;
import android.content.Context;
import android.hardware.Camera.CameraInfo;
import android.preference.PreferenceManager;
import android.view.SurfaceView;

/**
 * Call {@link #getInstance()} to get access to the SessionBuilder.
 */
public class SessionBuilder
{
    public final static String TAG = "SessionBuilder";

    /** Can be used with {@link #setVideoEncoder}. */
    public final static int VIDEO_NONE = 0;

    /** Can be used with {@link #setVideoEncoder}. */
    public final static int VIDEO_H264 = 1;

    /** Can be used with {@link #setAudioEncoder}. */
    public final static int AUDIO_NONE = 0;

    /** Can be used with {@link #setAudioEncoder}. */
    public final static int AUDIO_AAC = 5;

    // Default configuration
    private VideoQuality videoQuality = VideoQuality.DEFAULT_VIDEO_QUALITY;
    private AudioQuality audioQuality = AudioQuality.DEFAULT_AUDIO_QUALITY;
    private Context context;
    private int videoEncoder = VIDEO_H264;
    private int audioEncoder = AUDIO_AAC;
    private int camera = CameraInfo.CAMERA_FACING_BACK;
    private int timeToLive = 64;
    private int orientation = 0;
    private boolean isFlashEnabled = false;
    private SurfaceView surfaceView = null;
    private String origin = null;
    private String destination = null;
    private Callback callback = null;

    // Removes the default public constructor
    private SessionBuilder() {}

    // The SessionManager implements the singleton pattern
    private static volatile SessionBuilder builder = null;

    /**
     * Returns a reference to the {@link SessionBuilder}.
     * @return The reference to the {@link SessionBuilder}
     */
    public final static SessionBuilder getInstance()
    {
        // TODO: Does this need to be a singleton if there is a .clone() method?
        if (builder == null)
        {
            synchronized (SessionBuilder.class)
            {
                if (builder == null)
                {
                    SessionBuilder.builder = new SessionBuilder();
                }
            }
        }

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

    /** Sets the destination of the session. */
    public SessionBuilder setDestination(String destination)
    {
        this.destination = destination;
        return this;
    }

    /** Sets the origin of the session. It appears in the SDP of the session. */
    public SessionBuilder setOrigin(String origin)
    {
        this.origin = origin;
        return this;
    }

    /** Sets the video stream quality. */
    public SessionBuilder setVideoQuality(VideoQuality quality)
    {
        videoQuality = quality.clone();
        return this;
    }

    /** Sets the audio encoder. */
    public SessionBuilder setAudioEncoder(int encoder)
    {
        audioEncoder = encoder;
        return this;
    }

    /** Sets the audio quality. */
    public SessionBuilder setAudioQuality(AudioQuality quality)
    {
        audioQuality = quality.clone();
        return this;
    }

    /** Sets the default video encoder. */
    public SessionBuilder setVideoEncoder(int encoder)
    {
        videoEncoder = encoder;
        return this;
    }

    public SessionBuilder setFlashEnabled(boolean enabled)
    {
        isFlashEnabled = enabled;
        return this;
    }

    public SessionBuilder setCamera(int camera)
    {
        this.camera = camera;
        return this;
    }

    public SessionBuilder setTimeToLive(int ttl)
    {
        timeToLive = ttl;
        return this;
    }

    /**
     * Sets the SurfaceView required to preview the video stream.
     **/
    public SessionBuilder setSurfaceView(SurfaceView surfaceView)
    {
        this.surfaceView = surfaceView;
        return this;
    }

    /**
     * Sets the orientation of the preview.
     * @param orientation The orientation of the preview
     */
    public SessionBuilder setPreviewOrientation(int orientation)
    {
        this.orientation = orientation;
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

        switch (audioEncoder)
        {
            case AUDIO_AAC:
                AACStream stream = new AACStream();
                session.addAudioTrack(stream);
                break;
        }

        switch (videoEncoder)
        {
            case VIDEO_H264:
                H264Stream stream = new H264Stream(camera);
                if (context != null)
                {
                    stream.setPreferences(PreferenceManager.getDefaultSharedPreferences(context));
                }

                session.addVideoTrack(stream);
                break;
        }

        if (session.getVideoTrack() != null)
        {
            VideoStream video = session.getVideoTrack();
            video.setFlashState(isFlashEnabled);
            video.setVideoQuality(videoQuality);
            video.setSurfaceView(surfaceView);
            video.setPreviewOrientation(orientation);
            video.setDestinationPorts(5006); // TODO: Hard-coded port?
        }

        if (session.getAudioTrack() != null)
        {
            AudioStream audio = session.getAudioTrack();
            audio.setAudioQuality(audioQuality);
            audio.setDestinationPorts(5004); // TODO: Hard-coded port?
        }

        return session;
    }

    /** Returns the audio encoder set with {@link #setAudioEncoder(int)}. */
    public int getAudioEncoder()
    {
        return audioEncoder;
    }

    /** Returns the video encoder set with {@link #setVideoEncoder(int)}. */
    public int getVideoEncoder()
    {
        return videoEncoder;
    }

    /** Returns a new {@link SessionBuilder} with the same configuration. */
    public SessionBuilder clone()
    {
        // TODO: This seems to break the singleton pattern noted at the top. Why?
        return new SessionBuilder()
                .setDestination(destination)
                .setOrigin(origin)
                .setSurfaceView(surfaceView)
                .setPreviewOrientation(orientation)
                .setVideoQuality(videoQuality)
                .setVideoEncoder(videoEncoder)
                .setFlashEnabled(isFlashEnabled)
                .setCamera(camera)
                .setTimeToLive(timeToLive)
                .setAudioEncoder(audioEncoder)
                .setAudioQuality(audioQuality)
                .setContext(context)
                .setCallback(callback);
    }
}
