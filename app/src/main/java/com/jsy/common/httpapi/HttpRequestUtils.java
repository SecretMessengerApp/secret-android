/**
 * Secret
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
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
 * <p>
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
 * <p>
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
 * <p>
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
 * <p>
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
 * <p>
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
 * <p>
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

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.waz.zclient.utils.ServerConfig;
import com.waz.zclient.utils.SpUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HttpRequestUtils {


    public interface CommonHttpCallBack {
        void onSuc(String orgJson);

        void onFail(int errCode, String msg);
    }

    private static String getBaseUrl() {
        return ServerConfig.getBaseUrl();
    }

    public final static int buffSize = 4096;

    public static final int READ_TIME_OUT = 30 * 1000;
    public static final int CONNECT_TIME_OUT = 5 * 1000;

    /**
     * @param context
     * @param id
     * @param commonHttpCallBac
     */
    public static final void joinGroupConversation(final Context context, final String id, final CommonHttpCallBack commonHttpCallBac) {
        new Thread(new Runnable() {
            public void run() {

                final String tokenType = SpUtils.getString(context, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, "");
                final String token = SpUtils.getString(context, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, "");

                String urlPath = new StringBuilder().append(getBaseUrl())
                    .append("/conversations/").append(id).append("/join_invite").toString();

                ResponseResult responseResult = connectServer(urlPath, token, tokenType, null, null, "POST");
                if (responseResult.result != null) {
                    commonHttpCallBac.onSuc(responseResult.result);
                }
                if (responseResult.errMsg != null) {
                    commonHttpCallBac.onFail(responseResult.resCode, responseResult.errMsg);
                }

            }
        }).start();
    }

    public static final void changeConversationSettingStatus(final Context context, final String convId, final int type, final boolean isOpen, final CommonHttpCallBack commonHttpCallBac) {
        new Thread(new Runnable() {
            public void run() {

                final String tokenType = SpUtils.getString(context, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, "");
                final String token = SpUtils.getString(context, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, "");

                String urlPath = new StringBuilder().append(getBaseUrl())
                    .append("/conversations/").append(convId).append("/update").toString();

                JSONObject jsonObject = new JSONObject();
                try {
                    switch (type) {
                        case 1:
                            jsonObject.put("url_invite", isOpen);
                            break;
                        case 2:
                            jsonObject.put("confirm", isOpen);
                            break;
                        case 3:
                            jsonObject.put("addright", isOpen);
                            break;
                        default:
                            break;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                ResponseResult responseResult = connectServer(urlPath, token, tokenType, jsonObject.toString(), null, "PUT");
                if (responseResult.result != null) {
                    commonHttpCallBac.onSuc(responseResult.result);
                }
                if (responseResult.errMsg != null) {
                    commonHttpCallBac.onFail(responseResult.resCode, responseResult.errMsg);
                }

            }
        }).start();
    }

    @Deprecated
    public static void scanLogin(final String tokenType, final String token, final String qrCode, final CommonHttpCallBack commonHttpCallBack) {
        new Thread(new Runnable() {
            public void run() {
                String urlPath = new StringBuilder().append(getBaseUrl())
                    .append("/self/accept2d").toString();
                ResponseResult responseResult = connectServer(urlPath, token, tokenType, qrCode, null, "PUT");
                if (responseResult.result != null) {
                    commonHttpCallBack.onSuc(responseResult.result);
                }
                if (responseResult.errMsg != null) {
                    commonHttpCallBack.onFail(responseResult.resCode, responseResult.errMsg);
                }

            }
        }).start();
    }

    public static void transferGroup(final String tokenType, final String token, final String creator, final String rConvId, final CommonHttpCallBack commonHttpCallBack) {
        new Thread(new Runnable() {
            public void run() {
                String urlPath = new StringBuilder().append(getBaseUrl())
                    .append(String.format("/conversations/%s/creator", rConvId)).toString();
                ResponseResult responseResult = connectServer(urlPath, token, tokenType, creator, null, "PUT");
                if (responseResult.result != null) {
                    commonHttpCallBack.onSuc(responseResult.result);
                }
                if (responseResult.errMsg != null) {
                    commonHttpCallBack.onFail(responseResult.resCode, responseResult.errMsg);
                }

            }
        }).start();
    }

    private static ResponseResult connectServer(String urlPath, String token, String tokenType, String jsParams, String sign, String method) {
        ResponseResult responseResult = new ResponseResult();
        HttpURLConnection conn = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        OutputStream outwritestream = null;
        try {
            URL url = new URL(urlPath);
            conn = (HttpURLConnection) url.openConnection();
            conn.setUseCaches(false);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Charset", "UTF-8");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (!TextUtils.isEmpty(sign)) {
                conn.setRequestProperty("sign", sign);
            }
            if (!TextUtils.isEmpty(tokenType) && !TextUtils.isEmpty(token)) {
                conn.setRequestProperty("Authorization", tokenType + " " + token);
            }
            conn.setRequestMethod(method);
            if (!TextUtils.isEmpty(jsParams)) {
                conn.setDoOutput(true);
                conn.setDoInput(true);
                byte[] writebytes = jsParams.getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Length", String.valueOf(writebytes.length));
                outwritestream = conn.getOutputStream();
                outwritestream.write(writebytes);
                outwritestream.flush();
                outwritestream.close();
                outwritestream = null;
            }
            responseResult.resCode = conn.getResponseCode();
            if (responseResult.resCode == 200) {
                is = conn.getInputStream();
                baos = new ByteArrayOutputStream();
                int idx = -1;
                byte[] buff = new byte[buffSize];
                while ((idx = is.read(buff)) != -1) {
                    baos.write(buff, 0, idx);
                }
                is.close();
                is = null;
                responseResult.result = baos.toString();
                baos.close();
                baos = null;
            } else {
                InputStream errorStream = conn.getErrorStream();
                if(errorStream != null) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    int index = -1;
                    byte[] buff = new byte[buffSize];
                    while((index = errorStream.read(buff)) != -1) {
                        outputStream.write(buff, 0, index);
                    }
                    responseResult.errMsg = outputStream.toString("UTF-8");
                }else {
                    responseResult.errMsg = "error:" + conn.getResponseCode();
                }
            }
            conn.disconnect();
        } catch (MalformedURLException e) {
            responseResult.errMsg = "error:" + e.getMessage();
        } catch (IOException e) {
            responseResult.errMsg = "error:" + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            if (outwritestream != null) {
                try {
                    outwritestream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return responseResult;
    }


    public static void changePassword(final Context context, final String token, final String tokenType
            , final String oldPassword, final String newPassword, final CommonHttpCallBack commonHttpCallBac) {
        new Thread() {
            public void run() {

                String urlPath = getBaseUrl() + "/self/password";

                JsonObject contentJson = new JsonObject();
                contentJson.addProperty("old_password", oldPassword);
                contentJson.addProperty("new_password", newPassword);
                ResponseResult responseResult = connectServer(urlPath, token, tokenType, contentJson.toString(), null, "PUT");
                if (responseResult.result != null) {
                    commonHttpCallBac.onSuc(responseResult.result);
                }
                if (responseResult.errMsg != null) {
                    commonHttpCallBac.onFail(responseResult.resCode, responseResult.errMsg);
                }
            }
        }.start();
    }

    public static class ResponseResult {

        public static final int DEF_RES_CODE = -1;

        public int resCode = DEF_RES_CODE;
        public String errMsg = null;
        public String result = null;
    }
}
