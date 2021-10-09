/*
 *    Copyright 2015 Kaopiz Software Co., Ltd.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.jsy.common.dialog;

import android.content.Context;
import android.graphics.Canvas;
import androidx.annotation.DrawableRes;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;


public class PictureSpinView extends AppCompatImageView implements PictureIndeterminate {

    private float mRotateDegrees;
    private int mFrameTime;
    private boolean mNeedToUpdateView;
    private Runnable mUpdateViewRunnable;

    public PictureSpinView(Context context) {
        super(context);
        init();
    }

    public PictureSpinView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {

        mFrameTime = 1000 / 12;
        mUpdateViewRunnable = new Runnable() {
            @Override
            public void run() {
                mRotateDegrees += 30;
                mRotateDegrees = mRotateDegrees < 360 ? mRotateDegrees : mRotateDegrees - 360;
                invalidate();
                if (mNeedToUpdateView) {
                    postDelayed(this, mFrameTime);
                }
            }
        };
    }

    @Override
    public void setAnimationSpeed(float scale) {
        mFrameTime = (int) (1000 / 12 / scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mNeedToUpdateView) {
            canvas.rotate(mRotateDegrees, getWidth() / 2, getHeight() / 2);
        }
        super.onDraw(canvas);
    }

    public void setImageResource(@DrawableRes int resId,boolean needToUpdateView){
        setImageResource(resId);
        mNeedToUpdateView = needToUpdateView;
        if(mNeedToUpdateView) {
            post(mUpdateViewRunnable);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        mNeedToUpdateView=false;
        super.onDetachedFromWindow();
    }
}
