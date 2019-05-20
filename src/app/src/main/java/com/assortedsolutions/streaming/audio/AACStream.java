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

package com.assortedsolutions.streaming.audio;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import com.assortedsolutions.streaming.SessionBuilder;
import com.assortedsolutions.streaming.rtp.AACLATMPacketizer;
import com.assortedsolutions.streaming.rtp.MediaCodecInputStream;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Log;

/**
 * A class for streaming AAC from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress(InetAddress)}, {@link #setDestinationPorts(int)} and {@link #setAudioQuality(AudioQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class AACStream extends AudioStream
{
    public final static String TAG = "AACStream";

    /** There are 13 supported frequencies by ADTS. **/
    public static final int[] AUDIO_SAMPLING_RATES =
    {
        96000, // 0
        88200, // 1
        64000, // 2
        48000, // 3
        44100, // 4
        32000, // 5
        24000, // 6
        22050, // 7
        16000, // 8
        12000, // 9
        11025, // 10
        8000,  // 11
        7350,  // 12
        -1,   // 13
        -1,   // 14
        -1,   // 15
    };

    private String mSessionDescription = null;
    private int mProfile;
    private int mSamplingRateIndex;
    private int mChannel;
    private int mConfig;
    private AudioRecord mAudioRecord = null;
    private Thread mThread = null;

    public AACStream()
    {
        super();

        if (!AACStreamingSupported())
        {
            Log.e(TAG,"AAC not supported on this phone");
            throw new RuntimeException("AAC not supported by this phone !");
        }
        else
        {
            Log.d(TAG,"AAC supported on this phone");
        }
    }

    private static boolean AACStreamingSupported()
    {
        if (Build.VERSION.SDK_INT < 14)
        {
            return false;
        }

        try
        {
            MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public synchronized void start() throws IllegalStateException, IOException
    {
        if (!mStreaming)
        {
            configure();
            super.start();
        }
    }

    public synchronized void configure() throws IllegalStateException, IOException
    {
        super.configure();
        mQuality = mRequestedQuality.clone();

        // Checks if the user has supplied an exotic sampling rate
        int i = 0;
        for (; i < AUDIO_SAMPLING_RATES.length; i++)
        {
            if (AUDIO_SAMPLING_RATES[i] == mQuality.samplingRate)
            {
                mSamplingRateIndex = i;
                break;
            }
        }

        // If he did, we force a reasonable one: 16 kHz
        if (i > 12)
        {
            mQuality.samplingRate = 16000;
        }

        if (mMode != mRequestedMode || mPacketizer == null)
        {
            mMode = mRequestedMode;
            mPacketizer = new AACLATMPacketizer();
            mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
            mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
        }

        mProfile = 2; // AAC LC
        mChannel = 1;
        mConfig = (mProfile & 0x1F) << 11 | (mSamplingRateIndex & 0x0F) << 7 | (mChannel & 0x0F) << 3;

        mSessionDescription = "m=audio " + getDestinationPorts()[0] + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 mpeg4-generic/" + mQuality.samplingRate + "\r\n" +
                "a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=" + Integer.toHexString(mConfig) +
                "; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";
    }

    @Override
    protected void encodeWithMediaCodec() throws IOException
    {
        final int bufferSize = AudioRecord.getMinBufferSize(mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        ((AACLATMPacketizer)mPacketizer).setSamplingRate(mQuality.samplingRate);

        mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioRecord.startRecording();
        mMediaCodec.start();

        final MediaCodecInputStream inputStream = new MediaCodecInputStream(mMediaCodec);
        final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
            int len = 0;
            int bufferIndex = 0;

            try
            {
                while (!Thread.interrupted())
                {
                    bufferIndex = mMediaCodec.dequeueInputBuffer(10000);

                    if (bufferIndex >= 0)
                    {
                        inputBuffers[bufferIndex].clear();
                        len = mAudioRecord.read(inputBuffers[bufferIndex], bufferSize);

                        if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE)
                        {
                            Log.e(TAG,"An error occurred with the AudioRecord API: " + len);
                        }
                        else
                        {
                            mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime() / 1000, 0);
                        }
                    }
                }
            }
            catch (RuntimeException e)
            {
                Log.e(TAG, "Encoding threw", e);
            }
            }
        });

        mThread.start();

        // The packetizer encapsulates this stream in an RTP stream and send it over the network
        mPacketizer.setInputStream(inputStream);
        mPacketizer.start();

        mStreaming = true;
    }

    /** Stops the stream. */
    public synchronized void stop()
    {
        if (mStreaming)
        {
            if (mMode == MODE_MEDIACODEC_API)
            {
                Log.d(TAG, "Interrupting threads...");
                mThread.interrupt();
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
            }

            super.stop();
        }
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     * Will fail if called when streaming.
     */
    public String getSessionDescription() throws IllegalStateException
    {
        if (mSessionDescription == null)
        {
            throw new IllegalStateException("You need to call configure() first!");
        }

        return mSessionDescription;
    }
}
