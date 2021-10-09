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
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.utils.ScreenUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.IntegrationsService
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.Future

class ConnectRequestPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  import Threading.Implicits.Ui

  override val tpe: MsgPart = MsgPart.ConnectRequest

  lazy val chathead: ChatHeadViewNew = findById(R.id.cv__row_conversation__connect_request__chat_head)
  lazy val label: TextView = findById(R.id.ttv__row_conversation__connect_request__label)
  lazy val userDetails: UserDetailsView = findById(R.id.udv__row_conversation__connect_request__user_details)

  private lazy val browser = inject[BrowserController]
  private lazy val users = inject[UsersController]
  private lazy val integrations = inject[Signal[IntegrationsService]]
  private lazy val convController = inject[ConversationController]

  //  val members = message.map(m => m.members + m.userId)
  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    verbose(l"override def set(msg:")
    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData != null) {
        val convType = conversationData.convType
        if (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) {
          showGroup(conversationData)
        } else {
          showOneToOne()
        }
      }else{
        verbose(l"override def set(msg:null == conversationData")
      }
    }
    val chatHeadParams = chathead.getLayoutParams
    val screenWidth = ScreenUtils.getScreenWidth(getContext)
    val headWidth = screenWidth * 264 / 376
    chatHeadParams.width = headWidth
    chatHeadParams.height = headWidth
    chathead.setLayoutParams(chatHeadParams)
  }

  def showGroup(conversationData: ConversationData) = {
    verbose(l"override def set(msg:showGroup")
    userDetails.setVisibility(View.GONE)
    label.setVisibility(View.INVISIBLE)
    val defaultRes = MessageContentUtils.getGroupDefaultAvatar(conversationData.id)
    if (conversationData.smallRAssetId != null) {
      chathead.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(conversationData.smallRAssetId.str, getContext), defaultRes)
    } else {
      chathead.setImageResource(defaultRes)
    }
  }

  def showOneToOne() = {
    verbose(l"override def set(msg:showOneToOne")
    val user = for {
      self <- inject[Signal[UserId]]
      members <- message.map(m => Set(m.userId) ++ Set(m.recipient).flatten)
      Some(user) <- members.find(_ != self).fold {
        Signal.const(Option.empty[UserData])
      } { uId =>
        users.user(uId).map(Some(_))
      }
    } yield user


    val integration = for {
      usr <- user
      intService <- integrations
      integration <- Signal.future((usr.integrationId, usr.providerId) match {
        case (Some(i), Some(p)) => intService.getIntegration(p, i).map {
          case Right(integrationData) => Some(integrationData)
          case Left(_) => None
        }
        case _ => Future.successful(None)
      })
    } yield integration

    Signal(integration, user).onUi {
      case (Some(i), _) => chathead.setIntegration(i)
      case (_, usr) => chathead.setUserData(usr)
    }

    user.map(Some(_)).on(Threading.Ui){
      case Some(userData) =>
        userDetails.setVisibility(View.VISIBLE)
        userDetails.setUserData(userData)
      case _ =>
    }

    user.map(u => (u.isAutoConnect, u.isWireBot)).on(Threading.Ui) {
      case (true, _) =>
        label.setVisibility(View.VISIBLE)
        label.setText(R.string.content__message__connect_request__auto_connect__footer)
        TextViewUtils.linkifyText(label, getStyledColor(R.attr.wirePrimaryTextColor), true, true, new Runnable() {
          override def run() = browser.openUrl(AndroidURIUtil parse getString(R.string.url__help))
        })
      case (false, false) =>
        label.setVisibility(View.INVISIBLE)
        label.setTextColor(getStyledColor(R.attr.wirePrimaryTextColor))
        label.setText(R.string.content__message__connect_request__footer)
      case (_, true) =>
        label.setVisibility(View.VISIBLE)
        label.setTextColor(getColor(R.color.accent_red))
        label.setText(R.string.generic_service_warning)
    }
  }
}
