package com.assortedsolutions.streaming.rtp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.assortedsolutions.streaming.rtcp.SenderReport;
import android.util.Log;

/**
 * A basic implementation of an RTP socket.
 * It implements a buffering mechanism, relying on a FIFO of buffers and a Thread.
 * That way, if a packetizer tries to send many packets too quickly, the FIFO will
 * grow and packets will be sent one by one smoothly.
 */
public class RtpSocket implements Runnable
{
    public static final String TAG = "RtpSocket";

    /** Use this to use UDP for the transport protocol. */
    public final static int TRANSPORT_UDP = 0x00;

    /** Use this to use TCP for the transport protocol. */
    public final static int TRANSPORT_TCP = 0x01;

    public static final int RTP_HEADER_LENGTH = 12;
    public static final int MTU = 1300;

    private MulticastSocket multicastSocket;
    private DatagramPacket[] datagramPackets;
    private byte[][] buffers;
    private long[] timestamps;

    private SenderReport senderReport;

    private Semaphore bufferRequested;
    private Semaphore bufferCommitted;
    private Thread thread;

    private int transport;
    private long cacheSize;
    private long clock = 0;
    private long oldTimestamp = 0;
    private int ssrc;
    private int seq = 0;
    private int bufferCount;
    private int bufferIn;
    private int bufferOut;
    private int count = 0;
    private byte tcpHeader[];

    protected OutputStream outputStream = null;

    private AverageBitrate averageBitrate;

    /**
     * This RTP socket implements a buffering mechanism relying on a FIFO of buffers and a Thread.
     * @throws IOException
     */
    public RtpSocket()
    {
        cacheSize = 0;
        bufferCount = 300; // TODO: readjust that when the FIFO is full
        buffers = new byte[bufferCount][];
        datagramPackets = new DatagramPacket[bufferCount];
        senderReport = new SenderReport();
        averageBitrate = new AverageBitrate();
        transport = TRANSPORT_UDP;
        tcpHeader = new byte[] { '$', 0, 0, 0 };

        resetFifo();

        for (int i = 0; i < bufferCount; i++)
        {
            buffers[i] = new byte[MTU];
            datagramPackets[i] = new DatagramPacket(buffers[i], 1);

            /*							     Version(2)  Padding(0)					 					*/
            /*									 ^		  ^			Extension(0)						*/
            /*									 |		  |				^								*/
            /*									 | --------				|								*/
            /*									 | |---------------------								*/
            /*									 | ||  -----------------------> Source Identifier(0)	*/
            /*									 | ||  |												*/
            buffers[i][0] = (byte) Integer.parseInt("10000000",2);

            /* Payload Type */
            buffers[i][1] = (byte) 96;

            /* Byte 2,3        ->  Sequence Number                   */
            /* Byte 4,5,6,7    ->  Timestamp                         */
            /* Byte 8,9,10,11  ->  Sync Source Identifier            */
        }

        try
        {
            multicastSocket = new MulticastSocket();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void resetFifo()
    {
        count = 0;
        bufferIn = 0;
        bufferOut = 0;
        timestamps = new long[bufferCount];
        bufferRequested = new Semaphore(bufferCount);
        bufferCommitted = new Semaphore(0);
        senderReport.reset();
        averageBitrate.reset();
    }

    /** Closes the underlying socket. */
    public void close() {
        multicastSocket.close();
    }

    /** Sets the SSRC of the stream. */
    public void setSSRC(int ssrc)
    {
        this.ssrc = ssrc;
        for (int i = 0; i < bufferCount; i++)
        {
            setLong(buffers[i], ssrc,8,12);
        }

        senderReport.setSSRC(this.ssrc);
    }

    /** Returns the SSRC of the stream. */
    public int getSSRC() {
        return ssrc;
    }

    /** Sets the clock frequency of the stream in Hz. */
    public void setClockFrequency(long clock) {
        this.clock = clock;
    }

    /** Sets the size of the FIFO in ms. */
    public void setCacheSize(long cacheSize) {
        this.cacheSize = cacheSize;
    }

    /** Sets the Time To Live of the UDP packets. */
    public void setTimeToLive(int ttl) throws IOException
    {
        multicastSocket.setTimeToLive(ttl);
    }

    /** Sets the destination address and to which the packets will be sent. */
    public void setDestination(InetAddress dest, int dport, int rtcpPort)
    {
        if (dport != 0 && rtcpPort != 0)
        {
            transport = TRANSPORT_UDP;
            for (int i = 0; i < bufferCount; i++)
            {
                datagramPackets[i].setPort(dport);
                datagramPackets[i].setAddress(dest);
            }

            senderReport.setDestination(dest, rtcpPort);
        }
    }

    public int[] getLocalPorts()
    {
        return new int[]
        {
            multicastSocket.getLocalPort(),
            senderReport.getLocalPort()
        };
    }

    /**
     * Returns an available buffer from the FIFO, it can then be modified.
     * Call {@link #commitBuffer(int)} to send it over the network.
     * @throws InterruptedException
     **/
    public byte[] requestBuffer() throws InterruptedException
    {
        bufferRequested.acquire();
        buffers[bufferIn][1] &= 0x7F;
        return buffers[bufferIn];
    }

    /** Puts the buffer back into the FIFO without sending the packet. */
    public void commitBuffer()
    {
        if (thread == null)
        {
            thread = new Thread(this);
            thread.start();
        }

        if (++bufferIn >= bufferCount)
        {
            bufferIn = 0;
        }

        bufferCommitted.release();
    }

    /** Sends the RTP packet over the network. */
    public void commitBuffer(int length)
    {
        updateSequence();
        datagramPackets[bufferIn].setLength(length);

        averageBitrate.push(length);

        if (++bufferIn >= bufferCount)
        {
            bufferIn = 0;
        }

        bufferCommitted.release();

        if (thread == null)
        {
            thread = new Thread(this);
            thread.start();
        }
    }

    /** Returns an approximation of the bitrate of the RTP stream in bits per second. */
    public long getBitrate()
    {
        return averageBitrate.average();
    }

    /** Increments the sequence number. */
    private void updateSequence()
    {
        setLong(buffers[bufferIn], ++seq, 2, 4);
    }

    /**
     * Overwrites the timestamp in the packet.
     * @param timestamp The new timestamp in ns.
     **/
    public void updateTimestamp(long timestamp)
    {
        timestamps[bufferIn] = timestamp;
        setLong(buffers[bufferIn], (timestamp / 100L) * (clock / 1000L) / 10000L, 4, 8);
    }

    /** Sets the marker in the RTP packet. */
    public void markNextPacket()
    {
        buffers[bufferIn][1] |= 0x80;
    }

    /** The Thread sends the packets in the FIFO one by one at a constant rate. */
    @Override
    public void run()
    {
        RtpSocketStatistics stats = new RtpSocketStatistics(50,3000);
        try
        {
            // Caches cacheSize milliseconds of the stream in the FIFO.
            Thread.sleep(cacheSize);
            long delta = 0;
            while (bufferCommitted.tryAcquire(4, TimeUnit.SECONDS))
            {
                if (oldTimestamp != 0)
                {
                    // We use our knowledge of the clock rate of the stream and the difference between two timestamps to
                    // compute the time lapse that the packet represents.
                    if ((timestamps[bufferOut] - oldTimestamp) > 0)
                    {
                        stats.push(timestamps[bufferOut] - oldTimestamp);
                        long d = stats.average() / 1000000;

                        // We ensure that packets are sent at a constant and suitable rate no matter how the RtpSocket is used.
                        if (cacheSize > 0)
                        {
                            Thread.sleep(d);
                        }
                    }
                    else if ((timestamps[bufferOut] - oldTimestamp) < 0)
                    {
                        Log.e(TAG, "TS: " + timestamps[bufferOut] + " OLD: " + oldTimestamp);
                    }

                    delta += timestamps[bufferOut] - oldTimestamp;
                    if (delta > 500000000 || delta < 0)
                    {
                        delta = 0;
                    }
                }

                senderReport.update(datagramPackets[bufferOut].getLength(), (timestamps[bufferOut] / 100L) * (clock / 1000L) / 10000L);
                oldTimestamp = timestamps[bufferOut];
                if (count++ > 30)
                {
                    if (transport == TRANSPORT_UDP)
                    {
                        multicastSocket.send(datagramPackets[bufferOut]);
                    }
                    else
                    {
                        sendTCP();
                    }
                }

                if (++bufferOut >= bufferCount)
                {
                    bufferOut = 0;
                }

                bufferRequested.release();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        thread = null;
        resetFifo();
    }

    private void sendTCP()
    {
        synchronized (outputStream)
        {
            int len = datagramPackets[bufferOut].getLength();

            Log.d(TAG, "sent " + len);

            tcpHeader[2] = (byte) (len >> 8);
            tcpHeader[3] = (byte) (len & 0xFF);
            try
            {
                outputStream.write(tcpHeader);
                outputStream.write(buffers[bufferOut], 0, len);
            }
            catch (Exception e)
            {
                Log.e(TAG, "Writing to output stream threw", e);
            }
        }
    }

    private void setLong(byte[] buffer, long n, int begin, int end)
    {
        for (end--; end >= begin; end--)
        {
            buffer[end] = (byte) (n % 256);
            n >>= 8;
        }
    }
}
