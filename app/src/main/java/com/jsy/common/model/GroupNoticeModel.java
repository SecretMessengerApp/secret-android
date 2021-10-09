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

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.jsy.common.utils.MessageUtils;

import java.io.Serializable;

public class GroupNoticeModel implements Serializable {

    @SerializedName(MessageUtils.KEY_TEXTJSON_MSGDATA)
    public MsgData msgData;

    @SerializedName(MessageUtils.KEY_TEXTJSON_MSGTYPE)
    public String msgType;

    public class MsgData implements Serializable {

        public String text;

        public String url;

        public String appid;

    }

    public static GroupNoticeModel parseJson(String js) {
        try {
            return new GsonBuilder().create().fromJson(js, GroupNoticeModel.class);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
