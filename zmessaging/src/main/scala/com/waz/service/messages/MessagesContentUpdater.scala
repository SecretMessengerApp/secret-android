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
import com.waz.api.Message.Status
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging.clock
import com.waz.threading.Threading
import com.waz.utils._
import org.threeten.bp.Instant.now

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._

class MessagesContentUpdater(messagesStorage: MessagesStorage,
                             convs:           ConversationStorage,
                             deletions:       MsgDeletionStorage,
                             prefs:           GlobalPreferences) extends DerivedLogTag {

  import Threading.Implicits.Background

  def getMessage(msgId: MessageId) = messagesStorage.getMessage(msgId)

  def deleteMessage(msg: MessageData) = messagesStorage.delete(msg)

  def deleteMessagesForConversation(convId: ConvId): Future[Unit] = messagesStorage.deleteAll(convId)

  def updateMessage(id: MessageId)(updater: MessageData => MessageData): Future[Option[MessageData]] = messagesStorage.update(id, updater) map {
    case Some((msg, updated)) if msg != updated =>
      verbose(l"updateMessage MessageId:$MessageId, updated:$updated")
      assert(updated.id == id && updated.convId == msg.convId)
      messagesStorage.forceUpdateLastMessage(updated.convId)
      Some(updated)
    case _ =>
      verbose(l"updateMessage case _ =>")
      None
  }

  def updateMessageAction(id: MessageId, actionValue: Int) = {
    updateMessage(id)(_.copy(msgAction = actionValue))
  }

  def updateMessageTranslateContent(id: MessageId, content: String) = {
    updateMessage(id)(_.copy(translateContent = Some(content)))
  }

  def deleteMessageTranslateContent(id: MessageId) = {
    updateMessage(id)(_.copy(translateContent = None))
  }

  def updateMessageActions(forbidDatas: Vector[ForbidData]) = {
    Future.traverse(forbidDatas) {
      forbidData =>
        updateMessageAction(forbidData.message, forbidData.action.serial)
    }
  }

  def updateMessageReadState(id: MessageId, stateValue: Int) = {
    updateMessage(id)(_.copy(readState = stateValue))
  }

  def updateMessageReadStates(readDatas: Seq[Liking]) = {
    Future.traverse(readDatas) {
      readDatas =>
        updateMessageReadState(readDatas.message, readDatas.action.serial)
    }
  }

  def updateMessageReadStates1(forbidDatas: Vector[ForbidData]) = {
    Future.traverse(forbidDatas) {
      forbidData =>
        updateMessageReadState(forbidData.message, MessageActions.Action_MsgRead)
    }
  }

  // removes messages and records deletion
  // this is used when user deletes a message manually (on local or remote device)
  def deleteOnUserRequest(ids: Seq[MessageId]) =
    deletions.insertAll(ids.map(id => MsgDeletion(id, now(clock)))) flatMap { _ =>
      messagesStorage.removeAll(ids)
    }

  /**
    * @param exp ConvExpiry takes precedence over one-time expiry (exp), which takes precedence over the MessageExpiry
    */
  def addLocalMessage(msg: MessageData, state: Status = Status.PENDING, exp: Option[Option[FiniteDuration]] = None, localTime: LocalInstant = LocalInstant.Now) =
    Serialized.future("add local message", msg.convId) {

      def expiration =
        if (MessageData.EphemeralMessageTypes(msg.msgType) || (msg.msgType==Message.Type.TEXTJSON && msg.contentType.fold(false)(_==ServerIdConst.EMOJI_GIF))) //add EMOJI_GIF for Ephemeral
          convs.get(msg.convId).map(_.fold(Option.empty[EphemeralDuration])(_.ephemeralExpiration)).map {
            case Some(ConvExpiry(d))    => Some(d)
            case Some(MessageExpiry(d)) => exp.getOrElse(Some(d))
            case _                      => exp.flatten
          }
        else Future.successful(None)

      for {
        time <- remoteTimeAfterLast(msg.convId) //TODO: can we find a way to save this only on the localTime of the message?
        exp  <- expiration
        m = returning(msg.copy(state = state, time = time, localTime = localTime, ephemeral = exp)) { m =>
          verbose(l"addLocalMessage: $m, exp: $exp")
        }
        res <- messagesStorage.addMessage(m)
      } yield res
    }

  def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant] = None) = Serialized.future("add local message", msg.convId) {
    verbose(l"addLocalSentMessage: $msg")
    time.fold(lastSentEventTime(msg.convId))(Future.successful).flatMap { t =>
      verbose(l"adding local sent message to storage, $t")
      messagesStorage.addMessage(msg.copy(state = Status.SENT, time = t + 1.millis, localTime = LocalInstant.Now))
    }
  }

  def addLocalConnectRequestMsg(convId: ConvId, msg: MessageData): Future[MessageData] = {
    verbose(l"addLocalConnectRequestMsg convId: $convId,msg:$msg")
    messagesStorage.queryMessagesByType(convId, Message.Type.CONNECT_REQUEST).flatMap {
      case msgs: Seq[MessageData] if (msgs.nonEmpty) =>
        verbose(l"addLocalConnectRequestMsg has convId: $convId,msgs.size:${msgs.size}")
        Future.successful {msgs.head}
      case _ =>
        verbose(l"addLocalConnectRequestMsg empty convId: $convId")
        messagesStorage.insert(msg)
    }
  }

  private def remoteTimeAfterLast(convId: ConvId) =
    messagesStorage.getLastMessage(convId).flatMap {
      case Some(msg) => Future successful msg.time
      case _ => convs.get(convId).map(_.fold(RemoteInstant.Epoch)(_.lastEventTime))
    }.flatMap { time =>
      prefs.preference(BackendDrift).apply().map(drift => (time + 1.millis) max LocalInstant.Now.toRemote(drift))
    }

  private def lastSentEventTime(convId: ConvId) =
    messagesStorage.getLastSentMessage(convId) flatMap {
      case Some(msg) => Future successful msg.time
      case _ => convs.get(convId).map(_.fold(RemoteInstant.Epoch)(_.lastEventTime))
    }

  /**
   * Updates last local message or creates new one.
   */
  def updateOrCreateLocalMessage(convId: ConvId, msgType: Message.Type, update: MessageData => MessageData, create: => MessageData) =
    Serialized.future("update-or-create-local-msg", convId, msgType) {
      messagesStorage.lastLocalMessage(convId, msgType) flatMap {
        case Some(msg) => // got local message, try updating
          @volatile var shouldCreate = false
          verbose(l"got local message: $msg, will update")
          updateMessage(msg.id) { msg =>
            if (msg.isLocal) update(msg)
            else { // msg was already synced, need to create new local message
              shouldCreate = true
              msg
            }
          } flatMap { res =>
            verbose(l"shouldCreate: $shouldCreate")
            if (shouldCreate) addLocalMessage(create).map(Some(_))
            else Future.successful(res)
          }
        case _ => addLocalMessage(create).map(Some(_))
      }
    }

  private[service] def addMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] = {
    verbose(l"addMessages: ${msgs.map(_.id)}")

    for {
      toAdd <- skipPreviouslyDeleted(msgs)
      (systemMsgs, contentMsgs) = toAdd.partition(_.isSystemMessage)
      sm <- addSystemMessages(convId, systemMsgs)
      cm <- addContentMessages(convId, contentMsgs)
    } yield sm.toSet ++ cm
  }

  private def skipPreviouslyDeleted(msgs: Seq[MessageData]) =
    deletions.getAll(msgs.map(_.id)) map { deletions =>
      val ds: Set[MessageId] = deletions.collect { case Some(MsgDeletion(id, _)) => id } (breakOut)
      msgs.filter(m => !ds(m.id))
    }

  private def addSystemMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Seq[MessageData]] =
    if (msgs.isEmpty) Future.successful(Seq.empty)
    else {
      messagesStorage.getMessages(msgs.map(_.id): _*) flatMap { prev =>
        val prevIds: Set[MessageId] = prev.collect { case Some(m) => m.id } (breakOut)
        val toAdd = msgs.filterNot(m => prevIds.contains(m.id))

        RichFuture.traverseSequential(toAdd.groupBy(_.id).toSeq) { case (_, ms) =>
          val msg = ms.last
          messagesStorage.hasSystemMessage(convId, msg.time, msg.msgType, msg.userId).flatMap {
            case false =>
              messagesStorage.lastLocalMessage(convId, msg.msgType).flatMap {
                case Some(m) if m.userId == msg.userId =>
                  verbose(l"lastLocalMessage(${msg.msgType}) : $m")

                  if (m.msgType == Message.Type.MEMBER_JOIN || m.msgType == Message.Type.MEMBER_LEAVE) {
                    val remaining = m.members.diff(msg.members)
                    if (remaining.nonEmpty) addMessage(m.copy(id = MessageId(), members = remaining))
                  }
                  messagesStorage.remove(m.id).flatMap(_ => messagesStorage.addMessage(msg.copy(localTime = m.localTime)))
                case res =>
                  verbose(l"lastLocalMessage(${msg.msgType}) returned: $res")
                  messagesStorage.addMessage(msg)
              }.map(Some(_))
            case true =>
              Future.successful(None)
          }
        }.map(_.flatten)
      }
    }

  private def addContentMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]] = {
    // merge data from multiple events in single message
    def merge(msgs: Seq[MessageData]): MessageData = {

      def mergeLocal(m: MessageData, msg: MessageData) =
        msg.copy(id = m.id, localTime = m.localTime)

      def mergeMatching(prev: MessageData, msg: MessageData) = {
        val u = prev.copy(
          msgType       = if (msg.msgType != Message.Type.UNKNOWN) msg.msgType else prev.msgType ,
          time          = if (msg.time.isBefore(prev.time) || prev.isLocal) msg.time else prev.time,
          protos        = prev.protos ++ msg.protos,
          content       = msg.content,
          quote         = msg.quote
        )
        prev.msgType match {
          case Message.Type.RECALLED => prev // ignore updates to already recalled message
          case _ => u
        }
      }

      if (msgs.size == 1) msgs.head
      else msgs.reduce { (prev, msg) =>
        if (prev.isLocal && prev.userId == msg.userId) mergeLocal(prev, msg)
        else if (prev.userId == msg.userId) mergeMatching(prev, msg)
        else {
          warn(l"got message id conflict, will add it with random id, existing: $prev, new: $msg")
          addMessage(msg.copy(id = MessageId()))
          prev
        }
      }
    }

    if (msgs.isEmpty) Future.successful(Set.empty)
    else
      messagesStorage.updateOrCreateAll (
        msgs.groupBy(_.id).mapValues { data =>
          { (prev: Option[MessageData]) => merge(prev.toSeq ++ data) }
        }
      )
  }

  private[service] def addMessage(msg: MessageData): Future[Option[MessageData]] = addMessages(msg.convId, Seq(msg)).map(_.headOption)

  // updates server timestamp for local messages, this should make sure that local messages are ordered correctly after one of them is sent
  def updateLocalMessageTimes(conv: ConvId, prevTime: RemoteInstant, time: RemoteInstant) =
    messagesStorage.findLocalFrom(conv, prevTime) flatMap { local =>
      verbose(l"local messages from $prevTime: $local")
      messagesStorage updateAll2(local.map(_.id), { m =>
        verbose(l"try updating local message time, msg: $m, time: $time")
        if (m.isLocal) m.copy(time = time + (m.time.toEpochMilli - prevTime.toEpochMilli).millis) else m
      })
    }
}
