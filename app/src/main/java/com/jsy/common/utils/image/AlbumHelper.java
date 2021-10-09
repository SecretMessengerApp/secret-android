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

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore.Audio.Albums;
import android.provider.MediaStore.Images.Media;
import android.provider.MediaStore.Images.Thumbnails;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class AlbumHelper {

    private static final int MIN_WIDTH_HEIGHT = 300;

    private ContentResolver cr;

    private HashMap<String, String> thumbnailList = new HashMap<>();

    private List<HashMap<String, String>> albumList = new ArrayList<>();
    private HashMap<String, ImageBucket> bucketList = new HashMap<>();

    private static AlbumHelper instance;

    private AlbumHelper() {
    }

    public static AlbumHelper getHelper() {
        if (instance == null) {
            instance = new AlbumHelper();
        }
        return instance;
    }

    public void init(Context context) {
        if(cr == null) {
            cr = context.getContentResolver();
        }
    }

    private void getThumbnail() {
        String[] projection = {Thumbnails._ID, Thumbnails.IMAGE_ID,
                Thumbnails.DATA};
        Cursor cursor = cr.query(Thumbnails.EXTERNAL_CONTENT_URI, projection,
                null, null, Thumbnails.IMAGE_ID + " DESC");
        getThumbnailColumnData(cursor);
    }

    private void getThumbnailColumnData(Cursor cur) {
        if(cur != null && cur.moveToFirst()) {
            int _id;
            int image_id;
            String image_path;
            int _idColumn = cur.getColumnIndex(Thumbnails._ID);
            int image_idColumn = cur.getColumnIndex(Thumbnails.IMAGE_ID);
            int dataColumn = cur.getColumnIndex(Thumbnails.DATA);

            do {
                _id = cur.getInt(_idColumn);
                image_id = cur.getInt(image_idColumn);
                image_path = cur.getString(dataColumn);

                thumbnailList.put(String.valueOf(image_id), image_path);
            }while(cur.moveToNext());
        }
    }

    @TargetApi(16)
    void getAlbum16() {
        String[] projection = {Albums._ID, Albums.ALBUM, Albums.ALBUM_ART,
                Albums.ALBUM_KEY, Albums.ARTIST, Albums.NUMBER_OF_SONGS};
        Cursor cursor = cr.query(Albums.EXTERNAL_CONTENT_URI, projection, null,
                null, Albums._ID + " DESC");
        getAlbumColumnData(cursor);

    }

    @TargetApi(14)
    void getAlbum14() {
        String[] projection = {Albums._ID, Albums.ALBUM, Albums.ALBUM_ART,
                Albums.ALBUM_KEY, Albums.ARTIST, Albums.NUMBER_OF_SONGS};
        Cursor cursor = cr.query(Albums.EXTERNAL_CONTENT_URI, projection, null,
                null, Albums._ID + " DESC");
        getAlbumColumnData(cursor);

    }

    private void getAlbumColumnData(Cursor cur) {
        if (cur != null && cur.moveToFirst()) {
            int _id;
            String album;
            String albumArt;
            String albumKey;
            String artist;
            int numOfSongs;

            int _idColumn = cur.getColumnIndex(Albums._ID);
            int albumColumn = cur.getColumnIndex(Albums.ALBUM);
            int albumArtColumn = cur.getColumnIndex(Albums.ALBUM_ART);
            int albumKeyColumn = cur.getColumnIndex(Albums.ALBUM_KEY);
            int artistColumn = cur.getColumnIndex(Albums.ARTIST);
            int numOfSongsColumn = cur.getColumnIndex(Albums.NUMBER_OF_SONGS);

            do {
                _id = cur.getInt(_idColumn);
                album = cur.getString(albumColumn);
                albumArt = cur.getString(albumArtColumn);
                albumKey = cur.getString(albumKeyColumn);
                artist = cur.getString(artistColumn);
                numOfSongs = cur.getInt(numOfSongsColumn);

                HashMap<String, String> hash = new HashMap<String, String>();
                hash.put("_id", _id + "");
                hash.put("album", album);
                hash.put("albumArt", albumArt);
                hash.put("albumKey", albumKey);
                hash.put("artist", artist);
                hash.put("numOfSongs", numOfSongs + "");
                albumList.add(hash);

            } while (cur.moveToNext());

        }
    }

   private boolean hasBuildImagesBucketList = false;

    private void buildImagesBucketList(int minWidthHeight) {
        getThumbnail();

        minWidthHeight = Math.min(minWidthHeight, MIN_WIDTH_HEIGHT);
        String[] columns = new String[]{Media._ID, Media.BUCKET_ID, Media.WIDTH, Media.HEIGHT,
                Media.PICASA_ID, Media.DATA, Media.DISPLAY_NAME, Media.TITLE,
                Media.SIZE, Media.BUCKET_DISPLAY_NAME};
        Cursor cur = null;
//		if (android.os.Build.VERSION.SDK_INT >= 16) {
//			cur = cr.query(
//					Media.EXTERNAL_CONTENT_URI,
//					columns,
//					Media.WIDTH + ">? and " + Media.HEIGHT + ">?",
//					new String[] { String.valueOf(minWidthHeight),
//							String.valueOf(minWidthHeight) },
//					// null, null, null,
//					Media._ID + " DESC");
//		} else {
//			cur = cr.query(Media.EXTERNAL_CONTENT_URI, columns, null, null,
//					Media._ID + " DESC");
//		}

        cur = cr.query(
                Media.EXTERNAL_CONTENT_URI,
                columns,
                Media.WIDTH + ">? and " + Media.HEIGHT + ">?",
                new String[]{String.valueOf(minWidthHeight),
                        String.valueOf(minWidthHeight)},
                // null, null, null,
                Media._ID + " DESC");

        if (cur.moveToFirst()) {
            int photoIDIndex = cur.getColumnIndexOrThrow(Media._ID);
            int photoPathIndex = cur.getColumnIndexOrThrow(Media.DATA);
            int photoNameIndex = cur.getColumnIndexOrThrow(Media.DISPLAY_NAME);
            int photoTitleIndex = cur.getColumnIndexOrThrow(Media.TITLE);
            int photoSizeIndex = cur.getColumnIndexOrThrow(Media.SIZE);
            int bucketDisplayNameIndex = cur
                    .getColumnIndexOrThrow(Media.BUCKET_DISPLAY_NAME);
            int bucketIdIndex = cur.getColumnIndexOrThrow(Media.BUCKET_ID);
            int picasaIdIndex = cur.getColumnIndexOrThrow(Media.PICASA_ID);
            int widthIdIndex = cur.getColumnIndexOrThrow(Media.WIDTH);
            int heightIdIndex = cur.getColumnIndexOrThrow(Media.HEIGHT);
            int totalNum = cur.getCount();

            do {
                String _id = cur.getString(photoIDIndex);
                String name = cur.getString(photoNameIndex);
                String path = cur.getString(photoPathIndex);
                String title = cur.getString(photoTitleIndex);
                String size = cur.getString(photoSizeIndex);
                String bucketName = cur.getString(bucketDisplayNameIndex);
                String bucketId = cur.getString(bucketIdIndex);
                String picasaId = cur.getString(picasaIdIndex);
                int width = cur.getInt(widthIdIndex);
                int height = cur.getInt(heightIdIndex);
                File file = new File(path);
                if (!file.exists() || file.length() <= 500) {// 500 B
                    continue;
                }
                try {
                    if (TextUtils.isEmpty(path)
                            || TextUtils.isEmpty(name)
                            || (
                            // !name.toLowerCase().endsWith(".png")
                            // &&
                            !name.toLowerCase().endsWith(".jpg") && !name
                                    .toLowerCase().endsWith(".jpeg"))
                            || TextUtils.isEmpty(size)
                            || Integer.parseInt(size) <= 0) {
                        continue;
                    }
                } catch (NumberFormatException e) {
                    continue;
                }

                ImageBucket bucket = bucketList.get(bucketId);
                if (bucket == null) {
                    bucket = new ImageBucket();
                    bucketList.put(bucketId, bucket);
                    bucket.imageList = new ArrayList<ImageItem>();
                    bucket.bucketName = bucketName;
                }
                bucket.count++;
                ImageItem imageItem = new ImageItem();
                imageItem.imageId = _id;
                imageItem.imagePath = path;
                imageItem.width = width;
                imageItem.height = height;
                imageItem.thumbnailPath = thumbnailList.get(_id);
                bucket.imageList.add(imageItem);

            } while (cur.moveToNext());
        }

        Iterator<Entry<String, ImageBucket>> itr = bucketList.entrySet()
                .iterator();
        while (itr.hasNext()) {
            Entry<String, ImageBucket> entry = (Entry<String, ImageBucket>) itr
                    .next();
            ImageBucket bucket = entry.getValue();
            for (int i = 0; i < bucket.imageList.size(); ++i) {
                ImageItem image = bucket.imageList.get(i);
            }
        }
        hasBuildImagesBucketList = true;
        long endTime = System.currentTimeMillis();
    }

    public List<ImageBucket> getImagesBucketList(boolean refresh, int MIN_WIDTH_HEIGHT) {
        if (refresh && hasBuildImagesBucketList) {
            bucketList.clear();
            albumList.clear();
            thumbnailList.clear();
        }
        if (refresh || (!refresh && !hasBuildImagesBucketList)) {
            buildImagesBucketList(MIN_WIDTH_HEIGHT);
        }
        List<ImageBucket> tmpList = new ArrayList<ImageBucket>();
        Iterator<Entry<String, ImageBucket>> itr = bucketList.entrySet()
                .iterator();
        while (itr.hasNext()) {
            Entry<String, ImageBucket> entry = (Entry<String, ImageBucket>) itr
                    .next();
            tmpList.add(entry.getValue());
        }
        return tmpList;
    }

    String getOriginalImagePath(String image_id) {
        String path = null;
        String[] projection = {Media._ID, Media.DATA};
        Cursor cursor = cr.query(Media.EXTERNAL_CONTENT_URI, projection,
                Media._ID + "=" + image_id, null, Media._ID + " DESC");
        if (cursor != null) {
            cursor.moveToFirst();
            path = cursor.getString(cursor.getColumnIndex(Media.DATA));

        }
        return path;
    }

}
