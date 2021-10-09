/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.popup;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.waz.zclient.R;

public class JoinGroupPopUpWindow extends BasePopupWindow {

    private TextView mTvRefuse;
    private TextView mTvAllow;
    private JoinGroupAllowCallBack mCallBack;

    public JoinGroupPopUpWindow(Context context, int width, int height) {
        super(context,width,height);
    }

    @Override
    public int getLayoutId() {
        return R.layout.join_group_pop;
    }

    @Override
    protected void init() {
        mTvRefuse = findViewById(R.id.tv_refuse);
        mTvAllow = findViewById(R.id.tv_allow);
        mTvRefuse.setOnClickListener(this);
        mTvAllow.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        if(v.getId()==R.id.tv_refuse){
            if (mCallBack != null) {
                mCallBack.clickRefuse();
            }
            dismiss();
        }
        else if(v.getId()==R.id.tv_allow){
            if (mCallBack != null) {
                mCallBack.clickAllow();
            }
            dismiss();
        }
    }

    public void setCallBack(JoinGroupAllowCallBack callBack) {
        this.mCallBack = callBack;
    }

    public interface JoinGroupAllowCallBack {
        void clickAllow();
        void clickRefuse();
    }
}
