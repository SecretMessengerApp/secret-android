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
package com.waz.zclient.emoji.view;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.j256.ormlite.dao.Dao;
import com.jsy.common.httpapi.EmojiGifService;
import com.jsy.common.httpapi.SimpleHttpListener;
import com.jsy.common.utils.MainHandler;
import com.jsy.common.utils.RxJavaUtil;
import com.jsy.common.utils.ScreenUtils;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.zclient.R;
import com.waz.zclient.ZApplication;
import com.waz.zclient.emoji.Constants;
import com.waz.zclient.emoji.OnEmojiChangeListener;
import com.waz.zclient.emoji.activity.EmojiManagerActivity;
import com.waz.zclient.emoji.adapter.EmojiAddAdapter;
import com.waz.zclient.emoji.adapter.TabFragmentAdapter;
import com.waz.zclient.emoji.bean.EmojiBean;
import com.waz.zclient.emoji.bean.EmojiResponse;
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog;
import com.waz.zclient.emoji.utils.EmojiUtils;
import com.waz.zclient.emoji.utils.GifSavedDaoHelper;
import com.waz.zclient.utils.MainActivityUtils;
import com.waz.zclient.utils.SpUtils;
import com.jsy.res.utils.ViewUtils;

import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;
import java.util.List;

public class EmojiKeyboardCustomLayout extends LinearLayout implements OnEmojiChangeListener, TabLayout.BaseOnTabSelectedListener {

    private TabLayout mEmojiTabLayout;
    private ViewPager mEmojiViewPager;
    private FragmentManager fragmentManager;
    private TabFragmentAdapter adapter;
    private List<EmojiBean> emojiDatas = new ArrayList<>();

    private int stickerStartTabPosition = 0;
    private int[]resIds={R.drawable.icon_emoji_menu_gif,R.drawable.icon_emoji_menu_favorite,R.drawable.icon_emoji_menu_recent};


    public EmojiKeyboardCustomLayout(Context context) {
        this(context, null);
    }

    public EmojiKeyboardCustomLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EmojiKeyboardCustomLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mEmojiTabLayout = ViewUtils.getView(this, R.id.emoji_tabLayout);
        mEmojiViewPager = ViewUtils.getView(this, R.id.emoji_viewPager);
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        this.fragmentManager = fragmentManager;
    }

    public void loadData() {

        EmojiGifService.getInstance().getEmojiGifs(new SimpleHttpListener<EmojiResponse>() {
            @Override
            public void onFail(int code, String err) {
                super.onFail(code, err);
                LogUtils.d("JACK8", "NormalServiceAPI 11,onFail:" + code + "-" + err);
                updateUI();
            }

            @Override
            public void onSuc(EmojiResponse data, String orgJson) {
                super.onSuc(data, orgJson);
                LogUtils.d("JACK8", "NormalServiceAPI 22,onSuc:" + orgJson);
                updateInternal(data);
            }
        });
    }

    private void updateInternal(EmojiResponse data) {
        Constants.EMOJI_BASE_URL = data.getPath();
        final List<EmojiBean> remoteEmojiList = data.getZips();
        if (remoteEmojiList != null && remoteEmojiList.size() > 0) {
            RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<Integer>() {
                @Override
                public Integer doInBackground() {
                    return EmojiUtils.updateAllDbEmoji(remoteEmojiList);
                }

                @Override
                public void onFinish(Integer result) {
                    updateUI();
                }

                @Override
                public void onError(Throwable e) {
                    updateUI();
                }
            });

        } else {
            updateUI();
        }
    }

    private void updateUI() {
        Log.d("JACK8", "updateUI() called");
        stickerStartTabPosition = 0;
        GifSavedDaoHelper.unregisterObserver(databaseObserver);
        GifSavedDaoHelper.registerObserver(databaseObserver);
        if (mEmojiTabLayout.getTabCount() > 0) {
            mEmojiTabLayout.removeOnTabSelectedListener(this);
            mEmojiTabLayout.removeAllTabs();
        }
        List<EmojiBean> tempEmojiList = new ArrayList<>();

        if (adapter != null) {
            if (emojiDatas.size() > 0) {
                emojiDatas.clear();
                adapter.notifyDataSetChanged();
            }
        }

        databaseObserver.onChange();

        List<EmojiBean> emojiList = EmojiUtils.getAllLocalEmoji();
        for (EmojiBean emotionRes : emojiList) {
            tempEmojiList.add(emotionRes);
            mEmojiTabLayout.addTab(mEmojiTabLayout.newTab().setCustomView(getTabView(emotionRes)).setTag(emotionRes.getId()));
        }

        mEmojiTabLayout.addTab(mEmojiTabLayout.newTab().setCustomView(getTabView(R.drawable.icon_emoji_menu_add)).setTag(TabFragmentAdapter.POSITION_ADD));
        mEmojiTabLayout.addTab(mEmojiTabLayout.newTab().setCustomView(getTabView(R.drawable.icon_emoji_menu_settings)).setTag(TabFragmentAdapter.POSITION_SETTING));

        emojiDatas.addAll(tempEmojiList);
        mEmojiTabLayout.addOnTabSelectedListener(this);
        if (adapter == null) {
            adapter = new TabFragmentAdapter(fragmentManager, emojiDatas);
            mEmojiViewPager.setAdapter(adapter);
            adapter.setOnEmojiChangeListener(this);
            mEmojiViewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(mEmojiTabLayout));
        } else {
            adapter.notifyDataSetChanged();
        }

    }

    private View getTabView(int resId) {
        View tabView = LayoutInflater.from(getContext()).inflate(R.layout.emoji_custom_tab_item, this, false);
        ImageView contentImageView = tabView.findViewById(R.id.content_imageView);
        contentImageView.setImageResource(resId);
        return tabView;
    }

    private View getTabView(EmojiBean emojiBean) {
        View tabView = LayoutInflater.from(getContext()).inflate(R.layout.emoji_custom_tab_item, this, false);
        RLottieImageView lottieImageView = tabView.findViewById(R.id.content_imageView);
        EmojiUtils.loadSticker(getContext(), emojiBean, lottieImageView, ScreenUtils.dip2px(getContext(), 35f), ScreenUtils.dip2px(getContext(), 35f));
        return tabView;
    }

    @Override
    public void onEmojiAdd(final EmojiBean emojiBean) {
        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<Integer>() {
            @Override
            public Integer doInBackground() {
                return EmojiUtils.AddEmoji2Db(emojiBean);
            }

            @Override
            public void onFinish(Integer result) {

                int index = stickerStartTabPosition + EmojiUtils.getDefaultEmojiSize();
                LogUtils.d("JACK8", "onEmojiAdd,index:"+index+",stickerStartTabPosition:"+stickerStartTabPosition);
                mEmojiTabLayout.addTab(mEmojiTabLayout.newTab().setCustomView(getTabView(emojiBean)).setTag(emojiBean.getId()), index);
                emojiDatas.add(index, emojiBean);
                adapter.notifyDataSetChanged();

            }

            @Override
            public void onError(Throwable e) {
                LogUtils.d("+++++onEmojiAdd error:" + e.getMessage());
            }
        });
    }

    @Override
    public void onEmojiRemove(final EmojiBean emojiBean) {

        RxJavaUtil.run(new RxJavaUtil.OnRxAndroidListener<Integer>() {
            @Override
            public Integer doInBackground() {
                return EmojiUtils.RemoveEmojiInDb(emojiBean);
            }

            @Override
            public void onFinish(Integer result) {
                LogUtils.d("+++++onEmojiRemove onFinish");
                removeTab(emojiBean);
                emojiDatas.remove(emojiBean);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Throwable e) {
                LogUtils.d("+++++onEmojiRemove error:" + e.getMessage());
            }
        });
    }

    @Override
    public void onEmojiChanged() {
        LogUtils.d("JACK8", "onEmojiChanged() called");
        updateUI();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        int tabPosition = tab.getPosition();
        if (tabPosition < mEmojiViewPager.getAdapter().getCount()) {
            mEmojiViewPager.setCurrentItem(tabPosition);
        }
        else if(tabPosition==mEmojiTabLayout.getTabCount()-2){
            addEmoji();
        }
        else if (tabPosition == mEmojiTabLayout.getTabCount()-1) {
            Activity activity = (Activity) getContext();
            activity.startActivityForResult(new Intent(getContext(), EmojiManagerActivity.class), MainActivityUtils.REQUEST_CODE_IntentType_EMOJI_SETTING());
        }
    }


    private void addEmoji() {
        int maxHeight=ScreenUtils.getScreenHeight(getContext());
        BottomSheetDialog dialog=new StrongBottomSheetDialog(getContext(),maxHeight/2,maxHeight);
        View view=LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_emoji,null,false);
        dialog.setContentView(view);
        RecyclerView bottomSheetRecyclerView= view.findViewById(R.id.bottomSheetRecyclerView);
        List<EmojiBean>emojiBeanList=EmojiUtils.getDbEmojiByDefault(false);
        if(emojiBeanList!=null && emojiBeanList.size()>0) {
            final EmojiAddAdapter addAdapter = new EmojiAddAdapter(emojiBeanList);
            addAdapter.setListener(new EmojiAddAdapter.AddListener() {
                @Override
                public void onAddEmojiButtonClick(final int position) {
                    try {
                        EmojiBean emojiBean = addAdapter.getItem(position);
                        if (emojiBean.isLocal()) {
                            emojiBean.setLocal(false);
                            EmojiKeyboardCustomLayout.this.onEmojiRemove(emojiBean);
                        } else {
                            emojiBean.setLocal(true);
                            EmojiKeyboardCustomLayout.this.onEmojiAdd(emojiBean);
                        }
                        addAdapter.notifyItemChanged(position);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onEmojiItemClick(final int position) {
                    try {
                        EmojiBean emojiBean = addAdapter.getItem(position);
                        EmojiStickerSetDialog dialog=new EmojiStickerSetDialog(getContext());
                        dialog.setOnEmojiChangeListener(new OnEmojiChangeListener() {
                            @Override
                            public void onEmojiAdd(EmojiBean emojiBean) {
                                addAdapter.notifyItemChanged(position);
                                EmojiKeyboardCustomLayout.this.onEmojiAdd(emojiBean);
                            }

                            @Override
                            public void onEmojiRemove(EmojiBean emojiBean) {
                                addAdapter.notifyItemChanged(position);
                                EmojiKeyboardCustomLayout.this.onEmojiRemove(emojiBean);
                            }

                            @Override
                            public void onEmojiChanged() {

                            }
                        });
                        dialog.setData(emojiBean);
                        dialog.show();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            addAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
                @Override
                public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                    if (view.getId() == R.id.tv_add) {

                    }
                }
            });
            bottomSheetRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            bottomSheetRecyclerView.setAdapter(addAdapter);
        }
        dialog.show();

    }



    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        int tabPosition = tab.getPosition();
        if(tabPosition==mEmojiTabLayout.getTabCount()-2){
            addEmoji();
        }
    }


    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        GifSavedDaoHelper.unregisterObserver(databaseObserver);
    }

    private Dao.DaoObserver databaseObserver = new Dao.DaoObserver() {
        @Override
        public void onChange() {
            LogUtils.d("JACK8","onChange,thread:"+Thread.currentThread().getName());
            MainHandler.getInstance().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    List gifDatas = GifSavedDaoHelper.getCount(SpUtils.getUserId(ZApplication.getInstance()));
                    for (int i = 0; i <gifDatas.size() ; i++) {
                        EmojiBean bean = getEmojiBean(TabFragmentAdapter.POSITION_GIF+i);
                        long num=Long.parseLong(gifDatas.get(i).toString());
                        if(num>0){
                            if(bean==null){
                                int insertPosition=Math.min(i,stickerStartTabPosition);
                                if(insertPosition==1 && stickerStartTabPosition==1){
                                    if(emojiDatas.get(0).getId()==TabFragmentAdapter.POSITION_RECENT){
                                        insertPosition=0;
                                    }
                                }
                                mEmojiTabLayout.addTab(mEmojiTabLayout.newTab().setCustomView(getTabView(resIds[i])).setTag(TabFragmentAdapter.POSITION_GIF + i), insertPosition);
                                emojiDatas.add(insertPosition, new EmojiBean(TabFragmentAdapter.POSITION_GIF + i));
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }
                                stickerStartTabPosition++;
                            }
                        }
                        else{
                            if(bean!=null){
                                removeTab(bean);
                                emojiDatas.remove(bean);
                                if(adapter!=null) {
                                    adapter.notifyDataSetChanged();
                                }
                                stickerStartTabPosition--;
                                if(stickerStartTabPosition<0){
                                    stickerStartTabPosition=0;
                                }
                            }
                        }
                    }
                }
            });

        }
    };

    private EmojiBean getEmojiBean(int id) {
        if (emojiDatas != null && emojiDatas.size() > 0) {
            for (int i = 0; i < emojiDatas.size(); i++) {
                if (emojiDatas.get(i).getId() == id) {
                    return emojiDatas.get(i);
                }
            }
        }
        return null;
    }

    private void removeTab(EmojiBean emojiBean) {
        int index = -1;
        for (int i = 0; i < mEmojiTabLayout.getTabCount(); i++) {
            Object obj = mEmojiTabLayout.getTabAt(i).getTag();
            if (obj != null) {
                int tag = Integer.parseInt(obj.toString());
                if (tag == emojiBean.getId()) {
                    index = i;
                    break;
                }
            }
        }
        if (index >= 0) {
            mEmojiTabLayout.removeTabAt(index);
        }
        LogUtils.d("JACK8", "removeTab,index:" + index);
    }
}
