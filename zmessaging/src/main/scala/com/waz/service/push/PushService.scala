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

import android.content.Context
import com.waz.log.LogSE._
import com.waz.api.NetworkMode.{OFFLINE, UNKNOWN}
import com.waz.api.impl.ErrorResponse
import com.waz.content.GlobalPreferences.BackendDrift
import com.waz.content.UserPreferences.LastStableNotification
import com.waz.content.{ConversationStorage, ConversationStorageImpl, GlobalPreferences, UserPreferences, UsersStorage}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogShow
import com.waz.model.Event.EventDecoder
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.ZMessaging.{accountTag, clock}
import com.waz.service._
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService.SyncSource
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.PushNotificationsClient.LoadNotificationsResult
import com.waz.sync.client.{PushNotificationEncoded, PushNotificationsClient}
import com.waz.threading.CancellableFuture.lift
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.events.{Signal, _}
import com.waz.utils.{RichInstant, _}
import com.waz.znet2.http.ResponseCode
import org.json.JSONObject
import org.threeten.bp.{Duration, Instant}
import FCMNotification.{Fetched, FinishedPipeline, StartedPipeline}
import com.waz.api.Message
import com.waz.model.ConversationData.ConversationType
import com.waz.utils.crypto.AESUtils

import scala.async.Async._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException}
import scala.util.Try

/** PushService handles notifications coming from FCM, WebSocket, and fetch.
  * We assume FCM notifications are unreliable, so we use them only as information that we should perform a fetch (syncHistory).
  * A notification from the web socket may trigger a fetch on error. When fetching we ask BE to send us all notifications since
  * a given lastId - it may take time and we even may find out that we lost some history (lastId not found) which triggers slow sync.
  * So, it may happen that during the fetch new notifications will arrive through the web socket. Their processing should be
  * performed only after the fetch is done. For that we use a serial dispatch queue and we process notifications in futures,
  * which are put at the end of the queue, essentially making PushService synchronous.
  * Also, we need to handle fetch failures. If a fetch fails, we want to repeat it - after some time, or on network change.
  * In such case, new web socket notifications waiting to be processed should be dismissed. The new fetch will (if successful)
  * receive them in the right order.
  */

trait PushService {

  //set withRetries to false if the caller is to handle their own retry logic
  def syncHistory(reason: SyncSource, withRetries: Boolean = true): Future[Unit]

  //def syncHistoryWebSocketChange(reason: SyncSource, withRetries: Boolean = true): Future[Unit]

  def onHistoryLost: SourceSignal[Instant] with BgEventSource

  def processing: Signal[Boolean]

  def waitProcessing: Future[Unit]

  /**
    * Drift to the BE time at the moment we fetch notifications
    * Used for calling (time critical) messages that can't always rely on local time, since the drift can be
    * greater than the window in which we need to respond to messages
    */
  def beDrift: Signal[Duration]

  def syncSystemNotifyNotification(syncId: Option[Uid]): Future[Unit]
}

class PushServiceImpl(selfUserId: UserId,
                      context: Context,
                      userPrefs: UserPreferences,
                      prefs: GlobalPreferences,
                      notificationStorage: PushNotificationEventsStorage,
                      client: PushNotificationsClient,
                      clientId: ClientId,
                      pipeline: EventPipeline,
                      otrService: OtrService,
                      wsPushService: WSPushService,
                      accounts: AccountsService,
                      pushTokenService: PushTokenService,
                      network: NetworkModeService,
                      lifeCycle: UiLifeCycle,
                      tracking: TrackingService,
                      sync: SyncServiceHandle,
                      timeouts: Timeouts,
                      fcmService: FCMNotificationStatsService,
                      conversationStorage: ConversationStorage,
                      usersStorage: UsersStorage)
                     (implicit ev: AccountContext) extends PushService {
  self =>

  import PushService._

  implicit val logTag: LogTag = accountTag[PushServiceImpl](selfUserId)
  private implicit val dispatcher = new SerialDispatchQueue(name = "PushService")

  override val onHistoryLost = new SourceSignal[Instant] with BgEventSource
  override val processing = Signal(false)

  override def waitProcessing =
    processing.filter(_ == false).head.map(_ => {})

  private val beDriftPref = prefs.preference(BackendDrift)
  override val beDrift = beDriftPref.signal.disableAutowiring()

  private var fetchInProgress =Future.successful({})

  private lazy val idPref = userPrefs.preference(LastStableNotification)

  private val timeOffset = System.currentTimeMillis()

  @inline private def timePassed = System.currentTimeMillis() - timeOffset

  //verbose(l"SYNC PushService created with the time offset: $timeOffset")

  notificationStorage.registerEventHandler { () =>
    Serialized.future(PipelineKey) {
      for {
        _ <- Future.successful(processing ! true)
        _ <- processEncryptedRows()
        _ <- processDecryptedRows()
        _ <- Future.successful(processing ! false)
      } yield {}
    }.recover {
      case ex =>
        processing ! false
        error(l"SYNC Unable to process events: $ex")
    }
  }

  private def processEncryptedRows() =
    notificationStorage.encryptedEvents.flatMap { rows =>
      verbose(l"synctest encrypted rows ${rows.size}")
      Future.sequence(rows.map { row =>
        if (!isOtrEventJson(row.event)) notificationStorage.setAsDecrypted(row.pushId)
        else {
          ConversationEvent.ConversationEventDecoder(row.event) match {
            case event: OtrEvent =>
              val writer = notificationStorage.writeClosure(row.pushId)
              otrService.decryptStoredOtrEvent(event, writer).flatMap {
                case Left(error) =>
                  val e = OtrErrorEvent(event.convId, event.time, event.from, error)
                  verbose(l"Got error when decrypting: $e")
                  notificationStorage.writeError(row.pushId, e)
                case Right(_)    => Future.successful(())
              }
            case _               =>
              Future.successful(())
          }
        }
      })
    }

  private def processDecryptedRows(): Future[Unit] = {
    def decodeRow(event: PushNotificationEvent) = {
      if (event.plain.isDefined && isOtrEventJson(event.event)) {
        verbose(l"decodeRow($event) for an otr event")
        val msg = GenericMessage(event.plain.get)
        val msgEvent = ConversationEvent.ConversationEventDecoder(event.event)
        otrService.parseGenericMessage(msgEvent.asInstanceOf[OtrMessageEvent], msg)
      } else if (isBgpOtrEventJson(event.event)) {
        verbose(l"decodeRow($event) for an bgp event")
        val msgEvent = ConversationEvent.ConversationEventDecoder(event.event)
        val jSONObject: JSONObject = event.event
        val data = jSONObject.optJSONObject("data")
        val text: String = data.optString("text")
        Try(GenericMessage(AESUtils.base64(text))).toOption.fold(Option(EventDecoder(event.event))) { message =>
          otrService.parseBgpGenericMessageFix(event.event, msgEvent, message)
        }
      } else if(isUserNoticeEventJson(event.event)){
        verbose(l"decodeRow($event) for an user notice event")
        val json=event.event
        val data=json.optJSONObject("data")
        var msgType=""
        var msgData=new JSONObject()
        if(data!=null){
          msgType=data.optString(ServerIdConst.KEY_TEXTJSON_MSGTYPE)
          msgData=data.optJSONObject(ServerIdConst.KEY_TEXTJSON_MSGDATA)
          if(msgData==null){
            msgData=new JSONObject()
          }
        }
        verbose(l"+++++++++++++++++++++getData:${msgType},${msgData.toString}")
        val userNoticeEvent=UserNoticeEvent(msgType,msgData)
        Some(userNoticeEvent)
      } else if (isBasicNotificationOtrEventJson(event.event)) {
        verbose(l"decodeRow isBasicNotificationOtrEventJson($event)")
        val msgEvent = ConversationEvent.ConversationEventDecoder(event.event).asInstanceOf[BasicNotificationMessageEvent]
        val msgId = msgEvent.eid.getOrElse(MessageId())
        val jsonContent = msgEvent.data.fold("{}")(_.toString())
        val genericMsg = GenericMessage(msgId.uid, GenericContent.TextJson(jsonContent))
        Some(GenericMessageEvent(msgEvent.convId, msgEvent.time, msgEvent.from, genericMsg, "", "")
          .withLocalTime(LocalInstant.apply(Instant.now())))
      } else {
        verbose(l"decodeRow($event) for a non-otr event")
        Some(EventDecoder(event.event))
      }
    }

    notificationStorage.getDecryptedRows().flatMap { rows =>
      verbose(l"synctest Decrypted rows ${rows.size}")
      //verbose(l"SYNC Decrypted rows ${rows.size}")
      if (rows.nonEmpty) {
        //val ids = rows.map(_.pushId).toSet
        for {
          //          _ <- Future.apply {
          //            fcmService.markNotificationsWithState(ids, StartedPipeline)
          //          }
          _ <- Future.apply(pipeline(rows.flatMap(decodeRow)))
          //_ = verbose(l"pipeline work finished")
          _ <- notificationStorage.removeRows(rows.map(_.pushId))
          //_ = verbose(l"rows removed from the notification storage ${rows.map(_.pushId).toList}")
          //          _ <- Future.apply {
          //            fcmService.markNotificationsWithState(ids, FinishedPipeline)
          //          }
         // _ = verbose(l"notifications marked")
          _ <- processDecryptedRows()
          //_ = verbose(l"decrypted rows processed")
        } yield {}
      } else Future.successful(())
    }
  }

  wsPushService.notifications() { notifications =>
//    storeNotifications(notifications, fetchInProgress.isCompleted)
    fetchInProgress = if (fetchInProgress.isCompleted) {
      storeNotifications(notifications, true)
    } else {
      fetchInProgress.flatMap(_ => storeNotifications(notifications, fetchInProgress.isCompleted))
    }
  }

  wsPushService.connected().onChanged.map(WebSocketChange).on(dispatcher){
    connectStatus =>
      if(connectStatus.connected){
        syncHistory(connectStatus)
      }else{
        Future.successful[WebSocketChange]({WebSocketChange(false)})
      }
  }

  private def storeNotifications(notifications: Seq[PushNotificationEncoded],changeNid:Boolean = false,needFilterTyping : Boolean = false): Future[Unit] = {
    if (notifications.isEmpty) {
      Future.successful({})
    }else{
      Serialized.future(PipelineKey) {
        async {
          //await {
          //  fcmService.markNotificationsWithState(notifications.map(_.id).toSet, Fetched)
          //}
          //verbose(l"SYNC storeNotifications ${notifications.size}")
          verbose(l"synctest notifications events:${notifications.size}")

          val result = await {
            notificationStorage.saveAll(notifications,needFilterTyping)
          }

          verbose(l"synctest save all events:${result.size}")


          val res = notifications.lift(notifications.lastIndexWhere { n =>
            !n.transient && (n.id != null && !android.text.TextUtils.isEmpty(n.id.str))
          })
          if (res.nonEmpty && changeNid)
            await {
              idPref := res.map(_.id)
            }
        }
      }
    }
  }


  private def storeSystemNotifyNotification(notifications: Seq[PushNotificationEncoded]): Future[Unit] =
    Serialized.future(PipelineKey_SIE) {
      async {

        await {
          notificationStorage.saveAll(notifications)
        }

        verbose(l"synctest storeSystemNotifyNotification events:${notifications.size}")

      }
    }


  private def isOtrEventJson(ev: JSONObject) =
    ev.getString("type").equals("conversation.otr-message-add")

  private def isBgpOtrEventJson(ev: JSONObject) =
    ev.getString("type").equals("conversation.bgp-message-add")

  private def isUserNoticeEventJson(ev:JSONObject)=
    ev.getString("type").equals("user.notice-message")

  private def isBasicNotificationOtrEventJson(ev: JSONObject): Boolean = {
    "conversation.json-message-add".equals(ev.optString("type"))
  }

  case class Results(notifications: Vector[PushNotificationEncoded], time: Option[Instant], firstSync: Boolean, historyLost: Boolean)

  private def futureHistoryResults(notifications: Vector[PushNotificationEncoded] = Vector.empty,
                                   time: Option[Instant] = None,
                                   firstSync: Boolean = false,
                                   historyLost: Boolean = false) =
    CancellableFuture.successful(Results(notifications, time, firstSync, historyLost))

  //expose retry loop to tests
  protected[push] val waitingForRetry: SourceSignal[Boolean] = Signal(false).disableAutowiring()

  override def syncHistory(source: SyncSource, withRetries: Boolean = true): Future[Unit] = {

    def load(lastId: Option[Uid], firstSync: Boolean = false, attempts: Int = 0): CancellableFuture[Results] =
      (lastId match {
        case None => if (firstSync) {
          verbose(l"None.loadLastNotification $clientId")
          client.loadLastNotification(clientId)
        } else {
          verbose(l"None.loadNotifications None")
          client.loadNotifications(None, clientId)
        }
        case id => {
          verbose(l"synctest loadNotifications : $id")
          client.loadNotifications(id, clientId)
        }
      }).flatMap {
        case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
          verbose(l"synctest loadNotifications success")
          futureHistoryResults(response.notifications, response.beTime, firstSync = firstSync)
        case Right(LoadNotificationsResult(response, historyLost)) if response.hasMore && !historyLost =>
          verbose(l"synctest loadNotifications hasmore")
          load(response.notifications.lastOption.map(_.id)).flatMap { results =>
            futureHistoryResults(
              response.notifications ++ results.notifications,
              if (results.time.isDefined) results.time else response.beTime,
              historyLost = results.historyLost
            )
          }
        case Right(LoadNotificationsResult(response, historyLost)) if lastId.isDefined && historyLost =>
          verbose(l"synctest loadNotifications historyLost")
          futureHistoryResults(response.notifications, response.beTime, historyLost = historyLost)
        case Left(e@ErrorResponse(ResponseCode.Unauthorized, _, _)) =>
          warn(l"Logged out, failing sync request")
          CancellableFuture.failed(FetchFailedException(e))
        case Left(err) =>
          warn(l"Request failed due to $err: attempting to load last page (since id: $lastId) again? $withRetries")
          if (!withRetries) CancellableFuture.failed(FetchFailedException(err))
          else {
            //We want to retry the download after the backoff is elapsed and the network is available,
            //OR on a network state change (that is not offline/unknown)
            //OR on a websocket state change
            val retry = Promise[Unit]()

            network.networkMode.onChanged.filter(!Set(UNKNOWN, OFFLINE).contains(_)).next.map(_ => retry.trySuccess({}))
            wsPushService.connected().onChanged.next.map(_ => retry.trySuccess({}))

            for {
              _ <- CancellableFuture.delay(syncHistoryBackoff.delay(attempts))
              _ <- lift(network.networkMode.filter(!Set(UNKNOWN, OFFLINE).contains(_)).head)
            } yield retry.trySuccess({})

            waitingForRetry ! true
            lift(retry.future).flatMap { _ =>
              waitingForRetry ! false
              if(attempts < MAX_SYNC_COUNT){
                load(lastId, firstSync, attempts + 1)
              }else{
                CancellableFuture.failed(FetchFailedException(err))
              }

            }
          }
      }

    def syncHistory(lastId: Option[Uid]): Future[Unit] =
    {
      Future.successful(processing ! true)
      load(lastId, firstSync = lastId.isEmpty).future.flatMap {
        case Results(nots, time, firstSync, historyLost) =>
          verbose(l"synctest loadNotifications size ${nots.size} nid:$lastId")
          Future.successful(processing ! false)
          //verbose(l"SYNC syncHistory size ${nots.size} nid:$lastId source : ${source}")
          //verbose(l"Sync history in load to nots = ${nots.size}, time =$time, firstSync =$firstSync, historyLost = $historyLost")
          if (firstSync) {
            idPref.update(nots.headOption.filter { n =>
              !n.transient && (n.id != null && !android.text.TextUtils.isEmpty(n.id.str))
            }.map(_.id))
          } else {
            (for {
              _ <- if (historyLost) sync.performFullSync().map(_ => onHistoryLost ! clock.instant()) else Future.successful({})
              _ <- beDriftPref.mutate(v => time.map(clock.instant.until(_)).getOrElse(v))
            } yield {
              nots
            }).flatMap(data => storeNotifications(data, changeNid = true,needFilterTyping = true))
          }
        case _ =>
          Future.successful(processing ! false)
      }
    }

    fetchInProgress = if(fetchInProgress.isCompleted) idPref().flatMap(syncHistory) else fetchInProgress.flatMap(_ => idPref().flatMap(syncHistory))
    fetchInProgress
  }

  override def syncSystemNotifyNotification(syncId: Option[Uid]): Future[Unit] = {
    def load(syncId: Option[Uid]): CancellableFuture[Results] =
      client.loadNotificationById(syncId).flatMap {
        case Right(LoadNotificationsResult(response, historyLost)) if !response.hasMore && !historyLost =>
          futureHistoryResults(response.notifications, response.beTime, false)
        case Left(err) =>
          CancellableFuture.failed(FetchFailedException(err))
      }

    load(syncId).future.flatMap {
      case Results(nots, time, firstSync, historyLost) =>
        storeSystemNotifyNotification(nots)
    }
  }
}

object PushService {

  //These are the most important event types that generate push notifications
  val TrackingEvents = Set("conversation.otr-message-add", "conversation.create", "conversation.rename", "conversation.member-join",
    "conversation.bgp-message-add", "conversation.update-blocktime", "conversation.member-join-ask")

  val PipelineKey = "pipeline_processing"
  //val PipelineKey_Process = "pipeline_processing_process"
  val PipelineKey_SIE = "pipeline_processing_sie"

  case class FetchFailedException(err: ErrorResponse) extends Exception(s"Failed to fetch notifications: ${err.message}")

  var syncHistoryBackoff: Backoff = new ExponentialBackoff(3.second, 15.seconds)

  val PROCESS_TIMEOUT = 15.second

  val MAX_SYNC_COUNT = 5

  sealed trait SyncSource

  case class FetchFromJob(nId: Option[Uid]) extends SyncSource

  case class FetchFromIdle(nId: Option[Uid]) extends SyncSource

  case class WebSocketChange(connected: Boolean) extends SyncSource

  object ForceSync extends SyncSource

  implicit val SyncSourceLogShow: LogShow[SyncSource] =
    LogShow.createFrom {
      case FetchFromJob(nId) => l"FetchFromJob(nId: $nId)"
      case FetchFromIdle(nId) => l"FetchFromIdle(nId: $nId)"
      case WebSocketChange(connected) => l"WebSocketChange(connected: $connected)"
      case ForceSync => l"ForcePush"
    }
}
