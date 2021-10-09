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

import com.waz.log.LogSE._
import com.waz.api.{Message, Verification}
import com.waz.api.Verification.UNKNOWN
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.{ConversationDataDao, ConversationType, UnreadCount}
import com.waz.model.ConversationData.ConversationType.Group
import com.waz.model.ConversationData.ConversationType.ThousandsGroup
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.Locales.currentLocaleOrdering
import com.waz.utils._
import com.waz.utils.events._

import scala.collection.GenMap
import scala.concurrent.Future

trait ConversationStorage extends CachedStorage[ConvId, ConversationData] {
  def updateLastMessage(convId: ConvId, msg: MessageData, isForce: Boolean = false): Future[Option[(ConversationData, ConversationData)]]
  def setUnknownVerification(convId: ConvId): Future[Option[(ConversationData, ConversationData)]]
  def getByRemoteIds(remoteId: Traversable[RConvId]): Future[Seq[ConvId]]
  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]]
  def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]]
  def getByConvIds(convIds: Set[ConvId]): Future[Map[ConvId, ConversationData]]

  def updateLocalId(oldId: ConvId, newId: ConvId): Future[Option[ConversationData]]
  def updateLocalIds(update: Map[ConvId, ConvId]): Future[Set[ConversationData]]

  def apply[A](f: GenMap[ConvId, ConversationData] => A): Future[A]

  def findGroupConversations(): Future[Seq[ConversationData]]
  def findByConversationType(convType: Set[ConversationType]) : Future[IndexedSeq[ConversationData]]

  def clearUnread(): Future[Unit]
}

class ConversationStorageImpl(storage: ZmsDatabase,userNoticeStorage:UserNoticeStorage,usersStorage: UsersStorage)
  extends CachedStorageImpl[ConvId, ConversationData](new UnlimitedLruCache(), storage)(ConversationDataDao, LogTag("ConversationStorage_Cached"))
    with ConversationStorage with DerivedLogTag {

  import EventContext.Implicits.global
  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationStorage")

  onAdded.on(dispatcher) { cs =>
    verbose(l"${cs.size} convs added")
    updateSearchKey(cs)
  }

  override def updateLastMessage(convId: ConvId, msg: MessageData, isForce: Boolean): Future[Option[(ConversationData, ConversationData)]] = {
    verbose(l"ConversationStorage_updateLastMessage convId: $convId, isForce:$isForce, MessageData isEmpty:${null == msg}")
    val msgType = if(null != msg) msg.msgType else Message.Type.UNKNOWN
    val contentType = if (null != msg) msg.contentType else Option.empty
    if (msgType != Message.Type.CONNECT_REQUEST && msgType != Message.Type.STARTED_USING_DEVICE && !ServerTextJsonParseUtils.isFilterContentType(contentType)) {
      update(
        convId, { c =>
          if (null == msg && isForce) {
            c.copy(
              lastMsgId = None,
              lastMsgType = Message.Type.TEXT,
              lastMsgContent = Seq.empty,
              lastMsgProtos = Seq.empty,
              lastMsgTime = RemoteInstant.Epoch,
              lastMsgUserId = UserId(),
              lastMsgMembers = Set.empty[UserId],
              lastMsgAction = MessageActions.Action_UnKnown,
              lastMsgContentType = None
            )
          } else {
            if (null != msg && (isForce || msg.time >= c.lastMsgTime || c.lastMsgId.exists(_ == msg.id)))
              c.copy(
                lastMsgId = Option(msg.id),
                lastMsgType = msg.msgType,
                lastMsgContent = msg.content,
                lastMsgProtos = msg.protos,
                lastMsgTime = msg.time,
                lastMsgUserId = msg.userId,
                lastMsgMembers = msg.members,
                lastMsgAction = msg.msgAction,
                lastMsgContentType = msg.contentType
              )
            else c
          }
        }
      )
    }else {
      Future.successful(None)
    }
  }

  def setUnknownVerification(convId: ConvId) = update(convId, { c => c.copy(verified = if (c.verified == Verification.UNVERIFIED) UNKNOWN else c.verified) })

  onUpdated.on(dispatcher) { cs =>
    verbose(l"${cs.size} convs updated")
    cs.foreach(convs=>{
      verbose(l"++++++++++++convs onUpdated conversationData:${convs._2}")
      userNoticeStorage.findByConv(convs._2.id.str).map {
        case Some(userNotice) =>
          verbose(l"convs userNoticeStorage exists")
          val name=if (convs._2.displayName.isEmpty) "" else convs._2.displayName.str
          val img = if (ConversationType.isGroupConv(convs._2.convType)) {
            if (convs._2.smallRAssetId == null) "" else convs._2.smallRAssetId.str
          }
          else if (ConversationType.isOneToOne(convs._2.convType)) {
            usersStorage.findUserForService(UserId(convs._2.id.str)).fold {
              if (convs._2.smallRAssetId == null) "" else convs._2.smallRAssetId.str
            } { u => u.rAssetId.getOrElse("") }
          }
          else ""
          verbose(l"convs userNoticeStorage name=${name},img=${img}")
          if(!name.equals(userNotice.name) || !img.equals(userNotice.img)){
            userNoticeStorage.update(userNotice.id, userNotice => userNotice.copy(name = name,img=img))
            verbose(l"convs userNoticeStorage update")
          }

        case None =>
          verbose(l"convs userNoticeStorage not exists")
      }
    })


    updateSearchKey(cs collect { case (p, c) if p.name != c.name || (p.convType == Group || p.convType == ThousandsGroup) != (c.convType == Group || c.convType == ThousandsGroup) || (c.name.nonEmpty && c.searchKey.isEmpty) => c })
  }

  private val init = for {
    convs   <- super.list()
    updater = (c: ConversationData) => c.copy(searchKey = c.savedOrFreshSearchKey)
    _       <- updateAll2(convs.map(_.id), updater)
  } yield {
    verbose(l"Caching ${convs.size} conversations")
  }

  private def updateSearchKey(cs: Seq[ConversationData]) =
    if (cs.isEmpty) Future successful Nil
    else updateAll2(cs.map(_.id), _.withFreshSearchKey)

  def apply[A](f: GenMap[ConvId, ConversationData] => A): Future[A] = init.flatMap(_ => contents.head).map(f)

  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]] = init.flatMap { _ =>
    findByRemoteId(remoteId).map(_.headOption)
  }

  override def getByRemoteIds(remoteIds: Traversable[RConvId]): Future[Seq[ConvId]] =
    getByRemoteIds2(remoteIds.toSet).map { convs =>
      remoteIds.flatMap(rId => convs.get(rId).map(_.id)).toSeq
    }

  override def getByRemoteIds2(remoteIds: Set[RConvId]): Future[Map[RConvId, ConversationData]] = init.flatMap { _ =>
    findByRemoteIds(remoteIds).map { convs =>
      remoteIds.flatMap(rId => convs.find(_.remoteId == rId)).map(c => c.remoteId -> c).toMap
    }
  }

  override def getByConvIds(convIds: Set[ConvId]): Future[Map[ConvId, ConversationData]] = init.flatMap { _ =>
    findByConvIds(convIds).map { convs =>
      convIds.flatMap(convId => convs.find(_.id == convId)).map(c => c.id -> c).toMap
    }
  }

  override def list: Future[Vector[ConversationData]] = init flatMap { _ => contents.head.map(_.values.toVector)  }

  def updateLocalId(oldId: ConvId, newId: ConvId) =
    updateLocalIds(Map(oldId -> newId)).map(_.headOption)

  def updateLocalIds(update: Map[ConvId, ConvId]) =
    for {
      _      <- removeAll(update.values)
      convs  <- getAll(update.keys)
      result <- insertAll(convs.flatten.map(c => c.copy(id = update(c.id))))
      _      <- removeAll(update.keys)
    } yield result

  override def findGroupConversations(): Future[Seq[ConversationData]] =
    //storage(ConversationDataDao.search(prefix, self, handleOnly, None)(_)).map(_.sortBy(_.displayName.str)(currentLocaleOrdering).take(limit))
    super.list()

  private def findByRemoteId(remoteId: RConvId) = find(c => c.remoteId == remoteId, ConversationDataDao.findByRemoteId(remoteId)(_), identity)
  private def findByRemoteIds(remoteIds: Set[RConvId]) = find(c => remoteIds.contains(c.remoteId), ConversationDataDao.findByRemoteIds(remoteIds)(_), identity)
  private def findByConvIds(convIds: Set[ConvId]) = find(c => convIds.contains(c.id), ConversationDataDao.findByConvIds(convIds)(_), identity)

  override def findByConversationType(convType: Set[ConversationType]) = find(c => convType.contains(c.convType), ConversationDataDao.findByConversationType(convType)(_), identity)

  override def clearUnread(): Future[Unit] = {
    Future.apply {
      storage(ConversationData.ConversationDataDao.clearUnread()(_))
      contents.head.foreach { maps =>
        val updated = Vector.newBuilder[(ConversationData, ConversationData)]

        maps.iterator.foreach { item =>
          val conversationData = item._2
          if ((conversationData.lastRead isBefore conversationData.lastMsgTime) || (conversationData.unreadCount.total >0)) {
            updated += (conversationData -> conversationData.copy(lastRead = conversationData.lastRead max conversationData.lastMsgTime, unreadCount = UnreadCount(0, 0, 0, 0, 0)))
          }
        }

        onUpdated ! updated.result()
      }
    }
  }
}

