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
package com.waz.zclient.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

public class KeyboardUtils {

    private KeyboardUtils() {
    }

    public static boolean isKeyboardVisible(Context context) {
        return getInputMethodManager(context).isActive();
    }

    public static void closeKeyboardIfShown(Activity activity) {
        if (isKeyboardVisible(activity)) {
            hideKeyboard(activity);
        }
    }

    public static boolean keyboardIsVisible(View contentView) {
        return getKeyboardHeight(contentView) > 0;
    }

    public static void hideKeyboard(Activity activity) {
        View view = null == activity ? null : activity.getCurrentFocus();
        if (view != null) {
            getInputMethodManager(activity).hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static boolean showKeyboard(Activity activity) {
        View view = null == activity ? null : activity.getCurrentFocus();
        if (view != null) {
            getInputMethodManager(activity).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            return true;
        }
        return false;
    }

    public static int getSoftButtonsBarHeight(Activity activity) {
        DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        if (realHeight > usableHeight) {
            return realHeight - usableHeight;
        } else {
            return 0;
        }
    }

    private static InputMethodManager getInputMethodManager(Context context) {
        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    public static int getKeyboardHeight(View contentView) {
        Rect r = new Rect();
        contentView.getWindowVisibleDisplayFrame(r);
        return contentView.getRootView().getHeight() - r.height();
    }

    /**
     * Hide the soft input.
     *
     * @param activity The activity.
     */
    public static void hideSoftInput(final Activity activity) {
        if(activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if(imm == null) return;
            View view = activity.getCurrentFocus();
            if(view == null) view = new View(activity);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Hide the soft input.
     *
     * @param view The view.
     */
    public static void hideSoftInput(final View view) {
        if(view != null) {
            InputMethodManager imm = (InputMethodManager) view.getContext().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm == null) return;
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Return whether soft input is visible.
     * <p>The minimum height is 200</p>
     *
     * @param activity The activity.
     * @return {@code true}: yes<br>{@code false}: no
     */
    public static boolean isSoftInputVisible(final Activity activity) {
        return isSoftInputVisible(activity, 200);
    }

    /**
     * Return whether soft input is visible.
     *
     * @param activity             The activity.
     * @param minHeightOfSoftInput The minimum height of soft input.
     * @return {@code true}: yes<br>{@code false}: no
     */
    public static boolean isSoftInputVisible(final Activity activity,
                                             final int minHeightOfSoftInput) {
        return getContentViewInvisibleHeight(activity) >= minHeightOfSoftInput;
    }

    private static int getContentViewInvisibleHeight(final Activity activity) {
        final FrameLayout contentView = activity.findViewById(android.R.id.content);
        final View contentViewChild = contentView.getChildAt(0);
        final Rect outRect = new Rect();
        contentViewChild.getWindowVisibleDisplayFrame(outRect);
        return contentViewChild.getBottom() - outRect.bottom;
    }

    /**
     * Show the soft input.
     *
     * @param activity The activity.
     */
    public static void showSoftInput(final Activity activity) {
        if(activity != null) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if(imm == null) return;
            View view = activity.getCurrentFocus();
            if(view == null) {
                view = new View(activity);
                view.setFocusable(true);
                view.setFocusableInTouchMode(true);
                view.requestFocus();
            }
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Show the soft input.
     *
     * @param view The view.
     */
    public static void showSoftInput(final View view) {
        if(view != null) {
            InputMethodManager imm = (InputMethodManager) view.getContext().getApplicationContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm == null) return;
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.requestFocus();
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
