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
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewLayout, MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{R, ViewHelper}

class MissedCallPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  setOrientation(LinearLayout.HORIZONTAL)

  override val tpe: MsgPart = MsgPart.MissedCall

  inflate(R.layout.message_missed_call_content)

  private val gtvIcon: GlyphTextView = findById(R.id.gtv__row_conversation__missed_call__icon)
  private val tvMessage: TypefaceTextView = findById(R.id.tvMessage)

  lazy implicit val uiStorage = inject[UiStorage]
  private lazy val zms = inject[Signal[ZMessaging]]
  private val userId = Signal[UserId]()

  private val user = Signal(zms, userId).flatMap {
    case (z, id) => UserSignal(id)
  }

  private val padding48 = MessageViewLayout.dp_48(getContext)
  private val padding24 = MessageViewLayout.dp_24(getContext)

  private val locale = getLocale
  private val msg = user map {
    case u if u.isSelf => getString(R.string.content__missed_call__you_called)
    case u if u.getDisplayName.isEmpty => ""
    case u =>
      getString(R.string.content__missed_call__xxx_called, u.getDisplayName.toUpperCase(locale))
  }

  msg.on(Threading.Ui) { m =>
    tvMessage.setText(m)
    TextViewUtils.boldText(tvMessage)
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    userId ! msg.message.userId

    opts.foreach { o =>
      gtvIcon.setText(if (o.isSelf) R.string.glyph__call else R.string.glyph__end_call)
      gtvIcon.setTextColor(if (o.isSelf) getColor(R.color.accent_green) else getStyledColor(R.attr.wirePrimaryTextColor))
      if (o.isSelf) {
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
        setPadding(padding48,0, padding24,0)
      } else {
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
        setPadding(padding48,0, padding48,0)
      }
    }
  }
}
