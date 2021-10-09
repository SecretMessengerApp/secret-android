/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class GroupChangeViewMemEntity implements Serializable, Parcelable {

    public boolean viewmem;

    public GroupChangeViewMemEntity(){}

    public GroupChangeViewMemEntity(boolean viewmem) {this.viewmem = viewmem;}

    protected GroupChangeViewMemEntity(Parcel in) {
        viewmem = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte((byte) (viewmem ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<GroupChangeViewMemEntity> CREATOR = new Creator<GroupChangeViewMemEntity>() {
        @Override
        public GroupChangeViewMemEntity createFromParcel(Parcel in) {
            return new GroupChangeViewMemEntity(in);
        }

        @Override
        public GroupChangeViewMemEntity[] newArray(int size) {
            return new GroupChangeViewMemEntity[size];
        }
    };
}
