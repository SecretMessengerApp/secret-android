/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.messages.parts

import java.io.File
import android.content.Context
import android.provider.MediaStore
import android.util.{AttributeSet, TypedValue}
import android.view.{Gravity, View, ViewGroup}
import android.widget.FrameLayout
import android.widget.ImageView.ScaleType
import androidx.appcompat.widget.{AppCompatImageView, AppCompatTextView}
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.jsy.common.model.circle.LocalMedia
import com.jsy.common.utils.DateUtils
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Promise

class GalleryItemViewHolder(frameLayout: CursorGalleryItem) extends RecyclerView.ViewHolder(frameLayout) {

  private var uri = Option.empty[URI]
  import GalleryItemViewHolder._

  def bind(localMedia: LocalMedia, callback: CursorImagesLayout.Callback): Unit = {
    val path = localMedia.getPath
    if(!uri.exists(_.getPath == path)) {
      uri = Some(AndroidURIUtil.fromFile(new File(path)))
      val mediaType = localMedia.getMimeType
      if(mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
        frameLayout.setThumbnail(path, HEIGHT, WIDTH)
        frameLayout.onClick(uri.foreach(callback.onGalleryPictureSelected))
      } else if(mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
        frameLayout setVideoThumbnail(path, HEIGHT, WIDTH, localMedia.getDuration)
        frameLayout.onClick(uri.foreach(callback.sendGalleryVideoSelected))
      }
    }
  }
}

object GalleryItemViewHolder{
  private val HEIGHT = 160
  private val WIDTH = 160
}

class CursorGalleryItem(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  import CursorGalleryItem.measuredHeight

  private val imageView = returning(new AppCompatImageView(context)) {
    _.setScaleType(ScaleType.CENTER_CROP)
  }

  private lazy val durationText = returning(new AppCompatTextView(context)) { textview =>
    textview.setVisibility(View.GONE)
    textview.setGravity(Gravity.CENTER)
    textview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12)
    textview.setTextColor(ContextCompat.getColor(context, R.color.white))
    textview.setPadding(0, 0, context.getResources.getDimensionPixelSize(R.dimen.dp6), 0);
  }

  private val durationTextLp = returning(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)) { lp =>
    lp.gravity = Gravity.BOTTOM | Gravity.RIGHT
    lp.height = context.getResources.getDimensionPixelSize(R.dimen.dp25);
  }

  addView(imageView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
  addView(durationText, durationTextLp)

  def setThumbnail(path: String, w: Int, h: Int): Unit = {
    durationText.setVisibility(View.GONE)
    val options = new RequestOptions
    if(w <= 0 && h <= 0) options.sizeMultiplier(0.5f)
    else options.`override`(w, h)
    options.diskCacheStrategy(DiskCacheStrategy.ALL)
    options.centerCrop
    options.placeholder(android.R.color.transparent)
    Glide.`with`(context).load(path).apply(options).into(imageView)
  }

  def setVideoThumbnail(path: String, w: Int, h: Int, duration: Long): Unit = {
    val options = new RequestOptions
    if(w <= 0 && h <= 0) options.sizeMultiplier(0.5f)
    else options.`override`(w, h)
    options.diskCacheStrategy(DiskCacheStrategy.ALL)
    options.centerCrop
    options.placeholder(android.R.color.transparent)
    Glide.`with`(context).load(path).apply(options).into(imageView)
    if(duration > 0) {
      durationText.setText(DateUtils.fromMMss(duration))
      durationText.setVisibility(View.VISIBLE)
    } else {
      durationText.setVisibility(View.GONE)
    }
  }

  override protected def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    super.onMeasure(heightMeasureSpec, heightMeasureSpec) // to make it square
    if(!measuredHeight.isCompleted) measuredHeight.success(this.getMeasuredHeight)
  }
}

object CursorGalleryItem {
  private val measuredHeight = Promise[Int]()
}
