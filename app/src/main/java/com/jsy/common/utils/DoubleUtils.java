/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.utils;

import android.view.View;

import java.util.WeakHashMap;

public class DoubleUtils {

    /**
     * Prevent continuous click, jump two pages
     */
    private static long lastClickTime;
    private final static long TIME = 1000;
    private final static WeakHashMap<View,Long> lastClickTimeMaps = new WeakHashMap<>();

    public static boolean isFastDoubleClick() {
        long time = System.currentTimeMillis();
        if (time - lastClickTime < TIME) {
            return true;
        }
        lastClickTime = time;
        return false;
    }
    public static boolean isFastDoubleClick(int interval) {
        long time = System.currentTimeMillis();
        if (time - lastClickTime < interval) {
            return true;
        }
        lastClickTime = time;
        return false;
    }

    public static boolean isFastDoubleClick(View view) {
        long time = System.currentTimeMillis();
        long lastClickTime = getLastClickTime(view);
        if (time - lastClickTime < TIME) {
            return true;
        }
        putLastClickTime(view, time);
        return false;
    }

    private static long getLastClickTime(View view) {
        if (lastClickTimeMaps.containsKey(view)) {
            return lastClickTimeMaps.get(view);
        }
        return 0;
    }

    private static void putLastClickTime(View view, long time) {
        lastClickTimeMaps.put(view, time);
    }

}
