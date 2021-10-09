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
package com.jsy.secret.sub.swipbackact.base;

import android.content.Context;
import android.os.Bundle;

import com.alibaba.android.arouter.launcher.ARouter;
import com.jsy.secret.sub.swipbackact.SwipBacActivity;
import com.jsy.secret.sub.swipbackact.router.BaseToMainActivityRouter;
import com.jsy.secret.sub.swipbackact.utils.RouterKeyUtil;

public abstract class BaseActivity extends SwipBacActivity {

    private BaseToMainActivityRouter mBaseToMainRouter;

    protected abstract boolean isExtendScalaBase();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityCreate(this, isSetTheme());
        }
    }

    public boolean isSetTheme() {
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityStart(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityResume(this);
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        if (!isExtendScalaBase()) {
            super.attachBaseContext(getBaseToMainRouter().getActivityNewBase(newBase));
        } else {
            super.attachBaseContext(newBase);
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityPause(this);
        }
    }

    @Override
    protected void onStop() {
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityStop(this);
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isExtendScalaBase()) {
            getBaseToMainRouter().onBaseActivityDestroy(this, isSetTheme());
        }
    }

    protected BaseToMainActivityRouter getBaseToMainRouter() {
        if (null == mBaseToMainRouter) {
            mBaseToMainRouter = (BaseToMainActivityRouter) ARouter.getInstance().build(RouterKeyUtil.ROUTER_BASETOMAIN).navigation();
        }
        return mBaseToMainRouter;
    }
}
