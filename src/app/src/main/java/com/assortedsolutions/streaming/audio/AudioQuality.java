package com.assortedsolutions.streaming.audio;

import android.util.Log;

/**
 * A class that represents the quality of an audio stream.
 */
public class AudioQuality
{
    public final static String TAG = "AudioQuality";

    /** Default audio stream quality. */
    public final static AudioQuality DEFAULT_AUDIO_QUALITY = new AudioQuality(8000,32000);

    /**
     * Represents a quality for an audio stream.
     * @param samplingRate The sampling rate
     * @param bitRate The bitrate in bit per seconds
     */
    public AudioQuality(int samplingRate, int bitRate)
    {
        this.samplingRate = samplingRate;
        this.bitRate = bitRate;
    }

    public int samplingRate = 0;
    public int bitRate = 0;

    public AudioQuality clone()
    {
        return new AudioQuality(samplingRate, bitRate);
    }

    public static AudioQuality parseQuality(String str)
    {
        AudioQuality quality = DEFAULT_AUDIO_QUALITY.clone();
        if (str != null)
        {
            String[] config = str.split("-");
            try
            {
                quality.bitRate = Integer.parseInt(config[0]) * 1000; // conversion to bit/s
                quality.samplingRate = Integer.parseInt(config[1]);
            }
            catch (IndexOutOfBoundsException ignore)
            {
                Log.e(TAG, "Parse quality threw", ignore);
            }
        }

        return quality;
    }
}
