package com.assortedsolutions.streaming.rtp;

import java.io.IOException;
import android.util.Log;

/**
 *
 *   RFC 3984.
 *
 *   H.264 streaming over RTP.
 *
 *   Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *
 */
public class H264Packetizer extends AbstractPacketizer implements Runnable
{
    public final static String TAG = "H264Packetizer";

    private Thread thread = null;
    private int nalLength = 0;
    private long delay = 0;
    private long oldtime = 0;
    private PacketizerStatistics stats = new PacketizerStatistics();
    private byte[] sps = null;
    private byte[] pps = null;
    private byte[] stapa = null;
    byte[] header = new byte[5];
    private int count = 0;
    private int streamType = 1;

    public H264Packetizer()
    {
        super();
        socket.setClockFrequency(90000);
    }

    public void start()
    {
        if (thread != null)
        {
            return;
        }

        thread = new Thread(this);
        thread.start();
    }

    public void stop()
    {
        if (thread == null)
        {
            return;
        }

        try
        {
            inputStream.close();
        }
        catch (IOException e)
        {
            Log.e(TAG, "Closing input stream threw", e);
        }

        thread.interrupt();

        try
        {
            thread.join();
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "Waiting for thread to die threw", e);
        }

        thread = null;
    }

    public void setStreamParameters(byte[] pps, byte[] sps)
    {
        this.pps = pps;
        this.sps = sps;

        // A STAP-A NAL (NAL type 24) containing the sps and pps of the stream
        if (pps != null && sps != null)
        {
            // STAP-A NAL header + NALU 1 (SPS) size + NALU 2 (PPS) size = 5 bytes
            stapa = new byte[sps.length + pps.length + 5];

            // STAP-A NAL header is 24
            stapa[0] = 24;

            // Write NALU 1 size into the array (NALU 1 is the SPS).
            stapa[1] = (byte) (sps.length >> 8);
            stapa[2] = (byte) (sps.length & 0xFF);

            // Write NALU 2 size into the array (NALU 2 is the PPS).
            stapa[sps.length + 3] = (byte) (pps.length >> 8);
            stapa[sps.length + 4] = (byte) (pps.length & 0xFF);

            // Write NALU 1 into the array, then write NALU 2 into the array.
            System.arraycopy(sps, 0, stapa, 3, sps.length);
            System.arraycopy(pps, 0, stapa, 5 + sps.length, pps.length);
        }
    }

    public void run()
    {
        long duration = 0;

        Log.d(TAG,"H264 packetizer started!");

        stats.reset();
        count = 0;

        if (inputStream instanceof MediaCodecInputStream)
        {
            streamType = 1;
            socket.setCacheSize(0);
        }
        else
        {
            streamType = 0;
            socket.setCacheSize(400);
        }

        try
        {
            while (!Thread.interrupted())
            {
                oldtime = System.nanoTime();

                // We read a NAL units from the input stream and we send them
                send();

                // We measure how long it took to receive NAL units from the phone
                duration = System.nanoTime() - oldtime;

                stats.push(duration);

                // Computes the average duration of a NAL unit
                delay = stats.average();
            }
        }
        catch (IOException e)
        {
            Log.e(TAG, "Run threw", e);
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "Run threw", e);
        }

        Log.d(TAG,"H264 packetizer stopped !");
    }

    /**
     * Reads a NAL unit in the FIFO and sends it.
     * If it is too big, we split it in FU-A units (RFC 3984).
     */
    private void send() throws IOException, InterruptedException
    {
        int sum = 1;
        int len = 0;
        int type;

        if (streamType == 0)
        {
            // NAL units are preceeded by their length, we parse the length
            fill(header,0,5);
            timestamp += delay;
            nalLength = header[3] & 0xFF | (header[2] & 0xFF) << 8 | (header[1] & 0xFF) << 16 | (header[0] & 0xFF) << 24;
            if (nalLength > 100000 || nalLength < 0)
            {
                resync();
            }
        }
        else if (streamType == 1)
        {
            // NAL units are preceeded with 0x00000001
            fill(header,0,5);
            timestamp = ((MediaCodecInputStream) inputStream).getLastBufferInfo().presentationTimeUs * 1000L;

            //timestamp += delay;
            nalLength = inputStream.available() + 1;
            if (!(header[0] == 0 && header[1] == 0 && header[2] == 0))
            {
                Log.e(TAG, "NAL units are not preceded by 0x00000001");

                streamType = 2;
                return;
            }
        }
        else
        {
            // Nothing precedes the NAL units
            fill(header,0,1);
            header[4] = header[0];
            timestamp = ((MediaCodecInputStream) inputStream).getLastBufferInfo().presentationTimeUs * 1000L;

            nalLength = inputStream.available()+1;
        }

        // Parses the NAL unit type
        type = header[4] & 0x1F;

        // The stream already contains NAL unit type 7 or 8, we don't need
        // to add them to the stream ourselves
        if (type == 7 || type == 8)
        {
            Log.v(TAG,"SPS or PPS present in the stream.");

            count++;
            if (count > 4)
            {
                sps = null;
                pps = null;
            }
        }

        // We send two packets containing NALU type 7 (SPS) and 8 (PPS)
        // Those should allow the H264 stream to be decoded even if no SDP was sent to the decoder.
        if (type == 5 && sps != null && pps != null)
        {
            buffer = socket.requestBuffer();
            socket.markNextPacket();
            socket.updateTimestamp(timestamp);
            System.arraycopy(stapa, 0, buffer, rtpHeaderLength, stapa.length);
            super.send(rtpHeaderLength +stapa.length);
        }

        // Small NAL unit => Single NAL unit
        if (nalLength <= MAXPACKETSIZE- rtpHeaderLength - 2)
        {
            buffer = socket.requestBuffer();
            buffer[rtpHeaderLength] = header[4];
            len = fill(buffer, rtpHeaderLength + 1,  nalLength - 1);
            socket.updateTimestamp(timestamp);
            socket.markNextPacket();
            super.send(nalLength + rtpHeaderLength);
        }
        else // Large NAL unit => Split nal unit
        {
            // Set FU-A header
            header[1] = (byte) (header[4] & 0x1F);  // FU header type
            header[1] += 0x80; // Start bit

            // Set FU-A indicator
            header[0] = (byte) ((header[4] & 0x60) & 0xFF); // FU indicator NRI
            header[0] += 28;

            while (sum < nalLength)
            {
                buffer = socket.requestBuffer();
                buffer[rtpHeaderLength] = header[0];
                buffer[rtpHeaderLength + 1] = header[1];
                socket.updateTimestamp(timestamp);
                if ((len = fill(buffer, rtpHeaderLength + 2, nalLength - sum > MAXPACKETSIZE - rtpHeaderLength - 2 ? MAXPACKETSIZE - rtpHeaderLength - 2 : nalLength - sum)) < 0)
                {
                    return;
                }

                sum += len;

                // Last packet before next NAL
                if (sum >= nalLength)
                {
                    // End bit on
                    buffer[rtpHeaderLength + 1] += 0x40;
                    socket.markNextPacket();
                }

                super.send(len + rtpHeaderLength + 2);

                // Switch start bit
                header[1] = (byte) (header[1] & 0x7F);
            }
        }
    }

    private int fill(byte[] buffer, int offset,int length) throws IOException
    {
        int sum = 0;
        int len;

        while (sum < length)
        {
            len = inputStream.read(buffer, offset + sum, length - sum);
            if (len < 0)
            {
                throw new IOException("End of stream");
            }

            else sum += len;
        }

        return sum;
    }

    private void resync() throws IOException
    {
        int type;

        Log.e(TAG,"Packetizer out of sync. Let's try to fix that... NAL length: " + nalLength);

        while (true)
        {
            header[0] = header[1];
            header[1] = header[2];
            header[2] = header[3];
            header[3] = header[4];
            header[4] = (byte) inputStream.read();

            type = header[4]&0x1F;

            if (type == 5 || type == 1)
            {
                nalLength = header[3] & 0xFF | (header[2] & 0xFF) << 8 | (header[1] & 0xFF) << 16 | (header[0] & 0xFF) << 24;
                if (nalLength > 0 && nalLength < 100000)
                {
                    oldtime = System.nanoTime();
                    Log.e(TAG,"A NAL unit may have been found in the bit stream!");
                    break;
                }

                if (nalLength == 0)
                {
                    Log.e(TAG,"NAL unit with NULL size found...");
                }
                else if (header[3] == 0xFF && header[2] == 0xFF && header[1] == 0xFF && header[0] == 0xFF)
                {
                    Log.e(TAG,"NAL unit with 0xFFFFFFFF size found...");
                }
            }
        }
    }
}
