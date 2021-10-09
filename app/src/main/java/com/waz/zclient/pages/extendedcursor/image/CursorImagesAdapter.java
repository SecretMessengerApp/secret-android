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
package com.waz.zclient.pages.extendedcursor.image;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.jsy.common.model.circle.LocalMedia;
import com.waz.zclient.R;
import com.waz.zclient.messages.parts.*;

class CursorImagesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int VIEW_TYPE_CAMERA = 0;
    private static final int VIEW_TYPE_GALLERY = 1;

    private Cursor cursor;
    private CursorImagesLayout.Callback callback;
    private AdapterCallback adapterCallback;
    private CameraViewHolder cameraViewHolder;
    private CursorCameraLayout.Callback cameraCallback = new CursorCameraLayout.Callback() {
        @Override
        public void openCamera() {
            if (callback != null) {
                callback.openCamera();
            }
        }

        @Override
        public void openVideo() {
            if (callback != null) {
                callback.openVideo();
            }
        }

        @Override
        public void onCameraPreviewAttached() {
            adapterCallback.onCameraPreviewAttached();
        }

        @Override
        public void onCameraPreviewDetached() {
            adapterCallback.onCameraPreviewDetached();
        }

        @Override
        public void onPictureTaken(byte[] imageData, boolean isMirrored) {
            if (callback != null) {
                callback.onPictureTaken(imageData, isMirrored);
            }
        }
    };

    private boolean closed = false;
    private ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (!closed) {
                load();
            }
        }
    };
    private final ContentResolver resolver;

    CursorImagesAdapter(Context context, AdapterCallback adapterCallback) {
        this.resolver = context.getContentResolver();
        this.adapterCallback = adapterCallback;

        load();
        resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, observer);
    }

    private static final String[] PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.Video.VideoColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE};

    private static final String SELECTION = "("
            + MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
            + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            + " OR "
            + MediaStore.Files.FileColumns.MEDIA_TYPE + " = "
            + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO +
            ")"
            + " AND " + MediaStore.MediaColumns.SIZE + " > 0";

    private static class LoadTask extends AsyncTask<Void, Void, Cursor> {
        private final CursorImagesAdapter adapter;

        LoadTask(CursorImagesAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        protected Cursor doInBackground(Void... params) {
            Cursor c = adapter.resolver.query(MediaStore.Files.getContentUri("external"), PROJECTION, SELECTION, null, MediaStore.Files.FileColumns._ID + " ASC");
            if (c != null) {
                c.moveToLast(); // force cursor loading and move to last, as we are displaying images in reverse order
            }
            return c;
        }

        @Override
        protected void onPostExecute(Cursor c) {
            if (adapter.closed) {
                c.close();
            } else {
                if (adapter.cursor != null) {
                    adapter.cursor.close();
                }
                adapter.setCursor(c);
            }
        }
    }

    private void setCursor(Cursor c) {
        cursor = c;
        notifyDataSetChanged();
    }

    private void load() {
        new LoadTask(this).execute();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_CAMERA) {
            cameraViewHolder = new CameraViewHolder(inflater.inflate(R.layout.item_camera_cursor, parent, false));
            cameraViewHolder.getLayout().setCallback(cameraCallback);
            return cameraViewHolder;
        } else {
            CursorGalleryItem item = (CursorGalleryItem)inflater.inflate(R.layout.item_cursor_gallery, parent, false);
            return new GalleryItemViewHolder(item);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if(getItemViewType(position) == VIEW_TYPE_GALLERY) {
            cursor.moveToPosition(cursor.getCount() - position);

            String path = cursor.getString(cursor.getColumnIndexOrThrow(PROJECTION[1]));

            String pictureType = cursor.getString(cursor.getColumnIndexOrThrow(PROJECTION[2]));

            int width = cursor.getInt(cursor.getColumnIndexOrThrow(PROJECTION[3]));

            int height = cursor.getInt(cursor.getColumnIndexOrThrow(PROJECTION[4]));

            int duration = cursor.getInt(cursor.getColumnIndexOrThrow(PROJECTION[5]));

            int mediaType = cursor.getInt(cursor.getColumnIndexOrThrow(PROJECTION[6]));

            ((GalleryItemViewHolder) holder).bind(
                new LocalMedia(path, duration, mediaType, pictureType, width, height)
                , callback);
        }
    }

    @Override
    public int getItemCount() {
        return cursor == null ? 1 : cursor.getCount() + 1;
    }

    @Override
    public int getItemViewType(int position) {
        return (position == 0) ? VIEW_TYPE_CAMERA : VIEW_TYPE_GALLERY;
    }

    void close() {
        closed = true;
        if (cameraViewHolder != null) {
            cameraViewHolder.getLayout().onClose();
        }

        if (cursor != null) {
            cursor.close();
            cursor = null;
            notifyDataSetChanged();
        }

        resolver.unregisterContentObserver(observer);
    }

    private static class CameraViewHolder extends RecyclerView.ViewHolder {

        public CursorCameraLayout getLayout() {
            return (CursorCameraLayout) itemView;
        }

        CameraViewHolder(View itemView) {
            super(itemView);
        }
    }

    public void setCallback(CursorImagesLayout.Callback callback) {
        this.callback = callback;
    }

    interface AdapterCallback {
        void onCameraPreviewDetached();
        void onCameraPreviewAttached();
    }
}

