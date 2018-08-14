package com.adobe.videodecoder;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    ImageView imageView = null;
    FrameFetcher frameFetcher = null;
    TextView totalDuration;
    TextView currentFrame;
    private Handler mHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);

        totalDuration = findViewById(R.id.totalDurationTxt);

        currentFrame = findViewById(R.id.currentFrameTxt);

        mHandler = new Handler(getMainLooper())
        {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                updateUIElements(frameFetcher.getNextBitMap(true), "" + frameFetcher.getTotalDuration(), "" + frameFetcher.getCurrentFrameValue());
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        loadSampleFile();
    }

    public void loadSampleFile()
    {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String fileName = "file";

                int resID = getResources().getIdentifier(fileName,
                        "raw", getPackageName());

                AssetFileDescriptor mAFD = getApplicationContext().getResources().openRawResourceFd(resID);

//                frameFetcher = new FrameFetcher();
//                frameFetcher.setDataSource(mAFD.getFileDescriptor(), mAFD.getStartOffset(), mAFD.getLength());
//                frameFetcher.init();
//
//                mHandler.sendEmptyMessage(0);

                FrameFetcherMCBased ff = new FrameFetcherMCBased();
                try {
                    ff.initalize(mAFD);

                    //fetch frames anytime..
                    ff.getCurrentFrame();


                } catch (IOException e) {
                    e.printStackTrace();
                    ff.deinit();
                }

            }
        };

        Thread thread = new Thread(runnable);
        thread.start();

    }

    public void getPrevFrame(View view) {
        updateUIElements(frameFetcher.getNextBitMap(false), "" + frameFetcher.getTotalDuration(), "" + frameFetcher.getCurrentFrameValue());
    }

    public void getNextFrame(View view) {
        updateUIElements(frameFetcher.getNextBitMap(true), "" + frameFetcher.getTotalDuration(), "" + frameFetcher.getCurrentFrameValue());
    }

    private void updateUIElements(Bitmap bmp, String td, String cd)
    {
        imageView.setImageBitmap(bmp);
        totalDuration.setText(td);
        currentFrame.setText(cd);
    }

    @Override
    protected void onDestroy() {
        frameFetcher.release();
        super.onDestroy();
    }
}
