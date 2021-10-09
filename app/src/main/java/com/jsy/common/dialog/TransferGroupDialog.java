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
package com.jsy.common.dialog;


import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import com.waz.zclient.R;
import com.waz.zclient.ZApplication;


public class TransferGroupDialog extends Dialog {
    private ClickCallback listener;
    private TextView dialog_title;
    private String userName;
    private String titleText;

    public interface ClickCallback {
        void onNotSaveClick();

        void onSaveClick();
    }

    public TransferGroupDialog(Context context) {
        super(context, R.style.publish_cancel_dialog);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        Window window = getWindow();
        if(window != null) {
            window.setWindowAnimations(R.style.DialogWindowStyle);
        }
    }

    public TransferGroupDialog(Context context, String userName, ClickCallback listener) {
        this(context);
        this.userName = userName;
        this.listener = listener;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_transfer_group);

        dialog_title = findViewById(R.id.dialog_title);
        titleText = ZApplication.getInstance().getResources().getString(R.string.conversation_detail_transfer_group_confirm);

        TextView tvNotSave = findViewById(R.id.tv_not_save);
        tvNotSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(listener != null)
                    listener.onNotSaveClick();
            }
        });

        TextView tvSave = findViewById(R.id.tv_save);
        tvSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(listener != null)
                    listener.onSaveClick();
            }
        });

        formatTitle(userName);
    }

    public void formatTitle(String userName) {
        String result = String.format(titleText, userName);
        dialog_title.setText(Html.fromHtml(result));
    }
}
