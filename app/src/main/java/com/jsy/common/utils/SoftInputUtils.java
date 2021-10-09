/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

public class SoftInputUtils {

    public static void showWindowSoftInputMethod(Context context, View editTextOrAutocomplete) {
        if(editTextOrAutocomplete.isFocusable()
                || editTextOrAutocomplete.isFocusableInTouchMode()) {
            editTextOrAutocomplete.setFocusable(true);
            editTextOrAutocomplete.setFocusableInTouchMode(true);
        }
        if(!editTextOrAutocomplete.isFocused()) {
            editTextOrAutocomplete.requestFocus();
        }

        InputMethodManager inputMethodManager = getInputMethodManager(context);
        if(inputMethodManager != null) {
            inputMethodManager.showSoftInput(editTextOrAutocomplete, 0);
        }
    }

    public static void hindWindowSoftInputMethod(Context context, View editTextOrAutocomplete) {
        InputMethodManager inputMethodManager = getInputMethodManager(context);
        if(inputMethodManager != null && inputMethodManager.isActive()) {
            inputMethodManager.hideSoftInputFromWindow(editTextOrAutocomplete.getWindowToken(), 0);
        }
    }

    private static InputMethodManager getInputMethodManager(Context context) {
        return context != null ? (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE) : null;
    }

    public static void setSoftInputModeADJUST_RESIZE(Activity activity) {
        setSoftInputMode(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    public static void setSoftInputModeADJUST_PAN(Activity activity) {
        setSoftInputMode(activity, WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
    }

    private static void setSoftInputMode(Activity activity, int mode) {
        if(activity != null) {
            Window window = activity.getWindow();
            if(window != null) {
                window.setSoftInputMode(mode);
            }
        }
    }
}
