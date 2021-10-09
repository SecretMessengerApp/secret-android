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
package com.waz.zclient.notifications.controllers

import java.io.{File, FileNotFoundException, FileOutputStream, IOException}

import android.app.{Notification, NotificationChannel, NotificationChannelGroup, NotificationManager}
import android.content.{ContentValues, Context}
import android.database.sqlite.SQLiteException
import android.graphics.{Color, Typeface}
import android.net.Uri
import android.os.{Build, Environment}
import android.provider.MediaStore
import android.text.style.{ForegroundColorSpan, StyleSpan}
import android.text.{SpannableString, Spanned}
import androidx.core.app.{NotificationCompat, RemoteInput}
import androidx.core.app.NotificationCompat.Style
import com.waz.content.Preferences.PrefKey
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{ConvId, UserId}
import com.waz.service.AccountsService
import com.waz.services.notifications.NotificationsHandlerService
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.Intents.{CallIntent, QuickReplyIntent}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.{MessageNotificationsChannelId, PingNotificationsChannelId}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.{ResString, RingtoneUtils, format}
import com.waz.zclient.{Injectable, Injector, Intents, R}

import scala.collection.JavaConverters._
import scala.concurrent.Future

case class Span(style: Int, range: Int, offset: Int = 0)

object Span {
  val ForegroundColorSpanBlack = 1
  val ForegroundColorSpanGray  = 2
  val StyleSpanBold            = 3
  val StyleSpanItalic          = 4

  val HeaderRange = 1
  val BodyRange   = 2
}

case class SpannableWrapper(header: ResString,
                            body: ResString,
                            spans: List[Span],
                            separator: String) {

  override def toString: String =
    format(className = "SpannableWrapper", oneLiner = true,
      "header"    -> Some(header),
      "body"      -> Some(body),
      "spans"     -> (if (spans.nonEmpty) Some(spans) else None),
      "separator" -> (if (separator.nonEmpty) Some(separator) else None)
    )

  def build(implicit cxt: Context): SpannableString = {
    val headerStr = header.resolve
    val bodyStr = body.resolve
    val wholeStr = headerStr + separator + bodyStr

    def range(span: Span) = span.range match {
      case Span.HeaderRange => (0, headerStr.length)
      case Span.BodyRange   => (headerStr.length + span.offset, wholeStr.length)
    }

    def style(span: Span) = span.style match {
      case Span.ForegroundColorSpanBlack => new ForegroundColorSpan(Color.BLACK)
      case Span.ForegroundColorSpanGray  => new ForegroundColorSpan(Color.GRAY)
      case Span.StyleSpanBold            => new StyleSpan(Typeface.BOLD)
      case Span.StyleSpanItalic          => new StyleSpan(Typeface.ITALIC)
    }

    returning(new SpannableString(wholeStr)) { ss =>
      spans.map(span => (style(span), range(span))).foreach {
        case (style, (start, end)) if end > start => ss.setSpan(style, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        case _ =>
      }
    }
  }

  def +(span: Span): SpannableWrapper = copy(spans = this.spans ++ List(span))

  def +(sw: SpannableWrapper): SpannableWrapper = {
    val spans     = this.spans ++ sw.spans.map(span => if (span.range == Span.HeaderRange) span.copy(range = Span.BodyRange) else span)
    val body      = if (sw.header != ResString.Empty) sw.header else sw.body
    copy(spans = spans, body = body)
  }
}

object SpannableWrapper {
  def apply(header: ResString): SpannableWrapper =
    SpannableWrapper(header = header, body = ResString.Empty, spans = List.empty, separator = "")
  def apply(header: ResString, spans: List[Span]): SpannableWrapper =
    SpannableWrapper(header = header, body = ResString.Empty, spans = spans, separator = "")

  val Empty: SpannableWrapper = SpannableWrapper(ResString.Empty)
}

case class StyleBuilder(style: Int,
                        title: SpannableWrapper,
                        summaryText: Option[String] = None,
                        bigText: Option[SpannableWrapper] = None,
                        lines: List[SpannableWrapper] = List.empty) {
  override def toString: String =
    format(className = "StyleBuilder", oneLiner = true,
      "style"       -> Some(style),
      "title"       -> Some(title),
      "summaryText" -> summaryText,
      "bigText"     -> bigText,
      "lines"       -> (if (lines.nonEmpty) Some(lines) else None)
    )

  def build(implicit cxt: Context): Style = style match {
    case StyleBuilder.BigText =>
      returning(new NotificationCompat.BigTextStyle) { bts =>
        bts.setBigContentTitle(title.build)
        summaryText.foreach(bts.setSummaryText)
        bigText.map(_.build).foreach(bts.bigText(_))
      }
    case StyleBuilder.Inbox =>
      returning(new NotificationCompat.InboxStyle) { is =>
        is.setBigContentTitle(title.build)
        summaryText.foreach(is.setSummaryText)
        lines.map(_.build).foreach(is.addLine(_))
      }
    }
}

object StyleBuilder {
  val BigText = 1
  val Inbox   = 2
}

case class NotificationProps(accountId:                UserId,
                             when:                     Option[Long] = None,
                             showWhen:                 Option[Boolean] = None,
                             category:                 Option[String] = None,
                             priority:                 Option[Int] = None,
                             smallIcon:                Option[Int] = None,
                             contentTitle:             Option[SpannableWrapper] = None,
                             contentText:              Option[SpannableWrapper] = None,
                             style:                    Option[StyleBuilder] = None,
                             groupSummary:             Option[Boolean] = None,
                             group:                    Option[UserId] = None,
                             openAccountIntent:        Option[UserId] = None,
                             clearNotificationsIntent: Option[(UserId, Option[ConvId])] = None,
                             openConvIntent:           Option[(UserId, ConvId, Int)] = None,
                             contentInfo:              Option[String] = None,
                             color:                    Option[Int] = None,
                             vibrate:                  Option[Array[Long]] = None,
                             autoCancel:               Option[Boolean] = None,
                             sound:                    Option[Uri] = None,
                             onlyAlertOnce:            Option[Boolean] = None,
                             lights:                   Option[(Int, Int, Int)] = None,
                             largeIcon:                Option[Bitmap] = None,
                             action1:                  Option[(UserId, ConvId, Int, Boolean)] = None,
                             action2:                  Option[(UserId, ConvId, Int, Boolean)] = None,
                             lastIsPing:               Option[Boolean] = None
                            ) {
  override def toString: String =
    format(className = "NotificationProps", oneLiner = false,
      "when"                     -> when,
      "showWhen"                 -> showWhen,
      "category"                 -> category,
      "priority"                 -> priority,
      "smallIcon"                -> smallIcon,
      "contentTitle"             -> contentTitle,
      "contentText"              -> contentText,
      "style"                    -> style,
      "groupSummary"             -> groupSummary,
      "openAccountIntent"        -> openAccountIntent,
      "clearNotificationsIntent" -> clearNotificationsIntent,
      "openConvIntent"           -> openConvIntent,
      "contentInfo"              -> contentInfo,
      "vibrate"                  -> vibrate,
      "autoCancel"               -> autoCancel,
      "sound"                    -> sound,
      "onlyAlertOnce"            -> onlyAlertOnce,
      "lights"                   -> lights,
      "largeIcon"                -> largeIcon,
      "action1"                  -> action1,
      "action2"                  -> action2,
      "lastIsPing"               -> lastIsPing
    )

  def build()(implicit cxt: Context): Notification = {
    val channelId = if (lastIsPing.contains(true)) PingNotificationsChannelId(accountId) else MessageNotificationsChannelId(accountId)
    val builder = new NotificationCompat.Builder(cxt, channelId)

    when.foreach(builder.setWhen)
    showWhen.foreach(builder.setShowWhen)
    category.foreach(builder.setCategory)
    priority.foreach(builder.setPriority)
    smallIcon.foreach(builder.setSmallIcon)
    contentTitle.map(_.build).foreach(builder.setContentTitle)
    contentText.map(_.build).foreach(builder.setContentText)
    style.map(_.build).foreach(builder.setStyle)
    groupSummary.foreach { summary =>
      builder.setGroupSummary(summary)
      builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
    }
    group.foreach(accountId => builder.setGroup(accountId.str))

    openAccountIntent.foreach(userId => builder.setContentIntent(Intents.OpenAccountIntent(userId)))

    openConvIntent.foreach {
      case (accountId, convId, requestBase) => builder.setContentIntent(Intents.OpenConvIntent(accountId, convId, requestBase))
    }

    clearNotificationsIntent.foreach { case (uId, convId) =>
      builder.setDeleteIntent(NotificationsHandlerService.clearNotificationsIntent(uId, convId))
    }

    contentInfo.foreach(builder.setContentInfo)
    color.foreach(builder.setColor)
    vibrate.foreach(builder.setVibrate)
    autoCancel.foreach(builder.setAutoCancel)
    sound.foreach(builder.setSound)
    onlyAlertOnce.foreach(builder.setOnlyAlertOnce)
    lights.foreach { case (c, on, off) => builder.setLights(c, on, off) }
    largeIcon.foreach(bmp => builder.setLargeIcon(bmp))

    action1.map {
      case (userId, convId, requestBase, _) =>
        new NotificationCompat.Action.Builder(R.drawable.ic_action_call, getString(R.string.notification__action__call), CallIntent(userId, convId, requestBase)).build()
    }.foreach(builder.addAction)

    action2.map {
      case (userId, convId, requestBase, bundleEnabled) => createQuickReplyAction(userId, convId, requestBase, bundleEnabled)
    }.foreach(builder.addAction)

    builder.build()
  }

  private def createQuickReplyAction(userId: UserId, convId: ConvId, requestCode: Int, bundleEnabled: Boolean)(implicit cxt: Context) = {
    if (bundleEnabled) {
      val remoteInput = new RemoteInput.Builder(NotificationsHandlerService.InstantReplyKey)
        .setLabel(getString(R.string.notification__action__reply))
        .build
      new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), NotificationsHandlerService.quickReplyIntent(userId, convId))
        .addRemoteInput(remoteInput)
        .setAllowGeneratedReplies(true)
        .build()
    } else
      new NotificationCompat.Action.Builder(R.drawable.ic_action_reply, getString(R.string.notification__action__reply), QuickReplyIntent(userId, convId, requestCode)).build()
  }
}

trait NotificationManagerWrapper {
  def getActiveNotificationIds: Seq[Int]
  def showNotification(id: Int, notificationProps: NotificationProps): Unit
  def cancelNotifications(ids: Set[Int]): Unit
}

object NotificationManagerWrapper {

  val IncomingCallNotificationsChannelId = "INCOMING_CALL_NOTIFICATIONS_CHANNEL_ID"
  val OngoingNotificationsChannelId      = "STICKY_NOTIFICATIONS_CHANNEL_ID"

  def PingNotificationsChannelId(userId: UserId)         = s"PINGS_NOTIFICATIONS_CHANNEL_ID_${userId.str.hashCode}"
  def MessageNotificationsChannelId(userId: UserId)      = s"MESSAGE_NOTIFICATIONS_CHANNEL_ID_${userId.str.hashCode}"

  case class ChannelGroup(id: String, name: String, channels: Set[ChannelInfo])

  case class ChannelInfo(id: String, name: String, description: String, sound: Uri, vibration: Boolean)
  object ChannelInfo {
    def apply(id: String, name: Int, description: Int, sound: Uri, vibration: Boolean)(implicit cxt: Context): ChannelInfo = ChannelInfo(id, getString(name), getString(description), sound, vibration)
  }

  class AndroidNotificationsManager(notificationManager: NotificationManager)(implicit inj: Injector, cxt: Context, eventContext: EventContext)
    extends NotificationManagerWrapper with Injectable with DerivedLogTag {

    val accountChannels = inject[AccountsService].accountManagers.flatMap(ams => Signal.sequence(ams.map { am =>

      def getSound(pref: PrefKey[String], default: Int): Future[Uri] =
        am.userPrefs.preference(pref).apply().map {
          case ""  => RingtoneUtils.getUriForRawId(cxt, default)
          case str => Uri.parse(str)
        } (Threading.Ui)

      for {
        msgSound <- Signal.future(getSound(UserPreferences.TextTone, R.raw.new_message_gcm))
        pingSound <- Signal.future(getSound(UserPreferences.PingTone, R.raw.ping_from_them))
        vibration <- Signal.future(am.userPrefs.preference(UserPreferences.VibrateEnabled).apply())
        channel <- am.storage.usersStorage.signal(am.userId).map(user => ChannelGroup(user.id.str, user.getDisplayName, Set(
            ChannelInfo(MessageNotificationsChannelId(am.userId), R.string.message_notifications_channel_name, R.string.message_notifications_channel_description, msgSound, vibration),
            ChannelInfo(PingNotificationsChannelId(am.userId), R.string.ping_notifications_channel_name, R.string.ping_notifications_channel_description, pingSound, vibration)
          )))
      } yield channel
    }.toSeq:_*))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

      addToExternalNotificationFolder(R.raw.new_message_gcm, getString(R.string.wire_notification_name))
      addToExternalNotificationFolder(R.raw.ping_from_them, getString(R.string.wire_ping_name))

      accountChannels { channels =>

        notificationManager.getNotificationChannels.asScala.filter { ch =>
          !channels.flatMap(_.channels).exists(_.id == ch.getId) && !Set(OngoingNotificationsChannelId, IncomingCallNotificationsChannelId).contains(ch.getId)
        }.foreach(ch => notificationManager.deleteNotificationChannel(ch.getId))

        notificationManager.getNotificationChannelGroups.asScala.filter { ch =>
          !channels.map(_.id).contains(ch.getId)
        }.foreach(ch => notificationManager.deleteNotificationChannelGroup(ch.getId))

        channels.foreach {
          case ChannelGroup(groupId, groupName, channelInfos) =>
            notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(groupId, groupName))
            channelInfos.foreach {
              case ChannelInfo(id, name, description, sound, vibration) =>
                notificationManager.createNotificationChannel(
                  returning(new NotificationChannel(id, name, NotificationManager.IMPORTANCE_MAX)) { ch =>
                    ch.setDescription(description)
                    ch.setShowBadge(true)
                    ch.enableVibration(vibration)
                    ch.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                    ch.setSound(sound, Notification.AUDIO_ATTRIBUTES_DEFAULT)
                    ch.setGroup(groupId)
                    ch.enableLights(true)
                  })
          }
        }
      }

      notificationManager.createNotificationChannel(
        returning(new NotificationChannel(OngoingNotificationsChannelId, getString(R.string.ongoing_channel_name), NotificationManager.IMPORTANCE_LOW)) { ch =>
          ch.setDescription(getString(R.string.ongoing_channel_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
        })

      notificationManager.createNotificationChannel(
        returning(new NotificationChannel(IncomingCallNotificationsChannelId, getString(R.string.incoming_call_notifications_channel_name), NotificationManager.IMPORTANCE_HIGH)) { ch =>
          ch.setDescription(getString(R.string.ongoing_channel_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
        })
    }

    def showNotification(id: Int, notificationProps: NotificationProps) = {
      verbose(l"build: $id")
      notificationManager.notify(id, notificationProps.build())
    }

    override def getActiveNotificationIds: Seq[Int] =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        notificationManager.getActiveNotifications.toSeq.map(_.getId)
      else {
        warn(l"Tried to access method getActiveNotifications from api level: ${Build.VERSION.SDK_INT}")
        Seq.empty
      }

    def getNotificationChannel(channelId: String) = notificationManager.getNotificationChannel(channelId)

    private def addToExternalNotificationFolder(rawId: Int, name: String) =
      Option(cxt.getExternalFilesDir(Environment.DIRECTORY_NOTIFICATIONS)).foreach { dir =>
        val uri      = MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        val query    = s"${MediaStore.MediaColumns.DATA} LIKE '%$name%'"
        val toneFile = new File(s"${dir.getAbsolutePath}/$name.ogg")
        if (toneFile.exists()) {
          val contentValues = returning(new ContentValues) { values =>
            values.put(MediaStore.MediaColumns.DATA, toneFile.getAbsolutePath)
            values.put(MediaStore.MediaColumns.TITLE, name)
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/ogg")
            values.put(MediaStore.MediaColumns.SIZE, toneFile.length.toInt.asInstanceOf[Integer])
            values.put(MediaStore.Audio.AudioColumns.IS_RINGTONE, true)
            values.put(MediaStore.Audio.AudioColumns.IS_NOTIFICATION, true)
            values.put(MediaStore.Audio.AudioColumns.IS_ALARM, true)
            values.put(MediaStore.Audio.AudioColumns.IS_MUSIC, false)
          }

          try {
            val fIn = cxt.getResources.openRawResource(rawId)
            val buffer = new Array[Byte](fIn.available)
            fIn.read(buffer)
            fIn.close()

            returning(new FileOutputStream(toneFile)) { fOut =>
              fOut.write(buffer)
              fOut.flush()
              fOut.close()
            }

            // TODO: On some devices (Redmi 6A, Xperia X Compact) the query causes SQLiteException.
            // test on one of these devices and find out why.
            val cursor = try {
              Option(cxt.getContentResolver.query(uri, null, query, null, null))
            } catch {
              case ex: SQLiteException =>
                error(l"query to access the media store failed; uri: $uri, query: ${redactedString(query)}", ex)
                None
            }

            if (cursor.forall(_.getCount == 0)) cxt.getContentResolver.insert(uri, contentValues)
            cursor.foreach(_.close())
          } catch {
            case ex: FileNotFoundException =>
              error(l"File not found: $toneFile")
            case ex: IOException =>
              error(l"query to access the media store failed; uri: $uri, query: ${redactedString(query)}", ex)
          }
        } else {
          error(l"File not found: $toneFile")
        }
      }

    override def cancelNotifications(ids: Set[Int]): Unit = {
      verbose(l"cancel: $ids")
      ids.foreach(notificationManager.cancel)
    }
  }
}

