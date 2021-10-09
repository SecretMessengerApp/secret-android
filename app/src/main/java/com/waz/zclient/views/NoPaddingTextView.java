/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.waz.zclient.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import androidx.appcompat.widget.AppCompatTextView;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;

public class NoPaddingTextView extends AppCompatTextView {

    private final String TAG = NoPaddingTextView.class.getSimpleName();
    private TextPaint textPaint;
    private Rect rect;
    private int layoutWidth = -1;
    private String[] lineContents;
    private float line_space_height = 0.0f;
    private float line_space_height_mult = 1.0f;

    public NoPaddingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public NoPaddingTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        textPaint = new TextPaint();
        rect = new Rect();
        line_space_height = getLineSpacingExtra();
        line_space_height_mult = getLineSpacingMultiplier();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        Layout _layout = getLayout();
        if(_layout != null) {
            final String _tvContent = TextUtils.isEmpty(getText()) ? "" : getText().toString();
            final int _tvLenght = _tvContent.length();
            textPaint.getTextBounds(_tvContent, 0, _tvLenght, rect);
            textPaint.setTextSize(getTextSize());
            textPaint.setColor(getCurrentTextColor());
            getTextContentData(_layout);
            int _lineHeight = -rect.top + rect.bottom;
            initLayout(_layout);
            int[] _area = getWidthAndHeigt(widthMeasureSpec, heightMeasureSpec, (int) textPaint.measureText(_tvContent), _layout.getLineCount(), _lineHeight);
            setMeasuredDimension(_area[0], _area[1]);
        }
    }

    private void initLayout(Layout _layout) {
        if(layoutWidth < 0) {
            layoutWidth = _layout.getWidth();
        }
    }

    private int[] getWidthAndHeigt(int pWidthMeasureSpec, int pHeightMeasureSpec, int pWidth, int pLineCount, int pLineHeight) {
        int _widthMode = MeasureSpec.getMode(pWidthMeasureSpec);
        int _heightMode = MeasureSpec.getMode(pHeightMeasureSpec);
        int _widthSize = MeasureSpec.getSize(pWidthMeasureSpec);
        int _heightSize = MeasureSpec.getSize(pHeightMeasureSpec);
        int _width;
        int _height;
        if(_widthMode == MeasureSpec.EXACTLY) {
            _width = _widthSize;
        }else {
            _width = pWidth - rect.left;
        }
        if(_heightMode == MeasureSpec.EXACTLY) {
            _height = _heightSize;
        }else {
            if(pLineCount > 1) {
                _height = pLineHeight * pLineCount + (int) (line_space_height * line_space_height_mult * (pLineCount - 1));
            }else {
                _height = pLineHeight * pLineCount;
            }
        }
        int[] _area = {
                _width,
                _height
        };
        return _area;
    }

    private void getTextContentData(Layout _layout) {
        lineContents = new String[_layout.getLineCount()];
        for(int i = 0; i < _layout.getLineCount(); i++) {
            int _start = _layout.getLineStart(i);
            int _end = _layout.getLineEnd(i);
            lineContents[i] = getText().subSequence(_start, _end).toString();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float _line_height = -rect.top + rect.bottom;
        float _line_space = line_space_height * line_space_height_mult;
        for(int i = 0; i < lineContents.length; i++) {
            canvas.drawText(lineContents[i], 0, -rect.top + (_line_height + _line_space) * i, textPaint);
        }
    }
}
