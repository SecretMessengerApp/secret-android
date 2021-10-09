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
package com.waz.zclient.conversation

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import android.util.{AttributeSet, TypedValue}
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, ImageButton, ImageView, TextView}
import com.facebook.rebound.ui.Util
import com.waz.api.Message.Type
import com.waz.model.{AssetData, AssetId, MessageData}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.WireImage
import com.waz.zclient.common.views.RoundedImageAssetDrawable
import com.waz.zclient.conversation.ReplyView.ConversationReplyBackgroundDrawable
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.paintcode.WireStyleKit.ResizingBehavior
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichTextView, RichView}
import com.waz.zclient.{R, ViewHelper}

class ReplyView(context: Context, attrs: AttributeSet, defStyle: Int) extends FrameLayout(context, attrs, defStyle) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.reply_view)

  private val closeButton = findById[ImageButton](R.id.reply_close)
  private val senderText = findById[TextView](R.id.reply_sender)
  private val contentText = findById[LinkTextView](R.id.reply_content)
  private val image = findById[ImageView](R.id.reply_image)
  private val container = findById[ViewGroup](R.id.reply_container)

  private var onClose: () => Unit = () => {}

  closeButton.onClick(onClose())

  setOnClickListener(new View.OnClickListener {
    override def onClick(v: View): Unit = {}
  })

  container.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
  container.setBackground(new ConversationReplyBackgroundDrawable(getStyledColor(R.attr.replyBorderColor), getStyledColor(R.attr.SecretThreeBackgroundColor)
    , Util.dpToPx(4.0F, getResources)))

  def setOnClose(onClose: => Unit): Unit = this.onClose = () => onClose

  def setMessage(messageData: MessageData, assetData: Option[AssetData], senderName: String): Unit = {
    setSender(senderName, !messageData.editTime.isEpoch)
    messageData.msgType match {
      case Type.TEXT | Type.TEXT_EMOJI_ONLY | Type.RICH_MEDIA =>
        set(messageData.contentString, bold = false, None, None)
      case Type.LOCATION =>
        set(messageData.location.map(_.getName).getOrElse(getString(R.string.reply_message_type_location)), bold = true, Some(WireStyleKit.drawLocation), None)
      case Type.VIDEO_ASSET =>
        set(getString(R.string.reply_message_type_video), bold = true, Some(WireStyleKit.drawVideocall), Some(messageData.assetId))
      case Type.ASSET =>
        set(getString(R.string.reply_message_type_image), bold = true, Some(WireStyleKit.drawImage), Some(messageData.assetId))
      case Type.AUDIO_ASSET =>
        set(getString(R.string.reply_message_type_audio), bold = true, Some(WireStyleKit.drawVoiceMemo), None)
      case Type.ANY_ASSET =>
        set(assetData.flatMap(_.name).getOrElse(getString(R.string.reply_message_type_asset)), bold = true, Some(WireStyleKit.drawFile), None)
      case _ =>
      // Other types shouldn't be able to be replied to
    }
  }

  private def setSender(name: String, edited: Boolean): Unit = {
    senderText.setText(name)
    senderText.setEndCompoundDrawable(if (edited) Some(WireStyleKit.drawEdit) else None, getStyledColor(R.attr.wirePrimaryTextColor))
  }

  private def set(text: String, bold: Boolean, drawMethod: Option[(Canvas, RectF, ResizingBehavior, Int) => Unit], imageAsset: Option[AssetId]): Unit = {
    contentText.setText(text)
    if (bold){
      contentText.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
      contentText.setAllCaps(true)
      contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__smaller))
    }  else {
      contentText.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__regular)))
      contentText.setAllCaps(false)
      contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__small))
      contentText.markdownQuotes()
    }
    setStartIcon(drawMethod)
    imageAsset.fold {
      image.setVisibility(View.GONE)
    } { assetId =>
      val imageDrawable = new RoundedImageAssetDrawable(Signal.const(WireImage(assetId)), scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single, cornerRadius = 10)
      image.setVisibility(View.VISIBLE)
      image.setImageDrawable(imageDrawable)
    }
  }

  private def setStartIcon(drawMethod: Option[(Canvas, RectF, ResizingBehavior, Int) => Unit]): Unit = {
    contentText.setStartCompoundDrawable(drawMethod, getStyledColor(R.attr.wirePrimaryTextColor))
  }
}

object ReplyView {

  class ReplyBackgroundDrawable(borderColor: Int, backgroundColor: Int) extends Drawable {

    private val paint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(borderColor))
    private val radius = 18
    private val strokeWidth = 0
    private val sideBarWidth = 15

    override def draw(canvas: Canvas): Unit = {
      val rect = new RectF(canvas.getClipBounds)
      rect.inset(strokeWidth, strokeWidth)

      paint.setXfermode(null)
      paint.setStyle(Paint.Style.FILL_AND_STROKE)
      paint.setColor(backgroundColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeWidth(strokeWidth)
      paint.setColor(borderColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
      paint.setStyle(Paint.Style.FILL)
      canvas.drawRect(0, rect.top, sideBarWidth, rect.bottom, paint)

    }

    override def setAlpha(alpha: Int): Unit = paint.setAlpha(alpha)

    override def setColorFilter(colorFilter: ColorFilter): Unit = paint.setColorFilter(colorFilter)

    override def getOpacity: Int = paint.getAlpha
  }

  class MessageReplyBackgroundDrawable(borderColor: Int, backgroundColor: Int, sideBarColor: Int) extends Drawable {

    private val paint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(borderColor))
    private val radius = 18
    private val strokeWidth = 0
    private val sideBarWidth = 15

    override def draw(canvas: Canvas): Unit = {
      val rect = new RectF(canvas.getClipBounds)
      rect.inset(strokeWidth, strokeWidth)

      paint.setXfermode(null)
      paint.setStyle(Paint.Style.FILL_AND_STROKE)
      paint.setColor(backgroundColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setStyle(Paint.Style.STROKE)
      paint.setStrokeWidth(strokeWidth)
      paint.setColor(borderColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setColor(sideBarColor)
      paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
      paint.setStyle(Paint.Style.FILL)
      canvas.drawRect(0, rect.top, sideBarWidth, rect.bottom, paint)

    }

    override def setAlpha(alpha: Int): Unit = paint.setAlpha(alpha)

    override def setColorFilter(colorFilter: ColorFilter): Unit = paint.setColorFilter(colorFilter)

    override def getOpacity: Int = paint.getAlpha
  }

  class ConversationReplyBackgroundDrawable(borderColor: Int, backgroundColor: Int, sideBarWidth:Int = 15) extends Drawable {

    private val paint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(borderColor))
    private val radius = 0
    private val strokeWidth = 0

    override def draw(canvas: Canvas): Unit = {
      val rect = new RectF(canvas.getClipBounds)
      rect.inset(strokeWidth, strokeWidth)

      paint.setXfermode(null)
      paint.setStyle(Paint.Style.FILL_AND_STROKE)
      paint.
        setColor(backgroundColor)
      canvas.drawRoundRect(rect, radius, radius, paint)

      paint.setColor(Color.parseColor("#F27405"))
      paint.setStyle(Paint.Style.FILL)
      canvas.drawRect(0, rect.top, sideBarWidth, rect.bottom, paint)
    }

    override def setAlpha(alpha: Int): Unit = paint.setAlpha(alpha)

    override def setColorFilter(colorFilter: ColorFilter): Unit = paint.setColorFilter(colorFilter)

    override def getOpacity: Int = paint.getAlpha
  }
}
