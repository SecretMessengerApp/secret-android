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
package com.jsy.common.model.conversation;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.Serializable;

public class TabListMenuModel implements Serializable {

    private String text;
    private String convId;
    private String rassetId;
    private boolean isEditing = false;
    private boolean read = false;
    private int subType;
    private String uuid;
    private String joinUrl;

    public TabListMenuModel(String text, String convId, String rassetId,int subType,String uuid,String joinUrl,boolean read) {
        this.text = text;
        this.convId = convId;
        this.rassetId = rassetId;
        this.subType=subType;
        this.uuid=uuid;
        this.joinUrl=joinUrl;
        this.read=read;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getConvId() {
        return convId;
    }

    public void setConvId(String convId) {
        this.convId = convId;
    }

    public String getRassetId() {
        return rassetId;
    }

    public void setRassetId(String rassetId) {
        this.rassetId = rassetId;
    }


    public boolean isEditing() {
        return isEditing;
    }

    public void setEditing(boolean editing) {
        isEditing = editing;
    }


    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }


    public int getSubType() {
        return subType;
    }

    public void setSubType(int subType) {
        this.subType = subType;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getJoinUrl() {
        return joinUrl;
    }

    public void setJoinUrl(String joinUrl) {
        this.joinUrl = joinUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
