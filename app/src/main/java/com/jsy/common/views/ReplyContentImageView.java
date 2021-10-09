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
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import androidx.appcompat.widget.AppCompatImageView;
import android.util.AttributeSet;


public class ReplyContentImageView extends AppCompatImageView {

    public ReplyContentImageView(Context context) {
        super(context);
    }

    public ReplyContentImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReplyContentImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private float[] radius = {0F, 0F, 18F, 18F, 18F, 18F, 0F, 0F};
    private Path path = new Path();
    private RectF roundRect = new RectF();

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if(changed) {
            roundRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if(!roundRect.isEmpty()) {
            path.reset();
            path.addRoundRect(roundRect, radius, Path.Direction.CW);
            canvas.clipPath(path);
        }

        super.onDraw(canvas);
    }
}
