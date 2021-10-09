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

import android.os.Parcel;
import android.os.Parcelable;

public class UserIdName implements Parcelable {
    private String userId;
    private String userName;


    public String getUserName() {
        return userName;
    }

    public String getUserId() {
        return userId;
    }

    public UserIdName() {
    }

    public UserIdName(String userId, String userName) {
        this.userId = userId;
        this.userName = userName;
    }

    public static final Creator<UserIdName> CREATOR = new Creator<UserIdName>() {
        @Override
        public UserIdName createFromParcel(Parcel source) {
            return new UserIdName(source);
        }

        @Override
        public UserIdName[] newArray(int size) {
            return new UserIdName[size];
        }
    };

    public UserIdName(Parcel source) {
        userId = source.readString();
        userName = source.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(userId);
        dest.writeString(userName);
    }
}
