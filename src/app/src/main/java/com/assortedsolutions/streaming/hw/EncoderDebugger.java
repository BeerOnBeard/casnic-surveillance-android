package com.assortedsolutions.streaming.hw;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import com.assortedsolutions.streaming.hw.CodecManager.Codec;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

/**
 *
 * The purpose of this class is to detect and by-pass some bugs (or underspecified configuration) that
 * encoders available through the MediaCodec API may have. <br />
 * Feeding the encoder with a surface is not tested here.
 * Some bugs you may have encountered:<br />
 * <ul>
 * <li>U and V panes reversed</li>
 * <li>Some padding is needed after the Y pane</li>
 * <li>stride!=width or slice-height!=height</li>
 * </ul>
 */
public class EncoderDebugger
{
    public final static String TAG = "EncoderDebugger";

    /** Prefix that will be used for all shared preferences saved by libstreaming. */
    private static final String PREF_PREFIX = "libstreaming-";

    /**
     * If this is set to false the test will be run only once and the result
     * will be saved in the shared preferences.
     */
    private static final boolean DEBUG = false;

    /** Set this to true to see more logs. */
    private static final boolean VERBOSE = false;

    /** Will be incremented every time this test is modified. */
    private static final int VERSION = 3;

    /** Bit rate that will be used with the encoder. */
    private final static int BITRATE = 1000000;

    /** Frame rate that will be used to test the encoder. */
    private final static int FRAMERATE = 20;

    private final static String MIME_TYPE = "video/avc";

    private final static int NB_DECODED = 34;
    private final static int NB_ENCODED = 50;

    private int decoderColorFormat;
    private int encoderColorFormat;
    private String decoderName;
    private String encoderName;
    private String errorLog;
    private MediaCodec encoder;
    private MediaCodec decoder;
    private int width;
    private int height;
    private int size;
    private byte[] SPS;
    private byte[] PPS;
    private byte[] data;
    private byte[] initialImage;
    private MediaFormat decOutputFormat;
    private NV21Convertor nv21Convertor;
    private SharedPreferences preferences;
    private byte[][] video;
    private byte[][] decodedVideo;
    private String base64PPS;
    private String base64SPS;

    public synchronized static void asyncDebug(final Context context, final int width, final int height)
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try
                {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                    debug(prefs, width, height);
                }
                catch (Exception e)
                {
                    Log.e(TAG, "Starting debug threw", e);
                }
            }
        }).start();
    }

    public synchronized static EncoderDebugger debug(Context context, int width, int height)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return debug(prefs, width, height);
    }

    public synchronized static EncoderDebugger debug(SharedPreferences prefs, int width, int height)
    {
        EncoderDebugger debugger = new EncoderDebugger(prefs, width, height);
        debugger.debug();
        return debugger;
    }

    public String getBase64PPS()
    {
        return base64PPS;
    }

    public String getBase64SPS()
    {
        return base64SPS;
    }

    public String getEncoderName()
    {
        return encoderName;
    }

    public int getEncoderColorFormat()
    {
        return encoderColorFormat;
    }

    /** This {@link NV21Convertor} will do the necessary work to feed properly the encoder. */
    public NV21Convertor getNV21Convertor()
    {
        return nv21Convertor;
    }

    /** A log of all the errors that occurred during the test. */
    public String getErrorLog()
    {
        return errorLog;
    }

    private EncoderDebugger(SharedPreferences prefs, int width, int height)
    {
        preferences = prefs;
        this.width = width;
        this.height = height;
        size = width*height;
        reset();
    }

    private void reset()
    {
        nv21Convertor = new NV21Convertor();
        video = new byte[NB_ENCODED][];
        decodedVideo = new byte[NB_DECODED][];
        errorLog = "";
        PPS = null;
        SPS = null;
    }

    private void debug()
    {
        // If testing the phone again is not needed,
        // we just restore the result from the shared preferences
        if (!checkTestNeeded())
        {
            String resolution = width + "x" + height + "-";

            boolean success = preferences.getBoolean(PREF_PREFIX + resolution + "success",false);
            if (!success)
            {
                throw new RuntimeException("Phone not supported with this resolution (" + width + "x" + height + ")");
            }

            nv21Convertor.setSize(width, height);
            nv21Convertor.setSliceHeigth(preferences.getInt(PREF_PREFIX + resolution + "sliceHeight", 0));
            nv21Convertor.setStride(preferences.getInt(PREF_PREFIX + resolution + "stride", 0));
            nv21Convertor.setYPadding(preferences.getInt(PREF_PREFIX + resolution + "padding", 0));
            nv21Convertor.setPlanar(preferences.getBoolean(PREF_PREFIX + resolution + "planar", false));
            nv21Convertor.setColorPanesReversed(preferences.getBoolean(PREF_PREFIX + resolution + "reversed", false));
            encoderName = preferences.getString(PREF_PREFIX + resolution + "encoderName", "");
            encoderColorFormat = preferences.getInt(PREF_PREFIX + resolution + "colorFormat", 0);
            base64PPS = preferences.getString(PREF_PREFIX + resolution + "pps", "");
            base64SPS = preferences.getString(PREF_PREFIX + resolution + "sps", "");

            return;
        }

        Log.d(TAG, ">>>> Testing the phone for resolution " + width + "x" + height);

        // Builds a list of available encoders and decoders we may be able to use
        // because they support some nice color formats
        Codec[] encoders = CodecManager.findEncodersForMimeType(MIME_TYPE);
        Codec[] decoders = CodecManager.findDecodersForMimeType(MIME_TYPE);

        int count = 0;
        int n = 1;
        for (Codec encoder1 : encoders)
        {
            count += encoder1.formats.length;
        }

        // Tries available encoders
        for (Codec encoder1 : encoders)
        {
            for (int j = 0; j < encoder1.formats.length; j++)
            {
                reset();

                encoderName = encoder1.name;
                encoderColorFormat = encoder1.formats[j];

                Log.v(TAG, ">> Test " + (n++) + "/" + count + ": " + encoderName + " with color format " + encoderColorFormat + " at " + width + "x" + height);

                // Converts from NV21 to YUV420 with the specified parameters
                nv21Convertor.setSize(width, height);
                nv21Convertor.setSliceHeigth(height);
                nv21Convertor.setStride(width);
                nv21Convertor.setYPadding(0);
                nv21Convertor.setEncoderColorFormat(encoderColorFormat);

                // /!\ NV21Convertor can directly modify the input
                createTestImage();
                data = nv21Convertor.convert(initialImage);

                try
                {
                    // Starts the encoder
                    configureEncoder();
                    searchSPSandPPS();

                    Log.v(TAG, "SPS and PPS in b64: SPS=" + base64SPS + ", PPS=" + base64PPS);

                    // Feeds the encoder with an image repeatedly to produce some NAL units
                    encode();

                    // We now try to decode the NALs with decoders available on the phone
                    boolean decoded = false;
                    for (int k = 0; k < decoders.length && !decoded; k++)
                    {
                        for (int l = 0; l < decoders[k].formats.length && !decoded; l++)
                        {
                            decoderName = decoders[k].name;
                            decoderColorFormat = decoders[k].formats[l];
                            try
                            {
                                configureDecoder();
                            } catch (Exception e)
                            {
                                Log.d(TAG, decoderName + " can't be used with " + decoderColorFormat + " at " + width + "x" + height, e);

                                releaseDecoder();
                                break;
                            }

                            try
                            {
                                decode(true);
                                Log.d(TAG, decoderName + " successfully decoded the NALs (color format " + decoderColorFormat + ")");

                                decoded = true;
                            }
                            catch (Exception e)
                            {
                                Log.e(TAG, decoderName + " failed to decode the NALs", e);
                            }
                            finally
                            {
                                releaseDecoder();
                            }
                        }
                    }

                    if (!decoded)
                    {
                        throw new RuntimeException("Failed to decode NALs from the encoder.");
                    }

                    // Compares the image before and after
                    if (!compareLumaPanes())
                    {
                        // TODO: try again with a different stride
                        // TODO: try again with the "stride" param
                        throw new RuntimeException("It is likely that stride != width");
                    }

                    int padding;
                    if ((padding = checkPaddingNeeded()) > 0)
                    {
                        if (padding < 4096)
                        {
                            Log.d(TAG, "Some padding is needed: " + padding);

                            nv21Convertor.setYPadding(padding);
                            createTestImage();
                            data = nv21Convertor.convert(initialImage);
                            encodeDecode();
                        }
                        else
                        {
                            // TODO: try again with a different sliceHeight
                            // TODO: try again with the "slice-height" param
                            throw new RuntimeException("It is likely that sliceHeight != height");
                        }
                    }

                    createTestImage();
                    if (!compareChromaPanes(false))
                    {
                        if (compareChromaPanes(true))
                        {
                            nv21Convertor.setColorPanesReversed(true);
                            Log.d(TAG, "U and V pane are reversed");
                        }
                        else
                        {
                            throw new RuntimeException("Incorrect U or V pane...");
                        }
                    }

                    saveTestResult(true);
                    Log.v(TAG, "The encoder " + encoderName + " is usable with resolution " + width + "x" + height);
                    return;
                }
                catch (Exception e)
                {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    e.printStackTrace(pw);
                    String stack = sw.toString();
                    String str = "Encoder " + encoderName + " cannot be used with color format " + encoderColorFormat;
                    Log.e(TAG, str, e);
                    errorLog += str + "\n" + stack;
                }
                finally
                {
                    releaseEncoder();
                }
            }
        }

        saveTestResult(false);
        Log.e(TAG,"No usable encoder were found on the phone for resolution " + width + "x" + height);
        throw new RuntimeException("No usable encoder were found on the phone for resolution " + width + "x" + height);
    }

    private boolean checkTestNeeded()
    {
        String resolution = width + "x" + height + "-";

        // Forces the test
        if (DEBUG || preferences ==null)
        {
            return true;
        }

        // If the sdk has changed on the phone, or the version of the test
        // it has to be run again
        if (preferences.contains(PREF_PREFIX + resolution + "lastSdk"))
        {
            int lastSdk = preferences.getInt(PREF_PREFIX + resolution + "lastSdk", 0);
            int lastVersion = preferences.getInt(PREF_PREFIX + resolution + "lastVersion", 0);
            return Build.VERSION.SDK_INT > lastSdk || VERSION > lastVersion;
        }
        else
        {
            return true;
        }
    }


    /**
     * Saves the result of the test in the shared preferences,
     * we will run it again only if the SDK has changed on the phone,
     * or if this test has been modified.
     */
    private void saveTestResult(boolean success)
    {
        String resolution = width + "x" + height + "-";
        Editor editor = preferences.edit();

        editor.putBoolean(PREF_PREFIX + resolution + "success", success);

        if (success)
        {
            editor.putInt(PREF_PREFIX + resolution + "lastSdk", Build.VERSION.SDK_INT);
            editor.putInt(PREF_PREFIX + resolution + "lastVersion", VERSION);
            editor.putInt(PREF_PREFIX + resolution + "sliceHeight", nv21Convertor.getSliceHeigth());
            editor.putInt(PREF_PREFIX + resolution + "stride", nv21Convertor.getStride());
            editor.putInt(PREF_PREFIX + resolution + "padding", nv21Convertor.getYPadding());
            editor.putBoolean(PREF_PREFIX + resolution + "planar", nv21Convertor.getPlanar());
            editor.putBoolean(PREF_PREFIX + resolution + "reversed", nv21Convertor.getUVPanesReversed());
            editor.putString(PREF_PREFIX + resolution + "encoderName", encoderName);
            editor.putInt(PREF_PREFIX + resolution + "colorFormat", encoderColorFormat);
            editor.putString(PREF_PREFIX + resolution + "encoderName", encoderName);
            editor.putString(PREF_PREFIX + resolution + "pps", base64PPS);
            editor.putString(PREF_PREFIX + resolution + "sps", base64SPS);
        }

        editor.apply();
    }

    /**
     * Creates the test image that will be used to feed the encoder.
     */
    private void createTestImage()
    {
        initialImage = new byte[3 * size / 2];
        for (int i = 0; i < size; i++)
        {
            initialImage[i] = (byte) (40 + i % 199);
        }

        for (int i = size; i < 3 * size / 2; i += 2)
        {
            initialImage[i] = (byte) (40 + i % 200);
            initialImage[i + 1] = (byte) (40 + (i + 99) % 200);
        }
    }

    /**
     * Compares the Y pane of the initial image, and the Y pane
     * after having encoded & decoded the image.
     */
    private boolean compareLumaPanes()
    {
        int d;
        int e;
        int f = 0;

        for (int j = 0; j < NB_DECODED; j++)
        {
            for (int i = 0; i < size; i += 10)
            {
                d = (initialImage[i] & 0xFF) - (decodedVideo[j][i] & 0xFF);
                e = (initialImage[i + 1] & 0xFF) - (decodedVideo[j][i + 1] & 0xFF);
                d = d < 0 ? -d : d;
                e = e < 0 ? -e : e;
                if (d > 50 && e > 50)
                {
                    decodedVideo[j] = null;
                    f++;
                    break;
                }
            }
        }

        return f <= NB_DECODED / 2;
    }

    private int checkPaddingNeeded()
    {
        int i = 0;
        int j = 3 * size / 2 - 1;
        int max = 0;
        int[] r = new int[NB_DECODED];

        for (int k = 0; k < NB_DECODED; k++)
        {
            if (decodedVideo[k] != null)
            {
                i = 0;
                while (i < j && (decodedVideo[k][j - i] & 0xFF) < 50)
                {
                    i+=2;
                }

                if (i > 0)
                {
                    r[k] = ((i >> 6) << 6);
                    max = r[k] > max ? r[k] : max;
                    Log.i(TAG,"Padding needed: " + r[k]);
                }
                else
                {
                    Log.v(TAG,"No padding needed.");
                }
            }
        }

        return ((max >> 6) << 6);
    }

    /**
     * Compares the U or V pane of the initial image, and the U or V pane
     * after having encoded & decoded the image.
     */
    private boolean compareChromaPanes(boolean crossed)
    {
        int d;
        int f = 0;

        for (int j = 0; j < NB_DECODED; j++)
        {
            if (decodedVideo[j] != null)
            {
                // We compare the U and V pane before and after
                if (!crossed)
                {
                    for (int i = size; i < 3 * size / 2; i += 1)
                    {
                        d = (initialImage[i] & 0xFF) - (decodedVideo[j][i] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50)
                        {
                            f++;
                            break;
                        }
                    }

                    // We compare the V pane before with the U pane after
                }
                else
                {
                    for (int i = size; i < 3 * size / 2; i += 2)
                    {
                        d = (initialImage[i] & 0xFF) - (decodedVideo[j][i + 1] & 0xFF);
                        d = d < 0 ? -d : d;
                        if (d > 50)
                        {
                            f++;
                        }
                    }
                }
            }
        }

        return f <= NB_DECODED / 2;
    }

    /**
     * Converts the image obtained from the decoder to NV21.
     */
    private void convertToNV21(int k)
    {
        byte[] buffer = new byte[3 * size / 2];

        int stride = width;
        int sliceHeight = height;
        int colorFormat = decoderColorFormat;
        boolean planar = false;

        if (decOutputFormat != null)
        {
            MediaFormat format = decOutputFormat;
            if (format != null)
            {
                if (format.containsKey("slice-height"))
                {
                    sliceHeight = format.getInteger("slice-height");
                    if (sliceHeight < height)
                    {
                        sliceHeight = height;
                    }
                }

                if (format.containsKey("stride"))
                {
                    stride = format.getInteger("stride");
                    if (stride < width)
                    {
                        stride = width;
                    }
                }

                if (format.containsKey(MediaFormat.KEY_COLOR_FORMAT) && format.getInteger(MediaFormat.KEY_COLOR_FORMAT) > 0)
                {
                    colorFormat = format.getInteger(MediaFormat.KEY_COLOR_FORMAT);
                }
            }
        }

        switch (colorFormat)
        {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                planar = false;
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                planar = true;
                break;
        }

        for (int i = 0; i < size; i++)
        {
            if (i % width == 0)
            {
                i += stride - width;
            }

            buffer[i] = decodedVideo[k][i];
        }

        if (!planar)
        {
            for (int i = 0, j = 0; j < size / 4; i += 1, j += 1)
            {
                if (i % width / 2 == 0)
                {
                    i += (stride - width) / 2;
                }

                buffer[size + 2 * j + 1] = decodedVideo[k][stride * sliceHeight + 2 * i];
                buffer[size + 2 * j] = decodedVideo[k][stride * sliceHeight + 2 * i + 1];
            }
        }
        else
        {
            for (int i = 0, j = 0; j < size / 4; i += 1, j += 1)
            {
                if (i % width / 2 == 0)
                {
                    i += (stride - width) / 2;
                }

                buffer[size + 2 * j + 1] = decodedVideo[k][stride * sliceHeight + i];
                buffer[size + 2 * j] = decodedVideo[k][stride * sliceHeight * 5 / 4 + i];
            }
        }

        decodedVideo[k] = buffer;
    }

    /**
     * Instantiates and starts the encoder.
     * @throws IOException The encoder cannot be configured
     */
    private void configureEncoder() throws IOException
    {
        encoder = MediaCodec.createByCodecName(encoderName);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAMERATE);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, encoderColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
    }

    private void releaseEncoder()
    {
        if (encoder == null)
        {
            return;
        }

        try
        {
            encoder.stop();
        }
        catch (Exception ignore)
        {
            Log.e(TAG, "Stopping encoder threw", ignore);
        }

        try
        {
            encoder.release();
        }
        catch (Exception ignore)
        {
            Log.e(TAG, "Releasing encoder threw", ignore);
        }
    }

    /**
     * Instantiates and starts the decoder.
     * @throws IOException The decoder cannot be configured
     */
    private void configureDecoder() throws IOException
    {
        byte[] prefix = new byte[] { 0x00, 0x00, 0x00, 0x01 };

        ByteBuffer csd0 = ByteBuffer.allocate(4 + SPS.length + 4 + PPS.length);
        csd0.put(new byte[] { 0x00, 0x00, 0x00, 0x01 });
        csd0.put(SPS);
        csd0.put(new byte[] { 0x00, 0x00, 0x00, 0x01 });
        csd0.put(PPS);

        decoder = MediaCodec.createByCodecName(decoderName);
        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        mediaFormat.setByteBuffer("csd-0", csd0);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decoderColorFormat);
        decoder.configure(mediaFormat, null, null, 0);
        decoder.start();

        ByteBuffer[] decInputBuffers = decoder.getInputBuffers();

        int decInputIndex = decoder.dequeueInputBuffer(1000000 / FRAMERATE);
        if (decInputIndex >= 0)
        {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(SPS);
            decoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        }
        else
        {
            Log.e(TAG,"No buffer available!");
        }

        decInputIndex = decoder.dequeueInputBuffer(1000000 / FRAMERATE);
        if (decInputIndex >= 0)
        {
            decInputBuffers[decInputIndex].clear();
            decInputBuffers[decInputIndex].put(prefix);
            decInputBuffers[decInputIndex].put(PPS);
            decoder.queueInputBuffer(decInputIndex, 0, decInputBuffers[decInputIndex].position(), timestamp(), 0);
        }
        else
        {
            Log.e(TAG,"No buffer available!");
        }
    }

    private void releaseDecoder()
    {
        if (decoder == null)
        {
            return;
        }

        try
        {
            decoder.stop();
        }
        catch (Exception ignore)
        {
            Log.e(TAG, "Stopping decoder threw", ignore);
        }

        try
        {
            decoder.release();
        }
        catch (Exception ignore)
        {
            Log.e(TAG, "Releasing decoder threw", ignore);
        }
    }

    /**
     * Tries to obtain the SPS and the PPS for the encoder.
     */
    private long searchSPSandPPS()
    {
        ByteBuffer[] inputBuffers = encoder.getInputBuffers();
        ByteBuffer[] outputBuffers = encoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();
        byte[] csd = new byte[128];
        int len = 0;
        int p = 4;
        int q = 4;
        long elapsed = 0;
        long now = timestamp();

        while (elapsed < 3000000 && (SPS == null || PPS == null))
        {
            // Some encoders won't give us the SPS and PPS unless they receive something to encode first...
            int bufferIndex = encoder.dequeueInputBuffer(1000000 / FRAMERATE);
            if (bufferIndex >= 0)
            {
                check(inputBuffers[bufferIndex].capacity() >= data.length, "The input buffer is not big enough.");
                inputBuffers[bufferIndex].clear();
                inputBuffers[bufferIndex].put(data, 0, data.length);
                encoder.queueInputBuffer(bufferIndex, 0, data.length, timestamp(), 0);
            }
            else
            {
                Log.e(TAG,"No buffer available !");
            }

            // We are looking for the SPS and the PPS here. As always, Android is very inconsistent, I have observed that some
            // encoders will give those parameters through the MediaFormat object (that is the normal behaviour).
            // But some other will not, in that case we try to find a NAL unit of type 7 or 8 in the byte stream outputed by the encoder...

            int index = encoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);

            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
            {
                // The PPS and PPS should be there
                MediaFormat format = encoder.getOutputFormat();
                ByteBuffer spsb = format.getByteBuffer("csd-0");
                ByteBuffer ppsb = format.getByteBuffer("csd-1");
                SPS = new byte[spsb.capacity() - 4];
                spsb.position(4);
                spsb.get(SPS,0, SPS.length);
                PPS = new byte[ppsb.capacity() - 4];
                ppsb.position(4);
                ppsb.get(PPS,0, PPS.length);
                break;
            }
            else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                outputBuffers = encoder.getOutputBuffers();
            }
            else if (index >= 0)
            {
                len = info.size;
                if (len < 128)
                {
                    outputBuffers[index].get(csd,0,len);
                    if (len > 0 && csd[0] == 0 && csd[1] == 0 && csd[2] == 0 && csd[3] == 1)
                    {
                        // Parses the SPS and PPS, they could be in two different packets and in a different order
                        //depending on the phone so we don't make any assumption about that
                        while (p < len)
                        {
                            while (!(csd[p + 0] == 0 && csd[p + 1] == 0 && csd[p + 2] == 0 && csd[p + 3] == 1) && p + 3 < len)
                            {
                                p++;
                            }

                            if (p + 3 >= len)
                            {
                                p=len;
                            }

                            if ((csd[q] & 0x1F) == 7)
                            {
                                SPS = new byte[p - q];
                                System.arraycopy(csd, q, SPS, 0, p - q);
                            }
                            else
                            {
                                PPS = new byte[p - q];
                                System.arraycopy(csd, q, PPS, 0, p - q);
                            }

                            p += 4;
                            q = p;
                        }
                    }
                }

                encoder.releaseOutputBuffer(index, false);
            }

            elapsed = timestamp() - now;
        }

        check(PPS != null && SPS != null, "Could not determine the SPS & PPS.");
        base64PPS = Base64.encodeToString(PPS, 0, PPS.length, Base64.NO_WRAP);
        base64SPS = Base64.encodeToString(SPS, 0, SPS.length, Base64.NO_WRAP);

        return elapsed;
    }

    private long encode()
    {
        int n = 0;
        long elapsed = 0;
        long now = timestamp();
        int encOutputIndex = 0;
        int encInputIndex = 0;
        BufferInfo info = new BufferInfo();
        ByteBuffer[] encInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encOutputBuffers = encoder.getOutputBuffers();

        while (elapsed < 5000000)
        {
            // Feeds the encoder with an image
            encInputIndex = encoder.dequeueInputBuffer(1000000 / FRAMERATE);
            if (encInputIndex >= 0)
            {
                check(encInputBuffers[encInputIndex].capacity() >= data.length, "The input buffer is not big enough.");
                encInputBuffers[encInputIndex].clear();
                encInputBuffers[encInputIndex].put(data, 0, data.length);
                encoder.queueInputBuffer(encInputIndex, 0, data.length, timestamp(), 0);
            }
            else
            {
                Log.d(TAG,"No buffer available!");
            }

            // Tries to get a NAL unit
            encOutputIndex = encoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);
            if (encOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                encOutputBuffers = encoder.getOutputBuffers();
            }
            else if (encOutputIndex >= 0)
            {
                video[n] = new byte[info.size];
                encOutputBuffers[encOutputIndex].clear();
                encOutputBuffers[encOutputIndex].get(video[n++], 0, info.size);
                encoder.releaseOutputBuffer(encOutputIndex, false);
                if (n >= NB_ENCODED)
                {
                    flushMediaCodec(encoder);
                    return elapsed;
                }
            }

            elapsed = timestamp() - now;
        }

        throw new RuntimeException("The encoder is too slow.");
    }

    /**
     * @param withPrefix If set to true, the decoder will be fed with NALs preceeded with 0x00000001.
     * @return How long it took to decode all the NALs
     */
    private long decode(boolean withPrefix)
    {
        int n = 0;
        int i = 0;
        int j = 0;
        long elapsed = 0;
        long now = timestamp();
        int decInputIndex = 0;
        int decOutputIndex = 0;
        ByteBuffer[] decInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decOutputBuffers = decoder.getOutputBuffers();
        BufferInfo info = new BufferInfo();

        while (elapsed < 3000000)
        {
            // Feeds the decoder with a NAL unit
            if (i < NB_ENCODED)
            {
                decInputIndex = decoder.dequeueInputBuffer(1000000 / FRAMERATE);
                if (decInputIndex >= 0)
                {
                    int l1 = decInputBuffers[decInputIndex].capacity();
                    int l2 = video[i].length;
                    decInputBuffers[decInputIndex].clear();

                    if ((withPrefix && hasPrefix(video[i])) || (!withPrefix && !hasPrefix(video[i])))
                    {
                        check(l1 >= l2, "The decoder input buffer is not big enough (nal=" + l2 + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(video[i],0, video[i].length);
                    }
                    else if (withPrefix && !hasPrefix(video[i]))
                    {
                        check(l1 >= l2 + 4, "The decoder input buffer is not big enough (nal=" + (l2 + 4) + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(new byte[] { 0, 0, 0, 1 });
                        decInputBuffers[decInputIndex].put(video[i],0, video[i].length);
                    }
                    else if (!withPrefix && hasPrefix(video[i]))
                    {
                        check(l1 >= l2 - 4, "The decoder input buffer is not big enough (nal=" + (l2 - 4) + ", capacity=" + l1 + ").");
                        decInputBuffers[decInputIndex].put(video[i],4, video[i].length - 4);
                    }

                    decoder.queueInputBuffer(decInputIndex, 0, l2, timestamp(), 0);
                    i++;
                }
                else
                {
                    Log.d(TAG,"No buffer available!");
                }
            }

            // Tries to get a decoded image
            decOutputIndex = decoder.dequeueOutputBuffer(info, 1000000 / FRAMERATE);
            if (decOutputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)
            {
                decOutputBuffers = decoder.getOutputBuffers();
            }
            else if (decOutputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)
            {
                decOutputFormat = decoder.getOutputFormat();
            }
            else if (decOutputIndex >= 0)
            {
                if (n > 2)
                {
                    // We have successfully encoded and decoded an image !
                    int length = info.size;
                    decodedVideo[j] = new byte[length];
                    decOutputBuffers[decOutputIndex].clear();
                    decOutputBuffers[decOutputIndex].get(decodedVideo[j], 0, length);

                    // Converts the decoded frame to NV21
                    convertToNV21(j);
                    if (j >= NB_DECODED - 1)
                    {
                        flushMediaCodec(decoder);
                        Log.v(TAG, "Decoding " + n + " frames took " + elapsed / 1000 + " ms");
                        return elapsed;
                    }

                    j++;
                }

                decoder.releaseOutputBuffer(decOutputIndex, false);
                n++;
            }

            elapsed = timestamp() - now;
        }

        throw new RuntimeException("The decoder did not decode anything.");
    }

    /**
     * Makes sure the NAL has a header or not.
     * @param withPrefix If set to true, the NAL will be preceded with 0x00000001.
     */
    private boolean hasPrefix(byte[] nal)
    {
        return nal[0] == 0 && nal[1] == 0 && nal[2] == 0 && nal[3] == 0x01;
    }

    /**
     * @throws IOException The decoder cannot be configured.
     */
    private void encodeDecode() throws IOException
    {
        encode();
        try
        {
            configureDecoder();
            decode(true);
        }
        finally
        {
            releaseDecoder();
        }
    }

    private void flushMediaCodec(MediaCodec mc)
    {
        int index = 0;
        BufferInfo info = new BufferInfo();
        while (index != MediaCodec.INFO_TRY_AGAIN_LATER)
        {
            index = mc.dequeueOutputBuffer(info, 1000000/FRAMERATE);
            if (index >= 0)
            {
                mc.releaseOutputBuffer(index, false);
            }
        }
    }

    private void check(boolean condition, String message)
    {
        if (!condition)
        {
            Log.e(TAG, message);
            throw new IllegalStateException(message);
        }
    }

    private long timestamp()
    {
        return System.nanoTime() / 1000;
    }
}
