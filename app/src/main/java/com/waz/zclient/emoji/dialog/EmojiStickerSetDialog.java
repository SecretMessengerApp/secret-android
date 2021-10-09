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
import android.graphics.Rect;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.R;
import com.waz.zclient.emoji.OnEmojiChangeListener;
import com.waz.zclient.emoji.adapter.EmojiNewAdapter;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.utils.EmojiUtils;

import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ContentPreviewViewer;

import java.util.List;

public class EmojiStickerSetDialog extends Dialog {

    private TextView tv_name;
    private FrameLayout fl_content;
    private TextView tv_sticker_set;
    private TextView tv_cancel;
    private EmojiBean emojiBean;
    private OnEmojiChangeListener listener;
    private RecyclerListView recyclerView;

    private Context context;


    public void setOnEmojiChangeListener(OnEmojiChangeListener listener) {
        this.listener = listener;
    }

    public EmojiStickerSetDialog(@NonNull Context context) {
        super(context,R.style.EmojiMenuDialog);
        this.context=context;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
    }

    private void initView(){
        View view= LayoutInflater.from(getContext()).inflate(R.layout.dialog_emoji_sticker_set,null);
        view.setMinimumWidth(ScreenUtils.getScreenWidth(getContext()));
        tv_name=view.findViewById(R.id.tv_name);
        tv_sticker_set=view.findViewById(R.id.tv_sticker_set);
        fl_content=view.findViewById(R.id.fl_content);
        tv_cancel=view.findViewById(R.id.tv_cancel);
        setContentView(view);
        Window window = getWindow();
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.x = 0;
        lp.y = 0;
        lp.width=ScreenUtils.getScreenWidth(getContext());
        window.setAttributes(lp);

        tv_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        this.tv_name.setText(this.emojiBean.getName());
        if(this.emojiBean.isDefault()){
            tv_sticker_set.setVisibility(View.GONE);
        }
        else{
            tv_sticker_set.setVisibility(View.VISIBLE);
            if(!this.emojiBean.isLocal()){
                tv_sticker_set.setText(getContext().getResources().getString(R.string.emoji_set_add)+" "+String.format(getContext().getResources().getString(R.string.emoji_gif_count),emojiBean.getGifSize()));
                tv_sticker_set.setTextColor(Color.parseColor("#007EE5"));
            }
            else{
                tv_sticker_set.setText(getContext().getResources().getString(R.string.emoji_set_remove)+" "+String.format(getContext().getResources().getString(R.string.emoji_gif_count),emojiBean.getGifSize()));
                tv_sticker_set.setTextColor(Color.parseColor("#FF3C30"));
            }
            tv_sticker_set.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                      if(listener!=null){
                          if(emojiBean.isLocal()){
                              emojiBean.setLocal(false);
                              listener.onEmojiRemove(emojiBean);
                          }
                          else{
                              emojiBean.setLocal(true);
                              listener.onEmojiAdd(emojiBean);
                          }
                      }
                      dismiss();
                }
            });
        }

        List<EmotionItemBean> list= EmojiUtils.convert2EmojiList(emojiBean,-1);
        if(list!=null && list.size()>0){
            EmojiNewAdapter adapter=new EmojiNewAdapter(list);

            recyclerView=new RecyclerListView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, recyclerView, 400, null);
                    return super.onInterceptTouchEvent(event) || result;
                }
            };
            recyclerView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return ContentPreviewViewer.getInstance().onTouch(event, recyclerView, 400, null, null);
                }
            });
            recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
            recyclerView.setClipToPadding(false);
            recyclerView.setLayoutManager(new GridLayoutManager(getContext(),4));
            recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    super.getItemOffsets(outRect, view, parent, state);
                    int position=parent.getChildAdapterPosition(view);
                    if(position>=4){
                        outRect.top = ScreenUtils.dip2px(getContext(), 10f);
                        outRect.bottom = ScreenUtils.dip2px(getContext(), 10f);
                    }
                }
            });
            recyclerView.setAdapter(adapter);
            fl_content.removeAllViews();
            fl_content.addView(recyclerView);
        }

    }

    public void setData(EmojiBean emojiBean){
        this.emojiBean=emojiBean;

    }
}
