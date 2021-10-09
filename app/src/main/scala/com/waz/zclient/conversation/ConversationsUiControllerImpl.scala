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
package com.waz.zclient.conversation

import android.content.Context
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.conversation._
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.Future

class ConversationsUiControllerImpl()(implicit inj: Injector, cxt: Context, eventContext: EventContext)
  extends Injectable
    with ConversationsUiController
    with DerivedLogTag {

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val selfId = inject[Signal[UserId]]
  private lazy val convController = inject[ConversationController]

  override def onSelfJoinConversation(accountId: UserId, conversationData: ConversationData): Future[Unit] = {
    verbose(l"onSelfJoinConversation end accountId:$accountId, conversationData:$conversationData")
    if (ConversationType.isGroupConv(conversationData.convType)) {
      convController.needJumpNewConvId.currentValue.foreach {
        convId =>
          if (StringUtils.isNotBlank(convId) && convId.equalsIgnoreCase(conversationData.remoteId.str)) {
            convController.selectConv(conversationData.id, ConversationChangeRequester.START_CONVERSATION)
            convController.needJumpNewConvId ! ""
          }
      }
      Future.successful({})
    } else {
      Future.successful({})
    }
  }
}

object ConversationsUiController {

}
