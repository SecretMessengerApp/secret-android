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
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.util.AttributeSet
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.impl.ProgressIndicator.ProgressData
import com.waz.model.MessageData
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.parts.assets.DeliveryState._
import com.jsy.res.theme.ThemeUtils
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.views.GlyphProgressView
import com.waz.zclient.{R, ViewHelper}

class AssetActionButton(context: Context, attrs: AttributeSet, style: Int) extends GlyphProgressView(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val isFileType = withStyledAttributes(attrs, R.styleable.AssetActionButton) {
    _.getBoolean(R.styleable.AssetActionButton_isFileType, false)
  }

  val zms = inject[Signal[ZMessaging]]
  val assets = inject[AssetsController]
  val message = Signal[MessageData]()
  val accentController = inject[AccentColorController]

  val asset = assets.assetSignal(message)
  val deliveryState = DeliveryState(message, asset)

  val isPlaying = Signal(false)

  val onClicked = EventStream[DeliveryState]

  private[this] var normalButtonDrawable: Drawable = getDrawable(R.drawable.selector__icon_button__background__video_message)
  //private val normalButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message)
  private val errorButtonDrawable = getDrawable(R.drawable.selector__icon_button__background__video_message__error)

  private var onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mime.extension)) else normalButtonDrawable

  def normalButtonDrawable_=(value: Drawable): Unit = {
    this.normalButtonDrawable = value
    this.onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mime.extension)) else normalButtonDrawable
  }

  def normalButtonResource(value: Int) = {
    this.normalButtonDrawable = getDrawable(value)
    this.onCompletedDrawable = if (isFileType) new FileDrawable(asset.map(_._1.mime.extension)) else normalButtonDrawable
  }

  accentController.accentColor.map(_.color).on(Threading.Ui)(setProgressColor)

  private val text = deliveryState flatMap {
    case Complete if !isFileType =>
      isPlaying map {
        case true  => R.string.glyph__pause
        case false => R.string.glyph__play
      }
    case Uploading | Downloading => Signal const R.string.glyph__close
    case _: Failed | Cancelled   => Signal const R.string.glyph__redo
    case _                       => Signal const 0
  } map {
    case 0     => ""
    case resId => getString(resId)
  }

  private val drawable = deliveryState.map {
    case Complete                => onCompletedDrawable
    case Uploading | Downloading => normalButtonDrawable
    case _: Failed | Cancelled   => errorButtonDrawable
    case _                       => null
  }

  private val progress = for {
    assetId <- message.map(_.assetId)
    state <- deliveryState flatMap {
      case Uploading   => assets.uploadProgress(assetId).map(Option(_))
      case Downloading => assets.downloadProgress(assetId).map(Option(_))
      case _           => Signal const Option.empty[ProgressData]
    }
  } yield state

  text.on(Threading.Ui) {
    setText
  }
  drawable.on(Threading.Ui) {
    setBackground
  }

  progress.on(Threading.Ui) {
    case Some(p) =>
      import com.waz.api.ProgressIndicator.State._
      p.state match {
        case CANCELLED | FAILED | COMPLETED => clearProgress()
        case RUNNING if p.total == -1 => startEndlessProgress()
        case RUNNING => setProgress(if (p.total > 0) p.current.toFloat / p.total.toFloat else 0)
        case _ => clearProgress()
      }
    case _ => clearProgress()
  }

  this.onClick {
    deliveryState.currentValue.foreach {
      onClicked ! _
    }
  }

  onClicked {
    cc =>
      LogUtils.i("AssetActionButton", "onClicked {d cc:" + cc)
      cc match {
        case UploadFailed => message.currentValue.foreach(assets.retry(_, true))
        case Uploading => message.currentValue.foreach(assets.cancelUpload)
        case Downloading => message.currentValue.foreach(assets.cancelDownload)
        case _ =>
        // do nothing, individual view parts will handle what happens when in the Completed state.
      }
  }
}

protected class FileDrawable(ext: Signal[String])(implicit context: Context, cxt: EventContext) extends Drawable {

  //private final val textCorrectionSpacing = getDimenPx(R.dimen.wire__padding__4)
  private final val fileGlyph = getString(R.string.glyph__file)
  private final val glyphPaint = new Paint
  //private final val textPaint = new Paint

  //private var extension = ""

  glyphPaint.setTypeface(TypefaceUtils.getGlyphsTypeface)
  glyphPaint.setColor(getColor(if (ThemeUtils.isDarkTheme(context)) R.color.white_80 else R.color.black_80))
  glyphPaint.setAntiAlias(true)
  glyphPaint.setTextAlign(Paint.Align.CENTER)
  glyphPaint.setTextSize(getDimenPx(R.dimen.content__audio_message__button__size))

  //textPaint.setColor(getColor(R.color.white))
  //textPaint.setAntiAlias(true)
  //textPaint.setTextAlign(Paint.Align.CENTER)
  //textPaint.setTextSize(getDimenPx(R.dimen.wire__text_size__tiny))

  //ext.on(Threading.Ui) { ex =>
  //  extension = ex.toUpperCase(Locale.getDefault)
  //  invalidateSelf()
  //}

  override def draw(canvas: Canvas): Unit = {
    canvas.drawText(fileGlyph, getBounds.width / 2, getBounds.height, glyphPaint)
    //canvas.drawText(extension, getBounds.width / 2, getBounds.height - textCorrectionSpacing, textPaint)
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    glyphPaint.setColorFilter(colorFilter)
    //textPaint.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int): Unit = {
    glyphPaint.setAlpha(alpha)
    //textPaint.setAlpha(alpha)
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT
}
