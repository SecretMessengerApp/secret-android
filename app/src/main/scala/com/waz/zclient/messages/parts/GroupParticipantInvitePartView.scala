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

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.model.GroupParticipantInviteConfirmModel
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.EventStream
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.{ConversationListAdapter, ConversationListController}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.participants.{GroupParticipantInviteActivity, ParticipantsController}
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{R, ViewHelper}


class GroupParticipantInvitePartView(context: Context, attrs: AttributeSet, style: Int) extends android.widget.FrameLayout(context, attrs, style) with ViewHelper with ClickableViewPart with EphemeralPartView {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  val view = inflate(R.layout.message_group_participant_invite_content, this, true)

  val ttvTitle: TextView = view.findViewById(R.id.ttvTitle)
  val civConversationHead: ChatHeadViewNew = view.findViewById(R.id.civConversationHead)
  val ttvContent: TextView = view.findViewById(R.id.ttvContent)

  override val onClicked = EventStream[Unit]()
  lazy val activity = inject[Activity]
  private lazy val convController = inject[ConversationController]

  override val tpe: MsgPart = MsgPart.TextJson_Group_Participant_Invite

  val convListController = inject[ConversationListController]
  val collectionController = inject[CollectionController]
  val accentColorController = inject[AccentColorController]

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__emoji)


  onClicked { _ =>
  }

  view.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if (model != null && !isSelf) {
        convListController.conversationGroupOrThousandsGroupList(ConversationListAdapter.GroupOrThousandGroup).currentValue.foreach { selfId_groups =>
          val existedConve = selfId_groups._2.filter { c =>
            c.remoteId.str.equals(model.msgData.conversationId) && c.isActive
          }
          if (existedConve.isEmpty) {
            participantsController.otherParticipant.currentValue.foreach { other =>
              GroupParticipantInviteActivity.startSelf(activity, model, other)
            }
          } else {
            convController.selectConv(Option(existedConve.head.id), ConversationChangeRequester.UPDATE_CONVERSATION_ACTIVITY)
          }
        }

      }
    }
  })

  private lazy val participantsController = inject[ParticipantsController]

  private var model: GroupParticipantInviteConfirmModel = _
  private var isSelf: Boolean = false

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    opts.map(_.isSelf).foreach{it =>
      isSelf =it
      setBackgroundResource(getChatMessageBg(isSelf))
    }

    model = GroupParticipantInviteConfirmModel.parseJson(msg.message.contentString)
    if (model != null && model.msgData != null) {
      participantsController.otherParticipant.currentValue.foreach { other =>
        val you = getResources.getString(R.string.content__system__you)
        if (SpUtils.getUserId(getContext).equals(msg.message.userId.str)) {
          ttvTitle.setText(String.format(getResources.getString(R.string.group_participant_invite_title), other.displayName.str))
          ttvContent.setText(String.format(getResources.getString(R.string.group_participant_invite_content), you, other.displayName.str, model.msgData.name))
        } else {
          ttvContent.setText(String.format(getResources.getString(R.string.group_participant_invite_content), other.displayName.str, you, model.msgData.name))
          ttvTitle.setText(String.format(getResources.getString(R.string.group_participant_invite_title), you))
        }
      }
      civConversationHead.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(model.msgData.asset, getContext), MessageContentUtils.getGroupDefaultAvatar(model.msgData.conversationId))
    } else {
      civConversationHead.setImageResource(R.drawable.icon_group_avatar_default)
    }

  }

  def isEmojiOnly(msg: MessageData, part: Option[MessageContent]) = false

}
