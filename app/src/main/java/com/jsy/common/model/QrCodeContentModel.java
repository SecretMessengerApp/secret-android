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
package com.jsy.common.model;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Patterns;

import com.google.gson.JsonArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

public class QrCodeContentModel implements Serializable {

    @Deprecated
    public static final String SECRET_LOGIN_PREFIX = "secret_login:";
    @Deprecated
    public static final String TYPE_FRIEND = "2";
    @Deprecated
    public static final String GROUP_URL = "3";

    public static final String QRCODE_TYPE_USER = "u.isecret.im";
    public static final String QRCODE_TYPE_GROUP = "g.isecret.im";
    public static final String QRCODE_TYPE_LOGIN = "l.isecret.im";

    private String loginKey;

    private String type;
    private String userId;
    private String userName;
    private String handle;
    private String picture;
    private String url;
    private String scheme;
    private String[] fields;
    private String[] uriPaths;
    private String[] uriQuerys;
    private boolean isLinkUrl;
    private String qrContent;


    public QrCodeContentModel(String type, String userId, String userName, String handle, String picture) {
        this.type = type;
        this.userId = userId;
        this.userName = userName;
        this.handle = handle;
        this.picture = picture;
    }

    public QrCodeContentModel(String qrContent, boolean isLinkUrl) {
        this.qrContent = qrContent;
        this.isLinkUrl = isLinkUrl;
    }

    public QrCodeContentModel(String type, String url) {
        this.type = type;
        this.url = url;
    }

    public QrCodeContentModel(String loginKey) {
        this.type = SECRET_LOGIN_PREFIX;
        this.loginKey = loginKey;
    }

    public String getLoginKey() {
        return loginKey;
    }

    public void setLoginKey(String loginKey) {
        this.loginKey = loginKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String[] getFields() {
        return fields;
    }

    public void setFields(String[] fields) {
        this.fields = fields;
    }

    public String[] getUriPaths() {
        return uriPaths;
    }

    public void setUriPaths(String[] uriPaths) {
        this.uriPaths = uriPaths;
    }

    public String[] getUriQuerys() {
        return uriQuerys;
    }

    public void setUriQuerys(String[] uriQuerys) {
        this.uriQuerys = uriQuerys;
    }

    public boolean isLinkUrl() {
        return isLinkUrl;
    }

    public void setLinkUrl(boolean linkUrl) {
        isLinkUrl = linkUrl;
    }

    public String getQrContent() {
        return qrContent;
    }

    public void setQrContent(String qrContent) {
        this.qrContent = qrContent;
    }

    @Deprecated
    private static final QrCodeContentModel parseJson(String qrCodeContent) {
        if (qrCodeContent.startsWith(SECRET_LOGIN_PREFIX)) {
            return new QrCodeContentModel(qrCodeContent.substring(SECRET_LOGIN_PREFIX.length()));
        } else {
            try {
                JSONObject json = new JSONObject(qrCodeContent);
                String type = json.optString("type");

                if (type.equalsIgnoreCase(GROUP_URL)) {
                    String url = json.optString("url");
                    return new QrCodeContentModel(type, url);
                } else {
                    String userId = json.optString("userId");
                    String userName = json.optString("userName");
                    String handle = json.getString("handle");
                    String picture = json.getString("picture");
                    return new QrCodeContentModel(type, userId, userName, handle, picture);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Deprecated
    private static final boolean isValidQrContent(String qrCodeContent) {
        if (TextUtils.isEmpty(qrCodeContent)) {
            return false;
        }
        if (qrCodeContent.length() > SECRET_LOGIN_PREFIX.length() && qrCodeContent.startsWith(SECRET_LOGIN_PREFIX)) {
            return true;
        } else if (qrCodeContent.startsWith("{") && qrCodeContent.endsWith("}")) {
            try {
                JSONObject json = new JSONObject(qrCodeContent);
                if (json.has("type")) {
                    return true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Deprecated
    private static final QrCodeContentModel parseQrCodeJson(String qrCodeContent) {
        if (isValidQrContent(qrCodeContent)) {
            return parseJson(qrCodeContent);
        } else {
            return null;
        }
    }

    @Deprecated
    public JSONObject createJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("userId", userId);
            json.put("userName", userName);
            json.put("handle", handle);
            json.put("picture", picture);
            return json;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Deprecated
    public JSONObject createGroupUrlJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("url", url);
            return json;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static final String QRCODE_URI_SCHEME = "https";
    public static final String QRCODE_LINK_GROUP_QUERYKEY = "url";
    private static final String QRCODE_LINK_SYMBOL_1 = "?";
    private static final String QRCODE_LINK_SYMBOL_2 = ":";
    private static final String QRCODE_LINK_SYMBOL_3 = "\\|";

    public static final QrCodeContentModel parseQrCodeForContent(String qrCodeContent) {
        QrCodeContentModel qrCodeContentModel = parseQrCodeJson(qrCodeContent);
        if (qrCodeContentModel != null) {
            return qrCodeContentModel;
        } else {
            return QrCodeContentModel.parseQrCodeForLink(qrCodeContent);
        }
    }

    private static final QrCodeContentModel parseQrCodeForLink(String qrCodeContent) {
        if (TextUtils.isEmpty(qrCodeContent)) {
            return null;
        }
        Uri qrUri = Uri.parse(qrCodeContent);
        QrCodeContentModel contentModel = new QrCodeContentModel(qrCodeContent, Patterns.WEB_URL.matcher(qrCodeContent).find());
        if (null == qrUri) {
            String parseStr = qrCodeContent.contains(QRCODE_LINK_SYMBOL_1) ? qrCodeContent.substring(qrCodeContent.indexOf(QRCODE_LINK_SYMBOL_1) + 1) : qrCodeContent;
            if (TextUtils.isEmpty(parseStr)) {
                return contentModel;
            }
            String type;
            String content;
            if (parseStr.contains(QRCODE_LINK_SYMBOL_2)) {
                int typeSplit = parseStr.indexOf(QRCODE_LINK_SYMBOL_2);
                type = parseStr.substring(0, typeSplit).trim();
                content = parseStr.substring(typeSplit + 1).trim();
            } else {
                type = "";
                content = parseStr;
            }
            String[] fields = TextUtils.isEmpty(content) ? null : content.split(QRCODE_LINK_SYMBOL_3);
            contentModel.setType(type);
            contentModel.setFields(fields);
        } else {
            String type = qrUri.getHost();
            contentModel.setType(type);

            List<String> paths = qrUri.getPathSegments();
            contentModel.setUriPaths(null != paths ? paths.toArray(new String[paths.size()]) : null);

            Set<String> uriQueryKeys = qrUri.getQueryParameterNames();
            int querySize = null == uriQueryKeys ? 0 : uriQueryKeys.size();
            if (querySize > 0) {
                for (String queryKey : uriQueryKeys) {
                    if (TextUtils.isEmpty(queryKey)) {
                        continue;
                    }
                    if (QRCODE_TYPE_GROUP.equalsIgnoreCase(type)
                        && QRCODE_LINK_GROUP_QUERYKEY.toLowerCase().equalsIgnoreCase(queryKey.toLowerCase())) {
                        List<String> queryValues = qrUri.getQueryParameters(queryKey);
                        contentModel.setUriQuerys(null != queryValues ? queryValues.toArray(new String[queryValues.size()]) : null);
                        break;
                    }
                }
            }
        }
        return contentModel;
    }

    public static boolean isSupportQRType(String type) {
        if (TextUtils.isEmpty(type)) {
            return false;
        }
        return QRCODE_TYPE_USER.equalsIgnoreCase(type)
                || QRCODE_TYPE_GROUP.equalsIgnoreCase(type)
                || QRCODE_TYPE_LOGIN.equalsIgnoreCase(type);
    }

    public String arrayToJson(String[] arrays) {
        try {
            int length = null == arrays ? 0 : arrays.length;
            JsonArray jsonArray = new JsonArray();
            for (int i = 0; i < length; i++) {
                jsonArray.add(arrays[i].trim());
            }
            return jsonArray.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

}
