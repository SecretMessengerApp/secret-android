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

import com.waz.log.LogSE._
import com.waz.content.ConversationStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ForbidData.Types
import com.waz.model.GenericContent.{Forbid, LastRead, Reaction}
import com.waz.model._
import com.waz.service.MetaDataService
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.{Failure, Success}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.utils.RichWireInstant

import scala.concurrent.Future
import scala.concurrent.Future.successful

class LastReadSyncHandler(selfUserId: UserId,
                          convs: ConversationStorage,
                          metadata: MetaDataService,
                          convSync: ConversationsSyncHandler,
                          msgsSync: MessagesSyncHandler,
                          otrSync: OtrSyncHandler) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def postLastRead(convId: ConvId, time: RemoteInstant): Future[SyncResult] = {
    verbose(l"postLastRead($convId, $time)")

    convs.get(convId).flatMap {
      case Some(conv) if conv.lastRead.isAfter(time) => // no need to send this msg as lastRead was already advanced
        Future.successful(Success)
      case Some(conv) =>
        val msg = GenericMessage(Uid(), LastRead(conv.remoteId, time))
        otrSync
          .postOtrMessageIgnoreMissing(ConvId(selfUserId.str), msg)
          .map(SyncResult(_))
      case None =>
        Future.successful(Failure(s"No conversation found for id: $convId"))
    }
  }

  def postMsgRead(convId: ConvId, msgId: MessageId): Future[SyncResult] = {
    verbose(l"postMsgRead($convId, $msgId)")
    convs.get(convId).flatMap {
      case Some(conv) =>
//        val msg = GenericMessage(Uid(), Proto.MsgRead(conv.remoteId, msgId))
        val msg = GenericMessage(Uid(), Reaction(msgId, Liking.Action.MsgRead))
        otrSync
          .postOtrMessageIgnoreMissing(ConvId(selfUserId.str), msg)
          .map(SyncResult(_))
      case None =>
        successful(Failure("postMsgRead not found"))
    }
  }
}
