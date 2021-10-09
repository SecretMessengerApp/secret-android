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

import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;

import com.jsy.common.utils.StorageUtil;
import com.waz.zclient.utils.ServerConfig;
import com.waz.zclient.utils.SpUtils;

import java.io.File;

public class CircleConstant {
    public static String SAVE_IMG_PATH;
    public static String BACKUP_FIRST_PATH;
    public static String DOWNLOAD_PATH;

    public static void setPackageName(Context context) {
        SAVE_IMG_PATH = StorageUtil.getExternalStoragePublicPath(Environment.DIRECTORY_PICTURES) + File.separator + "Secret";
        BACKUP_FIRST_PATH = StorageUtil.getExternalStoragePublicPath(Environment.DIRECTORY_DOCUMENTS);

        String saveFilePath = StorageUtil.getExternalSandBoxFilesPath(context);
        DOWNLOAD_PATH = saveFilePath + File.separator + "okhttp_download";
    }

    public static String appendAvatarUrl(String assetId, Context context) {
        if(TextUtils.isEmpty(assetId)){
            return "";
        }
        return ServerConfig.getBaseUrl() + "/assets/v3/" + assetId + "?access_token=" + SpUtils.getToken(context);
    }
}
