/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.views;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Patterns;
import android.webkit.*;
import com.jsy.common.model.QrCodeContentModel;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.utils.IntentUtils;

import java.io.File;
import java.util.Arrays;

public class OpenUrlWebView extends WebView {

    private final static String TAG = OpenUrlWebView.class.getSimpleName();
    private final static int FILE_CHOOSER_RESULT_CODE = 10000;
    private Context mContext;
    private ValueCallback<Uri[]> mFilePathCallback;
    private OnWebViewCallBack webViewCallBack;
    private String mCameraFilePath, mVideoFilePath;

    public OpenUrlWebView(Context context) {
        super(context);
        initView(context);
    }

    public OpenUrlWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public OpenUrlWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    public OpenUrlWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView(context);
    }

    public void setOnWebViewCallBack(OnWebViewCallBack webViewCallBack) {
        this.webViewCallBack = webViewCallBack;
    }

    public void initView(Context context) {
        this.mContext = context;
        initWebViewSettings();
        setWebViewListener();
    }

    public void initWebViewSettings() {
        final WebSettings settings = getSettings();
        settings.setDefaultTextEncodingName("utf-8");

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);

        settings.setLoadsImagesAutomatically(true);
        settings.setAppCacheEnabled(false);
        settings.setTextZoom(100);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        setHapticFeedbackEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setVerticalScrollBarEnabled(false);
    }

    private void setWebViewListener() {
        this.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = null == request ? null : request.getUrl();
                String url = null == uri ? "" : uri.toString();
                WebView.HitTestResult hitTestResult = view.getHitTestResult();
                boolean isLink = !TextUtils.isEmpty(url) && Patterns.WEB_URL.matcher(url).find();
                LogUtils.d(TAG, "shouldOverrideUrlLoading hitTestResult.getType(" + (null == hitTestResult ? "null" : ("isLink:" + isLink + " ,linkType:" + hitTestResult.getType() + ", linkExtra:" + hitTestResult.getExtra())) + ")==url:" + url);
                if (TextUtils.isEmpty(url)) {
                    return super.shouldOverrideUrlLoading(view, request);
                }
                if (isLink) {
                    view.loadUrl(url);
                    return true;
                } else {
                    int linkType = null == hitTestResult ? WebView.HitTestResult.UNKNOWN_TYPE : hitTestResult.getType();
                    switch (linkType) {
                        case WebView.HitTestResult.EDIT_TEXT_TYPE:
                            break;
                        case WebView.HitTestResult.EMAIL_TYPE:
                            break;
                        case WebView.HitTestResult.PHONE_TYPE:
                            Intent intent = new Intent();
                            intent.setAction(Intent.ACTION_CALL);
                            intent.setData(uri);
                            mContext.startActivity(intent);
                            break;
                        case WebView.HitTestResult.GEO_TYPE:
                        case WebView.HitTestResult.IMAGE_TYPE:
                        case WebView.HitTestResult.SRC_ANCHOR_TYPE:
                        case WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE:
                        case WebView.HitTestResult.UNKNOWN_TYPE:
                        default:
                            try {
                                Intent uriIntent = new Intent(Intent.ACTION_VIEW, uri);
                                mContext.startActivity(uriIntent);
                            } catch (Exception e) {
                                e.printStackTrace();
                                LogUtils.e(TAG, "shouldOverrideUrlLoading Exception e(" + e.getMessage() + ")==url:" + url);
                            }
                            break;
                    }
                    return true;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (null != webViewCallBack) {
                    webViewCallBack.onOpenUrlProgress(0);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (null != webViewCallBack) {
                    webViewCallBack.onOpenUrlProgress(100);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (null != webViewCallBack) {
                    webViewCallBack.onOpenUrlProgress(100);
                }
            }

        });
        this.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (null != webViewCallBack) {
                    webViewCallBack.onOpenUrlProgress(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (null != webViewCallBack) {
                    webViewCallBack.onOpenUrlTitle(title);
                }
            }

            //For Android >= 5.0
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                return webViewFileChooser(filePathCallback, fileChooserParams);
            }

        });
        this.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                webViewDownload(url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    private void webViewDownload(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
        LogUtils.d(TAG, "webViewDownload==contentDisposition:" + contentDisposition + " ,mimetype:" + mimetype + " ,contentLength:" + contentLength + " ,userAgent(:" + userAgent + "),==url :" + url);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setData(Uri.parse(url));
        mContext.startActivity(intent);
    }

    private boolean webViewFileChooser(ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
        LogUtils.d(TAG, "webViewFileChooser==fileChooserParams:(" + (null == fileChooserParams ? "null" : (fileChooserParams.getFilenameHint() + ", getAcceptTypes:" +
            Arrays.toString(fileChooserParams.getAcceptTypes()) + ", getMode:" +
            fileChooserParams.getMode() + ",getTitle :" +
            fileChooserParams.getTitle() + ",isCaptureEnabled :" +
            fileChooserParams.isCaptureEnabled() + ",createIntent :" +
            fileChooserParams.createIntent())) + ")"
        );
        if (mFilePathCallback != null) {
            mFilePathCallback.onReceiveValue(null);
            mFilePathCallback = null;
        }
        if (null == webViewCallBack) {
            filePathCallback.onReceiveValue(null);
            return false;
        }
        this.mFilePathCallback = filePathCallback;

        CharSequence titleStr = null == fileChooserParams ? "" : fileChooserParams.getTitle();

        Intent fileIntent = null == fileChooserParams ? null : fileChooserParams.createIntent();
        if (null == fileIntent) {
            fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
            fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            fileIntent.setType("*/*");
        }
        if (fileChooserParams.getAcceptTypes() != null)
            fileIntent.putExtra(Intent.EXTRA_MIME_TYPES, fileChooserParams.getAcceptTypes());

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        mCameraFilePath = CircleConstant.SAVE_IMG_PATH + File.separator + "Camera_" + System.currentTimeMillis() + ".jpg";
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, IntentUtils.getFileUri(mContext, new File(mCameraFilePath)));

        Intent videoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        mVideoFilePath = CircleConstant.SAVE_IMG_PATH + File.separator + "Video_" + System.currentTimeMillis() + ".mp4";
        videoIntent.putExtra(MediaStore.EXTRA_OUTPUT, IntentUtils.getFileUri(mContext, new File(mVideoFilePath)));

        Intent chooser = new Intent(Intent.ACTION_CHOOSER);
        chooser.putExtra(Intent.EXTRA_TITLE, TextUtils.isEmpty(titleStr) ? "File Chooser" : titleStr);
        chooser.putExtra(Intent.EXTRA_INTENT, fileIntent);
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent, videoIntent});
        webViewCallBack.onWebViewFileChooser(chooser, FILE_CHOOSER_RESULT_CODE);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void webViewFileChooserResult(int requestCode, int resultCode, Intent data) {
        LogUtils.d(TAG, "webViewFileChooserResult=start=requestCode:" + requestCode + "==resultCode==" + resultCode);
        if (requestCode != FILE_CHOOSER_RESULT_CODE /*|| null == mFilePathCallback*/) {
            return;
        }

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && null != data) {
            ClipData clipData = data.getClipData();
            int itemCount = null == clipData ? 0 : clipData.getItemCount();
            results = itemCount > 0 ? new Uri[itemCount] : null;
            for (int i = 0; i < itemCount; i++) {
                ClipData.Item item = clipData.getItemAt(i);
                results[i] = item.getUri();
            }
            String dataString = data.getDataString();
            if (!TextUtils.isEmpty(dataString)) {
                results = new Uri[]{Uri.parse(dataString)};
            }
        } else if (resultCode == Activity.RESULT_OK) {
            File cameraFile = TextUtils.isEmpty(mCameraFilePath) ? null : new File(mCameraFilePath);
            File videoFile = TextUtils.isEmpty(mVideoFilePath) ? null : new File(mVideoFilePath);
            File file = null != cameraFile && cameraFile.exists() ? cameraFile : videoFile;
            if (null != file && file.exists()) {
                results = new Uri[]{IntentUtils.getFileUri(mContext, file)};
            }
        }

//        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);

        LogUtils.d(TAG, "webViewFileChooserResult=end=results:" + Arrays.toString(results));

        mFilePathCallback.onReceiveValue(results);
        mFilePathCallback = null;
        mCameraFilePath = null;
        mVideoFilePath = null;
    }

    @Override
    public void destroy() {
        super.destroy();
        webViewCallBack = null;
        if (null != mFilePathCallback) {
            mFilePathCallback.onReceiveValue(null);
        }
        mFilePathCallback = null;
        mCameraFilePath = null;
        mVideoFilePath = null;
    }

    public interface OnWebViewCallBack {
        void onOpenUrlProgress(int progress);

        void onOpenUrlTitle(String title);

        void onWebViewFileChooser(Intent intent, int requestCode);
    }
}
