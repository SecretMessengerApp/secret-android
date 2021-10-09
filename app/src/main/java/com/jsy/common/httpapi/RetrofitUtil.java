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
package com.jsy.common.httpapi;

import android.text.TextUtils;

import com.waz.zclient.BuildConfig;
import com.waz.zclient.ZApplication;
import com.waz.zclient.utils.ServerConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;


public class RetrofitUtil {

    private static final int CONNECT_TIMEOUNT = 5 * 1000;
    private static final int READ_TIMEOUT = 60 * 1000;
    private static final int WRITE_TIMEOUT = 60 * 1000;

    private static Converter.Factory gsonConverterFactory = GsonConverterFactory.create();
    private static CallAdapter.Factory rxJava2CallAdapterFactory = RxJava2CallAdapterFactory.create();
    private static String currnetBaseUrl;
    private static OkHttpClient okHttpClient;
    private static Retrofit retrofit;

    private static String getBaseUrl() {
        return ServerConfig.getBaseUrl();
    }

    public static Retrofit getRetrofit() {
        initRetrofit();
        return retrofit;
    }

    public static OkHttpClient initClient() {
        if (okHttpClient == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUNT, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
                .addInterceptor(new SecretHttpInterceptor(ZApplication.getInstance()));
                //.addInterceptor(new CookiesInterceptor(ZApplication.getInstance()))
            if (BuildConfig.DEBUG) {
                builder.addInterceptor(new LogInterceptor());
            }
            okHttpClient = builder.build();
        }
        return okHttpClient;
    }

    private static Retrofit initRetrofit() {
        if (retrofit == null || TextUtils.isEmpty(currnetBaseUrl) || !currnetBaseUrl.equals(getBaseUrl())) {
            retrofit = new Retrofit.Builder()
                .client(initClient())
                .baseUrl(getBaseUrl())
                .addConverterFactory(gsonConverterFactory)
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .build();
            currnetBaseUrl = getBaseUrl();
        }
        return retrofit;
    }

    private static Retrofit signInRetrofit;

    public static Retrofit getSignInRetrofit() {
        if (null == signInRetrofit) {
            signInRetrofit = new Retrofit.Builder()
                .client(initClient())
                .baseUrl(ServerConfig.getSignInUrl())
                .addConverterFactory(gsonConverterFactory)
                .addCallAdapterFactory(rxJava2CallAdapterFactory)
                .build();
        }
        return signInRetrofit;
    }
}
