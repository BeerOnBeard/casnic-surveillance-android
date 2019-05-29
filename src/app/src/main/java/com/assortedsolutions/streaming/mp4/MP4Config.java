package com.assortedsolutions.streaming.mp4;

import android.util.Base64;

/**
 * Finds SPS & PPS parameters in mp4 file.
 */
public class MP4Config
{
    public final static String TAG = "MP4Config";

    private String profileLevel;
    private String PPS;
    private String SPS;

    public MP4Config(String sps, String pps)
    {
        PPS = pps;
        SPS = sps;
        profileLevel = toHexString(Base64.decode(sps, Base64.NO_WRAP),1,3);
    }

    public String getProfileLevel() { return profileLevel; }

    public String getB64PPS() { return PPS; }

    public String getB64SPS() { return SPS; }

    private static String toHexString(byte[] buffer, int start, int len)
    {
        String c;
        StringBuilder s = new StringBuilder();
        for (int i = start; i < start + len; i++)
        {
            c = Integer.toHexString(buffer[i] & 0xFF);
            s.append(c.length() < 2 ? "0" + c : c);
        }

        return s.toString();
    }
}
