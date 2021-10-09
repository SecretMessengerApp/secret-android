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
package com.jsy.common.model;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class EmojiGifModel implements Serializable {

    public String msgType;

    public MsgData msgData;

    public static class MsgData implements Serializable {

        @SerializedName("zipId")
        public String id;

        @SerializedName("url")
        public String url;

        @SerializedName("zipName")
        public String folderName;

        @SerializedName("zipIcon")
        public String icon;

        @SerializedName("name")
        public String name;

        public MsgData(String id, String url, String folderName, String icon, String name) {
            this.id = id;
            this.url = url;
            this.folderName = folderName;
            this.icon = icon;
            this.name = name;
        }
    }

    public static EmojiGifModel parseJson(String messgeContentString) {
        return new Gson().fromJson(messgeContentString, EmojiGifModel.class);
    }
}
