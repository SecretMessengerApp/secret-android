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
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.{IConversation, Message}
import com.waz.api.Message.Type.MEMBER_JOIN
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.UsersController.DisplayName.{Me, Other}
import com.waz.zclient.messages._
import com.waz.zclient.paintcode.ConversationIcon
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class MemberChangePartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with MessageViewPart
    with ViewHelper
    with DerivedLogTag {
  
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.MemberChange

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_member_change_content)

  private lazy val zMessaging = inject[Signal[ZMessaging]]
  private lazy val users = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]

  val messageView: SystemMessageView = findById(R.id.smv_header)
  val warningText: TextView = findById(R.id.service_warning_text)
  val position = Signal[Int]()

  val iconGlyph: Signal[Either[Int, Drawable]] = message map { msg =>
    msg.msgType match {
      case Message.Type.MEMBER_JOIN if msg.firstMessage => Right(ConversationIcon(R.color.light_graphite))
      case Message.Type.MEMBER_JOIN => Left(R.string.glyph__plus)
      case _ => Left(R.string.glyph__minus)
    }
  }


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    messageView.setVisibility(View.GONE)

    opts.foreach(position ! _.position)

  }

  val senderName = message.map(_.userId).flatMap(users.displayName)

  private val linkText = for {
    zms <- zMessaging
    msg <- message
    displayName <- senderName
    conversationData <- convController.currentConv
    names <- users.getMemberNamesSplit(msg.members, zms.selfUserId)
    namesListString = users.membersNamesString(names.main, separateLast = names.others.isEmpty && !names.andYou, boldNames = true)
  } yield {
    import Message.Type._
    val me = zms.selfUserId
    val userId = msg.userId
    val shorten = names.others.nonEmpty
    val othersCount = names.others.size

    val flag = false

    val showMessage = conversationData.view_chg_mem_notify

    (msg.msgType, displayName, msg.members.toSeq) match {
      //Create Conv
      case (MEMBER_JOIN, Me, _) if msg.firstMessage && msg.name.isDefined && shorten       =>
        if(showMessage) getQuantityString(R.plurals.content__system__with_others_only, othersCount, namesListString, othersCount.toString) else ""
      case (MEMBER_JOIN, Me, _) if msg.firstMessage && msg.name.isDefined                  =>
        if(showMessage) getString(R.string.content__system__with_list_only, namesListString) else ""
      case (MEMBER_JOIN, Other(_), Seq(`me`)) if msg.firstMessage && msg.name.isDefined    =>
        getString(R.string.content__system__with_you_only)
      case (MEMBER_JOIN, Other(_), _) if msg.firstMessage && msg.name.isDefined && shorten =>
        getQuantityString(R.plurals.content__system__with_others_and_you, othersCount, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Other(_), _) if msg.firstMessage && msg.name.isDefined            =>
        getString(R.string.content__system__with_list_and_you, namesListString)

      //Add
      case (MEMBER_JOIN, Me, _) if shorten                                      =>
        getQuantityString(R.plurals.content__system__you_added_people_with_others, othersCount, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Me, _)                                                 =>
        if(TextUtils.isEmpty(namesListString)){
          getString(R.string.content__system__you_added_suc)
        }else{
          getString(R.string.content__system__you_added_people, namesListString)
        }
      case (MEMBER_JOIN, Other(name), Seq(`me`))                                =>
        getString(R.string.content__system__someone_added_you, name)
      case (MEMBER_JOIN, Me, Seq(`me`)) if userId == me                         =>
        if(flag) getString(R.string.content__system__you_joined).toUpperCase else ""
      case (MEMBER_JOIN, Other(name), Seq(other)) if userId == other            =>
        if(flag) getString(R.string.content__system__other_joined, name) else ""
      case (MEMBER_JOIN, Other(name), _) if shorten && msg.members.contains(me) =>
        getQuantityString(R.plurals.content__system__someone_added_people_and_you_with_others, othersCount, name, namesListString, othersCount.toString)
      case (MEMBER_JOIN, Other(name), _) if shorten                             =>
        if(showMessage) getQuantityString(R.plurals.content__system__someone_added_people_with_others, othersCount, name, namesListString, othersCount.toString) else ""
      case (MEMBER_JOIN, Other(name), _)                                        =>
        if(showMessage) getString(R.string.content__system__someone_added_people, name, namesListString) else ""

      //Remove
      case (MEMBER_LEAVE, Me, Seq(`me`))              =>
        getString(R.string.content__system__you_left)
      case (MEMBER_LEAVE, Me, _)                      =>
        getString(R.string.content__system__you_removed_other, namesListString)
      case (MEMBER_LEAVE, Other(name), Seq(`me`))     =>
        getString(R.string.content__system__other_removed_you, name)
      case (MEMBER_LEAVE, Other(name), Seq(`userId`)) =>
        if(showMessage) getString(R.string.content__system__other_left, name) else ""
      case (MEMBER_LEAVE, Other(name), _)             =>
        if(showMessage) getString(R.string.content__system__other_removed_other, name, namesListString) else ""
      case _                                          =>
        verbose(l"Unexpected system message format: (${msg.msgType} from $displayName with ${msg.members.toSeq})")
        ""
    }
  }

  message.map(m => if (m.firstMessage && m.name.nonEmpty) Some(16) else None)
    .map(_.map(toPx))
    .onUi(_.foreach(this.setMarginTop))

  iconGlyph {
    case Left(i) => messageView.setIconGlyph(i)
    case Right(d) => messageView.setIcon(d)
  }

  linkText.onUi { text =>
    messageView.setVisibility(View.VISIBLE)

    if (TextUtils.isEmpty(text)){
      setVisibility(View.GONE)
    }else{
      setVisibility(View.VISIBLE)
    }
    messageView.setTextWithLink(text, getColor(R.color.accent_blue)) {
      participantsController.onShowParticipants ! None
    }
  }

}
