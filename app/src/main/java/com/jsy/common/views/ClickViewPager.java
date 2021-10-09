/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
import android.content.res.Resources;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import com.jsy.secret.sub.swipbackact.SwipBacActivity;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public class ClickViewPager extends ViewPager {

    private final String TAG = ClickViewPager.class.getSimpleName();

    private boolean interceptActionDown = false;
    private OnclickCallBack onclickCallBack;
    private boolean canScroll = false;

    private int displayWidth = Resources.getSystem().getDisplayMetrics().widthPixels;

    public interface OnclickCallBack {
        void onClickCallBack();
    }

    public void setCanScroll(boolean canScroll) {
        this.canScroll = canScroll;
    }

    public void setInterceptActionDown(boolean interceptActionDown) {
        this.interceptActionDown = interceptActionDown;
    }

    public void setOnclickListenner(OnclickCallBack onclickCallBack) {
        this.onclickCallBack = onclickCallBack;
    }

    public ClickViewPager(@NonNull Context context) {
        super(context);
    }

    public ClickViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (onclickCallBack != null) {
                    onclickCallBack.onClickCallBack();
                }
                LogUtils.v(TAG, "onInterceptTouchEvent ACTION_DOWN--");
                canScroll = ev.getX() < SwipBacActivity.MINI_PROGRAMS_SMOOTH_WIDTH || ev.getX() > displayWidth - SwipBacActivity.MINI_PROGRAMS_SMOOTH_WIDTH;
                break;
            case MotionEvent.ACTION_MOVE:
                LogUtils.v(TAG, "onInterceptTouchEvent ACTION_MOVE");
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_OUTSIDE:
                LogUtils.v(TAG, "onInterceptTouchEvent ACTION_UP");
                break;
        }

        if (canScroll) {
            return super.onInterceptTouchEvent(ev);
        } else {
            return canScroll;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                canScroll = ev.getX() < SwipBacActivity.MINI_PROGRAMS_SMOOTH_WIDTH || ev.getX() > displayWidth - SwipBacActivity.MINI_PROGRAMS_SMOOTH_WIDTH;
                break;
            case MotionEvent.ACTION_MOVE:
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
        if (!interceptActionDown && canScroll) {

            return super.onTouchEvent(ev);
        } else {
            return false;
        }

    }
}
