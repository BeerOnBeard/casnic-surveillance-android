package com.assortedsolutions.streaming.hw;

import java.nio.ByteBuffer;
import android.media.MediaCodecInfo;

/**
 * Converts from NV21 to YUV420 semi planar or planar.
 */
public class NV21Convertor
{
    private int sliceHeight;
    private int height;
    private int stride;
    private int width;
    private int size;
    private boolean planar;
    private boolean panesReversed = false;
    private int yPadding;
    private byte[] buffer;

    public void setSize(int width, int height)
    {
        this.height = height;
        this.width = width;
        sliceHeight = height;
        stride = width;
        size = this.width * this.height;
    }

    public void setStride(int width)
    {
        stride = width;
    }

    public void setSliceHeigth(int height)
    {
        sliceHeight = height;
    }

    public void setPlanar(boolean planar)
    {
        this.planar = planar;
    }

    public void setYPadding(int padding)
    {
        yPadding = padding;
    }

    public int getBufferSize()
    {
        return 3 * size / 2;
    }

    public void setEncoderColorFormat(int colorFormat)
    {
        switch (colorFormat)
        {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                setPlanar(false);
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                setPlanar(true);
                break;
        }
    }

    public void setColorPanesReversed(boolean b)
    {
        panesReversed = b;
    }

    public int getStride()
    {
        return stride;
    }

    public int getSliceHeigth()
    {
        return sliceHeight;
    }

    public int getYPadding()
    {
        return yPadding;
    }

    public boolean getPlanar()
    {
        return planar;
    }

    public boolean getUVPanesReversed()
    {
        return panesReversed;
    }

    public void convert(byte[] data, ByteBuffer buffer)
    {
        byte[] result = convert(data);
        int min = buffer.capacity() < data.length ? buffer.capacity() : data.length;
        buffer.put(result, 0, min);
    }

    public byte[] convert(byte[] data)
    {
        // A buffer large enough for every case
        if (buffer == null || buffer.length != 3 * sliceHeight * stride / 2 + yPadding)
        {
            buffer = new byte[3 * sliceHeight * stride / 2 + yPadding];
        }

        if (!planar)
        {
            if (sliceHeight == height && stride == width)
            {
                // Swaps U and V
                if (!panesReversed)
                {
                    for (int i = size; i < size + size / 2; i += 2)
                    {
                        buffer[0] = data[i + 1];
                        data[i + 1] = data[i];
                        data[i] = buffer[0];
                    }
                }

                if (yPadding > 0)
                {
                    System.arraycopy(data, 0, buffer, 0, size);
                    System.arraycopy(data, size, buffer, size + yPadding, size / 2);
                    return buffer;
                }

                return data;
            }
        }
        else
        {
            if (sliceHeight == height && stride == width)
            {
                // De-interleave U and V
                if (!panesReversed)
                {
                    for (int i = 0; i < size / 4; i += 1)
                    {
                        buffer[i] = data[size + 2 * i + 1];
                        buffer[size / 4 + i] = data[size + 2 * i];
                    }
                }
                else
                {
                    for (int i = 0; i < size / 4; i += 1)
                    {
                        buffer[i] = data[size + 2 * i];
                        buffer[size / 4 + i] = data[size + 2 * i + 1];
                    }
                }

                if (yPadding == 0)
                {
                    System.arraycopy(buffer, 0, data, size, size /2);
                }
                else
                {
                    System.arraycopy(data, 0, buffer, 0, size);
                    System.arraycopy(buffer, 0, buffer, size + yPadding, size / 2);
                    return buffer;
                }

                return data;
            }
        }

        return data;
    }
}
