package com.adobe.videodecoder;

import android.annotation.TargetApi;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;

public class FrameFetcherMCBased {

    private MediaExtractor extractor = null;
    private MediaFormat mOutputFormat; // member variable
    private static final String TAG = "FinalClass";
    private int trackIndex;
    private MediaFormat outputFormat;
    private MediaCodec codec;
    private ImageReader imageReader;
    private boolean keepFetchingFrames;
    private static final long timeoutUs = 10000;
    private volatile Looper mImageAcquireLooper;
    private Handler mHandler;
    private volatile byte[] mLatestImage;
    private final Object mImageWaitingLock = new Object();
    private String nameCodec;
    private int saveWidth;
    private int saveHeight;

    FrameFetcherMCBased(){

    }

    public static byte[] RGBADisplay(Image image)
    {
        int Width = image.getWidth();
        int Height = image.getHeight();
        byte[] ImageRGB = new byte[Width*Height*4];
        Image.Plane[] planes = image.getPlanes();

        byte[] p0 = new byte[planes[0].getBuffer().remaining()];
        byte[] p1 = new byte[planes[1].getBuffer().remaining()];
        byte[] p2 = new byte[planes[2].getBuffer().remaining()];


        for(int i = 0; i<Height-1; i++){
            for (int j = 0; j<Width; j++){
                int Y = p0[i*Width+j]&0xFF;
                int U = p1[(i/2)*(Width/2)+j/2]&0xFF;
                int V = p2[(i/2)*(Width/2)+j/2]&0xFF;
                U = U-128;
                V = V-128;

                int R,G,B;
                R = (int)(Y + 1.140*V);
                G = (int)(Y - 0.395*U - 0.581*V);
                B = (int)(Y + 2.032*U);

                if (R>255) {
                    R = 255;
                } else if (R<0) {
                    R = 0;
                }
                if (G>255) {
                    G = 255;
                } else if (G<0) {
                    G = 0;
                }
                if (B>255) {
                    B = 255;
                } else if (B<0) {
                    B = 0;
                }
                ImageRGB[i*4*Width+j*4] = (byte)R;
                ImageRGB[i*4*Width+j*4+1] = (byte)G;
                ImageRGB[i*4*Width+j*4+2] = (byte)B;
                ImageRGB[i*4*Width+j*4+3] = (byte) -1;
            }
        }


        return ImageRGB;
    }

    private int selectTrack(MediaExtractor extractor) {
        // Select the first video track we find, ignore the rest.
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                return i;
            }
        }

        return -1;
    }

    @TargetApi(Build.VERSION_CODES.N)
    public void initalize(AssetFileDescriptor mAFD) throws IOException
    {
        //loop variable..
        keepFetchingFrames = true;

        //media extractor..
        extractor = new MediaExtractor();
        extractor.setDataSource(mAFD);
        trackIndex = selectTrack(extractor);
        if (trackIndex < 0) {
            throw new RuntimeException("No video track found in " + "file");
        }
        extractor.selectTrack(trackIndex);
        mOutputFormat = extractor.getTrackFormat(trackIndex);
        nameCodec = mOutputFormat.getString(MediaFormat.KEY_MIME);
        saveWidth = mOutputFormat.getInteger(MediaFormat.KEY_WIDTH);
        saveHeight = mOutputFormat.getInteger(MediaFormat.KEY_HEIGHT);

        //image reader...
        imageReader = ImageReader.newInstance(saveWidth,saveHeight, ImageFormat.YUV_420_888, 1);

        //media codec..
        codec = MediaCodec.createDecoderByType(nameCodec);
        codec.configure(mOutputFormat, imageReader.getSurface(), null, 0);
        outputFormat = codec.getOutputFormat(); // option B
        codec.start();

        extactImages();
    }


    public void deinit()
    {
        if (codec != null) {
            codec.stop();
            codec.release();
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
    }

    public void extactImages() {
        int decodeCount = 0;
        boolean outputDone = false;
        boolean inputDone = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long startWhen = System.nanoTime();
        while (!outputDone){

            if(!keepFetchingFrames)
            {
                //put it on hold..


            }else {
                if (!inputDone) {
                    int inputBufferId = codec.dequeueInputBuffer(timeoutUs);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);

                        int chunkSize = 0;
                        if (inputBuffer != null) {
                            chunkSize = extractor.readSampleData(inputBuffer, 0);
                            if (chunkSize < 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                long presentationTimeUs = extractor.getSampleTime();
                                codec.queueInputBuffer(inputBufferId, 0, chunkSize,
                                        presentationTimeUs, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                if (!outputDone) {
                    int outputBufferId = codec.dequeueOutputBuffer(info, timeoutUs);
                    if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        Log.d(TAG, "no output from decoder available");
                    }else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = codec.getOutputFormat();
                        Log.d(TAG, "decoder output format changed: " + outputFormat);
                    } else if (outputBufferId < 0) {
                        Log.d(TAG, "unexpected result from decoder.dequeueOutputBuffer: " + outputBufferId);
                    } else if (outputBufferId >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true;
                        }
//
//                        ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferId);
//                        MediaFormat bufferFormat = codec.getOutputFormat(outputBufferId);

                        boolean doRender = (info.size != 0);
                        codec.releaseOutputBuffer(outputBufferId,doRender);
                        decodeCount++;

                        Image img = imageReader.acquireLatestImage();
                        Log.i(TAG, " Image Frame Received.." + decodeCount);

                        if (img != null) {
                            //commenting the code as it has some issues...
//                            synchronized (mImageWaitingLock) {
//                                mLatestImage = RGBADisplay(img);
//                            }

                            img.close();
                        }

                    } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = codec.getOutputFormat(); // option B
                    }
                }
            }

        }

        long total_time = System.nanoTime() - startWhen;
        Log.i(TAG, "Total Frame Extracted " + decodeCount);
        Log.i(TAG, "Total Frame Extracted Per ms : " + total_time/(decodeCount*1.0e6));
    }

    byte[] getCurrentFrame()
    {
        byte[] bytes = null;
        synchronized (mImageWaitingLock) {
            bytes = mLatestImage;
        }
        return bytes;
    }
}
