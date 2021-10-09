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

package com.waz.services.websocket

import android.app._
import android.content
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.os.{Build, IBinder}
import androidx.core.app.NotificationCompat
import com.waz.content.GlobalPreferences.{PushEnabledKey, WsForegroundKey}
import com.waz.jobs.PushTokenCheckJob
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.AccountsService.InForeground
import com.waz.service.{AccountsService, GlobalModule, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient._
import com.waz.zclient.log.LogUI._

class WebSocketController(implicit inj: Injector, ctx: Context) extends Injectable with DerivedLogTag{
  private lazy val global   = inject[GlobalModule]
  private lazy val accounts = inject[AccountsService]
  private lazy val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  private lazy val cloudPushAvailable =
    for {
      gpsAvailable <- global.googleApi.isGooglePlayServicesAvailable
      devPrefEnabled <- global.prefs(PushEnabledKey).signal
      pushTokenDefined <- currentAccount.map(_.pushToken.isDefined)
    } yield (gpsAvailable && devPrefEnabled && pushTokenDefined)

  lazy val accountWebsocketStates: Signal[(Set[ZMessaging], Set[ZMessaging])] =
    for {
      cloudPushAvailable  <- cloudPushAvailable
      uiActive            <- global.lifecycle.uiActive
      wsForegroundEnabled <- global.prefs(WsForegroundKey).signal
      _ = verbose(l"wsForeground enabled: $wsForegroundEnabled, cloudPushAvailable: $cloudPushAvailable, uiActive: $uiActive")
      accs                <- accounts.zmsInstances
      accsInFG            <- Signal.sequence(accs.map(_.selfUserId).map(id => accounts.accountState(id).map(st => id -> st)).toSeq : _*).map(_.toMap)
      (zmsWithWSActive, zmsWithWSInactive) =
        accs.partition(zms => accsInFG(zms.selfUserId) == InForeground || (!cloudPushAvailable && (uiActive || wsForegroundEnabled)))
    } yield (zmsWithWSActive, zmsWithWSInactive)

  lazy val serviceInForeground: Signal[Boolean] =
    for {
      uiActive       <- global.lifecycle.uiActive
      (zmsWithWS, _) <- accountWebsocketStates
    } yield !uiActive && zmsWithWS.nonEmpty

  lazy val notificationTitleRes: Signal[Option[Int]] =
    serviceInForeground.flatMap {
      case true =>
        verbose(l"Service in foreground")
        global.network.isOnline.flatMap {
          //checks to see if there are any accounts that haven't yet established a web socket
          case true => accounts.zmsInstances.flatMap(zs => Signal.sequence(zs.map(_.wsPushService.connected).toSeq: _ *).map(_.exists(!identity(_)))).map {
            case true => verbose(l"connecting");Option(R.string.ws_foreground_notification_connecting_title)
            case _    => verbose(l"connected");Option(R.string.ws_foreground_notification_connected_title)
          }
          case _ =>
            verbose(l"Returning no notification titles despite service in foreground")
            Signal.const(Option(R.string.ws_foreground_notification_no_internet_title))
        }
      case _ =>
        verbose(l"Returning no notification titles")
        Signal.const(Option.empty[Int])
    }
}

/**
  * Receiver called on boot or when app is updated.
  */
class OnBootAndUpdateBroadcastReceiver extends BroadcastReceiver with DerivedLogTag {

  private var context: Context = _

  val ACTION = "com.waz.services.websocket.OnBootAndUpdateBroadcastReceiver.ACTION"

  override def onReceive(context: Context, intent: Intent): Unit = {
    this.context = context
    verbose(l"onReceive ${RichIntent(intent)}")
    if (Option(ZMessaging.currentGlobal).isDefined) {
      Option(context.getApplicationContext.asInstanceOf[WireApplication].module).foreach { injector =>
        injector.binding[AccountsService] match {
          case Some(accounts) =>
            verbose(l"AccountsService loaded")
            accounts().zmsInstances.head.foreach { zs =>
              zs.map(_.selfUserId).foreach(PushTokenCheckJob(_))
            }(Threading.Background)

          case _ =>
            verbose(l"Failed to load AccountsService")
        }

        injector.binding[WebSocketController] match {
          case Some(controller) =>
            verbose(l"WebSocketController loaded")
            controller().serviceInForeground.head.foreach {
              case true =>
                verbose(l"startForegroundService")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                  context.startForegroundService(new Intent(context, classOf[WebSocketService]))
                else
                  WebSocketService(context)
              case false =>
                verbose(l"foreground service not needed, will wait for application to start service if necessary")
            }(Threading.Ui)

          case None =>
            verbose(l"Failed to load WebSocketController")
        }
      }
    } else {
      verbose(l"Failed to load ZMessaging.currentGlobal ==null")
    }
  }
}


/**
  * Service keeping the process running as long as web socket should be connected.
  */
class WebSocketService extends ServiceHelper with DerivedLogTag {

  import WebSocketService._

  private implicit def context = getApplicationContext

  private lazy val launchIntent = PendingIntent.getActivity(context, 1, Intents.ShowAdvancedSettingsIntent/*PreferencesAdaptActivity.getIntent(context, PreferencesAdaptActivity.INTENT_VAL_advanced)*/, 0)

  private lazy val controller = inject[WebSocketController]
  private lazy val notificationManager = inject[NotificationManager]

  private lazy val webSocketActiveSubscription =
    controller.accountWebsocketStates {
      case (zmsWithWSActive, zmsWithWSInactive) =>
        verbose(l"zmsWithWSActive: ${zmsWithWSActive.map(_.selfUserId)}, zmsWithWSInactive: ${zmsWithWSInactive.map(_.selfUserId)}")
        zmsWithWSActive.foreach(_.wsPushService.activate())
        zmsWithWSInactive.foreach(_.wsPushService.deactivate())
        if (zmsWithWSActive.isEmpty) {
          verbose(l"stopping")
          stopSelf()
        }
    }

  private lazy val appInForegroundSubscription =
    controller.notificationTitleRes {
      case None =>
        stopForeground(true)
      case Some(title) =>
        verbose(l"startForeground")
        createNotificationChannel()
        startForeground(WebSocketService.ForegroundId,
          new NotificationCompat.Builder(this, ForegroundNotificationChannelId)
            .setSmallIcon(R.drawable.ic_menu_logo)
            .setContentTitle(getString(title))
            .setContentIntent(launchIntent)
            .setStyle(new NotificationCompat.BigTextStyle()
              .bigText(getString(R.string.ws_foreground_notification_summary)))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setPriority(if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .build()
        )
    }

  override def onBind(intent: content.Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    verbose(l"onStartCommand(${RichIntent(intent)}, $startId)")
    webSocketActiveSubscription
    appInForegroundSubscription

    Service.START_STICKY
  }

  private def createNotificationChannel(): Unit =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      notificationManager.createNotificationChannel(
        returning(new NotificationChannel(ForegroundNotificationChannelId, getString(R.string.foreground_service_notification_name), NotificationManager.IMPORTANCE_LOW)) { ch =>
          ch.setDescription(getString(R.string.foreground_service_notification_description))
          ch.enableVibration(false)
          ch.setShowBadge(false)
          ch.setSound(null, null)
          ch.enableLights(false)
          ch.setBypassDnd(true)
        })
    }

  private lazy val broadcastReceiver:OnBootAndUpdateBroadcastReceiver = new OnBootAndUpdateBroadcastReceiver

  override def onCreate(): Unit = {
    super.onCreate()
    verbose(l"onCreate")
    val intentFilter : IntentFilter =new IntentFilter()
    intentFilter.addAction("com.waz.services.websocket.OnBootAndUpdateBroadcastReceiver.ACTION")
    intentFilter.addAction("android.intent.action.MY_PACKAGE_REPLACED")
    intentFilter.addAction("android.intent.action.BOOT_COMPLETED")
    intentFilter.addAction("android.intent.action.LOCKED_BOOT_COMPLETED")
    intentFilter.addAction("android.intent.action.SCREEN_OFF")
    intentFilter.addAction("android.intent.action.SCREEN_ON")
    intentFilter.addAction("android.intent.action.USER_PRESENT")
    registerReceiver(broadcastReceiver, intentFilter)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    verbose(l"onDestroy")
    try {
      unregisterReceiver(broadcastReceiver)
    } catch {
      case ex:Exception =>
    }
  }
}

object WebSocketService {

  val ForegroundNotificationChannelId = "FOREGROUND_NOTIFICATION_CHANNEL_ID"

  val ForegroundId = 41235

  def apply(context: Context) = context.startService(new Intent(context, classOf[WebSocketService]))
}
