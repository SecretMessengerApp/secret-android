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
package com.jsy.res.utils;

import android.content.Context;

public class SharedPreferencesHelper {
    public static final String SP_NAME_NORMAL = "SpUtils.SP_NAME_NORMAL";

    public static final String SP_KEY_DARK_MODE="dark_mode";
    public static final String SP_KEY_FOLLOW_SYSTEM="follow_system";

    public static boolean putBoolean(Context context, String spName, String key, boolean val) {
        if (spName == null) {
            spName = SP_NAME_NORMAL;
        }
        return context.getSharedPreferences(spName, Context.MODE_PRIVATE).edit().putBoolean(key, val).commit();
    }

    public static boolean getBoolean(Context context, String spName, String key, boolean defVal) {
        if (context == null) {
            return defVal;
        }
        if (spName == null) {
            spName = SP_NAME_NORMAL;
        }
        return context.getSharedPreferences(spName, Context.MODE_PRIVATE).getBoolean(key,false);
    }
}
