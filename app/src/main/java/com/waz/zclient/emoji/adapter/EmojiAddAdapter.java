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
package com.waz.zclient.emoji.adapter;

import android.graphics.Color;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jsy.res.utils.ColorUtils;
import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.R;
import com.waz.zclient.emoji.Constants;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.utils.EmojiUtils;

import java.util.List;

public class EmojiAddAdapter extends BaseQuickAdapter<EmojiBean, BaseViewHolder> {

    public interface AddListener{
        void onAddEmojiButtonClick(int position);
        void onEmojiItemClick(int position);
    }

    private AddListener listener;
    public void setListener(AddListener listener) {
        this.listener = listener;
    }

    public EmojiAddAdapter(@Nullable List<EmojiBean> data) {
        super(R.layout.adapter_emoji_custom_add, data);
    }

    @Override
    protected void convert(final BaseViewHolder helper, final EmojiBean item) {
        helper.setText(R.id.tv_name, item.getName())
            .setText(R.id.tv_count, mContext.getResources().getString(R.string.emoji_gif_count, item.getGifSize()));
        RecyclerView rv_emoji = helper.getView(R.id.rv_emoji);
        rv_emoji.setLayoutManager(new GridLayoutManager(helper.itemView.getContext(), Constants.SPAN_COUNT));

        View view_divider=helper.getView(R.id.view_divider);
        if(helper.getAdapterPosition()<this.getItemCount()-1){
            view_divider.setVisibility(View.VISIBLE);
        }
        else{
            view_divider.setVisibility(View.INVISIBLE);
        }
        TextView tv_name = helper.getView(R.id.tv_name);
        TextView tv_add = helper.getView(R.id.tv_add);

        if (item.isLocal()) {
            tv_add.setText(R.string.emoji_already_add);
            tv_add.getLayoutParams().width= ScreenUtils.dip2px(helper.itemView.getContext(),78f);
            tv_add.setSelected(true);
            tv_add.setTextColor(ColorUtils.getColor(helper.itemView.getContext(),R.color.SecretBlue));
        } else {
            tv_add.setText(R.string.emoji_add);
            tv_add.getLayoutParams().width= ScreenUtils.dip2px(helper.itemView.getContext(),61f);
            tv_add.setSelected(false);
            tv_add.setTextColor(Color.WHITE);
        }

        List<EmotionItemBean> list = EmojiUtils.convert2EmojiList(item, Constants.SPAN_COUNT);
        EmojiNewAdapter adapter = new EmojiNewAdapter(list);
        adapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener(){
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if(listener!=null){
                    listener.onEmojiItemClick(helper.getAdapterPosition());
                }
            }
        });
        tv_name.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener!=null){
                    listener.onEmojiItemClick(helper.getAdapterPosition());
                }
            }
        });
        tv_add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(listener!=null){
                    listener.onAddEmojiButtonClick(helper.getAdapterPosition());
                }
            }
        });
        rv_emoji.setAdapter(adapter);

    }
}
