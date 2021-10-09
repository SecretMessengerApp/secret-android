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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.*;
import com.jsy.common.adapter.GroupShareChatheadAdapter;
import com.jsy.common.adapter.OnChatheadWithTextClick;
import com.jsy.common.views.ParticipantsGridView;
import com.waz.model.UserData;
import com.waz.zclient.R;
import scala.collection.immutable.Set;

/**
 * Created by eclipse on 2018/12/20.
 */

public class GroupShareLinkPopupWindow extends PopupWindow implements View.OnClickListener{


    private Context mContext;
    private final LinearLayout mPopGroupShareLink;
    private ParticipantsGridView participantsGridView;
    private GroupShareChatheadAdapter adapter;
    private GroupShareCallBack callBack;

    private RelativeLayout dismiss;
    private RelativeLayout mRlCopyLink;
    private RelativeLayout mRlSendLink;
    private ImageView mIvSearch;
    private TextView mTvSendUser;
    private View llAnimView;

    private Set<UserData> selectData;

    public static final int COLUMNS = 5;
    public static final int ROWS = 3;

    public GroupShareLinkPopupWindow(Activity context, int height){

        super(context);
        mContext = context;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopGroupShareLink = (LinearLayout) inflater.inflate(R.layout.popup_window_share_group_link, null);
        this.setContentView(mPopGroupShareLink);

        llAnimView = mPopGroupShareLink.findViewById(R.id.llAnimView);
        dismiss = mPopGroupShareLink.findViewById(R.id.dismiss);
        mRlCopyLink = mPopGroupShareLink.findViewById(R.id.copy_link);
        mRlSendLink = mPopGroupShareLink.findViewById(R.id.send_link);
        mIvSearch = mPopGroupShareLink.findViewById(R.id.group_share_search);
        mTvSendUser = mPopGroupShareLink.findViewById(R.id.tv_send_user);

        dismiss.setOnClickListener(this);
        mRlCopyLink.setOnClickListener(this);
        mRlSendLink.setOnClickListener(this);
        mIvSearch.setOnClickListener(this);

        participantsGridView = mPopGroupShareLink.findViewById(R.id.pgv__participants);
        adapter = new GroupShareChatheadAdapter(context);

        adapter.setOnChatheadItemClickCallBack(new OnChatheadWithTextClick() {
            @Override
            public void clickItem(Set<UserData> selectedUesr) {
                if(selectedUesr.size() > 0){
                    selectData = selectedUesr;
                    mRlCopyLink.setVisibility(View.GONE);
                    mRlSendLink.setVisibility(View.VISIBLE);
                    mTvSendUser.setText(String.format(mContext.getResources().getString(R.string.conversation_share_user_mem), String.valueOf(selectedUesr.size())));
                }else{
                    mRlCopyLink.setVisibility(View.VISIBLE);
                    mRlSendLink.setVisibility(View.GONE);
                }
            }
        });

        participantsGridView.setAdapter(adapter);
        participantsGridView.setNumColumns(COLUMNS);

        this.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
        this.setHeight(height);
        this.setFocusable(true);
        ColorDrawable dw = new ColorDrawable(0x00000000);
        this.setBackgroundDrawable(dw);

        mPopGroupShareLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }


    @Override
    public void dismiss() {
        super.dismiss();
    }

    public void resetUI(){
        mRlCopyLink.setVisibility(View.VISIBLE);
        mRlSendLink.setVisibility(View.GONE);
    }


    public void showAtLocationWithAnim(View parent, int gravity, int x, int y) {
        super.showAtLocation(parent, gravity, x, y);
        startAnim();
    }

    public void startAnim() {
        llAnimView.startAnimation(AnimationUtils.loadAnimation(mContext, R.anim.in_from_bottom_enter_short_duration));
    }


    public void removeAllSelectUser(){
        adapter.removeAllSelectUser();
    }


    public void setCallBack(GroupShareCallBack callBack){
        this.callBack = callBack;
    }

    @Override
    public void onClick(View view) {

        int i = view.getId();
        if (i == R.id.dismiss) {
            dismiss();

        } else if (i == R.id.copy_link) {
            if (callBack != null) {
                callBack.clickCopy();
            }

        } else if (i == R.id.send_link) {
            dismiss();
            if (callBack != null) {
                callBack.clickSend(selectData);
            }

        } else if (i == R.id.group_share_search) {
            dismiss();
            if (callBack != null) {
                callBack.clickSearch();
            }

        }

    }


    public interface GroupShareCallBack{

        void clickCopy();

        void clickSend(Set<UserData> selectData);

        void clickSearch();

    }
}

