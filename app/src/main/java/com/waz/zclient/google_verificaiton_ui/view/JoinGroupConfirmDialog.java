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
package com.waz.zclient.google_verificaiton_ui.view;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.waz.zclient.R;


public class JoinGroupConfirmDialog extends Dialog {

    private JoinGroupConfirmDialog.BtnClickListener btnClickListener;

    public JoinGroupConfirmDialog(@NonNull Context context) {

        super(context, R.style.Dialog_Msg);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.dialog_join_group);

        //setCanceledOnTouchOutside(false);

        setCancelable(false);

        initView();

    }

    private void initView() {

        final TextView confirmTextView = findViewById(R.id.confirm_send_code);

        final TextView cancelTextView = findViewById(R.id.cancle_send_code);

        confirmTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                if(btnClickListener != null){
                    btnClickListener.onBtnClick();
                }
            }
        });

        cancelTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
                if(btnClickListener != null){
                    btnClickListener.onDismiss();
                }
            }
        });

    }


    @Override
    public void show() {
        super.show();
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.width= ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.height= ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;
        getWindow().getDecorView().setPadding(0, 0, 0, 0);
        getWindow().setAttributes(layoutParams);
    }

    public void setBtnClickListener(JoinGroupConfirmDialog.BtnClickListener listerer){
        this.btnClickListener = listerer;
    }

    public interface BtnClickListener{
        void onBtnClick();
        void onDismiss();
    }


}
