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

package com.waz.zclient

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.Uid
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal

import scala.concurrent.Future

class ErrorsController(implicit inj: Injector) extends Injectable with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background

  private val zms = inject[Signal[ZMessaging]]

  def dismissSyncError(errorId: Uid): Future[Unit] =
    zms.map(_.errors).head.flatMap(_.dismissError(errorId))

}
