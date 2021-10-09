/**
 * Secret
 * Copyright (C) 2021 Secret
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
import android.graphics.Typeface
import android.text.style.MetricAffectingSpan
import android.text.{SpannableString, Spanned, TextPaint}
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.messages._
import com.waz.zclient.messages.parts.StartedUsingDeviceMsgPartView._
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class StartedUsingDeviceMsgPartView(context: Context, attrs: AttributeSet, style: Int)
  extends RelativeLayout(context, attrs, style)
    with MessageViewPart
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.StartedUsingDevice

  inflate(R.layout.message_started_using_device_content)

  private val textView: LinkTextView = findById(R.id.ttv__system_message__text)

  lazy val participantsController = inject[ParticipantsController]

  private lazy val glyphsTypefaceSpan = new GlyphsTypefaceSpan(TypefaceUtils.getGlyphsTypeface)

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MessageView.MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    textView.setText("")

    if (msg.message.msgType == Message.Type.STARTED_USING_DEVICE) {
      val conversationType = participantsController.conv.currentValue.map(_.convType).getOrElse(ConversationType.Unknown)
      val contentStr = if (conversationType == ConversationType.ThousandsGroup) {
        getString(R.string.conversation_list_no_encrypte_notifications /*content__otr__start_this_device__message*/)
      } else {
        getString(R.string.conversation_list_encrypte_notifications /*content__otr__start_this_device__message*/)
      }

      val iconStr = new SpannableString(getString(R.string.glyph__lock))
      iconStr.setSpan(glyphsTypefaceSpan, 0, iconStr.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE)

      textView.append(iconStr)
      textView.append("  ")
      textView.append(contentStr)
    }
  }
}

object StartedUsingDeviceMsgPartView {

  private class GlyphsTypefaceSpan(typeface: Typeface) extends MetricAffectingSpan {

    override def updateMeasureState(textPaint: TextPaint): Unit = {
      changed(textPaint)
    }

    override def updateDrawState(textPaint: TextPaint): Unit = {
      changed(textPaint)
    }

    private def changed(textPaint: TextPaint): Unit = {
      textPaint.setTypeface(typeface)
    }
  }
}
