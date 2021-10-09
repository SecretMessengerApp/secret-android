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
package com.jsy.common.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.waz.zclient.R;

public class ARCaptureView extends View {
    private Context mContext;
    private Paint mPaint;
    private int outside_color = 0xEEDCDCDC;
    private int inside_color = 0xFFFFFFFF;

    private float center_X;
    private float center_Y;

    private int outside_add_size;
    private int inside_reduce_size;

    private float button_radius;
    private float button_outside_radius;
    private float button_inside_radius;
    private int button_size;

    public ARCaptureView(Context context) {
        this(context, null);
    }

    public ARCaptureView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ARCaptureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public ARCaptureView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        this.mContext = context;
        button_size = mContext.getResources().getDimensionPixelSize(R.dimen.quick_reply__content_height);
        if (null != attrs) {
            TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ARCaptureView, 0, 0);
            if (a.hasValue(R.styleable.ARCaptureView_arcapture_size)) {
                button_size = a.getDimensionPixelSize(R.styleable.ARCaptureView_arcapture_size, button_size);
            }
            a.recycle();
        }//
        button_radius = button_size / 2.0f;
        button_outside_radius = button_radius;
        button_inside_radius = button_outside_radius * 0.7f;

//        outside_add_size = button_size / 5;
//        inside_reduce_size = button_size / 8;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        center_X = (button_size + outside_add_size * 2) / 2;
        center_Y = (button_size + outside_add_size * 2) / 2;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setStyle(Paint.Style.FILL);

        mPaint.setColor(outside_color);
        canvas.drawCircle(center_X, center_Y, button_outside_radius, mPaint);

        mPaint.setColor(inside_color);
        canvas.drawCircle(center_X, center_Y, button_inside_radius, mPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(button_size + outside_add_size * 2, button_size + outside_add_size * 2);
    }
}
