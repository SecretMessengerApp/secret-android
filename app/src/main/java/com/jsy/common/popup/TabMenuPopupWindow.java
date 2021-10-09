/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.popup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.R;

import java.util.List;


public class TabMenuPopupWindow extends BasePopupWindow {

    private LinearLayout ll_container;
    private OnMenuClickListener listener;

    private View triangleView;
    private boolean bShowTriangle=false;

    public void setListener(OnMenuClickListener listener) {
        this.listener = listener;
    }

    public void showTriangle(boolean flag){
        this.bShowTriangle=flag;
    }

    public TabMenuPopupWindow(Context context) {
        super(context, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        this.triangleView=getTriangleIndicatorView(mContext,ScreenUtils.dip2px(mContext,16), ScreenUtils.dip2px(mContext,8f), Color.BLACK);
    }

    private View getTriangleIndicatorView(Context context, final float widthPixel, final float heightPixel, final int color) {
        ImageView indicator = new ImageView(context);
        Drawable drawable = new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                Path path = new Path();
                Paint paint = new Paint();
                paint.setColor(color);
                paint.setStyle(Paint.Style.FILL);
                path.moveTo(0f, 0f);
                path.lineTo(widthPixel, 0f);
                path.lineTo(widthPixel / 2, heightPixel);
                path.close();
                canvas.drawPath(path, paint);
            }

            @Override
            public void setAlpha(int alpha) {

            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {

            }

            @Override
            public int getOpacity() {
                return PixelFormat.TRANSLUCENT;
            }

            @Override
            public int getIntrinsicWidth() {
                return (int) widthPixel;
            }

            @Override
            public int getIntrinsicHeight() {
                return (int) heightPixel;
            }
        };
        indicator.setImageDrawable(drawable);
        return indicator;
    }

    @Override
    public int getLayoutId() {
        return R.layout.tab_menu_popup;
    }

    @Override
    protected void init() {
        super.init();
        ll_container=findViewById(R.id.ll_container);
    }

    public void setData(List<String>args){
        if(args==null || args.size()==0){
            return;
        }
        ll_container.removeAllViews();

        LinearLayout ll_popup=new LinearLayout(mContext);
        ll_popup.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,ScreenUtils.dip2px(mContext,36f)));
        ll_popup.setOrientation(LinearLayout.HORIZONTAL);
        ll_popup.setBackgroundResource(R.drawable.shape_solid_black_ovl_8dp);

        for (int i = 0; i < args.size(); i++) {
            if(i>0){
               View divider=new View(mContext);
               divider.setBackgroundColor(Color.parseColor("#ff444444"));
               LinearLayout.LayoutParams dividerLayoutParams=new LinearLayout.LayoutParams(ScreenUtils.dip2px(mContext,1f),LinearLayout.LayoutParams.MATCH_PARENT);
               int padding=ScreenUtils.dip2px(mContext,6f);
               dividerLayoutParams.topMargin=dividerLayoutParams.bottomMargin=padding;
               divider.setLayoutParams(dividerLayoutParams);

               ll_popup.addView(divider);

            }
            TextView tv=new TextView(mContext);
            LinearLayout.LayoutParams tvLayoutParams=new LinearLayout.LayoutParams(ScreenUtils.dip2px(mContext,62f),LinearLayout.LayoutParams.MATCH_PARENT);
            tv.setLayoutParams(tvLayoutParams);
            tv.setGravity(Gravity.CENTER);
            tv.setText(args.get(i));
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP,14f);
            tv.setTag(i);

            tv.setOnClickListener(this);

            ll_popup.addView(tv);
        }

        ll_container.addView(ll_popup);

        if (bShowTriangle && triangleView != null) {
            LinearLayout.LayoutParams layoutParams=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.CENTER;
            triangleView.setLayoutParams(layoutParams);

            ll_container.addView(triangleView);
        }

        updateMeasureInfo();

    }


    @Override
    public void onClick(View v) {
        super.onClick(v);
        if(v instanceof TextView){
            if (listener!=null) {
                int position=Integer.parseInt(v.getTag().toString());
                listener.onMenuClick(position);
            }
            dismiss();
        }
    }

    public interface OnMenuClickListener{
        void onMenuClick(int index);
    }
}
