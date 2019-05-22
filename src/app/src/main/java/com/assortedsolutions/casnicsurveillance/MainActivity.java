package com.assortedsolutions.casnicsurveillance;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.assortedsolutions.streaming.Session;
import com.assortedsolutions.streaming.SessionBuilder;
import com.assortedsolutions.streaming.audio.AudioQuality;
import com.assortedsolutions.streaming.rtsp.RtspService;
import com.assortedsolutions.streaming.video.VideoQuality;

public class MainActivity extends Activity implements Session.Callback {

    private final static String TAG = "MainActivity";

    private SurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        surfaceView = findViewById(R.id.surface);

        SessionBuilder.getInstance()
            .setCallback(this)
            .setSurfaceView(surfaceView)
            .setContext(getApplicationContext())
            .setAudioEncoder(SessionBuilder.AUDIO_AAC)
            .setAudioQuality(new AudioQuality(16000, 32000))
            .setVideoEncoder(SessionBuilder.VIDEO_H264)
            .setVideoQuality(new VideoQuality(320, 240, 20, 500000));

        Intent server = new Intent(this, RtspService.class);

        Log.d(TAG, "starting RTSP Server service");
        startService(server);

        surfaceView.setBackgroundColor(Color.argb(255, 255, 0, 0));
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
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
    }

    @Override
    public void onSessionStarted()
    {
        Log.d(TAG,"Session Started");
        surfaceView.setBackgroundColor(Color.argb(0, 255, 0, 0));
    }

    @Override
    public void onSessionStopped()
    {
        Log.d(TAG,"Session Stopped");
        surfaceView.setBackgroundColor(Color.argb(255, 255, 0, 0));
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
