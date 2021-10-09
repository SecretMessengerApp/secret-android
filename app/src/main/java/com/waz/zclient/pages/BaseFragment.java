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
package com.waz.zclient.pages;

import android.content.Context;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.ServiceContainer;
import com.waz.zclient.controllers.IControllerFactory;

public class BaseFragment<T> extends com.jsy.secret.sub.swipbackact.base.BaseFragment<T> implements ServiceContainer {
    private IControllerFactory controllerFactory;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof ServiceContainer) {
            controllerFactory = ((ServiceContainer) context).getControllerFactory();
        }
    }

    @Override
    public final IControllerFactory getControllerFactory() {
        return controllerFactory;
    }

    public <A> A inject(Class<A> dependencyClass) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity != null) {
            return activity.injectJava(dependencyClass);
        } else {
            return null;
        }
    }

    public void showProgressDialog(boolean cancelable) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(cancelable);
        }
    }

    public void showProgressDialog(String msg, boolean cancelable) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(msg, cancelable);
        }
    }

    public void showProgressDialog(String msg) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(msg);
        }
    }

    public void showProgressDialog(int resId, boolean cancelable) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(resId, cancelable);
        }
    }

    public void showProgressDialog(String msg,boolean cancelable,int resId,boolean needUpdateView){
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(msg,cancelable, resId,needUpdateView);
        }
    }

    public void showProgressDialog(int resId) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog(resId);
        }
    }

    public void showProgressDialog() {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showProgressDialog();
        }
    }

    public void dismissProgressDialog() {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).dismissProgressDialog();
        }
    }

    /**
     * @see #dismissProgressDialog()
     */
    @Deprecated
    public void closeProgressDialog() {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).closeProgressDialog();
        }
    }

    public boolean isShowingProgressDialog(){
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            return ((BaseActivity) getActivity()).isShowingProgressDialog();
        }else{
            return false;
        }
    }

    public void showToast(int msgResId) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showToast(msgResId);
        }
    }

    public void showToast(String msg) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showToast(msg);
        }
    }

    public void showToast(String msg, int duration) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showToast(msg, duration);
        }
    }

    public void showToast(String msg, int duration, int gravity) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            ((BaseActivity) getActivity()).showToast(msg, duration, gravity);
        }
    }

    public boolean dealResponseError(int code) {
        if (getActivity() != null && getActivity() instanceof BaseActivity) {
            return ((BaseActivity) getActivity()).dealResponseError(code);
        }
        return false;
    }

}
