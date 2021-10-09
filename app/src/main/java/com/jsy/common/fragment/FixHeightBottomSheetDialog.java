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
package com.jsy.common.fragment;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.waz.zclient.R;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class FixHeightBottomSheetDialog extends BottomSheetDialog {
    private View mContentView;

    public FixHeightBottomSheetDialog(@NonNull Context context) {
        super(context);
    }

    public FixHeightBottomSheetDialog(@NonNull Context context, int theme) {
        super(context, theme);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        fixHeight();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        this.mContentView = view;
        getDelegate().findViewById(com.google.android.material.R.id.design_bottom_sheet).setBackgroundColor(getContext().getResources().getColor(R.color.transparent));

    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        this.mContentView = view;
    }

    private void fixHeight() {
        if (null == mContentView) {
            return;
        }

        View parent = (View) mContentView.getParent();
        BottomSheetBehavior behavior = BottomSheetBehavior.from(parent);
        mContentView.measure(0, 0);
        behavior.setPeekHeight(mContentView.getMeasuredHeight());
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) parent.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        parent.setLayoutParams(params);
    }
}
