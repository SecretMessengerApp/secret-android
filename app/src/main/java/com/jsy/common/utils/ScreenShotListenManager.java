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
package com.jsy.common.utils;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class ScreenShotListenManager {

    private static final String TAG = "ScreenShotListenManager";

    private static final String[] MEDIA_PROJECTIONS = {
        MediaStore.Images.ImageColumns.DATA,
    };

    private static final String[] KEYWORDS = {
        "screenshot", "screen_shot", "screen-shot", "screen shot",
        "screencapture", "screen_capture", "screen-capture", "screen capture",
        "screencap", "screen_cap", "screen-cap", "screen cap"
    };

    private final static List<String> sHasCallbackPaths = new ArrayList<>();
    private Context mContext;
    private OnScreenShotListener mListener;

    private MediaContentObserver mInternalObserver;

    private MediaContentObserver mExternalObserver;
    private Handler mHandler;
    private HandlerThread mUiHandlerThread;

    private ScreenShotListenManager(Context context) {

        if (context == null) {
            throw new IllegalArgumentException("The context must not be null.");
        }

        mContext = context;

        mUiHandlerThread = new HandlerThread("Screen_Observer");
        mUiHandlerThread.start();
        mHandler = new Handler(mUiHandlerThread.getLooper());
    }

    public static ScreenShotListenManager newInstance(Context context) {
        return new ScreenShotListenManager(context);
    }

    public void startListen() {
        mInternalObserver = new MediaContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI, mHandler);
        mExternalObserver = new MediaContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mHandler);
        mContext.getContentResolver().registerContentObserver(
            MediaStore.Images.Media.INTERNAL_CONTENT_URI,
            true,
            mInternalObserver
        );
        mContext.getContentResolver().registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mExternalObserver
        );
    }


    public void stopListen() {
        if (mInternalObserver != null) {
            try {
                mContext.getContentResolver().unregisterContentObserver(mInternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mInternalObserver = null;
        }
        if (mExternalObserver != null) {
            try {
                mContext.getContentResolver().unregisterContentObserver(mExternalObserver);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mExternalObserver = null;
        }
        mListener = null;
    }

    private void handleMediaContentChange(Uri contentUri) {
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                contentUri,
                MEDIA_PROJECTIONS,
                null,
                null,
                MediaStore.Images.ImageColumns.DATE_ADDED + " desc limit 1"
            );
            if (cursor == null) {
                return;
            }
            if (!cursor.moveToFirst()) {
                return;
            }

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            String data = cursor.getString(dataIndex);

            handleMediaRowData(data);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
    }

    private void handleMediaRowData(String data) {
        if (checkScreenShot(data)) {
            if (mListener != null && !checkCallback(data)) {
                mListener.onShot(data);
            }
        }
    }


    private boolean checkScreenShot(String data) {

        if (TextUtils.isEmpty(data)) {
            return false;
        }
        data = data.toLowerCase();
        for (String keyWork : KEYWORDS) {
            if (data.contains(keyWork)) {
                return true;
            }
        }
        return false;
    }


    private boolean checkCallback(String imagePath) {

        if (sHasCallbackPaths.contains(imagePath)) {
            return true;
        }

        if (sHasCallbackPaths.size() >= 20) {
            for (int i = 0; i < 5; i++) {
                sHasCallbackPaths.remove(0);
            }
        }
        sHasCallbackPaths.add(imagePath);
        return false;
    }

    public void setListener(OnScreenShotListener listener) {
        mListener = listener;
    }

    public interface OnScreenShotListener {
        void onShot(String imagePath);
    }


    private class MediaContentObserver extends ContentObserver {
        private final Uri mContentUri;

        public MediaContentObserver(Uri contentUri, Handler handler) {
            super(handler);
            mContentUri = contentUri;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            handleMediaContentChange(mContentUri);
        }
    }
}
