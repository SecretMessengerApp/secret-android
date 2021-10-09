/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;


public class ModuleUtils {


    public static final String CLAZZ_AppEntryActivity = "com.waz.zclient.appentry.AppEntryActivity";

    public static final String CLAZZ_MainActivity = "com.waz.zclient.MainActivity";
    public static final String CLAZZ_ConversationActivity = "com.waz.zclient.ConversationActivity";

    public static Class<?> classForName(String clazzName) {
        try {
            return Class.forName(clazzName);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final boolean startActivity(Context context, String className) {
        Class clazz = classForName(className);
        if (clazz != null) {
            context.startActivity(new Intent(context, clazz));
            return true;
        }
        return false;
    }

    public static final boolean startActivity(Context context, String className, Bundle Bundle) {
        Class clazz = classForName(className);
        if (clazz != null) {
            Intent intent = new Intent(context, clazz);
            intent.putExtras(Bundle);
            context.startActivity(intent);
            return true;
        }
        return false;
    }
}

