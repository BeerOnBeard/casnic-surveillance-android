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

import com.assortedsolutions.streaming.MediaStream;
import android.media.MediaRecorder;

/**
 * Don't use this class directly.
 */
public abstract class AudioStream extends MediaStream
{
    protected int mAudioSource;
    protected int mOutputFormat;
    protected int mAudioEncoder;
    protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
    protected AudioQuality mQuality = mRequestedQuality.clone();

    public AudioStream() {
        setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
    }

    public void setAudioSource(int audioSource) {
        mAudioSource = audioSource;
    }

    public void setAudioQuality(AudioQuality quality) {
        mRequestedQuality = quality;
    }

    protected void setAudioEncoder(int audioEncoder) {
        mAudioEncoder = audioEncoder;
    }

    protected void setOutputFormat(int outputFormat) {
        mOutputFormat = outputFormat;
    }
}
