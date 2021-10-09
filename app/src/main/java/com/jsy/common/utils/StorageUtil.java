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
package com.jsy.common.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;


public class StorageUtil {

    public static String getInternalFilesPath(Context context) {
        return context.getFilesDir().getAbsolutePath();
    }

    public static String getInternalCachePath(Context context) {
        return context.getCacheDir().getAbsolutePath();
    }

    public static String getExternalSandBoxFilesPath(Context context) {
        return getExternalSandBoxPath(context, null);
    }

    public static String getExternalSandBoxPath(Context context, String type) {
        return context.getExternalFilesDir(type).getAbsolutePath();
    }

    public static String getExternalSandBoxCachePath(Context context) {
        return context.getExternalCacheDir().getAbsolutePath();
    }

    public static String getExternalStoragePublicPath(String type) {
        return Environment.getExternalStoragePublicDirectory(type).getAbsolutePath();
    }

    public static File getExternalStoragePublicDirectory(String type) {
        return Environment.getExternalStoragePublicDirectory(type);
    }
}
