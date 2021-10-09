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

import java.util.UUID

import android.content.Context
import android.graphics.Color
import android.text.{Spannable, SpannableString, SpannableStringBuilder}
import androidx.recyclerview.widget.RecyclerView
import android.util.{AttributeSet, TypedValue}
import android.view.View
import android.widget.{LinearLayout, TextView}
import com.jsy.res.utils.ColorUtils
import com.waz.api.{ContentSearchQuery, Message}
import com.waz.model.{Mention, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.{CollectionController, CollectionUtils}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.{ClickableViewPart, HighlightViewPart, MessageView, MsgPart}
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.{BuildConfig, R, ViewHelper}

class TextPartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper with ClickableViewPart with EphemeralPartView
    with EphemeralIndicatorPartView with MentionsViewPart with HighlightViewPart {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_text_content)

  override val tpe: MsgPart = MsgPart.Text

  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]
  lazy val trackingService = inject[TrackingService]
  val messageActionsController = inject[MessageActionsController]
  private lazy val messageActions = inject[MessageActionsController]

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  private var data: MessageAndLikes = MessageAndLikes.Empty

  setOrientation(LinearLayout.HORIZONTAL)


  private val textView = findById[LinkTextView](R.id.text)

  private val translate_textView = findById[LinkTextView](R.id.translate_text)

  private val translate_line = findById[View](R.id.translate_line)

  private val text_part = findById[LinearLayout](R.id.text_part)

  registerEphemeral(textView)

  textView.setOnClickListener(new OnDoubleClickListener {
    override def onSingleClick(): Unit = TextPartView.this.onSingleClick()

    override def onDoubleClick(): Unit = TextPartView.this.onDoubleClick()
  })

  text_part.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(v: View): Boolean =
      messageActions.showDialog(data)
  })

  textView.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(v: View): Boolean =
      messageActions.showDialog(data)
  })

  private def setText(text: String): Unit = { // TODO: remove try/catch blocks when the bug is fixed
    try {
      textView.setTransformedText(text)
    } catch {
      case ex: ArrayIndexOutOfBoundsException =>
        info(l"Error while transforming text link. text: ${redactedString(text)}")
        if (BuildConfig.FLAVOR == "internal") throw ex
    }

    try {
      textView.markdown()
    } catch {
      case ex: ArrayIndexOutOfBoundsException =>
        info(l"Error on markdown. text: ${redactedString(text)}")
        if (BuildConfig.FLAVOR == "internal") throw ex
    }
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    //animator.end()
    stopHighlight()
    super.set(msg, prev, next, part, opts, adapter)
    data = msg

    val pr = prev.map(_.contentString)
    val cu = msg.message.contentString
    val ne = next.map(_.contentString)

    opts.foreach { opts =>
      val isSameSide@(preIsSameSide, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, part, opts)
      setItemBackground(tpe = tpe, bgView = this, isSelf = opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = false)
      if(opts.isSelf){
        translate_line.setBackgroundColor(ColorUtils.getAttrColor(getContext,R.attr.SecretSelfTranslateDividerColor))
      }else{
        translate_line.setBackgroundColor(ColorUtils.getAttrColor(getContext,R.attr.SecretOtherTranslateDividerColor))
      }
    }

    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)
    translate_textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (isEmojiOnly(msg.message, part)) textSizeEmoji else textSizeRegular)
    translate_textView.setTextColor(ColorUtils.getAttrColor(getContext,R.attr.SecretSecondTextColor))

    if (msg.message.translateContent.isEmpty) {
      translate_textView.setVisibility(View.GONE)
      translate_line.setVisibility(View.GONE)
    } else {
      msg.message.translateContent.foreach {
        case translateContent =>
          translate_textView.setVisibility(View.VISIBLE)
          translate_line.setVisibility(View.VISIBLE)
          translate_textView.setText(translateContent)
        case _ =>
          translate_textView.setVisibility(View.GONE)
          translate_line.setVisibility(View.GONE)
      }
    }



    val contentString = msg.message.contentString
    val (text, offset) = part.fold(contentString, 0)(ct => (ct.content, contentString.indexOf(ct.content)))
    val mentions = msg.message.content.flatMap(_.mentions)

    if (mentions.isEmpty) setText(text)
    else {
      // https://github.com/wearezeta/documentation/blob/master/topics/mentions/use-cases/002-receive-and-display-message.md#step-2-replace-mention-in-message
      val (replaced, mentionHolders) = TextPartView.replaceMentions(text, mentions, offset)

      setText(replaced)

      val updatedMentions = TextPartView.updateMentions(textView.getText.toString, mentionHolders, offset)

      val spannable = TextPartView.restoreMentionHandles(textView.getText, mentionHolders)
      addMentionSpans(
        spannable,
        updatedMentions,
        opts.flatMap(_.selfId),
        accentColorController.accentColor.map(_.color).currentValue.getOrElse(Color.BLUE)
      )
      textView.setText(spannable)
    }

    textView.setTextLink(null, null)

  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) =
    part.fold(msg.msgType == Message.Type.TEXT_EMOJI_ONLY)(_.tpe == Message.Part.Type.TEXT_EMOJI_ONLY)

}

object TextPartView {

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
