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
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.{RelativeLayout}
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages.{ClickableViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

class PingPartView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ClickableViewPart with ViewHelper with EphemeralPartView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Ping

  inflate(R.layout.message_ping_content)

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)

  val textViewMessage: TypefaceTextView = findById(R.id.ttv__row_conversation__ping_message)
  val glyphTextView: GlyphTextView = findById(R.id.gtv__ping_icon)

  val locale = getLocale

  val users = inject[UsersController]

  val userName = message.map(_.userId).flatMap(users.displayName)

  val text = userName map {
    case Me => getString(R.string.content__you_pinged)
    case Other(name) => getString(R.string.content__xxx_pinged, name.toUpperCase(locale))
  }

  (for {
    t <- text
    exp <- expired
  } yield (t, exp)).on(Threading.Ui) {
    case (t, true) =>
      textViewMessage.setText(t)
      //making the text bold ruins obfuscation for some reason
    case (t, _) =>
      textViewMessage.setText(t)
      TextViewUtils.boldText(textViewMessage)
  }

  message.map(_.userId).flatMap(users.accentColor).map(_.color).onUi { c =>
    textViewMessage.setTextColor(c)
    glyphTextView.setTextColor(c)
  }

  registerEphemeral(textViewMessage)
  registerEphemeral(glyphTextView)


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    opts.foreach { opts =>

      val textLp: RelativeLayout.LayoutParams = textViewMessage.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]
      textLp.addRule(RelativeLayout.ALIGN_PARENT_START)
      textViewMessage.setLayoutParams(textLp)

      val iconLp: RelativeLayout.LayoutParams = glyphTextView.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]
      iconLp.addRule(RelativeLayout.END_OF, R.id.ttv__row_conversation__ping_message)
      iconLp.setMarginStart(getResources.getDimension(R.dimen.wire__padding__small).toInt)
      glyphTextView.setLayoutParams(iconLp)


      if (opts.isSelf) {
        this.setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
      } else {
        this.setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
      }
    }
  }

  // TODO: animate new ping, we need some generic controller to track message animations acrosss recycled views
}
