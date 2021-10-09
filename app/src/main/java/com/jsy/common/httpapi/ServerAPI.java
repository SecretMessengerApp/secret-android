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

import android.text.TextUtils;

import com.alibaba.android.arouter.launcher.ARouter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jsy.common.model.HttpBaseModel;
import com.jsy.common.router.AppToMainOperateRouter;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.jsy.secret.sub.swipbackact.utils.RouterKeyUtil;
import com.waz.zclient.ZApplication;
import com.waz.zclient.utils.SpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.http.Body;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;
import retrofit2.http.Path;

public class ServerAPI {

    private static final String TAG = ServerAPI.class.getSimpleName();

    private static ServerAPI instance;

    public static ServerAPI getInstance() {
        if (instance != null) {
            return instance;
        }
        return instance = new ServerAPI();
    }

    private final static Map<String, Call> circleDomainRequestCalls = new HashMap<>();

    public static void cancelCall(String apiCallKey) {
        Call circleCall = circleDomainRequestCalls.get(apiCallKey);
        if (circleCall != null && !circleCall.isCanceled()) {
            circleCall.cancel();
            circleDomainRequestCalls.remove(apiCallKey);
        }
    }

    public static final Map<String, String> createHeaders() {
        String tokenType = SpUtils.getTokenType(ZApplication.getInstance());
        String token = SpUtils.getToken(ZApplication.getInstance());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("Authorization", tokenType + " " + token);
        return headers;
    }

    class ResponseResult<R extends Serializable> {
        R r;
        List<R> rs;
        int code;
        String message;
        String orgJson;
        int httpCode;
        boolean parseSuc = false;

        public ResponseResult() {
        }
    }

    public <R extends Serializable> void enqueue2(Call<ResponseBody> call, final String callKey, final boolean forOrginJson, final OnHttpListener<R> onHttpListener) {
        LogUtils.i(TAG, "enqueue start THREAD_NAME >>>" + Thread.currentThread().getName());
        circleDomainRequestCalls.put(callKey, call);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, final retrofit2.Response<ResponseBody> response) {
                LogUtils.i(TAG, "enqueue onResponse THREAD_NAME >>>" + Thread.currentThread().getName());
                circleDomainRequestCalls.remove(callKey);
                Observable.create(new ObservableOnSubscribe<ResponseResult>() {
                    @Override
                    public void subscribe(ObservableEmitter<ResponseResult> emitter) {
                        ResponseResult responseResult = new ResponseResult();
                        responseResult.httpCode = response.code();
                        LogUtils.i(TAG, "enqueue onResponse subscribe responseResult.httpCode:" + responseResult.httpCode + ",forOrginJson:" + forOrginJson +",THREAD_NAME >>>>>>" + Thread.currentThread().getName());
                        if (responseResult.httpCode == 200) {
                            if (forOrginJson) {
                                try {
                                    Class<R> clazz = onHttpListener.getClazz();
                                    responseResult.orgJson = response.body().string();
                                    LogUtils.i(TAG, "enqueue onResponse subscribe responseResult.httpCode:200, forOrginJson:true ,responseResult.orgJson:" + responseResult.orgJson);
                                    if (HttpBaseModel.isNullJson(responseResult.orgJson)) {
                                        responseResult.r = clazz.newInstance();
                                        responseResult.parseSuc = true;
                                    } else if (HttpBaseModel.isNullJsonArr(responseResult.orgJson)) {
                                        responseResult.rs = new ArrayList();
                                        responseResult.parseSuc = true;
                                    } else {
                                        if (HttpBaseModel.isArr(responseResult.orgJson)) {
                                            Gson gson = new GsonBuilder().create();
                                            responseResult.rs = HttpBaseModel.parseJsonArray(gson, responseResult.orgJson, clazz);
                                            responseResult.parseSuc = true;
                                        } else if (!TextUtils.isEmpty(responseResult.orgJson)) {
                                            Gson gson = new GsonBuilder().create();
                                            responseResult.r = gson.fromJson(responseResult.orgJson, clazz);
                                            responseResult.parseSuc = true;
                                        } else {
                                            responseResult.parseSuc = true;
                                        }
                                    }
                                } catch (IOException e) {
                                    responseResult.code = HttpObserver.ERR_LOCAL;
                                    responseResult.message = "IOException:" + e.getMessage();
                                } catch (Exception e) {
                                    responseResult.code = HttpObserver.ERR_LOCAL;
                                    responseResult.message = "Exception:" + e.getMessage();
                                }
                            } else {
                                try {
                                    responseResult.orgJson = response.body().string();
                                    LogUtils.i(TAG, "enqueue onResponse subscribe responseResult.httpCode:200, forOrginJson:false ,responseResult.orgJson:" + responseResult.orgJson);
                                    HttpBaseModel<R> baseModel = HttpBaseModel.fromJson(responseResult.orgJson);
                                    if (baseModel.code == HttpObserver.RET_OK) {
                                        Gson gson = new GsonBuilder().create();
                                        Class<R> clazz = onHttpListener.getClazz();
                                        if (HttpBaseModel.isArr(baseModel.result)) {
                                            responseResult.rs = HttpBaseModel.parseJsonArray(gson, baseModel.result, clazz);
                                            responseResult.parseSuc = true;
                                        } else if (!TextUtils.isEmpty(baseModel.result)) {
                                            responseResult.r = gson.fromJson(baseModel.result, clazz);
                                            responseResult.parseSuc = true;
                                        } else {
                                            responseResult.parseSuc = true;
                                        }
                                    } else {
                                        responseResult.message = baseModel.message;
                                        responseResult.code = baseModel.code;
                                        if (TextUtils.isEmpty(responseResult.message)) {
                                            responseResult.message = TextUtils.isEmpty(baseModel.result) ? "error from server is empty" : baseModel.result;
                                        }
                                    }
                                } catch (JSONException e) {
                                    responseResult.code = HttpObserver.ERR_LOCAL;
                                    responseResult.message = "JSONException:" + e.getMessage();
                                } catch (IOException e) {
                                    responseResult.code = HttpObserver.ERR_LOCAL;
                                    responseResult.message = "IOException:" + e.getMessage();
                                } catch (Exception e) {
                                    responseResult.code = HttpObserver.ERR_LOCAL;
                                    responseResult.message = "Exception:" + e.getMessage();
                                }
                            }
                        } else {
                            try {
                                int errorCode = response.code();
                                responseResult.code = errorCode;
                                String errorBody = null != response.errorBody() ? response.errorBody().string() : null;
                                responseResult.message = response.message();
                                responseResult.orgJson = !TextUtils.isEmpty(errorBody) ? errorBody : (null != response.body() ? response.body().string() : null);
                                if (!TextUtils.isEmpty(responseResult.orgJson)) {
                                    if (!forOrginJson) {
                                        HttpBaseModel<R> baseModel = HttpBaseModel.fromJson(responseResult.orgJson);
                                        responseResult.code = baseModel.code == 0 ? errorCode : baseModel.code;
                                        responseResult.message = TextUtils.isEmpty(baseModel.message) ? responseResult.orgJson : baseModel.message;
                                    } else {
                                        responseResult.message = responseResult.orgJson;
                                    }
                                }
                                if (responseResult.code == HttpObserver.HTTP_UNAUTHORIZED) {
                                    AppToMainOperateRouter operateRouter = (AppToMainOperateRouter) ARouter.getInstance().build(RouterKeyUtil.ROUTER_APPTOMAIN).navigation();
                                    if (null != operateRouter) {
                                        operateRouter.updateCurrentAccountToken();
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                responseResult.code = HttpObserver.HTTP_NOT_200;
                                responseResult.message = "Exception:" + e.getMessage();
                            }
                        }
                        emitter.onNext(responseResult);
                        emitter.onComplete();
                    }
                }).subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<ResponseResult>() {
                            Disposable d = null;

                            @Override
                            public void onSubscribe(Disposable d) {
                                this.d = d;
                            }

                            @Override
                            public void onNext(ResponseResult responseResult) {
                                LogUtils.i(TAG, "enqueue onResponse parse onNext THREAD_NAME >>>" + Thread.currentThread().getName());
                                if (responseResult.parseSuc) {
                                    if (responseResult.rs != null) {
                                        onHttpListener.onSuc(responseResult.rs, responseResult.orgJson);
                                    } else {
                                        onHttpListener.onSuc((R) responseResult.r, responseResult.orgJson);
                                    }
                                } else {
                                    onHttpListener.onFail(responseResult.code, responseResult.message);
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                LogUtils.i(TAG, "enqueue onResponse parse onError THREAD_NAME >>>" + Thread.currentThread().getName()+",Throwable:"+e.getLocalizedMessage());
                                onHttpListener.onFail(HttpObserver.DATA_ERROR, e.getMessage());
                            }

                            @Override
                            public void onComplete() {
                                if (d != null && !d.isDisposed()) {
                                    d.dispose();
                                }
                                LogUtils.i(TAG, "enqueue onResponse parse onComplete THREAD_NAME >>>" + Thread.currentThread().getName());
                                onHttpListener.onComplete();
                            }
                        });
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                LogUtils.i(TAG, "onFailure THREAD_NAME >>>" + Thread.currentThread().getName()+",Throwable:"+t.getLocalizedMessage());
                circleDomainRequestCalls.remove(callKey);
                if (onHttpListener != null) {
                    onHttpListener.onFail(HttpObserver.ERR_LOCAL, t.getMessage());
                    onHttpListener.onComplete();
                }
            }
        });
    }


    /**
     * @param property
     * @return
     */
    public static RequestBody jsonToMultipartBody(String property) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), property);
        return requestBody;
    }

    public static MultipartBody filesToMultipartBody(List<File> files, String keyUpload) {
        MultipartBody.Builder builder = new MultipartBody.Builder();
        for (File file : files) {
            RequestBody requestBody = getFileBody(file);//RequestBody.create(MediaType.parse("image/png"), file);
            builder.addFormDataPart(keyUpload, file.getName(), requestBody);
        }
        builder.setType(MultipartBody.FORM);
        MultipartBody multipartBody = builder.build();
        return multipartBody;
    }

    public static List<MultipartBody.Part> filesToMultipartBodyParts(List<File> files, String keyUpload) {
        List<MultipartBody.Part> parts = new ArrayList<>(files.size());
        for (File file : files) {
            MultipartBody.Part part = fileToMultipartBodyPart(file, keyUpload);
            parts.add(part);
        }
        return parts;
    }

    public static final MultipartBody.Part fileToMultipartBodyPart(File file, String keyUpload) {
        RequestBody requestBody = getFileBody(file);
        MultipartBody.Part multiPart = MultipartBody.Part.createFormData(keyUpload, file.getName(), requestBody);
        return multiPart;
    }


    public static RequestBody getTextBody(String str) {
        return RequestBody.create(MediaType.parse("text/plain; charset=utf-8"), str);
    }

    public static RequestBody getByteBody(byte[] bte) {
        return RequestBody.create(MediaType.parse("application/octet-stream"), bte);
    }

    public static RequestBody getByteBody(String json) {
        return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
    }

    public static RequestBody getFileBody(File file) {
        return RequestBody.create(MediaType.parse("application/octet-stream"), file);
    }


    //    ==================================== for rxJava example ====================================
    public Observable<HttpBaseModel<String>> getStringData(JSONObject jsonObj) {
        Gson gson = new GsonBuilder().create();
        String jsonStr = gson.toJson(jsonObj);
        return RetrofitUtil.getRetrofit().create(IHttpObservable.class).getStringData("api-address", createHeaders(), jsonStr);
    }


    public interface IHttpObservable {

        @FormUrlEncoded
        @POST("{api}")
        Observable<HttpBaseModel<String>> getStringData(@Path(value = "api", encoded = true) String api, @HeaderMap Map<String, String> headers, @Body String jsonStr);


    }


}

