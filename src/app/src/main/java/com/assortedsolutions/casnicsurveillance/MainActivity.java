package com.assortedsolutions.casnicsurveillance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.assortedsolutions.streaming.Session;
import com.assortedsolutions.streaming.SessionBuilder;
import com.assortedsolutions.streaming.audio.AudioQuality;
import com.assortedsolutions.streaming.gl.SurfaceView;
import com.assortedsolutions.streaming.rtsp.RtspServer;
import com.assortedsolutions.streaming.video.VideoQuality;

public class MainActivity extends Activity implements Session.Callback, SurfaceHolder.Callback {

    private final static String TAG = "MainActivity";

    private SurfaceView mSurfaceView;
    private Session mSession;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mSurfaceView = findViewById(R.id.surface);

        mSession = SessionBuilder.getInstance()
                .setCallback(this)
                .setSurfaceView(mSurfaceView)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                .setAudioQuality(new AudioQuality(16000, 32000))
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(320, 240, 20, 500000))
                .build();

        //mSession.setDestination("192.168.50.230");
        //mSession.configure();

        Intent server = new Intent(this, RtspServer.class);

        Log.d(TAG, "starting RTSP Server service");
        startService(server);

        mSurfaceView.getHolder().addCallback(this);
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mSession.release();
    }

    /************************************
     * Session.Callback implementations *
     ************************************/

    @Override
    public void onBitrateUpdate(long bitrate)
    {
        Log.d(TAG,"Bitrate: " + bitrate);
    }

    @Override
    public void onSessionError(int message, int streamType, Exception e)
    {
        if (e != null) {
            logError(e.getMessage());
        }
    }

    @Override
    public void onPreviewStarted()
    {
        Log.d(TAG,"Preview Started");
    }

    @Override
    public void onSessionConfigured()
    {
        Log.d(TAG, "Session Configured");
        mSession.start();
    }

    @Override
    public void onSessionStarted()
    {
        Log.d(TAG,"Session Started");
    }

    @Override
    public void onSessionStopped()
    {
        Log.d(TAG,"Session Stopped");
    }

    /******************************************
     * SurfaceHolder.Callback implementations *
     ******************************************/

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        Log.d(TAG, "Surface Changed");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
        Log.d(TAG, "Surface Created");
        mSession.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        Log.d(TAG, "Surface Destroyed");
        mSession.stop();
    }

    /** Displays a popup to report the error to the user */
    private void logError(final String msg)
    {
        final String error = (msg == null) ? "Error unknown" : msg;

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(error).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {}
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
