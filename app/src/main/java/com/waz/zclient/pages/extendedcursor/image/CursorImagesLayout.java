/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.pages.extendedcursor.image;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.jsy.common.adapter.ImageGridAdapter;
import com.jsy.common.config.PictureConfig;
import com.jsy.common.config.PictureSelectionConfig;
import com.jsy.common.model.LocalMediaFolder;
import com.jsy.common.model.LocalMediaLoader;
import com.jsy.common.model.circle.LocalMedia;
import com.waz.utils.wrappers.URI;
import com.waz.zclient.R;
import com.waz.zclient.ui.animation.interpolators.penner.Quint;
import com.jsy.res.utils.ViewUtils;

import java.util.ArrayList;
import java.util.List;

public class CursorImagesLayout extends FrameLayout implements View.OnClickListener, CursorImagesAdapter.AdapterCallback, ImageGridAdapter.OnPictureSelectChangedListener {

    private static final int IMAGE_ROWS = 3;

    private RecyclerView recyclerView;
    private CursorImagesAdapter cursorImagesAdapter;
    private ImageGridAdapter multipleSelectAdapter;

    private RelativeLayout mRlMultipleSelect;
    private RelativeLayout mRlMultipleSend;
    private CheckBox mCompressCheck;
    private Button mBtnMultipleSend;

    private View buttonNavToCamera;
    private View buttonOpenGallery;

    private Callback callback;

    private int firstVisiblePosition;

    private LocalMediaLoader mediaLoader;
    public List<LocalMedia> images = new ArrayList<>();
    public List<LocalMedia> selectImages = new ArrayList<>();
    private boolean isMultipleSelect = false;

    public CursorImagesLayout(Context context) {
        this(context, null);
    }

    public CursorImagesLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CursorImagesLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        recyclerView = ViewUtils.getView(this, R.id.rv__cursor_images);
        recyclerView.setHasFixedSize(true);

        mRlMultipleSelect = ViewUtils.getView(this, R.id.gtv__cursor_image_multiple);
        mRlMultipleSend = ViewUtils.getView(this, R.id.rl_multiple_send);
        mCompressCheck = ViewUtils.getView(this, R.id.compress_checkBox);
        mBtnMultipleSend = ViewUtils.getView(this, R.id.bt_multiple_send);

        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        if(isMultipleSelect){
            initMultipleSelect();
        }else{
            initSingleSelect();
        }


        buttonNavToCamera = ViewUtils.getView(this, R.id.gtv__cursor_image__nav_camera_back);
        buttonOpenGallery = ViewUtils.getView(this, R.id.gtv__cursor_image__nav_open_gallery);

        buttonNavToCamera.setVisibility(View.GONE);
        buttonNavToCamera.setOnClickListener(this);
        buttonOpenGallery.setOnClickListener(this);
        mRlMultipleSelect.setOnClickListener(this);
        mBtnMultipleSend.setOnClickListener(this);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if(newState == RecyclerView.SCROLL_STATE_IDLE){
                    RecyclerView.LayoutManager layout = recyclerView.getLayoutManager();
                    if(layout instanceof GridLayoutManager){
                        firstVisiblePosition = ((GridLayoutManager) layout).findFirstVisibleItemPosition();
                    }

                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if(isMultipleSelect){
            setMultipleSelectAdapter();
        }else{
            setSingleSelectAdapter();
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.close();
            cursorImagesAdapter = null;
        }
    }

    @Override
    public void onClick(View v) {
        int vId = v.getId();
        if (vId == R.id.gtv__cursor_image__nav_camera_back) {
            recyclerView.smoothScrollToPosition(0);
        } else if (vId == R.id.gtv__cursor_image__nav_open_gallery) {
            if (callback != null) {
                callback.openGallery();
            }
        } else if (vId == R.id.gtv__cursor_image_multiple){
            isMultipleSelect = !isMultipleSelect;
            mRlMultipleSend.setVisibility(View.GONE);
            buttonNavToCamera.setVisibility(View.VISIBLE);
            buttonOpenGallery.setVisibility(View.VISIBLE);
            if(isMultipleSelect){
                initMultipleSelect();
                setMultipleSelectAdapter();
                mRlMultipleSelect.setBackgroundResource(R.drawable.shape_cursor_multiple_image_blue);
            }else{
                initSingleSelect();
                setSingleSelectAdapter();
                mRlMultipleSelect.setBackgroundResource(R.drawable.shape_cursor_multiple_image);
            }
        }else if(vId == R.id.bt_multiple_send){
            if(multipleImageSendCallback != null){
                multipleImageSendCallback.sendMultipleImages(selectImages,mCompressCheck.isChecked());
            }
        }
        else {

        }
    }

    public void setCallback(Callback callback) {
        this.callback = callback;

        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.setCallback(callback);
        }
    }

    @Override
    public void onCameraPreviewDetached() {
        buttonNavToCamera.setVisibility(View.VISIBLE);
        buttonNavToCamera.setAlpha(0);
        buttonNavToCamera
                .animate()
                .setInterpolator(new Quint.EaseOut())
                .alpha(1);
    }

    @Override
    public void onCameraPreviewAttached() {
        buttonNavToCamera
                .animate()
                .alpha(0)
                .setDuration(getResources().getInteger(R.integer.animation_duration_short))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        buttonNavToCamera.setAlpha(1);
                        buttonNavToCamera.setVisibility(View.GONE);
                    }
                });
    }

    public void onClose() {
        if (cursorImagesAdapter != null) {
            cursorImagesAdapter.close();
        }
    }

    public void initSingleSelect(){
        GridLayoutManager layout = new GridLayoutManager(getContext(), IMAGE_ROWS, GridLayoutManager.HORIZONTAL, false);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return (cursorImagesAdapter.getItemViewType(position) == CursorImagesAdapter.VIEW_TYPE_CAMERA) ? IMAGE_ROWS : 1;
            }
        });
        recyclerView.setLayoutManager(layout);
        int dividerSpacing = getContext().getResources().getDimensionPixelSize(R.dimen.extended_container__camera__gallery_grid__divider__spacing);
        recyclerView.addItemDecoration(new CursorImagesItemDecoration(dividerSpacing));
    }

    public void initMultipleSelect(){
        GridLayoutManager layout = new GridLayoutManager(getContext(), IMAGE_ROWS, GridLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layout);
//        int dividerSpacing = getContext().getResources().getDimensionPixelSize(R.dimen.extended_container__camera__gallery_grid__divider__spacing);
//        recyclerView.addItemDecoration(new CursorImagesItemDecoration(dividerSpacing));
        ((SimpleItemAnimator) recyclerView.getItemAnimator())
            .setSupportsChangeAnimations(false);
    }


    public void setSingleSelectAdapter(){

        cursorImagesAdapter = new CursorImagesAdapter(getContext(), this);
        cursorImagesAdapter.setCallback(callback);

        recyclerView.setAdapter(cursorImagesAdapter);
        recyclerView.smoothScrollToPosition(firstVisiblePosition);
    }


    public void setMultipleSelectAdapter(){

        mediaLoader = new LocalMediaLoader(activity, PictureConfig.TYPE_ALL, true);

        multipleSelectAdapter = new ImageGridAdapter(getContext(), PictureSelectionConfig.getInstance(),GridLayoutManager.HORIZONTAL);

        multipleSelectAdapter.setOnPictureSelectChangedListener(this);

        mediaLoader.loadAllMedia(new LocalMediaLoader.LocalMediaLoadListener() {
            @Override
            public void loadComplete(List<LocalMediaFolder> folders) {
                if (folders.size() > 0) {
                    LocalMediaFolder folder = folders.get(0);
                    folder.setChecked(true);
                    List<LocalMedia> localImg = folder.getImages();

                    if (localImg.size() >= images.size()) {
                        images = localImg;
                    }
                }
                if (multipleSelectAdapter != null) {
                    if (images == null) {
                        images = new ArrayList<>();
                    }
                    multipleSelectAdapter.bindImagesData(images);
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        recyclerView.setAdapter(multipleSelectAdapter);
                        recyclerView.scrollToPosition(firstVisiblePosition);
                    }
                });
            }
        });

    }

    private FragmentActivity activity;

    private MultipleImageSendCallback multipleImageSendCallback;

    public void setMultipleImageSendCallback(FragmentActivity activity,MultipleImageSendCallback multipleImageSendCallback){
        this.activity = activity;
        this.multipleImageSendCallback = multipleImageSendCallback;
    }

    @Override
    public void onChange(List<LocalMedia> selectImages) {
        this.selectImages = selectImages;
        if(selectImages.size() > 0){
            mRlMultipleSend.setVisibility(View.VISIBLE);
            buttonNavToCamera.setVisibility(View.GONE);
            buttonOpenGallery.setVisibility(View.GONE);
        }else{
            mRlMultipleSend.setVisibility(View.GONE);
            buttonNavToCamera.setVisibility(View.VISIBLE);
            buttonOpenGallery.setVisibility(View.VISIBLE);
        }
    }

    public interface Callback {
        void openCamera();

        void openVideo();

        void onGalleryPictureSelected(URI uri);

        void sendGalleryVideoSelected(URI uri);

        void openGallery();

        void onPictureTaken(byte[] imageData, boolean isMirrored);
    }

    public interface MultipleImageSendCallback{
        void sendMultipleImages(List<LocalMedia> selectImages,boolean isCompress);
    }
}
