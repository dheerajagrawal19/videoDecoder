package com.adobe.videodecoder;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import java.util.HashMap;

public class FrameFetcher extends MediaMetadataRetriever {
    private static final int MS_OF_S = 1000;
    private static final int US_OF_S = MS_OF_S * MS_OF_S;
    private static final int FPS = 15;
    private static final double INCREMENT_IN_MS = US_OF_S / FPS;
    private final String TAG = this.getClass().toString();
    private int mFileStartTime;
    private int mFileEndTime;
    private int mCurrentTime;
    private boolean mInitialized;
    private HashMap<Integer, Bitmap> hashMap;

    public FrameFetcher() {
        mFileEndTime = 0;
        mFileStartTime = 0;
        mCurrentTime = 0;
        mInitialized = false;
        hashMap = new HashMap<>();
    }

    public void init()
    {
        try {

            String time = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

            mFileStartTime = 0;
            mFileEndTime = Integer.parseInt(time);
            mCurrentTime = 0;
            mInitialized = true;

            runAsync();

        }catch (Exception e)
        {
            mInitialized = false;
        }
    }

    public float getTotalDuration()
    {
        return (float)mFileEndTime/MS_OF_S;
    }

    public float getCurrentFrameValue()
    {
        return (float)mCurrentTime/US_OF_S;
    }

    private Bitmap getBitMapAt(int us)
    {
        Bitmap frame = null;
        synchronized (this)
        {

            if (hashMap.containsKey(us)){
                frame = hashMap.get(us);
            }else {
                if (us >= mFileStartTime && us <= mFileEndTime * MS_OF_S)
                {
//                    Log.i(TAG, "createBitmaps generating file at: " + us);
                    frame = getFrameAtTime((long) us, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame != null)
                    {
//                        Log.i(TAG, "createBitmaps generated file at: " + us);
                        frame.setHasAlpha(true);
                        Bitmap bitmap = frame.copy(Bitmap.Config.ARGB_8888, true);
                        hashMap.put(us, bitmap);
                        return frame;
                    }
                }
            }
        }
        return frame;
    }

    public Bitmap getNextBitMap(boolean inp)
    {
        if (!mInitialized)
        {
            Log.i(TAG, "Object Not initalized");
            return null;
        }
        int us = 0;
        synchronized (this) {
            us = mCurrentTime;
        }

        if(inp)
            us += INCREMENT_IN_MS;
        else
            us -= INCREMENT_IN_MS;

        if (us < mFileStartTime)
            us = 0;

        if (us > mFileEndTime * MS_OF_S)
            us = mFileEndTime * MS_OF_S;


        synchronized (this) {
            mCurrentTime = us;
        }

        return getBitMapAt(us);

    }


    private void runAsync()
    {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = mFileStartTime; i< mFileEndTime; i += INCREMENT_IN_MS)
                {
                    getBitMapAt(i);
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

}
