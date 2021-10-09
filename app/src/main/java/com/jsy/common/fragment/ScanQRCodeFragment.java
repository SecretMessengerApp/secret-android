/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.jsy.common.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.Result;
import com.jsy.common.acts.OpenUrlActivity;
import com.jsy.common.acts.SendConnectRequestActivity;
import com.jsy.common.httpapi.ImApiConst;
import com.jsy.common.httpapi.OnHttpListener;
import com.jsy.common.httpapi.SpecialServiceAPI;
import com.jsy.common.model.HttpResponseBaseModel;
import com.jsy.common.model.QrCodeContentModel;
import com.jsy.common.model.SearchUserInfo;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.BaseScalaFragment;
import com.waz.zclient.R;
import com.waz.zclient.common.controllers.SoundController;
import com.waz.zclient.google_verificaiton_ui.view.JoinGroupConfirmDialog;
import com.waz.zclient.utils.SpUtils;
import com.waz.zclient.utils.StringUtils;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.List;

import me.dm7.barcodescanner.zxing.ZXingScannerView;


public class ScanQRCodeFragment extends BaseScalaFragment implements ZXingScannerView.ResultHandler {
    public static final String TAG = ScanQRCodeFragment.class.getSimpleName();
    private static final String CAMERA_ID = "CAMERA_ID";

    public static ScanQRCodeFragment newInstance(String cameraId) {
        ScanQRCodeFragment fragment = new ScanQRCodeFragment();
        Bundle bundle = new Bundle();
        bundle.putString(CAMERA_ID, cameraId);
        fragment.setArguments(bundle);
        return fragment;
    }

    private View rootView;
    //    private FrameLayout containerLayout;
    private ZXingScannerView scannerView;
    private boolean isStopCamera = false;
    private String mCameraId;

    public String getCameraId() {
        return mCameraId;
    }

    public void setCameraId(String cameraId) {
        this.mCameraId = cameraId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        mCameraId = null != bundle ? bundle.getString(CAMERA_ID) : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        } else {
            rootView = inflater.inflate(R.layout.fragment_scan_qrcode, container, false);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
//        containerLayout = view.findViewById(R.id.zxing_container);
//        containerLayout.removeAllViews();
        scannerView = view.findViewById(R.id.zxing_scan_view);
//        scannerView = new ZXingScannerView(getActivity()) {
//            @Override
//            protected IViewFinder createViewFinderView(Context context) {
//                return super.createViewFinderView(context);
//            }
//        };
//        containerLayout.addView(scannerView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void refreshGLView() {
        LogUtils.i(TAG, "refreshGLView 111 isStopCamera:" + isStopCamera + ",mCameraId:" + mCameraId);
        if (null != scannerView) {
            isStopCamera = false;
            scannerView.setResultHandler(this);
            int cameraId = -1;
            try {
                cameraId = TextUtils.isEmpty(mCameraId) ? -1 : Integer.valueOf(mCameraId);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            if (cameraId < 0) {
                scannerView.startCamera();
            } else {
                scannerView.startCamera(cameraId);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshGLView();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            onStopCamera();
        } else {
            refreshGLView();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        onStopCamera();
    }

    @Override
    public void handleResult(Result result) {
        String text = result == null ? "" : result.getText();
        LogUtils.i(TAG, "handleResult result:" + text);
        SoundController ctrl = (SoundController) inject(SoundController.class);
        if (ctrl != null) {
            ctrl.playPingFromMe(false);
        }

        if (!StringUtils.isBlank(text)) {
            checkParseContent(text);
        } else {
            Toast.makeText(getActivity(), getResources().getString(R.string.invalid_qr_code), Toast.LENGTH_SHORT).show();
            resumeCameraPreview();
        }

    }

    private void resumeCameraPreview() {
        refreshGLView();
//        if (null != scannerView) {
//            isStopCamera = false;
//            scannerView.resumeCameraPreview(this);
//            scannerView.setAutoFocus(true);
//        }
    }

    public void stopCameraPreview() {
        if (null != scannerView && !isStopCamera) {
            isStopCamera = true;
            scannerView.stopCameraPreview();
        }
    }

    private void invalidToast() {
        showToast(getResources().getString(R.string.invalid_qr_code));
        resumeCameraPreview();
    }

    private void checkParseContent(String content) {
        QrCodeContentModel qrCodeContentModel = QrCodeContentModel.parseQrCodeForContent(content);
        if (null == qrCodeContentModel) {
            invalidToast();
            return;
        }
        String type = TextUtils.isEmpty(qrCodeContentModel.getType()) ? "" : qrCodeContentModel.getType().toLowerCase().trim();
        if (QrCodeContentModel.SECRET_LOGIN_PREFIX.equalsIgnoreCase(type) && !TextUtils.isEmpty(qrCodeContentModel.getLoginKey())) {
            loginWeb(qrCodeContentModel.getLoginKey());
        } else if (QrCodeContentModel.GROUP_URL.equalsIgnoreCase(type)) {
            joinGroup(qrCodeContentModel.getUrl());
        } else if (QrCodeContentModel.TYPE_FRIEND.equalsIgnoreCase(type)) {
            addFriend(QrCodeContentModel.QRCODE_TYPE_USER, qrCodeContentModel.getUserId(), qrCodeContentModel.getUserName(), qrCodeContentModel.getHandle(), qrCodeContentModel.getPicture());
        }else {
            String[] fields = qrCodeContentModel.getFields();
            String[] uriPaths = qrCodeContentModel.getUriPaths();
            String[] uriQuerys = qrCodeContentModel.getUriQuerys();
            int fieldLength = null == fields ? 0 : fields.length;
            int pathLength = null == uriPaths ? 0 : uriPaths.length;
            int queryLength = null == uriQuerys ? 0 : uriQuerys.length;
            LogUtils.d(TAG, "checkParseContent==type=" + type
                + "=pageData：" + Arrays.toString(fields)
                + "=uriPaths：" + Arrays.toString(uriPaths)
                + "=uriQuerys：" + Arrays.toString(uriQuerys));
            if (QrCodeContentModel.QRCODE_TYPE_LOGIN.equalsIgnoreCase(type) && pathLength > 0) {
                loginWeb(uriPaths[0]);
            } else if (QrCodeContentModel.QRCODE_TYPE_USER.equalsIgnoreCase(type) && pathLength > 0) {
                reqUserExtIdFor(type, uriPaths[0]);
            } else if (QrCodeContentModel.QRCODE_TYPE_GROUP.equalsIgnoreCase(type) && queryLength > 0) {
                joinGroup(uriQuerys[0]);
            } else if (qrCodeContentModel.isLinkUrl()) {
                OpenUrlActivity.startSelf(getActivity(), qrCodeContentModel.getQrContent());
                getActivity().finish();
            } else {
                invalidToast();
            }
        }
    }

    private void reqUserExtIdFor(final String type, String extid) {
        LogUtils.d(TAG, "reqUserKeyFor==type=" + type + "=userKey：" + extid);
        if (StringUtils.isBlank(extid)) {
            invalidToast();
            return;
        }
        scannerView.stopCamera();
        showProgressDialog(R.string.secret_data_loading);
        SpecialServiceAPI.getInstance().get(String.format(ImApiConst.SCAN_REQ_USER, extid), null, false, new OnHttpListener<SearchUserInfo>() {

            @Override
            public void onFail(int code, String err) {
                LogUtils.d(TAG, "reqUserExtIdFor=code=" + code + "=err：" + err);
                if (isAdded()) {
                    dismissProgressDialog();
                    resumeCameraPreview();
                    showToast(R.string.invalid_qr_code_error);
                }
            }

            @Override
            public void onSuc(SearchUserInfo userInfo, String orgJson) {
                LogUtils.d(TAG, "reqUserExtIdFor==object==orgJson=" + orgJson);
                if (isAdded()) {
                    dismissProgressDialog();
                    if (null != userInfo) {
                        addFriend(type, userInfo.getId(), userInfo.getName(), userInfo.getHandle(), userInfo.userAvatar());
                    } else {
                        showToast(getResources().getString(R.string.secret_response_code_503));
                        getActivity().finish();
                    }
                }
            }

            @Override
            public void onSuc(List<SearchUserInfo> r, String orgJson) {
                LogUtils.d(TAG, "reqUserExtIdFor==list==orgJson=" + orgJson);
                if (isAdded()) {
                    dismissProgressDialog();
                }
            }
        });
    }

    private void addFriend(String type, String userId, String userName, String handle, String picture) {
        LogUtils.d(TAG, "addFriend==type=" + type + "=userId：" + userId);
        if (!TextUtils.isEmpty(userId) && userId.equals(SpUtils.getUserId(getActivity()))) {
            showToast(getResources().getString(R.string.invalid_qr_code_self));
            resumeCameraPreview();
        } else if (QrCodeContentModel.QRCODE_TYPE_USER.equalsIgnoreCase(type)) {
            SendConnectRequestActivity.startSelf(userId, getActivity(), true, null);
            getActivity().finish();
        } else {
            invalidToast();
        }
    }

    private void joinGroup(String groupUrl) {
        int length = StringUtils.isBlank(groupUrl) ? 0 : groupUrl.length();
        final String groupId = length >= 10 ? groupUrl.substring(groupUrl.length() - 10) : "";
        LogUtils.d(TAG, "joinGroup==groupId=" + groupId + "=groupUrl：" + groupUrl);
        if (StringUtils.isBlank(groupId)) {
            invalidToast();
            return;
        }
        if (!isAdded()) {
            return;
        }
        JoinGroupConfirmDialog dialog = new JoinGroupConfirmDialog(getActivity());
        dialog.show();
        dialog.setBtnClickListener(new JoinGroupConfirmDialog.BtnClickListener() {
            @Override
            public void onBtnClick() {
                String urlPath = new StringBuilder()
                    .append("/conversations/").append(groupId).append("/join_invite").toString();
                showProgressDialog(R.string.secret_data_loading);
                SpecialServiceAPI.getInstance().post(urlPath, "", new OnHttpListener<HttpResponseBaseModel>() {
                    @Override
                    public void onFail(int code, String err) {
                        LogUtils.d(TAG, "reqjoinGroup=code=" + code + "=err：" + err);
                        showToast(R.string.invalid_qr_code_error);
                        if (isAdded()) {
                            dismissProgressDialog();
                            resumeCameraPreview();
                        }
                    }

                    @Override
                    public void onSuc(HttpResponseBaseModel serializable, String orgJson) {
                        LogUtils.d(TAG, "reqjoinGroup==object==orgJson=" + orgJson);
                        //{"data":{"conv":"1bddf284-cc4c-454c-a3b2-9c70a4085aa0"},"msg":"already in convsation","code":2001}
                        if (isAdded()) {
                            dismissProgressDialog();

                            try {
                                JSONObject orgObj = new JSONObject(orgJson);
                                String code = orgObj.optString("code");
                                if (!StringUtils.isBlank(code) && "2002".equalsIgnoreCase(code)) {
                                    showToast(R.string.conversation_join_group_closed);
                                } else {
                                    showToast(R.string.content__system__you_added_suc);
                                }
                            } catch (Exception e) {
                                showToast(R.string.content__system__you_added_suc);
                                getActivity().finish();
                            }

                            getActivity().finish();
                        }
                    }

                    @Override
                    public void onSuc(List<HttpResponseBaseModel> r, String orgJson) {
                        LogUtils.d(TAG, "reqjoinGroup==list==orgJson=" + orgJson);
                        if (isAdded()) {
                            dismissProgressDialog();
                            showToast(R.string.content__system__you_added_suc);
                            getActivity().finish();
                        }
                    }
                });
            }

            @Override
            public void onDismiss() {
                if (null != scannerView) {
                    resumeCameraPreview();
                }
            }
        });
    }

    private void loginWeb(String loginKey) {
        LogUtils.d(TAG, "loginWeb==loginKey=" + loginKey);
        if (StringUtils.isBlank(loginKey)) {
            invalidToast();
            return;
        }
        scannerView.stopCamera();
        showProgressDialog(R.string.secret_data_loading);
        SpecialServiceAPI.getInstance().put(ImApiConst.SCAN_LOGIN, loginKey, new OnHttpListener<HttpResponseBaseModel>() {
            @Override
            public void onFail(int code, String err) {
                LogUtils.d(TAG, "reqloginWeb=code=" + code + "=err：" + err);
                if (isAdded()) {
                    dismissProgressDialog();
                    resumeCameraPreview();
                    showToast(R.string.secret_scan_login_failure);
                }
            }

            @Override
            public void onSuc(HttpResponseBaseModel serializable, String orgJson) {
                LogUtils.d(TAG, "reqloginWeb==object==orgJson=" + orgJson);
                if (isAdded()) {
                    dismissProgressDialog();
                    showToast(R.string.secret_scan_login_successful);
                    getActivity().finish();
                }
            }

            @Override
            public void onSuc(List<HttpResponseBaseModel> r, String orgJson) {
                LogUtils.d(TAG, "reqloginWeb==list==orgJson=" + orgJson);
                if (isAdded()) {
                    dismissProgressDialog();
                    showToast(R.string.secret_scan_login_successful);
                    getActivity().finish();
                }
            }
        });
    }

    public boolean onBackPressed() {
        getActivity().finish();
        return true;
    }

    public void onStopCamera() {
        if (null != scannerView && !isStopCamera) {
            LogUtils.i(TAG, "onStopCamera 111 isStopCamera:" + isStopCamera);
            isStopCamera = true;
            scannerView.stopCamera();
        }
    }

    @Override
    public void onDestroyView() {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        }
        super.onDestroyView();
//        onStopCamera();
    }
}
