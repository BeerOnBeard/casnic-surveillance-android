package com.assortedsolutions.streaming.rtp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.util.Log;

/**
 * An InputStream that uses data from a MediaCodec.
 * The purpose of this class is to interface existing RTP packetizers of
 * libstreaming with the new MediaCodec API. This class is not thread safe !
 */
public class MediaCodecInputStream extends InputStream
{
    public final String TAG = "MediaCodecInputStream";

    private MediaCodec mediaCodec = null;
    private BufferInfo bufferInfo = new BufferInfo();
    private ByteBuffer[] buffers = null;
    private ByteBuffer buffer = null;
    private int index = -1;
    private boolean closed = false;

    public MediaFormat mediaFormat;

    public MediaCodecInputStream(MediaCodec mediaCodec)
    {
        this.mediaCodec = mediaCodec;
        buffers = this.mediaCodec.getOutputBuffers();
    }

    @Override
    public void close() {
        closed = true;
    }

    @Override
    public int read() { return 0; }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException
    {
        int min = 0;

        try
        {
            if (this.buffer == null)
            {
                while (!Thread.interrupted() && !closed)
                {
                    index = mediaCodec.dequeueOutputBuffer(bufferInfo, 500000);

                    if (index >= 0)
                    {
                        this.buffer = buffers[index];
                        this.buffer.position(0);
                        break;
                    }
                    else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
                    {
                        buffers = mediaCodec.getOutputBuffers();
                    }
                    else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
                    {
                        mediaFormat = mediaCodec.getOutputFormat();
                        Log.i(TAG, mediaFormat.toString());
                    }
                    else if (index == MediaCodec.INFO_TRY_AGAIN_LATER)
                    {
                        Log.v(TAG,"No buffer available...");
                    }
                    else
                    {
                        Log.e(TAG,"Message: " + index);
                    }
                }
            }

            if (closed)
            {
                throw new IOException("This InputStream was closed");
            }

            min = length < bufferInfo.size - this.buffer.position() ? length : bufferInfo.size - this.buffer.position();
            this.buffer.get(buffer, offset, min);
            if (this.buffer.position() >= bufferInfo.size)
            {
                mediaCodec.releaseOutputBuffer(index, false);
                this.buffer = null;
            }
        }
        catch (RuntimeException e)
        {
            Log.e(TAG, "Reading threw", e);
        }

        return min;
    }

    public int available()
    {
        if (buffer != null)
        {
            return bufferInfo.size - buffer.position();
        }

        return 0;
    }

    public BufferInfo getLastBufferInfo()
    {
        return bufferInfo;
    }
}
