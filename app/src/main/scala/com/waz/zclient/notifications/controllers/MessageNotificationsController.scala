/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType._
import com.waz.bitmap.BitmapUtils
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.verbose
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.images.ImageLoader
import com.waz.service.push.NotificationUiController
import com.waz.service.{AccountsService, UiLifeCycle}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.ui.MemoryImageCache.BitmapRequest
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.Bitmap
import com.waz.zclient.WireApplication._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils.{getInt, getIntArray, toPx}
import com.waz.zclient.utils.{ResString, RingtoneUtils}
import com.waz.zclient.{BuildConfig, Injectable, Injector, R}
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageNotificationsController(bundleEnabled: Boolean = Build.VERSION.SDK_INT > Build.VERSION_CODES.M,
                                     applicationId: String = BuildConfig.APPLICATION_ID)
                                    (implicit inj: Injector, cxt: Context, eventContext: EventContext)
  extends Injectable
    with NotificationUiController
    with DerivedLogTag {

  import MessageNotificationsController._
  import Threading.Implicits.Background

  private lazy val notificationManager = inject[NotificationManagerWrapper]

  private lazy val selfId = inject[Signal[UserId]]
  private lazy val soundController = inject[SoundController]
  private lazy val convController = inject[ConversationController]

  override val notificationsSourceVisible: Signal[Map[UserId, Boolean]] =
    for {
      accs <- inject[Signal[AccountsService]].flatMap(_.accountsWithManagers)
      uiActive <- inject[UiLifeCycle].uiActive
      selfId <- selfId
    } yield {
      accs.map { accId =>
        verbose(l"notificationsSourceVisible accId: $accId, selfId: $selfId, uiActive : $uiActive")
        accId ->
          !uiActive
      }.toMap
    }

  override def onNotificationsChanged(accountId: UserId, nots: Set[NotificationData]): Future[Unit] = {
    val toShow: Set[NotificationData] = nots.filter(_.isShowNotify)

    verbose(l"onNotificationsChanged: $accountId, nots: ${nots.size}, toShow:${toShow.size}")
    for {
      summaries <- createSummaryNotificationProps(accountId, toShow).map(_.map(p => (toNotificationGroupId(accountId), p)))
      convNots <- createConvNotifications(accountId, toShow).map(_.toMap)
      _ <- Threading.Ui {
        val nots = convNots ++ summaries
        if (nots.isEmpty) {
          notificationManager.cancelNotifications(Set(toNotificationGroupId(accountId), toEphemeralNotificationGroupId(accountId)))
        } else {
          nots.foreach {
            case (id, props) =>
              notificationManager.showNotification(id, props)
            case (_, _) =>
          }
        }
      }.future
    } yield {}
  }

  def showAppNotification(title: ResString, body: ResString): Future[Unit] = {
    val contentTitle = SpannableWrapper(title, List(Span(Span.StyleSpanBold, Span.HeaderRange)))
    val contentText = SpannableWrapper(
      header = ResString(""),
      body = body,
      spans = List(Span(Span.ForegroundColorSpanBlack, Span.HeaderRange)),
      separator = ""
    )
    for {
      accountId <- selfId.head
    } yield {
      val props = NotificationProps(
        accountId,
        when = Some(Instant.now().toEpochMilli),
        showWhen = Some(true),
        category = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon = Some(R.drawable.ic_menu_logo),
        openAccountIntent = Some(accountId),
        color = None,
        contentTitle = Some(contentTitle),
        contentText = Some(contentText),
        style = Some(StyleBuilder(StyleBuilder.BigText, title = contentTitle, bigText = Some(contentText))),
        autoCancel = Some(true)
      )
      notificationManager.showNotification(accountId.hashCode(), props)
    }
  }

  private def createSummaryNotificationProps(userId: UserId, nots: Set[NotificationData]) = {
    verbose(l"createSummaryNotificationProps: $userId, ${nots.size}")
    if (nots.nonEmpty && bundleEnabled){
      Future.successful(Some(NotificationProps(userId,
        when = Some(nots.minBy(_.time.instant).time.instant.toEpochMilli),
        showWhen = Some(true),
        category = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon = Some(R.drawable.ic_menu_logo),
        groupSummary = Some(true),
        group = Some(userId),
        openAccountIntent = Some(userId),
        contentInfo = None,
        color = None,
        autoCancel = Some(true)
      )))
    } else Future.successful(None)
  }

  private def createConvNotifications(accountId: UserId, nots: Set[NotificationData]) = {
    verbose(l"createConvNotifications  accountId: $accountId, nots: $nots")
    if (nots.nonEmpty) {
      val (ephemeral, normal) = nots.toSeq.sortBy(_.time).partition(_.ephemeral)

      val groupedConvs =
        if (bundleEnabled)
          normal.groupBy(_.conv).map {
            case (convId, ns) => toNotificationConvId(accountId, convId) -> ns
          } ++ ephemeral.groupBy(_.conv).map {
            case (convId, ns) => toEphemeralNotificationConvId(accountId, convId) -> ns
          }
        else
          Map(toNotificationGroupId(accountId) -> normal, toEphemeralNotificationGroupId(accountId) -> ephemeral)

      Future.sequence(groupedConvs.filter(_._2.nonEmpty).map {
        case (notId, ns) =>
          for {
            commonProps <- commonNotificationProperties(ns, accountId)
            specificProps <-
              if (ns.size == 1) singleNotificationProperties(commonProps, accountId, ns.head)
              else multipleNotificationProperties(commonProps, accountId, ns)
          } yield {
            verbose(l"createConvNotifications notId: $notId, commonProps:$commonProps, specificProps:$specificProps")
            notId -> specificProps
          }
      })
    } else Future.successful(Iterable.empty)
  }

  private def commonNotificationProperties(ns: Seq[NotificationData], userId: UserId) = {
    verbose(l"commonNotificationProperties: $userId, nots: ${ns.size}")
    for {
      pic <- getPictureForNotifications(userId, ns)
    } yield {
      NotificationProps(userId,
        showWhen = Some(true),
        category = Some(NotificationCompat.CATEGORY_MESSAGE),
        priority = Some(NotificationCompat.PRIORITY_HIGH),
        smallIcon = Some(R.drawable.ic_menu_logo),
        vibrate = if (soundController.isVibrationEnabled(userId)) Some(getIntArray(R.array.new_message_gcm).map(_.toLong)) else Some(Array(0l, 0l)),
        autoCancel = Some(true),
        sound = getSound(ns),
        onlyAlertOnce = Some(ns.forall(_.hasBeenDisplayed)),
        group = Some(userId),
        when = Some(ns.maxBy(_.time.instant).time.instant.toEpochMilli),
        largeIcon = pic,
        lights = Some(Color.WHITE, getInt(R.integer.notifications__system__led_on), getInt(R.integer.notifications__system__led_off)),
        color = None,
        lastIsPing = ns.map(_.msgType).lastOption.map(_ == KNOCK)
      )
    }
  }

  private def singleNotificationProperties(props: NotificationProps, account: UserId, n: NotificationData) = {
    verbose(l"singleNotificationProperties: $account, $n, $props")
    for {
      title <- getMessageTitle(account, n).map(t => SpannableWrapper(t, List(Span(Span.StyleSpanBold, Span.HeaderRange))))
      body <- getMessage(account, n, singleConversationInBatch = true)
      conv   <- convController.getConversation(n.conv).map(_.getOrElse(ConversationData.Empty))
      //      nature <- getUserNature(account, n).map(_.getOrElse(0))
      _ = verbose(l"nature: , title: $title")
    } yield {
      val requestBase = System.currentTimeMillis.toInt
      val bigTextStyle = StyleBuilder(StyleBuilder.BigText, title = title, summaryText = None, bigText = Some(body))
      LogUtils.i("MessageNotificationsController", "singleNotificationProperties==n.conv:" + n.conv + "==bigTextStyle==" + bigTextStyle.toString)
      val specProps = props.copy(
        contentTitle = Some(title),
        contentText = Some(body),
        style = Some(bigTextStyle),
        openConvIntent = Some((account, n.conv, requestBase)),
        clearNotificationsIntent = Some((account, Some(n.conv)))
      )
      if (n.msgType != NotificationType.CONNECT_REQUEST && !conv.isServerNotification)
        if (n.convType == ConversationType.ThousandsGroup.id) {
          specProps.copy(
            action2 = Some((account, n.conv, requestBase + 1, bundleEnabled))
          )
        } else {
          specProps.copy(
            action1 = Some((account, n.conv, requestBase + 1, bundleEnabled)),
            action2 = Some((account, n.conv, requestBase + 2, bundleEnabled))
          )
        }
      else specProps
    }
  }

  private def getMessageTitle(account: UserId, n: NotificationData) = {
    if (n.ephemeral)
      Future.successful(ResString(R.string.notification__message__ephemeral_someone))
    else {
      getConvName(account, n).map(_.getOrElse(Name.Empty)).map { convName =>
        ResString(convName)
      }
    }
  }

  private def getConvName(account: UserId, n: NotificationData) =
    inject[AccountToConvsStorage].apply(account).flatMap {
      case Some(st) =>
        st.get(n.conv).map(_.map(_.displayName))
      case None =>
        Future.successful(Option.empty[Name])
    }

  private def getUserName(account: UserId, n: NotificationData) =
    inject[AccountToUsersStorage].apply(account).flatMap {
      case Some(st) =>
        st.get(n.user).map(_.map(_.getDisplayName))
      case None =>
        Future.successful(Option.empty[Name])
    }

  private def isGroupConv(account: UserId, n: NotificationData) =
    inject[AccountToConvsService].apply(account).flatMap {
      case Some(service) => service.isGroupConversation(n.conv)
      case _ => Future.successful(false)
    }

  private def getUserNature(account: UserId, n: NotificationData): Future[Option[Int]] = {
    try {
      inject[AccountToUsersStorage].apply(account).flatMap {
        case Some(st) =>
          st.get(n.user).map(_.map(_.nature)).map(_.map(_.getOrElse(NatureTypes.Type_Normal)))
        case None =>
          Future.successful(Option.empty[Int])
      }
    } catch {
      case ex: Exception =>
        verbose(l"getUserNature e: ${ex.getMessage}")
        Future.successful(Option.empty[Int])
    }
  }

  private def getMessage(account: UserId, n: NotificationData, singleConversationInBatch: Boolean): Future[SpannableWrapper] = {
    val message = n.msg.replaceAll("\\r\\n|\\r|\\n", " ")
    verbose(l"getMessage singleConversationInBatch:$singleConversationInBatch,message: ${message},NotificationData: ${n}")
    for {
      header <- n.msgType match {
        case CONNECT_ACCEPTED => Future.successful(ResString.Empty)
        case _ => getDefaultNotificationMessageLineHeader(account, n, singleConversationInBatch)
      }
      convName <- getConvName(account, n).map(_.getOrElse(Name.Empty))
      userName <- getUserName(account, n).map(_.getOrElse(Name.Empty))
    } yield {
      val body = n.msgType match {
        case _ if n.ephemeral && n.isSelfMentioned => ResString(R.string.notification__message_with_mention__ephemeral)
        case _ if n.ephemeral && n.isReply => ResString(R.string.notification__message_with_quote__ephemeral)
        case _ if n.ephemeral => ResString(R.string.notification__message__ephemeral)
        case TEXT => ResString(message)
        case TEXTJSON => getTextJsonShowContent(message, n)
        case MISSED_CALL => ResString(R.string.notification__message__one_to_one__wanted_to_talk)
        case KNOCK => ResString(R.string.notification__message__one_to_one__pinged)
        case ANY_ASSET => ResString(R.string.notification__message__one_to_one__shared_file)
        case ASSET => ResString(R.string.notification__message__one_to_one__shared_picture)
        case VIDEO_ASSET => ResString(R.string.notification__message__one_to_one__shared_video)
        case AUDIO_ASSET => ResString(R.string.notification__message__one_to_one__shared_audio)
        case LOCATION => ResString(R.string.notification__message__one_to_one__shared_location)
        case RENAME => ResString(R.string.notification__message__group__renamed_conversation, convName)
        case MEMBER_LEAVE => ResString(R.string.notification__message__group__remove)
        case MEMBER_JOIN => ResString(R.string.notification__message__group__add)
        case LIKE if n.likedContent.nonEmpty =>
          n.likedContent.collect {
            case LikedContent.PICTURE =>
              ResString(R.string.notification__message__liked_picture)
            case LikedContent.TEXT_OR_URL =>
              ResString(R.string.notification__message__liked, n.msg)
          }.getOrElse(ResString(R.string.notification__message__liked_message))
        case CONNECT_ACCEPTED => ResString(R.string.notification__message__single__accept_request, userName)
        case CONNECT_REQUEST => ResString(R.string.people_picker__invite__share_text__header, userName)
        case MESSAGE_SENDING_FAILED => ResString(R.string.notification__message__send_failed)
        case _ => ResString.Empty
      }

      getMessageSpannable(header, body, n.msgType == TEXT)
    }
  }

  def getTextJsonShowContent(message: String, n: NotificationData): ResString = {
    ResString(R.string.notification__message__textjson)
  }

  private def getDefaultNotificationMessageLineHeader(account: UserId, n: NotificationData, singleConversationInBatch: Boolean) =
    for {
      convName <- getConvName(account, n)
      userName <- getUserName(account, n).map(_.getOrElse(Name.Empty))
      isGroup <- isGroupConv(account, n)
    } yield {
      if (n.ephemeral) ResString.Empty
      else {
        val prefixId =
          if (!singleConversationInBatch && isGroup)
            if (n.isSelfMentioned)
              R.string.notification__message_with_mention__group__prefix__text
            else if (n.isReply)
              R.string.notification__message_with_quote__group__prefix__text
            else
              R.string.notification__message__group__prefix__text
          else if (!singleConversationInBatch && !isGroup || singleConversationInBatch && isGroup)
            if (n.isSelfMentioned)
              R.string.notification__message_with_mention__name__prefix__text
            else if (n.isReply)
              R.string.notification__message_with_quote__name__prefix__text
            else
              R.string.notification__message__name__prefix__text
          else if (singleConversationInBatch && isGroup && n.isReply)
            R.string.notification__message_with_quote__name__prefix__text_one2one
          else 0
        if (prefixId > 0) {
          convName match {
            case Some(cn) => ResString(prefixId, userName, cn)
            case None => ResString(prefixId, List(ResString(userName), ResString(R.string.notification__message__group__default_conversation_name)))
          }
        }
        else ResString.Empty
      }
    }

  @TargetApi(21)
  private def getMessageSpannable(header: ResString, body: ResString, isTextMessage: Boolean) = {
    val spans = Span(Span.ForegroundColorSpanBlack, Span.HeaderRange) ::
      (if (!isTextMessage) List(Span(Span.StyleSpanItalic, Span.BodyRange)) else Nil)
    SpannableWrapper(header = header, body = body, spans = spans, separator = "")
  }

  private def getPictureForNotifications(userId: UserId, nots: Seq[NotificationData]): Future[Option[Bitmap]] = {
    verbose(l"getPictureForNotifications: $userId, nots: ${nots.size}")
    if (nots.exists(_.ephemeral)) Future.successful(None)
    else {
      inject[AccountToUsersStorage].apply(userId).flatMap {
        case Some(st) =>
          for {
            //TODO if a user doesn't have a picture, should we default to some bitmap?
            assetId <- st.getAll(nots.map(_.user).toSet).map(_.flatten.flatMap(_.picture)).map { pictures =>
              if (pictures.size == 1) pictures.headOption else None
            }
            bitmap <- {
              val imageLoader = inject[AccountToImageLoader]
              val assetsStorage = inject[AccountToAssetsStorage]

              Future.sequence(List(imageLoader(userId), assetsStorage(userId))).flatMap {
                case Some(imageLoader: ImageLoader) :: Some(assetsStorage: AssetsStorage) :: Nil =>
                  for {
                    assetData <- assetId.fold(Future.successful(Option.empty[AssetData]))(assetsStorage.get)
                    bmp <- assetData.fold(Future.successful(Option.empty[Bitmap])) { ad =>
                      imageLoader.loadBitmap(ad, BitmapRequest.Single(toPx(64)), forceDownload = false).map(Option(_)).withTimeout(500.millis).recoverWith {
                        case _: Throwable => CancellableFuture.successful(None)
                      }.future
                    }
                  } yield
                    bmp.map { original => Bitmap.fromAndroid(BitmapUtils.createRoundBitmap(original, toPx(64), 0, Color.TRANSPARENT)) }
                case _ => Future.successful(None)
              }
            }
          } yield bitmap
        case _ => Future.successful(None)
      }
    }
  }

  private def getSound(ns: Seq[NotificationData]) = {
    if (soundController.soundIntensityNone) None
    else if (!soundController.soundIntensityFull && (ns.size > 1 && ns.lastOption.forall(_.msgType != KNOCK))) None
    else ns.map(_.msgType).lastOption.fold(Option.empty[Uri]) {
      case ASSET | ANY_ASSET | VIDEO_ASSET | AUDIO_ASSET |
           LOCATION | TEXT | TEXTJSON | CONNECT_ACCEPTED | CONNECT_REQUEST | RENAME |
           LIKE => Option(getSelectedSoundUri(soundController.currentTonePrefs._2, R.raw.new_message_gcm))
      case KNOCK => Option(getSelectedSoundUri(soundController.currentTonePrefs._3, R.raw.ping_from_them))
      case _ => None
    }
  }

  private def getSelectedSoundUri(value: String, @RawRes defaultResId: Int): Uri =
    getSelectedSoundUri(value, defaultResId, defaultResId)

  private def getSelectedSoundUri(value: String, @RawRes preferenceDefault: Int, @RawRes returnDefault: Int): Uri = {
    if (!TextUtils.isEmpty(value) && !RingtoneUtils.isDefaultValue(cxt, value, preferenceDefault)) Uri.parse(value)
    else RingtoneUtils.getUriForRawId(cxt, returnDefault)
  }

  private def multipleNotificationProperties(props: NotificationProps, account: UserId, ns: Seq[NotificationData]): Future[NotificationProps] = {
    verbose(l"multipleNotificationProperties: $account, $ns, $props")
    val convIds = ns.map(_.conv).toSet
    val isSingleConv = convIds.size == 1

    val n = ns.head

    for {
      convName <- getConvName(account, n).map(_.getOrElse(Name.Empty))
      messages <- Future.sequence(ns.sortBy(_.time.instant).map(n => getMessage(account, n, singleConversationInBatch = isSingleConv)).takeRight(5).toList)
      //      nature <- getUserNature(account, n).map(_.getOrElse(0))
      conv   <- convController.getConversation(n.conv).map(_.getOrElse(ConversationData.Empty))
      _ = verbose(l"nature: , convName: $convName")
    } yield {
      val header =
        if (isSingleConv) {
          if (ns.exists(_.ephemeral)) ResString(R.string.notification__message__ephemeral_someone)
          else ResString(convName.str)
        }
        else
          ResString(R.plurals.notification__new_messages__multiple, convIds.size, ns.size)

      val separator = " • "

      val title =
        if (isSingleConv && ns.size > 5)
          SpannableWrapper(
            header = header,
            body = ResString(R.plurals.conversation_list__new_message_count, ns.size),
            spans = List(
              Span(Span.StyleSpanBold, Span.HeaderRange),
              Span(Span.StyleSpanItalic, Span.BodyRange, separator.length),
              Span(Span.ForegroundColorSpanGray, Span.BodyRange)
            ),
            separator = separator
          )
        else
          SpannableWrapper(
            header = header,
            spans = List(Span(Span.StyleSpanBold, Span.HeaderRange))
          )

      val requestBase = System.currentTimeMillis.toInt
      val inboxStyle = StyleBuilder(StyleBuilder.Inbox, title = title, summaryText = None, lines = messages)
      LogUtils.i("MessageNotificationsController", "multipleNotificationProperties==n.conv:" + n.conv + "==inboxStyle==" + inboxStyle.toString)
      val specProps = props.copy(
        contentTitle = Some(title),
        contentText = Some(messages.last),
        style = Some(inboxStyle)
      )

      if (n.convType == ConversationType.ThousandsGroup.id) {
        specProps.copy(
          openConvIntent = Some((account, n.conv, requestBase)),
          clearNotificationsIntent = Some((account, Some(n.conv))),
          action2 = Some((account, n.conv, requestBase + 1, bundleEnabled))
        )
      } else {
        specProps.copy(
          openConvIntent = Some((account, n.conv, requestBase)),
          clearNotificationsIntent = Some((account, Some(n.conv))),
          action1 = Some((account, n.conv, requestBase + 1, bundleEnabled)),
          action2 = Some((account, n.conv, requestBase + 2, bundleEnabled))
        )
      }
    }
  }
}

object MessageNotificationsController {

  def toNotificationGroupId(userId: UserId): Int = userId.str.hashCode()

  def toEphemeralNotificationGroupId(userId: UserId): Int = toNotificationGroupId(userId) + 1

  def toNotificationConvId(userId: UserId, convId: ConvId): Int = (userId.str + convId.str).hashCode()

  def toEphemeralNotificationConvId(userId: UserId, convId: ConvId): Int = toNotificationConvId(userId, convId) + 1

  val ZETA_MESSAGE_NOTIFICATION_ID: Int = 1339272
  val ZETA_EPHEMERAL_NOTIFICATION_ID: Int = 1339279

}
