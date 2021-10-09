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
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.NetworkMode
import com.waz.model.{AssetId, Dim2, MessageContent, MessageData}
import com.waz.service.NetworkModeService
import com.waz.service.media.GoogleMapsMediaService
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.messages.{ClickableViewPart, HighlightViewPart, MessageView, MsgPart}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.State
import com.waz.zclient.common.views.ImageController.{DataImage, ImageSource, WireImage}
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.{R, ViewHelper}

class LocationPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ClickableViewPart with ViewHelper with EphemeralPartView with EphemeralIndicatorPartView
    with HighlightViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  override val tpe = MsgPart.Location

  inflate(R.layout.message_location_content)

  val network = inject[NetworkModeService]
  val browser = inject[BrowserController]

  val imageView: View   = findById(R.id.fl__row_conversation__map_image_container)
  val tvName: TextView  = findById(R.id.ttv__row_conversation_map_name)
  val pinView: TextView = findById(R.id.gtv__row_conversation__map_pin_glyph)
  val placeholder: View = findById(R.id.ttv__row_conversation_map_image_placeholder_text)

  private val imageSize = Signal[Dim2]()

  val name = message.map(_.location.fold("")(_.getName))
  val image = for {
    msg <- message
    dim <- imageSize if dim.width > 0
  } yield
    msg.location.fold2[ImageSource](WireImage(msg.assetId), { loc =>
      DataImage(GoogleMapsMediaService.mapImageAsset(AssetId(s"${msg.assetId.str}_${dim.width}_${dim.height}"), loc, dim)) // use dimensions in id, to avoid caching images with different sizes
    })

  val imageDrawable = new ImageAssetDrawable(image, background = Some(new ColorDrawable(getColor(R.color.light_graphite_24))))

  val loadingFailed = imageDrawable.state.map {
    case State.Failed(_, _) => true
    case _ => false
  } .orElse(Signal const false)

  val imageLoaded = imageDrawable.state.map {
    case State.Loaded(_, _, _) => true
    case _ => false
  } .orElse(Signal const false)

  val showPlaceholder = expired flatMap {
    case true => Signal const false
    case false =>
      loadingFailed.zip(network.networkMode) map { case (failed, mode) => failed && mode == NetworkMode.OFFLINE }
  }

  val showPin = expired flatMap {
    case true => Signal const false
    case false => imageLoaded
  }

  registerEphemeral(tvName)
  registerEphemeral(imageView, imageDrawable)

  name { tvName.setText }
  showPin.on(Threading.Ui) { pinView.setVisible }
  showPlaceholder.on(Threading.Ui) { placeholder.setVisible }

  accentController.accentColor { c => pinView.setTextColor(c.color) }

  onClicked { _ =>
    expired.head foreach {
      case true => // ignore click on expired msg
      case false => message.currentValue.flatMap(_.location) foreach { browser.openLocation }
    }
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)

    imageSize ! Dim2(imageView.getWidth, imageView.getHeight)
  }



  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    opts.foreach { opts =>
      val isSameSide@(preIsSameSide, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, part, opts)
      setItemBackground(tpe = tpe, bgView = this, isSelf = opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = false)
    }

  }

}

