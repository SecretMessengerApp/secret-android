/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.widget.AppCompatButton;
import android.util.AttributeSet;
import com.waz.zclient.R;

/**
 * https://www.cnblogs.com/cloudfloating/p/9858305.html
 */
public class ProgressButton extends AppCompatButton {

    private float mCornerRadius = 0;
    private float mProgressMargin = 0;

    private boolean mFinish;

    private int mProgress;
    private int mMaxProgress = 100;
    private int mMinProgress = 0;

    private GradientDrawable mDrawableButton;
    private GradientDrawable mDrawableProgressBackground;
    private GradientDrawable mDrawableProgress;

    public ProgressButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    public ProgressButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        //Progress background drawable
        mDrawableProgressBackground = new GradientDrawable();
        //Progress drawable
        mDrawableProgress = new GradientDrawable();
        //Normal drawable
        mDrawableButton = new GradientDrawable();

        //Get default normal color
        int defaultButtonColor = ContextCompat.getColor(context, R.color.gray);
        //Get default progress color
        int defaultProgressColor = ContextCompat.getColor(context, R.color.accent_green);
        //Get default progress background color
        int defaultBackColor = ContextCompat.getColor(context, R.color.gray);

        TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.ProgressButton);

        try {
            mProgressMargin = attr.getDimension(R.styleable.ProgressButton_progressMargin, mProgressMargin);
            mCornerRadius = attr.getDimension(R.styleable.ProgressButton_cornerRadius, mCornerRadius);
            //Get custom normal color
            int buttonColor = attr.getColor(R.styleable.ProgressButton_buttonColor, defaultButtonColor);
            //Set normal color
            mDrawableButton.setColor(buttonColor);
            //Get custom progress background color
            int progressBackColor = attr.getColor(R.styleable.ProgressButton_progressBackColor, defaultBackColor);
            //Set progress background drawable color
            mDrawableProgressBackground.setColor(progressBackColor);
            //Get custom progress color
            int progressColor = attr.getColor(R.styleable.ProgressButton_progressColor, defaultProgressColor);
            //Set progress drawable color
            mDrawableProgress.setColor(progressColor);

            //Get default progress
            mProgress = attr.getInteger(R.styleable.ProgressButton_progress, mProgress);
            //Get minimum progress
            mMinProgress = attr.getInteger(R.styleable.ProgressButton_minProgress, mMinProgress);
            //Get maximize progress
            mMaxProgress = attr.getInteger(R.styleable.ProgressButton_maxProgress, mMaxProgress);

        } finally {
            attr.recycle();
        }

        //Set corner radius
        mDrawableButton.setCornerRadius(mCornerRadius);
        mDrawableProgressBackground.setCornerRadius(mCornerRadius);
        mDrawableProgress.setCornerRadius(mCornerRadius - mProgressMargin);
        setBackgroundDrawable(mDrawableButton);

        mFinish = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mProgress > mMinProgress && mProgress <= mMaxProgress && !mFinish) {
            //Calculate the width of progress
            float progressWidth =
                    (float) getMeasuredWidth() * ((float) (mProgress - mMinProgress) / mMaxProgress - mMinProgress);

            //If progress width less than 2x corner radius, the radius of progress will be wrong
            if (progressWidth < mCornerRadius * 2) {
                progressWidth = mCornerRadius * 2;
            }

            //Set rect of progress
            mDrawableProgress.setBounds((int) mProgressMargin, (int) mProgressMargin,
                    (int) (progressWidth - mProgressMargin), getMeasuredHeight() - (int) mProgressMargin);

            //Draw progress
            mDrawableProgress.draw(canvas);

            if (mProgress == mMaxProgress) {
                setBackgroundDrawable(mDrawableButton);
                mFinish = true;
            }
        }
        super.onDraw(canvas);
    }

    /**
     * Set current progress
     */
    public void setProgress(int progress) {
        if (!mFinish) {
            mProgress = progress;
            setBackgroundDrawable(mDrawableProgressBackground);
            invalidate();
        }
    }

    public void setMaxProgress(int maxProgress) {
        mMaxProgress = maxProgress;
    }

    public void setMinProgress(int minProgress) {
        mMinProgress = minProgress;
    }

    public void reset() {
        mFinish = false;
        mProgress = mMinProgress;
    }
}
