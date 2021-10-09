/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.httpapi.{EmojiGifService, SimpleHttpListener}
import com.jsy.common.model.EmojiGifModel
import com.jsy.common.utils.{ScreenUtils, Utils}
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.emoji.OnEmojiChangeListener
import com.waz.zclient.emoji.bean.{EmojiBean, EmojiResponse}
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog
import com.waz.zclient.emoji.utils.EmojiUtils
import com.waz.zclient.messages.parts.EmojiGifPartView._
import com.waz.zclient.messages.{ClickableViewPart, MessageView, MsgPart}
import org.telegram.ui.Components.RLottieImageView

import scala.concurrent.Future

class EmojiGifPartView(context: Context, attrs: AttributeSet, style: Int) extends android.widget.FrameLayout(context, attrs, style)
  with ClickableViewPart with EphemeralPartView with EphemeralIndicatorPartView {

  private var stickerDialog: EmojiStickerSetDialog = _

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  val view = inflate(R.layout.message_emoji_gif_content, this, true)

  val imageView: RLottieImageView = view.findViewById(R.id.imageAssetImagePartContent)
  imageView.setImageDrawable(context.getDrawable(R.drawable.emoji_placeholder))


  override val tpe: MsgPart = MsgPart.TextJson_EmojiGifPart

  private var model: EmojiGifModel = _
  private lazy val width = ScreenUtils.dip2px(getContext, 100)

  override def onSingleClick(): Unit = {
    if (model != null && model.msgData != null) {
      showSticker(model.msgData.id)
    }
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MessageView.MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    model = EmojiGifModel.parseJson(msg.message.contentString)
    if (model != null && model.msgData != null) {
      EmojiUtils.loadImage(context, model.msgData.url, imageView, width, width)
    }
  }

  private def showSticker(id: String): Unit = {
    import scala.collection.JavaConverters._
    val list = EmojiUtils.getAllDbEmoji
    if (list != null && list.size() > 0) {
      list.asScala.find(it => it.getId.toString == id) match {
        case Some(data) =>showStickerDialog(data)
        case None =>
      }
    }
  }

  private def showStickerDialog(data: EmojiBean) {
    if (stickerDialog != null && stickerDialog.isShowing) {
      stickerDialog.dismiss()
      stickerDialog = null
    }
    if (!Utils.isDestroyed(getContext)) {
      stickerDialog = new EmojiStickerSetDialog(getContext)
      stickerDialog.setOnEmojiChangeListener(new OnEmojiChangeListener {
        override def onEmojiAdd(emojiBean: EmojiBean): Unit = {
          addOrDelete(emojiBean)
        }

        override def onEmojiRemove(emojiBean: EmojiBean): Unit = {
          addOrDelete(emojiBean, remove = true)
        }

        override def onEmojiChanged(): Unit = {}
      })
      stickerDialog.setData(data)
      stickerDialog.show()
    }
  }

  private def addOrDelete(itemBean: EmojiBean, remove: Boolean = false): Unit = {
    Future {
      if (remove) {
        EmojiUtils.RemoveEmojiInDb(itemBean)
      } else {
        EmojiUtils.AddEmoji2Db(itemBean)
      }
    }(Threading.Background)
  }
}

object EmojiGifPartView {
}
