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

import com.waz.api.Message
import com.waz.api.Message.Type.{MEMBER_JOIN, MEMBER_LEAVE}
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.content._
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.{Forbid, MsgDeleted, MsgEdit, MsgRecall, Reaction, Text}
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.ZMessaging.accountTag
import com.waz.service._
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal}
import org.threeten.bp.Clock

import scala.concurrent.Future

/**
  * A trait representing some controller responsible for displaying notifications in the UI. It is expected that this
  * controller is a global singleton
  */
trait NotificationUiController {
  /**
    * A call to the UI telling it that it has notifications to display. This needs to be a future so that we can wait
    * for the displaying of notifications before finishing the event processing pipeline. Upon completion of the future,
    * we can also mark these notifications as displayed.
    *
    * @return a Future that should enclose the display of notifications to the UI
    */
  def onNotificationsChanged(accountId: UserId, ns: Set[NotificationData]): Future[Unit]

  /**
    * To be called by the UI when any conversations in a given account are visible to the user. When visible, the user
    * should see in some way that they have notifications for that conversation, so we can automatically dismiss the
    * notifications for them.
    */
  def notificationsSourceVisible: Signal[Map[UserId, Boolean]]
}

class NotificationService(selfUserId: UserId,
                          messages: MessagesStorage,
                          storage: NotificationStorage,
                          convs: ConversationStorage,
                          pushService: PushService,
                          uiController: NotificationUiController,
                          userService: UserService,
                          clock: Clock) {

  import Threading.Implicits.Background

  implicit lazy val logTag: LogTag = accountTag[NotificationService](selfUserId)

//  uiController.notificationsSourceVisible { sources =>
//    sources.get(selfUserId).map(Some(_)).foreach(dismissNotifications)
//  }(EventContext.Global) //TODO account event context

  /**
    * Removes all notifications that are being displayed for the given set of input conversations and updates the UI
    * with all remaining notifications
    *
    * @param forConvs the conversations for which to remove notifications, or None if all notifications should be cleared.
    */
  def dismissNotifications(forConvs: Option[Set[ConvId]] = None): Future[Unit] = {
    verbose(l"dismissNotifications: $forConvs")
    for {
      nots <- storage.list().map(_.toSet)
      toRemove = forConvs match {
        case None => nots
        case Some(convs) => nots.filter(n => convs.contains(n.conv))
      }
      _ <- if (toRemove.nonEmpty) storage.removeAll(toRemove.map(_.id)) else Future.successful({})
      _ <- if (toRemove.nonEmpty) pushNotificationsToUi() else Future.successful({})
    } yield {}
  }

  val messageNotificationEventsStage = EventScheduler.Stage[ConversationEvent]({ (c, events) =>
    verbose(l"notification init c:${c.str}  events:${events.size}")
    if (events.nonEmpty) {
      for {
        Some(conv) <- convs.getByRemoteId(c)
        (undoneLikes, likes) <- getReactionChanges(conv, events)
        (unForbid, forbid) <- getForbidChanges(conv, events)
        currentNotifications <- storage.list().map(_.toSet)
        msgNotifications <- getMessageNotifications(c, conv, events)
        (afterEditsApplied, beforeEditsApplied) = applyEdits(currentNotifications ++ msgNotifications, events)
        deleted = events.collect {
          case GenericMessageEvent(_, _, _, GenericMessage(_, MsgDeleted(_, msg)), name, asset) => NotId(msg)
          case GenericMessageEvent(_, _, _, GenericMessage(_, MsgRecall(msg)), name, asset) => NotId(msg)
        }.toSet
        toShow = (afterEditsApplied ++ likes ++ forbid).filterNot(n => (undoneLikes ++ deleted ++ unForbid).contains(n.id))
        toRemove = undoneLikes ++ beforeEditsApplied ++ deleted ++ unForbid
        _ = verbose(l"notification pushNotificationsToUi toShow:${toShow.size}  toRemove:${toRemove.size}")
        _ <- storage.removeAll(toRemove)
        _ <- storage.insertAll(toShow)
        _ = {
          if (toRemove.nonEmpty || toShow.nonEmpty) pushNotificationsToUi()
        }
      } yield {}
    } else Future.successful({})
  })

  val connectionNotificationEventStage = EventScheduler.Stage[UserConnectionEvent]({ (_, events) =>
    if (events.nonEmpty) {
      val toShow = events.collect {
        case UserConnectionEvent(_, _, userId, msg, ConnectionStatus.PendingFromOther, time, name) =>
          NotificationData(NotId(CONNECT_REQUEST, userId), msg.getOrElse(""), ConvId(userId.str), userId, CONNECT_REQUEST, ConversationType.Unknown.id, time)
        case UserConnectionEvent(_, _, userId, _, ConnectionStatus.Accepted, time, name) =>
          NotificationData(NotId(CONNECT_ACCEPTED, userId), "", ConvId(userId.str), userId, CONNECT_ACCEPTED, ConversationType.Unknown.id, time)
//        case ContactJoinEvent(userId, _) =>
//          verbose(l"ContactJoinEvent")
//          NotificationData(NotId(CONTACT_JOIN, userId), "", ConvId(userId.str), userId, CONTACT_JOIN)
      }

      if (toShow.nonEmpty) {
        storage.insertAll(toShow).map(_ => pushNotificationsToUi())
      } else {
        Future.successful({})
      }
    } else Future.successful({})
  })

  private def pushNotificationsToUi(): Future[Unit] = {
    def shouldShowNotification(self: UserData,
                               n: NotificationData,
                               conv: ConversationData,
                               notificationSourceVisible: Map[UserId, Boolean]): Boolean = {
      // broken down for better readability
      val appAllows =
        notificationSourceVisible.getOrElse(self.id,false) &&
        n.user != self.id &&
          conv.lastRead.isBefore(n.time) &&
          conv.cleared.forall(_.isBefore(n.time))
      val isReplyOrMention = n.isSelfMentioned || n.isReply
      if (!appAllows) {
        false
      } else {
        if (self.availability == Availability.Away) {
          false
        } else if (self.availability == Availability.Busy) {
          if (!isReplyOrMention) {
            false
          } else {
            conv.muted.isAllAllowed || conv.muted.onlyMentionsAllowed
          }
        } else {
          conv.muted.isAllAllowed || (conv.muted.onlyMentionsAllowed && isReplyOrMention)
        }
      }
    }

    verbose(l"notification pushNotificationsToUi")

    for {
      Some(self) <- userService.getSelfUser
      toShow <- storage.list()
      notificationSourceVisible <- uiController.notificationsSourceVisible.head
      convs <- convs.getAll(toShow.map(_.conv).toSet)
        .map(_.collect { case Some(c) => c.id -> c }.toMap)
      (show, ignore) = toShow.partition { n =>
        shouldShowNotification(self, n, convs(n.conv), notificationSourceVisible)
      }
      _ = verbose(l"show: ${show.size}, ignore: ${ignore.size}")
      _ <- if (show.nonEmpty) uiController.onNotificationsChanged(self.id, show.toSet) else Future.successful({})
      _ <- storage.insertAll(show.map(_.copy(hasBeenDisplayed = true)))
      _ <- storage.removeAll(ignore.map(_.id))
    } yield {}

  }

  def isShowNotify(conv: ConversationData, msg: MessageData): Boolean = {
    val convType = conv.convType.id
    val isShow = msg.msgType match {
      case MEMBER_JOIN | MEMBER_LEAVE =>
        if (convType == ConversationType.Group.id){
          conv.view_chg_mem_notify
        } else if(convType == ConversationType.ThousandsGroup.id){
          false
        } else {
          true
        }
      case _ =>
        true
    }
    isShow
  }

  private def getMessageNotifications(rConvId: RConvId, conv: ConversationData, events: Vector[Event]) = {
    val eventTimes = events.collect { case e: ConversationEvent => e.time }
    if (eventTimes.nonEmpty) {
      for {
        //        Some(conv) <- convs.getByRemoteId(rConvId)
        drift <- pushService.beDrift.head
        msgs <- messages.findMessagesFrom(conv.id, eventTimes.min).map(_.filterNot(_.userId == selfUserId))
        quoteIds <- messages
          .getAll(msgs.filter(!_.hasMentionOf(selfUserId)).flatMap(_.quote.map(_.message)).toSet)
          .map(_.flatten.filter(_.userId == selfUserId).map(_.id).toSet)
      } yield {
        msgs.flatMap { msg =>

          import Message.Type._
          val tpe = msg.msgType match {
            case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA => Some(NotificationType.TEXT)
            case TEXTJSON => Some(NotificationType.TEXTJSON)
            case KNOCK => Some(NotificationType.KNOCK)
            case ASSET => Some(NotificationType.ASSET)
            case LOCATION => Some(NotificationType.LOCATION)
            case RENAME => Some(NotificationType.RENAME)
            case MISSED_CALL => Some(NotificationType.MISSED_CALL)
            case ANY_ASSET => Some(NotificationType.ANY_ASSET)
            case AUDIO_ASSET => Some(NotificationType.AUDIO_ASSET)
            case VIDEO_ASSET => Some(NotificationType.VIDEO_ASSET)
            case MEMBER_JOIN =>
              if (msg.members == Set(msg.userId)) None // ignoring auto-generated member join event when user accepts connection
              else Some(NotificationType.MEMBER_JOIN)
            case MEMBER_LEAVE => Some(NotificationType.MEMBER_LEAVE)
            case _ => None
          }

          tpe.map { tp =>
            verbose(l"quoteIds: $quoteIds, message: $msg, tpe: $tpe")
            NotificationData(
              id = NotId(msg.id),
              msg = if (msg.isEphemeral) "" else msg.contentString, msg.convId,
              user = msg.userId,
              msgType = tp,
              convType = conv.convType.id,
              //TODO do we ever get RemoteInstant.Epoch?
              time = if (msg.time == RemoteInstant.Epoch) msg.localTime.toRemote(drift) else msg.time,
              ephemeral = msg.isEphemeral,
              isSelfMentioned = msg.mentions.flatMap(_.userId).contains(selfUserId),
              isReply = msg.quote.map(_.message).exists(quoteIds(_)),
              isShowNotify = isShowNotify(conv, msg)
            )
          }
        }.toSet
      }
    } else Future.successful(Set.empty[NotificationData])
  }

  private def applyEdits(currentNotifications: Set[NotificationData], events: Vector[Event]) = {
    val edits = events
      .collect { case GenericMessageEvent(_, _, _, GenericMessage(newId, MsgEdit(id, Text(msg, _, _, _))), name, asset) => (id, (newId, msg)) }
      .toMap

    val (afterEditsApplied, beforeEditsApplied) = edits.foldLeft((currentNotifications.map(n => (n.id, n)).toMap, Set.empty[NotId])) {
      case ((edited, toRemove), (oldId, (newId, newContent))) =>
        edited.get(NotId(oldId.str)) match {
          case Some(toBeEdited) =>
            val newNotId = NotId(newId.str)
            val updated = toBeEdited.copy(id = newNotId, msg = newContent)

            ((edited - toBeEdited.id) + (newNotId -> updated), toRemove + toBeEdited.id)

          case _ => (edited, toRemove)
        }
    }

    (afterEditsApplied.values.toSet, beforeEditsApplied)
  }

  private def getReactionChanges(conv: ConversationData, events: Vector[Event]) = {
    val reactions = events.collect {
      case GenericMessageEvent(_, time, from, GenericMessage(_, Reaction(msg, action)), name, asset) if from != selfUserId => Liking(msg, from, time, action)
    }

    messages.getAll(reactions.map(_.message).toSet).map(_.flatten).map { msgs =>

      val msgsById = msgs.map(m => m.id -> m).toMap
      val convsByMsg = msgs.iterator.by[MessageId, Map](_.id).mapValues(_.convId)
      val myMsgs = msgs.collect { case m if m.userId == selfUserId => m.id }.toSet

      val (toRemove, toAdd) =
        reactions
          .filter(_.action != Liking.Action.MsgRead)
          .groupBy(r => (r.message, r.user))
          .collect { case ((messageId, _), likeUnlikeEvents) if myMsgs.contains(messageId) && likeUnlikeEvents.nonEmpty => likeUnlikeEvents.maxBy(_.timestamp) }
          .toSet
          .partition(_.action == Liking.Action.Unlike)

      (toRemove.map(r => NotId(r.id)), toAdd.map { r =>
        val msg = msgsById(r.message)
        NotificationData(
          id = NotId(r.id),
          msg = msg.contentString,
          conv = convsByMsg(r.message),
          user = r.user,
          msgType = LIKE,
          convType = conv.convType.id,
          time = r.timestamp,
          likedContent = Some(msg.msgType match {
            case Message.Type.ASSET => LikedContent.PICTURE
            case Message.Type.TEXT |
                 Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
            case _ => LikedContent.OTHER
          }),
          isShowNotify = isShowNotify(conv, msg)
        )
      })
    }
  }

  private def getForbidChanges(conv: ConversationData, events: Vector[Event]) = {
    val forbids = events.collect {
      case GenericMessageEvent(_, time, from, GenericMessage(_, Forbid(msg, optName, types, action)), name, asset) if from != selfUserId => ForbidData(msg, from, optName, time, types, action)
    }

    messages.getAll(forbids.map(_.message).toSet).map(_.flatten).map { msgs =>

      val msgsById = msgs.map(m => m.id -> m).toMap
      val convsByMsg = msgs.iterator.by[MessageId, Map](_.id).mapValues(_.convId)
      val myMsgs = msgs.collect { case m if m.userId == selfUserId => m.id }.toSet

      val (toUnForbid, toForbid) =
        forbids
          .filter(_.types == ForbidData.Types.Forbid)
          .groupBy(r => (r.message, r.types))
          .collect { case ((messageId, _), forbidEvents) if myMsgs.contains(messageId) && forbidEvents.nonEmpty => forbidEvents.maxBy(_.timestamp) }
          .toSet
          .partition(_.action != ForbidData.Action.Forbid)

      def getNotId(id: (MessageId, ForbidData.Types)) = {
        s"$LIKE-${id._1.str}-${id._2.serial}"
      }

      (toUnForbid.map(r => NotId(getNotId(r.id))), toForbid.map { r =>
        val msg = msgsById(r.message)
        NotificationData(
          id = NotId(getNotId(r.id)),
          msg = msg.contentString,
          conv = convsByMsg(r.message),
          user = r.userId,
          msgType = LIKE,
          convType = conv.convType.id,
          time = r.timestamp,
          likedContent = Some(msg.msgType match {
            case Message.Type.ASSET => LikedContent.PICTURE
            case Message.Type.TEXT |
                 Message.Type.TEXT_EMOJI_ONLY => LikedContent.TEXT_OR_URL
            case _ => LikedContent.OTHER
          }),
          isShowNotify = isShowNotify(conv, msg)
        )
      })
    }
  }
}
