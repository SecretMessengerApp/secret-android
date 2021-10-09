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
package com.waz.zclient.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.sync.client.CustomBackendClient;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.ZApplication;


public class ServerConfig {
    private static final String TAG = ServerConfig.class.getSimpleName();
    private static final String KEY_BASEURL = "BaseUrl_";
    private static Context mContext;

    public static void initServerConfig(Context context) {
        mContext = context.getApplicationContext();
    }

    private static SharedPreferences getSharedPreferences() {
        Context context;
        if (null == ZApplication.getInstance()) {
            context = mContext;
        } else {
            context = ZApplication.getInstance().getZApplication();
        }
        if (null == context) {
            LogUtils.e(TAG, "getSharedPreferences null == context");
        }
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static String getStringPreference(String key, String defValue) {
        SharedPreferences sp = getSharedPreferences();
        if (null == sp) {
            return defValue;
        }
        String value = sp.getString(key, defValue);
        if (TextUtils.isEmpty(value)) {
            return defValue;
        }
        return value;
    }

    private static String configUrl;
    private static String curUserId;

    private static String baseUrl;
    private static String websocketUrl;
    private static String blackListHost;
    private static String teamsUrl;
    private static String accountsUrl;
    private static String websiteUrl;
    private static String signInUrl;

    public static void updateBackendConfig(CustomBackendClient.BackendConfigResponse configResponse) {
        CustomBackendClient.EndPoints endPoints = null == configResponse ? null : configResponse.endpoints();
        if (null != endPoints) {
            LogUtils.i(TAG, "updateBackendConfig curUserId:" + curUserId + ", configUrl:" + configUrl);
            configUrl = "";
            curUserId = "";
            configUrl = getConfigUrl();
            baseUrl = null == endPoints.backendURL() ? "" : endPoints.backendURL().toString();
            websocketUrl = null == endPoints.backendWSURL() ? "" : endPoints.backendWSURL().toString();
            blackListHost = null == endPoints.blackListURL() ? "" : endPoints.blackListURL().toString();
            teamsUrl = null == endPoints.teamsURL() ? "" : endPoints.teamsURL().toString();
            accountsUrl = null == endPoints.accountsURL() ? "" : endPoints.accountsURL().toString();
            websiteUrl = null == endPoints.websiteURL() ? "" : endPoints.websiteURL().toString();
            signInUrl = null == endPoints.signInUrl() ? "" : endPoints.signInUrl().toString();
        } else {
            removeBackendConfig();
        }
    }

    public static void removeBackendConfig() {
        LogUtils.i(TAG, "removeBackendConfig curUserId:" + curUserId + ", configUrl:" + configUrl);
        configUrl = "";
        curUserId = "";
        baseUrl = "";
        websocketUrl = "";
        blackListHost = "";
        teamsUrl = "";
        accountsUrl = "";
        websiteUrl = "";
        signInUrl = "";
    }

    public static boolean saveConfigUrl(String configUrl, String userId) {
        userId = TextUtils.isEmpty(userId) ? SpUtils.getUserId(ZApplication.getInstance()) : userId;
        if (!TextUtils.isEmpty(userId)) {
            SharedPreferences sp = getSharedPreferences();
            if (null != sp) {
                if (TextUtils.isEmpty(configUrl)) {
                    return sp.edit().putString(KEY_BASEURL + userId, BuildConfig.BACKEND_URL).commit();
                } else {
                    return sp.edit().putString(KEY_BASEURL + userId, configUrl).commit();
                }

            }
        }
        return false;
    }

    public static boolean clearConfigUrl(String userId) {
        if (!TextUtils.isEmpty(userId) && userId.equalsIgnoreCase(curUserId)) {
            removeBackendConfig();
            SharedPreferences sp = getSharedPreferences();
            if (null != sp) {
                return sp.edit().remove(KEY_BASEURL + userId).commit();
            }
        }
        return false;
    }

    public static boolean isSameConfigUrl(String configUrl) {
        if (TextUtils.isEmpty(configUrl)) {
            return false;
        }
        String userId = SpUtils.getUserId(ZApplication.getInstance());
        if (TextUtils.isEmpty(userId)) {
            return false;
        }
        if (!userId.equalsIgnoreCase(curUserId)) {
            return false;
        }
        String curConfigUrl = getConfigUrl();
        if (!isValidVerification(curConfigUrl)) {
            return false;
        }
        return configUrl.equalsIgnoreCase(curConfigUrl);
    }

    public static String getForceConfigUrl() {
        String userId = SpUtils.getUserId(ZApplication.getInstance());
        if (TextUtils.isEmpty(userId)) {
            return BuildConfig.BACKEND_URL;
        } else {
            return getStringPreference(KEY_BASEURL + userId, BuildConfig.BACKEND_URL);
        }
    }

    private static boolean isValidVerification(String url) {
        return !TextUtils.isEmpty(url) && !TextUtils.isEmpty(curUserId);
    }

    private static String getConfigUrl() {
        if (!isValidVerification(configUrl)) {
            String userId = SpUtils.getUserId(ZApplication.getInstance());
            if (TextUtils.isEmpty(userId)) {
                curUserId = "";
                return BuildConfig.BACKEND_URL;
            } else {
                curUserId = userId;
                configUrl = getStringPreference(KEY_BASEURL + userId, BuildConfig.BACKEND_URL);
            }
        }
        if (TextUtils.isEmpty(configUrl)) {
            curUserId = "";
            return BuildConfig.BACKEND_URL;
        }
        LogUtils.i(TAG, "getConfigUrl curUserId:" + curUserId + ", configUrl:" + configUrl);
        return configUrl;
    }

    public static String getEnvironment() {
        return getStringPreference(BackendController.ENVIRONMENT_PREF(), "");
    }

    public static String getBaseUrl() {
        if (!isValidVerification(baseUrl)) {
            baseUrl = getStringPreference(BackendController.BASE_URL_PREF(), getConfigUrl());
        }
        return baseUrl;
    }

    public static String getWebSocketUrl() {
        if (!isValidVerification(websocketUrl)) {
            websocketUrl = getStringPreference(BackendController.WEBSOCKET_URL_PREF(), getConfigUrl() + BuildConfig.WEBSOCKET_URL);
        }
        return websocketUrl;
    }

    public static String getBlackListHost() {
        if (!isValidVerification(blackListHost)) {
            blackListHost = getStringPreference(BackendController.BLACKLIST_HOST_PREF(), getConfigUrl() + BuildConfig.BLACKLIST_HOST);
        }
        return blackListHost;
    }

    public static String getTeamsUrl() {
        if (!isValidVerification(teamsUrl)) {
            teamsUrl = getStringPreference(BackendController.TEAMS_URL_PREF(), getConfigUrl());
        }
        return teamsUrl;
    }

    public static String getAccountsUrl() {
        if (!isValidVerification(accountsUrl)) {
            accountsUrl = getStringPreference(BackendController.ACCOUNTS_URL_PREF(), BuildConfig.ACCOUNTS_URL);
        }
        return accountsUrl;
    }

    public static String getWebsiteUrl() {
        if (!isValidVerification(websiteUrl)) {
            websiteUrl = getStringPreference(BackendController.WEBSITE_URL_PREF(), getConfigUrl());
        }
        return websiteUrl;
    }

    public static String getSignInUrl() {
        if (TextUtils.isEmpty(signInUrl)) {
            signInUrl = getStringPreference(BackendController.SIGNIN_URL_PREF(), BuildConfig.BACKEND_URL);
        }
        return signInUrl;
    }

    public static String getForgetPasswordUrl() {
        return BuildConfig.FORGET_PASSWORD_URL;
    }

    public static String getSecretBaseUrl() {
        return BuildConfig.SECRET_BASE_URL;
    }

    public static String getSecretServiceUrl() {
        return BuildConfig.SECRET_SERVICE_URL;
    }
}
