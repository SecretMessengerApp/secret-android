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
package com.waz.zclient.pages.main.conversation;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.waz.utils.IoUtils;
import com.waz.utils.wrappers.AndroidURI;
import com.waz.utils.wrappers.AndroidURIUtil;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.utils.MainActivityUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class AssetIntentsManager {

    private static final String INTENT_GALLERY_TYPE = "image/*";
    private final Context context;
    private final Callback callback;

    private static String openDocumentAction() {
        return Intent.ACTION_OPEN_DOCUMENT;
    }

    public AssetIntentsManager(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    private void openDocument(String mimeType, IntentType tpe, boolean allowMultiple) {
        Intent documentIntent = new Intent(openDocumentAction()).setType(mimeType).addCategory(Intent.CATEGORY_OPENABLE);
        if (allowMultiple) {
            documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }
        callback.openIntent(documentIntent, tpe);
    }

    public void openFileSharing() {
        openDocument("*/*", IntentType.FILE_SHARING, true);
    }

    public void openBackupImport() {
        openDocument("*/*", IntentType.BACKUP_IMPORT, false);
    }

    public void captureVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        callback.openIntent(intent, IntentType.VIDEO);
    }

    public void openGallery() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.GALLERY, false);
    }

    public void openGalleryForSketch() {
        openDocument(INTENT_GALLERY_TYPE, IntentType.SKETCH_FROM_GALLERY, false);
    }

    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {

        if (callback == null) {
            throw new IllegalStateException("A callback must be set!");
        }

        IntentType type = IntentType.get(requestCode);

        if (type == IntentType.UNKNOWN) {
            return false;
        }

        if (resultCode == Activity.RESULT_CANCELED) {
            callback.onCanceled(type);
            return true;
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onFailed(type);
            return true;
        }

        if(data.getClipData() != null) {
            ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                callback.onDataReceived(type, new AndroidURI(clipData.getItemAt(i).getUri()));
            }
            return true;
        }

        if (data.getData() == null) {
            callback.onFailed(type);
            return false;
        }

        URI uri = new AndroidURI(data.getData());
        if (type == IntentType.VIDEO) {
            uri = copyVideoToCache(uri);
        }

        if (uri != null) {
            callback.onDataReceived(type, uri);
        }
        return true;
    }

    @Nullable
    private URI copyVideoToCache(URI uri) {
        File mediaStorageDir = context.getExternalCacheDir();
        if (mediaStorageDir == null || !mediaStorageDir.exists()) {
            return null;
        }

        java.util.Date date = new java.util.Date();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(date.getTime());
        File targetFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");

        if (targetFile.exists()) {
            targetFile.delete();
        }
        try {
            targetFile.createNewFile();
            InputStream inputStream = context.getContentResolver().openInputStream(AndroidURIUtil.unwrap(uri));
            if (inputStream != null) {
                IoUtils.copy(inputStream, targetFile);
            }
        } catch (IOException e) {
            return null;
        } finally {
            try {
                context.getContentResolver().delete(AndroidURIUtil.unwrap(uri), null, null);
            } catch (SecurityException | IllegalArgumentException ignored) {
            }
        }

        return new AndroidURI(FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", targetFile));
    }

    public enum IntentType {
        UNKNOWN(MainActivityUtils.REQUEST_CODE_IntentType_UNKNOWN()),
        GALLERY(MainActivityUtils.REQUEST_CODE_IntentType_GALLERY()),
        SKETCH_FROM_GALLERY(MainActivityUtils.REQUEST_CODE_IntentType_SKETCH_FROM_GALLERY()),
        VIDEO(MainActivityUtils.REQUEST_CODE_IntentType_VIDEO()),
        VIDEO_CURSOR_BUTTON(MainActivityUtils.REQUEST_CODE_IntentType_VIDEO_CURSOR_BUTTON()),
        CAMERA(MainActivityUtils.REQUEST_CODE_IntentType_CAMERA()),
        FILE_SHARING(MainActivityUtils.REQUEST_CODE_IntentType_FILE_SHARING()),
        BACKUP_IMPORT(MainActivityUtils.REQUEST_CODE_IntentType_BACKUP_IMPORT());

        public int requestCode;

        IntentType(int requestCode) {
            this.requestCode = requestCode;
        }

        public static IntentType get(int requestCode) {

            if (requestCode == GALLERY.requestCode) {
                return GALLERY;
            }

            if (requestCode == SKETCH_FROM_GALLERY.requestCode) {
                return SKETCH_FROM_GALLERY;
            }

            if (requestCode == CAMERA.requestCode) {
                return CAMERA;
            }

            if (requestCode == VIDEO_CURSOR_BUTTON.requestCode) {
                return VIDEO_CURSOR_BUTTON;
            }

            if (requestCode == VIDEO.requestCode) {
                return VIDEO;
            }

            if (requestCode == FILE_SHARING.requestCode) {
                return FILE_SHARING;
            }

            if (requestCode == BACKUP_IMPORT.requestCode) {
                return BACKUP_IMPORT;
            }

            return UNKNOWN;
        }
    }

    public interface Callback {
        void onDataReceived(IntentType type, URI uri);

        void onCanceled(IntentType type);

        void onFailed(IntentType type);

        void openIntent(Intent intent, AssetIntentsManager.IntentType intentType);
    }
}
