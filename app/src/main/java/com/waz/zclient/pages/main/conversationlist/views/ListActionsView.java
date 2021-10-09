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
package com.waz.zclient.pages.main.conversationlist.views;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.jsy.res.utils.ViewUtils;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;
import com.waz.zclient.views.MessageBubbleView;

public class ListActionsView extends FrameLayout implements View.OnClickListener {

    private static final String TAG = "ListActionsView";

    public static final int STATE_AVATAR = 1;
    public static final int STATE_FRIEND = 2;
    public static final int STATE_SETTINGS = 3;
    public static final int STATE_DEFAULT_GROUP = 4;

    private ImageView[] imageViewList=new ImageView[4];

    private View vAvastar;
    private View vFriend;
    private View vSettings;
    private View vDefaultGRoup;

    private MessageBubbleView vNotificationAvastar;

    private View bottomBorder;

    private Callback callback;

    public ListActionsView(Context context) {
        this(context, null);
    }

    public ListActionsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ListActionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_actions_view, this, true);
        view.setLongClickable(true);

        imageViewList[0] = ViewUtils.getView(this, R.id.chat_imagebutton_avastar);
        imageViewList[1] = ViewUtils.getView(this, R.id.chat_imagebutton_friend);
        imageViewList[2] = ViewUtils.getView(this, R.id.chat_imagebutton_settings);
        imageViewList[3] = ViewUtils.getView(this, R.id.chat_default_group);

        vAvastar = ViewUtils.getView(this, R.id.vAvastar);
        vFriend = ViewUtils.getView(this, R.id.vFriend);
        vSettings = ViewUtils.getView(this, R.id.vSettings);
        vDefaultGRoup = ViewUtils.getView(this, R.id.vDefaultGRoup);

        //vAvastar.setOnClickListener(this);
        vFriend.setOnClickListener(this);
        vSettings.setOnClickListener(this);
        vDefaultGRoup.setOnClickListener(this);

        vNotificationAvastar = ViewUtils.getView(this, R.id.vNotificationAvastar);
        bottomBorder = ViewUtils.getView(this, R.id.v_conversation_list_bottom_border);

        GestureDetector gestureDetector=new GestureDetector(getContext(),new GestureDetector.SimpleOnGestureListener(){
           @Override
           public boolean onSingleTapUp(MotionEvent e) {
               LogUtils.d(TAG, "onSingleTapUp() called with: e = [" + e + "]");
               onClick(vAvastar);
               return super.onSingleTapUp(e);
           }

           @Override
           public boolean onDoubleTap(MotionEvent e) {
               LogUtils.d(TAG, "onDoubleTap() called with: e = [" + e + "]");
               if(callback!=null){
                   callback.onAvatarDoubleTap();
               }
               return super.onDoubleTap(e);
           }

           @Override
           public boolean onDoubleTapEvent(MotionEvent e) {
               LogUtils.d(TAG, "onDoubleTapEvent() called with: e = [" + e + "]");
               return super.onDoubleTapEvent(e);
           }

           @Override
           public boolean onSingleTapConfirmed(MotionEvent e) {
               LogUtils.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
               return super.onSingleTapConfirmed(e);
           }
       });
       vAvastar.setOnTouchListener((v, event) -> {
           gestureDetector.onTouchEvent(event);
           return true;
       });
        vNotificationAvastar.setOnActionListener(new MessageBubbleView.ActionListener() {
            @Override
            public void onDrag() {
                LogUtils.d(TAG, "onDrag() called");
            }

            @Override
            public void onDisappear() {
                LogUtils.d(TAG, "onDisappear() called");
                if(callback!=null){
                    callback.onClearUnReadMsg();
                }
            }

            @Override
            public void onRestore() {
                LogUtils.d(TAG, "onRestore() called");
            }

            @Override
            public void onMove() {
                LogUtils.d(TAG, "onMove() called");
            }
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(View view) {
        if (view == null || callback == null) {
            return;
        }
        int i = view.getId();
        if (i == R.id.vAvastar) {
            callback.onAvatarPress();

        } else if (i == R.id.vFriend) {
            callback.onFriendPress();

        } else if (i == R.id.vSettings) {
            callback.onSettingsPress();

        } else if (i == R.id.vDefaultGRoup) {
            callback.onDefaultGroupPress();

        }
    }


    public void showAvastarRedPoint(int visible) {
        vNotificationAvastar.setVisibility(visible);
    }

    public void setActionButtonStatus(int status) {
        for (int i = 0; i <imageViewList.length ; i++) {
            if((i+1)==status){
                imageViewList[i].setSelected(true);
            }
            else{
                imageViewList[i].setSelected(false);
            }
        }
    }

    public void rotateGroupAnim(float toAngle){
        vDefaultGRoup.clearAnimation();
        ObjectAnimator.ofFloat(vDefaultGRoup, "rotation", 0, toAngle).setDuration(200).start();
    }

    public void setDefaultGroupStatus(boolean hasDefault) {
        if (hasDefault) {
            vDefaultGRoup.setVisibility(View.VISIBLE);
        } else {
            vDefaultGRoup.setVisibility(View.GONE);
        }
    }

    public interface Callback {
        void onAvatarPress();

        //void onArchivePress();
        void onFriendPress();

        void onSettingsPress();

        void onDefaultGroupPress();

        void onAvatarDoubleTap();

        void onClearUnReadMsg();
    }
}
