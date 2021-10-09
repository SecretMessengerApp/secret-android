/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.download;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.jsy.common.utils.PictureFileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class GlideImageDownload {


    public static void pictureDownload(final Context context, final String picUrl, final String filePath, final String fileName, final PictureDownloadCallBack callBack) {
        Observable.just(picUrl).flatMap((Function<String, ObservableSource<File>>) url -> {
            FutureTarget<File> target = Glide.with(context).downloadOnly().load(url).submit();
            File sourceFile = target.get();

            File destFile;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                destFile = savePictureQ(context, sourceFile, fileName);
            } else {
                destFile = savePicture(sourceFile, filePath, fileName);
            }
            if (destFile != null) {
                return Observable.just(destFile);
            } else {
                return Observable.error(new Exception("destFile is null or not exists"));
            }
        }).subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Observer<File>() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(File file) {
                    if (callBack != null) {
                        callBack.onDownLoadSuccess(file);
                    }
                }

                @Override
                public void onError(Throwable e) {
                    if (callBack != null) {
                        callBack.onDownloadFail(e);
                    }
                }

                @Override
                public void onComplete() {

                }
            });
    }

    @Nullable
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static File savePictureQ(Context context, File sourceFile, String outFileName) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.ImageColumns.DISPLAY_NAME, outFileName);
        contentValues.put(MediaStore.Images.ImageColumns.RELATIVE_PATH, "Pictures/Secret");
        Uri insertUri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
        if (insertUri != null) {
            try {
                OutputStream    outputStream    = context.getContentResolver().openOutputStream(insertUri);
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                byte[]          buffer          = new byte[1024];
                int             len;
                while ((len = fileInputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, len);
                }
                fileInputStream.close();
                outputStream.close();
                String destFilePath = PictureFileUtils.getPath(context, insertUri);
                if (destFilePath != null) {
                    File destFile = new File(destFilePath);
                    if (destFile.exists()) {
                        return destFile;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    @Nullable
    private static File savePicture(File sourceFile, String outFilePath, String outFileName) {
        File appDir = new File(outFilePath);
        if (!appDir.exists()) {
            appDir.mkdirs();
        }
        File destFile = new File(appDir, outFileName);
        fileCopy(sourceFile, destFile);
        if (destFile.exists()) {
            return destFile;
        }
        return null;
    }

    public static void fileCopy(File source, File target) {
        try (FileInputStream fileInputStream = new FileInputStream(source); FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fileInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface PictureDownloadCallBack {
        void onDownLoadSuccess(File file);

        void onDownloadFail(Throwable e);
    }

}
