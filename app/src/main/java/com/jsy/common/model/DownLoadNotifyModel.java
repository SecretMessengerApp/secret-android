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

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;

import com.jsy.common.srv.DownloadManagerNotificationService;
import com.waz.zclient.R;

public class DownLoadNotifyModel implements Parcelable {
    public static String TAG = DownLoadNotifyModel.class.getSimpleName();
    private PendingIntent downloadSuccessPendingIntent;
    private String channelId = TAG;
    private int notificationId = DownloadManagerNotificationService.NO_NOTIFICATION;
    private String contentTitle = "";
    private String contentText = "";
    private String bigText = "";
    private boolean autoCancel = true;
    private int flags = Notification.FLAG_AUTO_CANCEL;
    private int notificationIconResId = R.drawable.ic_menu_logo;
    private String mediaType;
    private String url;
    private String md5FileName;
    private int progress;
    private int length;
    private int what;
    private String subText = "";
    private String exception;
    private boolean isApk;

    public static DownLoadNotifyModel createNotificationModel(DownLoadNotifyModel input,
                                                              int progress,
                                                              int length,
                                                              String subtext,
                                                              int what,
                                                              String exception) {
        DownLoadNotifyModel notificationModel = new DownLoadNotifyModel();
        notificationModel.url = input.url;
        notificationModel.isApk = input.isApk;
        notificationModel.md5FileName = input.md5FileName;
        notificationModel.notificationId = input.notificationId;
        notificationModel.contentTitle = input.contentTitle;
        notificationModel.notificationIconResId = input.notificationIconResId;
        notificationModel.downloadSuccessPendingIntent = input.downloadSuccessPendingIntent;

        notificationModel.progress = progress;
        notificationModel.length = length;
        notificationModel.subText = subtext;
        notificationModel.what = what;
        notificationModel.exception = exception;
        return notificationModel;
    }

    public static DownLoadNotifyModel createNotificationModel(String url,
                                                              boolean isApk,
                                                              String toMd5FileName,
                                                              int notificationId,
                                                              String contentTitle,
                                                              int notificationIconResId) {
        DownLoadNotifyModel notificationModel = new DownLoadNotifyModel();
        notificationModel.url = url;
        notificationModel.isApk = isApk;
        notificationModel.md5FileName = toMd5FileName;
        notificationModel.notificationId = notificationId;
        notificationModel.contentTitle = contentTitle;
        notificationModel.notificationIconResId = notificationIconResId;
        return notificationModel;
    }

    private DownLoadNotifyModel() {

    }

    protected DownLoadNotifyModel(Parcel in) {
        downloadSuccessPendingIntent = in.readParcelable(PendingIntent.class.getClassLoader());
        channelId = in.readString();
        notificationId = in.readInt();
        contentTitle = in.readString();
        contentText = in.readString();
        bigText = in.readString();
        autoCancel = in.readByte() != 0;
        flags = in.readInt();
        notificationIconResId = in.readInt();
        mediaType = in.readString();
        url = in.readString();
        md5FileName = in.readString();
        progress = in.readInt();
        length = in.readInt();
        what = in.readInt();
        subText = in.readString();
        exception = in.readString();
        isApk = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(downloadSuccessPendingIntent, flags);
        dest.writeString(channelId);
        dest.writeInt(notificationId);
        dest.writeString(contentTitle);
        dest.writeString(contentText);
        dest.writeString(bigText);
        dest.writeByte((byte) (autoCancel ? 1 : 0));
        dest.writeInt(flags);
        dest.writeInt(notificationIconResId);
        dest.writeString(mediaType);
        dest.writeString(url);
        dest.writeString(md5FileName);
        dest.writeInt(progress);
        dest.writeInt(length);
        dest.writeInt(what);
        dest.writeString(subText);
        dest.writeString(exception);
        dest.writeByte((byte) (isApk ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<DownLoadNotifyModel> CREATOR = new Creator<DownLoadNotifyModel>() {
        @Override
        public DownLoadNotifyModel createFromParcel(Parcel in) {
            return new DownLoadNotifyModel(in);
        }

        @Override
        public DownLoadNotifyModel[] newArray(int size) {
            return new DownLoadNotifyModel[size];
        }
    };

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public int getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public String getContentTitle() {
        return contentTitle;
    }

    public void setContentTitle(String contentTitle) {
        this.contentTitle = contentTitle;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public String getBigText() {
        return bigText;
    }

    public void setBigText(String bigText) {
        this.bigText = bigText;
    }

    public boolean isAutoCancel() {
        return autoCancel;
    }

    public void setAutoCancel(boolean autoCancel) {
        this.autoCancel = autoCancel;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }

    public int getNotificationIconResId() {
        return notificationIconResId;
    }

    public void setNotificationIconResId(int notificationIconResId) {
        this.notificationIconResId = notificationIconResId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMd5FileName() {
        return md5FileName;
    }

    public void setMd5FileName(String md5FileName) {
        this.md5FileName = md5FileName;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getWhat() {
        return what;
    }

    public void setWhat(int what) {
        this.what = what;
    }

    public String getSubText() {
        return subText;
    }

    public void setSubText(String subText) {
        this.subText = subText;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

    public boolean isApk() {
        return isApk;
    }

    public void setApk(boolean apk) {
        isApk = apk;
    }

    public PendingIntent getDownloadSuccessPendingIntent() {
        return downloadSuccessPendingIntent;
    }

    public void setDownloadSuccessPendingIntent(PendingIntent downloadSuccessPendingIntent) {
        this.downloadSuccessPendingIntent = downloadSuccessPendingIntent;
    }
}
