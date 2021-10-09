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
package com.waz.zclient.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PointFEvaluator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;

public class MessageBubbleView extends View {

    private static final String TAG = "MessageBubbleView";

    public static int STATE_NORMAL = 0;
    public static int STATE_DISAPPEAR = 1;
    public static int STATE_DRAGING = 2;
    public static int STATE_MOVE = 3;

    Paint mPaint;
    Paint textPaint;
    Paint disappearPaint;
    Path mPath;
    float textMove;
    float centerRadius;
    float dragRadius;
    int dragCircleX;
    int centerCircleX;
    int dragCircleY;
    int centerCircleY;
    float d;

    String mNumber;
    int maxDragLength;
    float textSize;
    int textColor;
    int circleColor;

    int[] disappearPic;
    Bitmap[] disappearBitmap;
    Rect bitmapRect;
    int bitmapIndex;
    boolean startDisappear;

    ActionListener actionListener;

    int curState;


    public MessageBubbleView(Context context) {
        this(context, null);
    }

    public MessageBubbleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MessageBubbleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.MessageBubbleView);
        circleColor = ta.getColor(R.styleable.MessageBubbleView_mbv_circleColor, Color.RED);
        textColor = ta.getColor(R.styleable.MessageBubbleView_mbv_textColor, Color.WHITE);
        textSize = ta.getDimension(R.styleable.MessageBubbleView_mbv_textSize, sp2px(12));
        mNumber = ta.getString(R.styleable.MessageBubbleView_mbv_textNumber);
        ta.recycle();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        LogUtils.d(TAG, "onSizeChanged() called with: w = [" + w + "], h = [" + h + "], oldw = [" + oldw + "], oldh = [" + oldh + "]");
        init();
    }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(circleColor);
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(textColor);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(textSize);
        disappearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        disappearPaint.setFilterBitmap(false);

        Paint.FontMetrics textFontMetrics = textPaint.getFontMetrics();
        textMove = -(textFontMetrics.top + textFontMetrics.bottom) / 2;

        centerCircleX = getWidth() / 2;
        centerCircleY = getHeight() / 2;
        centerRadius = Math.min(getWidth(), getHeight()) / 2f;

        mPath = new Path();
        dragRadius = centerRadius;
        dragCircleX = centerCircleX;
        dragCircleY = centerCircleY;

        maxDragLength = (int) (4 * dragRadius);

        if (disappearPic == null) {
            disappearPic = new int[]{R.drawable.explosion_one, R.drawable.explosion_two, R.drawable.explosion_three, R.drawable.explosion_four, R.drawable.explosion_five};
        }
        if(disappearBitmap==null) {
            disappearBitmap = new Bitmap[disappearPic.length];
            for (int i = 0; i < disappearPic.length; i++) {
                disappearBitmap[i] = BitmapFactory.decodeResource(getResources(), disappearPic[i]);
            }
        }
        curState = STATE_NORMAL;
        startDisappear = false;

    }


    @Override
    protected void onMeasure(int widthMeasure, int heightMeasure) {
        int widthMode = MeasureSpec.getMode(widthMeasure);
        int widthSize = MeasureSpec.getSize(widthMeasure);
        int heightMode = MeasureSpec.getMode(heightMeasure);
        int heightSize = MeasureSpec.getSize(heightMeasure);
        int mWidth,mHeight;
        if (widthMode == MeasureSpec.EXACTLY) {
            mWidth = widthSize;
        } else {
            mWidth = getPaddingLeft() + dp2px(20) + getPaddingRight();
        }
        if (heightMode == MeasureSpec.EXACTLY) {
            mHeight = heightSize;
        } else {
            mHeight = getPaddingTop() + dp2px(20) + getPaddingBottom();
        }
        setMeasuredDimension(mWidth, mHeight);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                if (curState != STATE_DISAPPEAR) {
                    d = (float) Math.hypot(centerCircleX - event.getX(), centerCircleY - event.getY());
                    if (d < centerRadius + 40) {
                        curState = STATE_DRAGING;
                    } else {
                        curState = STATE_NORMAL;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                dragCircleX = (int) event.getX();
                dragCircleY = (int) event.getY();
                if (curState == STATE_DRAGING) {
                    d = (float) Math.hypot(centerCircleX - event.getX(), centerCircleY - event.getY());
                    if (d <= maxDragLength - maxDragLength / 7f) {
                        centerRadius = dragRadius - d / 4;
                        if (actionListener != null) {
                            actionListener.onDrag();
                        }
                    } else {
                        centerRadius = 0;
                        curState = STATE_MOVE;
                    }
                } else if (curState == STATE_MOVE) {
                    if (actionListener != null) {
                        actionListener.onMove();
                    }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if (curState == STATE_DRAGING || curState == STATE_MOVE) {
                    d = (float) Math.hypot(centerCircleX - event.getX(), centerCircleY - event.getY());
                    if (d > maxDragLength) {
                        curState = STATE_DISAPPEAR;
                        startDisappear = true;
                        disappearAnim();
                    } else {
                        restoreAnim();
                    }
                    invalidate();
                }
                break;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        LogUtils.d(TAG, "onDraw() called with: canvas = [" + canvas + "]");
        if(getVisibility()!=View.VISIBLE){
            return;
        }

        if (curState == STATE_NORMAL) {
            canvas.drawCircle(centerCircleX, centerCircleY, centerRadius, mPaint);
            if (!TextUtils.isEmpty(mNumber)) {
                canvas.drawText(mNumber, centerCircleX, centerCircleY + textMove, textPaint);
            }
        } else if (curState == STATE_DRAGING) {
            canvas.drawCircle(centerCircleX, centerCircleY, centerRadius, mPaint);
            canvas.drawCircle(dragCircleX, dragCircleY, dragRadius, mPaint);
            drawBezier(canvas);
            if(!TextUtils.isEmpty(mNumber)) {
                canvas.drawText(mNumber, dragCircleX, dragCircleY + textMove, textPaint);
            }
        } else if (curState == STATE_MOVE) {
            canvas.drawCircle(dragCircleX, dragCircleY, dragRadius, mPaint);
            if(!TextUtils.isEmpty(mNumber)) {
                canvas.drawText(mNumber, dragCircleX, dragCircleY + textMove, textPaint);
            }
        } else if (curState == STATE_DISAPPEAR && startDisappear) {
            if (disappearBitmap != null) {
                canvas.drawBitmap(disappearBitmap[bitmapIndex], null, bitmapRect, disappearPaint);
            }
        }

    }

    private void disappearAnim() {
        bitmapRect = new Rect(dragCircleX - (int) dragRadius, dragCircleY - (int) dragRadius, dragCircleX + (int) dragRadius, dragCircleY + (int) dragRadius);
        ValueAnimator disappearAnimator = ValueAnimator.ofInt(0, disappearBitmap.length);
        disappearAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                bitmapIndex = (int) animation.getAnimatedValue();
                invalidate();
            }
        });
        disappearAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startDisappear = false;
                if (actionListener != null) {
                    actionListener.onDisappear();
                }
            }
        });
        disappearAnimator.setInterpolator(new LinearInterpolator());
        disappearAnimator.setDuration(500);
        disappearAnimator.start();
    }

    private void restoreAnim() {
        ValueAnimator valueAnimator = ValueAnimator.ofObject(new PointFEvaluator(), new PointF(dragCircleX, dragCircleY), new PointF(centerCircleX, centerCircleY));
        valueAnimator.setDuration(200);
        valueAnimator.setInterpolator(new TimeInterpolator() {
            @Override
            public float getInterpolation(float input) {
                float f = 0.571429f;
                return (float) (Math.pow(2, -4 * input) * Math.sin((input - f / 4) * (2 * Math.PI) / f) + 1);
            }
        });
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                PointF pointF = (PointF) animation.getAnimatedValue();
                dragCircleX = (int) pointF.x;
                dragCircleY = (int) pointF.y;
                invalidate();
            }
        });
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                centerRadius = dragRadius;
                curState = STATE_NORMAL;
                if (actionListener != null) {
                    actionListener.onRestore();
                }
            }
        });
        valueAnimator.start();
    }

    private void drawBezier(Canvas canvas) {
        float controlX = (centerCircleX + dragCircleX) / 2;
        float controlY = (dragCircleY + centerCircleY) / 2;
        d = (float) Math.hypot(centerCircleX - dragCircleX, centerCircleY - dragCircleY);
        float sin = (centerCircleY - dragCircleY) / d;
        float cos = (centerCircleX - dragCircleX) / d;
        float dragCircleStartX = dragCircleX - dragRadius * sin;
        float dragCircleStartY = dragCircleY + dragRadius * cos;
        float centerCircleEndX = centerCircleX - centerRadius * sin;
        float centerCircleEndY = centerCircleY + centerRadius * cos;
        float centerCircleStartX = centerCircleX + centerRadius * sin;
        float centerCircleStartY = centerCircleY - centerRadius * cos;
        float dragCircleEndX = dragCircleX + dragRadius * sin;
        float dragCircleEndY = dragCircleY - dragRadius * cos;

        mPath.reset();
        mPath.moveTo(centerCircleStartX, centerCircleStartY);
        mPath.quadTo(controlX, controlY, dragCircleEndX, dragCircleEndY);
        mPath.lineTo(dragCircleStartX, dragCircleStartY);
        mPath.quadTo(controlX, controlY, centerCircleEndX, centerCircleEndY);
        mPath.close();

        canvas.drawPath(mPath, mPaint);
    }


    @Override
    public void setVisibility(int visibility) {
        LogUtils.d(TAG, "setVisibility() called with: visibility = [" + visibility + "]");
        if(getVisibility()!=visibility) {
            super.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                show();
            } else {
                hide();
            }
        }
    }

    private void show() {
        LogUtils.d(TAG, "show() called");
        init();
        invalidate();
    }

    private void hide(){
        LogUtils.d(TAG, "hide() called");
        curState =STATE_DISAPPEAR;
        startDisappear=false;
        invalidate();
    }

    public void setNumber(String number) {
        mNumber = number;
        invalidate();
    }


    public interface ActionListener {

        void onDrag();

        void onDisappear();

        void onRestore();

        void onMove();
    }


    public void setOnActionListener(ActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public static int dp2px(final float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static int sp2px(final float spValue) {
        final float fontScale = Resources.getSystem().getDisplayMetrics().scaledDensity;
        return (int) (spValue * fontScale + 0.5f);
    }

}
