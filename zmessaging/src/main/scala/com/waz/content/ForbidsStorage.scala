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

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ForbidData.{Action, ForbidDao, Types}
import com.waz.model._
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.{AggregatingSignal, EventContext, RefreshingSignal, Signal}
import com.waz.utils._

import scala.collection.{breakOut, mutable}
import scala.concurrent.duration._
import scala.concurrent.Future

trait ForbidsStorage extends CachedStorage[(MessageId, ForbidData.Types), ForbidData] {
  def loadAll(msgs: Seq[MessageId]): Future[Vector[Forbids]]
  def addOrUpdate(forbidData: ForbidData): Future[Forbids]
  def getForbids(msg: MessageId): Future[Forbids]
  def getForbidForTypes(msg: MessageId, types: ForbidData.Types = ForbidData.Types.Forbid): Future[Option[(UserId, Option[String], ForbidData.Action)]]
  def isForbidState(msg: MessageId, types: ForbidData.Types = ForbidData.Types.Forbid): Future[Boolean]
  def forbids(msg:MessageId): Signal[Forbids]
}

class ForbidsStorageImpl(context: Context, storage: Database)
  extends CachedStorageImpl[(MessageId, ForbidData.Types), ForbidData](new TrimmingLruCache(context, Fixed(MessagesStorage.cacheSize)), storage)(ForbidDao, LogTag("ForbidStorage"))
    with ForbidsStorage
    with DerivedLogTag {

  import ForbidsStorageImpl._
  import EventContext.Implicits.global

  private implicit val dispatcher = new SerialDispatchQueue()

  private val forbidsCache = new TrimmingLruCache[MessageId, Map[ForbidData.Types, (UserId, Option[String], ForbidData.Action)]](context, Fixed(1024))
  private val maxTime = returning(new AggregatingSignal[RemoteInstant, RemoteInstant](onChanged.map(_.maxBy(_.timestamp).timestamp), storage.read(ForbidDao.findMaxTime(_)), _ max _))(_.disableAutowiring())

  onChanged.on(dispatcher) { forbids =>
    verbose(l"onChanged.on(dispatcher) forbids:$forbids")
    forbids.groupBy(_.message) foreach { case (msg, ls) =>
      Option(forbidsCache.get(msg)) foreach { current =>
        val (toForbid, toUnknown) = ls.partition(_.types == Types.Forbid)
        val (toAdd, toRemove) = toForbid.partition(_.action == Action.Forbid)
        forbidsCache.put(msg, current -- toRemove.map(_.types) ++ toAdd.map(l => l.types -> (l.userId, l.userName, l.action)))
      }
    }
  }

  private def updateCache(msg: MessageId, forbidDatas: Iterable[ForbidData]) = {
    verbose(l"updateCache msg:$msg, forbidDatas:${Option(forbidDatas)}")
    val forbidTypeUser: Map[ForbidData.Types, (UserId, Option[String], ForbidData.Action)] = forbidDatas.collect { case l if l.types == Types.Forbid && l.action == Action.Forbid => l.types -> (l.userId, l.userName, l.action) }(breakOut)
    Forbids(msg, forbidTypeUser)
  }

  override def insertAll(vs: Traversable[ForbidData]): Future[Set[ForbidData]] = {
    verbose(l"insertAll:")
    val values = new mutable.HashMap[(MessageId, ForbidData.Types), ForbidData]
    vs foreach { v =>
      if (values.get(v.id).forall(_.timestamp.isBefore(v.timestamp)))
        values(v.id) = v
    }
    updateOrCreateAll2(values.keys, { (id, v) => v.fold(values(id)) { _ max values(id) }})
  }

  override def getForbids(msg: MessageId): Future[Forbids] = Future {
    verbose(l"getForbids: msg:$msg")
    Option(forbidsCache.get(msg))
  } flatMap {
    case Some(typeUser) => Future.successful(Forbids(msg, typeUser))
    case None => find(_.message == msg, ForbidDao.findForMessage(msg)(_), identity) map { updateCache(msg, _) }
  }

  override def forbids(msg: MessageId): Signal[Forbids] =
    new RefreshingSignal[Forbids](
      CancellableFuture.lift(getForbids(msg)),
      onChanged.map(_.filter(_.message == msg))
    )

  override def addOrUpdate(forbidData: ForbidData): Future[Forbids] = {
    verbose(l"addOrUpdate: $forbidData")
    if (forbidData.timestamp.isEpoch) updateOrCreate(forbidData.id, l => l.copy(userId = forbidData.userId, timestamp = maxTime.currentValue.getOrElse(l.timestamp) + 1.milli, action = forbidData.action), forbidData) // local update
    else updateOrCreate(forbidData.id, _ max forbidData, forbidData)
  }.flatMap(_ => getForbids(forbidData.message))

  override def loadAll(msgs: Seq[MessageId]): Future[Vector[Forbids]] = Future {
    verbose(l"loadAll:")
    msgs.map(m => m -> Option(forbidsCache.get(m))).toMap
  } flatMap { cached =>
    val toLoad: Set[MessageId] = cached.collect { case (id, None) => id } (breakOut)
    find(l => toLoad(l.message), ForbidDao.findForMessages(toLoad)(_), identity) map { forbidDatas =>
      val typeUserMap = cached.mapValues(_.getOrElse(Map.empty)) ++ forbidDatas.groupBy(_.message).map { case (msg, ls) => msg -> forbidTypeUser(ls) }
      msgs.map { msg =>
        val users = typeUserMap(msg)
        if (forbidsCache.get(msg) == null) forbidsCache.put(msg, users)
        Forbids(msg, users)
      } (breakOut)
    }
  }

  override def getForbidForTypes(msg: MessageId, types: ForbidData.Types = ForbidData.Types.Forbid):Future[Option[(UserId, Option[String], ForbidData.Action)]] =  {
    getForbids(msg).flatMap {
      forbids =>
        val forbidTypeUser = forbids.forbidTypeUser
        val userAction = if (!forbidTypeUser.isEmpty && forbidTypeUser.contains(types)) forbidTypeUser.get(types) else None
        Future.successful(userAction)
    }
  }

  override def isForbidState(msg: MessageId, types: Types = ForbidData.Types.Forbid): Future[Boolean] = {
    getForbidForTypes(msg, types).flatMap {
      case Some(userAction) if userAction._3 == ForbidData.Action.Forbid => Future.successful(true)
      case _ => Future.successful(false)
    }
  }

}

object ForbidsStorageImpl {

  private def forbidTypeUser(forbidDatas: Seq[ForbidData]): Map[ForbidData.Types, (UserId, Option[String], ForbidData.Action)] =
    forbidDatas.collect { case l if l.types == Types.Forbid && l.action == Action.Forbid => l.types -> (l.userId, l.userName, l.action) }(breakOut)
}

case class Forbids(message: MessageId, forbidTypeUser: Map[ForbidData.Types, (UserId, Option[String], ForbidData.Action)])

object Forbids {
  def Empty(message: MessageId) = Forbids(message, Map())
}
