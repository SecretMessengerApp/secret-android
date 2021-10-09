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

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class SendTextJsonMessageEntity implements Serializable, Parcelable {

    public String from;

    public String msgType;
    public String msgJson;

    public int actionType;
    public String messageId;

    public boolean waitingForConfirmation;

    public static final Creator<SendTextJsonMessageEntity> CREATOR = new Creator<SendTextJsonMessageEntity>() {
        @Override
        public SendTextJsonMessageEntity createFromParcel(Parcel source) {
            return new SendTextJsonMessageEntity(source);
        }

        @Override
        public SendTextJsonMessageEntity[] newArray(int size) {
            return new SendTextJsonMessageEntity[size];
        }
    };

    public SendTextJsonMessageEntity() {
    }

    public SendTextJsonMessageEntity(String from, String msgType, String msgJson) {
        this.from = from;
        this.msgType = msgType;
        this.msgJson = msgJson;
    }

    public SendTextJsonMessageEntity(String from, int actionType, String messageId) {
        this.from = from;
        this.actionType = actionType;
        this.messageId = messageId;
    }

    public SendTextJsonMessageEntity(Parcel source) {
        from = source.readString();
        msgType = source.readString();
        msgJson = source.readString();
        actionType = source.readInt();
        messageId = source.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(from);
        dest.writeString(msgType);
        dest.writeString(msgJson);
        dest.writeInt(actionType);
        dest.writeString(messageId);
    }

}
