/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;
import com.jsy.common.dialog.GroupShareLinkPopupWindow;
import com.waz.zclient.R;
import com.waz.zclient.ui.pullforaction.OverScrollListener;
import com.waz.zclient.ui.pullforaction.OverScrollMode;
import com.waz.zclient.ui.pullforaction.PullForActionView;
import com.waz.zclient.utils.ContextUtils;

public class ParticipantsGridView extends GridView implements PullForActionView, View.OnTouchListener {
    private static final float CLICK_EVENT_MAX_MOVE_DISTANCE = 10;
    private OverScrollListener overScrollListener;
    private Callback callback;
    private float actionDownX;
    private float actionDownY;

    public ParticipantsGridView(Context context) {
        super(context);
        init();
    }

    public ParticipantsGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }


    public ParticipantsGridView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        int leftPadding = getResources().getDimensionPixelSize(R.dimen.participants__left_margin);
        int rightPadding = getResources().getDimensionPixelSize(R.dimen.participants__right_margin);

        setVerticalSpacing(getResources().getDimensionPixelSize(R.dimen.participants__chathead__vertical_spacing));

        setPadding(leftPadding, 0, rightPadding, 0);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(false);

        setOnTouchListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int heightSpec;

        if (getLayoutParams().height == LayoutParams.WRAP_CONTENT) {

            heightSpec = MeasureSpec.makeMeasureSpec(

                    Integer.MAX_VALUE >> 2, MeasureSpec.AT_MOST);
        } else {

            heightSpec = heightMeasureSpec;

        }
        super.onMeasure(widthMeasureSpec, heightSpec);

    }

    @Override
    @SuppressWarnings("PMD.UselessOverridingMethod")
    public int computeVerticalScrollOffset() {
        return super.computeVerticalScrollOffset();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    //
    //  Overscroll stuff
    //
    //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Sets the overscroll listener from the PullToRefreshContainer.
     */
    public void setOverScrollListener(OverScrollListener listener) {
        overScrollListener = listener;
    }

    /**
     * The BouncingListVieContainer is notified from this spot.
     * At this place the default MaxOverscrollY can be overwritten.
     */
    @Override
    protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
        if (overScrollListener != null) {
            if (deltaY < 0) {
                overScrollListener.onOverScrolled(OverScrollMode.TOP);
            } else {
                overScrollListener.onOverScrolled(OverScrollMode.BOTTOM);
            }
        }
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, 0, isTouchEvent);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                actionDownX = motionEvent.getX();
                actionDownY = motionEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
                float actionUpX = motionEvent.getX();
                float actionUpY = motionEvent.getY();
                if (isClickEvent(actionDownX, actionUpX, actionDownY, actionUpY) && callback != null) {
                    callback.onClicked();
                }
                break;
        }
        return false;
    }

    private boolean isClickEvent(float startX, float endX, float startY, float endY) {
        float differenceX = Math.abs(startX - endX);
        float differenceY = Math.abs(startY - endY);
        return !(differenceX > CLICK_EVENT_MAX_MOVE_DISTANCE || differenceY > CLICK_EVENT_MAX_MOVE_DISTANCE);
    }

    public interface Callback {
        void onClicked();
    }


}
