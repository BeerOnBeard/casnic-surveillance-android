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

package com.assortedsolutions.streaming.mp4;

import android.util.Base64;

/**
 * Finds SPS & PPS parameters in mp4 file.
 */
public class MP4Config
{
    public final static String TAG = "MP4Config";

    private String mProfileLevel;
    private String mPPS;
    private String mSPS;

    public MP4Config(String sps, String pps)
    {
        mPPS = pps;
        mSPS = sps;
        mProfileLevel = toHexString(Base64.decode(sps, Base64.NO_WRAP),1,3);
    }

    public String getProfileLevel() {
        return mProfileLevel;
    }

    public String getB64PPS() { return mPPS; }

    public String getB64SPS() { return mSPS; }

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
