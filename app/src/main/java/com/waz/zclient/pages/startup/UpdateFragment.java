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
package com.waz.zclient.pages.startup;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.jsy.common.views.ProgressButton;
import com.waz.zclient.R;
import com.waz.zclient.pages.BaseFragment;
import com.jsy.res.utils.ViewUtils;

public class UpdateFragment extends BaseFragment<UpdateFragment.Container> {


    public static final String TAG = UpdateFragment.class.getName();

    private String android_url;
    private boolean isForceUpdate;

    private ProgressButton zetaButtonForceUpdate;
    private ProgressButton zetaButtonNotForceUpdate;
    private ProgressButton zetaButtonQuit;
    private RelativeLayout rlForceUpdate;
    private RelativeLayout rlNotForceUpdate;

    public static UpdateFragment newInstance(String android_url, boolean isForceUpdate) {
        Bundle args = new Bundle();
        args.putString("android_url", android_url);
        args.putBoolean("isForceUpdate", isForceUpdate);
        UpdateFragment updateFragment = new UpdateFragment();
        updateFragment.setArguments(args);
        return updateFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            android_url = savedInstanceState.getString("android_url");
            isForceUpdate = savedInstanceState.getBoolean("isForceUpdate");
        } else {
            if (getArguments() != null) {
                android_url = getArguments().getString("android_url");
                isForceUpdate = getArguments().getBoolean("isForceUpdate");
            } else {
                // ...
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("android_url", android_url);
        outState.putBoolean("isForceUpdate", isForceUpdate);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_update, container, false);

        zetaButtonForceUpdate = ViewUtils.getView(view, R.id.zb__update__download_force);
        zetaButtonNotForceUpdate = ViewUtils.getView(view, R.id.zb__update__download_not_force);
        zetaButtonQuit = ViewUtils.getView(view, R.id.zb__update__quit);
        rlForceUpdate = ViewUtils.getView(view, R.id.rl_force_update);
        rlNotForceUpdate = ViewUtils.getView(view, R.id.rl_not_force_update);

        /*
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            //noinspection deprecation
            zetaButton.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color));
            zetaButtonQuit.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color));
        } else {
            zetaButton.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color, getContext().getTheme()));
            zetaButtonQuit.setAccentColor(getResources().getColor(R.color.forced_update__button__background_color, getContext().getTheme()));
        }*/

        zetaButtonForceUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().onClickUpdate();
            }
        });
        rlForceUpdate.setVisibility(isForceUpdate ? View.VISIBLE : View.GONE);
        rlNotForceUpdate.setVisibility(isForceUpdate ? View.GONE : View.VISIBLE);
        zetaButtonQuit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().onClickQuit();
            }
        });
        zetaButtonNotForceUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getContainer().onClickUpdate();
            }
        });
        return view;
    }

    public void updateDownloadText(int progress, int max, boolean isFaile) {
        if(isForceUpdate){
            zetaButtonForceUpdate.setMinProgress(0);
            zetaButtonForceUpdate.setMaxProgress(max);
            zetaButtonForceUpdate.setProgress(progress);

            if (isFaile) {
                zetaButtonForceUpdate.setClickable(true);
                zetaButtonForceUpdate.setText(getResources().getText(R.string.content__file__status__download_failed__minimized));
                zetaButtonForceUpdate.setTextColor(ContextCompat.getColor(getActivity(), R.color.notification_red_color));
            } else {
                zetaButtonForceUpdate.setClickable(false);
                zetaButtonForceUpdate.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
                zetaButtonForceUpdate.setText(getResources().getText(R.string.forced_update__downloading));
            }
        }else{
            zetaButtonNotForceUpdate.setMinProgress(0);
            zetaButtonNotForceUpdate.setMaxProgress(max);
            zetaButtonNotForceUpdate.setProgress(progress);

            if (isFaile) {
                zetaButtonNotForceUpdate.setClickable(true);
                zetaButtonNotForceUpdate.setText(getResources().getText(R.string.content__file__status__download_failed__minimized));
                zetaButtonNotForceUpdate.setTextColor(ContextCompat.getColor(getActivity(), R.color.notification_red_color));
            } else {
                zetaButtonNotForceUpdate.setClickable(false);
                zetaButtonNotForceUpdate.setTextColor(ContextCompat.getColor(getActivity(), R.color.white));
                zetaButtonNotForceUpdate.setText(getResources().getText(R.string.forced_update__downloading));
            }
        }

    }

    public interface Container {

        void onClickUpdate();

        void onClickQuit();
    }

}
