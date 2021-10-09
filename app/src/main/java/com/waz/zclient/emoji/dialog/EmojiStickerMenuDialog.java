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
package com.waz.zclient.emoji.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.jsy.common.utils.RxJavaUtil;
import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.R;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.bean.GifSavedItem;
import com.waz.zclient.emoji.utils.EmojiUtils;
import com.waz.zclient.emoji.utils.GifSavedDaoHelper;
import com.waz.zclient.utils.SpUtils;

import org.telegram.ui.ContentPreviewViewer;

public class EmojiStickerMenuDialog extends Dialog implements View.OnClickListener {

    private EmotionItemBean emotionItemBean;
    private TextView tv_send;
    private TextView tv_favorite;
    private TextView tv_sticker_set;
    private TextView tv_cancel;
    private ContentPreviewViewer.ContentPreviewViewerDelegate delegate;

    public void setDelegate(ContentPreviewViewer.ContentPreviewViewerDelegate delegate) {
        this.delegate = delegate;
    }

    public EmojiStickerMenuDialog(@NonNull Context context, EmotionItemBean emotionItemBean) {
        super(context, R.style.EmojiMenuDialog);
        this.emotionItemBean=emotionItemBean;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
    }

    private void initView(){

        if(emotionItemBean==null)return;

        View view= LayoutInflater.from(getContext()).inflate(R.layout.dialog_emoji_menu,null);
        view.setMinimumWidth(ScreenUtils.getScreenWidth(getContext()));
        tv_send=view.findViewById(R.id.tv_send);
        tv_favorite=view.findViewById(R.id.tv_favorite);
        tv_sticker_set=view.findViewById(R.id.tv_sticker_set);
        tv_cancel=view.findViewById(R.id.tv_cancel);
        tv_send.setOnClickListener(this);
        tv_favorite.setOnClickListener(this);
        tv_sticker_set.setOnClickListener(this);
        tv_cancel.setOnClickListener(this);
        setContentView(view);
        Window window = getWindow();
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.x = 0;
        lp.y = 0;
        lp.width=ScreenUtils.getScreenWidth(getContext());
        window.setAttributes(lp);

        if(emotionItemBean.getFolderName()==null){
            tv_sticker_set.setVisibility(View.GONE);
        }
        else{
            tv_sticker_set.setVisibility(View.VISIBLE);
        }

        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<Boolean>() {
            @Override
            public Boolean doInBackground() {
                if(emotionItemBean instanceof GifSavedItem){
                    GifSavedItem gifSavedItem=(GifSavedItem)emotionItemBean;
                    if(EmojiUtils.isGifFavorite(gifSavedItem)){
                        return true;
                    }
                }
                return GifSavedDaoHelper.existsSavedGif(SpUtils.getUserId(getContext()), true, emotionItemBean.getUrl());
            }

            @Override
            public void onFinish(Boolean result) {
                if(result) {
                    tv_favorite.setText(R.string.message_bottom_menu_action_favorite_remove);
                    tv_favorite.setTextColor(Color.parseColor("#FF3C30"));

                }else {
                    tv_favorite.setText(R.string.message_bottom_menu_action_favorite_add);
                    tv_favorite.setTextColor(Color.parseColor("#007EE5"));
                }
            }
            @Override
            public void onError(Throwable e) {

            }
        });
    }

    @Override
    public void onClick(View v) {
        if(delegate==null)return;
        int id=v.getId();
        if(id==R.id.tv_send){
            delegate.onSend(emotionItemBean);
        }
        else if(id==R.id.tv_favorite){
            delegate.onFavorite(emotionItemBean);
        }
        else if(id==R.id.tv_sticker_set){
            delegate.onStickerSet(emotionItemBean);
        }
        else if(id==R.id.tv_cancel){
        }
        dismiss();
    }


}
