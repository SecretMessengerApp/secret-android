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
package com.waz.zclient.router;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.StyleRes;

import com.jsy.common.utils.dynamiclanguage.DynamicLanguageContextWrapper;
import com.jsy.res.theme.ThemeUtils;
import com.jsy.secret.sub.swipbackact.SwipBacActivity;
import com.jsy.secret.sub.swipbackact.router.BaseToMainActivityRouter;
import com.alibaba.android.arouter.facade.annotation.Route;
import com.jsy.secret.sub.swipbackact.utils.RouterKeyUtil;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.Constants;

@Route(path = RouterKeyUtil.ROUTER_BASETOMAIN)
public class BaseToMainActivityImpl implements BaseToMainActivityRouter {
    private final static String TAG = BaseToMainActivityImpl.class.getSimpleName();
    private SwipBacActivity mActivity;
    private JavaToScalaOperate javaToScalaOperate;

    @Override
    public void init(Context context) {
        JavaToScalaOperate javaToScalaOperate = getJavaToScalaOperate();
        LogUtils.i(TAG, "init:" + context + ",javaToScalaOperate:" + javaToScalaOperate);
    }

    private JavaToScalaOperate getJavaToScalaOperate() {
        if (javaToScalaOperate == null) {
            javaToScalaOperate = new JavaToScalaOperate();
        }
        return javaToScalaOperate;
    }

    private BroadcastReceiver themeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (null != mActivity && (Constants.ACTION_CHANGE_THEME.equals(action) || Constants.ACTION_CHANGE_LANGUAGE.equals(action))) {
                mActivity.recreate();
            }
        }
    };


    @Override
    public void onBaseActivityCreate(SwipBacActivity activity, boolean isTheme) {
        if (isTheme) {
            mActivity = activity;
            activity.setTheme(getBaseTheme(activity));
            IntentFilter intentFilter = new IntentFilter(Constants.ACTION_CHANGE_THEME);
            intentFilter.addAction(Constants.ACTION_CHANGE_LANGUAGE);
            activity.registerReceiver(themeReceiver, intentFilter);
        }
    }

    @StyleRes
    private int getBaseTheme(SwipBacActivity activity) {
        if (!ThemeUtils.isDarkTheme(activity)) {
            if (activity.canUseSwipeBackLayout()) {
                return com.waz.zclient.R.style.SecretAppThemeLight0;
            } else {
                return com.waz.zclient.R.style.SecretAppThemeLight1;
            }
        } else {
            if (activity.canUseSwipeBackLayout()) {
                return com.waz.zclient.R.style.SecretAppThemeDark0;
            } else {
                return com.waz.zclient.R.style.SecretAppThemeDark1;
            }
        }
    }

    @Override
    public void onBaseActivityStart(SwipBacActivity activity) {
        getJavaToScalaOperate().onBaseActivityStart(activity);
    }

    @Override
    public void onBaseActivityResume(SwipBacActivity activity) {
        getJavaToScalaOperate().onBaseActivityResume(activity);
    }

    @Override
    public Context getActivityNewBase(Context newBase) {
        return DynamicLanguageContextWrapper.updateContext(newBase);
    }

    @Override
    public void onBaseActivityPause(SwipBacActivity activity) {
    }

    @Override
    public void onBaseActivityStop(SwipBacActivity activity) {
        getJavaToScalaOperate().onBaseActivityStop(activity);
    }

    @Override
    public void onBaseActivityDestroy(SwipBacActivity activity, boolean isTheme) {
        if (isTheme) {
            activity.unregisterReceiver(themeReceiver);
        }
    }
}
