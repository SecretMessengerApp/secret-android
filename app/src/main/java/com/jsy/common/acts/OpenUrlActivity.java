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
package com.jsy.common.acts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import com.jsy.common.views.OpenUrlWebView;
import com.jsy.secret.sub.swipbackact.SwipBacActivity;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.GlyphTextView;
import com.waz.zclient.ui.text.TypefaceTextView;

public class OpenUrlActivity extends SwipBacActivity {

    private final String TAG = OpenUrlActivity.class.getSimpleName();

    private final static String INTENT_KET_url = "url";

    public static void startSelf(Context context, String url) {
        Intent intent = new Intent(context, OpenUrlActivity.class)
            .putExtra(INTENT_KET_url, url);
        context.startActivity(intent);
    }

    private Toolbar toolbar;
    private TypefaceTextView urlTitleText;
    private GlyphTextView closeBtn;
    private ProgressBar mProgressBar;
    private OpenUrlWebView openUrlWebView;

    private String url;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            url = savedInstanceState.getString(INTENT_KET_url);
        } else {
            url = getIntent().getStringExtra(INTENT_KET_url);
        }
        LogUtils.d(TAG, "url:" + url);

        setContentView(R.layout.activity_open_url);
        initViewAndListener();

        if (!TextUtils.isEmpty(url)) {
            openUrlWebView.loadUrl(url);
        } else {
            this.finish();
        }
    }

    private void initViewAndListener() {
        toolbar = findViewById(R.id.open_url_tool);
        urlTitleText = findViewById(R.id.tvOpenUrlTitle);
        openUrlWebView = findViewById(R.id.openUrlWebView);
        closeBtn = findViewById(R.id.close_button);
        mProgressBar = findViewById(R.id.openurl_progressbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenUrlActivity.this.finish();
            }
        });
        openUrlWebView.setOnWebViewCallBack(new OpenUrlWebView.OnWebViewCallBack() {
            @Override
            public void onOpenUrlProgress(int progress) {
                if (progress >= 90) {
                    if (mProgressBar.getVisibility() != View.GONE) {
                        mProgressBar.setVisibility(View.GONE);
                    }
                } else if (progress >= 0) {
                    mProgressBar.setProgress(progress);
                    if (mProgressBar.getVisibility() != View.VISIBLE) {
                        mProgressBar.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onOpenUrlTitle(String title) {
                urlTitleText.setText(TextUtils.isEmpty(title) ? "" : title);
            }

            @Override
            public void onWebViewFileChooser(Intent intent, int requestCode) {
                OpenUrlActivity.this.startActivityForResult(intent, requestCode);
            }
        });
    }

    @Override
    public boolean canUseSwipeBackLayout() {
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(INTENT_KET_url, url);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (null != openUrlWebView) {
            openUrlWebView.webViewFileChooserResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (openUrlWebView != null && openUrlWebView.canGoBack()) {
            openUrlWebView.goBack();
        } else {
            super.onBackPressed();

        }
//            super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != openUrlWebView) {
            openUrlWebView.destroy();
        }
        if (null != mProgressBar) {
            mProgressBar.setVisibility(View.GONE);
        }
    }
}
