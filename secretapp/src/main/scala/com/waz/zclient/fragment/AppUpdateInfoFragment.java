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
package com.waz.zclient.fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.jsy.secret.R;
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener;
import com.waz.zclient.pages.BaseFragment;

public class AppUpdateInfoFragment extends BaseFragment<AppUpdateInfoFragment.Container> implements View.OnClickListener, OnBackPressedListener {

    public static final String TAG = AppUpdateInfoFragment.class.getSimpleName();

    private static final String ARGS_KEY_currentVersionName = "currentVersionName";

    public static AppUpdateInfoFragment newInstance(String currentVersionName) {

        Bundle args = new Bundle();
        args.putString(ARGS_KEY_currentVersionName, currentVersionName);

        AppUpdateInfoFragment fragment = new AppUpdateInfoFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.lay_app_update_info, null);
        View ivCloseAppUpdateInfo = rootView.findViewById(R.id.ivCloseAppUpdateInfo);
        TextView tvUpdateInfoDetail = rootView.findViewById(R.id.tvUpdateInfoDetail);
        tvUpdateInfoDetail.setMovementMethod(ScrollingMovementMethod.getInstance());
        View tvCloseAppUpdateInfo = rootView.findViewById(R.id.tvCloseAppUpdateInfo);
        ivCloseAppUpdateInfo.setOnClickListener(this);
        tvCloseAppUpdateInfo.setOnClickListener(this);

        TextView tvUpdateInfoTitle = rootView.findViewById(R.id.tvUpdateInfoTitle);
        String currentVersionName = getArguments().getString(ARGS_KEY_currentVersionName);
        tvUpdateInfoTitle.setText(String.format(getResources().getString(R.string.app_update_info_title), currentVersionName));
        return rootView;
    }

    @Override
    public void onClick(View v) {
        int vId = v.getId();
        if (vId == R.id.ivCloseAppUpdateInfo || vId == R.id.tvCloseAppUpdateInfo){
            getContainer().onClickCloseAppUpdateInfo();
        }else{
            //...
        }

    }

    public interface Container {
        void onClickCloseAppUpdateInfo();
    }

    @Override
    public boolean onBackPressed() {
        getContainer().onClickCloseAppUpdateInfo();
        return true;
    }
}
