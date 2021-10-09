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
package com.jsy.common.config;

public final class PictureConfig {
    public final static String FC_TAG = "picture";
    public final static String EXTRA_RESULT_SELECTION = "extra_result_media";
    public final static String EXTRA_LOCAL_MEDIAS = "localMedias";
    public final static String EXTRA_PREVIEW_SELECT_LIST = "previewSelectList";
    public final static String EXTRA_SELECT_LIST = "selectList";
    public final static String EXTRA_POSITION = "position";
    public final static String EXTRA_MEDIA = "media";
    public final static String DIRECTORY_PATH = "directory_path";
    public final static String BUNDLE_CAMERA_PATH = "CameraPath";
    public final static String BUNDLE_ORIGINAL_PATH = "OriginalPath";
    public final static String EXTRA_BOTTOM_PREVIEW = "bottom_preview";
    public final static String EXTRA_CONFIG = "PictureSelectorConfig";
    public final static String IMAGE = "image";
    public final static String VIDEO = "video";

    public final static int TYPE_ALL = 0;
    public final static int TYPE_IMAGE = 1;
    public final static int TYPE_VIDEO = 2;
    public final static int TYPE_AUDIO = 3;

    public static final int MAX_COMPRESS_SIZE = 100;
    public static final int TYPE_CAMERA = 1;
    public static final int TYPE_PICTURE = 2;

    public final static int SINGLE = 1;
    public final static int MULTIPLE = 2;

    public final static int CHOOSE_REQUEST = 188;
    public final static int REQUEST_CAMERA = 909;
    public final static int READ_EXTERNAL_STORAGE = 0x01;
    public final static int CAMERA = 0x02;
}
