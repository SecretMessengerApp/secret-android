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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import com.waz.zclient.R;


public class PictureDialog extends Dialog {
    public Context context;
    private View rootView;
    private PictureSpinView imageView;
    private TextView textView;

    public PictureDialog(Context context) {
        super(context, R.style.picture_alert_dialog);
        this.context = context;
        rootView = LayoutInflater.from(context).inflate(R.layout.picture_alert_dialog, null);
        imageView=rootView.findViewById(R.id.picture_dialog_icon);
        textView = rootView.findViewById(R.id.picture_dialog_text);
        textView.setMaxWidth(context.getResources().getDisplayMetrics().widthPixels >> 2);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogWindowStyle);
    }


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(rootView);
    }

    public void show(String msg,int resId,boolean needToUpdateView){
        imageView.setImageResource(resId,needToUpdateView);
        textView.setVisibility(TextUtils.isEmpty(msg) ? View.GONE : View.VISIBLE);
        textView.setText(TextUtils.isEmpty(msg) ? "" : msg);
        super.show();
    }

    @Override
    public void show() {
        textView.setVisibility(View.GONE);
        super.show();
    }
}
