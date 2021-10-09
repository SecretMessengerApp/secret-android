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
package com.jsy.common.model.circle;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import java.io.Serializable;

public class LocalMedia implements Parcelable, Serializable {
    private String path;
    private String compressPath;
    private String cutPath;
    private long duration;
    private boolean isChecked;
    private boolean isCut;
    public int position;
    private int num;
    private int mimeType;
    private String pictureType;
    private boolean compressed;
    private String videoFrame;
    private int width;
    private int height;

    public LocalMedia() {

    }

    public LocalMedia(String path, long duration, int mimeType, String pictureType) {
        this.path = path;
        this.duration = duration;
        this.mimeType = mimeType;
        this.pictureType = pictureType;
    }

    public LocalMedia(String path, long duration, int mimeType, String pictureType, int width, int height) {
        this.path = path;
        this.duration = duration;
        this.mimeType = mimeType;
        this.pictureType = pictureType;
        this.width = width;
        this.height = height;
    }

    public LocalMedia(String path, long duration,
                      boolean isChecked, int position, int num, int mimeType) {
        this.path = path;
        this.duration = duration;
        this.isChecked = isChecked;
        this.position = position;
        this.num = num;
        this.mimeType = mimeType;
    }

    public String getPictureType() {
        if (TextUtils.isEmpty(pictureType)) {
            pictureType = "image/jpeg";
        }
        return pictureType;
    }

    public void setPictureType(String pictureType) {
        this.pictureType = pictureType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCompressPath() {
        return compressPath;
    }

    public void setCompressPath(String compressPath) {
        this.compressPath = compressPath;
    }

    public String getCutPath() {
        return cutPath;
    }

    public void setCutPath(String cutPath) {
        this.cutPath = cutPath;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }


    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    public boolean isCut() {
        return isCut;
    }

    public void setCut(boolean cut) {
        isCut = cut;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getNum() {
        return num;
    }

    public void setNum(int num) {
        this.num = num;
    }

    public int getMimeType() {
        return mimeType;
    }

    public void setMimeType(int mimeType) {
        this.mimeType = mimeType;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getVideoFrame() {
        return videoFrame;
    }

    public void setVideoFrame(String videoFrame) {
        this.videoFrame = videoFrame;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.path);
        dest.writeString(this.compressPath);
        dest.writeString(this.cutPath);
        dest.writeLong(this.duration);
        dest.writeByte(this.isChecked ? (byte) 1 : (byte) 0);
        dest.writeByte(this.isCut ? (byte) 1 : (byte) 0);
        dest.writeInt(this.position);
        dest.writeInt(this.num);
        dest.writeInt(this.mimeType);
        dest.writeString(this.pictureType);
        dest.writeByte(this.compressed ? (byte) 1 : (byte) 0);
        dest.writeString(this.videoFrame);
        dest.writeInt(this.width);
        dest.writeInt(this.height);
    }

    protected LocalMedia(Parcel in) {
        this.path = in.readString();
        this.compressPath = in.readString();
        this.cutPath = in.readString();
        this.duration = in.readLong();
        this.isChecked = in.readByte() != 0;
        this.isCut = in.readByte() != 0;
        this.position = in.readInt();
        this.num = in.readInt();
        this.mimeType = in.readInt();
        this.pictureType = in.readString();
        this.compressed = in.readByte() != 0;
        this.videoFrame = in.readString();
        this.width = in.readInt();
        this.height = in.readInt();
    }

    public static final Creator<LocalMedia> CREATOR = new Creator<LocalMedia>() {
        @Override
        public LocalMedia createFromParcel(Parcel source) {
            return new LocalMedia(source);
        }

        @Override
        public LocalMedia[] newArray(int size) {
            return new LocalMedia[size];
        }
    };
}
