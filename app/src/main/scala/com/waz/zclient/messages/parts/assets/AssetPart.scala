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
package com.waz.zclient.messages.parts.assets

import android.content.res.Resources
import android.view.{View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetData, Dim2, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.DeliveryState.{Downloading, OtherUploading}
import com.waz.zclient.messages.parts.{EphemeralIndicatorPartView, EphemeralPartView, ImagePartView}
import com.waz.zclient.messages.{ClickableViewPart, MessageView}
import com.waz.zclient.utils.ContextUtils.getDimen
import com.waz.zclient.utils.{StringUtils, _}
import com.waz.zclient.{R, ViewHelper}

trait AssetPart extends View with ClickableViewPart with ViewHelper with EphemeralPartView with DerivedLogTag {
  self =>
  lazy val controller = inject[AssetsController]

  def layoutList: PartialFunction[AssetPart, Int] = {
    case _: AudioAssetPartView => R.layout.message_audio_asset_content
    case _: FileAssetPartView => R.layout.message_file_asset_content
    case _: ImagePartView => R.layout.message_image_content
    case _: VideoAssetPartView => R.layout.message_video_asset_content
  }

  inflate(layoutList.orElse[AssetPart, Int] {
    case _ => throw new Exception("Unexpected AssetPart view type - ensure you define the content layout and an id for the content for the part")
  }(self))

  lazy val asset = controller.assetSignal(message)
  lazy val deliveryState = DeliveryState(message, asset)
  lazy val completed = deliveryState.map(_ == DeliveryState.Complete)
  lazy val accentColorController = inject[AccentColorController]
  protected val showDots: Signal[Boolean] = deliveryState.map(state => state == OtherUploading)

  lazy val assetBackground = new AssetBackground(showDots, expired, accentColorController.accentColor)

  //toggle content visibility to show only progress dot background if other side is uploading asset
  val hideContent = for {
    exp <- expired
  } yield exp


  def getFullBackGroundView: View = {
    if (self.isInstanceOf[AudioAssetPartView]) {
      self.asInstanceOf[AudioAssetPartView].audioContent
    } else {
      self
    }
  }

  onInflated()

  def onInflated(): Unit


  var isRepliedChild: Boolean = false

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    opts.foreach { opts =>
      val isSameSide@(preIsSameSide, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, part, opts)
      setItemBackground(tpe = tpe, getFullBackGroundView, opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = isRepliedChild)
    }

  }

}

trait ActionableAssetPart extends AssetPart {
  protected val assetActionButton: AssetActionButton = findById(R.id.action_button)

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    assetActionButton.message.publish(msg.message, Threading.Ui)
  }
}

trait PlayableAsset extends ActionableAssetPart {
  val duration = asset.map(_._1).map {
    case AssetData.WithDuration(d) => Some(d)
    case _ => None
  }
  val formattedDuration = duration.map(_.fold("")(d => StringUtils.formatTimeSeconds(d.getSeconds)))

  protected val durationView: TextView = findById(R.id.duration)

  formattedDuration.on(Threading.Ui)(durationView.setText)
}

trait FileLayoutAssetPart extends AssetPart with EphemeralIndicatorPartView {
  private lazy val content: ViewGroup = findById[ViewGroup](R.id.content)
  //For file and audio assets - we can hide the whole content
  //For images and video, we don't want the view to collapse (since they use merge tags), so we let them hide their content separately

  override def onInflated(): Unit = {
    //    content.setBackground(assetBackground)
    hideContent.map(!_).on(Threading.Ui) { v =>
      (0 until content.getChildCount).foreach(content.getChildAt(_).setVisible(v))
    }
  }
}

trait ImageLayoutAssetPart extends AssetPart with EphemeralIndicatorPartView {

  import ImageLayoutAssetPart._

  protected val imageDim = message.map(_.imageDimensions).collect { case Some(d) => d }
  protected val maxWidth = Signal[Int]()
  protected val maxHeight = Signal[Int]()
  override protected val showDots = deliveryState.map(state => state == OtherUploading || state == Downloading)

  private lazy val dpVideoHeight = getDimen(R.dimen.message_content_video_height).toInt
  private lazy val dp_48 = getDimen(R.dimen.message_content_padding_48).toInt
  private lazy val dp_24 = getDimen(R.dimen.message_content_padding_24).toInt

  val forceDownload = this match {
    case _: ImagePartView => false
    case _ => true
  }

  //  private lazy val imageContainer = returning(findById[FrameLayout](R.id.image_container)) {
  //    _.addOnLayoutChangeListener(new OnLayoutChangeListener {
  //      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit =
  //        maxWidth ! v.getWidth
  //    })
  //  }
  //
  //  hideContent.flatMap {
  //    case true => Signal.const[Drawable](assetBackground)
  //    case _ => imageDrawable.state map {
  //      case ImageAssetDrawable.State.Failed(_, _) |
  //           ImageAssetDrawable.State.Loading(_) => assetBackground
  //      case _ => imageDrawable
  //    } orElse Signal.const[Drawable](imageDrawable)
  //  }.on(Threading.Ui) { color =>
  //    //    imageContainer.setBackground(_)
  //
  //  }


  val displaySize = for {
    maxW <- maxWidth
    maxH <- maxHeight
    Dim2(imW, imH) <- imageDim
  } yield {
    if (this.isInstanceOf[VideoAssetPartView]) {
      Dim2(maxW, maxH)
    } else {
      var contentPaddingStart = 0
      var contentPaddingEnd = 0

      val (imSW, imSH) = getImageShowWH(imW, imH, maxW, maxH)

      val centered = if (imSW > maxW) maxW - contentPaddingStart - contentPaddingEnd else imSW

      val heightToWidth = imSH.toDouble / imSW.toDouble

      val width = centered
      //if (imH > imW) centered else maxW
      val height = heightToWidth * width

      //fit image within view port height-wise (plus the little bit of buffer space), if it's height to width ratio is not too big. For super tall/thin
      //images, we leave them as is otherwise they might become too skinny to be viewed properly
      val scaleDownToHeight = maxH * (1 - scaleDownBuffer)
      val scaleDown = if (height > scaleDownToHeight && heightToWidth < scaleDownUnderRatio) scaleDownToHeight.toDouble / height.toDouble else 1D

      val scaledWidth = width * scaleDown

      //finally, make sure the width of the now height-adjusted image is either the full view port width, or less than
      //or equal to the centered area (taking left and right margins into consideration). This is important to get the
      //padding right in the next signal
      val finalWidth =
      if (scaledWidth <= width) scaledWidth
      else if (scaledWidth >= maxW) maxW
      else width
      val finalHeight = heightToWidth * finalWidth

      verbose(l"AssetPart displaySize finalWidth:$finalWidth finalHeight:$finalHeight")
      Dim2(finalWidth.toInt, finalHeight.toInt)
    }
  }

  def getImageShowWH(imW: Int, imH: Int, maxW: Int, maxH: Int): (Int, Int) = {
    verbose(l"AssetPart getImageShowWH imW:$imW ,imH:$imH, maxW:$maxW ,maxH:$maxH")
    var imSW = imW
    var imSH = imH
    if (imW * 2.5 <= maxW && imH <= maxH) {
      imSW = imW * 2
      imSH = imH * 2
      getImageShowWH(imSW, imSH, maxW, maxH)
    } else {
      (imSW, imSH)
    }
  }

  def getMaxWidth(contex: android.content.Context): Int = {
    Resources.getSystem.getDisplayMetrics.widthPixels - dp_24 - dp_48
  }

  def getWrapMaxWidth(contex: android.content.Context, opts: MsgBindOptions): Int = {
    Math.min(Resources.getSystem.getDisplayMetrics.widthPixels - dp_24 - dp_48, opts.listDimensions.width)
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    opts.foreach { opts =>
      //      maxHeight ! opts.listDimensions.height

      if (this.isInstanceOf[VideoAssetPartView]) {
        maxWidth.mutateOrDefault(identity, getMaxWidth(getContext))
        maxHeight ! dpVideoHeight
      } else {
        maxWidth.mutateOrDefault(identity, getWrapMaxWidth(getContext, opts))
        maxHeight ! opts.listDimensions.height
      }

      verbose(l"AssetPart set(....) w->${opts.listDimensions.width}  h->${opts.listDimensions.height}  maxWidth->${maxWidth.currentValue} maxHeight->${maxHeight.currentValue}")
    }

  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)
    maxWidth ! (right - left)
    verbose(l"AssetPart onLayout(....)  l->$left t->$top r->$right  b->$bottom  w->${right - left}  h->${bottom - top}")
  }
}

object ImageLayoutAssetPart {
  //a little bit of space for scaling images within the viewport
  val scaleDownBuffer = 0.05

  //Height to width - images with a lower ratio will be scaled to fit in the view port. Taller images will be allowed to keep their size
  val scaleDownUnderRatio = 2.0
}
