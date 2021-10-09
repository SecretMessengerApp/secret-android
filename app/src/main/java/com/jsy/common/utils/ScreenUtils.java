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
package com.jsy.common.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ScreenUtils {

    /**
     * dp2px
     */
    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static int statusbarheight;

    public static int getStatusBarHeight(Context context) {
        if (statusbarheight == 0) {
            try {
                Class<?> c = Class.forName("com.android.internal.R$dimen");
                Object o = c.newInstance();
                Field field = c.getField("status_bar_height");
                int x = (Integer) field.get(o);
                statusbarheight = context.getApplicationContext().getResources().getDimensionPixelSize(x);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (statusbarheight == 0) {
            statusbarheight = dip2px(context, 25);
        }
        return statusbarheight;
    }

    public static int navigationBarHeight = -1;

    public static int getNavigationBarHeight(Context context) {
        if (navigationBarHeight < 0) {
            try {
                if (checkDeviceHasNavigationBar(context)) {
                    Class<?> c = Class.forName("com.android.internal.R$dimen");
                    Object o = c.newInstance();
                    Field field = c.getField("navigation_bar_height");
                    int x = (Integer) field.get(o);
                    navigationBarHeight = context.getApplicationContext().getResources().getDimensionPixelSize(x);
                } else {
                    navigationBarHeight = 0;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return navigationBarHeight;
    }


    private static int screenWidth = 0;

    public static int getScreenWidth(Context context) {
        if(screenWidth > 0) {
            return screenWidth;
        }
        if(context == null) {
            return 0;
        }
        if(context instanceof Activity) {
            DisplayMetrics localDisplayMetrics = new DisplayMetrics();
            ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(localDisplayMetrics);
            screenWidth = localDisplayMetrics.widthPixels;
            return Math.max(0, screenWidth);
        }else {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            screenWidth = displayMetrics.widthPixels;
            return Math.max(0, screenWidth);
        }
    }

    private static int screenHeight = 0;

    public static int getScreenHeight(Context context) {
        if(screenHeight > 0) {
            return screenHeight;
        }
        if(context == null) {
            return 0;
        }
        if(context instanceof Activity) {
            DisplayMetrics localDisplayMetrics = new DisplayMetrics();
            ((Activity) context).getWindowManager().getDefaultDisplay().getMetrics(localDisplayMetrics);
            screenHeight = localDisplayMetrics.heightPixels - getStatusBarHeight(context);
            return Math.max(0, screenHeight);
        }else {
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            screenHeight = displayMetrics.heightPixels - getStatusBarHeight(context);
            return Math.max(0, screenHeight);
        }
    }

    public static boolean checkDeviceHasNavigationBar(Context context) {
        boolean hasNavigationBar = false;
        Resources rs = context.getResources();
        int id = rs.getIdentifier("config_showNavigationBar", "bool", "android");
        if (id > 0) {
            hasNavigationBar = rs.getBoolean(id);
        }
        try {
            Class systemPropertiesClass = Class.forName("android.os.SystemProperties");
            Method m = systemPropertiesClass.getMethod("get", String.class);
            String navBarOverride = (String) m.invoke(systemPropertiesClass, "qemu.hw.mainkeys");
            if ("1".equals(navBarOverride)) {
                hasNavigationBar = false;
            } else if ("0".equals(navBarOverride)) {
                hasNavigationBar = true;
            }
        } catch (Exception e) {

        }
        return hasNavigationBar;
    }
}
