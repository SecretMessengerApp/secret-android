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

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.waz.zclient.emoji.OnEmojiChangeListener;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.fragment.EmojiFragment;
import com.waz.zclient.emoji.fragment.FavoriteFragment;
import com.waz.zclient.emoji.fragment.GifFragment;
import com.waz.zclient.emoji.fragment.RecentlyFragment;

import java.util.ArrayList;
import java.util.List;

public class TabFragmentAdapter extends OpenFragmentStatePagerAdapter<EmojiBean>{

    public static final int POSITION_GIF=-2;
    public static final int POSITION_FAVORITE=-1;
    public static final int POSITION_RECENT=0;
    public static final int POSITION_ADD=1001;
    public static final int POSITION_SETTING=1002;
    private List<EmojiBean> mDatas = new ArrayList<>();
    private OnEmojiChangeListener onEmojiChangeListener;

    public TabFragmentAdapter(FragmentManager fm,List<EmojiBean>list) {
        super(fm);
        mDatas.clear();
        mDatas=list;
    }

    @Override
    public int getCount() {
        return mDatas.size();
    }

    @Override
    public Fragment getItem(int position) {
        EmojiBean bean=getItemData(position);
        if(bean!=null){
            if(bean.getId()==POSITION_GIF){
                return new GifFragment(onEmojiChangeListener);
            }
            else if(bean.getId()==POSITION_FAVORITE){
                return new FavoriteFragment(onEmojiChangeListener);
            }
            else if(bean.getId()==POSITION_RECENT){
                return new RecentlyFragment(onEmojiChangeListener);
            }
            else{
                return EmojiFragment.apply(getItemData(position),onEmojiChangeListener);
            }
        }
        return null;

    }

    public void setOnEmojiChangeListener(OnEmojiChangeListener onEmojiChangeListener){
        this.onEmojiChangeListener=onEmojiChangeListener;
    }

    @Override
    EmojiBean getItemData(int position) {
        if(position>=0 && position<mDatas.size()){
            return mDatas.get(position);
        }
        else{
            return null;
        }
    }

    @Override
    boolean dataEquals(EmojiBean oldData, EmojiBean newData) {
        if(oldData==null){
            return newData==null;
        }
        else {
            return oldData.equals(newData);
        }
    }

    @Override
    int getDataPosition(EmojiBean data) {
        return mDatas.indexOf(data);
    }


}
