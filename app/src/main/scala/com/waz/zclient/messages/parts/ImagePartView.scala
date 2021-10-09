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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, LinearLayout}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.utils.DensityUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Dim2, MessageContent, MessageData}
import com.waz.service.downloads.AssetLoader.DownloadOnWifiOnlyException
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.threading.Threading
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.views.{ImageAssetDrawable, RoundedImageAssetDrawable}
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.ImageLayoutAssetPart
import com.waz.zclient.messages.{HighlightViewPart, MessageViewPart, MsgPart}
import com.waz.zclient.utils.{Offset, RichView}
import com.waz.zclient.common.views.ImageAssetDrawable.State.Failed
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.log.LogUI._

class ImagePartView(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
  with ImageLayoutAssetPart
  with HighlightViewPart
  with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Image

  private lazy val assets = inject[AssetsController]
  private lazy val messageActions = inject[MessageActionsController]

  private val flImageParent = findById[FrameLayout](R.id.flImageParent)
  private val imageIcon = findById[View](R.id.image_icon)
  private val imageAssetImagePartContent = findById[android.widget.ImageView](R.id.imageAssetImagePartContent)

  private var data: MessageAndLikes = MessageAndLikes.Empty

  private lazy val imageRadius = DensityUtils.dp2px(getContext, 5.0F)
  private val imageDrawable = new RoundedImageAssetDrawable(message map { m => WireImage(m.assetId) }
    , forceDownload = forceDownload, scaleType = ScaleType.FitXY, cornerRadius = imageRadius)

  override def getFullBackGroundView: View = {
    flImageParent
  }

  val margin = for {
    maxW <- maxWidth
    Dim2(dW, dH) <- displaySize
  } yield {
    if (dW >= maxW) Offset.Empty
    else {
      val contentPaddingStart = 0
      val contentPaddingEnd = 0
      val left = if (getLayoutDirection == View.LAYOUT_DIRECTION_LTR) contentPaddingStart else maxW - contentPaddingStart - dW

      verbose(l"AssetPart Offset(....)  l->$left t->0 r->${maxW - dW} b->0")
      Offset(left, 0, maxW - dW - left, 0)
      //      Offset(0, 0, 0, 0)
    }
  }

  displaySize.onUi { wh =>
    flImageParent.setLayoutParams(returning(flImageParent.getLayoutParams) { lp =>

      lp.width = wh.width
      lp.height = wh.height

      verbose(l"AssetPart displaySize w->${lp.width} h->${lp.height}")
    })
  }

  margin.on(Threading.Ui) {
    m =>
      flImageParent.setMargin(m)
      verbose(l"AssetPart Margin Image l->${m.l} t->${m.t} r->${m.r} b->${m.b}")
  }

  val noWifi = imageDrawable.state.map {
    case Failed(_, Some(DownloadOnWifiOnlyException)) => true
    case _ => false
  }

  (for {
    noW <- noWifi
    hide <- hideContent
  } yield !hide && noW).on(Threading.Ui) { visibility =>
    imageIcon.setVisible(visibility)
    imageAssetImagePartContent.setVisible(!visibility)
  }

  setAssetImage(assetBackground)

  def setAssetImage(assetBackground: Drawable): Unit = {

    val w = assetBackground.getIntrinsicWidth
    val h = assetBackground.getIntrinsicHeight

    verbose(l"AssetPart setAssetImage assetBackground w->${w} h->${h}")

    imageAssetImagePartContent.setBackground(assetBackground)
  }

  hideContent.flatMap {
    case true => Signal.const[Drawable](assetBackground)
    case _ => imageDrawable.state map {
      case ImageAssetDrawable.State.Failed(_, _) |
           ImageAssetDrawable.State.Loading(_) => assetBackground
      case _ => imageDrawable
    } orElse Signal.const[Drawable](imageDrawable)
  }.on(Threading.Ui)(setAssetImage)

  imageAssetImagePartContent.setOnClickListener(new OnDoubleClickListener {
    override def onSingleClick(): Unit = {
      message.head.map(assets.showSingleImage(_, ImagePartView.this))(Threading.Ui)
    }
    override def onDoubleClick(): Unit = {
      ImagePartView.this.onDoubleClick()
    }
  })

  imageAssetImagePartContent.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(view: View): Boolean = {
      messageActions.showDialog(data)
    }
  })

  override def onInflated(): Unit = {}


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    data = msg
  }
}

class WifiWarningPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.WifiWarning

  inflate(R.layout.message_wifi_warning_content)

  //A little bit hacky - but we can safely rely on the fact there should be an ImagePartView for each WifiWarningPartView
  //def to ensure we only get the ImagePartView after the view is attached to the window (the parent will be null otherwise)
  def imagePart: Option[ImagePartView] = Option(getParent).map(_.asInstanceOf[ViewGroup]).flatMap { p =>
    (0 until p.getChildCount).map(p.getChildAt).collectFirst {
      case v: ImagePartView => v
    }
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    this.setVisible(false) //setVisible(true) is called for all view parts shortly before setting...
  }

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    imagePart.foreach(_.noWifi.on(Threading.Ui)(this.setVisible))
  }
}


