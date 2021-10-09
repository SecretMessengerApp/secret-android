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
package com.waz.services.notifications

import android.app.{NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import androidx.core.app.RemoteInput
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.service.ZMessaging
import com.waz.services.FutureService
import com.waz.threading.Threading
import com.waz.utils.{TimedWakeLock, returning}
import com.waz.zclient.ServiceHelper
import com.waz.zclient.log.LogUI._
import com.waz.zclient.notifications.controllers.MessageNotificationsController.toNotificationConvId

import scala.concurrent.Future
import scala.concurrent.duration._

class NotificationsHandlerService extends FutureService with ServiceHelper with DerivedLogTag {

  import NotificationsHandlerService._
  import Threading.Implicits.Background

  override protected lazy val wakeLock = new TimedWakeLock(getApplicationContext, 2.seconds)

  override protected def onIntent(intent: Intent, id: Int): Future[Any] = wakeLock.async {

    val account = Option(intent.getStringExtra(ExtraAccountId)).map(UserId)
    val conversation = Option(intent.getStringExtra(ExtraConvId)).map(ConvId)
    val instantReplyContent = Option(RemoteInput.getResultsFromIntent(intent)).map(_.getCharSequence(InstantReplyKey))

    Option(ZMessaging.currentAccounts) match {
      case Some(accs) =>
        account match {
          case Some(acc) => accs.getZms(acc).flatMap {
            case Some(zms) if ActionClear == intent.getAction =>
              verbose(l"Clearing notifications for account: $acc and conversation:$conversation")
              zms.notifications.dismissNotifications(conversation.map(Set(_)))
            case Some(zms) if ActionQuickReply == intent.getAction =>
              (instantReplyContent, conversation) match {
                case (Some(content), Some(convId)) =>
                  zms.convsUi.sendTextMessage(convId, content.toString, exp = Some(None)).map { _ =>
                    inject[NotificationManager].cancel(toNotificationConvId(acc, convId))
                  }
                case _ =>
                  Future.successful({})
              }
            case _ =>
              Future.successful({})
          }
          case None =>
            warn(l"No account id passed on intent")
            Future.successful({})
        }
      case None =>
        warn(l"No AccountsService available")
        Future.successful({})
    }
  }
}

  object NotificationsHandlerService {
    val ActionClear = "com.wire.CLEAR_NOTIFICATIONS"
    val ActionQuickReply = "com.wire.QUICK_REPLY"
    val ExtraAccountId = "account_id"
    val ExtraConvId = "conv_id"

    val InstantReplyKey = "instant_reply_key"

    def clearNotificationsIntent(userId: UserId, convId: Option[ConvId] = None)(implicit context: Context): PendingIntent = {
      PendingIntent.getService(
        context,
        userId.str.hashCode + convId.map(_.str.hashCode).getOrElse(0),
        returning(
          new Intent(context, classOf[NotificationsHandlerService])
            .setAction(ActionClear)
            .putExtra(ExtraAccountId, userId.str)) { intent =>
          convId.foreach(c => intent.putExtra(ExtraConvId, c.str))
        },
        PendingIntent.FLAG_UPDATE_CURRENT)
    }

    def quickReplyIntent(userId: UserId, convId: ConvId)(implicit context: Context): PendingIntent =
      PendingIntent.getService(
        context,
        (userId.str + convId.str).hashCode,
        new Intent(context, classOf[NotificationsHandlerService])
          .setAction(ActionQuickReply)
          .putExtra(ExtraAccountId, userId.str)
          .putExtra(ExtraConvId, convId.str),
        PendingIntent.FLAG_ONE_SHOT)
  }
