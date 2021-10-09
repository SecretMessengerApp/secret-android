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

import okhttp3.ResponseBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Retrofit;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NormalServiceAPI {

    private final String TAG = NormalServiceAPI.class.getSimpleName();

    private static volatile NormalServiceAPI service;

    public static NormalServiceAPI getInstance() {
        if (service == null) {
            synchronized (NormalServiceAPI.class) {
                if (service == null) {
                    service = new NormalServiceAPI();
                }
            }
        }
        return service;
    }

    public <R extends Serializable> void get(String api, Map params, OnHttpListener<R> onHttpListener) {
        get(api, params, false, onHttpListener);
    }

    public <R extends Serializable> void get(String api, Map params, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.get(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void post(String api, Map<String, Object> params, OnHttpListener<R> onHttpListener) {
        post(api, params, false, onHttpListener);
    }

    public <R extends Serializable> void post(String api, Map<String, Object> params, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postBodyByMap(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void postForm(String api, Map params, OnHttpListener<R> onHttpListener) {
        postForm(api, params, false, onHttpListener);
    }

    public <R extends Serializable> void postForm(String api, Map params, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postFormByFieldMap(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void postFormNoEncoded(String api, Map params, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postFormByFieldMapNoEncoded(api, params);
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void delete(String api, Map params, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.delete(api, params);
        ServerAPI.getInstance().enqueue2(call, api, false, onHttpListener);
    }

    public <R extends Serializable> void postFormByFieldMap(String api, Map params, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postFormByFieldMap(api, params);
        ServerAPI.getInstance().enqueue2(call, api, false, onHttpListener);
    }

    public <R extends Serializable> void postBodyByRequestBody(String api, JSONObject json, OnHttpListener<R> onHttpListener) {
        String property = json == null ? "{}" : json.toString();
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postBodyByRequestBody(api, ServerAPI.jsonToMultipartBody(property));
        ServerAPI.getInstance().enqueue2(call, api, false, onHttpListener);
    }

    public <R extends Serializable> void postBodyByMap(String api, Map params, OnHttpListener<R> onHttpListener) {
        if (params == null) {
            params = new HashMap();
        }
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postBodyByMap(api, params);
        ServerAPI.getInstance().enqueue2(call, api, false, onHttpListener);
    }

    public <R extends Serializable> void post(String api, JSONObject json, OnHttpListener<R> onHttpListener) {
        post(api, json, false, onHttpListener);
    }

    public <R extends Serializable> void post(String api, JSONObject json, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        post(api, json.toString(), forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void post(String api, String property, OnHttpListener<R> onHttpListener) {
        post(api, property, false, onHttpListener);
    }

    public <R extends Serializable> void post(String api, String property, boolean forOrginJson, OnHttpListener<R> onHttpListener) {
        Retrofit mRetrofit = RetrofitUtil.getRetrofit();
        IHttp iHttp = mRetrofit.create(IHttp.class);
        Call<ResponseBody> call = iHttp.postWithProperty(api, ServerAPI.createHeaders(), ServerAPI.getByteBody(property));
        ServerAPI.getInstance().enqueue2(call, api, forOrginJson, onHttpListener);
    }

    public <R extends Serializable> void reqSingleMsgEdit(final String rConvId, final boolean isOpen, final OnHttpListener<R> listener) {
        Map p = new HashMap<>();
        p.put("rConvId", rConvId);
        p.put("isOpen", isOpen);
        post(ServerApiCode.API_SINGLEMSGEDIT, p, listener);
    }

    public <R extends Serializable> void reqSingleMsgEditReply(final String rConvId, final boolean isOpen, final String operateType, final OnHttpListener<R> listener) {
        Map p = new HashMap<>();
        p.put("rConvId", rConvId);
        p.put("isOpen", isOpen);
        p.put("operateType", operateType);
        post(ServerApiCode.API_SINGLEMSGEDIT, p, listener);
    }

    class ServerApiCode{

        public static final String API_SINGLEMSGEDIT = "/moments/file/auth";

        public static final String API_SINGLEMSGEDIT_REPLY = "/moments/file/auth";
    }
}
