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
package com.waz.zclient.pages.main.conversationlist.views.listview;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ViewConfiguration;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public class VelocityRecyclerView extends RecyclerView {
    public static final String TAG = VelocityRecyclerView.class.getName();
    // Cached ViewConfiguration and system-wide constant values
    private int minDefaultFlingVelocity;
    private int maxDefaultFlingVelocity;
    private int minFlingVelocity;
    private int maxFlingVelocity;

    public VelocityRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public VelocityRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VelocityRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        minDefaultFlingVelocity = vc.getScaledMinimumFlingVelocity();
        maxDefaultFlingVelocity = vc.getScaledMaximumFlingVelocity();
        float density = getResources().getDisplayMetrics().density;
        float scale = density * 2.0f / 3.0f;
        int velocity = (int) ((float) maxDefaultFlingVelocity / (scale <= 1 ? 1 : scale));
        setMaxFlingVelocity(velocity);
        setMinFlingVelocity(minDefaultFlingVelocity);
    }

    private void setMinFlingVelocity(int velocity) {
        this.minFlingVelocity = velocity;
    }

    public void setMaxFlingVelocity(int velocity) {
        this.maxFlingVelocity = velocity;
    }

    @Override
    public int getMaxFlingVelocity() {
        return maxFlingVelocity;
    }

    @Override
    public int getMinFlingVelocity() {
        return minFlingVelocity;
    }

    public int getMaxDefaultFlingVelocity() {
        return maxDefaultFlingVelocity;
    }

    public int getMinDefaultFlingVelocity() {
        return minDefaultFlingVelocity;
    }

    @Override
    public boolean fling(int velocityX, int velocityY) {
        LogUtils.i(TAG, "fling=11=velocityX==" + velocityX + "==velocityY==" + velocityY + "==mMaxFlingVelocity==" + maxFlingVelocity);
        velocityX = Math.max(-this.maxFlingVelocity, Math.min(velocityX, this.maxFlingVelocity));
        velocityY = Math.max(-this.maxFlingVelocity, Math.min(velocityY, this.maxFlingVelocity));
        return super.fling(velocityX, velocityY);
    }

}
