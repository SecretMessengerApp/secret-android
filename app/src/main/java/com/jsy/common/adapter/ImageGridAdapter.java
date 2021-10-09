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
package com.jsy.common.adapter;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.jsy.common.config.PictureConfig;
import com.jsy.common.config.PictureMimeType;
import com.jsy.common.config.PictureSelectionConfig;
import com.jsy.common.model.circle.LocalMedia;
import com.jsy.common.utils.DateUtils;
import com.jsy.common.utils.ToastUtil;
import com.waz.zclient.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static int DURATION = 450;
    protected Context context;
    private OnPictureSelectChangedListener imageSelectChangedListener;
    private int maxSelectNum = 9;
    private List<LocalMedia> images = new ArrayList<>();
    private List<LocalMedia> selectImages = new ArrayList<>();
    private boolean enablePreview;
    private boolean is_checked_num;
    private boolean showCheckIcon;
    private float sizeMultiplier;
    private boolean zoomAnim;
    private int orientation;
    private int overrideWidth, overrideHeight;
    private int VERTICAL = 1;
    private int HORIZONTAL = 0;


    public ImageGridAdapter(Context context, PictureSelectionConfig config,int orientation) {
        this.context = context;
        this.enablePreview = true;
        this.is_checked_num = config.checkNumMode;
        this.sizeMultiplier = config.sizeMultiplier;
        this.zoomAnim = config.zoomAnim;
        this.showCheckIcon = config.showCheckIcon;
        this.orientation = orientation;
        this.overrideWidth = config.overrideWidth;
        this.overrideHeight = config.overrideHeight;
    }


    public void bindImagesData(List<LocalMedia> images) {
        this.images = images;
        notifyDataSetChanged();
    }

    public List<LocalMedia> getSelectedImages() {
        if (selectImages == null) {
            selectImages = new ArrayList<>();
        }
        return selectImages;
    }

    public List<LocalMedia> getImages() {
        if (images == null) {
            images = new ArrayList<>();
        }
        return images;
    }

    @Override
    public int getItemViewType(int position) {
        return PictureConfig.TYPE_PICTURE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = R.layout.cursor_image_grid_item;

        if(orientation == VERTICAL){
            layoutId = R.layout.cursor_image_grid_item;
        }else if(orientation == HORIZONTAL){
            layoutId = R.layout.cursor_image_grid_item_horizontal;
        }

        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {

        final ViewHolder contentHolder = (ViewHolder) holder;
        final LocalMedia image = images.get(position);
        image.position = contentHolder.getAdapterPosition();
        final String path = image.getPath();
        final String pictureType = image.getPictureType();

        if (is_checked_num) {
            notifyCheckChanged(contentHolder, image);
        }
        selectImage(contentHolder, isSelected(image), false);

        final int mediaMimeType = PictureMimeType.isPictureType(pictureType);
        boolean gif = PictureMimeType.isGif(pictureType);

        contentHolder.tv_isGif.setVisibility((gif && (orientation == VERTICAL) )? View.VISIBLE : View.GONE);
        contentHolder.tv_duration.setVisibility(mediaMimeType == PictureConfig.TYPE_VIDEO
            ? View.VISIBLE : View.GONE);
        long duration = image.getDuration();
        contentHolder.tv_duration.setText(DateUtils.timeParse(duration));

        RequestOptions options = new RequestOptions();
        if (overrideWidth <= 0 && overrideHeight <= 0) {
            options.sizeMultiplier(sizeMultiplier);
        } else {
            options.override(overrideWidth, overrideHeight);
        }
        options.diskCacheStrategy(DiskCacheStrategy.ALL);
        options.centerCrop();
        options.placeholder(R.drawable.image_placeholder);
        Glide.with(context)
            //.asBitmap()
            .load(path)
            .apply(options)
            .into(contentHolder.iv_picture);
        if (showCheckIcon) {
            contentHolder.ll_check.setVisibility(View.VISIBLE);
            if (enablePreview) {
                contentHolder.ll_check.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!new File(path).exists()) {
                            ToastUtil.toastByString(context, PictureMimeType.s(context, mediaMimeType));
                            return;
                        }
                        changeCheckboxState(contentHolder, image);
                    }
                });
            }
        } else {
            contentHolder.ll_check.setVisibility(View.GONE);
        }
        contentHolder.contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!new File(path).exists()) {
                    ToastUtil.toastByString(context, PictureMimeType.s(context, mediaMimeType));
                    return;
                }
                int index = position;
                boolean eqResult =
                    mediaMimeType == PictureConfig.TYPE_IMAGE && enablePreview;

                if (eqResult) {
                    if(imageSelectChangedListener != null){
                        //imageSelectChangedListener.onPictureClick(image, index);
                    }
                } else {
                    changeCheckboxState(contentHolder, image);
                }
            }
        });
    }


    @Override
    public int getItemCount() {
        return images.size();
    }


    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView iv_picture;
        TextView check;
        View contentView;
        LinearLayout ll_check;
        TextView tv_duration, tv_isGif;

        public ViewHolder(View itemView) {
            super(itemView);
            contentView = itemView;
            iv_picture = itemView.findViewById(R.id.iv_picture);
            check = itemView.findViewById(R.id.check);
            ll_check = itemView.findViewById(R.id.ll_check);
            tv_duration = itemView.findViewById(R.id.tv_duration);
            tv_isGif = itemView.findViewById(R.id.tv_gif);
        }
    }


    public boolean isSelected(LocalMedia image) {
        for (LocalMedia media : selectImages) {
            if (media.getPath().equals(image.getPath())) {
                return true;
            }
        }
        return false;
    }

    private void notifyCheckChanged(ViewHolder viewHolder, LocalMedia imageBean) {
        viewHolder.check.setText("");
        for (LocalMedia media : selectImages) {
            if (media.getPath().equals(imageBean.getPath())) {
                imageBean.setNum(media.getNum());
                media.setPosition(imageBean.getPosition());
                if (PictureMimeType.isVideo(imageBean.getPictureType())) {
                    viewHolder.check.setText("√");
                } else {
                    viewHolder.check.setText(String.valueOf(imageBean.getNum()));
                }
            }
        }
    }


    private void changeCheckboxState(ViewHolder contentHolder, final LocalMedia image) {
        boolean isChecked = contentHolder.check.isSelected();
        String pictureType = selectImages.size() > 0 ? selectImages.get(0).getPictureType() : "";
        if (!TextUtils.isEmpty(pictureType)) {
            boolean toEqual = PictureMimeType.mimeToEqual(pictureType, image.getPictureType());
            if (!toEqual) {
                ToastUtil.toastByString(context,context.getString(R.string.picture_rule));
                return;
            }
        }
        if (selectImages.size() >= maxSelectNum && !isChecked) {
            ToastUtil.toastByString(context, context.getResources().getQuantityString(R.plurals.picture_message_max_num, maxSelectNum, maxSelectNum));
            return;
        }

        if (selectImages.size() > 0 && selectImages.get(0).getPictureType().equals("video/mp4") && !isChecked) {
            ToastUtil.toastByString(context, context.getString(R.string.picture_video_max_one));
            return;
        }

        if (isChecked) {
            for (LocalMedia media : selectImages) {
                if (media.getPath().equals(image.getPath())) {
                    selectImages.remove(media);
                    subSelectPosition();
                    disZoom(contentHolder.iv_picture);
                    break;
                }
            }
        } else {
            selectImages.add(image);
            image.setNum(selectImages.size());
            zoom(contentHolder.iv_picture);
        }
        notifyItemChanged(contentHolder.getAdapterPosition());
        selectImage(contentHolder, !isChecked, true);
        if (imageSelectChangedListener != null) {
            imageSelectChangedListener.onChange(selectImages);
        }
    }

    private void subSelectPosition() {
        if (is_checked_num) {
            int size = selectImages.size();
            for (int index = 0, length = size; index < length; index++) {
                LocalMedia media = selectImages.get(index);
                media.setNum(index + 1);
                notifyItemChanged(media.position);
            }
        }
    }

    public void selectImage(ViewHolder holder, boolean isChecked, boolean isAnim) {
        holder.check.setSelected(isChecked);
        if (isChecked) {
            holder.iv_picture.setColorFilter(ContextCompat.getColor
                (context, R.color.image_overlay_true), PorterDuff.Mode.SRC_ATOP);
        } else {
            holder.iv_picture.setColorFilter(ContextCompat.getColor
                (context, R.color.image_overlay_false), PorterDuff.Mode.SRC_ATOP);
        }
    }

    public interface OnPictureSelectChangedListener {

        void onChange(List<LocalMedia> selectImages);

    }

    public void setOnPictureSelectChangedListener(OnPictureSelectChangedListener
                                                    imageSelectChangedListener) {
        this.imageSelectChangedListener = imageSelectChangedListener;
    }

    private void zoom(ImageView iv_img) {
        if (zoomAnim) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(iv_img, "scaleX", 1f, 1.12f),
                ObjectAnimator.ofFloat(iv_img, "scaleY", 1f, 1.12f)
            );
            set.setDuration(DURATION);
            set.start();
        }
    }

    private void disZoom(ImageView iv_img) {
        if (zoomAnim) {
            AnimatorSet set = new AnimatorSet();
            set.playTogether(
                ObjectAnimator.ofFloat(iv_img, "scaleX", 1.12f, 1f),
                ObjectAnimator.ofFloat(iv_img, "scaleY", 1.12f, 1f)
            );
            set.setDuration(DURATION);
            set.start();
        }
    }
}
