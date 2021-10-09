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
package com.waz.service.messages

import com.waz.log.LogSE._
import com.waz.api.Message
import com.waz.api.Message.{Status, Type}
import com.waz.api.impl.ErrorResponse
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent._
import com.waz.model.{Mention, MessageId, _}
import com.waz.service._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.otr.VerificationStateUpdater.{ClientUnverified, MemberAdded, VerificationChange}
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.AssetClient.Retention
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.RichFuture.traverseSequential
import com.waz.utils._
import com.waz.utils.crypto.ReplyHashing
import com.waz.utils.events.{EventContext, EventStream, Signal}
import org.json.JSONObject

import scala.collection.immutable.PagedSeq
import scala.collection.{breakOut, immutable, mutable}
import scala.concurrent.Future
import scala.concurrent.Future.{successful, traverse}
import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.{Failure, Success}

trait MessagesService {
  def msgEdited: EventStream[(MessageId, MessageId)]

  def addTextMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null): Future[MessageData]

  def addTextJsonMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null): Future[MessageData]

  def addKnockMessage(convId: ConvId, selfUserId: UserId, expectsReadReceipt: ReadReceiptSettings = AllDisabled): Future[MessageData]

  def addAssetMessage(convId: ConvId, asset: AssetData, expectsReadReceipt: ReadReceiptSettings = AllDisabled, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null): Future[MessageData]

  def addLocationMessage(convId: ConvId, content: Location, expectsReadReceipt: ReadReceiptSettings = AllDisabled, replyMessageId: MessageId = null): Future[MessageData]

  def addReplyMessage(quote: MessageId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]]

  def addMissedCallMessage(rConvId: RConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]]
  def addMissedCallMessage(convId: ConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]]
  def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: RemoteInstant, duration: FiniteDuration): Future[Option[MessageData]]

  def removeMessageByType(convId: ConvId, msgType :Message.Type): Future[IndexedSeq[MessageData]]

  def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: Name, fromSync: Boolean = false): Future[MessageData]

  def addLocalConnectRequestMsg(convId: ConvId, creator: UserId, convType: ConversationType, displayName: Name, smallRAssetId: RAssetId): Future[Any]

  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[Name], readReceiptsAllowed: Boolean, time: Option[RemoteInstant] = None): Future[Unit]

  //TODO forceCreate is a hacky workaround for a bug where previous system messages are not marked as SENT. Do NOT use!
  def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false, forceCreate: Boolean = false): Future[Option[MessageData]]
  def addMemberLeaveMessage(convId: ConvId, selfUserId: UserId, user: UserId): Future[Any]
  def addRenameConversationMessage(convId: ConvId, selfUserId: UserId, name: Name): Future[Option[MessageData]]
  def addReceiptModeChangeMessage(convId: ConvId, from: UserId, receiptMode: Int): Future[Option[MessageData]]
  def addTimerChangedMessage(convId: ConvId, from: UserId, duration: Option[FiniteDuration], time: RemoteInstant): Future[Unit]
  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]
  def addReceiptModeIsOnMessage(convId: ConvId): Future[MessageData]

  def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]]
  def addOtrVerifiedMessage(convId: ConvId): Future[Option[MessageData]]
  def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]]

  def retryMessageSending(conv: ConvId, msgId: MessageId): Future[Option[SyncId]]
  def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status): Future[Option[MessageData]]
  def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant] = None): Future[MessageData]
  def updateMessageReadState(messageId: MessageId, stateValue: Int): Future[Option[MessageData]]

  def updateMessageTranslate(messageId: MessageId, content: String): Future[Option[MessageData]]

  def deleteMessageTranslate(messageId: MessageId): Future[Option[MessageData]]

  def recallMessage(convId: ConvId, msgId: MessageId, userId: UserId, systemMsgId: MessageId = MessageId(), time: RemoteInstant, state: Message.Status = Message.Status.PENDING): Future[Option[MessageData]]
  def applyMessageEdit(convId: ConvId, userId: UserId, time: RemoteInstant, gm: GenericMessage): Future[Option[MessageData]]

  def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Any]

  def messageSent(convId: ConvId, msgId: MessageId, newTime: RemoteInstant, enabled_edit_msg: Boolean = false): Future[Option[MessageData]]
  def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]]
  def retentionPolicy(convData: ConversationData): CancellableFuture[Retention]
}

class MessagesServiceImpl(selfUserId: UserId,
                          teamId: Option[TeamId],
                          replyHashing: ReplyHashing,
                          storage: MessagesStorage,
                          updater: MessagesContentUpdater,
                          edits: EditHistoryStorage,
                          convs: ConversationsContentUpdater,
                          network: NetworkModeService,
                          members: MembersStorage,
                          usersStorage: UsersStorage,
                          sync:         SyncServiceHandle) extends MessagesService with DerivedLogTag {
  import Threading.Implicits.Background

  private implicit val ec = EventContext.Global

  override val msgEdited = EventStream[(MessageId, MessageId)]()

  override def recallMessage(convId: ConvId, msgId: MessageId, userId: UserId, systemMsgId: MessageId = MessageId(), time: RemoteInstant, state: Message.Status = Message.Status.PENDING) =
    updater.getMessage(msgId) flatMap {
      case Some(msg) if msg.convId != convId =>
        error(l"can not recall message belonging to other conversation: $msg, requested by $userId")
        Future successful None
      case Some(msg) if msg.canRecall(convId, userId) =>
        updater.deleteOnUserRequest(Seq(msgId)) flatMap { _ =>
          val recall = MessageData(systemMsgId, convId, Message.Type.RECALLED, time = msg.time, editTime = time max msg.time, userId = userId, state = state, protos = Seq(GenericMessage(systemMsgId.uid, MsgRecall(msgId))))
          if (userId == selfUserId) Future successful Some(recall) // don't save system message for self user
          else updater.addMessage(recall)
        }
      case Some(msg) if msg.isEphemeral =>
        // ephemeral message expired on other device, or on receiver side
        updater.deleteOnUserRequest(Seq(msgId)) map { _ => None }
      case msg =>
        warn(l"can not recall $msg, requested by $userId")
        Future successful None
    }

  override def applyMessageEdit(convId: ConvId, userId: UserId, time: RemoteInstant, gm: GenericMessage) = Serialized.future("applyMessageEdit", convId) {

    def findLatestUpdate(id: MessageId): Future[Option[MessageData]] =
      updater.getMessage(id) flatMap {
        case Some(msg) => Future successful Some(msg)
        case None =>
          edits.get(id) flatMap {
            case Some(EditHistory(_, updated, _)) => findLatestUpdate(updated)
            case None => Future successful None
          }
      }

    gm match {
      case GenericMessage(newId, MsgEdit(oldId, Text(text, mentions, links, quote))) =>

        def applyEdit(msg: MessageData) = {
          val newMsgId = MessageId(newId.str)
          for {
            _ <- edits.insert(EditHistory(msg.id, newMsgId, time))
            (tpe, ct) = MessageData.messageContent(text, mentions, links, weblinkEnabled = true)
            edited = msg.copy(id = newMsgId, msgType = tpe, content = ct, protos = Seq(gm), editTime = time)
            //            optMsg <- edited.adjustMentions(false).getOrElse(edited)
            res <- updater.addMessage(edited.adjustMentions(false).getOrElse(edited))
            quotesOf <- storage.findQuotesOf(msg.id)
            _ <- storage.updateAll2(quotesOf.map(_.id), _.replaceQuote(newMsgId, msg.replyMessageId))
            _ = msgEdited ! (msg.id, newMsgId)
            _ <- updater.deleteOnUserRequest(Seq(msg.id))
          } yield res
        }

        updater.getMessage(oldId) flatMap {
          case Some(msg) if msg.userId == userId && msg.convId == convId =>
            applyEdit(msg)
          case _ =>
            // original message was already deleted, let's check if it was already updated
            edits.get(oldId) flatMap {
              case Some(EditHistory(_, _, editTime)) if editTime <= time =>
                verbose(l"message $oldId has already been updated, discarding later update")
                Future successful None

              case Some(EditHistory(_, updated, _)) =>
                // this happens if message has already been edited locally,
                // but that edit is actually newer than currently received one, so we should revert it
                // we always use only the oldest edit for given message (as each update changes the message id)
                verbose(l"message $oldId has already been updated, will overwrite new message")
                findLatestUpdate(updated) flatMap {
                  case Some(msg) => applyEdit(msg)
                  case None =>
                    error(l"Previously updated message was not found for: $gm")
                    Future successful None
                }

              case None =>
                verbose(l"didn't find the original message for edit: $gm")
                Future successful None
            }
        }
      case _ =>
        error(l"invalid message for applyMessageEdit: $gm")
        Future successful None
    }
  }

  private def getReplyMessageId(messageId: MessageId) = if (messageId == null) None else Some(messageId)

  override def addTextMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null) = {
    verbose(l"addTextMessage($convId, ${content.size}, $mentions, $exp")
    val (tpe, ct) = MessageData.messageContent(content, mentions, weblinkEnabled = true)
    verbose(l"parsed content: $ct")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, tpe, selfUserId, ct, protos = Seq(GenericMessage(id.uid, Text(content, ct.flatMap(_.mentions), Nil, expectsReadReceipt.selfSettings))), forceReadReceipts = expectsReadReceipt.convSetting, replyMessageId = getReplyMessageId(replyMessageId), enabled_edit_msg = true), exp = exp) // FIXME: links
  }

  override def addTextJsonMessage(convId: ConvId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null) = {
    verbose(l"addTextJsonMessage($convId, ${content.size}, $mentions, $exp")
    val (tpe, ct) = MessageData.messageContentJson(content, mentions, weblinkEnabled = true)
    val parseResult = ct.map { content => ServerTextJsonParseUtils.getTextJsonContentType(content.content) }.headOption
    val contentType = if (parseResult.nonEmpty) parseResult.get else None
    verbose(l"parsed content: $ct")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, tpe, selfUserId, ct, protos = Seq(GenericMessage(id.uid, Text(content, ct.flatMap(_.mentions), Nil, expectsReadReceipt.selfSettings))), forceReadReceipts = expectsReadReceipt.convSetting, replyMessageId = getReplyMessageId(replyMessageId), contentType = contentType, enabled_edit_msg = true), exp = exp) // FIXME: links
  }

  override def addReplyMessage(quote: MessageId, content: String, expectsReadReceipt: ReadReceiptSettings = AllDisabled, mentions: Seq[Mention] = Nil, exp: Option[Option[FiniteDuration]] = None): Future[Option[MessageData]] = {

    verbose(l"addReplyMessage($quote, ${content.size}, $mentions, $exp)")
    updater.getMessage(quote).flatMap {
      case Some(original) =>
        val (tpe, ct) = MessageData.messageContent(content, mentions, weblinkEnabled = true)
        verbose(l"parsed content: $ct")
        val id = MessageId()
        val localTime = LocalInstant.Now
        replyHashing.hashMessage(original).flatMap { hash =>
          verbose(l"hash before sending: $hash, original time: ${original.time}")
          updater.addLocalMessage(
            MessageData(
              id, original.convId, tpe, selfUserId, ct,
              protos = Seq(GenericMessage(id.uid, Text(content, ct.flatMap(_.mentions), Nil, Some(Quote(quote, Some(hash))), expectsReadReceipt.selfSettings))),
              quote = Some(QuoteContent(quote, validity = true, hash = Some(hash))),
              forceReadReceipts = expectsReadReceipt.convSetting, enabled_edit_msg = true
            ),
            exp = exp,
            localTime = localTime
          ).map(Option(_))
        }
          .recover {
            case e@(_: IllegalArgumentException | _: replyHashing.MissingAssetException) =>
              error(l"Got exception when checking reply hash, skipping", e)
              None
          }
      case None =>
        error(l"A reply to a non-existent message: $quote")
        Future.successful(None)
    }
  }

  override def addLocationMessage(convId: ConvId, content: Location, expectsReadReceipt: ReadReceiptSettings = AllDisabled, replyMessageId: MessageId = null) = {
    verbose(l"addLocationMessage($convId, $content)")
    val id = MessageId()
    updater.addLocalMessage(MessageData(id, convId, Type.LOCATION, selfUserId, protos = Seq(GenericMessage(id.uid, content)), forceReadReceipts = expectsReadReceipt.convSetting, replyMessageId = getReplyMessageId(replyMessageId), enabled_edit_msg = true))
  }

  override def addLocalConnectRequestMsg(convId: ConvId, creator: UserId, convType: ConversationType, displayName: Name, smallRAssetId: RAssetId): Future[Any] = {
    val message = MessageData(
      MessageId(), convId, Message.Type.CONNECT_REQUEST, UserId(convId.str), content = MessageData.textContent(""), name = Some(displayName), recipient = Some(creator),
      //time = if (fromSync) RemoteInstant.Epoch else now(clock))
      time = RemoteInstant.Epoch - 1.millis)
    updater.addLocalConnectRequestMsg(convId, message)
  }

  override def addAssetMessage(convId: ConvId, asset: AssetData, expectsReadReceipt: ReadReceiptSettings = AllDisabled, exp: Option[Option[FiniteDuration]] = None, replyMessageId: MessageId = null) = {
    val tpe = asset match {
      case AssetData.IsImage() => Message.Type.ASSET
      case AssetData.IsVideo() => Message.Type.VIDEO_ASSET
      case AssetData.IsAudio() => Message.Type.AUDIO_ASSET
      case _                   => Message.Type.ANY_ASSET
    }
    val mid = MessageId(asset.id.str)
    updater.addLocalMessage(MessageData(mid, convId, tpe, selfUserId, protos = Seq(GenericMessage(mid.uid, Asset(asset, expectsReadConfirmation = expectsReadReceipt.selfSettings))), forceReadReceipts = expectsReadReceipt.convSetting, replyMessageId = getReplyMessageId(replyMessageId), enabled_edit_msg = true), exp = exp)
  }

  override def addRenameConversationMessage(convId: ConvId, from: UserId, name: Name) = {
    def update(msg: MessageData) = msg.copy(name = Some(name))
    def create = MessageData(MessageId(), convId, Message.Type.RENAME, from, name = Some(name))
    updater.updateOrCreateLocalMessage(convId, Message.Type.RENAME, update, create)
  }

  override def addReceiptModeChangeMessage(convId: ConvId, from: UserId, receiptMode: Int) = {
    val msgType = if (receiptMode > 0) Message.Type.READ_RECEIPTS_ON else Message.Type.READ_RECEIPTS_OFF
    def create = MessageData(MessageId(), convId, msgType, from)
    updater.updateOrCreateLocalMessage(convId, msgType, msg => msg, create)
  }

  override def addReceiptModeIsOnMessage(convId: ConvId): Future[MessageData] =
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.READ_RECEIPTS_ON, selfUserId, firstMessage = true))


  override def addTimerChangedMessage(convId: ConvId, from: UserId, duration: Option[FiniteDuration], time: RemoteInstant) =
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.MESSAGE_TIMER, from, time = time, duration = duration)).map(_ => {})

  override def removeMessageByType(convId: ConvId, msgType :Message.Type): Future[IndexedSeq[MessageData]] = {
    for{
      msgs <- storage.findMessagesByType(convId, msgType).flatMap{
        case msgs:Seq[MessageData]=>
          Future.successful(msgs)
        case _ =>
          Future.successful(IndexedSeq.empty)
      }
      _ <- if (msgs.nonEmpty) storage.removeAll(msgs.map(_.id)) else Future.successful(IndexedSeq.empty)
    }yield {
      verbose(l"removeMessageByType msgs.nonEmpty:(${msgs.nonEmpty})")
      msgs
    }
  }

  override def addConnectRequestMessage(convId: ConvId, fromUser: UserId, toUser: UserId, message: String, name: Name, fromSync: Boolean = false) = {
    val msg = MessageData(
      MessageId(), convId, Message.Type.CONNECT_REQUEST, fromUser, content = MessageData.textContent(message), name = Some(name), recipient = Some(toUser),
      time = RemoteInstant.Epoch)

    val delOldMsgs = removeMessageByType(convId, Message.Type.CONNECT_REQUEST)
    storage.insert(msg)

  }

  override def addKnockMessage(convId: ConvId, selfUserId: UserId, expectsReadReceipt: ReadReceiptSettings = AllDisabled) = {
    debug(l"addKnockMessage($convId, $selfUserId)")
    updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.KNOCK, selfUserId, forceReadReceipts = expectsReadReceipt.convSetting))
  }

  override def addDeviceStartMessages(convs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] =
    Serialized.future('addDeviceStartMessages)(traverse(convs filter isGroupOrOneToOne) { conv =>
      storage.getLastMessage(conv.id) map {
        case None =>
          debug(l"addDeviceStartMessages None ${conv.id}")
          Some(MessageData(MessageId(s"${conv.id.toString}_STARTED"), conv.id, Message.Type.STARTED_USING_DEVICE,
            selfUserId, time = RemoteInstant.Epoch))
        case Some(messageData) if messageData.msgType == Message.Type.CONNECT_REQUEST =>
          debug(l"addDeviceStartMessages Last.CONNECT_REQUEST ${conv.id}")
          Some(MessageData(MessageId(s"${conv.id.toString}_STARTED"), conv.id, Message.Type.STARTED_USING_DEVICE,
            selfUserId, time = RemoteInstant.Epoch))
        case _ =>
          None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten)
    })

  private def isGroupOrOneToOne(conv: ConversationData) = conv.convType == ConversationType.Group || conv.convType == ConversationType.OneToOne || conv.convType == ConversationType.ThousandsGroup

  def addHistoryLostMessages(cs: Seq[ConversationData], selfUserId: UserId): Future[Set[MessageData]] = {
    // TODO: those messages should include information about what was actually changed
    traverseSequential(cs) { conv =>
      storage.getLastMessage(conv.id) map {
        case Some(msg) if msg.msgType != Message.Type.STARTED_USING_DEVICE =>
          Some(MessageData(MessageId(), conv.id, Message.Type.HISTORY_LOST, selfUserId, time = msg.time + 1.millis))
        case _ =>
          // conversation has no messages or has STARTED_USING_DEVICE msg,
          // it means that conv was just created and we don't need to add history lost msg
          None
      }
    } flatMap { msgs =>
      storage.insertAll(msgs.flatten) flatMap { added =>
        // mark messages read if there is no other unread messages
        val times: Map[ConvId, RemoteInstant] = added.map(m => m.convId -> m.time)(breakOut)
        convs.storage.updateAll2(times.keys, { c =>
          val t = times(c.id)
          if (c.lastRead.toEpochMilli == t.toEpochMilli - 1) c.copy(lastRead = t) else c
        }) map { _ => added }
      }
    }
  }

  def addConversationStartMessage(convId: ConvId, creator: UserId, users: Set[UserId], name: Option[Name], readReceiptsAllowed: Boolean, time: Option[RemoteInstant]) = {
    updater
      .addLocalSentMessage(MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, name = name, members = users, firstMessage = true), time)
      .flatMap(_ =>
        if (readReceiptsAllowed)
          updater.addLocalSentMessage(MessageData(MessageId(), convId, Message.Type.READ_RECEIPTS_ON, creator, firstMessage = true), time.map(_ + 1.millis)).map(_ => ())
        else
          Future.successful({})
      )
  }

  override def addMemberJoinMessage(convId: ConvId, creator: UserId, users: Set[UserId], firstMessage: Boolean = false, forceCreate: Boolean = false) = {
    verbose(l"addMemberJoinMessage($convId, $creator, $users)")

    def updateOrCreate(added: Set[UserId]) = {
      def update(msg: MessageData) = msg.copy(members = msg.members ++ added)
      def create = MessageData(MessageId(), convId, Message.Type.MEMBER_JOIN, creator, members = added, firstMessage = firstMessage)

      if (forceCreate)
        updater.addLocalSentMessage(create).map(Some(_))
      else
        updater.updateOrCreateLocalMessage(convId, Message.Type.MEMBER_JOIN, update, create)
    }

    // check if we have local leave message with same users
    storage.lastLocalMessage(convId, Message.Type.MEMBER_LEAVE) flatMap {
      case Some(msg) if users.exists(msg.members) =>
        val toRemove = msg.members -- users
        val toAdd = users -- msg.members
        if (toRemove.isEmpty) updater.deleteMessage(msg) // FIXME: race condition
        else updater.updateMessage(msg.id)(_.copy(members = toRemove)) // FIXME: race condition

        if (toAdd.isEmpty) successful(None) else updateOrCreate(toAdd)
      case _ =>
        updateOrCreate(users)
    }
  }

  def removeLocalMemberJoinMessage(convId: ConvId, users: Set[UserId]): Future[Any] = {
    storage.lastLocalMessage(convId, Message.Type.MEMBER_JOIN) flatMap {
      case Some(msg) =>
        val members = msg.members -- users
        warn(l"removeLocalMemberJoinMessage case Some(msg) members.isEmpty:${members.isEmpty}, msg:$msg")
        if (members.isEmpty) {
          updater.deleteMessage(msg)
        } else {
          updater.updateMessage(msg.id) { _.copy(members = members) } // FIXME: possible race condition with addMemberJoinMessage or sync
        }
      case _ =>
        warn(l"removeLocalMemberJoinMessage: no local join message found")
        CancellableFuture.successful(())
    }
  }

  override def addMemberLeaveMessage(convId: ConvId, selfUserId: UserId, user: UserId) = {
    // check if we have local join message with this user and just remove him from the list
    storage.lastLocalMessage(convId, Message.Type.MEMBER_JOIN) flatMap {
      case Some(msg) if msg.members == Set(user) => updater.deleteMessage(msg) // FIXME: race condition
      case Some(msg) if msg.members(user) => updater.updateMessage(msg.id)(_.copy(members = msg.members - user)) // FIXME: race condition
      case _ =>
        // check for local MemberLeave message before creating new one
        def newMessage = MessageData(MessageId(), convId, Message.Type.MEMBER_LEAVE, selfUserId, members = Set(user))
        def update(msg: MessageData) = msg.copy(members = msg.members + user)
        updater.updateOrCreateLocalMessage(convId, Message.Type.MEMBER_LEAVE, update, newMessage)
    }
  }

  override def addOtrVerifiedMessage(convId: ConvId) =
    storage.getLastMessage(convId) flatMap {
      case Some(msg) if msg.msgType == Message.Type.OTR_UNVERIFIED || msg.msgType == Message.Type.OTR_DEVICE_ADDED ||  msg.msgType == Message.Type.OTR_MEMBER_ADDED =>
        verbose(l"addOtrVerifiedMessage, removing previous message: $msg")
        storage.remove(msg.id) map { _ => None }
      case _ =>
        updater.addLocalMessage(MessageData(MessageId(), convId, Message.Type.OTR_VERIFIED, selfUserId), Status.SENT) map { Some(_) }
    }

  override def addOtrUnverifiedMessage(convId: ConvId, users: Seq[UserId], change: VerificationChange): Future[Option[MessageData]] = {
    val msgType = change match {
      case ClientUnverified => Message.Type.OTR_UNVERIFIED
      case MemberAdded => Message.Type.OTR_MEMBER_ADDED
      case _ => Message.Type.OTR_DEVICE_ADDED
    }
    verbose(l"addOtrUnverifiedMessage($convId, $users, $change), msgType is $msgType")
    updater.addLocalSentMessage(MessageData(MessageId(), convId, msgType, selfUserId, members = users.toSet)) map {
      Some(_)
    }
  }

  override def retryMessageSending(conv: ConvId, msgId: MessageId) =
    updater.updateMessage(msgId) { msg =>
      if (msg.state == Status.SENT || msg.state == Status.PENDING) msg
      else msg.copy(state = Status.PENDING)
    }.flatMap {
      case Some(msg) => sync.postMessage(msg.id, conv, msg.editTime, true) map (Some(_))
      case _ => successful(None)
    }

  def messageSent(convId: ConvId, msgId: MessageId, newTime: RemoteInstant, enabled_edit_msg: Boolean = false): Future[Option[MessageData]] = {
    import com.waz.utils.RichFiniteDuration
    updater.updateMessage(msgId) { m => m.copy(state = Message.Status.SENT, time = newTime, expiryTime = m.ephemeral.map(_.fromNow()), enabled_edit_msg = enabled_edit_msg) } andThen {
      case Success(Some(m)) => storage.onMessageSent ! m
    }
  }

  override def addMissedCallMessage(rConvId: RConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]] =
    convs.convByRemoteId(rConvId).flatMap {
      case Some(conv) => addMissedCallMessage(conv.id, from, time)
      case None =>
        warn(l"No conversation found for remote id: $rConvId")
        Future.successful(None)
    }

  override def addMissedCallMessage(convId: ConvId, from: UserId, time: RemoteInstant): Future[Option[MessageData]] =
    updater.addMessage(MessageData(MessageId(), convId, Message.Type.MISSED_CALL, from, time = time))

  override def addSuccessfulCallMessage(convId: ConvId, from: UserId, time: RemoteInstant, duration: FiniteDuration) =
    updater.addMessage(MessageData(MessageId(), convId, Message.Type.SUCCESSFUL_CALL, from, time = time, duration = Some(duration)))

  def messageDeliveryFailed(convId: ConvId, msg: MessageData, error: ErrorResponse): Future[Option[MessageData]] =
    updateMessageState(convId, msg.id, Message.Status.FAILED) andThen {
      case Success(Some(m)) => storage.onMessageFailed ! (m, error)
    }

  override def updateMessageState(convId: ConvId, messageId: MessageId, state: Message.Status) =
    updater.updateMessage(messageId) {
      _.copy(state = state)
    }

  override def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant]): Future[MessageData] = {
    updater.addLocalSentMessage(msg, time)
  }

  override def updateMessageReadState(messageId: MessageId, stateValue: Int) =
    updater.updateMessageReadState(messageId, stateValue)

  override def updateMessageTranslate(messageId: MessageId, content: String) =
    updater.updateMessageTranslateContent(messageId, content)

  override def deleteMessageTranslate(messageId: MessageId) =
    updater.deleteMessageTranslateContent(messageId)


  def markMessageRead(convId: ConvId, id: MessageId) =
    if (!network.isOnlineMode) CancellableFuture.successful(None)
    else
      updater.updateMessage(id) { msg =>
        if (msg.state == Status.FAILED) msg.copy(state = Status.FAILED_READ)
        else msg
      }

  override def retentionPolicy(convData: ConversationData): CancellableFuture[Retention] = {

    val result = Signal.const(Retention.Expiring)
    CancellableFuture.lift(result.head)
  }
}
