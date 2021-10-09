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
package com.waz.service.push

import java.util.UUID

import android.content.Context
import com.waz.content.Database
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.push.PushNotificationEventsStorage.{EventHandler, EventIndex, PlainWriter}
import com.waz.sync.client.PushNotificationEncoded
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.events.EventContext
import com.waz.utils.{CachedStorage, CachedStorageImpl, TrimmingLruCache}
import org.json.JSONObject

import scala.collection.Iterable
import scala.concurrent.Future
import scala.util.Try


object PushNotificationEventsStorage {
  type PlainWriter = Array[Byte] => Future[Unit]
  type EventIndex = Uid

  type EventHandler = () => Future[Unit]
}

trait PushNotificationEventsStorage extends CachedStorage[EventIndex, PushNotificationEvent] {
  def setAsDecrypted(index: EventIndex): Future[Unit]

  def setAsEncrypted(index: EventIndex): Future[Unit]

  def writeClosure(index: EventIndex): PlainWriter

  def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit]

  def saveAll(pushNotifications: Seq[PushNotificationEncoded],needFilterTyping : Boolean = false): Future[Set[PushNotificationEvent]]

  def flush(): Future[Unit]

  def encryptedEvents: Future[Seq[PushNotificationEvent]]

  def removeRows(rows: Iterable[Uid]): Future[Unit]

  def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit]

  def getDecryptedRows(): Future[IndexedSeq[PushNotificationEvent]]
}

class PushNotificationEventsStorageImpl(context: Context, storage: Database, clientId: ClientId)
  extends CachedStorageImpl[EventIndex, PushNotificationEvent](new TrimmingLruCache(context, Fixed(1024 * 1024)), storage)(PushNotificationEventsDao, LogTag("PushNotificationEvents_Cached"))
    with PushNotificationEventsStorage
    with DerivedLogTag {

  private implicit val dispatcher = new SerialDispatchQueue(name = "PushNotificationEventsStorage")

  override def setAsDecrypted(index: EventIndex): Future[Unit] = {
    update(index, u => u.copy(decrypted = true)).map {
      case None =>
        ()
      //throw new IllegalStateException(s"Failed to set event with index $index as decrypted")
      case _ => ()
    }
  }


  override def setAsEncrypted(index: EventIndex): Future[Unit] = {
    update(index, u => u.copy(decrypted = false)).map {
      case None =>
        ()
      //throw new IllegalStateException(s"Failed to set event with index $index as decrypted")
      case _ => ()
    }
  }

  override def writeClosure(index: EventIndex): PlainWriter =
    (plain: Array[Byte]) => update(index, _.copy(decrypted = true, plain = Some(plain))).map(_ => Unit)

  override def writeError(index: EventIndex, error: OtrErrorEvent): Future[Unit] =
    update(index, _.copy(decrypted = true, event = MessageEvent.MessageEventEncoder(error), plain = None))
      .map(_ => Unit)

  override def saveAll(pushNotifications: Seq[PushNotificationEncoded],needFilterTyping : Boolean = false): Future[Set[PushNotificationEvent]] = {
    import com.waz.utils._
    def isOtrEventForUs(obj: JSONObject): Boolean = {
      returning(!obj.getString("type").startsWith("conversation.otr") || obj.getJSONObject("data").getString("recipient").equals(clientId.str)) { ret =>
        if (!ret) {
          verbose(l"Skipping otr event not intended for us: $obj")
        }
      }
    }

    def isTypingEvent(obj: JSONObject) = needFilterTyping && obj.optString("type").equalsIgnoreCase("conversation.typing")

    val eventsToSave = pushNotifications
      .flatMap { pn =>
        pn.events.toVector.filter(isOtrEventForUs).filterNot(isTypingEvent).map { event =>
          (pn.id, event, pn.transient)
        }
      }

    insertAll(eventsToSave.map { case (id, event, transient) =>
      val index = Try(UUID.fromString(id.str).timestamp()).toOption.fold {
        System.nanoTime()
      } { uid =>
        uid
      }
      PushNotificationEvent(id, index, event = event, transient = transient)
    })

    //    storage.withTransaction { implicit db =>
    ////      val curIndex = PushNotificationEventsDao.maxIndex()
    ////      val nextIndex = if (curIndex == -1) 0 else curIndex+1
    //      insertAll(eventsToSave.map { case (id, event, transient) =>
    //        PushNotificationEvent(id, System.currentTimeMillis(), event = event, transient = transient)
    //      })
    //
    //    }.future.map(_ => ())

  }

  def flush(): Future[Unit] = {
    storage.flushWALToDatabase()
  }

  def encryptedEvents: Future[Seq[PushNotificationEvent]] = storage.read { implicit db =>
    PushNotificationEventsDao.listEncrypted()
  }

  //limit amount of decrypted events we read to avoid overwhelming older phones
  def getDecryptedRows(): Future[IndexedSeq[PushNotificationEvent]] = storage.read { implicit db =>
    PushNotificationEventsDao.listDecrypted(100)
  }

  def removeRows(rows: Iterable[Uid]): Future[Unit] = removeAll(rows)

  //This method is called once on app start, so invoke the handler in case there are any events to be processed
  //This is safe as the handler only allows one invocation at a time.
  override def registerEventHandler(handler: EventHandler)(implicit ec: EventContext): Future[Unit] = {
    onAdded(_ => handler())
    processStoredEvents(handler)
  }

  private def processStoredEvents(processor: () => Future[Unit]): Future[Unit] =
    list().map { nots =>
      if (nots.nonEmpty) {
        processor()
      }
    }
}
