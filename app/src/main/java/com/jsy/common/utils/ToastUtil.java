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
package com.jsy.common.utils;

import android.content.Context;
import android.widget.Toast;
import com.jsy.common.httpapi.HttpObserver;
import com.waz.zclient.R;

public class ToastUtil {

    public static void toastByResId(Context mContext, int resId) {
        if (mContext != null) {
            Toast.makeText(mContext.getApplicationContext(), resId, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static void toastByString(Context mContext, String s) {
        if (mContext != null) {
            Toast.makeText(mContext.getApplicationContext(), s, Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static void toastByCode(Context mContext, int code) {
        if (mContext != null) {
            Toast.makeText(mContext.getApplicationContext(), getResponseError(mContext, code), Toast.LENGTH_LONG)
                    .show();
        }
    }

    public static String getResponseError(Context context, int code) {
        switch (code) {
            case HttpObserver.ERR_LOCAL:
                return context.getResources().getString(R.string.connection_timeout);
            case HttpObserver.DATA_ERROR:
                return context.getResources().getString(R.string.dataError);
            case HttpObserver.HTTP_NOT_200:
                return context.getResources().getString(R.string.serverError);
            default:
                return context.getResources().getString(R.string.serverError);
        }
    }
}
