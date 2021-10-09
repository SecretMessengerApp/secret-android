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
package com.waz.zclient;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.jsy.common.adapter.ViewsViewPagerAdapter;
import com.jsy.common.views.DepthPageTransformer;
import com.waz.zclient.pages.BaseFragment;

import java.util.ArrayList;
import java.util.List;

public class LaunchGuideFragment extends BaseFragment<LaunchGuideFragment.Containner> {

    private ViewPager mIn_vp;
    private LinearLayout mIn_ll;
    private List<View> mViewList;
    private ImageView mLight_dots;
    private int mDistance;
    private ImageView mOne_dot;
    private ImageView mTwo_dot;
    private ImageView mThree_dot;
    private TextView mBtn_next;


    public static final String TAG = LaunchGuideFragment.class.getSimpleName();

    public static LaunchGuideFragment newInstance(){
        LaunchGuideFragment launchGuideActivity = new LaunchGuideFragment();
        Bundle args = new Bundle();

        launchGuideActivity.setArguments(args);
        return launchGuideActivity;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_lanuch_guide,container,false);
        initView(rootView);
        initData(inflater);
        mIn_vp.setAdapter(new ViewsViewPagerAdapter(mViewList));
        moveDots();
        mIn_vp.setPageTransformer(true, new DepthPageTransformer());
        setClickListener();
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void moveDots() {
        mLight_dots.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mDistance = mIn_ll.getChildAt(1).getLeft() - mIn_ll.getChildAt(0).getLeft();
                mLight_dots.getViewTreeObserver()
                        .removeGlobalOnLayoutListener(this);
            }
        });
        mIn_vp.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                float leftMargin = mDistance * (position + positionOffset);
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mLight_dots.getLayoutParams();
                params.leftMargin = (int) leftMargin;
                mLight_dots.setLayoutParams(params);
            }

            @Override
            public void onPageSelected(int position) {
                if (position == 2) {
                    mBtn_next.setVisibility(View.VISIBLE);
                }
                if (position != 2 && mBtn_next.getVisibility() == View.VISIBLE) {
                    mBtn_next.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    private void setClickListener() {
        mOne_dot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIn_vp.setCurrentItem(0);
            }
        });
        mTwo_dot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIn_vp.setCurrentItem(1);
            }
        });
        mThree_dot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mIn_vp.setCurrentItem(2);
            }
        });

        mBtn_next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getContainer()!=null) {
                    getContainer().clickStart();
                }

            }
        });
    }


    private void initData(LayoutInflater inflater) {
        mViewList = new ArrayList<>();
        View view1 = inflater.inflate(R.layout.guide_indicator1, null);
        View view2 = inflater.inflate(R.layout.guide_indicator2, null);
        View view3 = inflater.inflate(R.layout.guide_indicator3, null);
        mViewList.add(view1);
        mViewList.add(view2);
        mViewList.add(view3);
    }

    private void initView(View rootView) {
        mIn_vp = rootView.findViewById(R.id.in_viewpager);
        mIn_ll = rootView.findViewById(R.id.in_ll);
        mLight_dots = rootView.findViewById(R.id.iv_light_dots);
        mBtn_next = rootView.findViewById(R.id.bt_next);
        mOne_dot = rootView.findViewById(R.id.one_dot);
        mTwo_dot = rootView.findViewById(R.id.two_dot);
        mThree_dot = rootView.findViewById(R.id.three_dot);
    }


    public interface Containner{
        void clickStart();
    }
}
