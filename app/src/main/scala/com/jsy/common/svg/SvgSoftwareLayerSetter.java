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
package com.jsy.common.svg;

import android.graphics.drawable.PictureDrawable;
import android.widget.ImageView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.ImageViewTarget;
import com.bumptech.glide.request.target.Target;

/**
 * Listener which updates the {@link ImageView} to be software rendered, because {@link
 * com.caverock.androidsvg.SVG SVG}/{@link android.graphics.Picture Picture} can't render on a
 * hardware backed {@link android.graphics.Canvas Canvas}.
 */
public class SvgSoftwareLayerSetter implements RequestListener<PictureDrawable> {

  @Override
  public boolean onLoadFailed(
      GlideException e, Object model, Target<PictureDrawable> target, boolean isFirstResource) {
    ImageView view = ((ImageViewTarget<?>) target).getView();
    view.setLayerType(ImageView.LAYER_TYPE_NONE, null);
    return false;
  }

  @Override
  public boolean onResourceReady(
      PictureDrawable resource,
      Object model,
      Target<PictureDrawable> target,
      DataSource dataSource,
      boolean isFirstResource) {
    ImageView view = ((ImageViewTarget<?>) target).getView();
    view.setLayerType(ImageView.LAYER_TYPE_SOFTWARE, null);
    return false;
  }
}
