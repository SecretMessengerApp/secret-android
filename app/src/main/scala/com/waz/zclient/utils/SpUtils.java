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
package com.waz.zclient.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.jsy.res.utils.SharedPreferencesHelper;

import java.util.List;

public class SpUtils {

    public static final String SP_NAME_FOREVER_SAVED = "SpUtils..SP_NAME_FOREVER_SAVED";
    public static final String SP_KEY_LAUNCH_GUIDE_SHOWED = "launchGuideShowed";
    public static final String SP_KEY_APP_LAST_SAVED_VERSION = "lastSavedVersion";
    public static final String SP_KEY_CHECK_NOTIFICATION_PERMISSIONS = "notification_permissions";
    public static final String SP_KEY_OPEN_SETTING_SELFSTART = "open_setting_selfstart";
    public static final String SP_KEY_OPEN_SELFSTART_REMAINDER = "open_selfstart_remainder";
    public static final String SP_KEY_OPEN_SELFSTART_COUNT = "open_selfstart_count";
    public static final String SP_KEY_CHECK_VERSION_UPDATE_TIME = "checkVersionUpdateTime";


    public static final String SP_NAME_NORMAL = SharedPreferencesHelper.SP_NAME_NORMAL;
    public static final String SP_KEY_USERID = "userId";
    public static final String SP_KEY_CLIENTID = "clientId";
    public static final String SP_KEY_EMAIL = "email";
    public static final String SP_KEY_USERNAME = "name";
    public static final String SP_KEY_HANDLE = "handle";
    public static final String SP_KEY_COOKIES = "cookies";

    public static final String SP_KEY_TOKEN = "token";
    public static final String SP_KEY_TOKEN_TYPE = "token_type";

    public static final String SP_KEY_REMOTE_ASSET_ID = "remoteAssetId";

    public static final String SP_KEY_CLIENTID_REGID = "client_resid_";
    public static final String SP_KEY_REGID_TIME = "client_resid";

    public static SharedPreferences getSharedPreferences(final Context context, final String fileName) {
        String name = fileName;
        if (fileName == null) {
            name = SP_NAME_NORMAL;
        }
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    public static boolean putString(Context context, String spName, String key, String val) {
        if (context == null) return false;
        return getSharedPreferences(context, spName).edit().putString(key, val).commit();
    }

    public static boolean putInt(Context context, String spName, String key, int val) {
        if (context == null) return false;
        return getSharedPreferences(context, spName).edit().putInt(key, val).commit();
    }

    public static boolean putLong(Context context, String spName, String key, long val) {
        if (context == null) return false;
        return getSharedPreferences(context, spName).edit().putLong(key, val).commit();
    }

    public static boolean putBoolean(Context context, String spName, String key, boolean val) {
        if (context == null) return false;
        return getSharedPreferences(context, spName).edit().putBoolean(key, val).commit();
    }

    public static boolean getBoolean(Context context, String spName, String key, boolean defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, spName).getBoolean(key, defVal);
    }

    public static String getUserId(Context context, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_USERID, defVal);
    }

    public static String getClientId(Context context) {
        return context.getSharedPreferences(SP_NAME_NORMAL, Context.MODE_PRIVATE).getString(SP_KEY_CLIENTID, "");
    }

    public static String getRemoteAssetId(Context context, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_REMOTE_ASSET_ID, defVal);
    }

    public static String getUserEmail(Context context, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_EMAIL, defVal);
    }

    public static String getUserName(Context context, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_USERNAME, defVal);
    }

    public static String getUserHandle(Context context, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_HANDLE, defVal);
    }

    public static Boolean isSelfstartSetting(Context context, boolean defVal){
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_FOREVER_SAVED).getBoolean(SP_KEY_OPEN_SETTING_SELFSTART, defVal);
    }

    public static void setSelfstartSetting(Context context, boolean value){
        if (context != null) {
            getSharedPreferences(context, SP_NAME_FOREVER_SAVED).edit().putBoolean(SP_KEY_OPEN_SETTING_SELFSTART, value).apply();
        }
    }

    public static long getSelfstartRemainder(Context context, long defVal){
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_FOREVER_SAVED).getLong(SP_KEY_OPEN_SELFSTART_REMAINDER, defVal);
    }

    public static void setSelfstartRemainder(Context context, long value){
        if (context != null) {
            getSharedPreferences(context, SP_NAME_FOREVER_SAVED).edit().putLong(SP_KEY_OPEN_SELFSTART_REMAINDER, value).apply();
        }
    }

    public static int getSelfstartCount(Context context, int defVal){
        if (context == null) return defVal;
        return getSharedPreferences(context, SP_NAME_FOREVER_SAVED).getInt(SP_KEY_OPEN_SELFSTART_COUNT, defVal);
    }

    public static void setSelfstartCount(Context context, int value){
        if (context != null) {
            getSharedPreferences(context, SP_NAME_FOREVER_SAVED).edit().putInt(SP_KEY_OPEN_SELFSTART_COUNT, value).apply();
        }
    }

    public static String getString(Context context, String spName, String key, String defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, spName).getString(key, defVal);
    }

    public static int getInt(Context context, String spName, String key, int defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, spName).getInt(key, defVal);
    }

    public static long getLong(Context context, String spName, String key, long defVal) {
        if (context == null) return defVal;
        return getSharedPreferences(context, spName).getLong(key, defVal);
    }

    public static String getUserId(Context context) {
        if (context == null) return "";
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_USERID, "");
    }

    public static boolean clear(Context context, String spName) {
        if (context == null) return false;
        return getSharedPreferences(context, spName).edit().clear().commit();
    }

    public static String getTokenType(Context context) {
        if (context == null) return "";
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_TOKEN_TYPE, "");
    }

    public static String getToken(Context context) {
        if (context == null) return "";
        return getSharedPreferences(context, SP_NAME_NORMAL).getString(SP_KEY_TOKEN, "");
    }

    public static String getCookie(Context context) {
        return getString(context, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_COOKIES, "");
    }

    public static String getConversationBackgroundSpKey(Context context, String rConvId) {
        if (context == null) return null;
        return rConvId + getUserId(context);
    }

    public static String getLanguage(Context context) {
        String languageKey = "secret_language";
        return getString(context, SP_NAME_NORMAL, languageKey, "default");
    }

    public static void setLanguage(Context context, String newLanguage) {
        String languageKey = "secret_language";
        putString(context, SP_NAME_NORMAL, languageKey, newLanguage);
    }
}
