/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.handler
import com.waz.api.{Message, NetworkMode}
import com.waz.api.impl.ErrorResponse
import com.waz.content.MessagesStorage
import com.waz.model._
import com.waz.service.ErrorsService
import com.waz.service.assets.AssetService
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.otr.OtrClientsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult.Failure
import com.waz.sync.SyncServiceHandle
import com.waz.sync.otr.OtrSyncHandler

import scala.concurrent.Future

class MessagesSyncHandlerSpec extends AndroidFreeSpec {

  val service    = mock[MessagesService]
  val msgContent = mock[MessagesContentUpdater]
  val clients    = mock[OtrClientsService]
  val otrSync    = mock[OtrSyncHandler]
  val convs      = mock[ConversationsContentUpdater]
  val storage    = mock[MessagesStorage]
  val assetSync  = mock[AssetSyncHandler]
  val sync       = mock[SyncServiceHandle]
  val assets     = mock[AssetService]
  val errors     = mock[ErrorsService]

  def getHandler: MessagesSyncHandler = {
    new MessagesSyncHandler(account1Id, service, msgContent, clients, otrSync, convs, storage, assetSync, sync, assets, errors)
  }

  scenario("post invalid message should fail immediately") {

    val convId = ConvId()
    val messageId = MessageId()
    implicit val requestInfo: RequestInfo = RequestInfo(0, clock.instant(), Option(NetworkMode.WIFI))

    (storage.getMessage _).expects(messageId).returning(Future.successful(None))

    result(getHandler.postMessage(convId, messageId, RemoteInstant.Epoch)).isInstanceOf[Failure] should be(true)
  }

  scenario("post message with no internet should fail immediately") {

    val convId = ConvId()
    val messageId = MessageId()
    val message = MessageData(messageId, convId = convId)
    val connectionError = ErrorResponse(ErrorResponse.ConnectionErrorCode, "", "")
    implicit val requestInfo: RequestInfo = RequestInfo(0, clock.instant(), Option(NetworkMode.OFFLINE))

    (storage.getMessage _).expects(messageId).returning(Future.successful(Option(message)))
    (convs.convById _).expects(convId).returning(Future.successful(Option(ConversationData(convId))))

    (otrSync.postOtrMessage _).expects(convId, *, * ,*).returning(Future.successful(Left(connectionError)))

    (service.messageDeliveryFailed _).expects(convId, message, connectionError).returning(Future.successful(Some(message.copy(state = Message.Status.FAILED))))

    result(getHandler.postMessage(convId, messageId, RemoteInstant.Epoch)).isInstanceOf[Failure] should be(true)
  }
}
