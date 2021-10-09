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
package com.jsy.common.acts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.jsy.res.utils.ViewUtils;

public class ContactUsActivity extends BaseActivity {

    public boolean canUseSwipeBackLayout(){
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contact_us);

        Toolbar      tool          = ViewUtils.getView(this, R.id.verificaiton_center_tool);
        LinearLayout ll_contact_us = ViewUtils.getView(this, R.id.ll_contact_us);
        TextView     tv_contact_us = ViewUtils.getView(this, R.id.tv_contact_us);

        setToolbarNavigtion(tool, this);

        ll_contact_us.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickCopy(tv_contact_us.getText());
            }
        });
    }

    private void clickCopy(CharSequence content) {
        ClipboardManager cm        = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData         plainText = ClipData.newPlainText("email", content);
        cm.setPrimaryClip(plainText);
        Toast.makeText(this, getResources().getString(R.string.secret_data_copy_successful), Toast.LENGTH_LONG).show();
    }
}
