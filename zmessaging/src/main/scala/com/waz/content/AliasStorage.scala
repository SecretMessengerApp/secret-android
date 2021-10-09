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
package com.waz.content

import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AliasData.AliasDataDao
import com.waz.model._
import com.waz.threading.SerialDispatchQueue
import com.waz.utils._
import com.waz.utils.events._

import scala.concurrent.Future

trait AliasStorage extends CachedStorage[(ConvId, UserId), AliasData] {

  def getAlias(convId: ConvId): Future[Seq[AliasData]]

  def getAlias(convId: ConvId, userId: UserId): Future[Option[AliasData]]

  def listSignal(convId: ConvId): Signal[Seq[AliasData]]
}

class AliasStorageImpl(storage: ZmsDatabase)
  extends CachedStorageImpl[(ConvId, UserId), AliasData](new UnlimitedLruCache(), storage)(AliasDataDao, LogTag("AliasStorage_Cached"))
    with AliasStorage with DerivedLogTag {

  private implicit val dispatcher = new SerialDispatchQueue(name = "AliasStorage")

  override def getAlias(convId: ConvId): Future[Seq[AliasData]] = storage(AliasDataDao.getAlias(convId)(_))

  override def getAlias(convId: ConvId, userId: UserId): Future[Option[AliasData]] = get(convId, userId)

  override def listSignal(convId: ConvId): Signal[Seq[AliasData]] = {
    new RefreshingSignal(getAlias(convId).lift, onChanged.map(_.filter(_.convId ==convId)))
  }
}
