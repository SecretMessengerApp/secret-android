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
package com.jsy.common.acts.scan;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.Result;
import com.jsy.common.utils.FileUtil;
import com.jsy.common.utils.QrCodeUtil;
import com.jsy.res.utils.ViewUtils;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.utils.IntentUtils;
import com.waz.zclient.utils.StringUtils;

import java.lang.ref.WeakReference;

import me.dm7.barcodescanner.zxing.ZXingScannerView;

/**
 * Created by eclipse on 2018/5/22.
 */

public class ScanAuthorizeLoginActivity extends BaseActivity implements ZXingScannerView.ResultHandler {

    private ZXingScannerView scannerView;

    public boolean showLocalQrCodePicture = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_scan_authorize_login);
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.scan_toolbar);
        setToolbarNavigtion(toolbar, this);

        scannerView = ViewUtils.getView(this, R.id.scan_view);
        scannerView.setResultHandler(this);
        scannerView.setAutoFocus(true);

        View showLocalQrCodePictureView = findViewById(R.id.album);
        showLocalQrCodePictureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPicture();
            }
        });
        showLocalQrCodePictureView.setVisibility(showLocalQrCodePicture ? View.VISIBLE : View.GONE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerView.startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_REQUET_PERMISSION_CAMERA);
        }
    }

    private final int REQUEST_CODE_REQUET_PERMISSION_READ_EXTERNAL_STORAGE = 1001;
    private final int REQUEST_CODE_REQUET_PERMISSION_CAMERA = 1002;
    private final int REQUEST_CODE_SELECT_PICTURE           = 1003;

    private void selectPicture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_CODE_REQUET_PERMISSION_READ_EXTERNAL_STORAGE);
        } else {
            startActivityForResult(IntentUtils.getPictureIntent(), REQUEST_CODE_SELECT_PICTURE);
        }
    }

    private String getSelectPicturePath(Intent data) {
        String selectPicturePath = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(data.getData(),
                proj, null, null, null);
        if (cursor.moveToFirst()) {
            int column_index = cursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            selectPicturePath = cursor.getString(column_index);
            if (selectPicturePath == null) {
                selectPicturePath = FileUtil.getPath(getApplicationContext(), data.getData());
            }
        }

        cursor.close();
        return selectPicturePath;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_REQUET_PERMISSION_READ_EXTERNAL_STORAGE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivityForResult(IntentUtils.getPictureIntent(), REQUEST_CODE_SELECT_PICTURE);
                } else {
                    showToast(R.string.toast_no_external_storage_permission);
                }
                break;
            case REQUEST_CODE_REQUET_PERMISSION_CAMERA: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    scannerView.startCamera();
                } else {
                    showToast(R.string.toast_no_camera_permission);
                }
            }
            break;
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_SELECT_PICTURE: {
                    try {
                        String selectPicturePath = getSelectPicturePath(data);
                        if (!TextUtils.isEmpty(selectPicturePath)) {
                            new ScanAuthorizeLoginActivity.DecodeAsyncTask(this).execute(selectPicturePath);
                        } else {
                            // ...
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                default:
                    break;
            }
        } else {
            // ...
        }
    }

    @Override
    public void handleResult(Result result) {
        String text = result == null ? "" : result.getText();
        Intent intent = new Intent();
        if (!StringUtils.isBlank(text)) {
            intent.putExtra("scan_result", text);
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED, intent);
        }

        finish();
    }

    public boolean canUseSwipeBackLayout() {
        return true;
    }


    @Override
    public void onStop() {
        scannerView.stopCamera();
        super.onStop();
    }


    private static class DecodeAsyncTask extends AsyncTask<String, Void, Result> {

        private final WeakReference<ScanAuthorizeLoginActivity> weakActivity;

        public DecodeAsyncTask(ScanAuthorizeLoginActivity activity) {
            weakActivity = new WeakReference<>(activity);
        }

        @Override
        protected Result doInBackground(String... params) {
            return QrCodeUtil.syncDecodeQRCode(params[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ScanAuthorizeLoginActivity activity = weakActivity.get();
            if (activity != null) {
                activity.showProgressDialog(R.string.on_recognizing);
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            ScanAuthorizeLoginActivity activity = weakActivity.get();
            if (activity != null) {
                activity.closeProgressDialog();
                activity.handleResult(result);
            }
        }
    }
}


