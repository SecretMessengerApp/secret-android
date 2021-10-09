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
package com.jsy.common.download;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.waz.zclient.utils.ServerConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitDownloadHolder {
    private static Retrofit mRetrofit;
    private static String currentBaseUrl;
    private OkHttpClient client;

    private static String getBaseUrl() {
        return ServerConfig.getBaseUrl();
    }

    private RetrofitDownloadHolder(String baseUrl) {

        Gson gson = new GsonBuilder()
            .setLenient()
            .create();
        mRetrofit = new Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(getClient())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build();
        currentBaseUrl = baseUrl;
    }

    private OkHttpClient getClient() {
        if (client == null) {
            OkHttpClient.Builder builder = new OkHttpClient.Builder();
//            if (BuildConfig.DEBUG) {
//                builder.addNetworkInterceptor(new LoggingInterceptor());
//            }
            client = builder
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        }

        return client;
    }

    public static Retrofit getRetrofitInstance() {
        if (mRetrofit == null || TextUtils.isEmpty(currentBaseUrl) || !currentBaseUrl.equals(getBaseUrl())) {
            synchronized (RetrofitDownloadHolder.class) {
                new RetrofitDownloadHolder(getBaseUrl());
            }
        }
        return mRetrofit;
    }
}
