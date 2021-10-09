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

import java.util.concurrent.ConcurrentHashMap

import android.content.Context
import com.waz.api.impl.ErrorResponse
import com.waz.api.{Message, MessageFilter}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.UnreadCount
import com.waz.model.MessageData.{MessageDataDao, MessageEntry}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.Timeouts
import com.waz.service.messages.MessageAndLikes
import com.waz.service.tracking.TrackingService
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils._
import com.waz.utils.events.{EventStream, Signal, SourceStream}

import scala.collection.immutable.Set
import scala.collection._
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait MessagesStorage extends CachedStorage[MessageId, MessageData] {

  def onMessageSent:   SourceStream[MessageData]
  def onMessageFailed: SourceStream[(MessageData, ErrorResponse)]

  def onMessagesDeletedInConversation: EventStream[Set[ConvId]]

  def delete(msg: MessageData): Future[Unit]
  def deleteAll(conv: ConvId):  Future[Unit]

  def addMessage(msg: MessageData): Future[MessageData]

  def getMessage(id: MessageId):    Future[Option[MessageData]]
  def getMessages(ids: MessageId*): Future[Seq[Option[MessageData]]]

  def msgsIndex(conv: ConvId): Future[ConvMessagesIndex]
  def msgsFilteredIndex(conv: ConvId, messageFilter: MessageFilter): Future[ConvMessagesIndex]

  def findLocalFrom(conv: ConvId, time: RemoteInstant): Future[IndexedSeq[MessageData]]

  //System message events no longer have IDs, so we need to search by type, timestamp and sender
  def hasSystemMessage(conv: ConvId, serverTime: RemoteInstant, tpe: Message.Type, sender: UserId): Future[Boolean]

  def getLastMessage(conv: ConvId): Future[Option[MessageData]]
  def getLastSentMessage(conv: ConvId): Future[Option[MessageData]]
  def lastLocalMessage(conv: ConvId, tpe: Message.Type): Future[Option[MessageData]]
  def countLaterThan(conv: ConvId, time: RemoteInstant): Future[Long]

  def findMessagesByType(conv: ConvId, msgType: Message.Type): Future[IndexedSeq[MessageData]]
  def queryMessagesByType(conv: ConvId, msgType: Message.Type): Future[Seq[MessageData]]
  def findMessagesFrom(conv: ConvId, time: RemoteInstant): Future[IndexedSeq[MessageData]]
  def findMessagesBetween(conv: ConvId, from: RemoteInstant, to: RemoteInstant): Future[IndexedSeq[MessageData]]

  def clear(convId: ConvId, clearTime: RemoteInstant): Future[Unit]

  def lastMessageFromSelfAndFromOther(conv: ConvId): Signal[(Option[MessageData], Option[MessageData])]

  def findQuotesOf(msgId: MessageId): Future[Seq[MessageData]]
  def countUnread(conv: ConvId, lastReadTime: RemoteInstant): Future[UnreadCount]

  def forceUpdateLastMessage(convId: ConvId): Future[Option[(ConversationData, ConversationData)]]

  def deleteReportNoticeMessage(convId: ConvId): Future[Unit]
}

class MessagesStorageImpl(context:     Context,
                          storage:     ZmsDatabase,
                          selfUserId:  UserId,
                          convs:       ConversationStorage,
                          users:       UsersStorage,
                          msgAndLikes: => MessageAndLikesStorage,
                          timeouts:    Timeouts,
                          tracking:    TrackingService)
  extends CachedStorageImpl[MessageId, MessageData](
    new TrimmingLruCache[MessageId, Option[MessageData]](context, Fixed(MessagesStorage.cacheSize)),
    storage
  )(MessageDataDao, LogTag("MessagesStorage_Cached")) with MessagesStorage with DerivedLogTag {

  import com.waz.utils.events.EventContext.Implicits.global

  private implicit val dispatcher = new SerialDispatchQueue(name = "MessagesStorage")

  //For tracking on UI
  val onMessageSent = EventStream[MessageData]()
  val onMessageFailed = EventStream[(MessageData, ErrorResponse)]()

  val onMessagesDeletedInConversation = EventStream[Set[ConvId]]()

  private val indexes = new ConcurrentHashMap[ConvId, ConvMessagesIndex]
  private val filteredIndexes = new MultiKeyLruCache[ConvId, MessageFilter, ConvMessagesIndex](MessagesStorage.filteredMessagesCacheSize)

  def msgsIndex(conv: ConvId): Future[ConvMessagesIndex] =
    Option(indexes.get(conv)).fold {
      Future(returning(new ConvMessagesIndex(conv, this, selfUserId, users, convs, msgAndLikes, storage, tracking))(indexes.put(conv, _)))
    } {
      Future.successful
    }

  def msgsFilteredIndex(conv: ConvId, messageFilter: MessageFilter): Future[ConvMessagesIndex] =
    filteredIndexes.get(conv, messageFilter).fold {
      Future(returning(new ConvMessagesIndex(conv, this, selfUserId, users, convs, msgAndLikes, storage, tracking, filter = Some(messageFilter)))(filteredIndexes.put(conv, messageFilter, _)))
    } {
      Future.successful
    }

  def msgsFilteredIndex(conv: ConvId): Seq[ConvMessagesIndex] = filteredIndexes.get(conv).values.toSeq

  onAdded { added =>
    Future.traverse(added.groupBy(_.convId)) { case (convId, msgs) =>
      verbose(l"MessagesStorage_onAdded convId: $convId, msgs:${msgs.size}")
      msgsFilteredIndex(convId).foreach(_.add(msgs))
      msgsIndex(convId).flatMap { index =>
        index.add(msgs).flatMap(_ => index.firstMessageId) map { first =>
          // XXX: calling update here is a bit ugly
          val ms = msgs.map {
            case msg if first.contains(msg.id) =>
              update(msg.id, _.copy(firstMessage = first.contains(msg.id)))
              msg.copy(firstMessage = first.contains(msg.id))
            case msg =>
              msg
          }
          updateLastMessage(convId, msgs.maxBy(_.time))
        }
      }
    } .recoverWithLog()
  }

  def updateLastMessage(convId: ConvId, msg: MessageData): Future[Option[(ConversationData, ConversationData)]] = {
    verbose(l"MessagesStorage_updateLastMessage msg:${msg.msgType}")
    msg.msgType match {
      case Message.Type.RECALLED => forceUpdateLastMessage(convId)
      case _ => convs.updateLastMessage(convId, msg)
    }
  }

  override def forceUpdateLastMessage(convId: ConvId): Future[Option[(ConversationData, ConversationData)]] = {
    verbose(l"MessagesStorage_forceUpdateLastMessage convId:$convId")
    getLastMessage(convId).flatMap {
      case Some(msg) => convs.updateLastMessage(convId, msg, true)
      case _ => convs.updateLastMessage(convId, null, true)
    }
  }

  onUpdated { updates =>
    Future.traverse(updates.groupBy(_._1.convId)) { case (convId, msgs) =>{
      verbose(l"MessagesStorage_onUpdated: $convId, msgs:${msgs.size}")
        msgsFilteredIndex(convId).foreach(_.update(msgs))
        for {
          index <- msgsIndex(convId)
          _ <- index.update(msgs)
        } yield ()
      } .recoverWithLog()
    }
  }

  convs.onUpdated.on(dispatcher) { _.foreach {
    case (prev, updated) if updated.lastRead != prev.lastRead =>
      verbose(l"lastRead of conversation ${updated.id} updated to ${updated.lastRead}, will update unread count")
      msgsIndex(updated.id).map(_.updateLastRead(updated)).recoverWithLog()
    case _ => // ignore
  } }

  override def addMessage(msg: MessageData) = put(msg.id, msg)

  override def countUnread(conv: ConvId, lastReadTime: RemoteInstant): Future[UnreadCount] = {
    // if a message is both a mention and a quote, we count it as a mention
    storage {
      MessageDataDao.findMessagesFrom(conv, lastReadTime)(_)
    }.future.flatMap { msgs =>
      msgs.acquire { msgs =>
        val unread = msgs.filter { m => !m.isLocal && m.convId == conv && m.time.isAfter(lastReadTime) && !m.isDeleted && m.userId != selfUserId && m.msgType != Message.Type.UNKNOWN }.toVector

        val repliesNotMentionsCount = getAll(unread.filter(!_.hasMentionOf(selfUserId)).flatMap(_.quote.map(_.message))).map(_.flatten)
          .map { quotes =>
            unread.count { m =>
              val quote = quotes.find(q => m.quote.map(_.message).contains(q.id))
              quote.exists(_.userId == selfUserId)
            }
          }

        repliesNotMentionsCount.map { unreadReplies =>
          UnreadCount(
            normal   = unread.count(m => !m.isSystemMessage && m.msgType != Message.Type.KNOCK && !m.hasMentionOf(selfUserId)) - unreadReplies,
            call     = unread.count(_.msgType == Message.Type.MISSED_CALL),
            ping     = unread.count(_.msgType == Message.Type.KNOCK),
            mentions = unread.count(_.hasMentionOf(selfUserId)),
            quotes   = unreadReplies
          )
        }
      }
    }

  }

  override def findQuotesOf(msgId: MessageId): Future[Seq[MessageData]] = storage(MessageDataDao.findQuotesOf(msgId)(_))

  def countSentByType(selfUserId: UserId, tpe: Message.Type): Future[Int] = storage(MessageDataDao.countSentByType(selfUserId, tpe)(_).toInt)

  def countMessages(conv: ConvId, p: MessageEntry => Boolean): Future[Int] = storage(MessageDataDao.countMessages(conv, p)(_))

  def countLaterThan(conv: ConvId, time: RemoteInstant): Future[Long] = storage(MessageDataDao.countLaterThan(conv, time)(_))

  override def getMessage(id: MessageId) = get(id)

  override def getMessages(ids: MessageId*) = getAll(ids)

  def getEntries(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.messagesCursor)

  def lastMessage(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.lastMessage)

  def lastMessageFromSelfAndFromOther(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(mi => mi.signals.lastMessageFromSelf zip mi.signals.lastMessageFromOther)

  def getLastMessage(conv: ConvId) = msgsIndex(conv).flatMap(_.getLastMessage)

  def getLastSentMessage(conv: ConvId) = msgsIndex(conv).flatMap(_.getLastSentMessage)

  def unreadCount(conv: ConvId): Signal[Int] = Signal.future(msgsIndex(conv)).flatMap(_.signals.unreadCount).map(_.messages)

  def lastRead(conv: ConvId) = Signal.future(msgsIndex(conv)).flatMap(_.signals.lastReadTime)

  override def lastLocalMessage(conv: ConvId, tpe: Message.Type) =
    msgsIndex(conv).flatMap(_.lastLocalMessage(tpe)).flatMap {
      case Some(id) => get(id)
      case _ => CancellableFuture.successful(None)
    }

  //TODO: use local instant?
  override def findLocalFrom(conv: ConvId, time: RemoteInstant) =
    find(m => m.convId == conv && m.isLocal && !m.time.isBefore(time), MessageDataDao.findLocalFrom(conv, time)(_), identity)

  override def findMessagesByType(conv: ConvId, msgType: Message.Type): Future[IndexedSeq[MessageData]] = {
    find(m => m.convId == conv && m.msgType == msgType, MessageDataDao.findByType(conv, msgType)(_), identity)
  }

  override def queryMessagesByType(conv: ConvId, msgType: Message.Type): Future[Seq[MessageData]] = storage(MessageDataDao.queryByType(conv, msgType)(_))

  override def findMessagesFrom(conv: ConvId, time: RemoteInstant) =
    find(m => m.convId == conv && !m.time.isBefore(time), MessageDataDao.findMessagesFrom(conv, time)(_), identity)

  override def findMessagesBetween(conv: ConvId, from: RemoteInstant, to: RemoteInstant): Future[IndexedSeq[MessageData]] =
    find(m => !m.isLocal, MessageDataDao.findMessagesBetween(conv, from, to)(_), identity)

  override def delete(msg: MessageData) = {
    verbose(l"delete($msg)")
    for {
      _ <- super.remove(msg.id)
      _ <- Future(msgsFilteredIndex(msg.convId).foreach(_.delete(msg)))
      index <- msgsIndex(msg.convId)
      _ <- index.delete(msg)
      _ <- storage.flushWALToDatabase()
      _ = onMessagesDeletedInConversation ! Set(msg.convId)
    } yield ()
  }

  override def remove(id: MessageId): Future[Unit] = {
    verbose(l"remove($id)")
    getMessage(id) flatMap {
      case Some(msg) => delete(msg)
      case None =>
        warn(l"No message found for: $id")
        Future.successful(())
    }
  }

  override def removeAll(keys: Iterable[MessageId]): Future[Unit] = {
    verbose(l"removeAll($keys)")
    for {
      fromDb <- getAll(keys)
      msgs = fromDb.collect { case Some(m) => m }
      _ <- super.removeAll(keys)
      _ <- Future.traverse(msgs) { msg =>
        verbose(l"removeAll Future.traverse(msgs) { msg:$msg")
        Future(msgsFilteredIndex(msg.convId).foreach(_.delete(msg))).zip(
        msgsIndex(msg.convId).flatMap(_.delete(msg)))
      }
      _ <- storage.flushWALToDatabase()
      _ = onMessagesDeletedInConversation ! msgs.map(_.convId).toSet
    } yield ()
  }

  def clear(conv: ConvId, upTo: RemoteInstant): Future[Unit] = {
    verbose(l"clear($conv, $upTo)")
    for {
      _ <- storage { MessageDataDao.deleteUpTo(conv, upTo)(_) } .future
      _ <- storage { MessageContentIndexDao.deleteUpTo(conv, upTo)(_) } .future
      _ <- deleteCached(m => m.convId == conv && ! m.time.isAfter(upTo))
      _ <- Future(msgsFilteredIndex(conv).foreach(_.delete(upTo)))
      _ <- msgsIndex(conv).flatMap(_.delete(upTo))
      _ <- storage.flushWALToDatabase()
      _ =  onMessagesDeletedInConversation ! Set(conv)
    } yield ()
  }

  override def deleteAll(conv: ConvId) = {
    verbose(l"deleteAll($conv)")
    for {
      _ <- storage { MessageDataDao.deleteForConv(conv)(_) } .future
      _ <- storage { MessageContentIndexDao.deleteForConv(conv)(_) } .future
      _ <- deleteCached(_.convId == conv)
      _ <- Future(msgsFilteredIndex(conv).foreach(_.delete()))
      _ <- msgsIndex(conv).flatMap(_.delete())
      _ <- storage.flushWALToDatabase()
      _ <- forceUpdateLastMessage(conv)
      _ = onMessagesDeletedInConversation ! Set(conv)
    } yield ()
  }

  override def deleteReportNoticeMessage(conv: ConvId): Future[Unit] = {
    verbose(l"deleteReportNoticeMessage($conv)")
    for {
      _ <- storage { MessageDataDao.deleteContentTypeMsgConv(conv, ServerIdConst.CONV_NOTICE_REPORT_BLOCKED)(_) } .future
//      _ <- storage { MessageContentIndexDao.deleteContentTypeMsgConv(conv, ServerIdConst.CONV_NOTICE_REPORT_BLOCKED)(_) } .future
      _ <- deleteCached(m => m.convId == conv && ServerIdConst.CONV_NOTICE_REPORT_BLOCKED.equalsIgnoreCase(m.contentType.getOrElse("")))
      _ <- Future(msgsFilteredIndex(conv).foreach(_.deleteContentTypeMsgConv(ServerIdConst.CONV_NOTICE_REPORT_BLOCKED)))
      _ <- msgsIndex(conv).flatMap(_.deleteContentTypeMsgConv(ServerIdConst.CONV_NOTICE_REPORT_BLOCKED))
      _ <- storage.flushWALToDatabase()
      _ =  onMessagesDeletedInConversation ! Set(conv)
    } yield ()
  }


  override def hasSystemMessage(conv: ConvId, serverTime: RemoteInstant, tpe: Message.Type, sender: UserId) = {
    def matches(msg: MessageData) = msg.convId == conv && msg.time == serverTime && msg.msgType == tpe && msg.userId == sender
    find(matches, MessageDataDao.findSystemMessage(conv, serverTime, tpe, sender)(_), identity).map(_.size).map {
      case 0 => false
      case 1 => true
      case _ =>
        warn(l"Found multiple system messages with given timestamp")
        true
    }
  }
}

object MessagesStorage {
  val cacheSize = 12048
  val filteredMessagesCacheSize = 32
  val FirstMessageTypes = {
    import Message.Type._
    Set(TEXT, TEXT_EMOJI_ONLY, KNOCK, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, LOCATION)
  }
}

trait MessageAndLikesStorage {
  val onUpdate: EventStream[MessageId]
  def apply(ids: Seq[MessageId]): Future[Seq[MessageAndLikes]]
  def getMessageAndLikes(id: MessageId): Future[Option[MessageAndLikes]]
  def combineWithLikes(msgs: Seq[MessageData]): Future[Seq[MessageAndLikes]]
  def combineWithLikes(msg: MessageData): Future[MessageAndLikes]
  def combine(msg: MessageData, likes: Likes, forbids: Forbids, selfUserId: UserId, quote: Option[MessageData]): MessageAndLikes
  def sortedLikes(likes: Likes, selfUserId: UserId): (IndexedSeq[UserId], Boolean)
}

class MessageAndLikesStorageImpl(selfUserId: UserId, messages: => MessagesStorage, likings: ReactionsStorage, forbidDatas: ForbidsStorage) extends MessageAndLikesStorage with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Background
  import com.waz.utils.events.EventContext.Implicits.global

  val onUpdate = EventStream[MessageId]() // TODO: use batching, maybe report new message data instead of just id

  messages.onDeleted { ids => ids foreach { onUpdate ! _ } }
  messages.onChanged { ms => ms foreach { m => onUpdate ! m.id }}
  likings.onChanged { _ foreach { l => onUpdate ! l.message } }
//  forbidDatas.onChanged { _ foreach { l => onUpdate ! l.message } }


  def apply(ids: Seq[MessageId]): Future[Seq[MessageAndLikes]] = for {
    msgs <- messages.getMessages(ids: _*).map(_.flatten)
    likes <- getLikes(msgs)
    forbids <- getForbids(msgs)
    quotes <- getQuotes(msgs)
  } yield msgs.map { msg =>
    combine(msg, likes.getOrElse(msg.id, Likes.Empty(msg.id)), forbids.getOrElse(msg.id, Forbids.Empty(msg.id)), selfUserId, quotes.get(msg.id).flatten)
  }

  def getMessageAndLikes(id: MessageId): Future[Option[MessageAndLikes]] = apply(Seq(id)).map(_.headOption)

  override def combineWithLikes(msgs: Seq[MessageData]): Future[Seq[MessageAndLikes]] = for {
    likes <- getLikes(msgs)
    forbids <- getForbids(msgs)
    quotes <- getQuotes(msgs)
  } yield msgs.map { msg =>
    combine(msg, likes.getOrElse(msg.id, Likes.Empty(msg.id)), forbids.getOrElse(msg.id, Forbids.Empty(msg.id)), selfUserId, quotes.get(msg.id).flatten)
  }

  override def combineWithLikes(msg: MessageData): Future[MessageAndLikes] = combineWithLikes(Seq(msg)).map(_.head)

  def getQuotes(msgs: Seq[MessageData]): Future[Map[MessageId, Option[MessageData]]] = {
    Future.sequence(msgs.flatMap(m => m.quote.map(m.id -> _.message).toSeq).map {
      case (m, q) => messages.getMessage(q).map(m -> _)
    }).map(_.toMap)
  }

  def getLikes(msgs: Seq[MessageData]): Future[Map[MessageId, Likes]] = {
    likings.loadAll(msgs.map(_.id)).map { likes =>
      likes.by[MessageId, Map](_.message)
    }
  }

  def getForbids(msgs: Seq[MessageData]): Future[Map[MessageId, Forbids]] = {
    forbidDatas.loadAll(msgs.map(_.id)).map { forbids =>
      forbids.by[MessageId, Map](_.message)
    }
  }

  def combine(msg: MessageData, likes: Likes, forbids: Forbids, selfUserId: UserId, quote: Option[MessageData]): MessageAndLikes = {
    verbose(l"combine(msg:$msg), forbids:${Option(forbids)}, likes:${Option(likes)}")
//    if (likes.likers.isEmpty) MessageAndLikes(msg, Vector(), likedBySelf = false, quote)
//    else sortedLikes(likes, selfUserId) match { case (likers, selfLikes) => MessageAndLikes(msg, likers, selfLikes, quote) }
    val (likers, selfLikes) = sortedLikes(likes, selfUserId)
    val (forbidUser, forbidName, isForbid) = sortedForbids(forbids, selfUserId)
    MessageAndLikes(msg, likers, selfLikes, forbidUser, forbidName, isForbid, quote)
  }

  def sortedLikes(likes: Likes, selfUserId: UserId): (IndexedSeq[UserId], Boolean) = {
    val likers = likes.likers
    if (likers.isEmpty) {
      (Vector(), false)
    } else {
      val users = likers.toVector.sortBy(_._2).map(_._1)
      (users, likers contains selfUserId)
    }
  }

  def sortedForbids(forbids: Forbids, selfUserId: UserId): (Option[UserId], Option[String], Boolean) = {
    val forbidTypeUser = forbids.forbidTypeUser
    if (forbidTypeUser.isEmpty || !forbidTypeUser.contains(ForbidData.Types.Forbid)) {
      (None, None, false)
    } else {
      forbidTypeUser.get(ForbidData.Types.Forbid) match {
        case Some(userAction) if userAction._3 == ForbidData.Action.Forbid => (Option(userAction._1), userAction._2, true)
        case _ => (None, None, false)
      }
    }
  }
}
