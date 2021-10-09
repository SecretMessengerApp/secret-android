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
package com.jsy.res.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TintableCompoundDrawablesView;

import com.jsy.res.R;
import com.jsy.res.utils.SharedPreferencesHelper;

public class ThemeUtils {
    private ThemeUtils() {
    }

    public static boolean isDarkTheme(Context context) {
        if (isFollowSystem(context)) {
            return isSystemDark(context);
        }
        return isDarkMode(context);
    }

    public static boolean isDarkMode(Context context) {
        return SharedPreferencesHelper.getBoolean(context, SharedPreferencesHelper.SP_NAME_NORMAL, SharedPreferencesHelper.SP_KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean isDarkTheme) {
        SharedPreferencesHelper.putBoolean(context, SharedPreferencesHelper.SP_NAME_NORMAL, SharedPreferencesHelper.SP_KEY_DARK_MODE, isDarkTheme);
    }

    public static boolean isFollowSystem(Context context) {
        return SharedPreferencesHelper.getBoolean(context, SharedPreferencesHelper.SP_NAME_NORMAL, SharedPreferencesHelper.SP_KEY_FOLLOW_SYSTEM, true);
    }

    public static void setFollowSystem(Context context, boolean flag) {
        SharedPreferencesHelper.putBoolean(context, SharedPreferencesHelper.SP_NAME_NORMAL, SharedPreferencesHelper.SP_KEY_FOLLOW_SYSTEM, flag);
    }

    public static boolean isSystemDark(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static String getThemeDesc(Context context) {
        if (ThemeUtils.isFollowSystem(context)) {
            return context.getResources().getString(R.string.dark_mode_follow_system);
        } else if (ThemeUtils.isDarkMode(context)) {
            return context.getResources().getString(R.string.dark_mode_enabled);
        } else {
            return context.getResources().getString(R.string.dark_mode_disabled);
        }
    }

    public static int getAttrColor(Resources.Theme theme, int attr) {
        if (attr == 0) {
            return 0;
        }
        TypedValue typedValue = new TypedValue();
        if (!theme.resolveAttribute(attr, typedValue, true)) {
            return 0;
        }
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return typedValue.data;
        }
        return 0;
    }

    public static ColorStateList getAttrColorStateList(Context context, Resources.Theme theme, int attr) {
        if (attr == 0) {
            return null;
        }
        TypedValue typedValue = new TypedValue();
        if (!theme.resolveAttribute(attr, typedValue, true)) {
            return null;
        }
        if (typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return ColorStateList.valueOf(typedValue.data);
        }
        if (typedValue.type == TypedValue.TYPE_ATTRIBUTE) {
            return getAttrColorStateList(context, theme, typedValue.data);
        }
        if (typedValue.resourceId == 0) {
            return null;
        }
        return ContextCompat.getColorStateList(context, typedValue.resourceId);
    }

    public static ColorStateList createColorStateList(int defaultColor, int selectedColor) {
        final int[][] states = new int[2][];
        final int[] colors = new int[2];
        states[0] = new int[]{};
        colors[0] = defaultColor;
        states[1] = new int[]{android.R.attr.state_selected};
        colors[1] = selectedColor;
        return new ColorStateList(states, colors);
    }

    public static void tintTextViewCompoundDrawable(TextView tv, @AttrRes int attr) {
        Context context = tv.getContext();
        ColorStateList colorStateList = getAttrColorStateList(context, context.getTheme(), attr);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tv.setCompoundDrawableTintList(colorStateList);
        } else if (tv instanceof TintableCompoundDrawablesView) {
            ((TintableCompoundDrawablesView) tv).setSupportCompoundDrawablesTintList(colorStateList);
        } else {
            Drawable[] drawables = tv.getCompoundDrawables();
            for (int i = 0; i < drawables.length; i++) {
                Drawable drawable = drawables[i];
                if (drawable != null) {
                    drawable = drawable.mutate();
                    LightingColorFilter colorFilter = new LightingColorFilter(Color.argb(255, 0, 0, 0), colorStateList.getDefaultColor());
                    drawable.setColorFilter(colorFilter);
                    drawables[i] = drawable;
                }
            }
            tv.setCompoundDrawables(drawables[0], drawables[1], drawables[2], drawables[3]);
        }

    }
}
