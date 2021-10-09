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
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import com.waz.model.{MessageContent, MessageData, UserData, UserId}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{AliasSignal, MainActivityUtils, SpUtils, UiStorage, UserSignal}
import com.waz.zclient.{R, ViewHelper}

class ForbidOtherPartView (context: Context, attrs: AttributeSet, style: Int) extends android.widget.RelativeLayout(context, attrs, style) with ViewHelper with ClickableViewPart with EphemeralPartView {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  val view = inflate(R.layout.message_frobid_other_content, this, true)

  val tvFrobidUserName : TypefaceTextView = findById(R.id.ttv_forbid_user_name)

  private val userId = Signal[UserId]()
  implicit lazy val uiStorage = inject[UiStorage]
  private lazy val convController = inject[ConversationController]


  private lazy val user = Signal(zms, userId).flatMap {
    case (_, id) => UserSignal(id)
  }

  private lazy val alias = Signal(convController.currentConv, userId).flatMap {
    case (conversationData, userId) => AliasSignal(conversationData.id, userId)
  }

  override val tpe: MsgPart = MsgPart.ForbidOther
  private var isThousandsGroupMsg = false

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    isThousandsGroupMsg = convController.currentConv.currentValue.exists(MainActivityUtils.isOnlyThousandsGroupConversation)
    userId ! msg.message.userId

    val opt_name = msg.forbidName.getOrElse("")

    val isCreator = convController.currentConv.currentValue.exists(_.creator.str.equalsIgnoreCase(msg.forbidUser.getOrElse(UserId()).str))
    val isSelf = SpUtils.getUserId(context).equalsIgnoreCase(msg.forbidUser.getOrElse(UserId()).str)

    if(isSelf){
      if (isThousandsGroupMsg) {
        msg.message.userName.foreach(name => tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip4,name)))
      }else{
        (for {
          showName <- user.map(_.getShowName)
          aliasName <- alias.map(_.map(_.getAliasName).filter(_.nonEmpty))
        } yield (showName, aliasName))
          .onUi { parts =>
            tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip4,parts._2.getOrElse(parts._1)))
          }
      }
    }else{
      if(isCreator){
        if (isThousandsGroupMsg) {
          msg.message.userName.foreach(name => tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip3,opt_name,name)))
        }else{
          (for {
            showName <- user.map(_.getShowName)
            aliasName <- alias.map(_.map(_.getAliasName).filter(_.nonEmpty))
          } yield (showName, aliasName))
            .onUi { parts =>
              tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip3,opt_name,parts._2.getOrElse(parts._1)))
            }
        }
      }else{
        if (isThousandsGroupMsg) {
          msg.message.userName.foreach(name => tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip,opt_name,name)))
        }else{
          (for {
            showName <- user.map(_.getShowName)
            aliasName <- alias.map(_.map(_.getAliasName).filter(_.nonEmpty))
          } yield (showName, aliasName))
            .onUi { parts =>
              tvFrobidUserName.setText(getResources.getString(R.string.conversation_detail_settings_forbid_message_tip,opt_name,parts._2.getOrElse(parts._1)))
            }
        }
      }
    }



  }



}
