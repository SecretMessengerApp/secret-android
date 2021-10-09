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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.customview.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public class DragViewGroup extends FrameLayout {
    public final static String TAG = DragViewGroup.class.getSimpleName();
    private Context mContext;
    private ViewDragHelper dragHelper;
    private int mOldLeft;
    private int mOldTop;
    private int dragDistance;
    private int minKeep;
    private int dragViewH;
    private OnDragListener dragListener;
    private boolean isDragBottom = false;

    public DragViewGroup(@NonNull Context context) {
        this(context, null);
    }

    public DragViewGroup(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DragViewGroup(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void setMinKeepDistance(int minKeep, int moveH) {
        this.minKeep = minKeep;
        this.dragViewH = minKeep + moveH;
    }

    public void setDragViewH(int dragViewH) {
        this.dragViewH = dragViewH;
    }

    public void setOnDragListener(OnDragListener dragListener) {
        this.dragListener = dragListener;
    }

    public void resetCapturedViewAt() {
        isDragBottom = false;
//        if (getVisibility() == VISIBLE && null != dragHelper) {
//            dragHelper.settleCapturedViewAt(0, 0);
//            invalidate();
//        }
    }

    private void init(Context context) {
        this.mContext = context;
        isDragBottom = false;
        dragHelper = ViewDragHelper.create(this, new ViewDragHelper.Callback() {
            @Override
            public boolean tryCaptureView(View child, int pointerId) {
                LogUtils.i(TAG, "tryCaptureView,child:" + child + ", pointerId:" + pointerId);
                return true;
            }

            @Override
            public void onViewReleased(View releasedChild, float xvel, float yvel) {
                LogUtils.i(TAG, "onViewReleased,releasedChild:" + releasedChild + ", xvel:" + xvel + ", yvel:" + yvel
                    + ", mOldLeft:" + mOldLeft + ", mOldTop:" + mOldTop + ", dragDistance:" + dragDistance + ", dragViewH:" + dragViewH + ", minKeep:" + minKeep);
                if (dragDistance < dragViewH / 2) {
                    isDragBottom = false;
                    dragHelper.settleCapturedViewAt(mOldLeft, 0);
                } else {
                    dragHelper.settleCapturedViewAt(mOldLeft, dragViewH - minKeep);
                    isDragBottom = true;
                }
                invalidate();
                if (null != dragListener) {
                    dragListener.onViewReleased(isDragBottom);
                }
            }

            @Override
            public void onViewCaptured(View capturedChild, int activePointerId) {
                LogUtils.i(TAG, "onViewCaptured,capturedChild:" + capturedChild + ", activePointerId:" + activePointerId + ", mOldLeft:" + mOldLeft + ", mOldTop:" + mOldTop);
                if (null != dragListener) {
                    dragListener.onViewCaptured();
                }
                mOldLeft = capturedChild.getLeft();
                mOldTop = capturedChild.getTop();
                if (mOldTop == 0) {
                    isDragBottom = false;
                }
            }

            @Override
            public int clampViewPositionVertical(View child, int top, int dy) {
//                LogUtils.i(TAG, "clampViewPositionVertical,child:" + child + ", top:" + top + ", dy:" + dy);
                int paddingTop = getPaddingTop();
                if (top < paddingTop) {
                    dragDistance = 0;
                    return paddingTop;
                }

                int pos = dragViewH - getPaddingBottom() - minKeep;
                if (top > pos) {
                    dragDistance = pos;
                    return pos;
                }
                isDragBottom = false;
                dragDistance = top;
                return top;
            }

            @Override
            public int clampViewPositionHorizontal(View child, int left, int dx) {
//                LogUtils.i(TAG, "clampViewPositionHorizontal,child:" + child + ", left:" + left + ", dx:" + dx);
                int paddingleft = getPaddingLeft();
                if (left < paddingleft) {
                    return paddingleft;
                }
                int pos = getWidth() - child.getWidth() - getPaddingRight();
                if (left > pos) {
                    return pos;
                }
                return left;
            }
        });
    }


    @Override
    public void computeScroll() {
        super.computeScroll();
        if (dragHelper != null && dragHelper.continueSettling(true)) {
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
//        dragViewH = h;
//        LogUtils.i(TAG, "onSizeChanged,h:" + h + ", w:" + w + ", oldw:" + oldw + ", oldh:" + oldh);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
//        LogUtils.i(TAG, "onInterceptTouchEvent,ev:" + ev.getAction() + ",isDragBottom:" + isDragBottom);
        if (dragHelper != null && isTouchDragArea(ev)) {
            return dragHelper.shouldInterceptTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
//        LogUtils.i(TAG, "onTouchEvent,event:" + event.getAction() + ",isDragBottom:" + isDragBottom);
        if (dragHelper != null && isTouchDragArea(event)) {
            dragHelper.processTouchEvent(event);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private boolean isTouchDragArea(MotionEvent event) {
        if (isDragBottom && null != event) {
            float y = event.getY();
            float bottomAreaH = (float) dragViewH - (float) minKeep * 1.5F;
            LogUtils.i(TAG, "isTouchDragArea,getY:" + y + ", getX:" + event.getX() + ",bottomAreaH:" + bottomAreaH);
            return y > bottomAreaH;
        }
        return true;
    }

    public interface OnDragListener {
        void onViewCaptured();

        void onViewReleased(boolean isBottom);
    }
}
