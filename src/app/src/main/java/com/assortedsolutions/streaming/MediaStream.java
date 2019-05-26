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

package com.assortedsolutions.streaming;

import java.io.IOException;
import java.net.InetAddress;
import com.assortedsolutions.streaming.audio.AudioStream;
import com.assortedsolutions.streaming.rtp.AbstractPacketizer;
import com.assortedsolutions.streaming.video.VideoStream;
import android.media.MediaCodec;
import android.util.Log;

/**
 * A MediaRecorder that streams what it records using a packetizer from the RTP package.
 * You can't use this class directly!
 */
public abstract class MediaStream implements Stream
{
    protected static final String TAG = "MediaStream";

    /** Prefix that will be used for all shared preferences saved by libstreaming */
    protected static final String PREF_PREFIX = "libstreaming-";

    /** The packetizer that will read the output of the camera and send RTP packets over the network. */
    protected AbstractPacketizer packetizer = null;
    
    protected boolean streaming = false;
    protected boolean configured = false;
    protected int rtpPort = 0;
    protected int rtcpPort = 0;
    protected InetAddress destination;

    private int timeToLive = 64;

    protected MediaCodec mediaCodec;

    static
    {
        try
        {
            Class.forName("android.media.MediaCodec");
            Log.i(TAG,"Phone supports the MediaCodec API");
        }
        catch (ClassNotFoundException e)
        {
            Log.e(TAG,"Phone does not support the MediaCodec API", e);
        }
    }

    public MediaStream() {}

    /**
     * Sets the destination IP address of the stream.
     * @param dest The destination address of the stream
     */
    public void setDestinationAddress(InetAddress dest)
    {
        destination = dest;
    }

    /**
     * Sets the destination ports of the stream.
     * If an odd number is supplied for the destination port then the next
     * lower even number will be used for RTP and it will be used for RTCP.
     * If an even number is supplied, it will be used for RTP and the next odd
     * number will be used for RTCP.
     * @param dport The destination port
     */
    public void setDestinationPorts(int dport)
    {
        if (dport % 2 == 1)
        {
            rtpPort = dport-1;
            rtcpPort = dport;
        }
        else
        {
            rtpPort = dport;
            rtcpPort = dport+1;
        }
    }

    /**
     * Sets the destination ports of the stream.
     * @param rtpPort Destination port that will be used for RTP
     * @param rtcpPort Destination port that will be used for RTCP
     */
    public void setDestinationPorts(int rtpPort, int rtcpPort)
    {
        this.rtpPort = rtpPort;
        this.rtcpPort = rtcpPort;
    }

    /**
     * Sets the Time To Live of packets sent over the network.
     * @param ttl The time to live
     */
    public void setTimeToLive(int ttl)
    {
        timeToLive = ttl;
    }

    /**
     * Returns a pair of destination ports, the first one is the
     * one used for RTP and the second one is used for RTCP.
     **/
    public int[] getDestinationPorts()
    {
        return new int[] {rtpPort, rtcpPort};
    }

    /**
     * Returns a pair of source ports, the first one is the
     * one used for RTP and the second one is used for RTCP.
     **/
    public int[] getLocalPorts()
    {
        return packetizer.getRtpSocket().getLocalPorts();
    }

    /**
     * Returns the packetizer associated with the {@link MediaStream}.
     * @return The packetizer
     */
    public AbstractPacketizer getPacketizer()
    {
        return packetizer;
    }

    /**
     * Returns an approximation of the bit rate consumed by the stream in bit per second.
     */
    public long getBitrate()
    {
        return !streaming ? 0 : packetizer.getRtpSocket().getBitrate();
    }

    /**
     * Indicates if the {@link MediaStream} is streaming.
     * @return A boolean indicating if the {@link MediaStream} is streaming
     */
    public boolean isStreaming()
    {
        return streaming;
    }

    /**
     * Configures the stream with the settings supplied with
     * {@link VideoStream#setVideoQuality(com.assortedsolutions.streaming.video.VideoQuality)}
     * for a {@link VideoStream} and {@link AudioStream#setAudioQuality(com.assortedsolutions.streaming.audio.AudioQuality)}
     * for a {@link AudioStream}.
     */
    public synchronized void configure() throws IllegalStateException, IOException
    {
        if (streaming)
        {
            throw new IllegalStateException("Configure cannot be called while streaming.");
        }

        if (packetizer != null)
        {
            packetizer.setDestination(destination, rtpPort, rtcpPort);
        }

        configured = true;
    }

    public synchronized void start() throws IllegalStateException, IOException
    {
        if (destination == null)
        {
            throw new IllegalStateException("No destination ip address set for the stream !");
        }

        if (rtpPort <= 0 || rtcpPort <= 0)
        {
            throw new IllegalStateException("No destination ports set for the stream !");
        }

        packetizer.setTimeToLive(timeToLive);

        encodeWithMediaCodec();
    }

    public synchronized void stop()
    {
        if (!streaming)
        {
            return;
        }

        try
        {
            packetizer.stop();
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec = null;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Stopping threw", e);
        }

        streaming = false;
    }

    protected abstract void encodeWithMediaCodec() throws IOException;

    /**
     * Returns a description of the stream using SDP.
     * This method can only be called after {@link Stream#configure()}.
     * @throws IllegalStateException Thrown when {@link Stream#configure()} was not called.
     */
    public abstract String getSessionDescription();

    /**
     * Returns the SSRC of the underlying {@link com.assortedsolutions.streaming.rtp.RtpSocket}.
     * @return the SSRC of the stream
     */
    public int getSSRC()
    {
        return getPacketizer().getSSRC();
    }
}
