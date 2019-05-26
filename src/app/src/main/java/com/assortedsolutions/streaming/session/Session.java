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
import java.net.InetAddress;
import java.net.UnknownHostException;

import com.assortedsolutions.streaming.Stream;
import com.assortedsolutions.streaming.audio.AudioStream;
import com.assortedsolutions.streaming.exceptions.CameraInUseException;
import com.assortedsolutions.streaming.exceptions.ConfNotSupportedException;
import com.assortedsolutions.streaming.exceptions.InvalidSurfaceException;
import com.assortedsolutions.streaming.exceptions.StorageUnavailableException;
import com.assortedsolutions.streaming.video.VideoStream;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

/**
 * You should instantiate this class with the {@link SessionBuilder}.<br />
 * This is the class you will want to use to stream audio and or video to some peer using RTP.<br />
 *
 * It holds a {@link VideoStream} and a {@link AudioStream} together and provides
 * synchronous and asynchronous functions to start and stop those steams.
 * You should implement a callback interface {@link Callback} to receive notifications and error reports.<br />
 *
 * If you don't use the RTSP protocol, you will still need to send a session description to the receiver
 * for him to be able to decode your audio/video streams. You can obtain this session description by calling
 * {@link #configure()} to configure the session with its parameters
 * (audio samplingrate, video resolution) and then {@link Session#getSessionDescription()}.<br />
 *
 * See the example 2 here: https://github.com/fyhertz/libstreaming-examples to
 * see an example of how to get a SDP.<br />
 *
 * See the example 3 here: https://github.com/fyhertz/libstreaming-examples to
 * see an example of how to stream to a RTSP server.<br />
 *
 */
public class Session
{
    public final static String TAG = "Session";

    /** Some app is already using a camera (Camera.open() has failed). */
    public final static int ERROR_CAMERA_ALREADY_IN_USE = 0x00;

    /** The phone may not support some streaming parameters that you are trying to use (bit rate, frame rate, resolution...). */
    public final static int ERROR_CONFIGURATION_NOT_SUPPORTED = 0x01;

    /**
     * The internal storage of the phone is not ready.
     * libstreaming tried to store a test file on the sdcard but couldn't.
     * See H264Stream and AACStream to find out why libstreaming would want to something like that.
     */
    public final static int ERROR_STORAGE_NOT_READY = 0x02;

    /** The supplied SurfaceView is not a valid surface, or has not been created yet. */
    public final static int ERROR_INVALID_SURFACE = 0x04;

    /**
     * The destination set with {@link Session#setDestination} could not be resolved.
     * May mean that the phone has no access to the internet, or that the DNS server could not
     * resolved the host name.
     */
    public final static int ERROR_UNKNOWN_HOST = 0x05;

    /**
     * Some other error occurred !
     */
    public final static int ERROR_OTHER = 0x06;

    private String origin;
    private String destination;
    private int timeToLive = 64;
    private long timestamp;

    private AudioStream audioStream = null;
    private VideoStream videoStream = null;

    private Callback callback;
    private Handler mainHandler;
    private Handler handler;

    /**
     * Creates a streaming session that can be customized by adding streams.
     */
    Session()
    {
        long uptime = System.currentTimeMillis();

        HandlerThread thread = new HandlerThread("com.assortedsolutions.streaming.Session");
        thread.start();

        handler = new Handler(thread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        timestamp = (uptime / 1000) << 32 & (((uptime - ((uptime / 1000) * 1000)) >> 32) / 1000); // NTP timestamp
        origin = "127.0.0.1";
    }

    public void addAudioStream(AudioStream stream)
    {
        removeAudioStream();
        audioStream = stream;
    }

    public AudioStream getAudioStream()
    {
        return audioStream;
    }

    public void removeAudioStream()
    {
        if (audioStream == null)
        {
            return;
        }

        audioStream.stop();
        audioStream = null;
    }

    public void addVideoStream(VideoStream stream)
    {
        removeVideoStream();
        videoStream = stream;
    }

    public VideoStream getVideoStream()
    {
        return videoStream;
    }

    public void removeVideoStream()
    {
        if (videoStream == null)
        {
            return;
        }

        videoStream.stopPreview();
        videoStream = null;
    }

    public boolean streamExists(int id)
    {
        if (id == 0)
        {
            return audioStream != null;
        }

        return videoStream != null;
    }

    public Stream getStream(int id)
    {
        if (id == 0)
        {
            return audioStream;
        }

        return videoStream;
    }

    /**
     * Sets the callback interface that will be called by the {@link Session}.
     * @param callback The implementation of the {@link Callback} interface
     */
    public void setCallback(Callback callback)
    {
        this.callback = callback;
    }

    /**
     * The origin address of the session.
     * It appears in the session description.
     * @param origin The origin address
     */
    public void setOrigin(String origin)
    {
        this.origin = origin;
    }

    /**
     * The destination address for all the streams of the session. <br />
     * Changes will be taken into account the next time you start the session.
     * @param destination The destination address
     */
    public void setDestination(String destination)
    {
        Log.e(TAG, "Destination set to " + destination);
        this.destination =  destination;
    }

    /** Returns the destination set with {@link #setDestination}. */
    public String getDestination()
    {
        return destination;
    }

    /**
     * Set the TTL of all packets sent during the session. <br />
     * Changes will be taken into account the next time you start the session.
     * @param ttl The Time To Live
     */
    public void setTimeToLive(int ttl)
    {
        timeToLive = ttl;
    }

    /**
     * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
     * @return The Session Description.
     */
    public String getSessionDescription()
    {
        if (destination == null)
        {
            throw new IllegalStateException("setDestination() has not been called");
        }

        StringBuilder sessionDescription = new StringBuilder();
        sessionDescription.append("v=0\r\n");

        // TODO: Add IPV6 support
        sessionDescription.append("o=- ").append(timestamp).append(" ").append(timestamp).append(" IN IP4 ").append(origin).append("\r\n");
        sessionDescription.append("s=Unnamed\r\n");
        sessionDescription.append("i=N/A\r\n");
        sessionDescription.append("c=IN IP4 ").append(destination).append("\r\n");

        // t=0 0 means the session is permanent (we don't know when it will stop)
        sessionDescription.append("t=0 0\r\n");
        sessionDescription.append("a=recvonly\r\n");

        // Prevents two different sessions from using the same peripheral at the same time
        if (audioStream != null)
        {
            sessionDescription.append(audioStream.getSessionDescription());
            sessionDescription.append("a=control:trackID=0\r\n");
        }

        if (videoStream != null)
        {
            sessionDescription.append(videoStream.getSessionDescription());
            sessionDescription.append("a=control:trackID=1\r\n");
        }

        return sessionDescription.toString();
    }

    /** Returns an approximation of the bandwidth consumed by the session in bit per second. */
    public long getBitrate()
    {
        return (audioStream == null ? 0 : audioStream.getBitrate()) + (videoStream == null ? 0 : videoStream.getBitrate());
    }

    /** Indicates if a stream is currently running. */
    public boolean isStreaming()
    {
        return (audioStream != null && audioStream.isStreaming()) || (videoStream != null && videoStream.isStreaming());
    }

    /**
     * Configures all streams of the session.
     * Throws exceptions in addition to calling a callback
     * {@link Callback#onSessionError} when an error occurs.
     **/
    public void configure() throws RuntimeException, IOException
    {
        for (int id = 0; id < 2; id++)
        {
            Stream stream = id == 0 ? audioStream : videoStream;
            if (stream != null && !stream.isStreaming())
            {
                try
                {
                    stream.configure();
                }
                catch (CameraInUseException e)
                {
                    postError(ERROR_CAMERA_ALREADY_IN_USE , id, e);
                    throw e;
                }
                catch (StorageUnavailableException e)
                {
                    postError(ERROR_STORAGE_NOT_READY , id, e);
                    throw e;
                }
                catch (ConfNotSupportedException e)
                {
                    postError(ERROR_CONFIGURATION_NOT_SUPPORTED , id, e);
                    throw e;
                }
                catch (InvalidSurfaceException e)
                {
                    postError(ERROR_INVALID_SURFACE , id, e);
                    throw e;
                }
                catch (IOException e)
                {
                    postError(ERROR_OTHER, id, e);
                    throw e;
                }
                catch (RuntimeException e)
                {
                    postError(ERROR_OTHER, id, e);
                    throw e;
                }
            }
        }

        postSessionConfigured();
    }

    /**
     * Starts a stream in a synchronous manner. <br />
     * Throws exceptions in addition to calling a callback.
     * @param id The id of the stream to start
     **/
    public void start(int id) throws CameraInUseException, ConfNotSupportedException, InvalidSurfaceException, IOException
    {
        Stream stream = id == 0 ? audioStream : videoStream;
        if (stream != null && !stream.isStreaming())
        {
            try
            {
                InetAddress destination = InetAddress.getByName(this.destination);
                stream.setTimeToLive(timeToLive);
                stream.setDestinationAddress(destination);
                stream.start();

                if (getStream(1 - id) == null || getStream(1 - id).isStreaming())
                {
                    postSessionStarted();
                }

                if (getStream(1 - id) == null || !getStream(1 - id).isStreaming())
                {
                    handler.post(updateBitrate);
                }
            }
            catch (UnknownHostException e)
            {
                postError(ERROR_UNKNOWN_HOST, id, e);
                throw e;
            }
            catch (CameraInUseException e)
            {
                postError(ERROR_CAMERA_ALREADY_IN_USE , id, e);
                throw e;
            }
            catch (StorageUnavailableException e)
            {
                postError(ERROR_STORAGE_NOT_READY , id, e);
                throw e;
            }
            catch (ConfNotSupportedException e)
            {
                postError(ERROR_CONFIGURATION_NOT_SUPPORTED , id, e);
                throw e;
            }
            catch (InvalidSurfaceException e)
            {
                postError(ERROR_INVALID_SURFACE , id, e);
                throw e;
            }
            catch (IOException e)
            {
                postError(ERROR_OTHER, id, e);
                throw e;
            }
            catch (RuntimeException e)
            {
                postError(ERROR_OTHER, id, e);
                throw e;
            }
        }
    }

    /** Stops all existing streams in a synchronous manner. */
    public void stop()
    {
        stop(0);
        stop(1);
        postSessionStopped();
    }

    /**
     * Stops one stream in a synchronous manner.
     * @param id The id of the stream to stop
     **/
    private void stop(final int id)
    {
        Stream stream = id == 0 ? audioStream : videoStream;
        if (stream != null)
        {
            stream.stop();
        }
    }

    /** Deletes all existing streams & release associated resources. */
    public void release()
    {
        removeAudioStream();
        removeVideoStream();
        handler.getLooper().quit();
    }

    private void postSessionConfigured()
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (callback != null)
                {
                    callback.onSessionConfigured();
                }
            }
        });
    }

    private void postSessionStarted()
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (callback != null)
                {
                    callback.onSessionStarted();
                }
            }
        });
    }

    private void postSessionStopped()
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (callback != null)
                {
                    callback.onSessionStopped();
                }
            }
        });
    }

    private void postError(final int reason, final int streamType, final Exception e)
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                if (callback != null)
                {
                    callback.onSessionError(reason, streamType, e);
                }
            }
        });
    }

    private void postBitRate(final long bitrate)
    {
        mainHandler.post(new Runnable()
        {
            @Override
            public void run() {
                if (callback != null)
                {
                    callback.onBitrateUpdate(bitrate);
                }
            }
        });
    }

    private Runnable updateBitrate = new Runnable()
    {
        @Override
        public void run()
        {
            if (isStreaming())
            {
                postBitRate(getBitrate());
                handler.postDelayed(updateBitrate, 500);
            }
            else
            {
                postBitRate(0);
            }
        }
    };
}
