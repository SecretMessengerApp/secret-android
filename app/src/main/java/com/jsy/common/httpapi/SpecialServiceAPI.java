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
package com.jsy.common.httpapi;

import com.jsy.common.model.HttpResponseBaseModel;
import com.jsy.common.model.UpdateGroupSettingResponseModel;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;


public class SpecialServiceAPI {

    private final String TAG = SpecialServiceAPI.class.getSimpleName();

    private static volatile SpecialServiceAPI service;

    public static SpecialServiceAPI getInstance() {
        if (service == null) {
            service = new SpecialServiceAPI();
        }
        return service;
    }

    public <R extends Serializable> void post(final String api, String property, final OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postWithProperty(api, ServerAPI.createHeaders(), ServerAPI.getByteBody(property));
        ServerAPI.getInstance().enqueue2(call, api, true, onHttpListener);
    }

    public <R extends Serializable> void post(final String api, JSONObject jsonObj, final OnHttpListener<R> onHttpListener) {
        post(api, jsonObj.toString(), onHttpListener);
    }

    public <R extends Serializable> void get(final String api, Map params, boolean forOrginJson, final OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        if (params == null) {
            params = new HashMap();
        }
        Call<ResponseBody> call = iHttp.get(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void get(final String api, Map params, final OnHttpListener<R> onHttpListener) {
        get(api, params, true, onHttpListener);
    }

    public <R extends Serializable> void get(final String api, final OnHttpListener<R> onHttpListener) {
        get(api, null, onHttpListener);
    }

    public <R extends Serializable> void put(String api, String json, final OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.putWithProperty(api, ServerAPI.createHeaders(), ServerAPI.getByteBody(json));
        ServerAPI.getInstance().enqueue2(call, api, true, onHttpListener);
    }

    public <R extends Serializable> void put(String api, RequestBody requestBody, final OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.putWithProperty(api, requestBody);
        ServerAPI.getInstance().enqueue2(call, api, true, onHttpListener);
    }

    public void updateGroupInfo(String rConvId, String json, final OnHttpListener<HttpResponseBaseModel> onHttpListener) {
        String api = new StringBuilder().append(String.format("conversations/%s/update", rConvId)).toString();
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.putWithProperty(api, ServerAPI.createHeaders(), ServerAPI.getByteBody(json));
        ServerAPI.getInstance().enqueue2(call, api, true, onHttpListener);
    }

    public void updateGroupSetting(String rConvId, String json, final OnHttpListener<UpdateGroupSettingResponseModel> onHttpListener) {
        String api = new StringBuilder().append(String.format("conversations/%s/update", rConvId)).toString();
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.putWithProperty(api, ServerAPI.createHeaders(), ServerAPI.getByteBody(json));
        ServerAPI.getInstance().enqueue2(call, api, true, onHttpListener);
    }

    public <R extends Serializable> void signInGet(final String api, Map params, boolean forOrginJson, final OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getSignInRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        if (params == null) {
            params = new HashMap();
        }
        Call<ResponseBody> call = iHttp.get(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void signInGet(final String api, Map params, final OnHttpListener<R> onHttpListener) {
        signInGet(api, params, true, onHttpListener);
    }

    public <R extends Serializable> void signInGet(final String api, boolean forOrginJson, final OnHttpListener<R> onHttpListener) {
        signInGet(api, null, forOrginJson, onHttpListener);
    }

}
