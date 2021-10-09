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
package com.waz.service.messages

import com.waz.content.{Forbids, ForbidsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.service.UserService
import com.waz.sync.SyncServiceHandle
import com.waz.threading.Threading
import com.waz.utils._

import scala.concurrent.Future

class ForbidsService(storage: ForbidsStorage,
                     messages: MessagesContentUpdater,
                     sync: SyncServiceHandle,
                     users: UserService,
                     selfUserId: UserId)
    extends DerivedLogTag {

  import Threading.Implicits.Background

  def forbid(conv: ConvId, msg: MessageId): Future[Forbids] =
    addForbid(conv, msg, ForbidData.Types.Forbid, ForbidData.Action.Forbid)

  def unForbid(conv: ConvId, msg: MessageId): Future[Forbids] =
    addForbid(conv, msg, ForbidData.Types.Forbid, ForbidData.Action.UnForbid)

  private def addForbid(conv: ConvId,
                        msg: MessageId,
                        types: ForbidData.Types,
                        action: ForbidData.Action): Future[Forbids] = {
    verbose(l"addForbid: $conv, $msg, $action")
    val forbidData = ForbidData(msg, selfUserId, None, RemoteInstant.Epoch, types, action) // EPOCH is used to signal "local" in-band
    for {
      displayName <- users.getSelfUser.map(_.map(_.getDisplayNameForJava))
      forbid = forbidData.copy(userName = displayName)
      _ = verbose(l"addForbid forbid:$forbid")
      forbids <- storage.addOrUpdate(forbid)
      _       <- sync.postForbid(conv, forbid)
      _       <- messages.updateMessageAction(forbid.message, forbid.action.serial)
    } yield forbids
  }

  def updateLocalForbid(local: ForbidData, backendTime: RemoteInstant) =
    storage.update(local.id, { stored =>
      if (stored.timestamp <= local.timestamp) stored.copy(userId = local.userId, userName = local.userName, action = local.action, timestamp = backendTime)
      else stored
    })

  def processForbids(forbidDatas: Seq[ForbidData]): Future[Seq[Forbids]] = Future.traverse(forbidDatas) {
    verbose(l"processForbids forbidDatas:$forbidDatas")
    storage.addOrUpdate
  } // FIXME: use batching
}
