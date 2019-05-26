package com.assortedsolutions.streaming.rtp;

import android.os.SystemClock;

/**
 * Computes an average bit rate.
 **/
class AverageBitrate
{
    private final static long RESOLUTION = 200;

    private long oldNow, now, delta;
    private long[] elapsed, sum;
    private int count, index, total;
    private int size;

    public AverageBitrate()
    {
        size = 5000 / ((int)RESOLUTION);
        reset();
    }

    public void reset()
    {
        sum = new long[size];
        elapsed = new long[size];
        now = SystemClock.elapsedRealtime();
        oldNow = now;
        count = 0;
        delta = 0;
        total = 0;
        index = 0;
    }

    public void push(int length)
    {
        now = SystemClock.elapsedRealtime();

        if (count > 0)
        {
            delta += now - oldNow;
            total += length;

            if (delta > RESOLUTION)
            {
                sum[index] = total;
                total = 0;
                elapsed[index] = delta;
                delta = 0;
                index++;

                if (index >= size)
                {
                    index = 0;
                }
            }
        }

        oldNow = now;
        count++;
    }

    public int average()
    {
        long delta = 0, sum = 0;
        for (int i = 0; i < size; i++)
        {
            sum += this.sum[i];
            delta += elapsed[i];
        }

        return (int) (delta > 0 ? 8000 * sum / delta : 0);
    }
}
