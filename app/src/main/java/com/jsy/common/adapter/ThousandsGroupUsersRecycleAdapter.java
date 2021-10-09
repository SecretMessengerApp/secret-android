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
package com.jsy.common.adapter;

import android.content.Context;
import androidx.annotation.Nullable;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jsy.common.model.ThousandGroupUserModel;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.common.fragment.ThousandsGroupUsersFragment;
import com.jsy.common.listener.OnRecyclerItemClickDataListener;
import com.jsy.common.utils.GlideHelper;
import com.waz.zclient.R;

import java.util.List;

public class ThousandsGroupUsersRecycleAdapter extends BaseQuickAdapter<ThousandGroupUserModel.ThousandGroupUserItemModel, BaseViewHolder> {

    private int groupUserType;
    private Context context;
    private String selfUserId;
    private String creatorId;

    private View parentView;
    private OnRecyclerItemClickDataListener<ThousandGroupUserModel.ThousandGroupUserItemModel> onRecyclerItemClickDataListener;

    public ThousandsGroupUsersRecycleAdapter(View parentView, Context context,
                                             @Nullable List<ThousandGroupUserModel.ThousandGroupUserItemModel> data,
                                             String selfUserId, int groupUserType, String creatorId,
                                             OnRecyclerItemClickDataListener<ThousandGroupUserModel.ThousandGroupUserItemModel> onRecyclerItemClickDataListener) {
        super(R.layout.lay_thousand_group_user_item, data);
        this.parentView = parentView;
        this.context = context;
        this.selfUserId = selfUserId;
        this.creatorId = creatorId;
        this.groupUserType = groupUserType;
        this.onRecyclerItemClickDataListener = onRecyclerItemClickDataListener;
    }

    @Override
    protected void convert(final BaseViewHolder helper, final ThousandGroupUserModel.ThousandGroupUserItemModel item) {
        ImageView ivHeader = helper.getView(R.id.ivHeader);
        String targetAvatar = CircleConstant.appendAvatarUrl(item.getAsset(), context);
        Glide.with(context).load(targetAvatar).apply(GlideHelper.getCircleCropOptions()).into(ivHeader);

        TextView ttvName = helper.getView(R.id.ttvName);
        ttvName.setText(item.getName());

        TextView ttvSelfName = helper.getView(R.id.ttvSelfName);
        ttvSelfName.setVisibility(View.VISIBLE);

        if (item.getId().equalsIgnoreCase(selfUserId) && item.getId().equalsIgnoreCase(creatorId)) {
            ttvSelfName.setText(R.string.group_participant_user_row_creator_and_me);
        } else if (item.getId().equalsIgnoreCase(selfUserId)) {
            ttvSelfName.setText(R.string.group_participant_user_row_me);
        } else if (item.getId().equalsIgnoreCase(creatorId)) {
            ttvSelfName.setText(R.string.group_participant_user_row_creator);
        } else {
            ttvSelfName.setText("");
        }

        ImageView ivDel = helper.getView(R.id.icon_del);
        ivDel.setVisibility(item.isSelect() ? View.VISIBLE : View.GONE);

        ImageView ivArrow = helper.getView(R.id.icon_arrow);
        ivArrow.setVisibility(isShowArrow() ? View.VISIBLE : View.INVISIBLE);

        helper.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onRecyclerItemClickDataListener != null) {
                    onRecyclerItemClickDataListener.onItemViewsClick(parentView, v.getId(), helper.getPosition(), item);
                }
            }
        });
    }

    private boolean isShowArrow(){
       return groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_MORE();
    }

}
