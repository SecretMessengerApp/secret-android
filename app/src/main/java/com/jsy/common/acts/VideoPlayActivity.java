/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jsy.common.acts;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.RelativeLayout;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;

import java.io.IOException;


public class VideoPlayActivity extends BaseActivity implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener {

    private final String TAG = VideoPlayActivity.class.getSimpleName();

    /**
     * uri
     */
    private static final String INTENT_KEY_URI_STR = "uriStr";
    private static final String INTENT_KEY_CURRENT_POSITION = "currentPosition";
    private String uriStr;

    private View rlSurfaceParent;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayer mediaPlayer;
    private View iv_close;

    private DisplayMetrics displayMetrics = new DisplayMetrics();

    private int currentPosition;

    public static void startSelf(Context context, String uri) {
        Intent intent = new Intent(context, VideoPlayActivity.class);
        intent.putExtra(INTENT_KEY_URI_STR, uri);
        context.startActivity(intent);
    }

    @Override
    public boolean enableWhiteStatusBar() {
        return false;
    }

    @Override
    public int navigationBarColor() {
        return R.color.black;
    }

    @Override
    public boolean navigationBarDarkIcon() {
        return false;
    }

    @Override
    public void customInitStatusBar() {
        initStatusBar(R.id.iv_close);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getInt(INTENT_KEY_CURRENT_POSITION, 0);
            uriStr = savedInstanceState.getString(INTENT_KEY_URI_STR);
        } else {
            uriStr = getIntent().getStringExtra(INTENT_KEY_URI_STR);
        }
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        setContentView(R.layout.activity_video_play);
        iv_close = findById(R.id.iv_close);
        iv_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        rlSurfaceParent = findViewById(R.id.rlSurfaceParent);

        surfaceView = findViewById(R.id.surfaceView);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setKeepScreenOn(true);


        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(this, Uri.parse(uriStr));
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.prepareAsync();
            mediaPlayer.setScreenOnWhilePlaying(true);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    LogUtils.i(TAG, "MediaPlayer onCompletion");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private boolean isPlaying() {
        if(null != mediaPlayer){
            return mediaPlayer.isPlaying();
        }
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void changeSurfaceSize() {

        int screenWidth = displayMetrics.widthPixels; // 1400
        int screenHeight = displayMetrics.heightPixels; // 2712

        int videoWidth = mediaPlayer.getVideoWidth(); // 320
        int videoHeight = mediaPlayer.getVideoHeight(); // 560

        int fixedVideoWidth;
        int fixedVideoHeight;

        float sysScale = screenWidth * 1f / screenHeight; // 0.53
        float videoScale = videoWidth * 1f / videoHeight; //0.57

        float widthScale = screenWidth * 1f / videoWidth; // 4.5
        float heightScale = screenHeight * 1f / videoHeight; // 4.8

        if (sysScale > videoScale) {
            fixedVideoHeight = screenHeight;
            fixedVideoWidth = (int) (videoWidth * heightScale);
        } else {
            fixedVideoWidth = screenWidth;
            fixedVideoHeight = (int) (videoHeight * widthScale);
        }
        setSurfaceLp(fixedVideoWidth, fixedVideoHeight);
        LogUtils.i(TAG, "changeSurfaceSize screenWidth:" + screenWidth + "  screenHeight:" + screenHeight + "  videoWidth:" + videoWidth + "  videoHeight:" + videoHeight + "  fixedVideoWidth:" + fixedVideoWidth + "  fixedVideoWidth:" + fixedVideoWidth);
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {

        outState.putInt(INTENT_KEY_CURRENT_POSITION, currentPosition);
        outState.putString(INTENT_KEY_URI_STR, uriStr);

        super.onSaveInstanceState(outState);
    }


    @Override
    public void onPrepared(MediaPlayer mp) {
        changeSurfaceSize();
        if (currentPosition != 0) {
            mediaPlayer.seekTo(currentPosition);
        }
        mediaPlayer.start();
    }

//    private boolean isPortrait = true;
//    @Override
//    public void onConfigurationChanged(Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        isPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT;
//        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
//        changeSurfaceSize();
//    }

    /**
     * changeOrientation(rlSurfaceParent);
     *
     * @param view
     */
    public void changeOrientation(View view) {
        if (Configuration.ORIENTATION_LANDSCAPE == this.getResources()
                .getConfiguration().orientation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isPlaying()) {
            mediaPlayer.stop();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = null;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mediaPlayer.setDisplay(surfaceHolder);
        if (!isPlaying()) {
            mediaPlayer.start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    public void setSurfaceLp(int width, int height) {
        RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) surfaceView.getLayoutParams();
        lp.width = width;
        lp.height = height;
        surfaceView.setLayoutParams(lp);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (isPlaying()) {
            mediaPlayer.pause();
            currentPosition = mediaPlayer.getCurrentPosition();
        }
    }

    @Override
    public boolean canUseSwipeBackLayout() {
        return false;
    }

}
