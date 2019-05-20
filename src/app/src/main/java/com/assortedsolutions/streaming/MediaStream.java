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
import java.io.OutputStream;
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

    /** The packetizer that will read the output of the camera and send RTP packets over the networked. */
    protected AbstractPacketizer mPacketizer = null;
    
    protected boolean mStreaming = false;
    protected boolean mConfigured = false;
    protected int mRtpPort = 0;
    protected int mRtcpPort = 0;
    protected byte mChannelIdentifier = 0;
    protected OutputStream mOutputStream = null;
    protected InetAddress mDestination;

    private int mTTL = 64;

    protected MediaCodec mMediaCodec;

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
        mDestination = dest;
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
            mRtpPort = dport-1;
            mRtcpPort = dport;
        }
        else
        {
            mRtpPort = dport;
            mRtcpPort = dport+1;
        }
    }

    /**
     * Sets the destination ports of the stream.
     * @param rtpPort Destination port that will be used for RTP
     * @param rtcpPort Destination port that will be used for RTCP
     */
    public void setDestinationPorts(int rtpPort, int rtcpPort)
    {
        mRtpPort = rtpPort;
        mRtcpPort = rtcpPort;
        mOutputStream = null;
    }

    /**
     * If a TCP is used as the transport protocol for the RTP session,
     * the output stream to which RTP packets will be written to must
     * be specified with this method.
     */
    public void setOutputStream(OutputStream stream, byte channelIdentifier)
    {
        mOutputStream = stream;
        mChannelIdentifier = channelIdentifier;
    }

    /**
     * Sets the Time To Live of packets sent over the network.
     * @param ttl The time to live
     */
    public void setTimeToLive(int ttl)
    {
        mTTL = ttl;
    }

    /**
     * Returns a pair of destination ports, the first one is the
     * one used for RTP and the second one is used for RTCP.
     **/
    public int[] getDestinationPorts()
    {
        return new int[] { mRtpPort, mRtcpPort };
    }

    /**
     * Returns a pair of source ports, the first one is the
     * one used for RTP and the second one is used for RTCP.
     **/
    public int[] getLocalPorts()
    {
        return mPacketizer.getRtpSocket().getLocalPorts();
    }

    /**
     * Returns the packetizer associated with the {@link MediaStream}.
     * @return The packetizer
     */
    public AbstractPacketizer getPacketizer()
    {
        return mPacketizer;
    }

    /**
     * Returns an approximation of the bit rate consumed by the stream in bit per seconde.
     */
    public long getBitrate()
    {
        return !mStreaming ? 0 : mPacketizer.getRtpSocket().getBitrate();
    }

    /**
     * Indicates if the {@link MediaStream} is streaming.
     * @return A boolean indicating if the {@link MediaStream} is streaming
     */
    public boolean isStreaming()
    {
        return mStreaming;
    }

    /**
     * Configures the stream with the settings supplied with
     * {@link VideoStream#setVideoQuality(com.assortedsolutions.streaming.video.VideoQuality)}
     * for a {@link VideoStream} and {@link AudioStream#setAudioQuality(com.assortedsolutions.streaming.audio.AudioQuality)}
     * for a {@link AudioStream}.
     */
    public synchronized void configure() throws IllegalStateException, IOException
    {
        if (mStreaming)
        {
            throw new IllegalStateException("Configure cannot be called while streaming.");
        }

        if (mPacketizer != null)
        {
            mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
            mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
        }

        mConfigured = true;
    }

    public synchronized void start() throws IllegalStateException, IOException
    {
        if (mDestination == null)
        {
            throw new IllegalStateException("No destination ip address set for the stream !");
        }

        if (mRtpPort <= 0 || mRtcpPort <= 0)
        {
            throw new IllegalStateException("No destination ports set for the stream !");
        }

        mPacketizer.setTimeToLive(mTTL);

        encodeWithMediaCodec();
    }

    public synchronized void stop()
    {
        if (!mStreaming)
        {
            return;
        }

        try
        {
            mPacketizer.stop();
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        catch (Exception e)
        {
            Log.e(TAG, "Stopping threw", e);
        }

        mStreaming = false;
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
