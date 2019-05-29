package com.assortedsolutions.streaming.video;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.assortedsolutions.streaming.MediaStream;
import com.assortedsolutions.streaming.Stream;
import com.assortedsolutions.streaming.exceptions.CameraInUseException;
import com.assortedsolutions.streaming.exceptions.InvalidSurfaceException;
import com.assortedsolutions.streaming.hw.EncoderDebugger;
import com.assortedsolutions.streaming.hw.NV21Convertor;
import com.assortedsolutions.streaming.rtp.MediaCodecInputStream;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

/**
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream
{
    protected final static String TAG = "VideoStream";

    protected VideoQuality requestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
    protected VideoQuality quality = requestedQuality.clone();
    protected SurfaceHolder.Callback surfaceHolderCallback = null;
    protected SurfaceView surfaceView = null;
    protected SharedPreferences settings = null;
    protected int videoEncoder;
    protected int cameraId = 0;
    protected int requestedOrientation = 0;
    protected int orientation = 0;
    protected Camera camera;
    protected Thread cameraThread;
    protected Looper cameraLooper;

    protected boolean cameraOpenedManually = true;
    protected boolean surfaceReady = false;
    protected boolean unlocked = false;
    protected boolean previewStarted = false;
    protected boolean updated = false;

    protected String mimeType;
    protected int cameraImageFormat;

    /**
     * Don't use this class directly
     * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     */
    public VideoStream(int camera)
    {
        super();
        setCamera(camera);
    }

    /**
     * Sets the camera that will be used to capture video.
     * You can call this method at any time and changes will take effect next time you start the stream.
     * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
     */
    public void setCamera(int camera)
    {
        CameraInfo cameraInfo = new CameraInfo();
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++)
        {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == camera)
            {
                cameraId = i;
                break;
            }
        }
    }

    /**	Switch between the front facing and the back facing camera of the phone.
     * If {@link #startPreview()} has been called, the preview will be  briefly interrupted.
     * If {@link #start()} has been called, the stream will be  briefly interrupted.
     * You should not call this method from the main thread if you are already streaming.
     * @throws IOException
     * @throws RuntimeException
     **/
    public void switchCamera() throws RuntimeException, IOException
    {
        if (Camera.getNumberOfCameras() == 1)
        {
            throw new IllegalStateException("Phone only has one camera !");
        }

        boolean streaming = this.streaming;
        boolean previewing = camera != null && cameraOpenedManually;
        cameraId = (cameraId == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK;
        setCamera(cameraId);
        stopPreview();

        if (previewing)
        {
            startPreview();
        }

        if (streaming)
        {
            start();
        }
    }

    /**
     * Returns the id of the camera currently selected.
     * Can be either {@link CameraInfo#CAMERA_FACING_BACK} or
     * {@link CameraInfo#CAMERA_FACING_FRONT}.
     */
    public int getCamera()
    {
        return cameraId;
    }

    /**
     * Sets a Surface to show a preview of recorded media (video).
     * You can call this method at any time and changes will take effect next time you call {@link #start()}.
     */
    public synchronized void setSurfaceView(SurfaceView view)
    {
        surfaceView = view;
        if (surfaceHolderCallback != null && surfaceView != null && surfaceView.getHolder() != null)
        {
            surfaceView.getHolder().removeCallback(surfaceHolderCallback);
        }

        if (surfaceView != null && surfaceView.getHolder() != null)
        {
            surfaceHolderCallback = new Callback()
            {
                @Override
                public void surfaceDestroyed(SurfaceHolder holder)
                {
                    surfaceReady = false;
                    stopPreview();
                    Log.d(TAG,"Surface destroyed");
                }

                @Override
                public void surfaceCreated(SurfaceHolder holder)
                {
                    Log.d(TAG, "Surface created");
                    surfaceReady = true;
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
                {
                    Log.d(TAG,"Surface Changed");
                }
            };

            surfaceView.getHolder().addCallback(surfaceHolderCallback);
            surfaceReady = true;
        }
    }

    /**
     * Sets the orientation of the preview.
     * @param orientation The orientation of the preview
     */
    public void setPreviewOrientation(int orientation)
    {
        requestedOrientation = orientation;
        updated = false;
    }

    /**
     * Sets the configuration of the stream. You can call this method at any time
     * and changes will take effect next time you call {@link #configure()}.
     * @param videoQuality Quality of the stream
     */
    public void setVideoQuality(VideoQuality videoQuality)
    {
        if (!requestedQuality.equals(videoQuality))
        {
            requestedQuality = videoQuality.clone();
            updated = false;
        }
    }

    /**
     * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called
     * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
     */
    public void setPreferences(SharedPreferences prefs)
    {
        settings = prefs;
    }

    /**
     * Configures the stream. You need to call this before calling {@link #getSessionDescription()}
     * to apply your configuration of the stream.
     */
    public synchronized void configure() throws IllegalStateException, IOException
    {
        super.configure();
        orientation = requestedOrientation;
    }

    /**
     * Starts the stream.
     * This will also open the camera and display the preview
     * if {@link #startPreview()} has not already been called.
     */
    public synchronized void start() throws IllegalStateException, IOException
    {
        if (!previewStarted)
        {
            cameraOpenedManually = false;
        }

        super.start();
        Log.d(TAG,"Stream configuration: FPS: " + quality.framerate + " Width: " + quality.resX + " Height: " + quality.resY);
    }

    /** Stops the stream. */
    public synchronized void stop()
    {
        if (camera != null)
        {
            camera.setPreviewCallbackWithBuffer(null);

            super.stop();

            // We need to restart the preview
            if (!cameraOpenedManually)
            {
                destroyCamera();
            }
            else
            {
                try
                {
                    startPreview();
                }
                catch (RuntimeException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public synchronized void startPreview() throws RuntimeException
    {
        cameraOpenedManually = true;
        if (!previewStarted)
        {
            createCamera();
            updateCamera();
        }
    }

    /**
     * Stops the preview.
     */
    public synchronized void stopPreview()
    {
        cameraOpenedManually = false;
        stop();
    }

    /**
     * Video encoding is done by a MediaCodec.
     */
    protected void encodeWithMediaCodec() throws RuntimeException, IOException
    {
        Log.d(TAG,"Video encoded using the MediaCodec API with a buffer");

        // Updates the parameters of the camera if needed
        createCamera();
        updateCamera();

        // Estimates the frame rate of the camera
        measureFramerate();

        // Starts the preview if needed
        if (!previewStarted)
        {
            try
            {
                camera.startPreview();
                previewStarted = true;
            }
            catch (RuntimeException e)
            {
                destroyCamera();
                throw e;
            }
        }

        EncoderDebugger debugger = EncoderDebugger.debug(settings, quality.resX, quality.resY);
        final NV21Convertor converter = debugger.getNV21Convertor();

        mediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", quality.resX, quality.resY);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, quality.bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, quality.framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,debugger.getEncoderColorFormat());
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mediaCodec.start();

        Camera.PreviewCallback callback = new Camera.PreviewCallback()
        {
            long now = System.nanoTime()/1000, oldnow = now, i=0;
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();

            @Override
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                oldnow = now;
                now = System.nanoTime()/1000;
                if (i++ > 3)
                {
                    i = 0;
                }

                try
                {
                    int bufferIndex = mediaCodec.dequeueInputBuffer(500000);
                    if (bufferIndex>=0)
                    {
                        inputBuffers[bufferIndex].clear();
                        if (data == null)
                        {
                            Log.e(TAG,"Symptom of the \"Callback buffer was to small\" problem...");
                        }
                        else
                        {
                            converter.convert(data, inputBuffers[bufferIndex]);
                        }

                        mediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
                    }
                    else
                    {
                        Log.e(TAG,"No buffer available !");
                    }
                }
                finally
                {
                    VideoStream.this.camera.addCallbackBuffer(data);
                }
            }
        };

        for (int i = 0; i < 10; i++)
        {
            camera.addCallbackBuffer(new byte[converter.getBufferSize()]);
        }

        camera.setPreviewCallbackWithBuffer(callback);

        // The packetizer encapsulates the bit stream in an RTP stream and send it over the network
        packetizer.setInputStream(new MediaCodecInputStream(mediaCodec));
        packetizer.start();
        streaming = true;
    }

    /**
     * Returns a description of the stream using SDP.
     * This method can only be called after {@link Stream#configure()}.
     * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
     */
    public abstract String getSessionDescription() throws IllegalStateException;

    /**
     * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
     * If an exception is thrown in this Looper thread, we bring it back into the main thread.
     * @throws RuntimeException Might happen if another app is already using the camera.
     */
    private void openCamera() throws RuntimeException
    {
        final Semaphore lock = new Semaphore(0);
        final RuntimeException[] exception = new RuntimeException[1];

        cameraThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Looper.prepare();
                cameraLooper = Looper.myLooper();

                try
                {
                    camera = Camera.open(cameraId);
                }
                catch (RuntimeException e)
                {
                    exception[0] = e;
                }
                finally
                {
                    lock.release();
                    Looper.loop();
                }
            }
        });

        cameraThread.start();
        lock.acquireUninterruptibly();

        if (exception[0] != null)
        {
            throw new CameraInUseException(exception[0].getMessage());
        }
    }

    protected synchronized void createCamera() throws RuntimeException
    {
        if (surfaceView == null)
        {
            throw new InvalidSurfaceException("Invalid surface", null);
        }

        if (surfaceView.getHolder() == null || !surfaceReady)
        {
            throw new InvalidSurfaceException("Invalid surface", null);
        }

        if (camera == null)
        {
            openCamera();
            updated = false;
            unlocked = false;
            camera.setErrorCallback(new Camera.ErrorCallback() {
                @Override
                public void onError(int error, Camera camera) {

                // On some phones when trying to use the camera facing front the media server will die
                // Whether or not this callback may be called really depends on the phone
                if (error == Camera.CAMERA_ERROR_SERVER_DIED)
                {
                    // In this case the application must release the camera and instantiate a new one
                    Log.e(TAG,"Media server died");

                    // We don't know in what thread we are so stop needs to be synchronized
                    cameraOpenedManually = false;
                    stop();
                }
                else
                {
                    Log.e(TAG,"Unknown error with the camera: " + error);
                }
                }
            });

            try
            {
                // setRecordingHint(true) is a very nice optimization if you plan to only use the Camera for recording
                Parameters parameters = camera.getParameters();
                parameters.setRecordingHint(true);
                camera.setParameters(parameters);
                camera.setDisplayOrientation(orientation);

                try
                {
                    camera.setPreviewDisplay(surfaceView.getHolder());
                }
                catch (IOException e)
                {
                    throw new InvalidSurfaceException("Invalid surface", e);
                }

            }
            catch (RuntimeException e)
            {
                destroyCamera();
                throw e;
            }
        }
    }

    protected synchronized void destroyCamera()
    {
        if (camera != null)
        {
            if (streaming)
            {
                super.stop();
            }

            lockCamera();
            camera.stopPreview();

            try
            {
                camera.release();
            }
            catch (Exception e)
            {
                Log.e(TAG, "Releasing the camera threw", e);
            }

            camera = null;
            cameraLooper.quit();
            unlocked = false;
            previewStarted = false;
        }
    }

    protected synchronized void updateCamera() throws RuntimeException
    {
        // The camera is already correctly configured
        if (updated)
        {
            return;
        }

        if (previewStarted)
        {
            previewStarted = false;
            camera.stopPreview();
        }

        Parameters parameters = camera.getParameters();
        quality = VideoQuality.determineClosestSupportedResolution(parameters, quality);
        int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);

        parameters.setPreviewFormat(cameraImageFormat);
        parameters.setPreviewSize(quality.resX, quality.resY);
        parameters.setPreviewFpsRange(max[0], max[1]);

        try
        {
            camera.setParameters(parameters);
            camera.setDisplayOrientation(orientation);
            camera.startPreview();
            previewStarted = true;
            updated = true;
        }
        catch (RuntimeException e)
        {
            destroyCamera();
            throw e;
        }
    }

    protected void lockCamera()
    {
        if (!unlocked)
        {
            return;
        }

        Log.d(TAG,"Locking camera");

        try
        {
            camera.reconnect();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Reconnecting to camera threw", e);
        }

        unlocked = false;
    }

    protected void unlockCamera()
    {
        if (unlocked)
        {
            return;
        }

        Log.d(TAG,"Unlocking camera");

        try
        {
            camera.unlock();
        }
        catch (Exception e)
        {
            Log.e(TAG, "Unlocking camera threw", e);
        }

        unlocked = true;
    }

    /**
     * Computes the average frame rate at which the preview callback is called.
     * We will then use this average frame rate with the MediaCodec.
     * Blocks the thread in which this function is called.
     */
    private void measureFramerate()
    {
        final Semaphore lock = new Semaphore(0);

        final Camera.PreviewCallback callback = new Camera.PreviewCallback()
        {
            int i = 0;
            int t = 0;
            long now;
            long oldNow;
            long count = 0;

            @Override
            public void onPreviewFrame(byte[] data, Camera camera)
            {
                i++;
                now = System.nanoTime() / 1000;
                if (i > 3)
                {
                    t += now - oldNow;
                    count++;
                }

                if (i > 20)
                {
                    quality.framerate = (int)(1000000 / (t / count) + 1);
                    lock.release();
                }

                oldNow = now;
            }
        };

        camera.setPreviewCallback(callback);

        try
        {
            lock.tryAcquire(2, TimeUnit.SECONDS);
            Log.d(TAG,"Actual framerate: " + quality.framerate);
            if (settings != null)
            {
                Editor editor = settings.edit();
                editor.putInt(PREF_PREFIX + "fps" + requestedQuality.framerate + "," + cameraImageFormat + "," + requestedQuality.resX + requestedQuality.resY, quality.framerate);
                editor.commit();
            }
        }
        catch (InterruptedException e)
        {
            Log.e(TAG, "Measuring frame rate threw", e);
        }

        camera.setPreviewCallback(null);
    }
}
