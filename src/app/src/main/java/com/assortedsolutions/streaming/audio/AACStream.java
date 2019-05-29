package com.assortedsolutions.streaming.audio;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import com.assortedsolutions.streaming.session.SessionBuilder;
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

    private String sessionDescription = null;
    private int profile;
    private int samplingRateIndex;
    private int channel;
    private int config;
    private AudioRecord audioRecord = null;
    private Thread thread = null;

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
        if (streaming)
        {
            return;
        }

        configure();
        super.start();
    }

    public synchronized void configure() throws IllegalStateException, IOException
    {
        super.configure();
        quality = requestedQuality.clone();

        // Checks if the user has supplied an exotic sampling rate
        int i = 0;
        for (; i < AUDIO_SAMPLING_RATES.length; i++)
        {
            if (AUDIO_SAMPLING_RATES[i] == quality.samplingRate)
            {
                samplingRateIndex = i;
                break;
            }
        }

        // If he did, we force a reasonable one: 16 kHz
        if (i > 12)
        {
            quality.samplingRate = 16000;
        }

        if (packetizer == null)
        {
            packetizer = new AACLATMPacketizer();
            packetizer.setDestination(destination, rtpPort, rtcpPort);
        }

        profile = 2; // AAC LC
        channel = 1;
        config = (profile & 0x1F) << 11 | (samplingRateIndex & 0x0F) << 7 | (channel & 0x0F) << 3;

        sessionDescription = new StringBuilder()
            .append("m=audio ")
            .append(getDestinationPorts()[0])
            .append(" RTP/AVP 96\r\n")
            .append("a=rtpmap:96 mpeg4-generic/")
            .append(quality.samplingRate)
            .append("\r\n")
            .append("a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config=")
            .append(Integer.toHexString(config))
            .append("; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n")
            .toString();
    }

    @Override
    protected void encodeWithMediaCodec() throws IOException
    {
        final int bufferSize = AudioRecord.getMinBufferSize(quality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;

        ((AACLATMPacketizer) packetizer).setSamplingRate(quality.samplingRate);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, quality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        mediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_BIT_RATE, quality.bitRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, quality.samplingRate);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioRecord.startRecording();
        mediaCodec.start();

        final MediaCodecInputStream inputStream = new MediaCodecInputStream(mediaCodec);
        final ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
            int len = 0;
            int bufferIndex = 0;

            try
            {
                while (!Thread.interrupted())
                {
                    bufferIndex = mediaCodec.dequeueInputBuffer(10000);

                    if (bufferIndex >= 0)
                    {
                        inputBuffers[bufferIndex].clear();
                        len = audioRecord.read(inputBuffers[bufferIndex], bufferSize);

                        if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE)
                        {
                            Log.e(TAG,"An error occurred with the AudioRecord API: " + len);
                        }
                        else
                        {
                            mediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime() / 1000, 0);
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

        thread.start();

        // The packetizer encapsulates this stream in an RTP stream and send it over the network
        packetizer.setInputStream(inputStream);
        packetizer.start();

        streaming = true;
    }

    /** Stops the stream. */
    public synchronized void stop()
    {
        if (!streaming)
        {
            return;
        }

        Log.d(TAG, "Interrupting threads...");
        thread.interrupt();
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;

        super.stop();
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     * Will fail if called when streaming.
     */
    public String getSessionDescription() throws IllegalStateException
    {
        if (sessionDescription == null)
        {
            throw new IllegalStateException("You need to call configure() first!");
        }

        return sessionDescription;
    }
}
