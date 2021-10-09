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
package com.jsy.common.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.waz.zclient.R;

public class TitleMsgSureDialog extends Dialog {
    public Context context;
    private View rootView;

    private boolean showTitle;
    private boolean showMsg;
    private boolean showCancel;
    private boolean showConfirm;

    private TextView tvTitle;
    private TextView tvMsg;
    private View vSeparatorHorizontal;
    private TextView tvCancel;
    private View vSeparatorVertical;
    private TextView tvConfirm;

    public TitleMsgSureDialog(Context context) {
        super(context, R.style.forceUpdateDialogTheme);
        this.context = context;
        rootView = LayoutInflater.from(context).inflate(R.layout.dialog_title_msg_cancel_sure, null);
        tvTitle = rootView.findViewById(R.id.tvTitle);
        tvMsg = rootView.findViewById(R.id.tvMsg);
        vSeparatorHorizontal = rootView.findViewById(R.id.vSeparatorHorizontal);
        vSeparatorVertical = rootView.findViewById(R.id.vSeparatorVertical);
        tvConfirm = rootView.findViewById(R.id.tvConfirm);
        tvCancel = rootView.findViewById(R.id.tvCancel);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(rootView);
    }

    public TitleMsgSureDialog updateFields(boolean showTitle, boolean showMsg, boolean showCancel, boolean showConfirm) {
        this.showTitle = showTitle;
        this.showMsg = showMsg;
        this.showCancel = showCancel;
        this.showConfirm = showConfirm;
        tvTitle.setVisibility(showTitle?View.VISIBLE:View.GONE);
        tvMsg.setVisibility(showMsg?View.VISIBLE:View.GONE);
        vSeparatorVertical.setVisibility(showCancel && showConfirm ? View.VISIBLE : View.GONE);
        tvCancel.setVisibility(showCancel ? View.VISIBLE : View.GONE);
        tvConfirm.setVisibility(showConfirm ? View.VISIBLE : View.GONE);
        return this;
    }

    public void show(int resTitle, int resMsg, OnTitleMsgSureDialogClick onTitleMsgSureDialogClick) {
        show(resTitle > 0 ? context.getResources().getString(resTitle) : "", resMsg > 0 ? context.getResources().getString(resMsg) : "", onTitleMsgSureDialogClick);
    }

    public void show(String title, String msg, final OnTitleMsgSureDialogClick onTitleMsgSureDialogClick) {
        tvCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onTitleMsgSureDialogClick != null) {
                    onTitleMsgSureDialogClick.onClickCancel();
                }
            }
        });
        tvConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onTitleMsgSureDialogClick != null) {
                    onTitleMsgSureDialogClick.onClickConfirm();
                }
            }
        });
        tvTitle.setText(title);
        tvMsg.setText(msg);
        super.show();
    }

    public void setCancelText(int resId){
        if (tvCancel != null) {
            tvCancel.setText(resId);
        }
    }

    public void setConfirmText(int resId){
        if (tvConfirm != null) {
            tvConfirm.setText(resId);
        }
    }

    public void setCancelTextColor(int color){
        if (tvCancel != null) {
            tvCancel.setTextColor(color);
        }
    }

    public void setConfirmTextColor(int color){
        if (tvConfirm != null) {
            tvConfirm.setTextColor(color);
        }
    }

    @Deprecated
    @Override
    public final void show() {
        super.show();
    }

    public static class OnTitleMsgSureDialogClick {
        public void onClickCancel() {
        }

        public void onClickConfirm() {
        }
    }
}
