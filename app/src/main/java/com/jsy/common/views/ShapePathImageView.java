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
package com.jsy.common.views;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.waz.zclient.R;


public class ShapePathImageView extends AppCompatImageView {

    private final int leftTop = 0;
    private final int leftBottom = 1;
    private final int rightTop = 2;
    private final int rightBottom = 3;
    public static final int leftBottomRightBottom = 4;
    public static final int leftTopRightTop = 5;
    public static final int all = 6;
    public static final int none = 7;

    private int roundAngleDirection = -1;
    private Paint paint;
    private int radiusHorizontal = 0;
    private int radiusVertical = 0;
    private Paint paint2;

    private Path path = new Path();

    public ShapePathImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    public ShapePathImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ShapePathImageView(Context context, int radiusHorizontal, int radiusVertical) {
        super(context);
        this.radiusHorizontal = radiusHorizontal;
        this.radiusVertical = radiusVertical;
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {

        if (attrs != null) {
            TypedArray typedArray = context.obtainStyledAttributes(attrs,
                    R.styleable.ShapePath);

            radiusHorizontal = typedArray.getDimensionPixelSize(
                    R.styleable.ShapePath_radiusHorizontal,
                    radiusHorizontal);
            radiusVertical = typedArray.getDimensionPixelSize(
                    R.styleable.ShapePath_radiusVertical,
                    radiusVertical);

            roundAngleDirection = typedArray.getInt(
                    R.styleable.ShapePath_roundAngleDirection, -1);
            typedArray.recycle();
        }
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        paint2 = new Paint();
        paint2.setXfermode(null);
    }

    public void setroundAngleDirection(int roundAngleDirection) {
        this.roundAngleDirection = roundAngleDirection;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas2 = new Canvas(bitmap);
        super.draw(canvas2);
        switch (roundAngleDirection) {
            case leftTop:
                drawLeftTop(canvas2);
                break;
            case leftBottom:
                drawLeftBottom(canvas2);
                break;
            case rightTop:
                drawRightTop(canvas2);
                break;
            case rightBottom:
                drawRightBottom(canvas2);
                break;
            case leftBottomRightBottom:
                drawLeftBottom(canvas2);
                drawRightBottom(canvas2);
                break;
            case leftTopRightTop:
                drawLeftTop(canvas2);
                drawRightTop(canvas2);
                break;
            case all:
                drawLeftTop(canvas2);
                drawLeftBottom(canvas2);
                drawRightTop(canvas2);
                drawRightBottom(canvas2);
                break;
            case none:

                break;
            default:
                break;
        }
        canvas.drawBitmap(bitmap, 0, 0, paint2);
        bitmap.recycle();
    }

    private void drawLeftTop(Canvas canvas) {
        path.reset();
        path.moveTo(0, radiusVertical);
        path.lineTo(0, 0);
        path.lineTo(radiusHorizontal, 0);
        path.arcTo(new RectF(0, 0, radiusHorizontal * 2, radiusVertical * 2), -90, -90);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawLeftBottom(Canvas canvas) {
        path.reset();
        path.moveTo(0, getHeight() - radiusVertical);
        path.lineTo(0, getHeight());
        path.lineTo(radiusHorizontal, getHeight());
        path.arcTo(new RectF(0, getHeight() - radiusVertical * 2,
                0 + radiusHorizontal * 2, getHeight()), 90, 90);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawRightBottom(Canvas canvas) {
        path.reset();
        path.moveTo(getWidth() - radiusHorizontal, getHeight());
        path.lineTo(getWidth(), getHeight());
        path.lineTo(getWidth(), getHeight() - radiusVertical);
        path.arcTo(new RectF(getWidth() - radiusHorizontal * 2, getHeight()
                - radiusVertical * 2, getWidth(), getHeight()), 0, 90);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawRightTop(Canvas canvas) {
        path.reset();
        path.moveTo(getWidth(), radiusVertical);
        path.lineTo(getWidth(), 0);
        path.lineTo(getWidth() - radiusHorizontal, 0);
        path.arcTo(new RectF(getWidth() - radiusHorizontal * 2, 0, getWidth(),
                0 + radiusVertical * 2), -90, 90);
        path.close();
        canvas.drawPath(path, paint);
    }

}
