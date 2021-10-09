/**
 * Secret
 * Copyright (C) 2019 Secret
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
import android.text.{Spannable, SpannableString, SpannableStringBuilder}
import android.util.{AttributeSet, TypedValue}
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.Message
import com.waz.model.{Mention, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages._
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.{BuildConfig, R, ViewHelper}

import java.util.UUID

class TextJsonDisplayPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper with ClickableViewPart {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_text_content)

  override val tpe: MsgPart = MsgPart.TextJson_Display

  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]
  lazy val trackingService = inject[TrackingService]

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  setOrientation(LinearLayout.HORIZONTAL)


  private val textView = findById[LinkTextView](R.id.text)

  textView.setOnClickListener(new OnDoubleClickListener {
    override def onSingleClick(): Unit = TextJsonDisplayPartView.this.onSingleClick()

    override def onDoubleClick(): Unit = TextJsonDisplayPartView.this.onDoubleClick()
  })

  textView.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(v: View): Boolean =
      TextJsonDisplayPartView.this.getParent.asInstanceOf[View].performLongClick()
  })

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    val pr = prev.map(_.contentString)
    val cu = msg.message.contentString
    val ne = next.map(_.contentString)

    opts.foreach { opts =>
      val isSameSide@(preIsSameSide, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, part, opts)
      setItemBackground(tpe = tpe, bgView = this, isSelf = opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = false)
    }
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)

    if(BuildConfig.DEBUG) textView.setText(msg.message.contentString)
    else textView.setText(context.getResources.getString(R.string.conversation_unknow_textjson_message_display))

    textView.setTextLink(null, null)

  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) =
    part.fold(msg.msgType == Message.Type.TEXT_EMOJI_ONLY)(_.tpe == Message.Part.Type.TEXT_EMOJI_ONLY)

}


object TextJsonDisplayPartView {

  case class MentionHolder(mention: Mention, uuid: String, handle: String)

  def replaceMentions(text: String, mentions: Seq[Mention], offset: Int = 0): (String, Seq[MentionHolder]) = {
    val (accStr, mentionHolders, resultIndex) =
      mentions.sortBy(_.start).foldLeft(("", Seq.empty[MentionHolder], 0)) {
        case ((accStr, acc, resultIndex), mention) =>
          val start = mention.start - offset
          val end = start + mention.length
          val uuid = UUID.randomUUID().toString
          (
            accStr + text.substring(resultIndex, start) + uuid,
            acc ++ Seq(MentionHolder(mention, uuid, text.substring(start, end))),
            end
          )
      }

    (
      if (resultIndex < text.length) accStr + text.substring(resultIndex) else accStr,
      mentionHolders
    )
  }

  def updateMentions(text: String, mentionHolders: Seq[MentionHolder], offset: Int = 0): Seq[Mention] =
    mentionHolders.sortBy(_.mention.start).foldLeft((text, Seq.empty[Mention])) {
      case ((oldText, acc), holder) if oldText.contains(holder.uuid) =>
        val start = oldText.indexOf(holder.uuid)
        val end = start + holder.uuid.length
        (
          oldText.substring(0, start) + holder.handle + (if (end < oldText.length) oldText.substring(end) else ""),
          acc ++ Seq(holder.mention.copy(start = start + offset))
        )
      case ((oldText, acc), _) => (oldText, acc) // when Markdown deletes the mention
    }._2

  def restoreMentionHandles(text: CharSequence, mentionHolders: Seq[MentionHolder]): Spannable = {
    val ssb = SpannableStringBuilder.valueOf(text)

    mentionHolders.foldLeft(text.toString) {
      case (oldText, holder) if oldText.contains(holder.uuid) =>
        val start = oldText.indexOf(holder.uuid)
        ssb.replace(start, start + holder.uuid.length, holder.handle)
        oldText.replace(holder.uuid, holder.handle)
      case (oldText, _) => oldText // when Markdown deletes the mention
    }

    new SpannableString(ssb)
  }
}
