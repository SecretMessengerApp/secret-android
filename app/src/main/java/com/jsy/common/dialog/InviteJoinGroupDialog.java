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
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import com.waz.zclient.R;


public class InviteJoinGroupDialog extends Dialog {
    private OnInviteClikeCallback listener;
    private EditText et_excuse;

    public interface OnInviteClikeCallback {
        void onNotSaveClick();

        void onSaveClick(String inviteExcuse);
    }

    public InviteJoinGroupDialog(Context context) {
        super(context, R.style.publish_cancel_dialog);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        Window window = getWindow();
        if(window != null) {
            window.setWindowAnimations(R.style.DialogWindowStyle);
        }
    }

    public InviteJoinGroupDialog(Context context, OnInviteClikeCallback listener) {
        this(context);
        this.listener = listener;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_invite_join_group);
        et_excuse = findViewById(R.id.et_excuse);

        TextView tvNotSave = findViewById(R.id.tv_not_save);
        tvNotSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    listener.onNotSaveClick();
            }
        });

        TextView tvSave = findViewById(R.id.tv_save);
        tvSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null)
                    listener.onSaveClick(et_excuse.getText().toString().trim());
            }
        });
        et_excuse.addTextChangedListener(watcher);
    }

    TextWatcher watcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                                      int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // TODO Auto-generated method stub
        }

        @Override
        public void afterTextChanged(Editable s) {
            // TODO Auto-generated method stub
            int lines = et_excuse.getLineCount();
            if (lines > 2) {
                String str = s.toString();
                int cursorStart = et_excuse.getSelectionStart();
                int cursorEnd = et_excuse.getSelectionEnd();
                if (cursorStart == cursorEnd && cursorStart < str.length() && cursorStart >= 1) {
                    str = str.substring(0, cursorStart - 1) + str.substring(cursorStart);
                } else {
                    str = str.substring(0, s.length() - 1);
                }
                et_excuse.setText(str);
                et_excuse.setSelection(et_excuse.getText().length());
            }
        }
    };

    public String getEditText(){
        return et_excuse.getText().toString().trim();
    }
}
