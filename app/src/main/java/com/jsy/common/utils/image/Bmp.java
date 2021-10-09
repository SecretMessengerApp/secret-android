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
package com.jsy.common.utils.image;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;

import java.io.*;

public class Bmp {

    public static Bitmap revitionImageSize(String path) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(
            new File(path)));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, options);
        in.close();
        int i = 0;
        Bitmap bitmap = null;
        while (true) {
            if ((options.outWidth >> i <= 1000)
                && (options.outHeight >> i <= 1000)) {
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
                in = new BufferedInputStream(
                    new FileInputStream(new File(path)));
                options.inSampleSize = (int) Math.pow(2.0D, i);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
                in.close();
                break;
            }
            i++;
        }
        return bitmap;
    }

    public static Bitmap revitionImageSize(String path, int width, int height) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(
            new File(path)));
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, options);
        in.close();
        int i = 0;
        Bitmap bitmap = null;
        while (true) {
            if ((options.outWidth >> i <= width)
                && (options.outHeight >> i <= height)) {
                if (bitmap != null) {
                    bitmap.recycle();
                    bitmap = null;
                }
                in = new BufferedInputStream(
                    new FileInputStream(new File(path)));
                options.inSampleSize = (int) Math.pow(2.0D, i);
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeStream(in, null, options);
                in.close();
                break;
            }
            i++;
        }
        return bitmap;
    }

    public static Uri saveBitmap(Bitmap bm, String filePath, String fileName) {

        File localFile = new File(filePath);
        if (!localFile.exists()) {
            localFile.mkdir();
        }
        File finalImageFile = new File(localFile, fileName);
        if (finalImageFile.exists()) {
            finalImageFile.delete();
        }
        try {
            finalImageFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(finalImageFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        if (bm == null) {
            return null;
        }
        bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        try {
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Uri.fromFile(finalImageFile);
    }


    public static Bitmap rotateBitmap(InputStream sourceStream, Uri uri, Context context){

        Bitmap sourceBitmap = BitmapFactory.decodeStream(sourceStream);

        int degree = 0;

        try {
            degree = calculateDegree(context.getContentResolver().openInputStream(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (degree != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
        }

        return sourceBitmap;
    }


    public static Bitmap compressBitmap(InputStream sourceStream, Uri uri, Context context) {
        int compressMaxWidth = 480;
        int compressQuality = 90;
        BitmapFactory.Options sourceOptions = new BitmapFactory.Options();
        Bitmap sourceBitmap = BitmapFactory.decodeStream(sourceStream, null, sourceOptions);
        sourceOptions.inSampleSize = calculateInSampleSize(sourceOptions, compressMaxWidth, compressMaxWidth);

        int degree = 0;

        try {
            degree = calculateDegree(context.getContentResolver().openInputStream(uri));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (degree != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degree);
            sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
        }

        ByteArrayOutputStream newOutputStream = new ByteArrayOutputStream();
        sourceBitmap.compress(Bitmap.CompressFormat.JPEG, compressQuality, newOutputStream);
        byte[] bytes = newOutputStream.toByteArray();
        if (!sourceBitmap.isRecycled()) sourceBitmap.recycle();

        Bitmap newBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, sourceOptions);

        try {
            newOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return newBitmap;
    }


    @TargetApi(Build.VERSION_CODES.N)
    public static int calculateDegree(InputStream inputStream) {
        int degree = 0;

        ExifInterface exifInterface = null;
        try {
            exifInterface = new ExifInterface(inputStream);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                degree = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                degree = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                degree = 270;
            }

            return degree;

        } catch (Throwable e) {
            e.printStackTrace();
            return degree;
        }


    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;

        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int heightRatio = Math.round(height / reqHeight);
            int widthRatio = Math.round(width / reqWidth);
            if (heightRatio < widthRatio) {
                inSampleSize = heightRatio;
            } else {
                inSampleSize = widthRatio;
            }
        }
        return inSampleSize;
    }

    public static String getImageBase64(String srcPath) {
        BitmapFactory.Options newOpts = new BitmapFactory.Options();
        newOpts.inJustDecodeBounds = true;
        Bitmap bitmap = BitmapFactory.decodeFile(srcPath,newOpts);
        newOpts.inJustDecodeBounds = false;
        int w = newOpts.outWidth;
        int h = newOpts.outHeight;
        float hh = 1280f;
        float ww = 720f;
        int be = 1;
        if (w > h && w > ww) {
            be = (int) (newOpts.outWidth / ww);
        } else if (w < h && h > hh) {
            be = (int) (newOpts.outHeight / hh);
        }
        if (be <= 0)
            be = 1;
        newOpts.inSampleSize = be;
        bitmap = BitmapFactory.decodeFile(srcPath, newOpts);
        return compressImageToBase64(bitmap);
    }


    public static String compressImageToBase64(Bitmap image) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        int options = 100;
        while ( baos.toByteArray().length / 1024 > 100) {
            baos.reset();
            image.compress(Bitmap.CompressFormat.JPEG, options, baos);
            options -= 10;
        }

        byte[] bytes = baos.toByteArray();

        return Base64.encodeToString(bytes, Base64.NO_WRAP);

    }
}
