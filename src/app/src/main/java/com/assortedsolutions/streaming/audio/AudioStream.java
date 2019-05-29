package com.assortedsolutions.streaming.audio;

import com.assortedsolutions.streaming.MediaStream;
import android.media.MediaRecorder;

/**
 * Don't use this class directly.
 */
public abstract class AudioStream extends MediaStream
{
    protected int mAudioSource;
    protected AudioQuality requestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
    protected AudioQuality quality = requestedQuality.clone();

    public AudioStream() {
        setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
    }

    public void setAudioSource(int audioSource) {
        mAudioSource = audioSource;
    }

    public void setAudioQuality(AudioQuality quality) {
        requestedQuality = quality;
    }
}
