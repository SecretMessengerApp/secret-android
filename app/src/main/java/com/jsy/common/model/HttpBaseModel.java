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
package com.jsy.common.model;

import android.text.TextUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.annotations.Expose;
import com.jsy.common.httpapi.HttpObserver;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HttpBaseModel<R extends Serializable> implements Serializable {

    @Expose(serialize = false, deserialize = false)
    public static final String CODE = "code";
    @Expose(serialize = false, deserialize = false)
    public static final String DATA = "data";
    @Expose(serialize = false, deserialize = false)
    public static final String MSG = "msg";

    public int code;

    public String message;

    public String result;

    @Expose(serialize = false, deserialize = false)
    public List<R> list;
    @Expose(serialize = false, deserialize = false)
    public R element;

    public static boolean isNullJson(String result) {
        return !TextUtils.isEmpty(result) && "{}".equals(result);
    }

    public static boolean isNullJsonArr(String result) {
        return !TextUtils.isEmpty(result) && "[]".equals(result);
    }

    public static boolean isArr(String result) {
        return !TextUtils.isEmpty(result) && result.startsWith("[");
    }

    public boolean isObj() {
        return !TextUtils.isEmpty(result) && result.startsWith("{");
    }

    public static HttpBaseModel fromJson(String orgJson) throws JSONException {
        HttpBaseModel baseModel = new HttpBaseModel();
        JSONObject jsonObj = null;
        try {
            jsonObj = !TextUtils.isEmpty(orgJson) ? new JSONObject(orgJson) : null;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (null != jsonObj) {
            baseModel.result = jsonObj.optString(DATA);
            baseModel.code = jsonObj.optInt(CODE);
            baseModel.message = jsonObj.optString(MSG);
        }
        return baseModel;
    }


    public static <R extends Serializable> HttpBaseModel fromRxJava(String orgJson, Class clazz) throws JSONException {
        HttpBaseModel<R> baseModel = HttpBaseModel.fromJson(orgJson);
        if (baseModel.code == HttpObserver.RET_OK) {
            Gson gson = new Gson();
            if (HttpBaseModel.isArr(baseModel.result)) {
                ArrayList<R> listR = HttpBaseModel.parseJsonArray(gson, orgJson, clazz);
                baseModel.list = listR;
            } else if (!TextUtils.isEmpty(baseModel.result)) {
                R r = (R) gson.fromJson(orgJson, clazz);
                baseModel.element = r;
            } else {

            }
        } else {

        }
        return baseModel;
    }

    public static <S> List<S> jsonToList(Gson gson, String json, Class<S> t) {
        List<S> list = new ArrayList<>();
        JsonParser parser = new JsonParser();
        JsonArray jsonarray = parser.parse(json).getAsJsonArray();
        for (JsonElement element : jsonarray) {
            list.add(gson.fromJson(element, t));
        }
        return list;
    }


    public static <R> ArrayList<R> parseJsonArray(Gson gson, String jsonStr, Class<R> myClass) {
        Type type = new ListParameterizedType(myClass);
        return gson.fromJson(jsonStr, type);
    }

    private static class ListParameterizedType implements ParameterizedType {
        private Type type;

        private ListParameterizedType(Type type) {
            this.type = type;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{type};
        }

        @Override
        public Type getRawType() {
            return ArrayList.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    @Override
    public String toString() {
        return "HttpBaseModel{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", result='" + result + '\'' +
                ", element='" + element + '\'' +
                ", list='" + list + '\'' +
                '}';
    }
}
