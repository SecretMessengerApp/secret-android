/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.popup;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import com.waz.zclient.R;

public class NoGroupNoticePopupWindow extends BasePopupWindow {

    public NoGroupNoticePopupWindow(Context context) {
        super(context);
    }

    @Override
    public int getLayoutId() {
        return R.layout.no_group_notice_pop;
    }

    @Override
    protected void init() {
        super.init();
        LinearLayout ll_content=findViewById(R.id.ll_content);
        ll_content.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

    }
}
