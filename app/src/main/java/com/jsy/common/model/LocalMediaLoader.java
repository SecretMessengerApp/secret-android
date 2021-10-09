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

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.jsy.common.config.PictureConfig;
import com.jsy.common.model.circle.LocalMedia;
import com.jsy.common.config.PictureMimeType;
import com.waz.zclient.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class LocalMediaLoader {
    private static final Uri QUERY_URI = MediaStore.Files.getContentUri("external");
    private static final String ORDER_BY = MediaStore.Files.FileColumns._ID + " DESC";
    private static final String DURATION = "duration";
    private static final String NOT_GIF = "!='image/gif'";
    private static final int AUDIO_DURATION = 500;
    private int type = PictureConfig.TYPE_IMAGE;
    private FragmentActivity activity;
    private boolean isGif;
    private long videoMaxS = 0;
    private long videoMinS = 0;

    private static final String[] PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            DURATION};

    private static final String SELECTION = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
            + " AND " + MediaStore.MediaColumns.SIZE + ">0";

    private static final String SELECTION_NOT_GIF = MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
            + " AND " + MediaStore.MediaColumns.SIZE + ">0"
            + " AND " + MediaStore.MediaColumns.MIME_TYPE + NOT_GIF;

    private static String getSelectionArgsForSingleMediaCondition(String time_condition) {
        return MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
                + " AND " + MediaStore.MediaColumns.SIZE + ">0"
                + " AND " + time_condition;
    }

    private static String getSelectionArgsForAllMediaCondition(String time_condition, boolean isGif) {
        String condition = "(" + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?"
                + (isGif ? "" : " AND " + MediaStore.MediaColumns.MIME_TYPE + NOT_GIF)
                + " OR "
                + (MediaStore.Files.FileColumns.MEDIA_TYPE + "=? AND " + time_condition) + ")"
                + " AND " + MediaStore.MediaColumns.SIZE + ">0";
        return condition;
    }

    private static final String[] SELECTION_ALL_ARGS = {
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
            String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO),
    };

    private static String[] getSelectionArgsForSingleMediaType(int mediaType) {
        return new String[]{String.valueOf(mediaType)};
    }

    public LocalMediaLoader(FragmentActivity activity, int type, boolean isGif) {
        this.activity = activity;
        this.type = type;
        this.isGif = isGif;
    }

    public void loadAllMedia(final LocalMediaLoadListener imageLoadListener) {
        activity.getSupportLoaderManager().initLoader(type, null,
                new LoaderManager.LoaderCallbacks<Cursor>() {
                    @Override
                    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
                        CursorLoader cursorLoader = null;
                        switch (id) {
                            case PictureConfig.TYPE_ALL:
                                String all_condition = getSelectionArgsForAllMediaCondition(getDurationCondition(videoMaxS, videoMinS), isGif);
                                cursorLoader = new CursorLoader(activity, QUERY_URI, PROJECTION, all_condition, SELECTION_ALL_ARGS, ORDER_BY);
                                break;
                            case PictureConfig.TYPE_IMAGE:
                                String[] MEDIA_TYPE_IMAGE = getSelectionArgsForSingleMediaType(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE);
                                cursorLoader = new CursorLoader(
                                        activity, QUERY_URI,
                                        PROJECTION, isGif ? SELECTION : SELECTION_NOT_GIF, MEDIA_TYPE_IMAGE
                                        , ORDER_BY);
                                break;
                            case PictureConfig.TYPE_VIDEO:
                                String video_condition = getSelectionArgsForSingleMediaCondition(getDurationCondition(0, 0));
                                String[] MEDIA_TYPE_VIDEO = getSelectionArgsForSingleMediaType(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO);
                                cursorLoader = new CursorLoader(
                                        activity, QUERY_URI, PROJECTION, video_condition, MEDIA_TYPE_VIDEO
                                        , ORDER_BY);
                                break;
                            case PictureConfig.TYPE_AUDIO:
                                String audio_condition = getSelectionArgsForSingleMediaCondition(getDurationCondition(0, AUDIO_DURATION));
                                String[] MEDIA_TYPE_AUDIO = getSelectionArgsForSingleMediaType(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO);
                                cursorLoader = new CursorLoader(
                                        activity, QUERY_URI, PROJECTION, audio_condition, MEDIA_TYPE_AUDIO
                                        , ORDER_BY);
                                break;
                        }
                        return cursorLoader;
                    }

                    @Override
                    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                        try {
                            List<LocalMediaFolder> imageFolders = new ArrayList<>();
                            LocalMediaFolder allImageFolder = new LocalMediaFolder();
                            List<LocalMedia> latelyImages = new ArrayList<>();
                            if (data != null) {
                                int count = data.getCount();
                                if (count > 0) {
                                    data.moveToFirst();
                                    do {
                                        String path = data.getString
                                                (data.getColumnIndexOrThrow(PROJECTION[1]));

                                        String pictureType = data.getString
                                                (data.getColumnIndexOrThrow(PROJECTION[2]));

                                        int w = data.getInt
                                                (data.getColumnIndexOrThrow(PROJECTION[3]));

                                        int h = data.getInt
                                                (data.getColumnIndexOrThrow(PROJECTION[4]));

                                        int duration = data.getInt
                                                (data.getColumnIndexOrThrow(PROJECTION[5]));
                                        if(new File(path).exists()){
                                            LocalMedia image = new LocalMedia
                                                    (path, duration, type, pictureType, w, h);

                                            LocalMediaFolder folder = getImageFolder(path, imageFolders);
                                            List<LocalMedia> images = folder.getImages();
                                            images.add(image);
                                            folder.setImageNum(folder.getImageNum() + 1);
                                            latelyImages.add(image);
                                            int imageNum = allImageFolder.getImageNum();
                                            allImageFolder.setImageNum(imageNum + 1);
                                        }

                                    } while (data.moveToNext());

                                    if (latelyImages.size() > 0) {
                                        sortFolder(imageFolders);
                                        imageFolders.add(0, allImageFolder);
                                        allImageFolder.setFirstImagePath
                                                (latelyImages.get(0).getPath());
                                        String title = type == PictureMimeType.ofAudio() ?
                                                activity.getString(R.string.picture_all_audio)
                                                : activity.getString(R.string.picture_camera_roll);
                                        allImageFolder.setName(title);
                                        allImageFolder.setImages(latelyImages);
                                    }
                                    imageLoadListener.loadComplete(imageFolders);
                                } else {
                                    imageLoadListener.loadComplete(imageFolders);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onLoaderReset(Loader<Cursor> loader) {
                    }
                });
    }

    private void sortFolder(List<LocalMediaFolder> imageFolders) {
        Collections.sort(imageFolders, new Comparator<LocalMediaFolder>() {
            @Override
            public int compare(LocalMediaFolder lhs, LocalMediaFolder rhs) {
                if (lhs.getImages() == null || rhs.getImages() == null) {
                    return 0;
                }
                int lsize = lhs.getImageNum();
                int rsize = rhs.getImageNum();
                return lsize == rsize ? 0 : (lsize < rsize ? 1 : -1);
            }
        });
    }

    private LocalMediaFolder getImageFolder(String path, List<LocalMediaFolder> imageFolders) {
        File imageFile = new File(path);
        File folderFile = imageFile.getParentFile();
        for (LocalMediaFolder folder : imageFolders) {
            if (folder.getName().equals(folderFile.getName())) {
                return folder;
            }
        }
        LocalMediaFolder newFolder = new LocalMediaFolder();
        newFolder.setName(folderFile.getName());
        newFolder.setPath(folderFile.getAbsolutePath());
        newFolder.setFirstImagePath(path);
        imageFolders.add(newFolder);
        return newFolder;
    }

    private String getDurationCondition(long exMaxLimit, long exMinLimit) {
        long maxS = videoMaxS == 0 ? Long.MAX_VALUE : videoMaxS;
        if (exMaxLimit != 0) maxS = Math.min(maxS, exMaxLimit);

        return String.format(Locale.CHINA, "%d <%s duration and duration <= %d",
                Math.max(exMinLimit, videoMinS),
                Math.max(exMinLimit, videoMinS) == 0 ? "" : "=",
                maxS);
    }


    public interface LocalMediaLoadListener {
        void loadComplete(List<LocalMediaFolder> folders);
    }
}
