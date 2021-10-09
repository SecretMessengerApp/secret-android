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
package com.jsy.common.utils.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

public class BitmapCache {

    public Handler uiHandler;

    public BitmapCache(Handler uiHandler) {
        // TODO Auto-generated constructor stub
        this.uiHandler = uiHandler;
    }

    public final String TAG = getClass().getSimpleName();
    private HashMap<String, SoftReference<Bitmap>> imageCache = new HashMap<String, SoftReference<Bitmap>>();

    public void put(String path, Bitmap bmp) {
        if (!TextUtils.isEmpty(path) && bmp != null) {
            imageCache.put(path, new SoftReference<Bitmap>(bmp));
        }
    }

    public void displayBmp(final ImageView iv, final String thumbPath,
                           final String sourcePath, final ImageCallback callback) {
        if (TextUtils.isEmpty(thumbPath) && TextUtils.isEmpty(sourcePath)) {
            return;
        }

        final String path;
        final boolean isThumbPath;
        if (!TextUtils.isEmpty(thumbPath)) {
            path = thumbPath;
            isThumbPath = true;
        } else if (!TextUtils.isEmpty(sourcePath)) {
            path = sourcePath;
            isThumbPath = false;
        } else {
            // iv.setImageBitmap(null);
            return;
        }

        if (imageCache.containsKey(path)) {
            SoftReference<Bitmap> reference = imageCache.get(path);
            Bitmap bmp = reference.get();
            if (bmp != null) {
                if (callback != null) {
                    callback.imageLoad(iv, bmp, sourcePath);
                }
                iv.setImageBitmap(bmp);
                return;
            }
        }
        iv.setImageBitmap(null);

        new Thread() {
            Bitmap thumb;

            public void run() {

                try {
                    if (isThumbPath) {
                        thumb = BitmapFactory.decodeFile(thumbPath);
                        if (thumb == null) {
                            thumb = revitionImageSize(sourcePath);
                        }
                    } else {
                        thumb = revitionImageSize(sourcePath);
                    }
                } catch (Exception e) {

                }
                if (thumb == null) {
                    // thumb = MainActivity.bimap;
                }
                put(path, thumb);

                if (callback != null && uiHandler != null) {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.imageLoad(iv, thumb, sourcePath);
                        }
                    });
                }
            }
        }.start();

    }

    public Bitmap revitionImageSize(String path) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(
                new File(path)));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, options);
        in.close();
        int i = 0;
        Bitmap bitmap = null;
        while (true) {
            if ((options.outWidth >> i <= 256)
                    && (options.outHeight >> i <= 256)) {
                in = new BufferedInputStream(
                        new FileInputStream(new File(path)));
                options.inSampleSize = (int) Math.pow(2.0D, i);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
                break;
            }
            i++;
        }
        return bitmap;
    }

    public interface ImageCallback {
        void imageLoad(ImageView imageView, Bitmap bitmap,
                       Object... params);
    }
}
