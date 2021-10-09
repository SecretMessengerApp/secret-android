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
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;

public class DividerListItemDecoration extends RecyclerView.ItemDecoration {

    private Drawable mDivider;
    private int dividerVerticalWidth;
    private int dividerVerticalHeight;

    private int mOrientation = OrientationHelper.VERTICAL;

    private boolean showLastDivider = false;

    /**
     *
     * @param context
     * @param orientation
     * @param resDrawableId
     * @param showLastDivider
     */
    public DividerListItemDecoration(Context context, int orientation, int resDrawableId, boolean showLastDivider) {
        mDivider = ContextCompat.getDrawable(context, resDrawableId);
        this.dividerVerticalWidth = mDivider.getIntrinsicWidth();
        this.dividerVerticalHeight = mDivider.getIntrinsicHeight();
        this.showLastDivider = showLastDivider;
        setOrientation(orientation);
    }

    /**
     *
     * @param orientation
     * @param drawable
     * @param dividerVerticalWidth
     * @param dividerHorizonzallHeight
     * @param showLastDivider
     */
    public DividerListItemDecoration(int orientation, Drawable drawable, int dividerVerticalWidth, int dividerHorizonzallHeight, boolean showLastDivider) {
        mDivider = drawable;
        this.dividerVerticalWidth = dividerVerticalWidth;
        this.dividerVerticalHeight = dividerHorizonzallHeight;
        this.showLastDivider = showLastDivider;
        setOrientation(orientation);
    }

    public void setOrientation(int orientation) {
        if (orientation != OrientationHelper.HORIZONTAL && orientation != OrientationHelper.VERTICAL) {
            throw new IllegalArgumentException("invalid orientation");
        }
        mOrientation = orientation;
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        if (mOrientation == OrientationHelper.VERTICAL) {
            drawVertical(c, parent, state);
        } else {
            drawHorizontal(c, parent, state);
        }
    }

    public void drawVertical(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int left = parent.getPaddingLeft();
        int right = parent.getWidth() - parent.getPaddingRight();
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (showLastDivider) {

            } else {
                if (i == childCount - 1) {
                    return;
                } else {

                }
            }
            final View child = parent.getChildAt(i);
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int top = child.getBottom() + params.bottomMargin;
            final int bottom = top + dividerVerticalHeight;
            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(c);
        }
    }

    public void drawHorizontal(Canvas c, RecyclerView parent, RecyclerView.State state) {
        int top = parent.getPaddingTop();
        int bottom = parent.getHeight() - parent.getPaddingBottom();
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (showLastDivider) {

            } else {
                if (i == childCount - 1) {
                    return;
                } else {

                }
            }
            final View child = parent.getChildAt(i);
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int left = child.getRight() + params.rightMargin;
            final int right = left + dividerVerticalWidth;
            mDivider.setBounds(left, top, right, bottom);
            mDivider.draw(c);
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {

        boolean b = state.willRunPredictiveAnimations();
        int itemPosition = ((RecyclerView.LayoutParams) view.getLayoutParams()).getViewLayoutPosition();
        int childCount = parent.getAdapter().getItemCount();
        if (childCount == itemPosition + 1) {
            outRect.set(0, 0, 0, 0);
            return;
        }

        if (mOrientation == OrientationHelper.VERTICAL) {
            outRect.set(0, 0, 0, dividerVerticalHeight);
        } else {
            outRect.set(0, 0, dividerVerticalWidth, 0);
        }
    }

}
