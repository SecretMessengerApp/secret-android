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
package com.jsy.common.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Scroller;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public class PagerScrollViewGroup2 extends ViewGroup {

    private String TAG = PagerScrollViewGroup2.class.getSimpleName();

    private int pagerCount;
    private int pagerWidth;
    private Context context;
    private GestureDetector gestureDetector;
    private Scroller scroller;
    private boolean startFilling = false;

    private int oldIndex;
    private int toIndex;

    private int scrollX;
    private final int SPEED = 880;

    private int getPagerWidth() {
        return pagerWidth <= 0 ? getWidth() : pagerWidth;
    }

    public void setPagerWidth(int pagerWidth) {
        this.pagerWidth = pagerWidth;
    }

    private int getPagerCount() {
        return pagerCount <= 0 ? getChildCount() : pagerCount;
    }

    public void setPagerCount(int pagerCount) {
        this.pagerCount = pagerCount;
    }

    public PagerScrollViewGroup2(Context context) {
        super(context);
        this.context = context;
        init();
    }

    public PagerScrollViewGroup2(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public PagerScrollViewGroup2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        init();
    }


    public int getCurrentIndex() {
        return toIndex;
    }

    public void scrollToIndex(int toIndex) {
        this.toIndex = toIndex;
        scroller.startScroll(scrollX, 0, -(scrollX - toIndex * getPagerWidth()), 0);
        invalidate();
    }

    private void init() {
        // onDown -> onShowPress -> onLongPress
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                int shouldReturn = shouldReturn(e1, e2);
                if (shouldReturn > 0) {
                    return true;
                }
                float vx = velocityX / context.getResources().getDisplayMetrics().density;
                if (vx > SPEED) {
                    startFilling = true;
                    if (toIndex != 0) {
                        oldIndex = toIndex;
                        toIndex--;
                    } else {
                    }
                    scroller.startScroll(scrollX, 0, -(scrollX - toIndex * getPagerWidth()), 0);
                    invalidate();

                    if (onPageScrollListener != null) {
                        onPageScrollListener.onPageSelected(toIndex);
                    }
                    return true;
                } else if (vx < -SPEED) {
                    startFilling = true;
                    if (toIndex < pagerCount - 1) {
                        oldIndex = toIndex;
                        toIndex++;
                    } else {
                    }
                    scroller.startScroll(scrollX, 0, -(scrollX - toIndex * getPagerWidth()), 0);
                    invalidate();

                    if (onPageScrollListener != null) {
                        onPageScrollListener.onPageSelected(toIndex);
                    }
                    return true;
                } else {
                    return super.onFling(e1, e2, velocityX, velocityY);
                }
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                return super.onSingleTapUp(e);
            }

            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
            }

            @Override
            public void onShowPress(MotionEvent e) {
                super.onShowPress(e);
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return super.onDoubleTap(e);
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                return super.onDoubleTapEvent(e);
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return super.onSingleTapConfirmed(e);
            }

            @Override
            public boolean onContextClick(MotionEvent e) {
                return super.onContextClick(e);
            }

            @Override
            public boolean onDown(MotionEvent e) {
                startFilling = false;
                return super.onDown(e);
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                int shouldReturn = shouldReturn(e1, e2);
                if (shouldReturn > 0) {
                    return true;
                }
                scrollBy((int) distanceX, 0);
                return super.onScroll(e1, e2, distanceX, distanceY);
            }
        });
        scroller = new Scroller(context);
    }

    private int shouldReturn(MotionEvent e1, MotionEvent e2) {
        int shouldReturn = 0;
        if (toIndex == pagerCount - 1) {
            if (e2.getX() < e1.getX() && getScrollX() >= getPagerWidth() * toIndex) {
                shouldReturn = 1;
            }
        }
        if (toIndex == 0) {
            if (e2.getX() > e1.getX() && getScrollX() <= 0) {
                shouldReturn = 2;
            }
        }
        return shouldReturn;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).measure(MeasureSpec.UNSPECIFIED, heightMeasureSpec);
        }
    }


    private int lastInterceptX;
    private int lastInterceptY;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastInterceptX = (int) ev.getX();
                lastInterceptY = (int) ev.getY();
                gestureDetector.onTouchEvent(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                int endX = (int) ev.getX();
                int endY = (int) ev.getY();
                int dx = endX - lastInterceptX;
                int dy = endY - lastInterceptY;
                if (Math.abs(dx) > Math.abs(dy)) {
                    return true;
                }
                break;
            default:
                break;
        }
        return false;

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (startFilling) {
            return true;
        }
        if (getPagerWidth() == 0) {
            return true;
        }
        gestureDetector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                oldIndex = toIndex;
                break;
            case MotionEvent.ACTION_MOVE:
                scrollX = getScrollX();
                toIndex = (scrollX + getPagerWidth() * 1 / 2) / getPagerWidth();

                if (toIndex >= pagerCount) {
                    toIndex = pagerCount - 1;
                }
                if (toIndex < 0) {
                    toIndex = 0;
                }
                if (onPageScrollListener != null) {
                    onPageScrollListener.onPageScrolled(getScrollX(), getPagerWidth(), oldIndex, toIndex);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_CANCEL:
                if (startFilling) {
                } else {
                    scroller.startScroll(scrollX, 0, -(scrollX - toIndex * getPagerWidth()), 0);
                    invalidate();

                    if (onPageScrollListener != null) {
                        onPageScrollListener.onPageSelected(toIndex);
                    }
                }
                break;
        }
        return true;
    }

    /**
     * postInvalidate
     */
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidate();
            if (onPageScrollListener != null) {
                onPageScrollListener.onPageScrolled(scroller.getCurrX(), getPagerWidth(), oldIndex, toIndex);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = getChildAt(i);
            childView.layout(i * getPagerCount() * getPagerWidth(), getPaddingTop(), (i + 1) * getPagerCount() * getPagerWidth(), getHeight() - getPaddingBottom());

        }
    }

    private OnPageScrollListener onPageScrollListener;

    public void setOnPageScrollListener(OnPageScrollListener onPageScrollListener) {
        this.onPageScrollListener = onPageScrollListener;
    }
}
