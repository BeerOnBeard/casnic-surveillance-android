package com.assortedsolutions.streaming.rtp;

/** Used in packetizers to estimate timestamps in RTP packets. */
class PacketizerStatistics
{
    public final static String TAG = "PacketizerStatistics";

    private int count = 700;
    private int c = 0;
    private float m = 0;
    private float q = 0;
    private long elapsed = 0;
    private long start = 0;
    private long duration = 0;
    private long period = 10000000000L;
    private boolean initOffset = false;

    public PacketizerStatistics() {}

    public void reset()
    {
        initOffset = false;
        q = 0;
        m = 0;
        c = 0;
        elapsed = 0;
        start = 0;
        duration = 0;
    }

    public void push(long value)
    {
        elapsed += value;
        if (elapsed > period)
        {
            elapsed = 0;
            long now = System.nanoTime();
            if (!initOffset || (now - start < 0))
            {
                start = now;
                duration = 0;
                initOffset = true;
            }

            // Prevents drifting issues by comparing the real duration of the
            // stream with the sum of all temporal lengths of RTP packets.
            value += (now - start) - duration;
        }

        if (c < 5)
        {
            // We ignore the first 20 measured values because they may not be accurate
            c++;
            m = value;
        }
        else
        {
            m = (m * q + value) / (q + 1);
            if (q < count)
            {
                q++;
            }
        }
    }

    public long average()
    {
        long l = (long)m;
        duration += l;
        return l;
    }
}
