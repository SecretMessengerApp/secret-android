/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
import com.jsy.common.model.circle.LocalMedia;


import java.util.ArrayList;
import java.util.List;

public class LocalMediaFolder implements Parcelable {
    private String name;
    private String path;
    private String firstImagePath;
    private int imageNum;
    private int checkedNum;
    private boolean isChecked;
    private List<LocalMedia> images = new ArrayList<LocalMedia>();


    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getFirstImagePath() {
        return firstImagePath;
    }

    public void setFirstImagePath(String firstImagePath) {
        this.firstImagePath = firstImagePath;
    }

    public int getImageNum() {
        return imageNum;
    }

    public void setImageNum(int imageNum) {
        this.imageNum = imageNum;
    }

    public List<LocalMedia> getImages() {
        if (images == null) {
            images = new ArrayList<>();
        }
        return images;
    }

    public void setImages(List<LocalMedia> images) {
        this.images = images;
    }

    public int getCheckedNum() {
        return checkedNum;
    }

    public void setCheckedNum(int checkedNum) {
        this.checkedNum = checkedNum;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeString(this.path);
        dest.writeString(this.firstImagePath);
        dest.writeInt(this.imageNum);
        dest.writeInt(this.checkedNum);
        dest.writeByte(this.isChecked ? (byte) 1 : (byte) 0);
        dest.writeTypedList(this.images);
    }

    public LocalMediaFolder() {
    }

    protected LocalMediaFolder(Parcel in) {
        this.name = in.readString();
        this.path = in.readString();
        this.firstImagePath = in.readString();
        this.imageNum = in.readInt();
        this.checkedNum = in.readInt();
        this.isChecked = in.readByte() != 0;
        this.images = in.createTypedArrayList(LocalMedia.CREATOR);
    }

    public static final Creator<LocalMediaFolder> CREATOR = new Creator<LocalMediaFolder>() {
        @Override
        public LocalMediaFolder createFromParcel(Parcel source) {
            return new LocalMediaFolder(source);
        }

        @Override
        public LocalMediaFolder[] newArray(int size) {
            return new LocalMediaFolder[size];
        }
    };
}
