/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.model;
import com.google.gson.annotations.SerializedName;
import com.jsy.common.utils.MessageUtils;

import java.io.Serializable;
import java.util.List;

public class ConversationInviteMemberConfirmModel implements Serializable {

    @SerializedName(MessageUtils.KEY_TEXTJSON_MSGTYPE)
    public String msgType;

    @SerializedName(MessageUtils.KEY_TEXTJSON_MSGDATA)
    public MsgDataModel msgData;


    public static class MsgDataModel implements Serializable {

        @SerializedName(value = "creator")
        public String creator;

        @SerializedName(value = "reason")
        public String reason;

        @SerializedName(value = "inviter")
        public String inviter;

        @SerializedName(value = "code")
        public String code;

        @SerializedName(value = "name")
        public String name;

        @SerializedName(value = "nums")
        public int nums;

        @SerializedName(value = "type")
        public int type;

        @SerializedName(value = "users")
        public List<UserDataModel> users;


    }


    public static class UserDataModel implements Serializable {

        @SerializedName(value = "handle")
        public String handle;

        @SerializedName(value = "asset")
        public String asset;

        @SerializedName(value = "name")
        public String name;

        @SerializedName(value = "id")
        public String id;

    }



}
