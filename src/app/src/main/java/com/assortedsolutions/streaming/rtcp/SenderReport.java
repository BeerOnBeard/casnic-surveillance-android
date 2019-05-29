package com.assortedsolutions.streaming.rtcp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import android.os.SystemClock;

/**
 * Implementation of Sender Report RTCP packets.
 */
public class SenderReport
{
    private final static String TAG = "SenderReport";

    private static final int MTU = 1500;
    private static final int PACKET_LENGTH = 28;

    private MulticastSocket multicastSocket;
    private DatagramPacket datagramPacket;

    private byte[] buffer = new byte[MTU];
    private int SSRC;
    private int port = -1;
    private int octetCount = 0;
    private int packetCount = 0;
    private long interval;
    private long delta;
    private long now;
    private long oldNow;

    public SenderReport()
    {
        /*							     Version(2)  Padding(0)					 					*/
        /*									 ^		  ^			PT = 0	    						*/
        /*									 |		  |				^								*/
        /*									 | --------			 	|								*/
        /*									 | |---------------------								*/
        /*									 | ||													*/
        /*									 | ||													*/
        buffer[0] = (byte) Integer.parseInt("10000000",2);

        /* Packet Type PT */
        buffer[1] = (byte) 200;

        /* Byte 2,3          ->  Length		                     */
        setLong(PACKET_LENGTH / 4 - 1, 2, 4);

        /* Byte 4,5,6,7      ->  SSRC                            */
        /* Byte 8,9,10,11    ->  NTP timestamp hb				 */
        /* Byte 12,13,14,15  ->  NTP timestamp lb				 */
        /* Byte 16,17,18,19  ->  RTP timestamp		             */
        /* Byte 20,21,22,23  ->  packet count				 	 */
        /* Byte 24,25,26,27  ->  octet count			         */

        try
        {
            multicastSocket = new MulticastSocket();
        }
        catch (IOException e)
        {
            // Very unlikely to happen. Means that all UDP ports are already being used
            throw new RuntimeException(e.getMessage(), e);
        }

        datagramPacket = new DatagramPacket(buffer, 1);

        // By default we sent one report every 3 secconde
        interval = 3000;
    }

    public void close() {
        multicastSocket.close();
    }

    /**
     * Sets the temporal interval between two RTCP Sender Reports.
     * Default interval is set to 3 seconds.
     * Set 0 to disable RTCP.
     * @param interval The interval in milliseconds
     */
    public void setInterval(long interval) {
        this.interval = interval;
    }

    /**
     * Updates the number of packets sent, and the total amount of data sent.
     * @param length The length of the packet
     * @param rtpts
     *            The RTP timestamp.
     * @throws IOException
     **/
    public void update(int length, long rtpts) throws IOException
    {
        packetCount += 1;
        octetCount += length;
        setLong(packetCount, 20, 24);
        setLong(octetCount, 24, 28);

        now = SystemClock.elapsedRealtime();
        delta += oldNow != 0 ? now- oldNow : 0;
        oldNow = now;
        if (interval > 0 && delta >= interval)
        {
            // We send a Sender Report
            send(System.nanoTime(), rtpts);
            delta = 0;
        }
    }

    public void setSSRC(int ssrc)
    {
        this.SSRC = ssrc;
        setLong(ssrc,4,8);
        packetCount = 0;
        octetCount = 0;
        setLong(packetCount, 20, 24);
        setLong(octetCount, 24, 28);
    }

    public void setDestination(InetAddress dest, int dport)
    {
        port = dport;
        datagramPacket.setPort(dport);
        datagramPacket.setAddress(dest);
    }

    public int getPort() {
        return port;
    }

    public int getLocalPort() {
        return multicastSocket.getLocalPort();
    }

    public int getSSRC() {
        return SSRC;
    }

    /**
     * Resets the reports (total number of bytes sent, number of packets sent, etc.)
     */
    public void reset()
    {
        packetCount = 0;
        octetCount = 0;
        setLong(packetCount, 20, 24);
        setLong(octetCount, 24, 28);
        delta = now = oldNow = 0;
    }

    private void setLong(long n, int begin, int end)
    {
        for (end--; end >= begin; end--)
        {
            buffer[end] = (byte) (n % 256);
            n >>= 8;
        }
    }

    /**
     * Sends the RTCP packet over the network.
     *
     * @param ntpTimestamp the NTP timestamp.
     * @param rtpTimestamp the RTP timestamp.
     */
    private void send(long ntpTimestamp, long rtpTimestamp) throws IOException
    {
        long hb = ntpTimestamp / 1000000000;
        long lb = ( ( ntpTimestamp - hb * 1000000000 ) * 4294967296L ) / 1000000000;

        setLong(hb, 8, 12);
        setLong(lb, 12, 16);
        setLong(rtpTimestamp, 16, 20);

        datagramPacket.setLength(PACKET_LENGTH);
        multicastSocket.send(datagramPacket);
    }
}
