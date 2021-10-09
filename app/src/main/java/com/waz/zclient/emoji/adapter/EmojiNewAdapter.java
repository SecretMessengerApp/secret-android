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

import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.waz.zclient.R;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.view.MyStickerEmojiCell;

import java.util.List;

public class EmojiNewAdapter extends BaseQuickAdapter<EmotionItemBean, BaseViewHolder> {

    public EmojiNewAdapter(@Nullable List<EmotionItemBean> data) {
        super(R.layout.item_emoji, data);
    }

    @Override
    protected void convert(BaseViewHolder helper, EmotionItemBean item) {
        MyStickerEmojiCell iv_emoji=helper.getView(R.id.iv_emoji);
        iv_emoji.setSticker(item,helper.itemView,false);
    }

}
