/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 *
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.assortedsolutions.streaming.rtp;

import java.io.IOException;

import android.os.SystemClock;
import android.util.Log;

/**
 *   RFC 4629.
 *
 *   H.263 Streaming over RTP.
 *
 *   Must be fed with an InputStream containing H.263 frames.
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *
 */
public class H263Packetizer extends AbstractPacketizer implements Runnable {

    public final static String TAG = "H263Packetizer";
    private final static int MAXPACKETSIZE = 1400;
    private Statistics stats = new Statistics();

    private Thread t;

    public H263Packetizer() throws IOException {
        super();
    }

    public void start() throws IOException {
        if (t==null) {
            t = new Thread(this);
            t.start();
        }
    }

    public void stop() {
        try {
            is.close();
        } catch (IOException ignore) {}
        t.interrupt();
        t = null;

    }

    public void run() {
        long time, duration = 0;
        int i = 0, j = 0, tr;
        boolean firstFragment = true;

        // This will skip the MPEG4 header if this step fails we can't stream anything :(
        try {
            skipHeader();
        } catch (IOException e) {
            Log.e(TAG,"Couldn't skip mp4 header :/");
            return;
        }

        // Each packet we send has a two byte long header (See section 5.1 of RFC 4629)
        buffer[rtphl] = 0;
        buffer[rtphl+1] = 0;

        try {
            while (!Thread.interrupted()) {
                time = System.nanoTime();
                if (fill(rtphl+j+2,MAXPACKETSIZE-rtphl-j-2)<0) return;
                duration += System.nanoTime() - time;
                j = 0;
                // Each h263 frame starts with: 0000 0000 0000 0000 1000 00??
                // Here we search where the next frame begins in the bit stream
                for (i=rtphl+2;i<MAXPACKETSIZE-1;i++) {
                    if (buffer[i]==0 && buffer[i+1]==0 && (buffer[i+2]&0xFC)==0x80) {
                        j=i;
                        break;
                    }
                }
                // Parse temporal reference
                tr = (buffer[i+2]&0x03)<<6 | (buffer[i+3]&0xFF)>>2;
                //Log.d(TAG,"j: "+j+" buffer: "+printBuffer(rtphl, rtphl+5)+" tr: "+tr);
                if (firstFragment) {
                    // This is the first fragment of the frame -> header is set to 0x0400
                    buffer[rtphl] = 4;
                    firstFragment = false;
                } else {
                    buffer[rtphl] = 0;
                }
                if (j>0) {
                    // We send one RTCP Sender Report every 5 secs
                    delta += duration/1000000;
                    if (intervalBetweenReports>0) {
                        if (delta>=intervalBetweenReports && duration/1000000>10) {
                            delta = 0;
                            report.send(System.nanoTime(),ts);
                        }
                    }
                    // We have found the end of the frame
                    stats.push(duration);
                    ts+= stats.average()*9/100000; duration = 0;
                    //Log.d(TAG,"End of frame ! duration: "+stats.average());
                    // The last fragment of a frame has to be marked
                    socket.markNextPacket();
                    send(j);
                    socket.updateTimestamp(ts);
                    System.arraycopy(buffer,j+2,buffer,rtphl+2,MAXPACKETSIZE-j-2);
                    j = MAXPACKETSIZE-j-2;
                    firstFragment = true;
                } else {
                    // We have not found the beginning of another frame
                    // The whole packet is a fragment of a frame
                    send(MAXPACKETSIZE);
                }
            }
        } catch (IOException e) {}

        Log.d(TAG,"H263 Packetizer stopped !");

    }

    private int fill(int offset,int length) throws IOException {

        int sum = 0, len;

        while (sum<length) {
            len = is.read(buffer, offset+sum, length-sum);
            if (len<0) {
                throw new IOException("End of stream");
            }
            else sum+=len;
        }

        return sum;

    }

    // The InputStream may start with a header that we need to skip
    private void skipHeader() throws IOException {
        // Skip all atoms preceding mdat atom
        while (true) {
            while (is.read() != 'm');
            is.read(buffer,rtphl,3);
            if (buffer[rtphl] == 'd' && buffer[rtphl+1] == 'a' && buffer[rtphl+2] == 't') break;
        }
    }

}
