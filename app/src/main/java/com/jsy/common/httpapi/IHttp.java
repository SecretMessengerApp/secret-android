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

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.FieldMap;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.HeaderMap;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;
import retrofit2.http.Url;

public interface IHttp {

    @POST("{api}")
    Call<ResponseBody> postBodyByRequestBody(@Path(value = "api", encoded = true) String api, @Body RequestBody body);

    @GET("{api}")
    Call<ResponseBody> get(@Path(value = "api", encoded = true) String api, @QueryMap Map<String, Object> params);

    @HTTP(method = "DELETE", path = "{api}", hasBody = true)
    Call<ResponseBody> delete(@Path(value = "api", encoded = true) String api, @Body Map<String, Object> params);

    @FormUrlEncoded
    @POST("{api}")
    Call<ResponseBody> postFormByFieldMap(@Path(value = "api", encoded = true) String api, @FieldMap Map<String, Object> params);

    @POST("{api}")
    Call<ResponseBody> postFormByFieldMapNoEncoded(@Path(value = "api", encoded = true) String api, @FieldMap Map<String, Object> params);

    @POST("{api}")
    Call<ResponseBody> postBodyByMap(@Path(value = "api", encoded = true) String api, @Body Map<String, Object> params);

    @Multipart
    @POST("{api}")
    Call<ResponseBody> uploadFiles(@Path(value = "api", encoded = true) String api, @Part() List<MultipartBody.Part> parts);

    @POST("{api}")
    Call<ResponseBody> uploadFiles(@Path(value = "api", encoded = true) String api, @Body MultipartBody multipartBody);

    @Multipart
    @POST("{api}")
    Call<ResponseBody> uploadFile(@Path(value = "api", encoded = true) String api, @Part() MultipartBody.Part body);

    @POST("{api}")
    Call<ResponseBody> uploadFile(@Path(value = "api", encoded = true) String api, @Body() RequestBody body);

    @Multipart
    @POST("{api}")
    Call<ResponseBody> uploadFileWithRequestBody(@Path(value = "api", encoded = true) String api, @PartMap Map<String, RequestBody> param, @Part() MultipartBody.Part body);

    @POST
    Call<ResponseBody> postWithProperty(@Url String url, @HeaderMap Map<String, String> headers, @Body RequestBody paramBody);

    @PUT
    Call<ResponseBody> putWithProperty(@Url String url, @HeaderMap Map<String, String> headers, @Body RequestBody paramBody);

    @PUT
    Call<ResponseBody> putWithProperty(@Url String url, @Body RequestBody paramBody);

    @PUT("{api}")
    Call<ResponseBody> putWithRaw(@Path(value = "api", encoded = true) String api, @Body RequestBody paramBody);

    @HTTP(method = "DELETE", hasBody = true)
    Call<ResponseBody> deleteWithRaw(@Url String url, @Body RequestBody paramBody);

    @Headers("Content-Type:application/x-www-form-urlencoded")
    @DELETE("{api}")
    Call<ResponseBody> deleteWithUrl(@Path(value = "api", encoded = true) String api, @QueryMap Map<String, Object> params);

}
