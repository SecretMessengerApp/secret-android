/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
import android.content.pm.PackageManager;

import static androidx.core.content.PermissionChecker.checkSelfPermission;

public class PermissionUtils {

    public static final int REQUEST_READ_CONTACTS = 1;
    public static final int CAMERA = 2;

    private PermissionUtils() {
    }

    public static boolean verifyPermissions(int... grantResults) {
        if (grantResults == null) {
            return true;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static boolean hasSelfPermissions(Context context, String... permissions) {
        if (permissions == null) {
            return true;
        }
        for (String permission : permissions) {
            if (checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}

