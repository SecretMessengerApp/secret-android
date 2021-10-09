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
package com.jsy.common.views.camera;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.waz.zclient.R;

import me.dm7.barcodescanner.core.DisplayUtils;

public class CameraARScanView extends View {
    private static final String TAG = CameraARScanView.class.getSimpleName();
    private Context mContext;
    protected Paint mLaserPaint;
    protected boolean mSquareViewFinder;
    private Rect mFramingRect;
    private Bitmap mBitmap;
    private int mBitShowW, mBitShowH;
    private Rect mSrcRect, mDesRect;
    private int mBitFreeHeight;


    public CameraARScanView(Context context) {
        super(context);
        init(context);
    }

    public CameraARScanView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public CameraARScanView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public CameraARScanView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        this.mContext = context;
        Resources res = mContext.getResources();
        this.mLaserPaint = new Paint();
        this.mLaserPaint.setStyle(Paint.Style.STROKE);

        mBitmap = ((BitmapDrawable) res.getDrawable(R.drawable.icon_ar_scan_anim)).getBitmap();
        mBitShowW = mBitmap.getWidth();
        mBitShowH = mBitmap.getHeight();
        mSrcRect = new Rect(0, 0, mBitShowW, mBitShowH);
        mBitFreeHeight = res.getDimensionPixelSize(R.dimen.dp25);
    }

    private static final int SCANNER_LASER = 35;
    private int lastBitY;

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect framingRect = this.getFramingRect();
        if (null != framingRect) {
            mDesRect = new Rect(framingRect.left, lastBitY, framingRect.right, lastBitY + mBitShowH);
            int alpha = (int) (((float) (lastBitY + mBitShowH) / (float) framingRect.bottom) * 255);
            alpha = alpha > 230 ? 255 : alpha;
            alpha = alpha < 100 ? 100 : alpha;
            mLaserPaint.setAlpha(alpha);
            canvas.drawBitmap(mBitmap, mSrcRect, mDesRect, mLaserPaint);
            lastBitY = lastBitY + mBitShowH >= (framingRect.bottom + mBitFreeHeight) ? (-mBitShowH + mBitFreeHeight) : lastBitY + SCANNER_LASER;
            lastBitY = lastBitY + mBitShowH > (framingRect.bottom + mBitFreeHeight) ? lastBitY - (lastBitY + mBitShowH - (framingRect.bottom + mBitFreeHeight)) : lastBitY;
            long delayMilliseconds = lastBitY == (-mBitShowH + mBitFreeHeight) ? 350L : 10L;
            this.postInvalidateDelayed(delayMilliseconds, framingRect.left - 10, framingRect.top - 10, framingRect.right + 10, framingRect.bottom + 10);
//            LogUtils.i(TAG, "framingRect startBitY:" + lastBitY
//                + ", (lastBitY + mBitShowH):" + (lastBitY + mBitShowH)
//                + ", stopBitY:" + framingRect.bottom
//                + ", lastBitY:" + lastBitY
//                + ", alpha:" + alpha
//                + ", framingRect.left:" + framingRect.left
//                + ", framingRect.right:" + framingRect.right
//            );
        }
    }

    private Rect getFramingRect() {
        return mFramingRect;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        this.updateFramingRect();
    }

    public synchronized void updateFramingRect() {
        Point viewResolution = new Point(this.getWidth(), this.getHeight());
        int orientation = DisplayUtils.getScreenOrientation(this.getContext());
        int width;
        int height;
        if (this.mSquareViewFinder) {
            if (orientation != 1) {
                height = (int) ((float) this.getHeight());
                width = height;
            } else {
                width = (int) ((float) this.getWidth());
                height = width;
            }
        } else if (orientation != 1) {
            height = (int) ((float) this.getHeight());
            width = (int) ((float) this.getWidth());
        } else {
            width = (int) ((float) this.getWidth());
            height = (int) ((float) this.getHeight());
        }

        if (width > this.getWidth()) {
            width = this.getWidth();
        }

        if (height > this.getHeight()) {
            height = this.getHeight();
        }


        mBitShowH = (int) ((float) width / (float) mBitShowW * (float) mBitShowH);
        mBitFreeHeight = (int) ((float) width / (float) mBitShowW * (float) mBitFreeHeight);
        mBitShowW = width;
        mBitShowH = mBitShowH > height ? height : mBitShowH;
        lastBitY = (-mBitShowH + mBitFreeHeight);

        int leftOffset = (viewResolution.x - width) / 2;
        int topOffset = (viewResolution.y - height) / 2;
        this.mFramingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);
    }
}
