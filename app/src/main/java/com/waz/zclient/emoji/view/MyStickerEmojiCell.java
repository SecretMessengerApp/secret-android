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
package com.waz.zclient.emoji.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.R;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.bean.GifSavedItem;
import com.waz.zclient.emoji.utils.EmojiUtils;

import org.telegram.ui.Components.RLottieImageView;

public class MyStickerEmojiCell extends FrameLayout {

    private RLottieImageView imageView;
    private EmotionItemBean sticker;
    private GifSavedItem gifSavedItem;
    private Object parentObject;
    private TextView emojiTextView;
    private float alpha = 1;
    private boolean changingAlpha;
    private long lastUpdateTime;
    private boolean scaled;
    private float scale;
    private long time;
    private boolean recent;
    private static AccelerateInterpolator interpolator = new AccelerateInterpolator(0.5f);

    public MyStickerEmojiCell(Context context) {
        this(context,null);

    }

    public MyStickerEmojiCell(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setAutoRepeat(true);
        imageView.setImageDrawable(context.getDrawable(R.drawable.emoji_placeholder));
        addView(imageView, new FrameLayout.LayoutParams(ScreenUtils.dip2px(getContext(),70), ScreenUtils.dip2px(getContext(),70), Gravity.CENTER));

        emojiTextView = new TextView(context);
        emojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiTextView.setTextColor(Color.BLACK);
        addView(emojiTextView, new FrameLayout.LayoutParams(ScreenUtils.dip2px(getContext(),28), ScreenUtils.dip2px(getContext(),28), Gravity.BOTTOM | Gravity.RIGHT));

        setFocusable(true);
    }

    public EmotionItemBean getSticker() {
        return sticker;
    }

    public Object getParentObject() {
        return parentObject;
    }

    public boolean isRecent() {
        return recent;
    }

    public void setRecent(boolean value) {
        recent = value;
    }


    public void setSticker(EmotionItemBean document, Object parent, boolean showEmoji) {
        if (document != null) {
            sticker = document;
            parentObject = parent;
            try {
                EmojiUtils.loadSticker(getContext(),document,imageView, ScreenUtils.dip2px(getContext(),66), ScreenUtils.dip2px(getContext(),66));
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (showEmoji) {
                String emoji=document.getName();
               if (!TextUtils.isEmpty(emoji)) {
                   emojiTextView.setText(emoji);
                   emojiTextView.setVisibility(VISIBLE);
               }
               else {
                   emojiTextView.setVisibility(INVISIBLE);
               }
            } else {
                emojiTextView.setVisibility(INVISIBLE);
            }
        }
    }

    public void setScaled(boolean value) {
        scaled = value;
        lastUpdateTime = System.currentTimeMillis();
        invalidate();
    }

    public boolean isDisabled() {
        return changingAlpha;
    }

    public RLottieImageView getImageView() {
        return imageView;
    }

    @Override
    public void invalidate() {
        emojiTextView.invalidate();
        super.invalidate();
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (child == imageView && (changingAlpha || scaled && scale != 0.8f || !scaled && scale != 1.0f)) {
            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastUpdateTime);
            lastUpdateTime = newTime;
            if (changingAlpha) {
                time += dt;
                if (time > 1050) {
                    time = 1050;
                }
                alpha = 0.5f + interpolator.getInterpolation(time / 1050.0f) * 0.5f;
                if (alpha >= 1.0f) {
                    changingAlpha = false;
                    alpha = 1.0f;
                }
                imageView.setAlpha(alpha);
            } else if (scaled && scale != 0.8f) {
                scale -= dt / 400.0f;
                if (scale < 0.8f) {
                    scale = 0.8f;
                }
            } else {
                scale += dt / 400.0f;
                if (scale > 1.0f) {
                    scale = 1.0f;
                }
            }
            imageView.setScaleX(scale);
            imageView.setScaleY(scale);
            imageView.invalidate();
            invalidate();
        }
        return result;
    }


}
