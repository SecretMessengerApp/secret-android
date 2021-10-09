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

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent.Forbid
import com.waz.model._
import com.waz.service.messages.ForbidsService
import com.waz.sync.SyncResult
import com.waz.sync.otr.OtrSyncHandler

import scala.concurrent.Future

class ForbidsSyncHandler(service: ForbidsService, otrSync: OtrSyncHandler) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def postForbid(id: ConvId, forbidData: ForbidData): Future[SyncResult] =
    otrSync.postOtrMessage(id, GenericMessage(Uid(), Forbid(forbidData.message, forbidData.userName, forbidData.types, forbidData.action))).flatMap {
      case Right(time) =>
        service
          .updateLocalForbid(forbidData, time)
          .map(_ => SyncResult.Success)
      case Left(error) =>
        Future.successful(SyncResult(error))
    }
}
