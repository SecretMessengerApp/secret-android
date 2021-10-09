/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.jsy.common.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jsy.common.utils.MD5Util;
import com.jsy.common.utils.RxJavaUtil;
import com.jsy.common.utils.ToastUtil;
import com.jsy.common.utils.image.ImageUtils;
import com.jsy.common.views.ARCaptureView;
import com.jsy.common.views.camera.CameraARPicView;
import com.jsy.common.views.camera.CameraARScanView;
import com.jsy.common.views.camera.ScanCallback;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.BaseScalaFragment;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.SoundController;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifImageView;
import top.zibin.luban.Luban;
import top.zibin.luban.OnCompressListener;
import top.zibin.luban.OnRenameListener;

/**
 * AR
 */
public class ScanARCodeFragment extends BaseScalaFragment implements OnClickListener, ScanCallback {
    public static final String TAG = ScanARCodeFragment.class.getSimpleName();
    private static final int picIgnoreSize = 200;// KB

    public static ScanARCodeFragment newInstance() {
        ScanARCodeFragment fragment = new ScanARCodeFragment();
        return fragment;
    }

    private View rootView;
    private ARCaptureView discernView;
    //    private FrameLayout containerLayout;
    private CameraARPicView cameraARPicView;
    private CameraARScanView arScanView;
    private GifImageView mARPicAnim;
    private boolean isStopCamera = false;
    private boolean isDiscerning = false;
    private MediaPlayer mMediaPlayer;
    private Handler mHandler = new Handler();

    public boolean isDiscerning() {
        return isDiscerning;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        } else {
            rootView = inflater.inflate(R.layout.fragment_scan_arcode, container, false);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        discernView = view.findViewById(R.id.ar_discern_view);
        discernView.setOnClickListener(this);
        mARPicAnim = view.findViewById(R.id.arpic_anim_view);
        arScanView = view.findViewById(R.id.ar_scan_anim);
//        containerLayout = view.findViewById(R.id.ar_container);
//        containerLayout.removeAllViews();
        cameraARPicView = view.findViewById(R.id.ar_preview);
//        cameraARPicView = new CameraARPicView(getActivity());
        cameraARPicView.setScanCallback(this);
//        containerLayout.addView(cameraARPicView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGLView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            onTextureStop();
        } else {
            refreshGLView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        onTextureStop();
    }

    public void refreshGLView() {
        LogUtils.i(TAG, "refreshGLView 222 isStopCamera:" + isStopCamera);
        if (cameraARPicView != null) {
            isStopCamera = false;
            cameraARPicView.onTextureResume();
            showARScanView();
        }
    }

    private void showARScanView() {
        if (arScanView.getVisibility() == View.VISIBLE) {
            return;
        }
        hideARScanView();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                arScanView.setVisibility(View.VISIBLE);
            }
        }, 1000);
    }

    private void hideARScanView() {
        mHandler.removeCallbacksAndMessages(null);
        arScanView.setVisibility(View.GONE);
    }

    public String curCameraId() {
        if (null != cameraARPicView) {
            return cameraARPicView.getCameraId();
        }
        return null;
    }

    @Override
    public void onClick(View v) {
        int vId = v.getId();
        if (vId == R.id.ar_discern_view) {
            discernCurFrame();
        }
    }

    private void discernCurFrame() {
        if (null != cameraARPicView) {
            if (isDiscerning) {
                return;
            }
            isDiscerning = true;
            SoundController ctrl = (SoundController) inject(SoundController.class);
            if (ctrl != null) {
                ctrl.playPingFromMe(false);
            }
            showProgressDialog();
            cameraARPicView.takePicture();
        }
    }

    @Override
    public void onScanResult(final String picPath) {
        isDiscerning = false;
        dismissProgressDialog();
        if (TextUtils.isEmpty(picPath)) {
            return;
        }
        showProgressDialog();
        isDiscerning = true;
        scanResult(picPath);
    }

    public void picHandleResult(final String picPath) {
        if (!checkPicPathLegal(picPath)) {
            ToastUtil.toastByResId(getActivity(), R.string.invalid_qr_code_error);
            return;
        }
        try {
            showProgressDialog();
            isDiscerning = true;
            Luban.with(getActivity())
                .load(picPath)
                .ignoreBy(picIgnoreSize)
                .setTargetDir(CameraARPicView.getARFileDirectory(getActivity()).getPath())
                .setRenameListener(new OnRenameListener() {
                    @Override
                    public String rename(String filePath) {
                        String compressName = "compress_" + MD5Util.MD5(filePath);
                        return compressName;
                    }
                })
                .setCompressListener(new OnCompressListener() {

                    @Override
                    public void onStart() {
                        LogUtils.i(TAG, "picHandleResult setCompressListener onStart:" + picPath);
                    }

                    @Override
                    public void onSuccess(File outfile) {
                        LogUtils.i(TAG, "picHandleResult setCompressListener onSuccess:" + outfile.getPath());
                        scanResult(outfile.getPath());
                    }

                    @Override
                    public void onError(Throwable e) {
                        LogUtils.i(TAG, "picHandleResult setCompressListener onError:" + e.getMessage());
                        scanResult(picPath);
                    }
                }).launch();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.i(TAG, "picHandleResult Exception:" + e.getMessage());
            isDiscerning = false;
            dismissProgressDialog();
        }
    }

    private boolean checkPicPathLegal(String picPath) {
        if (TextUtils.isEmpty(picPath)) {
            return false;
        }
        String suffix = picPath.substring(picPath.lastIndexOf("."));
        return TextUtils.isEmpty(suffix)
                || ".jpg".equals(suffix)
                || ".png".equals(suffix)
                || ".apng".equals(suffix)
                || ".webp".equals(suffix)
                || ".jpeg".equals(suffix)
                || ".gif".equals(suffix);
    }

    public void scanResult(final String picPath) {
        LogUtils.i(TAG, "scanResult picPath:" + picPath);
        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<String>() {

            @Override
            public String doInBackground() {
                return ImageUtils.imageToBase64(picPath);
            }

            @Override
            public void onFinish(String result) {
            }

            @Override
            public void onError(Throwable e) {
                ToastUtil.toastByResId(getActivity(), R.string.invalid_qr_code_error);
                isDiscerning = false;
                dismissProgressDialog();
            }
        });
    }

    private void initAudioManager() {
        AudioManager mAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressLint("NewApi")
    private void startPlayRedPagMusic() {
        try {
            mMediaPlayer = new MediaPlayer();
            AssetFileDescriptor fileDescriptor = getActivity().getAssets().openFd("music_location_redreceive.wav");
            mMediaPlayer.setDataSource(fileDescriptor);
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {

                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    LogUtils.i(TAG, "startPlayRedPagMusic setOnErrorListener onError what:" + what + ",extra:" + extra);
                    return true;
                }
            });
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    LogUtils.i(TAG, "startPlayRedPagMusic setOnCompletionListener onCompletion");
                }
            });
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

                @Override
                public void onPrepared(MediaPlayer mp) {
                    LogUtils.i(TAG, "startPlayRedPagMusic setOnPreparedListener onPrepared");
                    mMediaPlayer.start();
                }
            });
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void destroyRedPagMusic() {
        if (null != mMediaPlayer) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    public boolean onBackPressed() {
        getActivity().finish();
        return true;
    }

    public void onTextureStop() {
        if (cameraARPicView != null && !isStopCamera) {
            LogUtils.i(TAG, "onTextureStop 222 isStopCamera:" + isStopCamera);
            isStopCamera = true;
            cameraARPicView.onTextureStop();
            hideARScanView();
        }
    }

    @Override
    public void onDestroyView() {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        }
        super.onDestroyView();
        //        onTextureStop();
        destroyRedPagMusic();
    }
}
