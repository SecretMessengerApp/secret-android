/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.httpapi;

import android.content.Context;
import android.text.TextUtils;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.ZApplication;
import com.waz.zclient.utils.SpUtils;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class SecretHttpInterceptor implements Interceptor {
    private Context mContext;

    public SecretHttpInterceptor(Context context) {
        super();
        this.mContext = context;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
//        CurrentUserAccountDataUtils.refreshCookie(ZApplication.getInstance());
        String tokenType = SpUtils.getTokenType(ZApplication.getInstance());
        String token = SpUtils.getToken(ZApplication.getInstance());
        String cookie = SpUtils.getCookie(ZApplication.getInstance());
        Request orgRequest = chain.request();
        Headers headers = orgRequest.headers();
        int headerSize = null == headers ? 0 : headers.size();
        Request.Builder builder = orgRequest.newBuilder();
        if (headerSize > 0) {
            builder.headers(headers);
        }
        builder.header("User-Agent", "Secret (Android)");
        if (TextUtils.isEmpty(headers.get("Content-type"))) {
            builder.header("Content-type", "application/json; charset=UTF-8");
        }
        builder.header("X-Requested-With", "XMLHttpRequest");
        if (!TextUtils.isEmpty(tokenType) && !TextUtils.isEmpty(token)) {
            builder.header("Authorization", tokenType + " " + token);
        } else {
            LogUtils.w("SecretHttpInterceptor", "OkHttp intercept token empty tokenType:" + tokenType + ",token:" + token);
        }
        if (!TextUtils.isEmpty(cookie)) {
            builder.header("Cookie", cookie);
        } else {
            LogUtils.w("SecretHttpInterceptor", "OkHttp intercept cookie empty");
        }
        builder.method(orgRequest.method(), orgRequest.body());
        Request newRequest = builder.build();
        return chain.proceed(newRequest);
    }
}
