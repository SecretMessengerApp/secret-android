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
package com.waz.zclient.ui.text;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.jsy.common.acts.OpenUrlActivity;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.markdown.MarkdownTextView;
import com.waz.zclient.ui.utils.TextViewUtils;

import timber.log.Timber;

/**
 * This view will automatically linkify the text passed to {@link LinkTextView#setTextLink(OnClickSpanListener, OnLongClickSpanListener)}, but will not steal
 * touch events that are not inside a URLSpan
 */
public class LinkTextView extends MarkdownTextView {

    private MovementMethod movement;

    @SuppressWarnings("unused")
    public LinkTextView(Context context) {
        super(context);
        init(context);
    }

    @SuppressWarnings("unused")
    public LinkTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        init(context);
    }

    @SuppressWarnings("unused")
    public LinkTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.getTheme().obtainStyledAttributes(
            attrs,
            R.styleable.TypefaceTextView,
            0, 0);

        String font = a.getString(R.styleable.TypefaceTextView_w_font);
        if (!TextUtils.isEmpty(font) && !isInEditMode()) {
            setTypeface(font);
        }

        String transform = a.getString(R.styleable.TypefaceTextView_transform);
        if (!TextUtils.isEmpty(transform) && getText() != null) {
            setTransformedText(getText().toString(), transform);
        }

        a.recycle();

        init(context);
    }

    private void init(Context context) {
        movement = new MovementMethod(context);
    }


    public void setTextWithLink(String text, int color, boolean bold, boolean underline, Runnable onClick) {
        setText(text);
        TextViewUtils.boldText(this);
        TextViewUtils.linkifyText(this, color, bold, underline, onClick);
        // setMovementMethod(movement);
    }

    public void setTextLink(OnClickSpanListener onClickSpanListener, OnLongClickSpanListener onLongClickSpanListener) {
        // TODO: remove try/catch blocks when the bug is fixed
        try {
            if (Linkify.addLinks(this, Linkify.WEB_URLS/* | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS*/)) {
                stripUnderlines(onClickSpanListener, onLongClickSpanListener);
            }
        } catch (Throwable t) {
            // ignore
        }

        setMovementMethod(movement);
        // Linkify.addLinks() removes all existing URLSpan objects, so we need to re-apply
        // the ones added generated through markdown.
        try {
            refreshLinks();
        } catch (ArrayIndexOutOfBoundsException ex) {
            Timber.i("Error while refreshing links. text: %s", getText());
            if (BuildConfig.FLAVOR.equals("internal")) {
                throw ex;
            }
        }
    }


    /*
     * This part (the method stripUnderlines) of the Wire software uses source coded posted on the StackOverflow site.
     * (http://stackoverflow.com/a/9852280/1751834)
     *
     * That work is licensed under a Creative Commons Attribution-ShareAlike 2.5 Generic License.
     * (http://creativecommons.org/licenses/by-sa/2.5)
     *
     * Contributors on StackOverflow:
     *  - Andrei (http://stackoverflow.com/users/570217)
     */
    private void stripUnderlines(OnClickSpanListener onClickSpanListener, OnLongClickSpanListener onLongClickSpanListener) {
        if (getText() == null) {
            return;
        }
        if (!(getText() instanceof Spannable)) {
            return;
        }
        movement.setOnClickSpanListener(onClickSpanListener);
        movement.setOnLongClickSpanListener(onLongClickSpanListener);
        Spannable s = (Spannable) getText();
        URLSpan[] spans = s.getSpans(0, s.length(), URLSpan.class);
        for (URLSpan span : spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            s.removeSpan(span);
            URLSpan spanNew = new URLSpanNoUnderline(span.getURL());
            s.setSpan(spanNew, start, end, 0);
        }
        setText(s);
    }


    private static class URLSpanNoUnderline extends URLSpan {

        public URLSpanNoUnderline(String url) {
            super(url);
        }

        @Override
        public void onClick(View widget) {
        }

        public static final Parcelable.Creator<URLSpanNoUnderline> CREATOR
            = new Parcelable.Creator<URLSpanNoUnderline>() {
            public URLSpanNoUnderline createFromParcel(Parcel in) {
                return new URLSpanNoUnderline(in);
            }

            public URLSpanNoUnderline[] newArray(int size) {
                return new URLSpanNoUnderline[size];
            }
        };

        URLSpanNoUnderline(Parcel in) {
            super(in);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }
    }


    public interface OnClickSpanListener {
        void onClickSpanLink(String link);
    }

    public interface OnLongClickSpanListener {
        void onLongClickSpanLink(String link);
    }

    public static class MovementMethod extends LinkMovementMethod {

        private GestureDetector gestureDetector;
        private OnClickSpanListener onClickSpanListener;
        private OnLongClickSpanListener onLongClickSpanListener;

        public void setOnClickSpanListener(OnClickSpanListener onClickSpanListener) {
            this.onClickSpanListener = onClickSpanListener;
        }

        public void setOnLongClickSpanListener(OnLongClickSpanListener onLongClickSpanListener) {
            this.onLongClickSpanListener = onLongClickSpanListener;
        }

        public MovementMethod(final Context context) {
            this.gestureDetector = new GestureDetector(context, new GestureDetector.OnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return false;
                }

                @Override
                public void onShowPress(MotionEvent e) {
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    String url = current == null ? "" : current.getURL();
                    if (onClickSpanListener != null) {
                        onClickSpanListener.onClickSpanLink(url);
                    } else if (!TextUtils.isEmpty(url)) {
                        OpenUrlActivity.startSelf(context, url);
                    }
                    return false;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {
                    if (onLongClickSpanListener != null) {
                        String url = current == null ? "" : current.getURL();
                        onLongClickSpanListener.onLongClickSpanLink(url);
                    } else {
                        // ...
                    }
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    return false;
                }
            });
        }

        private URLSpan current;

        URLSpan getLink(TextView widget, Spannable buffer, MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            x -= widget.getTotalPaddingLeft();
            y -= widget.getTotalPaddingTop();
            x += widget.getScrollX();
            y += widget.getScrollY();
            Layout layout = widget.getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);
            ClickableSpan[] clickableSpans = buffer.getSpans(off, off, ClickableSpan.class);
            for (ClickableSpan clickableSpan : clickableSpans) {
                if (clickableSpan instanceof URLSpan) {
                    return (URLSpan) clickableSpan;
                }
            }
            return null;
        }

        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    current = getLink(widget, buffer, event);
                    break;
                default:
                    break;
            }
            gestureDetector.onTouchEvent(event);
            return super.onTouchEvent(widget, buffer, event);
        }

    }
}
