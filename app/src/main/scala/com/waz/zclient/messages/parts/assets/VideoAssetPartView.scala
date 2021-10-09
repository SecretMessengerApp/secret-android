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

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, ImageView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.messages.{HighlightViewPart, MsgPart}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.common.views.ImageAssetDrawable.State.Loaded
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.log.LogUI._

class VideoAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with PlayableAsset with ImageLayoutAssetPart with HighlightViewPart with DerivedLogTag{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.VideoAsset

  private val controls = findById[View](R.id.controls)
  private val imageAssetVideoPartContent :ImageView = findViewById[View](R.id.imageAssetVideoPartContent).asInstanceOf[ImageView]

  private val imageDrawable = new ImageAssetDrawable(message map { m => WireImage(m.assetId) }, forceDownload = forceDownload, scaleType = ScaleType.CenterCrop)

  hideContent.map(!_).on(Threading.Ui)(controls.setVisible)

  hideContent.flatMap {
    case true => Signal.const[Drawable](assetBackground)
    case _ => imageDrawable.state map {
      case ImageAssetDrawable.State.Failed(_, _) |
           ImageAssetDrawable.State.Loading(_) => assetBackground
      case _ => imageDrawable
    } orElse Signal.const[Drawable](imageDrawable)
  }.on(Threading.Ui)(setAssetImage)

  imageDrawable.state.map {
    case Loaded(_, _, _) => getColor(R.color.white)
    case _ => getColor(R.color.black)
  }.on(Threading.Ui)(durationView.setTextColor)

  asset.disableAutowiring()

  assetActionButton.onClicked.filter(_ == DeliveryState.Complete) { _ =>
    asset.currentValue foreach { case (a, _) =>
      controller.openFile(a)
    }
  }

  displaySize { d =>
    setLayoutParams(returning(getLayoutParams) { lp =>
      //      lp.width = d.width
      lp.height = d.height
    })
  }

  setAssetImage(assetBackground)

  def setAssetImage(assetBackground: Drawable): Unit = {
    verbose(l"setAssetImage dw:${assetBackground.getIntrinsicWidth} dh:${assetBackground.getIntrinsicHeight}")
    imageAssetVideoPartContent.setImageDrawable(assetBackground)
  }

  override def onInflated(): Unit = {}

  override def getFullBackGroundView: View = {
    controls
  }
}
