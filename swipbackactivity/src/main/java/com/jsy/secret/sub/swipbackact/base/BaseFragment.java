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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.jsy.secret.sub.swipbackact.router.BaseToMainActivityRouter;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;

public abstract class BaseFragment<T> extends Fragment {
    protected T container;
    protected Context mContext;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.mContext = context;
        Fragment fragment = getParentFragment();
        try {
            if (fragment != null) {
                container = (T) fragment;
            } else {
                container = (T) context;
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
        onPostAttach(context);
    }

    protected void onPostAttach(Context context) {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogUtils.i(this.getClass().getSimpleName() + "==onCreate==");
    }

    public boolean isAddFragment() {
        return this.isAdded() && !this.isDetached();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LogUtils.i(this.getClass().getSimpleName() + "==onCreateView==");
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LogUtils.i(this.getClass().getSimpleName() + "==onViewCreated==");
    }

    @Override
    public void onResume() {
        super.onResume();
        LogUtils.i(this.getClass().getSimpleName() + "==onResume==");
    }

    @Override
    public void onPause() {
        super.onPause();
        LogUtils.i(this.getClass().getSimpleName() + "==onPause==");
    }

    @Override
    public void onStop() {
        super.onStop();
        LogUtils.i(this.getClass().getSimpleName() + "==onStop==");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LogUtils.i(this.getClass().getSimpleName() + "==onDestroyView==");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtils.i(this.getClass().getSimpleName() + "==onDestroy==");
    }

    @Override
    public final void onDetach() {
        onPreDetach();
        container = null;
        super.onDetach();
    }

    protected void onPreDetach() {
    }

    public T getContainer() {
        return container;
    }

    protected BaseToMainActivityRouter getBaseToMainRouter() {
        BaseActivity activity = (BaseActivity) getActivity();
        if (null != activity) {
            return activity.getBaseToMainRouter();
        }
        return null;
    }
}
