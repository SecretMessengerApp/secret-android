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
package com.waz.model

import com.waz.model.UserInfo.Service
import com.waz.specs.AndroidFreeSpec

class UserDataSpec extends AndroidFreeSpec {

  val referenceInfo = UserInfo(
    UserId(),
    Some(Name("Atticus")),
    Some(4),
    Some(EmailAddress("atticus@wire.com")),
    Some(PhoneNumber("+0099223344556677")),
    None, // ignoring pictures for now
    Some(TrackingId("123454fsdf")),
    false,
    Some(Handle("atticus")),
    Some(false),
    Some(Service(
      IntegrationId("f0f83af0-c7d3-42b7-ab8b-7fc137ee7173"),
      ProviderId("148668b1-e393-419d-b4ab-bf021e300262"))
    ),
    Some(TeamId("7d49b132-03b2-4124-bb18-9388577a6bb2")),
    Some(RemoteInstant.ofEpochSec(10000)),
    Some(SSOId("foo", "bar")),
    Some(ManagedBy("wire")),
    Some(Seq(UserField("Department", "Sales & Marketing"), UserField("Favourite color", "Blue")))
  )

  feature("Update from user info") {

    scenario("Creation transfers all data") {

      // WHEN
      val data = UserData(referenceInfo, false)

      // THEN
      data.id.shouldEqual(referenceInfo.id)
      data.name.shouldEqual(referenceInfo.name.get)
      data.accent.shouldEqual(referenceInfo.accentId.get)
      data.email.shouldEqual(referenceInfo.email)
      data.phone.shouldEqual(referenceInfo.phone)
      data.trackingId.shouldEqual(referenceInfo.trackingId)
      data.providerId.shouldEqual(referenceInfo.service.map(_.provider))
      data.integrationId.shouldEqual(referenceInfo.service.map(_.id))
      data.handle.shouldEqual(referenceInfo.handle)
      data.deleted.shouldEqual(referenceInfo.deleted)
      data.teamId.shouldEqual(referenceInfo.teamId)
      data.expiresAt.shouldEqual(referenceInfo.expiresAt)
      data.managedBy.shouldEqual(referenceInfo.managedBy)
      data.fields.shouldEqual(referenceInfo.fields.get)
    }

    scenario("Updating with empty UserInfo preserves data") {

      // GIVEN
      val oldData = UserData(referenceInfo, false)
      val info = UserInfo(referenceInfo.id, referenceInfo.name)

      // WHEN
      val data = oldData.updated(info)

      // THEN
      data.id.shouldEqual(referenceInfo.id)
      data.name.shouldEqual(referenceInfo.name.get)
      data.accent.shouldEqual(referenceInfo.accentId.get)
      data.email.shouldEqual(referenceInfo.email)
      data.phone.shouldEqual(referenceInfo.phone)
      data.trackingId.shouldEqual(referenceInfo.trackingId)
      data.providerId.shouldEqual(referenceInfo.service.map(_.provider))
      data.integrationId.shouldEqual(referenceInfo.service.map(_.id))
      data.handle.shouldEqual(referenceInfo.handle)
      data.deleted.shouldEqual(referenceInfo.deleted)
      data.teamId.shouldEqual(referenceInfo.teamId)
      data.expiresAt.shouldEqual(referenceInfo.expiresAt)
      data.managedBy.shouldEqual(referenceInfo.managedBy)
      data.fields.shouldEqual(referenceInfo.fields.get)
    }
  }
}
