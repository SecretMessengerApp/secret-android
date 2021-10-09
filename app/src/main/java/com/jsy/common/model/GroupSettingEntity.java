/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class GroupSettingEntity implements Serializable, Parcelable {
    public GroupSettingEntity() {

    }

    public boolean openLinkJoin;

    public GroupSettingEntity(boolean openLinkJoin) {
        this.openLinkJoin = openLinkJoin;
    }

    public static final Creator<GroupSettingEntity> CREATOR = new Creator<GroupSettingEntity>() {
        @Override
        public GroupSettingEntity createFromParcel(Parcel source) {
            return new GroupSettingEntity(source);
        }

        @Override
        public GroupSettingEntity[] newArray(int size) {
            return new GroupSettingEntity[size];
        }
    };

    public GroupSettingEntity(Parcel source) {
        openLinkJoin = source.readInt() == 1;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(openLinkJoin ? 1 : 0);
    }
}
