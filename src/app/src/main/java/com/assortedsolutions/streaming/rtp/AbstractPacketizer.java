package com.assortedsolutions.streaming.rtp;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Random;

/**
 *
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
abstract public class AbstractPacketizer
{
    protected static final int rtpHeaderLength = RtpSocket.RTP_HEADER_LENGTH;

    // Maximum size of RTP packets
    protected final static int MAXPACKETSIZE = RtpSocket.MTU - 28;

    protected RtpSocket socket = null;
    protected InputStream inputStream = null;
    protected byte[] buffer;

    protected long timestamp = 0;

    public AbstractPacketizer()
    {
        int ssrc = new Random().nextInt();
        timestamp = new Random().nextInt();
        socket = new RtpSocket();
        socket.setSSRC(ssrc);
    }

    public RtpSocket getRtpSocket() { return socket; }

    public int getSSRC() { return socket.getSSRC(); }

    public void setInputStream(InputStream is)
    {
        this.inputStream = is;
    }

    public void setTimeToLive(int ttl) throws IOException
    {
        socket.setTimeToLive(ttl);
    }

    /**
     * Sets the destination of the stream.
     * @param dest The destination address of the stream
     * @param rtpPort Destination port that will be used for RTP
     * @param rtcpPort Destination port that will be used for RTCP
     */
    public void setDestination(InetAddress dest, int rtpPort, int rtcpPort)
    {
        socket.setDestination(dest, rtpPort, rtcpPort);
    }

    /** Starts the packetizer. */
    public abstract void start();

    /** Stops the packetizer. */
    public abstract void stop();

    /** Updates data for RTCP SR and sends the packet. */
    protected void send(int length) throws IOException
    {
        socket.commitBuffer(length);
    }
}
