/**
 * Secret
 * Copyright (C) 2021 Secret
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

import com.jsy.common.model.HttpResponseBaseModel;

import java.util.Collections;
import java.util.Map;

public class ConversationApiService {

    private static volatile ConversationApiService service;

    public static ConversationApiService getInstance() {
        if (service == null) {
            synchronized (ConversationApiService.class) {
                if (service == null) {
                    service = new ConversationApiService();
                }
            }
        }
        return service;
    }

    public void checkGroupInviteUrl(String inviteCode, OnHttpListener<HttpResponseBaseModel> httpListener) {
        String requestUrl = String.format(ImApiConst.CONVERSATION_INVITE_URL_CHECK, inviteCode);
        SpecialServiceAPI.getInstance().get(requestUrl, Collections.emptyMap(), httpListener);
    }

    public void joinGroup(String inviteCode, OnHttpListener<HttpResponseBaseModel> httpListener) {
        String requestUrl = String.format(ImApiConst.CONVERSATION_JOIN_INVITE, inviteCode);
        SpecialServiceAPI.getInstance().post(requestUrl, "", httpListener);
    }

    public void translate(Map param, OnHttpListener<String> listener) {
        NormalServiceAPI.getInstance().post(ImApiConst.CONVERSATION_TRANSLATE, param, listener);
    }
}
