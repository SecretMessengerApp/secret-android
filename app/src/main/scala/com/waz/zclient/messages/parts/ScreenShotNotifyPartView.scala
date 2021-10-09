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
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageContent, MessageData, UserId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{AliasSignal, UiStorage, UserSignal}
import com.waz.zclient.{ViewHelper, _}

class ScreenShotNotifyPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper with MessageViewPart with Injectable with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.TextJson_Screen_Shot

  val contentView = inflate(R.layout.message_screen_shot_notify_content)

  val tfvNotifyMessage: TypefaceTextView = findById(R.id.tfvNotifyMessage)

  private val userId = Signal[UserId]()
  private lazy val convController = inject[ConversationController]
  lazy val zms = inject[Signal[ZMessaging]]
  lazy implicit val uiStorage = inject[UiStorage]

  private lazy val user = Signal(zms, userId).flatMap {
    case (_, id) => UserSignal(id)
  }

  private lazy val alias = Signal(convController.currentConv, userId).flatMap {
    case (conversationData, userId) => AliasSignal(conversationData.id, userId)
  }


  (for {
    showName <- user.map(_.getShowName)
    aliasName <- alias.map(_.map(_.getAliasName).filter(_.nonEmpty))
  } yield (showName, aliasName))
    .onUi { parts =>
      val name = parts._2.getOrElse(parts._1)
      val text = String.format(getResources.getString(R.string.conversation_someone_screen_shot), name)
      tfvNotifyMessage.setTransformedText(text)
    }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    val senderUserId = MessageContentUtils.getScreenShotUserId(msg.message.contentString)

    if (!TextUtils.isEmpty(senderUserId)) {
      userId ! UserId(senderUserId)
    } else {
      userId ! UserId.Zero
    }

  }


}
