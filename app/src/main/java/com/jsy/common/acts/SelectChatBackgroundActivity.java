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
package com.jsy.common.acts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.IntegerRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.jsy.common.utils.DensityUtils;
import com.waz.zclient.BaseActivity;
import com.waz.zclient.R;
import com.waz.zclient.ui.text.TypefaceTextView;
import com.jsy.res.theme.ThemeUtils;
import com.waz.zclient.utils.SpUtils;
import com.jsy.res.utils.ViewUtils;

public class SelectChatBackgroundActivity extends BaseActivity implements View.OnClickListener {

    private ViewPager select_viewpager;

    private ViewPager vpItems;

    private static final int[] BACKGROUNDS_LIGHT = new int[]{
        R.drawable.select_back_default_light,
        R.color.white,
        R.drawable.select_back_three,
        R.drawable.select_back_four,
        R.drawable.select_back_five};
    private static final int[] BACKGROUNDS_DARK  = new int[]{
        R.drawable.select_back_default_dark,
        R.color.black,
        R.drawable.select_back_three_dark,
        R.drawable.select_back_four_dark,
        R.drawable.select_back_five_dark};

    private static final int[] BACKGROUND_THUMBNAILS_LIGHT = new int[]{
        R.drawable.select_item_def_light,
        R.drawable.select_item_white,
        R.drawable.select_item_three,
        R.drawable.select_item_four,
        R.drawable.select_item_five_white};
    private static final int[] BACKGROUND_THUMBNAILS_DARK  = new int[]{
        R.drawable.select_item_def_dark,
        R.drawable.select_item_black,
        R.drawable.select_item_three,
        R.drawable.select_item_four,
        R.drawable.select_item_five_black};

    public static final void startSelfForResult(Activity activity, int requestCode, String spKey) {
        activity.startActivityForResult(
            new Intent(activity, SelectChatBackgroundActivity.class)
                .putExtra("spKey", spKey), requestCode);
    }

    private String spKey = "";

    @Override
    public boolean canUseSwipeBackLayout() {
        return true;
    }

    @Override
    public boolean enableWhiteStatusBar() {
        return false;
    }

    @Override
    public void customInitStatusBar() {
        initStatusBar(R.id.toolbar);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_background);

        spKey = getIntent().getStringExtra("spKey");

        final boolean isDark          = ThemeUtils.isDarkTheme(this);
        final int[]   showBackgrounds = isDark ? BACKGROUNDS_DARK : BACKGROUNDS_LIGHT;
        final int[]   showBackgroundThumbnails = isDark ? BACKGROUND_THUMBNAILS_DARK : BACKGROUND_THUMBNAILS_LIGHT;
        final int itemWidthHeight = DensityUtils.dp2px(getApplicationContext(),60.0F);

        Toolbar toolbar = ViewUtils.getView(this, R.id.toolbar);
        toolbar.setNavigationIcon(isDark ? R.drawable.action_back_light : R.drawable.action_back_dark);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        setStatusBarColorInt(Color.TRANSPARENT, !isDark);

        TypefaceTextView select_title = ViewUtils.getView(this, R.id.select_title);
        select_title.setTextColor(ContextCompat.getColor(this, isDark ? R.color.white : R.color.black));

        TextView tv_cancel = ViewUtils.getView(this, R.id.tv_cancel);
        tv_cancel.setOnClickListener(this);

        TextView tv_apply = ViewUtils.getView(this, R.id.tv_apply);
        tv_apply.setOnClickListener(this);

        vpItems = ViewUtils.getView(this, R.id.vpItems);

        select_viewpager = ViewUtils.getView(this, R.id.select_view_pager);
        select_viewpager.setAdapter(new PagerAdapter() {

            @Override
            public int getCount() {
                return showBackgrounds.length;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View view = getLayoutInflater().inflate(R.layout.select_viewpager_item_one, null);
                view.setBackgroundResource(showBackgrounds[position]);
                TextView tvText1 = view.findViewById(R.id.tvText1);
                TextView tvText2 = view.findViewById(R.id.tvText2);

                final int textColor = ContextCompat.getColor(SelectChatBackgroundActivity.this, isDark ? R.color.white : R.color.black);
                tvText1.setTextColor(textColor);
                tvText2.setTextColor(textColor);

                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                container.removeView((View) object);
            }
        });

        ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (vpItems.getCurrentItem() != position) {
                    vpItems.setCurrentItem(position, true);
                }
                if (select_viewpager.getCurrentItem() != position) {
                    select_viewpager.setCurrentItem(position, true);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        };

        select_viewpager.addOnPageChangeListener(onPageChangeListener);

        vpItems.addOnPageChangeListener(onPageChangeListener);


//        vpItems.setPageTransformer(false, new ZoomOutPageTransformer());
        vpItems.setOffscreenPageLimit(Math.min(5, showBackgrounds.length));
        vpItems.setPageMargin(-(getResources().getDisplayMetrics().widthPixels - itemWidthHeight * 2));
        RelativeLayout.LayoutParams vpLayoutParams = (RelativeLayout.LayoutParams) vpItems.getLayoutParams();
        vpLayoutParams.width = -1;
        vpLayoutParams.height = itemWidthHeight;
        vpItems.setLayoutParams(vpLayoutParams);

        vpItems.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return showBackgrounds.length;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {

                final RelativeLayout view = (RelativeLayout) getLayoutInflater().inflate(
                    R.layout.lay_select_chat_background_item, null);
                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(itemWidthHeight, itemWidthHeight);
                view.setLayoutParams(layoutParams);

                ImageView image_view = view.findViewById(R.id.image_view);
                image_view.setImageResource(showBackgroundThumbnails[position]);
                RelativeLayout.LayoutParams layoutParams2 = (RelativeLayout.LayoutParams) image_view.getLayoutParams();
                layoutParams2.width = layoutParams.width;
                layoutParams2.height = layoutParams.height;
                image_view.setLayoutParams(layoutParams2);
                container.addView(view);
                return view;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position,
                                    @NonNull Object object) {
                container.removeView((View) object);

            }
        });

        final int index = getIndex(SpUtils.getString(this, SpUtils.SP_NAME_NORMAL, spKey, null));

        select_viewpager.setCurrentItem(index);
        vpItems.setCurrentItem(index);
    }

    private static int getIndex(@Nullable String indexTag){
        int index = 0;
        if (indexTag != null) {
            try {
                index = Integer.parseInt(indexTag) % Math.min(BACKGROUNDS_LIGHT.length,BACKGROUNDS_DARK.length);
            } catch (NumberFormatException ignored) {
            }
        }
        return index;
    }

    @IntegerRes
    public static int getBackground(Context context, @Nullable String indexTag) {
        int index = getIndex(indexTag);
        return ThemeUtils.isDarkTheme(context) ? BACKGROUNDS_DARK[index] : BACKGROUNDS_LIGHT[index];
    }

    @IntegerRes
    public static int getDefaultBackground(Context context) {
        return getBackground(context, null);
    }

    @Override
    public void onClick(View view) {
        int vId = view.getId();

        if (vId == R.id.tv_cancel) {
            finish();
        } else if (vId == R.id.tv_apply) {
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, spKey, String.valueOf(vpItems.getCurrentItem()));
            setResult(RESULT_OK);
            finish();
        } else {

        }
    }

    @Override
    public void onBackPressed() {
         super.onBackPressed();
    }
}
