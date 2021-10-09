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
package com.waz.service

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model
import com.waz.model.GenericContent._
import com.waz.model._
import com.waz.service.conversation.{ConversationOrderEventsService, ConversationsContentUpdaterImpl}
import com.waz.service.messages.{ForbidsService, MessagesContentUpdater, ReactionsService, ReceiptService}

import scala.concurrent.Future.traverse

class GenericMessageService(selfUserId: UserId,
                            messages:   MessagesContentUpdater,
                            convs:      ConversationsContentUpdaterImpl,
                            convEvents: ConversationOrderEventsService,
                            reactions:  ReactionsService,
                            forbids:    ForbidsService,
                            receipts:   ReceiptService,
                            users:      UserService) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  val eventProcessingStage = EventScheduler.Stage[GenericMessageEvent] { (_, events) =>
    verbose(l"got events: $events")

    def lastForConv(items: Seq[(RConvId, RemoteInstant)]) = items.groupBy(_._1).map { case (conv, times) => times.maxBy(_._2.toEpochMilli) }

    val incomingReactions = events collect {
      case GenericMessageEvent(_, time, from, GenericMessage(_, Reaction(msg, action)), name, asset) => Liking(msg, from, time, action)
    }

    val incomingForbids = events collect {
      case GenericMessageEvent(_, time, from, GenericMessage(_, Forbid(msg, optName, types, action)), name, asset) => ForbidData(msg, from, optName, time, types, action)
    }

    val lastRead = lastForConv(events collect {
      case GenericMessageEvent(_, _, _, GenericMessage(_, LastRead(conv, time)), name, asset) => (conv, time)
    })

    val cleared = lastForConv(events collect {
      case GenericMessageEvent(_, _, userId, GenericMessage(_, Cleared(conv, time)), name, asset) if userId == selfUserId => (conv, time)
    })

    val deleted = events collect {
      case GenericMessageEvent(_, _, _, GenericMessage(_, MsgDeleted(_, msg)), name, asset) => msg
    }

    val confirmed = events.collect {
      case GenericMessageEvent(_, _, _, GenericMessage(_, DeliveryReceipt(msgs)), name, asset) => msgs
    }.flatten

    val availabilities = (events collect {
      case GenericMessageEvent(_, _, userId, GenericMessage(_, AvailabilityStatus(available)), name, asset) => userId -> available
    }).toMap

    val read = events.collect {
      case GenericMessageEvent(_, time, from, GenericMessage(_, Proto.ReadReceipt(msgs)), name, asset) => msgs.map { msg =>
        model.ReadReceipt(msg, from, time)
      }
    }.flatten

    for {
      _ <- messages.deleteOnUserRequest(deleted)
      _ <- traverse(lastRead) { case (remoteId, timestamp) =>
        convs.processConvWithRemoteId(remoteId, retryAsync = true) { conv => convs.updateConversationLastRead(conv.id, timestamp) }
      }
      _ <- reactions.processReactions(incomingReactions.filter(_.action != Liking.Action.MsgRead))
      _ <- forbids.processForbids(incomingForbids.filter(_.types == ForbidData.Types.Forbid))
      _ <- messages.updateMessageActions(incomingForbids.filter(_.types == ForbidData.Types.Forbid))
      _ <- messages.updateMessageReadStates1(incomingForbids.filter(data => (data.action != ForbidData.Types.Forbid && data.userId == selfUserId)))
      _ <- messages.updateMessageReadStates(incomingReactions.filter(data => (data.action == Liking.Action.MsgRead && data.user == selfUserId)))
      _ <- traverse(cleared) { case (remoteId, timestamp) =>
        convs.processConvWithRemoteId(remoteId, retryAsync = true) { conv => convs.updateConversationCleared(conv.id, timestamp) }
      }
      _ <- receipts.processDeliveryReceipts(confirmed)
      _ <- receipts.processReadReceipts(read)
      _ <- users.storeAvailabilities(availabilities)
    } yield ()
  }
}
