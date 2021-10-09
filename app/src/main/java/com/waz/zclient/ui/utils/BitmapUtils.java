/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.ui.utils;

import android.content.res.Resources;
import android.graphics.*;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.waz.zclient.R;

import java.io.ByteArrayOutputStream;
import java.util.Hashtable;

public class BitmapUtils {

    private static final int VIGNETTE_WIDTH = 50;
    private static final int VIGNETTE_HEIGHT = 50;

    private BitmapUtils(){}

    /**
     * Helper function to create a bitmap that serves as a vignette overlay.
     *
     * @return
     */
    public static Bitmap getVignetteBitmap(Resources resources) {
        double radiusFactor = ResourceUtils.getResourceFloat(resources, R.dimen.background__vignette_radius_factor);
        int radius = (int) (VIGNETTE_WIDTH * radiusFactor);

        int baseColor = resources.getColor(R.color.black_80);
        int colorCenter = resources.getColor(R.color.black);
        int colorEdge = resources.getColor(R.color.transparent);

        Bitmap dest = Bitmap.createBitmap(VIGNETTE_WIDTH, VIGNETTE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        Bitmap tempBitmap = Bitmap.createBitmap(dest.getWidth(),
                                                dest.getHeight(),
                                                Bitmap.Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(tempBitmap);
        tempCanvas.drawColor(baseColor);
        RadialGradient gradient = new RadialGradient(VIGNETTE_WIDTH / 2,
                                                                      VIGNETTE_HEIGHT / 2,
                                                                      radius,
                                                                      colorCenter,
                                                                      colorEdge,
                                                                      android.graphics.Shader.TileMode.CLAMP);
        Paint p = new Paint();
        p.setShader(gradient);
        p.setColor(0xFF000000);
        p.setAntiAlias(true);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        tempCanvas.drawCircle(VIGNETTE_WIDTH / 2, VIGNETTE_HEIGHT / 2, radius, p);
        canvas.drawBitmap(tempBitmap, 0, 0, null);

        return dest;
    }


    public static Bitmap getUnreadMarker(int width, int radius, int color) {
        if (width <= 0) {
            return null;
        }
        Bitmap dest = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(dest);
        Paint p = new Paint();
        p.setColor(color);
        p.setAntiAlias(true);
        canvas.drawCircle(width / 2, width / 2, radius, p);
        return dest;
    }


    public static Bitmap base64toBitmap(String base64) {

        Bitmap bitmap = null;
        try {
            byte[] bitmapArray;
            bitmapArray = Base64.decode(base64, Base64.NO_WRAP);
            bitmap = BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    public static String bitmaptoString(Bitmap bitmap) {
        String string = null;
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bStream);
        byte[] bytes = bStream.toByteArray();
        string = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return string;
    }

    private static final int    QRCODE_SIZE = 300;
    private static final String CHARSET     = "utf-8";
    private static final int    BLACK       = 0xff000000;
    private static final int    WHITE       = 0xffffffff;

    public static Bitmap getRQcode(String content) {
        Hashtable<EncodeHintType, Object> hashTable = new Hashtable<>();
        hashTable.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hashTable.put(EncodeHintType.CHARACTER_SET, CHARSET);
        hashTable.put(EncodeHintType.MARGIN, 1);
        Bitmap bitmap = null;
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QRCODE_SIZE, QRCODE_SIZE, hashTable);
            int       width     = bitMatrix.getWidth();
            int       height    = bitMatrix.getHeight();
            int[]     pixels    = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (bitMatrix.get(x, y)) {
                        pixels[y * width + x] = BLACK;
                    } else {
                        pixels[y * QRCODE_SIZE + x] = WHITE;
                    }
                }
            }
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        } catch (WriterException e) {
            e.printStackTrace();
        }
        return bitmap;
    }
}
