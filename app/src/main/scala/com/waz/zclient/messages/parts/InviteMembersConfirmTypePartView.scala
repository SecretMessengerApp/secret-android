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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.jsy.common.acts.GroupInviteConfirmActivity
import com.jsy.common.model.ConversationInviteMemberConfirmModel
import com.jsy.common.utils.MessageUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageContent, MessageData, Name}
import com.waz.service.messages.MessageAndLikes
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageView, MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{R, ViewHelper}

/**
  * Created by eclipse on 2019/1/22.
  */
class InviteMembersConfirmTypePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.InviteMembersType

  private lazy val convController = inject[ConversationController]

  val MSG_DATA = MessageUtils.KEY_TEXTJSON_MSGDATA

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_invite_members)

  val users = inject[UsersController]
  var dataModel: ConversationInviteMemberConfirmModel = _
  var hasRefresh: Boolean = false

  val nameView: TextView = findById(R.id.ttv__username)
  val memberView: TextView = findById(R.id.ttv_invite_member)
  val confirmView: TextView = findById(R.id.ttv_invite_confirm)


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    var contentString = msg.message.name.getOrElse(Name("")).str
    if (StringUtils.isBlank(contentString) || !contentString.contains(MSG_DATA)) {
      contentString = msg.message.content.headOption.fold("")(_.content)
    }

    if (!StringUtils.isBlank(contentString)) dataModel = new Gson().fromJson(contentString, classOf[ConversationInviteMemberConfirmModel])

    if (dataModel != null) {
      nameView.setText("\"" + dataModel.msgData.name + "\"")
      val str = getContext.getResources.getString(R.string.conversation_setting_invite_number, String.valueOf(dataModel.msgData.nums))
      memberView.setText(str)

      if (dataModel.msgData.`type` == 2 || hasRefresh) {
        confirmView.setEnabled(false)
        confirmView.setTextColor(Color.GRAY)
        confirmView.setText(getContext.getResources.getString(R.string.conversation_setting_invite_confirm))
      } else {
        confirmView.setEnabled(true)
        confirmView.setTextColor(Color.parseColor("#FF009DFF"))
        confirmView.setText(getContext.getResources.getString(R.string.conversation_setting_invite_to_confirm))
        confirmView.setOnClickListener(new View.OnClickListener {
          override def onClick(view: View): Unit = {
            convController.currentConv.currentValue.foreach { conversationData =>
              GroupInviteConfirmActivity.start(getContext, dataModel, conversationData.remoteId.str, msg.message.id.str)
            }
          }
        })
      }
    }

  }


  def refreshInviteStatus() = {
    hasRefresh = true
    confirmView.setEnabled(false)
    confirmView.setTextColor(Color.GRAY)
    confirmView.setText(getContext.getResources.getString(R.string.conversation_setting_invite_confirm))
  }

}
