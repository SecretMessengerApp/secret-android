/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.jsy.common.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.waz.zclient.R;

/**
 * {@link PictureDialog}
 */
@Deprecated
public class CustomDialog extends Dialog {


    private TextView tv;

    private View view;


    public CustomDialog(@NonNull Context context) {
        super(context, R.style.progress_dialog);

        LayoutInflater inflater = LayoutInflater.from(context);
        view = inflater.inflate(R.layout.progress_custom_dialog, null);
        tv = view.findViewById(R.id.id_tv_loadingmsg);
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(view);

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);

    }

    public void setMsg(String msg){
        tv.setText(msg);
    }
}
