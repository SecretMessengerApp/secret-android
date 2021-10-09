/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.adapter;

import android.text.TextUtils;
import android.view.View;
import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jsy.common.listener.OnSelectUserDataListener;
import com.jsy.common.model.SearchUserInfo;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.common.utils.GlideHelper;
import com.jsy.common.views.CircleImageView;
import com.waz.zclient.R;

import java.util.List;

public class GroupUserSearchAddAdapter extends BaseQuickAdapter<SearchUserInfo, BaseViewHolder> {

    private OnSelectUserDataListener selectUserDataListener;

    public void setSelectUserDataListener(OnSelectUserDataListener selectUserDataListener) {
        this.selectUserDataListener = selectUserDataListener;
    }

    public GroupUserSearchAddAdapter(List<SearchUserInfo> userInfos) {
        super(R.layout.adapter_group_search_user, userInfos);
    }

    @Override
    protected void convert(BaseViewHolder helper, final SearchUserInfo item) {
        helper.setText(R.id.ttvName1, TextUtils.isEmpty(item.getName()) ? item.getHandle() : item.getName());
        helper.setText(R.id.ttvHandler1, TextUtils.isEmpty(item.getHandle()) ? "" : "@" + item.getHandle());
        CircleImageView ivHeader = helper.getView(R.id.ivHeader1);
        String targetAvatar = CircleConstant.appendAvatarUrl(item.getAsset(), ivHeader.getContext());
        Glide.with(ivHeader.getContext()).load(targetAvatar).apply(GlideHelper.getCircleCropOptions()).into(ivHeader);
        helper.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != selectUserDataListener) {
                    selectUserDataListener.onSelectData(item);
                }
            }
        });
    }
}
