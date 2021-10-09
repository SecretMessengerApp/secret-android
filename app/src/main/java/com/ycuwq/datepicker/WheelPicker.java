/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.ycuwq.datepicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;
import com.waz.zclient.R;
import com.ycuwq.datepicker.util.LinearGradient;
import com.waz.zclient.ui.utils.ColorUtils;

import java.text.Format;
import java.util.List;


@SuppressWarnings({"FieldCanBeLocal", "unused", "SameParameterValue"})
public class WheelPicker<T> extends View {

	private List<T> mDataList;

	private Format mDataFormat;

	@ColorInt
	private int mTextColor;

	private int mTextSize;

	private Paint mTextPaint;

	private boolean mIsTextGradual;

	@ColorInt
	private int mSelectedItemTextColor;

	private int mSelectedItemTextSize;

	private Paint mSelectedItemPaint;

	private String mIndicatorText;

	@ColorInt
	private int mIndicatorTextColor;

	private int mIndicatorTextSize;

	private Paint mIndicatorPaint;

	private Paint mPaint;

	private int mTextMaxWidth, mTextMaxHeight;

	private String mItemMaximumWidthText;

	private int mHalfVisibleItemCount;

	private int mItemHeightSpace, mItemWidthSpace;

	private int mItemHeight;

	private int mCurrentPosition;

    private boolean mIsZoomInSelectedItem;

	private boolean mIsShowCurtain;

    @ColorInt
    private int mCurtainColor;

	private boolean mIsShowCurtainBorder;

	@ColorInt
	private int mCurtainBorderColor;

	private Rect mDrawnRect;

	private Rect mSelectedItemRect;

	private int mFirstItemDrawX, mFirstItemDrawY;

	private int mCenterItemDrawnY;

	private Scroller mScroller;

	private int mTouchSlop;

	private boolean mTouchSlopFlag;

	private VelocityTracker mTracker;

	private int mTouchDownY;

	private int mScrollOffsetY;

	private int mLastDownY;

	private boolean mIsCyclic = true;

	private int mMaxFlingY, mMinFlingY;

	private int mMinimumVelocity = 50, mMaximumVelocity = 12000;

	private boolean mIsAbortScroller;

	private LinearGradient mLinearGradient;

    private Handler mHandler = new Handler();

	private OnWheelChangeListener<T> mOnWheelChangeListener;

	private Runnable mScrollerRunnable = new Runnable() {
		@Override
		public void run() {

			if (mScroller.computeScrollOffset()) {

                mScrollOffsetY = mScroller.getCurrY();
				postInvalidate();
				mHandler.postDelayed(this, 16);
			}
			if (mScroller.isFinished() || (mScroller.getFinalY() == mScroller.getCurrY()
                    && mScroller.getFinalX() == mScroller.getCurrX())) {

				if (mItemHeight == 0) {
					return;
				}
				int position = -mScrollOffsetY / mItemHeight;
				position = fixItemPosition(position);
				if (mCurrentPosition != position) {
                    mCurrentPosition = position;
                    if (mOnWheelChangeListener == null) {
                        return;
                    }
                    mOnWheelChangeListener.onWheelSelected(mDataList.get(position),
                            position);
				}
			}
		}
	};

	public WheelPicker(Context context) {
		this(context, null);
	}

	public WheelPicker(Context context, @Nullable AttributeSet attrs) {
		this(context, attrs,0);
	}

	public WheelPicker(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		initAttrs(context, attrs);
		initPaint();
		mLinearGradient = new LinearGradient(mTextColor, mSelectedItemTextColor);
		mDrawnRect = new Rect();
		mSelectedItemRect = new Rect();
		mScroller = new Scroller(context);

		ViewConfiguration configuration = ViewConfiguration.get(context);
		mTouchSlop = configuration.getScaledTouchSlop();
	}

	private void initAttrs(Context context, @Nullable AttributeSet attrs) {
	    if (attrs == null) {
	        return;
        }
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.WheelPicker);
		mTextSize = a.getDimensionPixelSize(R.styleable.WheelPicker_itemTextSize,
				getResources().getDimensionPixelSize(R.dimen.WheelItemTextSize));
		mTextColor = a.getColor(R.styleable.WheelPicker_itemTextColor,
				Color.BLACK);
		mIsTextGradual = a.getBoolean(R.styleable.WheelPicker_textGradual, true);
		mIsCyclic = a.getBoolean(R.styleable.WheelPicker_wheelCyclic, false);
		mHalfVisibleItemCount = a.getInteger(R.styleable.WheelPicker_halfVisibleItemCount, 2);
		mItemMaximumWidthText = a.getString(R.styleable.WheelPicker_itemMaximumWidthText);
		mSelectedItemTextColor = a.getColor(R.styleable.WheelPicker_selectedTextColor, Color.parseColor("#33aaff"));
        mSelectedItemTextSize = a.getDimensionPixelSize(R.styleable.WheelPicker_selectedTextSize,
                getResources().getDimensionPixelSize(R.dimen.WheelSelectedItemTextSize));
        mCurrentPosition = a.getInteger(R.styleable.WheelPicker_currentItemPosition, 0);
        mItemWidthSpace = a.getDimensionPixelSize(R.styleable.WheelPicker_itemWidthSpace,
                getResources().getDimensionPixelOffset(R.dimen.WheelItemWidthSpace));
        mItemHeightSpace = a.getDimensionPixelSize(R.styleable.WheelPicker_itemHeightSpace,
                getResources().getDimensionPixelOffset(R.dimen.WheelItemHeightSpace));
        mIsZoomInSelectedItem = a.getBoolean(R.styleable.WheelPicker_zoomInSelectedItem, true);
        mIsShowCurtain = a.getBoolean(R.styleable.WheelPicker_wheelCurtain, true);
        mCurtainColor = a.getColor(R.styleable.WheelPicker_wheelCurtainColor,
            ColorUtils.getAttrColor(context,R.attr.SecretPrimaryTextColor));
        mIsShowCurtainBorder = a.getBoolean(R.styleable.WheelPicker_wheelCurtainBorder, true);
        mCurtainBorderColor = a.getColor(R.styleable.WheelPicker_wheelCurtainBorderColor, Color.BLACK);
        mIndicatorText = a.getString(R.styleable.WheelPicker_indicatorText);
        mIndicatorTextColor = a.getColor(R.styleable.WheelPicker_indicatorTextColor, mSelectedItemTextColor);
        mIndicatorTextSize = a.getDimensionPixelSize(R.styleable.WheelPicker_indicatorTextSize, mTextSize);
		a.recycle();
	}

	public void computeTextSize() {
        mTextMaxWidth = mTextMaxHeight = 0;
		if (mDataList.size() == 0) {
			return;
		}

        mPaint.setTextSize(mSelectedItemTextSize > mTextSize ? mSelectedItemTextSize : mTextSize);

        if (!TextUtils.isEmpty(mItemMaximumWidthText)) {
            mTextMaxWidth = (int) mPaint.measureText(mItemMaximumWidthText);
		} else {
			mTextMaxWidth = (int) mPaint.measureText(mDataList.get(0).toString());
		}
		Paint.FontMetrics metrics = mPaint.getFontMetrics();
		mTextMaxHeight = (int) (metrics.bottom - metrics.top);
	}

	private void initPaint() {
		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.LINEAR_TEXT_FLAG);
		mPaint.setStyle(Paint.Style.FILL);
		mPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.LINEAR_TEXT_FLAG);
        mTextPaint.setStyle(Paint.Style.FILL);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        mTextPaint.setColor(mTextColor);
        mTextPaint.setTextSize(mTextSize);
        mSelectedItemPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.LINEAR_TEXT_FLAG);
        mSelectedItemPaint.setStyle(Paint.Style.FILL);
        mSelectedItemPaint.setTextAlign(Paint.Align.CENTER);
        mSelectedItemPaint.setColor(mSelectedItemTextColor);
        mSelectedItemPaint.setTextSize(mSelectedItemTextSize);
        mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.LINEAR_TEXT_FLAG);
        mIndicatorPaint.setStyle(Paint.Style.FILL);
        mIndicatorPaint.setTextAlign(Paint.Align.LEFT);
        mIndicatorPaint.setColor(mIndicatorTextColor);
        mIndicatorPaint.setTextSize(mIndicatorTextSize);
	}

	private int measureSize(int specMode, int specSize, int size) {
		if (specMode == MeasureSpec.EXACTLY) {
			return specSize;
		} else {
			return Math.min(specSize, size);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int specWidthSize = MeasureSpec.getSize(widthMeasureSpec);
		int specWidthMode = MeasureSpec.getMode(widthMeasureSpec);
		int specHeightSize = MeasureSpec.getSize(heightMeasureSpec);
		int specHeightMode = MeasureSpec.getMode(heightMeasureSpec);

		int width = mTextMaxWidth + mItemWidthSpace;
		int height = (mTextMaxHeight + mItemHeightSpace) * getVisibleItemCount();

		width += getPaddingLeft() + getPaddingRight();
		height += getPaddingTop() + getPaddingBottom();
		setMeasuredDimension(measureSize(specWidthMode, specWidthSize, width),
				measureSize(specHeightMode, specHeightSize, height));
	}

	private void computeFlingLimitY() {
        mMinFlingY = mIsCyclic ? Integer.MIN_VALUE :
				- mItemHeight * (mDataList.size() - 1);
		mMaxFlingY = mIsCyclic ? Integer.MAX_VALUE : 0;
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
		mDrawnRect.set(getPaddingLeft(), getPaddingTop(),
				getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
		mItemHeight = mDrawnRect.height() / getVisibleItemCount();
		mFirstItemDrawX = mDrawnRect.centerX();
		mFirstItemDrawY = (int) ((mItemHeight - (mSelectedItemPaint.ascent() + mSelectedItemPaint.descent())) / 2);
		mSelectedItemRect.set(getPaddingLeft(), mItemHeight * mHalfVisibleItemCount,
				getWidth() - getPaddingRight(), mItemHeight + mItemHeight * mHalfVisibleItemCount);
		computeFlingLimitY();
		mCenterItemDrawnY = mFirstItemDrawY + mItemHeight * mHalfVisibleItemCount;

		mScrollOffsetY = -mItemHeight * mCurrentPosition;
	}

    private int fixItemPosition(int position) {
        if (position < 0) {
            position = mDataList.size() + (position % mDataList.size());

        }
        if (position >= mDataList.size()){
            position = position % mDataList.size();
        }
        return position;
    }

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		mPaint.setTextAlign(Paint.Align.CENTER);
		if (mIsShowCurtain) {
			mPaint.setStyle(Paint.Style.FILL);
			mPaint.setColor(mCurtainColor);
			canvas.drawRect(mSelectedItemRect, mPaint);
		}
		if (mIsShowCurtainBorder) {
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setColor(mCurtainBorderColor);
			canvas.drawRect(mSelectedItemRect, mPaint);
			canvas.drawRect(mDrawnRect, mPaint);
		}
		int drawnSelectedPos = - mScrollOffsetY / mItemHeight;
		mPaint.setStyle(Paint.Style.FILL);
		for (int drawDataPos = drawnSelectedPos - mHalfVisibleItemCount - 1;
            drawDataPos <= drawnSelectedPos + mHalfVisibleItemCount + 1; drawDataPos++) {
			int position = drawDataPos;
            if (mIsCyclic) {
				position = fixItemPosition(position);
			} else {
				if (position < 0 || position > mDataList.size() - 1) {
					continue;
				}
			}

			T data = mDataList.get(position);
			int itemDrawY = mFirstItemDrawY + (drawDataPos + mHalfVisibleItemCount) * mItemHeight + mScrollOffsetY;
			int distanceY = Math.abs(mCenterItemDrawnY - itemDrawY);

			if (mIsTextGradual) {
                if (distanceY < mItemHeight) {
                    float colorRatio = 1 - (distanceY / (float) mItemHeight);
                    mSelectedItemPaint.setColor(mLinearGradient.getColor(colorRatio));
                    mTextPaint.setColor(mLinearGradient.getColor(colorRatio));
                } else {
                    mSelectedItemPaint.setColor(mSelectedItemTextColor);
                    mTextPaint.setColor(mTextColor);
                }

				float alphaRatio;
				if (itemDrawY > mCenterItemDrawnY) {
					alphaRatio = (mDrawnRect.height() - itemDrawY) /
							(float) (mDrawnRect.height() - (mCenterItemDrawnY));
				} else {
					alphaRatio = itemDrawY / (float) mCenterItemDrawnY;
				}

				alphaRatio = alphaRatio < 0 ? 0 :alphaRatio;
				mSelectedItemPaint.setAlpha((int) (alphaRatio * 255));
				mTextPaint.setAlpha((int) (alphaRatio * 255));
            }

			if (mIsZoomInSelectedItem) {
                if (distanceY < mItemHeight) {
                    float addedSize = (mItemHeight - distanceY) / (float) mItemHeight * (mSelectedItemTextSize - mTextSize);
                    mSelectedItemPaint.setTextSize(mTextSize + addedSize);
                    mTextPaint.setTextSize(mTextSize + addedSize);
                } else {
                    mSelectedItemPaint.setTextSize(mTextSize);
                    mTextPaint.setTextSize(mTextSize);
                }
            } else {
                mSelectedItemPaint.setTextSize(mTextSize);
                mTextPaint.setTextSize(mTextSize);
            }
            String drawText = mDataFormat == null ? data.toString() : mDataFormat.format(data);
            if (distanceY < mItemHeight / 2) {
                canvas.drawText(drawText, mFirstItemDrawX, itemDrawY, mSelectedItemPaint);
            } else {
                canvas.drawText(drawText, mFirstItemDrawX, itemDrawY, mTextPaint);
            }
		}
		if (!TextUtils.isEmpty(mIndicatorText)) {
			canvas.drawText(mIndicatorText, mFirstItemDrawX + mTextMaxWidth / 2, mCenterItemDrawnY, mIndicatorPaint);
		}
	}


	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mTracker == null) {
			mTracker = VelocityTracker.obtain();
		}
		mTracker.addMovement(event);
		switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!mScroller.isFinished()) {
                    mScroller.abortAnimation();
                    mIsAbortScroller = true;
                } else {
                    mIsAbortScroller = false;
                }
                mTracker.clear();
                mTouchDownY = mLastDownY = (int) event.getY();
                mTouchSlopFlag = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mTouchSlopFlag && Math.abs(mTouchDownY - event.getY()) < mTouchSlop) {
                    break;
                }
                mTouchSlopFlag = false;
                float move = event.getY() - mLastDownY;
                mScrollOffsetY += move;
                mLastDownY = (int) event.getY();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                if (!mIsAbortScroller && mTouchDownY == mLastDownY) {
                    performClick();
                    if (event.getY() > mSelectedItemRect.bottom) {
                        int scrollItem = (int) (event.getY() - mSelectedItemRect.bottom) / mItemHeight + 1;
                        mScroller.startScroll(0, mScrollOffsetY, 0,
                                -scrollItem * mItemHeight);

                    } else if (event.getY() < mSelectedItemRect.top) {
                        int scrollItem = (int) (mSelectedItemRect.top - event.getY()) / mItemHeight + 1;
                        mScroller.startScroll(0, mScrollOffsetY, 0,
                                scrollItem * mItemHeight);
                    }
                } else {
                    mTracker.computeCurrentVelocity(1000, mMaximumVelocity);
                    int velocity = (int) mTracker.getYVelocity();
                    if (Math.abs(velocity) > mMinimumVelocity) {
                        mScroller.fling(0, mScrollOffsetY, 0, velocity,
                                0, 0, mMinFlingY, mMaxFlingY);
                        mScroller.setFinalY(mScroller.getFinalY() +
                                computeDistanceToEndPoint(mScroller.getFinalY() % mItemHeight));
                    } else {
                        mScroller.startScroll(0, mScrollOffsetY, 0,
                                computeDistanceToEndPoint(mScrollOffsetY % mItemHeight));
                    }
                }
				if (!mIsCyclic) {
					if (mScroller.getFinalY() > mMaxFlingY) {
						mScroller.setFinalY(mMaxFlingY);
					} else if (mScroller.getFinalY() < mMinFlingY) {
						mScroller.setFinalY(mMinFlingY);
					}
				}
				mHandler.post(mScrollerRunnable);
				mTracker.recycle();
				mTracker = null;
				break;
		}
		return true;
	}

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private int computeDistanceToEndPoint(int remainder) {
		if (Math.abs(remainder) > mItemHeight / 2) {
            if (mScrollOffsetY < 0) {
                return -mItemHeight - remainder;
            } else {
                return mItemHeight - remainder;
            }
        } else {
            return -remainder;
        }
	}


    public void setOnWheelChangeListener(OnWheelChangeListener<T> onWheelChangeListener) {
        mOnWheelChangeListener = onWheelChangeListener;
    }

    public Paint getTextPaint() {
        return mTextPaint;
    }

    public Paint getSelectedItemPaint() {
        return mSelectedItemPaint;
    }

    public Paint getPaint() {
        return mPaint;
    }

    public Paint getIndicatorPaint() {
        return mIndicatorPaint;
    }

    public List<T> getDataList() {
        return mDataList;
    }

    public void setDataList(@NonNull List<T> dataList) {
        mDataList = dataList;
        if (dataList.size() == 0) {
            return;
        }
        computeTextSize();
        computeFlingLimitY();
        requestLayout();
        postInvalidate();
    }

    public int getTextColor() {
        return mTextColor;
    }

    public void setTextColor(@ColorInt int textColor) {
    	if (mTextColor == textColor) {
    		return;
	    }
	    mTextPaint.setColor(textColor);
        mTextColor = textColor;
    	mLinearGradient.setStartColor(textColor);
        postInvalidate();
    }

    public int getTextSize() {
        return mTextSize;
    }

    public void setTextSize(int textSize) {
    	if (mTextSize == textSize) {
    		return;
	    }
        mTextSize = textSize;
    	mTextPaint.setTextSize(textSize);
        computeTextSize();
        postInvalidate();
    }

    public int getSelectedItemTextColor() {
        return mSelectedItemTextColor;
    }

    public void setSelectedItemTextColor(@ColorInt int selectedItemTextColor) {
    	if (mSelectedItemTextColor == selectedItemTextColor) {
    		return;
	    }
        mSelectedItemPaint.setColor(selectedItemTextColor);
        mSelectedItemTextColor = selectedItemTextColor;
    	mLinearGradient.setEndColor(selectedItemTextColor);
        postInvalidate();
    }

    public int getSelectedItemTextSize() {
        return mSelectedItemTextSize;
    }

    public void setSelectedItemTextSize(int selectedItemTextSize) {
    	if (mSelectedItemTextSize == selectedItemTextSize) {
    		return;
	    }
	    mSelectedItemPaint.setTextSize(selectedItemTextSize);
        mSelectedItemTextSize = selectedItemTextSize;
    	computeTextSize();
        postInvalidate();
    }


    public String getItemMaximumWidthText() {
        return mItemMaximumWidthText;
    }

    public void setItemMaximumWidthText(String itemMaximumWidthText) {
        mItemMaximumWidthText = itemMaximumWidthText;
        requestLayout();
        postInvalidate();
    }

    public int getHalfVisibleItemCount() {
        return mHalfVisibleItemCount;
    }

    public int getVisibleItemCount() {
        return mHalfVisibleItemCount * 2 + 1;
    }

    public void setHalfVisibleItemCount(int halfVisibleItemCount) {
    	if (mHalfVisibleItemCount == halfVisibleItemCount) {
    		return;
	    }
        mHalfVisibleItemCount = halfVisibleItemCount;
        requestLayout();
    }

    public int getItemWidthSpace() {
        return mItemWidthSpace;
    }

    public void setItemWidthSpace(int itemWidthSpace) {
    	if (mItemWidthSpace == itemWidthSpace) {
    		return;
	    }
        mItemWidthSpace = itemWidthSpace;
        requestLayout();
    }

    public int getItemHeightSpace() {
        return mItemHeightSpace;
    }

    public void setItemHeightSpace(int itemHeightSpace) {
    	if (mItemHeightSpace == itemHeightSpace) {
    		return;
	    }
        mItemHeightSpace = itemHeightSpace;
        requestLayout();
    }

    public int getCurrentPosition() {
        return mCurrentPosition;
    }

    public void setCurrentPosition(int currentPosition) {
        setCurrentPosition(currentPosition, true);
    }

    public synchronized void setCurrentPosition(int currentPosition, boolean smoothScroll) {
	    if (currentPosition > mDataList.size() - 1) {
		    currentPosition = mDataList.size() - 1;
	    }
	    if (currentPosition < 0) {
		    currentPosition = 0;
	    }
	    if (mCurrentPosition == currentPosition) {
	        return;
        }
        if (!mScroller.isFinished()) {
            mScroller.abortAnimation();
        }

        if (smoothScroll && mItemHeight > 0) {
            mScroller.startScroll(0, mScrollOffsetY, 0, (mCurrentPosition - currentPosition) * mItemHeight);
            int finalY = -currentPosition * mItemHeight;
            mScroller.setFinalY(finalY);
            mHandler.post(mScrollerRunnable);
        } else {
            mCurrentPosition = currentPosition;
            mScrollOffsetY = -mItemHeight * mCurrentPosition;
            postInvalidate();
            if (mOnWheelChangeListener != null) {
                mOnWheelChangeListener.onWheelSelected(mDataList.get(currentPosition), currentPosition);
            }
        }
    }

    public boolean isZoomInSelectedItem() {
        return mIsZoomInSelectedItem;
    }

    public void setZoomInSelectedItem(boolean zoomInSelectedItem) {
	    if (mIsZoomInSelectedItem == zoomInSelectedItem) {
		    return;
	    }
        mIsZoomInSelectedItem = zoomInSelectedItem;
        postInvalidate();
    }

    public boolean isCyclic() {
        return mIsCyclic;
    }

    public void setCyclic(boolean cyclic) {
	    if (mIsCyclic == cyclic) {
		    return;
	    }
        mIsCyclic = cyclic;
        computeFlingLimitY();
        requestLayout();
    }

    public int getMinimumVelocity() {
        return mMinimumVelocity;
    }

    public void setMinimumVelocity(int minimumVelocity) {
        mMinimumVelocity = minimumVelocity;
    }

    public int getMaximumVelocity() {
        return mMaximumVelocity;
    }

    public void setMaximumVelocity(int maximumVelocity) {
        mMaximumVelocity = maximumVelocity;
    }

    public boolean isTextGradual() {
        return mIsTextGradual;
    }

    public void setTextGradual(boolean textGradual) {
	    if (mIsTextGradual == textGradual) {
		    return;
	    }
        mIsTextGradual = textGradual;
        postInvalidate();
    }

    public boolean isShowCurtain() {
        return mIsShowCurtain;
    }

    public void setShowCurtain(boolean showCurtain) {
	    if (mIsShowCurtain == showCurtain) {
		    return;
	    }
        mIsShowCurtain = showCurtain;
        postInvalidate();
    }

    public int getCurtainColor() {
        return mCurtainColor;
    }

    public void setCurtainColor(@ColorInt int curtainColor) {
	    if (mCurtainColor == curtainColor) {
		    return;
	    }
        mCurtainColor = curtainColor;
        postInvalidate();
    }

    public boolean isShowCurtainBorder() {
        return mIsShowCurtainBorder;
    }

    public void setShowCurtainBorder(boolean showCurtainBorder) {
	    if (mIsShowCurtainBorder == showCurtainBorder) {
		    return;
	    }
        mIsShowCurtainBorder = showCurtainBorder;
        postInvalidate();
    }

    public int getCurtainBorderColor() {
        return mCurtainBorderColor;
    }

    public void setCurtainBorderColor(@ColorInt int curtainBorderColor) {
	    if (mCurtainBorderColor == curtainBorderColor) {
		    return;
	    }
        mCurtainBorderColor = curtainBorderColor;
        postInvalidate();
    }

	public void setIndicatorText(String indicatorText) {
		mIndicatorText = indicatorText;
		postInvalidate();
	}

	public void setIndicatorTextColor(int indicatorTextColor) {
		mIndicatorTextColor = indicatorTextColor;
		mIndicatorPaint.setColor(mIndicatorTextColor);
		postInvalidate();
	}

	public void setIndicatorTextSize(int indicatorTextSize) {
		mIndicatorTextSize = indicatorTextSize;
		mIndicatorPaint.setTextSize(mIndicatorTextSize);
		postInvalidate();
	}

	public void setDataFormat(Format dataFormat) {
		mDataFormat = dataFormat;
		postInvalidate();
	}

	public Format getDataFormat() {
		return mDataFormat;
	}

	public interface OnWheelChangeListener<T> {
		void onWheelSelected(T item, int position);
	}
}
