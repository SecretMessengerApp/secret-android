/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.popup;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jsy.common.acts.GroupNoticeActivity;
import com.waz.zclient.R;

public  class GroupNoticePopupWindow extends BasePopupWindow {

    private LinearLayout ll_content;
    private TextView tv_content;
    private String rConvId;

    public GroupNoticePopupWindow(Context context) {
        super(context);
    }

    @Override
    public int getLayoutId() {
        return R.layout.group_notice_pop;
    }

    @Override
    protected void init() {
        super.init();

        ll_content=findViewById(R.id.ll_content);
        tv_content=findViewById(R.id.tv_content);
        TextView tv_more = findViewById(R.id.tv_more);
        LinearLayout ll_bottom = findViewById(R.id.ll_bottom);
        tv_more.setOnClickListener(this);
        ll_bottom.setOnClickListener(this);

    }

    public void setData(String convId,String content){
        this.rConvId=convId;
        tv_content.setText(content);
    }

    public void setContentWidth(int width){
        ViewGroup.LayoutParams layoutParams=ll_content.getLayoutParams();
        layoutParams.width=width;
        ll_content.setLayoutParams(layoutParams);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if (v.getId() == R.id.tv_more) {
            GroupNoticeActivity.startGroupNoticeActivitySelf(mContext,rConvId);
            dismiss();
        }
        else if(v.getId()==R.id.ll_bottom){
            dismiss();
        }
    }
}
