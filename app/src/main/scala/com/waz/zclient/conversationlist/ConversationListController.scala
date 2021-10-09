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
package com.waz.zclient.conversationlist

import android.util.LruCache
import com.jsy.common.moduleProxy.ProxyConversationListManagerFragmentObject
import com.waz.api.Message
import com.waz.content.{ConversationStorage, MembersStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.{SerialDispatchQueue, Threading}
import com.waz.utils._
import com.waz.utils.events._
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversationlist.ConversationListAdapter.ListMode
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import com.waz.zclient.utils.UiStorage.ConversationCacheSize
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class ConversationListController(implicit inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import ConversationListController._

  val conversationMembersCache = new LruCache[ConvId, Signal[Seq[UserId]]](ConversationCacheSize)

  val zms = inject[Signal[ZMessaging]]
  val membersCache = zms map { new MembersCache(_) }
  val lastMessageCache = zms map { new LastMessageCache(_) }

  private val lastReportNoticeMessageCache = zms map {
    new LastReportNoticeMessageCache(_)
  }

  def members(conv: ConvId) = membersCache.flatMap(_.apply(conv))

  def membersLimit(conv: ConvId) = {
    Option(conversationMembersCache.get(conv)).getOrElse(returning(membersCache.flatMap(_.apply(conv, true)))(conversationMembersCache.put(conv, _)))
  }

  def lastMessage(conv: ConvId) = lastMessageCache.flatMap(_.apply(conv))

  def lastReportNoticeMessage(conv: ConvId): Signal[Option[MessageData]] = lastReportNoticeMessageCache.flatMap { sig =>
    sig.apply(conv)
  }

  lazy val userAccountsController = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  // availability will be other than None only when it's a one-to-one conversation
  // (and the other user's availability is set to something else than None)
  def availability(conv: ConvId): Signal[Availability] = for {
    currentUser <- userAccountsController.currentUser
    isInTeam = currentUser.exists(_.teamId.nonEmpty)
    memberIds <- if (isInTeam) members(conv) else Signal.const(Seq.empty)
    otherUser <- if (memberIds.size == 1) userData(memberIds.headOption) else Signal.const(Option.empty[UserData])
  } yield {
    otherUser.fold[Availability](Availability.None)(_.availability)
  }

  private def userData(id: Option[UserId]) = id.fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Option(_)))

  lazy val establishedConversations = for {
    z          <- zms
    convs      <- z.convsStorage.contents.throttle(ProxyConversationListManagerFragmentObject.ConvListUpdateThrottling)
  } yield convs.values.filter(EstablishedListFilter)

  lazy val regularConversationListData: Signal[Seq[ConversationData]] = conversationData(ConversationListAdapter.Normal)
  lazy val archiveConversationListData: Signal[Seq[ConversationData]] = conversationData(ConversationListAdapter.Archive)

  lazy val foldExpand: SourceSignal[Boolean] with NoAutowiring = Signal(false)

  def conversationData(listMode: ConversationListAdapter.ListMode) =
    for {
      convsStorage  <- inject[Signal[ConversationStorage]]
      conversations <- convsStorage.contents
    } yield{
      conversations.values.filter(listMode.filter).filter(_.isActive).toSeq.sorted(listMode.sort)
    }

  lazy val incomingConversationListData =
    for {
      selfUserId     <- inject[Signal[UserId]]
      convsStorage   <- inject[Signal[ConversationStorage]]
      membersStorage <- inject[Signal[MembersStorage]]
      conversations  <- convsStorage.contents
      incomingConvs  =  conversations.values.filter(ConversationListAdapter.Incoming.filter).toSeq
      members <- Signal.sequence(incomingConvs.map(c => membersStorage.activeMembers(c.id).map(_.find(_ != selfUserId))):_*)
    } yield (incomingConvs, members.flatten)

  def conversationGroupOrThousandsGroupList(listMode: ListMode) = for {
    z <- zms
    conversations <- z.convsStorage.contents //.filter { x => x.head._2.convType == IConversation.Type.GROUP || x.head._2.convType == IConversation.Type.THROUSANDS_GROUP }
  } yield {
    (z.selfUserId, conversations.values.filter(listMode.filter).toSeq)
  }

  def nextConversation(convId: ConvId): Future[Option[ConvId]] =
    regularConversationListData.head.map {
      case regular => regular.lift(regular.indexWhere(_.id == convId) + 1).map(_.id)
    }(Threading.Background)
}

object ConversationListController {

  lazy val RegularListFilter: (ConversationData => Boolean) = { c => Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.ThousandsGroup, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && !c.completelyCleared }
  lazy val GroupOrThousandsGroupListFilter: (ConversationData => Boolean) = { c => Set(ConversationType.Group, ConversationType.ThousandsGroup).contains(c.convType) && !c.hidden && !c.completelyCleared }
  lazy val IncomingListFilter: (ConversationData => Boolean) = { c => !c.hidden && c.convType == ConversationType.Incoming }
  lazy val ArchivedListFilter: (ConversationData => Boolean) = { c => Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.ThousandsGroup, ConversationType.Incoming, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && c.archived && !c.completelyCleared }
  lazy val EstablishedListFilter: (ConversationData => Boolean) = { c => RegularListFilter(c) && c.convType != ConversationType.WaitForConnection }
  lazy val EstablishedArchivedListFilter: (ConversationData => Boolean) = { c => ArchivedListFilter(c) && c.convType != ConversationType.WaitForConnection }
  lazy val IntegrationFilter: (ConversationData => Boolean) = { c => (c.convType == ConversationType.Group || c.convType == ConversationType.ThousandsGroup) && !c.hidden }

  lazy val messageCountFilter: ConversationData => Boolean = { c => Set(ConversationType.OneToOne, ConversationType.Group, ConversationType.ThousandsGroup, ConversationType.WaitForConnection).contains(c.convType) && !c.hidden && !c.completelyCleared && !c.isAllMuted}

  // Maintains a short list of members for each conversation.
  // Only keeps up to 4 users other than self user, this list is to be used for avatar in conv list.
  // We keep this always in memory to avoid reloading members list for every list row view (caused performance issues)
  class MembersCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext) extends Injectable {
    private implicit val dispatcher = new SerialDispatchQueue(name = "MembersCache")

    private def entries(convMembers: Seq[ConversationMemberData]) =
      convMembers.groupBy(_.convId).map { case (convId, ms) =>
        val otherUsers = ms.collect { case ConversationMemberData(user, _) if user != zms.selfUserId => user }
        convId -> ConversationAvatarView.shuffle(otherUsers, convId).take(4)
      }

    val updatedEntries = EventStream.union(
      zms.membersStorage.onAdded.map(_.map(_.convId).toSet),
      zms.membersStorage.onDeleted.map(_.map(_._2).toSet)
    ) mapAsync { convs =>
      zms.membersStorage.getByConvs(convs) map entries
    }

    val members = new AggregatingSignal[Map[ConvId, Seq[UserId]], Map[ConvId, Seq[UserId]]](updatedEntries, zms.membersStorage.list() map entries, _ ++ _)

    val membersLimit = (conv: ConvId, isLimit: Boolean) => new AggregatingSignal[Map[ConvId, Seq[UserId]], Map[ConvId, Seq[UserId]]](updatedEntries, zms.membersStorage.listByConvLimit(conv, "2") map entries, _ ++ _)

    def apply(conv: ConvId): Signal[Seq[UserId]] = members.map(_.getOrElse(conv, Seq.empty[UserId]))

    def apply(conv: ConvId, isLimit: Boolean): Signal[Seq[UserId]] = {
      if (isLimit) {
        membersLimit(conv, isLimit).map(_.getOrElse(conv, Seq.empty[UserId]))
      } else {
        apply(conv)
      }
    }
  }

  case class LastMsgs(lastMsg: Option[MessageData], lastMissedCall: Option[MessageData])

  // Keeps last message and missed call for each conversation, this is needed because MessagesStorage is not
  // supposed to be used for multiple conversations at the same time, as it loads an index of all conv messages.
  // Using MessagesStorage with multiple/all conversations forces it to reload full msgs index on every conv switch.
  class LastMessageCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext)
    extends Injectable with DerivedLogTag {

    private implicit val executionContext: ExecutionContext = Threading.Background

    private val cache = new mutable.HashMap[ConvId, Signal[Option[MessageData]]]

    private val lastReadCache = new mutable.HashMap[ConvId, Signal[Option[RemoteInstant]]]

    private val changeEvents = zms.messagesStorage.onChanged.map(_.groupBy(_.convId).mapValues(_.maxBy(_.time)))

    private val convLastReadChangeEvents = zms.convsStorage.onChanged.map(_.groupBy(_.id).mapValues(_.map(_.lastRead).head))

    private val missedCallEvents = zms.messagesStorage.onChanged.map(_.filter(_.msgType == Message.Type.MISSED_CALL).groupBy(_.convId).mapValues(_.maxBy(_.time)))

    private def messageUpdateEvents(conv: ConvId) = changeEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def lastReadUpdateEvents(conv: ConvId) = convLastReadChangeEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def missedCallUpdateEvents(conv: ConvId) = missedCallEvents.map(_.get(conv)).collect { case Some(m) => m }

    private def lastMessage(conv: ConvId) = zms.storage.db.read(MessageData.MessageDataDao.last(conv)(_))

    private def lastRead(conv: ConvId) = zms.storage.db.read(ConversationData.ConversationDataDao.getById(conv)(_).map(_.lastRead))

    private def lastUnreadMissedCall(conv: ConvId): Future[Option[MessageData]] =
      for {
        lastRead <- lastReadSignal(conv).head
        missed <-
          zms.storage.db.read { MessageData.MessageDataDao.findByType(conv, Message.Type.MISSED_CALL)(_).acquire { msgs =>
              lastRead.flatMap(i => msgs.toSeq.find(_.time.isAfter(i)))
            }
          }
      } yield missed

    def apply(conv: ConvId): Signal[LastMsgs] =
      Signal(lastMessageSignal(conv), lastMissedCallSignal(conv)).map(LastMsgs.tupled)

    private def lastMessageSignal(conv: ConvId): Signal[Option[MessageData]] = cache.getOrElseUpdate(conv,
      new AggregatingSignal[MessageData, Option[MessageData]](messageUpdateEvents(conv), lastMessage(conv), {
        case (res @ Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      }))

    private def lastReadSignal(conv: ConvId): Signal[Option[RemoteInstant]] = lastReadCache.getOrElseUpdate(conv,
      new AggregatingSignal[RemoteInstant, Option[RemoteInstant]](lastReadUpdateEvents(conv), lastRead(conv), {
        case (res @ Some(last), update) if last.isAfter(update) => res
        case (_, update) => Some(update)
      }))

    private def lastMissedCallSignal(conv: ConvId): Signal[Option[MessageData]] =
      new AggregatingSignal[MessageData, Option[MessageData]](missedCallUpdateEvents(conv), lastUnreadMissedCall(conv), {
        case (res @ Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      })
  }

  class LastReportNoticeMessageCache(zms: ZMessaging)(implicit inj: Injector, ec: EventContext) extends Injectable {

    private implicit val executionContext: ExecutionContext = Threading.Background

    private val reportNoticeEvents = zms.messagesStorage.onChanged.map(_.filter { messageData => ServerTextJsonParseUtils.isGroupReportBlocked(messageData.contentType) }.groupBy(_.convId).mapValues(_.maxBy(_.time)))

    private def lastReportNotice(conv: ConvId) = zms.storage.db.read(MessageData.MessageDataDao.lastReportNotice(conv)(_))

    private def reportNoticeUpdateEvents(conv: ConvId) = reportNoticeEvents.map(_.get(conv)).collect { case Some(m) => m }

    def apply(conv: ConvId): AggregatingSignal[MessageData, Option[MessageData]] = {
      new AggregatingSignal[MessageData, Option[MessageData]](reportNoticeUpdateEvents(conv), lastReportNotice(conv), {
        case (res@Some(last), update) if last.time.isAfter(update.time) => res
        case (_, update) => Some(update)
      })
    }
  }
}
