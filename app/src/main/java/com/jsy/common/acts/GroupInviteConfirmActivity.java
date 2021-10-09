/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.acts;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jsy.common.httpapi.OnHttpListener;
import com.jsy.common.httpapi.SpecialServiceAPI;
import com.jsy.common.model.ConversationInviteMemberConfirmModel;
import com.jsy.common.model.HttpResponseBaseModel;
import com.jsy.common.model.SendTextJsonMessageEntity;
import com.jsy.common.model.circle.CircleConstant;
import com.jsy.common.utils.DensityUtils;
import com.jsy.common.utils.GlideHelper;
import com.jsy.common.utils.MessageUtils;
import com.jsy.common.utils.rxbus2.RxBus;
import com.jsy.common.views.CircleImageView;
import com.waz.model.UserId;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.common.views.ChatHeadViewNew;
import com.waz.zclient.ui.text.TypefaceTextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class GroupInviteConfirmActivity extends BaseActivity implements View.OnClickListener {
    private static final String KEY_CONFIRM_MODEL = "key_confirm_model";
    private static final String KEY_CONV_ID = "key_conv_id";
    private Toolbar toolbar;
    private ChatHeadViewNew circleImageViewAvatar;
    private RecyclerView recyclerView;
    private TypefaceTextView btnConfirmJoin;
    private TextView tv_user_name;
    private TextView tv_invite_count;
    private TextView tv_invite_excuse;
    private MemberAdapter adapter;
    private ConversationInviteMemberConfirmModel model;
    private String rConvId;
    private static final String MESSAGE_ID = "message_id";
    private String messageId;

    public static void start(Context context, ConversationInviteMemberConfirmModel model,String rConvId,String messageId){
        Intent intent = new Intent(context,GroupInviteConfirmActivity.class);
        intent.putExtra(KEY_CONFIRM_MODEL,model);
        intent.putExtra(KEY_CONV_ID,rConvId);
        intent.putExtra(MESSAGE_ID, messageId);
        context.startActivity(intent);
    }

    public boolean canUseSwipeBackLayout(){
        return true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_invite_confirm);
        rConvId = getIntent().getStringExtra(KEY_CONV_ID);
        messageId = getIntent().getStringExtra(MESSAGE_ID);
        model = (ConversationInviteMemberConfirmModel) getIntent().getSerializableExtra(KEY_CONFIRM_MODEL);
        findView();
        initView();
    }

    private void findView() {
        toolbar = findById(R.id.toolbar);
        circleImageViewAvatar = findById(R.id.circle_image_view_avatar);
        recyclerView = findViewById(R.id.recycler_view);
        btnConfirmJoin = findById(R.id.btn_confirm_join);
        tv_user_name = findViewById(R.id.tv_user_name);
        tv_invite_count = findViewById(R.id.tv_invite_count);
        tv_invite_excuse = findViewById(R.id.tv_invite_excuse);
    }

    private void initView() {

        toolbar.setNavigationOnClickListener(v -> finish());
        btnConfirmJoin.setOnClickListener(this);
        circleImageViewAvatar.loadUser(new UserId(model.msgData.inviter));
        tv_user_name.setText(model.msgData.name);
        tv_invite_count.setText(getString(R.string.conversation_detail_transfer_group_invite_desc, model.msgData.users.size()));
        tv_invite_excuse.setText("\""+model.msgData.reason+"\"");
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, 5);
        gridLayoutManager.setSpanCount(model.msgData.users.size()>=5?5:model.msgData.users.size());
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setHasFixedSize(true);
        adapter = new MemberAdapter(model.msgData.users);
        recyclerView.setAdapter(adapter);
        View view = new View(this);
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(1, DensityUtils.dp2px(this,30));
        view.setLayoutParams(layoutParams);
        adapter.addHeaderView(view);
        adapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                showToast(""+position);
            }
        });
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_confirm_join) {
            confirmJoin();
        }
    }

    private class MemberAdapter extends BaseQuickAdapter<ConversationInviteMemberConfirmModel.UserDataModel, BaseViewHolder> {

        public MemberAdapter( @Nullable List<ConversationInviteMemberConfirmModel.UserDataModel> data) {
            super(R.layout.item_group_invite_member, data);
        }

        @Override
        protected void convert(BaseViewHolder helper, ConversationInviteMemberConfirmModel.UserDataModel item) {
            CircleImageView circleImageView = helper.getView(R.id.avatar);
            Glide.with(getApplicationContext()).load(CircleConstant.appendAvatarUrl(item.asset,GroupInviteConfirmActivity.this)).apply(GlideHelper.getCircleCropOptions()).into(circleImageView);
            helper.setText(R.id.username,item.name);
        }
    }

    public void confirmJoin(){
        //String tokenType = SpUtils.getTokenType(this);
        //String token = SpUtils.getToken(this);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("code",model.msgData.code);
            jsonObject.put("allow",true);

            String urlPath = new StringBuilder()
                .append(String.format("conversations/%s/invite/conf", rConvId)).toString();

            SpecialServiceAPI.getInstance().post(urlPath, jsonObject.toString(), new OnHttpListener<HttpResponseBaseModel>() {
                @Override
                public void onFail(int code, String err) {
                    showToast(getString(R.string.conversation_detail_transfer_group_nvitation_failed));
                }

                @Override
                public void onSuc(HttpResponseBaseModel serializable, String orgJson) {
                    RxBus.getDefault().post(new SendTextJsonMessageEntity(GroupInviteConfirmActivity.class.getSimpleName(), MessageUtils.MessageActionType.INVITE_MEMBER_REFRESH, messageId));
                    GroupInviteConfirmActivity.this.finish();
                }

                @Override
                public void onSuc(List<HttpResponseBaseModel> r, String orgJson) {

                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
