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
package com.waz.zclient.participants

import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.otr.Client
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.utils.UiStorage
import com.waz.zclient.{Injectable, Injector}

class DevicesDialogController(implicit inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {
  implicit val uiStorage = inject[UiStorage]

  lazy val accounts = inject[AccountsService]
  lazy val zms = inject[Signal[ZMessaging]]

  val onDevicesDialogAccept = EventStream[Unit]()

  val incomingClients = for {
    z <- zms
    client <- z.userPrefs(UserPreferences.SelfClient).signal
    clients <- client.clientId.fold(Signal.empty[Seq[Client]])(aid => z.otrClientsStorage.incomingClientsSignal(z.selfUserId, aid))
  } yield clients

  type DialogInfo = Either[Seq[Client], Boolean]

  val dialogInfo: Signal[Option[DialogInfo]] = for {
    clients <- incomingClients
  } yield if (clients.nonEmpty)
    Some(Left(clients))
  else
    None

  onDevicesDialogAccept.on(Threading.Background) { _ =>
    zms.head.flatMap(z => z.otrClientsService.updateUnknownToUnverified(z.selfUserId))(Threading.Background)
  }
}


