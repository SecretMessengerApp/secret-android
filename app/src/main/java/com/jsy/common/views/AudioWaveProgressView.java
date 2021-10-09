/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public class AudioWaveProgressView extends View {
    public static final String TAG = AudioWaveProgressView.class.getSimpleName();
    public static final int MAX_NUM_OF_LEVELS = 100;
    private long duration;
    private long currentHead;


    private Paint activePaint;
    private Paint inactivePaint;
    private float[] levels;
    private float binSpaceWidth;

    public void setAccentColor(int accentColor) {
        activePaint.setColor(accentColor);
    }

    public AudioWaveProgressView(Context context) {
        super(context);
        init(context);
    }

    public AudioWaveProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AudioWaveProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public AudioWaveProgressView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public void init(Context context) {
        activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        activePaint.setColor(Color.BLUE);
        inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        inactivePaint.setColor(Color.parseColor("#E87746"));
        binSpaceWidth = 2F;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        LogUtils.i(TAG, this + "onDraw levels:" + levels);
        if (levels == null) {
            return;
        }

        int height = canvas.getHeight();
        int width = canvas.getWidth();

        float inactiveWidth = width - (MAX_NUM_OF_LEVELS - 1) * binSpaceWidth;

        float binWidth = inactiveWidth / MAX_NUM_OF_LEVELS;

        float currentX = 0F;

        int breakPoint = (int) (MAX_NUM_OF_LEVELS * currentHead * 1.0f / duration);

        for (int i = 0; i < breakPoint; i++) {
            if (i > MAX_NUM_OF_LEVELS - 1) {
                return;
            }
            float lh = levels[i] * height;
            if (lh < binWidth) {
                lh = binWidth;
            }
            float top = (height - lh) / 2;

            canvas.drawRect(currentX, top, currentX + binWidth, top + lh, activePaint);
            currentX += binWidth + binSpaceWidth;
        }

        for (int i = breakPoint; i < MAX_NUM_OF_LEVELS; i++) {
            float lh = levels[i] * height;
            if (lh < binWidth) {
                lh = binWidth;
            }
            float top = (height - lh) / 2;

            canvas.drawRect(currentX, top, currentX + binWidth, top + lh, inactivePaint);
            currentX += binWidth + binSpaceWidth;
        }
    }

    public void onPlaybackStopped(long seconds) {
        LogUtils.i(TAG, this + "onPlaybackStopped seconds:" + seconds);
        this.currentHead = 0;
        this.duration = seconds;
        invalidate();
    }


    public void onPlaybackStarted(float[] levels) {
        this.levels = levels;
        LogUtils.i(TAG, this + "onPlaybackStarted levels:" + levels);
    }

    public void onPlaybackProceeded(long current, long total) {
        this.currentHead = current;
        this.duration = total;
        invalidate();
    }

    public void onPlaybackProceeded(long current) {
        LogUtils.i(TAG, this + "onPlaybackProceeded current:" + current);
        this.currentHead = current;
        invalidate();
    }
}
