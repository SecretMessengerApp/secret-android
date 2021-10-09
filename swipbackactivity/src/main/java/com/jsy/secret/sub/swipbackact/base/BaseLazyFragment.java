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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public abstract class BaseLazyFragment<T> extends BaseFragment<T> {
    protected boolean isSetUserVisibleHint = false;
    protected boolean isVisibleToUser = false;
    protected boolean isInitView = false;
    protected boolean isLazyData = false;
    protected boolean isForeground = false;
    private View rootView;

    @Override
    protected void onPostAttach(Context context) {
        super.onPostAttach(context);
        LogUtils.i(this.getClass().getSimpleName() + "==onPostAttach==");
    }

    @LayoutRes
    protected abstract int getContentViewId();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        } else {
            rootView = inflater.inflate(getContentViewId(), container, false);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view, savedInstanceState);
        isInitView = true;
    }

    protected abstract void initView(@NonNull View view, @Nullable Bundle savedInstanceState);

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        isSetUserVisibleHint = true;
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (isInitView && !isLazyData) {
                lazyInitData();
                isForeground = true;
                isLazyData = true;
            } else if (isInitView && isLazyData) {
                onHiddenChanged(false);
            }
        } else {
            if (isInitView && isLazyData) {
                onHiddenChanged(true);
            }
        }
        this.isVisibleToUser = isVisibleToUser;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isSetUserVisibleHint) {
            if (isVisibleToUser && isInitView && isLazyData) {
                onHiddenChanged(false);
            }
        } else {
            if (isInitView && !isLazyData) {
                lazyInitData();
                isForeground = true;
                isLazyData = true;
            } else if (isInitView && isLazyData) {
                onHiddenChanged(false);
            }
        }
    }

    protected abstract void lazyInitData();

    @Override
    public void onPause() {
        super.onPause();
        if (isSetUserVisibleHint) {
            if (isVisibleToUser && isInitView && isLazyData) {
                onHiddenChanged(true);
            }
        } else {
            if (isInitView && isLazyData) {
                onHiddenChanged(true);
            }
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        LogUtils.i(this.getClass().getSimpleName() + "==onHiddenChanged==hidden:" + hidden + ",isForeground:" + isForeground + ",isSetUserVisibleHint:"+isSetUserVisibleHint);
        isForeground = !hidden;
    }

    @Override
    public void onDestroyView() {
        if (rootView != null) {
            ViewGroup parent = (ViewGroup) rootView.getParent();
            if (parent != null) parent.removeView(rootView);
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isInitView = false;
        isLazyData = false;
        isVisibleToUser = false;
        isSetUserVisibleHint = false;
        isForeground = false;
    }

    @Override
    protected void onPreDetach() {
        super.onPreDetach();
        LogUtils.i(this.getClass().getSimpleName() + "==onPreDetach==");
    }
}
