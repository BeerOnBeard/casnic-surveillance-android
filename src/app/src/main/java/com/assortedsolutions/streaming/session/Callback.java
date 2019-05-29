package com.assortedsolutions.streaming.session;

import com.assortedsolutions.streaming.video.VideoStream;

/**
 * The callback interface you need to implement to get some feedback
 * Those will be called from the UI thread.
 */
public interface Callback
{
    /**
     * Called periodically to inform you on the bandwidth
     * consumption of the streams when streaming.
     */
    void onBitrateUpdate(long bitrate);

    /** Called when some error occurs. */
    void onSessionError(int reason, int streamType, Exception e);

    /**
     * Called when the preview of the {@link VideoStream}
     * has correctly been started.
     * If an error occurs while starting the preview,
     * {@link Callback#onSessionError} will be
     * called instead of {@link Callback#onPreviewStarted()}.
     */
    void onPreviewStarted();

    /**
     * Called when the session has correctly been configured
     * after calling {@link Session#configure()}.
     * If an error occurs while configuring the {@link Session},
     * {@link Callback#onSessionError} will be
     * called instead of  {@link Callback#onSessionConfigured()}.
     */
    void onSessionConfigured();

    /**
     * Called when the streams of the session have correctly been started.
     * If an error occurs while starting the {@link Session},
     * {@link Callback#onSessionError} will be
     * called instead of  {@link Callback#onSessionStarted()}.
     */
    void onSessionStarted();

    /** Called when the stream of the session have been stopped. */
    void onSessionStopped();
}
