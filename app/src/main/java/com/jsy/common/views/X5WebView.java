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
package com.jsy.common.views;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.viewpager.widget.ViewPager;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.BuildConfig;


public class X5WebView extends WebView {

    private ViewPager viewPager = null;

    private WebViewClient client = new WebViewClient() {

        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    public X5WebView(Context arg0, AttributeSet arg1) {
        super(arg0, arg1);
        this.setWebViewClient(client);
        // this.setWebChromeClient(chromeClient);
        // WebStorage webStorage = WebStorage.getInstance();
        initWebViewSettings();
        this.setClickable(true);
    }

    private void initWebViewSettings() {
        WebSettings webSetting = this.getSettings();
        webSetting.setJavaScriptEnabled(true);
        webSetting.setJavaScriptCanOpenWindowsAutomatically(true);
        webSetting.setAllowFileAccess(true);
        webSetting.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webSetting.setSupportZoom(true);
        webSetting.setBuiltInZoomControls(false);
        webSetting.setUseWideViewPort(true);
        webSetting.setSupportMultipleWindows(true);
        // webSetting.setLoadWithOverviewMode(true);
        webSetting.setAppCacheEnabled(true);
        // webSetting.setDatabaseEnabled(true);
        webSetting.setDomStorageEnabled(true);
        webSetting.setGeolocationEnabled(true);
        webSetting.setAppCacheMaxSize(Long.MAX_VALUE);
        // webSetting.setPageCacheCapacity(IX5WebSettings.DEFAULT_CACHE_CAPACITY);
        webSetting.setPluginState(WebSettings.PluginState.ON_DEMAND);
        // webSetting.setRenderPriority(WebSettings.RenderPriority.HIGH);
        webSetting.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webSetting.setAllowUniversalAccessFromFileURLs(true);
        webSetting.setAllowFileAccessFromFileURLs(true);
    }

    public X5WebView(Context arg0) {
        super(arg0);
        if (BuildConfig.DEBUG) {
            setBackgroundColor(85621);
        }
    }

    private boolean canIntercept=false;
    public void setCanIntercept(boolean canIntercept){
        this.canIntercept=canIntercept;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        post(() -> {
            ViewParent viewParent = getParent();
            while (viewParent != null) {
                if (viewParent instanceof ViewPager) {
                    viewPager = (ViewPager)viewParent;
                    break;
                } else {
                    viewParent = viewParent.getParent();
                }
            }
            LogUtils.d("X5WebView", "onFinishInflate,viewPager:" + viewPager);
        });
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(viewPager!=null && canIntercept) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                viewPager.requestDisallowInterceptTouchEvent(true);
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if(viewPager!=null && canIntercept) {
            if (clampedX) {
                viewPager.requestDisallowInterceptTouchEvent(false);
            }
        }
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }
}
