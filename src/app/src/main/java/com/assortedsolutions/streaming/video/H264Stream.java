package com.assortedsolutions.streaming.video;

import java.io.IOException;
import com.assortedsolutions.streaming.session.SessionBuilder;
import com.assortedsolutions.streaming.hw.EncoderDebugger;
import com.assortedsolutions.streaming.mp4.MP4Config;
import com.assortedsolutions.streaming.rtp.H264Packetizer;
import android.graphics.ImageFormat;
import android.media.MediaRecorder;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Base64;
import android.util.Log;

/**
 * A class for streaming H.264 from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress}, {@link #setDestinationPorts(int)} and {@link #setVideoQuality(VideoQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class H264Stream extends VideoStream
{
    public final static String TAG = "H264Stream";

    private MP4Config mp4Config;

    /**
     * Constructs the H.264 stream.
     * @param cameraId Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     * @throws IOException
     */
    public H264Stream(int cameraId)
    {
        super(cameraId);
        mimeType = "video/avc";
        cameraImageFormat = ImageFormat.NV21;
        videoEncoder = MediaRecorder.VideoEncoder.H264;
        packetizer = new H264Packetizer();
    }

    /**
     * Returns a description of the stream using SDP. It can then be included in an SDP file.
     */
    public synchronized String getSessionDescription() throws IllegalStateException
    {
        if (mp4Config == null)
        {
            throw new IllegalStateException("You need to call configure() first");
        }

        return "m=video " + getDestinationPorts()[0] + " RTP/AVP 96\r\n" +
                "a=rtpmap:96 H264/90000\r\n" +
                "a=fmtp:96 packetization-mode=1;profile-level-id=" + mp4Config.getProfileLevel() +
                ";sprop-parameter-sets=" + mp4Config.getB64SPS() + "," + mp4Config.getB64PPS() +
                ";\r\n";
    }

    /**
     * Starts the stream.
     * This will also open the camera and display the preview if {@link #startPreview()} has not already been called.
     */
    public synchronized void start() throws IllegalStateException, IOException
    {
        if (!streaming)
        {
            configure();
            byte[] pps = Base64.decode(mp4Config.getB64PPS(), Base64.NO_WRAP);
            byte[] sps = Base64.decode(mp4Config.getB64SPS(), Base64.NO_WRAP);
            ((H264Packetizer) packetizer).setStreamParameters(pps, sps);
            super.start();
        }
    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()} to apply
     * your configuration of the stream.
     */
    public synchronized void configure() throws IllegalStateException, IOException
    {
        super.configure();
        quality = requestedQuality.clone();
        mp4Config = testMediaCodecAPI();
    }

    /**
     * Tests if streaming with the given configuration (bit rate, frame rate, resolution) is possible
     * and determines the pps and sps. Should not be called by the UI thread.
     **/
    private MP4Config testMediaCodecAPI() throws RuntimeException
    {
        createCamera();
        updateCamera();
        try
        {
            EncoderDebugger debugger = EncoderDebugger.debug(settings, quality.resX, quality.resY);
            return new MP4Config(debugger.getBase64SPS(), debugger.getBase64PPS());
        }
        catch (Exception e)
        {
            Log.e(TAG,"Resolution not supported with the MediaCodec API.");
            throw e;
        }
    }
}
