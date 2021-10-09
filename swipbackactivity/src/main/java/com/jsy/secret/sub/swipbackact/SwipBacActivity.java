/**
 * Secret
 * Copyright (C) 2019 Secret
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
package com.jsy.secret.sub.swipbackact;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.customview.widget.ViewDragHelper;

import com.gyf.immersionbar.ImmersionBar;
import com.jsy.res.theme.ThemeUtils;
import com.jsy.secret.sub.swipbackact.swipbackview.SwipeBackLayout;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class SwipBacActivity extends AppCompatActivity implements SwipeBackLayout.SwipeListener {

    protected SwipeBackLayout mSwipeBackLayout;
    private boolean mOverrideExitAniamtion = false;

    public static final int SMOOTH_WIDTH = 20;

    public static final int MINI_PROGRAMS_SMOOTH_WIDTH = Math.max(Resources.getSystem().getDisplayMetrics().widthPixels / 6, dip2px(80));

    private boolean mIsFinishing;

    private final int ALPHA_COLOR = 0x60ffffff;
    private static final float MAX_TRANSLATIONX_OFFSET = 0.25f;

    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    @Override
    public void onContentChanged(){
        super.onContentChanged();
        try {
            if (enableWhiteStatusBar()) {
                ImmersionBar.with(this)
                    .fitsSystemWindows(fitsSystemWindows())
                    .statusBarColor(statusBarColor())
                    .statusBarDarkFont(statusBarDarkFont())
                    .navigationBarColor(navigationBarColor(), navigationBarAlpha())
                    .navigationBarDarkIcon(navigationBarDarkIcon())
                    .keyboardEnable(keyboardEnable())
                    .init();
            }
            else {
                customInitStatusBar();
            }
        } catch(Exception ex) {
          LogUtils.d("initStatusBar Exception:"+ex.getMessage());
        }
    }

    protected void setStatusBarColorSpecial(@ColorInt int statusBarColor,@ColorInt int navigationBarColor){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(statusBarColor);
            getWindow().setNavigationBarColor(navigationBarColor);
        }
        View decorView = getWindow().getDecorView();
        int uiFlag=View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(statusBarDarkFont()) {
                uiFlag |=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
            if(navigationBarDarkIcon()) {
                uiFlag |=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decorView.setSystemUiVisibility(uiFlag);
    }

    protected void setStatusBarColorInt(@ColorInt int color, boolean isDarkFont){
        ImmersionBar.with(this).statusBarColorInt(color).statusBarDarkFont(isDarkFont).init();
    }

    protected void initStatusBar(int titleBarId){
        initStatusBar(titleBarId,android.R.color.transparent);
    }
    protected void initStatusBar(@IdRes int titleBarId, @ColorRes int statusBarColor){
        ImmersionBar.with(this)
            .titleBar(titleBarId)
            .statusBarColor(statusBarColor)
            .navigationBarColor(navigationBarColor(), navigationBarAlpha())
            .navigationBarDarkIcon(navigationBarDarkIcon())
            .keyboardEnable(keyboardEnable())
            .init();
    }

    protected boolean enableWhiteStatusBar(){
        return true;
    }
    protected boolean fitsSystemWindows(){
        return true;
    }
    protected int statusBarColor(){
        if(ThemeUtils.isDarkTheme(this)) {
            return R.color.statusbar_dark;
        }
        else {
            return R.color.statusbar_light;
        }
    }
    protected int navigationBarColor(){
        if(ThemeUtils.isDarkTheme(this)) {
            return R.color.statusbar_dark;
        }
        else {
            return R.color.statusbar_light;
        }
    }
    protected float navigationBarAlpha(){
        return 0.5f;
    }
    protected boolean statusBarDarkFont(){
        return !ThemeUtils.isDarkTheme(this);
    }
    protected boolean navigationBarDarkIcon(){
        return !ThemeUtils.isDarkTheme(this);
    }
    protected boolean keyboardEnable(){
        return true;
    }
    protected void customInitStatusBar(){

    }

    public void setDecorViewTransparent() {
        setDecorViewTransparent(android.R.color.transparent);
    }

    public void setDecorViewTransparent(@DrawableRes int resId) {
        getWindow().getDecorView().findViewById(android.R.id.content).setBackgroundResource(resId);
    }

    /**
     * 8.0 bug
     *
     * @param requestedOrientation
     */
    @Override
    public void setRequestedOrientation(int requestedOrientation) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTranslucentOrFloating()) {
            LogUtils.i(this.getClass().getSimpleName(), "avoid calling setRequestedOrientation when Oreo.");
            return;
        }
        super.setRequestedOrientation(requestedOrientation);
    }

    private boolean isTranslucentOrFloating() {
        boolean isTranslucentOrFloating = false;
        try {
            int[] styleableRes = (int[]) Class.forName("com.android.internal.R$styleable").getField("Window").get(null);
            final TypedArray ta = obtainStyledAttributes(styleableRes);
            Method m = ActivityInfo.class.getMethod("isTranslucentOrFloating", TypedArray.class);
            m.setAccessible(true);
            isTranslucentOrFloating = (boolean) m.invoke(null, ta);
            m.setAccessible(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isTranslucentOrFloating;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTranslucentOrFloating()) {
            boolean result = fixOrientation();
            LogUtils.i(this.getClass().getSimpleName(), "onCreate fixOrientation when Oreo, result = " + result);
        }
        super.onCreate(savedInstanceState);

        ActivityContainner.add(this);
        //getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        //LogUtils.d(getClass().getSimpleName(),"isDarkTheme:"+isDarkTheme());
        if(ThemeUtils.isDarkTheme(this)){
            getWindow().getDecorView().findViewById(android.R.id.content).setBackgroundResource(android.R.color.black);
        }
        else {
            getWindow().getDecorView().findViewById(android.R.id.content).setBackgroundResource(android.R.color.white);
        }
        mSwipeBackLayout = new SwipeBackLayout(this);
        mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);
        mSwipeBackLayout.setEdgeSize(dip2px(SMOOTH_WIDTH));
        //mSwipeBackLayout.setScrimColor(ALPHA_COLOR);

        if (canUseSwipeBackLayout()) {
            mSwipeBackLayout.setEnableGesture(true);
        } else {
            mSwipeBackLayout.setEnableGesture(false);
        }
        mSwipeBackLayout.setSwipeListener(this);

    }

    private boolean fixOrientation() {
        try {
            Field field = Activity.class.getDeclaredField("mActivityInfo");
            field.setAccessible(true);
            ActivityInfo o = (ActivityInfo) field.get(this);
            o.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            field.setAccessible(false);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void setCanSwipeBack(boolean canSwipeBack) {

        this.canSwipeBack = canSwipeBack;
        mSwipeBackLayout.setEnableGesture(canSwipeBack);

    }


    public static int dip2px(float dpValue) {
        final float scale = Resources.getSystem().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }


    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (canUseSwipeBackLayout()) {
            if (mSwipeBackLayout != null) {
                mSwipeBackLayout.attachToActivity(this);
            }
        }
    }

    @Override
    public <T extends View> T findViewById(int id) {
        T v = super.findViewById(id);
        if (v != null)
            return v;
        if (canUseSwipeBackLayout()) {
            if (mSwipeBackLayout != null) {
                mSwipeBackLayout.findViewById(id);
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityContainner.remove(this);
        LogUtils.i(this.getClass().getSimpleName() + "==onDestroy==start");

    }


    /**
     * Override Exit Animation
     *
     * @param override
     */
    public void setOverrideExitAniamtion(boolean override) {
        if (canUseSwipeBackLayout()) {
            if (mSwipeBackLayout != null) {
                mOverrideExitAniamtion = override;
            }
        }
    }

    /**
     * Scroll out contentView and finish the activity
     */
    private void scrollToFinishActivity() {
        if (mSwipeBackLayout != null) {
            mSwipeBackLayout.scrollToFinishActivity();
        }
    }

    @Override
    public void finish() {
        if (canUseSwipeBackLayout()) {
            if (mOverrideExitAniamtion && !mIsFinishing) {
                scrollToFinishActivity();
                mIsFinishing = true;
                return;
            }
            mIsFinishing = false;
            onScrollStateChange(ViewDragHelper.STATE_IDLE,1.0f);
        }
        super.finish();
    }

    private boolean canSwipeBack = false;

    public boolean canUseSwipeBackLayout() {
        return canSwipeBack;
    }

    @Override
    public void onScrollStateChange(int state, float scrollPercent) {
        LogUtils.d("JACK8", "onScrollStateChange() called with: state = [" + state + "], scrollPercent = [" + scrollPercent + "]");
        if(scrollPercent>=0.0f && scrollPercent<=1.0f){
            Activity activity=ActivityContainner.getPenultimateActivity(SwipBacActivity.this);
            LogUtils.d("JACK8", "onScrollStateChange() called with: activity:"+activity);
            if (activity != null) {
                View decorView = activity.getWindow().getDecorView();
                decorView.setTranslationX(-decorView.getMeasuredWidth() * MAX_TRANSLATIONX_OFFSET * (1 - scrollPercent));
            }
        }
    }

    @Override
    public void onEdgeTouch(int edgeFlag) {
        LogUtils.d("JACK8", "onEdgeTouch() called with: edgeFlag = [" + edgeFlag + "]");
    }

    @Override
    public void onScrollOverThreshold() {
        LogUtils.d("JACK8", "onScrollOverThreshold() called");
    }
}
