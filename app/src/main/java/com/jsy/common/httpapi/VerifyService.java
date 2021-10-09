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
 * <p>
 * Wire
 * <<<<<<< HEAD
 * Copyright (C) 2019 Wire Swiss GmbH
 * =======
 * Copyright (C) 2018 Wire Swiss GmbH
 * >>>>>>> 78863bc0b599e8601d4a84bef67c32b66df3af58
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
 * <<<<<<< HEAD
 * Copyright (C) 2019 Wire Swiss GmbH
 * =======
 * Copyright (C) 2018 Wire Swiss GmbH
 * >>>>>>> 78863bc0b599e8601d4a84bef67c32b66df3af58
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
 * <<<<<<< HEAD
 * Copyright (C) 2019 Wire Swiss GmbH
 * =======
 * Copyright (C) 2018 Wire Swiss GmbH
 * >>>>>>> 78863bc0b599e8601d4a84bef67c32b66df3af58
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
 <<<<<<< HEAD
 * Copyright (C) 2019 Wire Swiss GmbH
 =======
 * Copyright (C) 2018 Wire Swiss GmbH
 >>>>>>> 78863bc0b599e8601d4a84bef67c32b66df3af58
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

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class VerifyService {

    private static volatile VerifyService service;

    private VerifyService() {
    }

    public static VerifyService getInstance() {
        if (service == null) {
            synchronized (VerifyService.class) {
                if (service == null) {
                    service = new VerifyService();
                }
            }
        }
        return service;
    }

    public void create(OnHttpListener listener) {

        NormalServiceAPI.getInstance().post(VerifyAPICode.CREATE, "", listener);

    }


    public void verify(int code, String pubKey, OnHttpListener listener) {

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("code", code);
            jsonObject.put("pubkey", pubKey);
            NormalServiceAPI.getInstance().post(VerifyAPICode.VERFIY, jsonObject, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public void close(String email_code,OnHttpListener listener) {

        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("email_code", email_code);
            NormalServiceAPI.getInstance().post(VerifyAPICode.CLOSE, jsonObject, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void send(String email,OnHttpListener listener) {
        if(!TextUtils.isEmpty(email)){
            NormalServiceAPI.getInstance().post(String.format("%s?email=%s",VerifyAPICode.SEND,email), "", listener);
        }
        else{
            NormalServiceAPI.getInstance().post(VerifyAPICode.SEND, "", listener);
        }
    }

    public void ecode(String email, String code, OnHttpListener listener) {
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("email", email);
            jsonObject.put("code", code);
            NormalServiceAPI.getInstance().post(VerifyAPICode.ECODE, jsonObject, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public void open(String email_code,String passcode, OnHttpListener listener) {
        JSONObject jsonObject = new JSONObject();

        try {
            jsonObject.put("email_code", email_code);
            jsonObject.put("passcode", Integer.valueOf(passcode));
            NormalServiceAPI.getInstance().post(VerifyAPICode.OPEN, jsonObject, listener);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public boolean checkCode(String code) {
        String regEx = "^\\d{6}$";
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(code);
        return matcher.matches();
    }


}
