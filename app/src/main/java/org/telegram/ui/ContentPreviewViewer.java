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
package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import androidx.recyclerview.widget.RecyclerView;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.jsy.common.utils.ScreenUtils;
import com.waz.zclient.emoji.bean.EmotionItemBean;
import com.waz.zclient.emoji.dialog.EmojiStickerMenuDialog;
import com.waz.zclient.emoji.utils.EmojiUtils;
import com.waz.zclient.emoji.view.MyStickerEmojiCell;
import com.waz.zclient.utils.ContextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;

public class ContentPreviewViewer {

    private class FrameLayoutDrawer extends FrameLayout {
        public FrameLayoutDrawer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            ContentPreviewViewer.this.onDraw(canvas);
        }
    }

    public interface ContentPreviewViewerDelegate {

        void onSend(EmotionItemBean itemBean);

        void onStickerSet(EmotionItemBean itemBean);

        void onFavorite(EmotionItemBean itemBean);

    }

    private final static int CONTENT_TYPE_NONE = -1;
    private final static int CONTENT_TYPE_STICKER = 0;
    private final static int CONTENT_TYPE_GIF = 1;

    private static TextPaint textPaint;

    private int startX;
    private int startY;
    private float lastTouchY;
    private float currentMoveY;
    private float moveY = 0;
    private float finalMoveY;
    private float startMoveY;
    private boolean animateY;
    private float currentMoveYProgress;
    private View currentPreviewCell;
    private boolean clearsInputField;
    private Runnable openPreviewRunnable;
    private EmojiStickerMenuDialog visibleDialog;
    private ContentPreviewViewerDelegate delegate;

    private boolean isRecentSticker;
    private WindowInsets lastInsets;

    private int currentAccount;

    private ColorDrawable backgroundDrawable = new ColorDrawable(0x71000000);
    private Activity parentActivity;
    private WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayoutDrawer containerView;
    private boolean isVisible = false;
    private float showProgress;
    private StaticLayout stickerEmojiLayout;
    private long lastUpdateTime;
    private int keyboardHeight = 0;
    private TextView tv_emoji;
    private RLottieImageView rLottieImageView;
    private int position = 0;

    private int currentContentType;
    private EmotionItemBean currentDocument;
    private Object parentObject;

    private Runnable showSheetRunnable = new Runnable() {
        @Override
        public void run() {
            if (parentActivity == null || delegate==null) {
                return;
            }
            if (currentContentType == CONTENT_TYPE_STICKER) {
                visibleDialog = new EmojiStickerMenuDialog(parentActivity, currentDocument);
                visibleDialog.setDelegate(delegate);
                visibleDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        visibleDialog = null;
                        close();
                    }
                });
                visibleDialog.show();
                containerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
    };

    @SuppressLint("StaticFieldLeak")
    private static volatile ContentPreviewViewer Instance = null;

    public static ContentPreviewViewer getInstance() {
        ContentPreviewViewer localInstance = Instance;
        if (localInstance == null) {
            synchronized (ContentPreviewViewer.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ContentPreviewViewer();
                }
            }
        }
        return localInstance;
    }

    public static boolean hasInstance() {
        return Instance != null;
    }

    public void reset() {
        if (openPreviewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
            openPreviewRunnable = null;
        }
        if (currentPreviewCell != null) {
            if (currentPreviewCell instanceof MyStickerEmojiCell) {
                ((MyStickerEmojiCell) currentPreviewCell).setScaled(false);
            }
            currentPreviewCell = null;
        }
    }

    public boolean onTouch(MotionEvent event, final RecyclerView listView, final int height, final Object listener, ContentPreviewViewerDelegate contentPreviewViewerDelegate) {
        delegate = contentPreviewViewerDelegate;
        if (openPreviewRunnable != null || isVisible()) {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_POINTER_UP) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (listView instanceof RecyclerListView) {
                            Log.d("JACK810", "reset setOnItemClickListener");
                            ((RecyclerListView) listView).setOnItemClickListener((RecyclerListView.OnItemClickListener) listener);
                        }
                    }
                }, 150);
                if (openPreviewRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                    openPreviewRunnable = null;
                } else if (isVisible()) {
                    close();
                    if (currentPreviewCell != null) {
                        if (currentPreviewCell instanceof MyStickerEmojiCell) {
                            ((MyStickerEmojiCell) currentPreviewCell).setScaled(false);
                        }
                        currentPreviewCell = null;
                    }
                }
            } else if (event.getAction() != MotionEvent.ACTION_DOWN) {
                if (isVisible) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        View view = listView.findChildViewUnder(x, y);
                        if (view == null) {
                            return false;
                        }
                        position = listView.getChildAdapterPosition(view);
                        Log.d("JACK812", "position:" + position);
                        int contentType = CONTENT_TYPE_NONE;
                        if (view instanceof MyStickerEmojiCell) {
                            contentType = CONTENT_TYPE_STICKER;
                        }
                        if (contentType == CONTENT_TYPE_NONE || view == currentPreviewCell) {
                            Log.d("JACK814", "return");
                            return true;
                        }
                        if (currentPreviewCell instanceof MyStickerEmojiCell) {
                            ((MyStickerEmojiCell) currentPreviewCell).setScaled(false);
                        }
                        currentPreviewCell = view;
                        setKeyboardHeight(height);
                        clearsInputField = false;
                        if (currentPreviewCell instanceof MyStickerEmojiCell) {
                            MyStickerEmojiCell stickerEmojiCell = (MyStickerEmojiCell) currentPreviewCell;
                            BaseQuickAdapter adapter = (BaseQuickAdapter) listView.getAdapter();
                            EmotionItemBean data = (EmotionItemBean) adapter.getItem(position-adapter.getHeaderLayoutCount());
                            openSticker(data, CONTENT_TYPE_STICKER, null);
                            stickerEmojiCell.setScaled(true);
                        }
                    }
                    return true;
                } else if (openPreviewRunnable != null) {
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        if (Math.hypot(startX - event.getX(), startY - event.getY()) > AndroidUtilities.dp(10)) {
                            AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                            openPreviewRunnable = null;
                        }
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(openPreviewRunnable);
                        openPreviewRunnable = null;
                    }
                }
            }
        }
        return false;
    }

    public boolean onInterceptTouchEvent(MotionEvent event, final RecyclerView listView, final int height, ContentPreviewViewerDelegate contentPreviewViewerDelegate) {
        delegate = contentPreviewViewerDelegate;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            View view = listView.findChildViewUnder(x, y);
            if (view == null) {
                return false;
            }
            position = listView.getChildAdapterPosition(view);
            Log.d("JACK811", "position:" + position);
            int contentType = CONTENT_TYPE_NONE;
            if (view instanceof MyStickerEmojiCell) {
                contentType = CONTENT_TYPE_STICKER;
            }

            if (contentType == CONTENT_TYPE_NONE) {
                return false;
            }
            startX = x;
            startY = y;
            currentPreviewCell = view;
            openPreviewRunnable = new Runnable() {
                @Override
                public void run() {
                    if (openPreviewRunnable == null) {
                        return;
                    }
                    if (listView instanceof RecyclerListView) {
                        ((RecyclerListView) listView).setOnItemClickListener((RecyclerListView.OnItemClickListener) null);
                    }
                    listView.requestDisallowInterceptTouchEvent(true);
                    openPreviewRunnable = null;
                    setParentActivity((Activity) listView.getContext());
                    setKeyboardHeight(height);
                    clearsInputField = false;
                    if (currentPreviewCell instanceof MyStickerEmojiCell) {
                        BaseQuickAdapter adapter = (BaseQuickAdapter) listView.getAdapter();
                        EmotionItemBean data = (EmotionItemBean) adapter.getItem(position-adapter.getHeaderLayoutCount());
                        openSticker(data, CONTENT_TYPE_STICKER, null);
                    }
                }
            };
            AndroidUtilities.runOnUIThread(openPreviewRunnable, 200);
            return true;
        }
        return false;
    }

    public void setDelegate(ContentPreviewViewerDelegate contentPreviewViewerDelegate) {
        delegate = contentPreviewViewerDelegate;
    }

    public void setParentActivity(Activity activity) {

        if (parentActivity == activity) {
            return;
        }
        parentActivity = activity;
        windowView = new FrameLayout(activity);
        windowView.setFocusable(true);
        windowView.setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    lastInsets = insets;
                    return insets;
                }
            });
        }

        containerView = new FrameLayoutDrawer(activity);
        containerView.setFocusable(false);
        windowView.addView(containerView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        containerView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_POINTER_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    close();
                }
                return true;
            }
        });
        windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        windowLayoutParams.gravity = Gravity.TOP;
        windowLayoutParams.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW;
        if (Build.VERSION.SDK_INT >= 21) {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        } else {
            windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
    }

    public void setKeyboardHeight(int height) {
        keyboardHeight = height;
    }


    public void openSticker(EmotionItemBean document, int contentType, Object parent) {
        Log.d("JACK813", "openSticker:" + document);
        if (parentActivity == null || windowView == null) {
            return;
        }
        stickerEmojiLayout = null;
        if (contentType == CONTENT_TYPE_STICKER) {
            if (textPaint == null) {
                textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setTextSize(AndroidUtilities.dp(24));
            }

            if (document != null) {
                try {
                    if (visibleDialog != null) {
                        visibleDialog.setOnDismissListener(null);
                        visibleDialog.dismiss();
                        visibleDialog = null;
                    }
                } catch (Exception e) {
                    //FileLog.e(e);
                }
                AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
                AndroidUtilities.runOnUIThread(showSheetRunnable, 1300);
            }
            parentObject = parent;

            if (rLottieImageView != null) {
                rLottieImageView.stopAnimation();
                containerView.removeView(rLottieImageView);
                rLottieImageView = null;
            }
            if (tv_emoji != null) {
                containerView.removeView(tv_emoji);
                tv_emoji = null;
            }
            try {
                rLottieImageView = new RLottieImageView(parentActivity);
                FrameLayout.LayoutParams imageLayoutParams = new FrameLayout.LayoutParams(ScreenUtils.dip2px(parentActivity, 200), ScreenUtils.dip2px(parentActivity, 200), Gravity.CENTER_HORIZONTAL);
                imageLayoutParams.topMargin = ScreenUtils.dip2px(parentActivity, 150);
                rLottieImageView.setLayoutParams(imageLayoutParams);
                rLottieImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                containerView.addView(rLottieImageView);

                EmojiUtils.loadImage(parentActivity, document.getUrl(), rLottieImageView, imageLayoutParams.width, imageLayoutParams.height);

                tv_emoji = new TextView(parentActivity);
                tv_emoji.setTextColor(Color.BLACK);
                tv_emoji.setTextSize(24);
                tv_emoji.setText(document.getName());
                FrameLayout.LayoutParams textLayoutParams = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL);
                textLayoutParams.topMargin = ScreenUtils.dip2px(parentActivity, 100);
                tv_emoji.setLayoutParams(textLayoutParams);
                containerView.addView(tv_emoji);
            } catch (Exception e) {
                e.printStackTrace();
            }
            //stickerEmojiLayout = new StaticLayout(document.getName(), textPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
        }

        currentContentType = contentType;
        currentDocument = document;
        containerView.invalidate();

        containerView.invalidate();

        if (!isVisible) {
            //AndroidUtilities.lockOrientation(parentActivity);
            try {
                if (windowView.getParent() != null) {
                    WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                    wm.removeView(windowView);
                }
            } catch (Exception e) {
                // FileLog.e(e);
            }
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            wm.addView(windowView, windowLayoutParams);
            isVisible = true;
            showProgress = 0.0f;
            lastTouchY = -10000;
            currentMoveYProgress = 0.0f;
            finalMoveY = 0;
            currentMoveY = 0;
            moveY = 0;
            lastUpdateTime = System.currentTimeMillis();
        }
    }


    public boolean isVisible() {
        return isVisible;
    }

    public void close() {
        if (parentActivity == null || visibleDialog != null) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(showSheetRunnable);
        showProgress = 1.0f;
        lastUpdateTime = System.currentTimeMillis();
        containerView.invalidate();
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            //FileLog.e(e);
        }
        if (rLottieImageView != null) {
            rLottieImageView.stopAnimation();
            containerView.removeView(rLottieImageView);
            rLottieImageView = null;
        }
        if (tv_emoji != null) {
            containerView.removeView(tv_emoji);
            tv_emoji = null;
        }
        currentDocument = null;
        delegate = null;
        isVisible = false;
    }

    public void destroy() {
        isVisible = false;
        delegate = null;
        currentDocument = null;
        try {
            if (visibleDialog != null) {
                visibleDialog.dismiss();
                visibleDialog = null;
            }
        } catch (Exception e) {
            //FileLog.e(e);
        }
        if (parentActivity == null || windowView == null) {
            return;
        }
        try {
            if (windowView.getParent() != null) {
                WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                wm.removeViewImmediate(windowView);
            }
            windowView = null;
        } catch (Exception e) {
            //FileLog.e(e);
        }
        Instance = null;
    }

    private float rubberYPoisition(float offset, float factor) {
        float delta = Math.abs(offset);
        return (-((1.0f - (1.0f / ((delta * 0.55f / factor) + 1.0f))) * factor)) * (offset < 0.0f ? 1.0f : -1.0f);
    }

    @SuppressLint("DrawAllocation")
    private void onDraw(Canvas canvas) {
        Log.d("JACK8", "+++++++++++++++++onDraw");
        if (containerView == null || backgroundDrawable == null) {
            return;
        }
        backgroundDrawable.setAlpha((int) (180 * showProgress));
        backgroundDrawable.setBounds(0, 0, containerView.getWidth(), containerView.getHeight());
        backgroundDrawable.draw(canvas);

        canvas.save();
        int size;
        int insets = 0;
        int top;
        if (Build.VERSION.SDK_INT >= 21 && lastInsets != null) {
            insets = lastInsets.getStableInsetBottom() + lastInsets.getStableInsetTop();
            top = lastInsets.getStableInsetTop();
        } else {
            top = ContextUtils.getStatusBarHeight(containerView.getContext().getApplicationContext());
        }

        if (currentContentType == CONTENT_TYPE_GIF) {
            size = Math.min(containerView.getWidth(), containerView.getHeight() - insets) - AndroidUtilities.dp(40f);
        } else {
            size = (int) (Math.min(containerView.getWidth(), containerView.getHeight() - insets) / 1.8f);
        }

        float transX = containerView.getWidth() / 2;
        float transY = containerView.getHeight() / 2;
        canvas.translate(transX, transY);
        //canvas.translate(containerView.getWidth() / 2, moveY + Math.max(size / 2 + top + (stickerEmojiLayout != null ? AndroidUtilities.dp(40) : 0), (containerView.getHeight() - insets - keyboardHeight) / 2));
        float scale = 0.8f * showProgress / 0.8f;
        size = (int) (size * scale);
        int w = 0;
        int h = 0;
//        if (stickerEmojiLayout != null) {
//            canvas.translate(-AndroidUtilities.dp(50), -w-200);
//            stickerEmojiLayout.draw(canvas);
//        }
        canvas.restore();
        if (isVisible) {
            if (showProgress != 1) {
                long newTime = System.currentTimeMillis();
                long dt = newTime - lastUpdateTime;
                lastUpdateTime = newTime;
                showProgress += dt / 120.0f;
                containerView.invalidate();
                if (showProgress > 1.0f) {
                    showProgress = 1.0f;
                }
                Log.d("JACK8", "showProgress:" + showProgress);
            }
        } else if (showProgress != 0) {
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastUpdateTime;
            lastUpdateTime = newTime;
            showProgress -= dt / 120.0f;
            containerView.invalidate();
            if (showProgress < 0.0f) {
                showProgress = 0.0f;
            }
            if (showProgress == 0) {
                //AndroidUtilities.unlockOrientation(parentActivity);
                try {
                    if (windowView.getParent() != null) {
                        WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
                        wm.removeView(windowView);
                    }
                } catch (Exception e) {
                    //FileLog.e(e);
                }
            }
        }
    }
}
