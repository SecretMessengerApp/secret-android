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
package com.jsy.common.utils.image;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

public class ImageItem implements Parcelable {
    public String imageId;
    public String thumbnailPath;
    public String imagePath;
    public int width;
    public int height;

    public ORIENTATION orientation = ORIENTATION.HORIZONTAL;

    private Bitmap bitmap;

    public ImageItem() {
        // TODO Auto-generated constructor stub
    }

    public String getImageId() {
        return imageId;
    }

    public void setImageId(String imageId) {
        this.imageId = imageId;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public Bitmap getBitmap() {
        if (bitmap == null) {
            try {
                bitmap = Bmp.revitionImageSize(imagePath);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Override
    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // TODO Auto-generated method stub
        dest.writeString(imageId);
        dest.writeString(thumbnailPath);
        dest.writeString(imagePath);
        dest.writeInt(width);
        dest.writeInt(height);
    }

    public static final Creator<ImageItem> CREATOR = new Creator<ImageItem>() {

        @Override
        public ImageItem[] newArray(int size) {
            // TODO Auto-generated method stub
            return new ImageItem[size];
        }

        @Override
        public ImageItem createFromParcel(Parcel source) {
            // TODO Auto-generated method stub
            return new ImageItem(source);
        }
    };

    public ImageItem(Parcel source) {
        // TODO Auto-generated constructor stub
        imageId = source.readString();
        thumbnailPath = source.readString();
        imagePath = source.readString();
        width = source.readInt();
        height = source.readInt();

    }

}
