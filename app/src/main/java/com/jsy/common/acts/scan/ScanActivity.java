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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.zxing.Result;
import com.jsy.common.fragment.ScanARCodeFragment;
import com.jsy.common.fragment.ScanQRCodeFragment;
import com.jsy.common.utils.FileUtil;
import com.jsy.common.utils.CheckPermissionUtils;
import com.jsy.common.utils.QrCodeUtil;
import com.jsy.common.utils.SelfStartSetting;
import com.jsy.res.utils.ViewUtils;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.utils.IntentUtils;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Created by eclipse on 2018/9/6.
 */
public class ScanActivity extends BaseActivity implements View.OnClickListener {
    private static final String TAG = ScanActivity.class.getSimpleName();
    private static final int IMAGE_SELECT_CODE = 100;
    private static final int PERMISSION_EXTERNAL_CODE = 101;
    private static final int PERMISSION_CAMERA_CODE = 102;
    private static final String[] externalPermission = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    private static final String[] cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE};
    public final static String KEY_SCAN_TYPE = "key_scan_type";
    public final static int TYPE_SCANQR = 1;
    public final static int TYPE_SCANAR = 2;
    private Toolbar toolbar;
    private TextView titleView;
    private ImageView albumView;
    private TextView qrTextView;
    private TextView arTextView;
    private ImageView qrImageView;
    private ImageView arImageView;
    private LinearLayout qrLayout;
    private LinearLayout arLayout;
    private int selectTabColor, unSelectTabColor;
    private int curScanType;
    private boolean isNeedCheck = true;

    private ScanARCodeFragment arCodeFragment;
    private ScanQRCodeFragment qrCodeFragment;

    public static void startSelf(Context context) {
        startSelf(context, TYPE_SCANQR);
    }

    public static void startSelf(Context context, int scanType) {
        Intent intent = new Intent();
        intent.putExtra(KEY_SCAN_TYPE, scanType);
        intent.setClass(context, ScanActivity.class);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        int scanType = getIntent().getIntExtra(KEY_SCAN_TYPE, TYPE_SCANQR);
        toolbar = ViewUtils.getView(this, R.id.scan_toolbar);
        setToolbarNavigtion(toolbar, this);
        titleView = ViewUtils.getView(this, R.id.scan_toolbar_title);
        albumView = ViewUtils.getView(this, R.id.scan_album_view);
        albumView.setOnClickListener(this);
        qrLayout = ViewUtils.getView(this, R.id.scan_qr_layout);
        qrLayout.setOnClickListener(this);
        arLayout = ViewUtils.getView(this, R.id.scan_ar_layout);
        arLayout.setOnClickListener(this);
        qrImageView = ViewUtils.getView(this, R.id.scan_qr_view);
        arImageView = ViewUtils.getView(this, R.id.scan_ar_view);
        arTextView = ViewUtils.getView(this, R.id.scan_ar_text);
        qrTextView = ViewUtils.getView(this, R.id.scan_qr_text);
        unSelectTabColor = ContextCompat.getColor(this, R.color.white);
        selectTabColor = ContextCompat.getColor(this, R.color.SecretBlue);
        addScanTypeFragment(scanType);
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.scan_album_view) {
            selectPicture(true);
        } else if (i == R.id.scan_qr_layout) {
            addScanTypeFragment(TYPE_SCANQR);
        } else if (i == R.id.scan_ar_layout) {
            addScanTypeFragment(TYPE_SCANAR);
        }
    }

    private void addScanTypeFragment(int scanType) {
        if (curScanType == scanType && curScanType > 0) {
            return;
        }
        switch (scanType) {
            case TYPE_SCANAR:
                if (!isDetachFragment(qrCodeFragment)) {
                    qrCodeFragment.onStopCamera();
                }
                addFragmentContainer(getARCodeFragment(), ScanARCodeFragment.TAG, qrCodeFragment);
                qrTextView.setTextColor(unSelectTabColor);
                arTextView.setTextColor(selectTabColor);
                arImageView.setImageResource(R.drawable.icon_scan_tab_ar_select);
                qrImageView.setImageResource(R.drawable.icon_scan_tab_qr_unselect);
                titleView.setText("");
                break;
            case TYPE_SCANQR:
            default:
                String mCameraId = null;
                if (!isDetachFragment(arCodeFragment)) {
                    arCodeFragment.onTextureStop();
                    mCameraId = arCodeFragment.curCameraId();
                }
                addFragmentContainer(getQRCodeFragment(mCameraId), ScanQRCodeFragment.TAG, arCodeFragment);
                qrTextView.setTextColor(selectTabColor);
                arTextView.setTextColor(unSelectTabColor);
                arImageView.setImageResource(R.drawable.icon_scan_tab_ar_unselect);
                qrImageView.setImageResource(R.drawable.icon_scan_tab_qr_select);
                titleView.setText("");
                break;
        }
        curScanType = scanType;
    }

    private Fragment getARCodeFragment() {
        if (null == arCodeFragment) {
            arCodeFragment = ScanARCodeFragment.newInstance();
        }
        return arCodeFragment;
    }

    private Fragment getQRCodeFragment(String cameraId) {
        if (null == qrCodeFragment) {
            qrCodeFragment = ScanQRCodeFragment.newInstance(cameraId);
        } else {
            qrCodeFragment.setCameraId(cameraId);
        }
        return qrCodeFragment;
    }

    private void addFragmentContainer(Fragment fragment, String tag, Fragment hidden) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        if (null == hidden) {
            fragmentTransaction.add(R.id.scan_container, fragment, tag);
        } else {
            fragmentTransaction.hide(hidden);
            if (fragment.isAdded()) {
                fragmentTransaction.show(fragment);
            } else {
                fragmentTransaction.add(R.id.scan_container, fragment, tag);
            }
        }
        fragmentTransaction.commitAllowingStateLoss();

//        getSupportFragmentManager().beginTransaction()
//            .replace(R.id.scan_container, fragment, tag)
//            .commitAllowingStateLoss();
    }

    private void selectPicture(boolean isCheckPermission) {
        if (!isCheckPermission || CheckPermissionUtils.checkPermissions(this, externalPermission, PERMISSION_EXTERNAL_CODE)) {
            startActivityForResult(IntentUtils.getPictureIntent(), IMAGE_SELECT_CODE);
        }
    }

    public boolean canUseSwipeBackLayout() {
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isNeedCheck) {
            isNeedCheck = !CheckPermissionUtils.checkPermissions(this, cameraPermission, PERMISSION_CAMERA_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_EXTERNAL_CODE) {
            if (CheckPermissionUtils.verifyPermissions(grantResults)) {
                selectPicture(false);
            } else {
                showMissingPermissionDialog();
            }
        } else if (requestCode == PERMISSION_CAMERA_CODE) {
            if (!CheckPermissionUtils.verifyPermissions(grantResults)) {
                CheckPermissionUtils.showMissingPermissionDialog(this);
            } else {
                getPermissionCamera();
            }
            isNeedCheck = false;
        }
    }

    private void getPermissionCamera() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        int size = null == fragments ? 0 : fragments.size();
        for (int i = 0; i < size; i++) {
            Fragment fragment = fragments.get(i);
            String tag = isDetachFragment(fragment) ? "" : fragment.getTag();
            if (ScanARCodeFragment.TAG.equals(tag)) {
                ((ScanARCodeFragment) fragment).refreshGLView();
            } else if (ScanQRCodeFragment.TAG.equals(tag)) {
                ((ScanQRCodeFragment) fragment).refreshGLView();
            }
        }
    }

    private void showMissingPermissionDialog() {
        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle(getResources().getString(R.string.secret_open_permission)).setMessage(getResources().getString(R.string.secret_open_permission_tip2));
        alertDialog.setCancelable(false);
        alertDialog.setNegativeButton(getResources().getString(R.string.secret_permission_refuse), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        }).setPositiveButton(getResources().getString(R.string.secret_permission_allow), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SelfStartSetting.openAppDetailSetting(ScanActivity.this);
            }
        });
        AlertDialog dialog = alertDialog.create();
        dialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == IMAGE_SELECT_CODE) {
            picSelectResult(getSelectPicturePath(data));
        }
    }

    private void picSelectResult(String picPath) {
        if (TextUtils.isEmpty(picPath)) {
            return;
        }
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        int size = null == fragments ? 0 : fragments.size();
        for (int i = 0; i < size; i++) {
            Fragment fragment = fragments.get(i);
            String tag = isDetachFragment(fragment) ? "" : fragment.getTag();
            if (ScanARCodeFragment.TAG.equals(tag)) {
                ((ScanARCodeFragment) fragment).picHandleResult(picPath);
            } else if (ScanQRCodeFragment.TAG.equals(tag)) {
                new ScanActivity.DecodeAsyncTask(this).execute(picPath);
            }
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

    private class DecodeAsyncTask extends AsyncTask<String, Void, Result> {
        private final WeakReference<ScanActivity> weakActivity;

        public DecodeAsyncTask(ScanActivity activity) {
            weakActivity = new WeakReference<>(activity);
        }

        @Override
        protected Result doInBackground(String... params) {
            return QrCodeUtil.syncDecodeQRCode(params[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            ScanActivity activity = weakActivity.get();
            if (activity != null) {
                activity.showProgressDialog(R.string.on_recognizing);
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            super.onPostExecute(result);
            ScanActivity activity = weakActivity.get();
            if (activity != null) {
                activity.dismissProgressDialog();
                picHandleResult(result);
            }
        }
    }

    private void picHandleResult(Result result) {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        int size = null == fragments ? 0 : fragments.size();
        for (int i = 0; i < size; i++) {
            Fragment fragment = fragments.get(i);
            String tag = isDetachFragment(fragment) ? "" : fragment.getTag();
            if (ScanQRCodeFragment.TAG.equals(tag)) {
                ((ScanQRCodeFragment) fragment).handleResult(result);
            }
        }
    }

    private boolean isDetachFragment(Fragment fragment) {
        boolean isDetach = (null == fragment || !fragment.isAdded() || fragment.isDetached() || fragment.isHidden());
        LogUtils.i(TAG, "isDetachFragment isDetach:" + isDetach);
        return isDetach;
    }

    private boolean getFragmentDiscerning() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        int size = null == fragments ? 0 : fragments.size();
        for (int i = 0; i < size; i++) {
            Fragment fragment = fragments.get(i);
            String tag = isDetachFragment(fragment) ? "" : fragment.getTag();
            if (ScanARCodeFragment.TAG.equals(tag)) {
                return ((ScanARCodeFragment) fragment).isDiscerning();
            } else if (ScanQRCodeFragment.TAG.equals(tag)) {
                return false;
            }
        }
        return false;
    }

    private boolean getChildBackPressed() {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        int size = null == fragments ? 0 : fragments.size();
        if (size == 0) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            Fragment fragment = fragments.get(i);
            String tag = isDetachFragment(fragment) ? "" : fragment.getTag();
            if (ScanARCodeFragment.TAG.equals(tag)) {
                return ((ScanARCodeFragment) fragment).onBackPressed();
            } else if (ScanQRCodeFragment.TAG.equals(tag)) {
                return ((ScanQRCodeFragment) fragment).onBackPressed();
            }
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        boolean isChildBack = getChildBackPressed();
        if (!isChildBack) {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        arCodeFragment = null;
        qrCodeFragment = null;
    }
}
