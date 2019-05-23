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

package com.assortedsolutions.streaming.rtsp;

import static com.assortedsolutions.streaming.session.SessionBuilder.AUDIO_AAC;
import static com.assortedsolutions.streaming.session.SessionBuilder.AUDIO_NONE;
import static com.assortedsolutions.streaming.session.SessionBuilder.VIDEO_H264;
import static com.assortedsolutions.streaming.session.SessionBuilder.VIDEO_NONE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.Set;
import com.assortedsolutions.streaming.session.Session;
import com.assortedsolutions.streaming.session.SessionBuilder;
import com.assortedsolutions.streaming.audio.AudioQuality;
import com.assortedsolutions.streaming.video.VideoQuality;

import android.content.ContentValues;
import android.hardware.Camera.CameraInfo;
import android.util.Log;

/**
 * This class parses URIs received by the RTSP server and configures a Session accordingly.
 */
public class UriParser
{
    public final static String TAG = "UriParser";

    /**
     * Configures a Session according to the given URI.
     * Here are some examples of URIs that can be used to configure a Session:
     * <ul><li>rtsp://xxx.xxx.xxx.xxx:8086?h264&flash=on</li>
     * <li>rtsp://xxx.xxx.xxx.xxx:8086?h263&camera=front&flash=on</li>
     * <li>rtsp://xxx.xxx.xxx.xxx:8086?h264=200-20-320-240</li>
     * <li>rtsp://xxx.xxx.xxx.xxx:8086?aac</li></ul>
     * @param uri The URI
     * @throws IllegalStateException
     * @throws IOException
     * @return A Session configured according to the URI
     */
    public static Session parse(String uri) throws IllegalStateException, IOException
    {
        SessionBuilder builder = SessionBuilder.getInstance().clone();
        String query = URI.create(uri).getQuery();
        String[] queryParams = query == null ? new String[0] : query.split("&");
        ContentValues params = new ContentValues();
        for (String param : queryParams)
        {
            String[] keyValue = param.split("=");
            String value = "";
            try
            {
                value = keyValue[1];
            }
            catch(ArrayIndexOutOfBoundsException e)
            {
                Log.e(TAG, "Getting key value threw", e);
            }

            params.put(
                URLEncoder.encode(keyValue[0], "UTF-8"), // Name
                URLEncoder.encode(value, "UTF-8")  // Value
            );
        }

        if (params.size() > 0)
        {
            builder
                .setAudioEncoder(AUDIO_NONE)
                .setVideoEncoder(VIDEO_NONE);

            Set<String> paramKeys = params.keySet();

            // Those parameters must be parsed first or else they won't necessarily be taken into account
            for(String paramName : paramKeys)
            {
                String paramValue = params.getAsString(paramName);

                // FLASH ON/OFF
                if (paramName.equalsIgnoreCase("flash"))
                {
                    if (paramValue.equalsIgnoreCase("on"))
                    {
                        builder.setFlashEnabled(true);
                    }
                    else
                    {
                        builder.setFlashEnabled(false);
                    }
                }

                // CAMERA -> the client can choose between the front facing camera and the back facing camera
                else if (paramName.equalsIgnoreCase("camera"))
                {
                    if (paramValue.equalsIgnoreCase("back"))
                    {
                        builder.setCamera(CameraInfo.CAMERA_FACING_BACK);
                    }
                    else if (paramValue.equalsIgnoreCase("front"))
                    {
                        builder.setCamera(CameraInfo.CAMERA_FACING_FRONT);
                    }
                }

                // MULTICAST -> the stream will be sent to a multicast group
                // The default mutlicast address is 228.5.6.7, but the client can specify another
                else if (paramName.equalsIgnoreCase("multicast"))
                {
                    if (paramValue != null)
                    {
                        try
                        {
                            InetAddress addr = InetAddress.getByName(paramValue);
                            if (!addr.isMulticastAddress())
                            {
                                throw new IllegalStateException("Invalid multicast address !");
                            }

                            builder.setDestination(paramValue);
                        }
                        catch (UnknownHostException e)
                        {
                            throw new IllegalStateException("Invalid multicast address !");
                        }
                    }
                    else
                    {
                        // Default multicast address
                        builder.setDestination("228.5.6.7");
                    }
                }

                // UNICAST -> the client can use this to specify where he wants the stream to be sent
                else if (paramName.equalsIgnoreCase("unicast"))
                {
                    if (paramValue != null)
                    {
                        builder.setDestination(paramValue);
                    }
                }

                // TTL -> the client can modify the time to live of packets
                // By default ttl=64
                else if (paramName.equalsIgnoreCase("ttl"))
                {
                    if (paramValue!=null)
                    {
                        try
                        {
                            int ttl = Integer.parseInt(paramValue);
                            if (ttl<0) throw new IllegalStateException();
                            builder.setTimeToLive(ttl);
                        }
                        catch (Exception e)
                        {
                            throw new IllegalStateException("The TTL must be a positive integer !");
                        }
                    }
                }

                // H.264
                else if (paramName.equalsIgnoreCase("h264"))
                {
                    VideoQuality quality = VideoQuality.parseQuality(paramValue);
                    builder.setVideoQuality(quality).setVideoEncoder(VIDEO_H264);
                }

                // AAC
                else if (paramName.equalsIgnoreCase("aac"))
                {
                    AudioQuality quality = AudioQuality.parseQuality(paramValue);
                    builder.setAudioQuality(quality).setAudioEncoder(AUDIO_AAC);
                }
            }
        }

        if (builder.getVideoEncoder() == VIDEO_NONE && builder.getAudioEncoder() == AUDIO_NONE)
        {
            SessionBuilder b = SessionBuilder.getInstance();
            builder.setVideoEncoder(b.getVideoEncoder());
            builder.setAudioEncoder(b.getAudioEncoder());
        }

        Session session = builder.build();
        return session;
    }
}
