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
package com.waz.zclient

import android.app.{Activity, ActivityManager, NotificationManager}
import android.content.res.Configuration
import android.content.{Context, ContextWrapper, Intent}
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.{Build, PowerManager, Vibrator}
import android.renderscript.RenderScript
import android.telephony.TelephonyManager
import androidx.fragment.app.{FragmentActivity, FragmentManager}
import androidx.multidex.MultiDexApplication
import com.evernote.android.job.{JobCreator, JobManager}
import com.google.android.gms.security.ProviderInstaller
import com.jsy.res.theme.ThemeUtils
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.NetworkMode
import com.waz.background.WorkManagerSyncRequestService
import com.waz.content._
import com.waz.jobs.PushTokenCheckJob
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log._
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service._
import com.waz.service.call.GlobalCallingService
import com.waz.service.conversation.{ConversationsService, ConversationsUiService, SelectedConversationService}
import com.waz.service.images.ImageLoader
import com.waz.service.messages.MessagesService
import com.waz.service.tracking.TrackingService
import com.waz.services.fcm.{FetchJob, FetchSystemNotificationJob}
import com.waz.services.gps.GoogleApiImpl
import com.waz.services.websocket.WebSocketController
import com.waz.sync.client.CustomBackendClient
import com.waz.sync.{SyncHandler, SyncRequestService}
import com.waz.threading.Threading
import com.waz.utils.SafeBase64
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.GoogleApi
import com.waz.zclient.appentry.controllers.{CreateTeamController, InvitationsController}
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.camera.controllers.{AndroidCameraFactory, GlobalCameraController}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.{AccentColorController, ClientsController, KeyboardController, PasswordController}
import com.waz.zclient.common.views.ImageController
import com.waz.zclient.controllers._
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.IConfirmationController
import com.waz.zclient.controllers.deviceuser.IDeviceUserController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.controllers.navigation.INavigationController
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.conversation.{ConversationController, ConversationsUiControllerImpl, ReplyController}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.deeplinks.DeepLinkService
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.controllers.{MessageActionsController, NavigationController}
import com.waz.zclient.messages.{LikesController, MessagePagedListController, MessageViewFactory, MessagesController, UsersController}
import com.waz.zclient.notifications.controllers.NotificationManagerWrapper.AndroidNotificationsManager
import com.waz.zclient.notifications.controllers._
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.participants.{DevicesDialogController, ParticipantsController}
import com.waz.zclient.preferences.PreferencesController
import com.waz.zclient.tracking.{CrashController, GlobalTrackingController, UiTrackingController}
import com.waz.zclient.utils.{AndroidBase64Delegate, BackStackNavigator, BackendController, ExternalFileSharing, LocalThumbnailCache, UiStorage}
import com.waz.zclient.views.DraftMap
import me.leolin.shortcutbadger.ShortcutBadger
import org.threeten.bp.Clock

import java.io.File
import java.util.Calendar
import javax.net.ssl.SSLContext
import scala.concurrent.Future
import scala.util.control.NonFatal

object WireApplication extends DerivedLogTag {
  var APP_INSTANCE: WireApplication = _

  type AccountToImageLoader = (UserId) => Future[Option[ImageLoader]]
  type AccountToAssetsStorage = (UserId) => Future[Option[AssetsStorage]]
  type AccountToUsersStorage = (UserId) => Future[Option[UsersStorage]]
  type AccountToConvsStorage = (UserId) => Future[Option[ConversationStorage]]
  type AccountToConvsService = (UserId) => Future[Option[ConversationsService]]

  lazy val Global = new Module {

    verbose(l"Global module created!!")

    implicit lazy val ctx: WireApplication = WireApplication.APP_INSTANCE
    implicit lazy val wContext: WireContext = ctx
    implicit lazy val eventContext: EventContext = EventContext.Global

    //Android services
    bind[ActivityManager] to ctx.getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
    bind[PowerManager] to ctx.getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    bind[Vibrator] to ctx.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]
    bind[AudioManager] to ctx.getSystemService(Context.AUDIO_SERVICE).asInstanceOf[AudioManager]
    bind[SensorManager] to ctx.getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager]
    bind[NotificationManager] to ctx.getSystemService(Context.NOTIFICATION_SERVICE).asInstanceOf[NotificationManager]
    bind[TelephonyManager] to ctx.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
    bind[RenderScript] to RenderScript.create(ctx)

    def controllerFactory = APP_INSTANCE.asInstanceOf[ZApplication].getControllerFactory

    bind[NotificationManagerWrapper] to new AndroidNotificationsManager(inject[NotificationManager])

    //SE Services
    bind[GlobalModule] to ZMessaging.currentGlobal
    bind[AccountsService] to ZMessaging.currentAccounts
    bind[Signal[AccountsService]] to Signal.future(ZMessaging.accountsService) //use for early-initialised classes
    bind[BackendConfig] to inject[GlobalModule].backend
    bind[AccountStorage] to inject[GlobalModule].accountsStorage
    bind[TeamsStorage] to inject[GlobalModule].teamsStorage
    bind[SSOService] to inject[GlobalModule].ssoService
    bind[GoogleApi] to inject[GlobalModule].googleApi
    bind[GlobalCallingService] to inject[GlobalModule].calling
    bind[SyncHandler] to inject[GlobalModule].syncHandler
    bind[Clock] to ZMessaging.clock

    bind[Signal[Option[AccountManager]]] to ZMessaging.currentAccounts.activeAccountManager
    bind[Signal[AccountManager]] to inject[Signal[Option[AccountManager]]].collect { case Some(am) => am }
    bind[Signal[Option[ZMessaging]]] to ZMessaging.currentUi.currentZms
    bind[Signal[ZMessaging]] to inject[Signal[Option[ZMessaging]]].collect { case Some(z) => z }
    bind[GlobalPreferences] to inject[GlobalModule].prefs
    bind[NetworkModeService] to inject[GlobalModule].network
    bind[UiLifeCycle] to inject[GlobalModule].lifecycle
    bind[TrackingService] to inject[GlobalModule].trackingService
    bind[PermissionsService] to inject[GlobalModule].permissions
    bind[MetaDataService] to inject[GlobalModule].metadata
    bind[LogsService] to inject[GlobalModule].logsService
    bind[CustomBackendClient] to inject[GlobalModule].customBackendClient

    import com.waz.threading.Threading.Implicits.Background

    bind[AccountToImageLoader] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.imageLoader)))
    bind[AccountToAssetsStorage] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.assetsStorage)))
    bind[AccountToUsersStorage] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.usersStorage)))
    bind[AccountToConvsStorage] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.convsStorage)))
    bind[AccountToConvsService] to (userId => inject[AccountsService].getZms(userId).map(_.map(_.conversations)))

    // the current user's id
    bind[Signal[Option[UserId]]] to inject[Signal[AccountsService]].flatMap(_.activeAccountId)
    bind[Signal[UserId]] to inject[Signal[ZMessaging]].map(_.selfUserId)
    bind[Signal[Option[TeamId]]] to inject[Signal[ZMessaging]].map(_.teamId)


    // services  and storages of the current zms
    bind[Signal[ConversationsService]] to inject[Signal[ZMessaging]].map(_.conversations)
    bind[Signal[SelectedConversationService]] to inject[Signal[ZMessaging]].map(_.selectedConv)
    bind[Signal[ConversationsUiService]] to inject[Signal[ZMessaging]].map(_.convsUi)
    bind[Signal[UserService]] to inject[Signal[ZMessaging]].map(_.users)
    bind[Signal[UserSearchService]] to inject[Signal[ZMessaging]].map(_.userSearch)
    bind[Signal[ConversationStorage]] to inject[Signal[ZMessaging]].map(_.convsStorage)
    bind[Signal[UsersStorage]] to inject[Signal[ZMessaging]].map(_.usersStorage)
    bind[Signal[MembersStorage]] to inject[Signal[ZMessaging]].map(_.membersStorage)
    bind[Signal[OtrClientsStorage]] to inject[Signal[ZMessaging]].map(_.otrClientsStorage)
    bind[Signal[AssetsStorage]] to inject[Signal[ZMessaging]].map(_.assetsStorage)
    bind[Signal[MessagesStorage]] to inject[Signal[ZMessaging]].map(_.messagesStorage)
    bind[Signal[ImageLoader]] to inject[Signal[ZMessaging]].map(_.imageLoader)
    bind[Signal[MessagesService]] to inject[Signal[ZMessaging]].map(_.messages)
    bind[Signal[IntegrationsService]] to inject[Signal[ZMessaging]].map(_.integrations)
    bind[Signal[UserPreferences]] to inject[Signal[ZMessaging]].map(_.userPrefs)
    bind[Signal[MessageAndLikesStorage]] to inject[Signal[ZMessaging]].map(_.msgAndLikes)
    bind[Signal[ReadReceiptsStorage]] to inject[Signal[ZMessaging]].map(_.readReceiptsStorage)
    bind[Signal[ReactionsStorage]] to inject[Signal[ZMessaging]].map(_.reactionsStorage)
    bind[Signal[ForbidsStorage]] to inject[Signal[ZMessaging]].map(_.forbidsStorage)
    bind[Signal[FCMNotificationStatsService]] to inject[Signal[ZMessaging]].map(_.fcmNotStatsService)
    bind[Signal[AliasStorage]] to inject[Signal[ZMessaging]].map(_.aliasStorage)

    // old controllers
    // TODO: remove controller factory, reimplement those controllers
    bind[IControllerFactory] toProvider controllerFactory
    bind[IPickUserController] toProvider controllerFactory.getPickUserController
    bind[IConversationScreenController] toProvider controllerFactory.getConversationScreenController
    bind[INavigationController] toProvider controllerFactory.getNavigationController
    bind[IUserPreferencesController] toProvider controllerFactory.getUserPreferencesController
    bind[ISingleImageController] toProvider controllerFactory.getSingleImageController
    bind[ISlidingPaneController] toProvider controllerFactory.getSlidingPaneController
    bind[IDeviceUserController] toProvider controllerFactory.getDeviceUserController
    bind[IGlobalLayoutController] toProvider controllerFactory.getGlobalLayoutController
    bind[ILocationController] toProvider controllerFactory.getLocationController
    bind[ICameraController] toProvider controllerFactory.getCameraController
    bind[IConfirmationController] toProvider controllerFactory.getConfirmationController

    // global controllers
    bind[BackendController] to new BackendController()
    bind[WebSocketController] to new WebSocketController
    bind[CrashController] to new CrashController
    bind[AccentColorController] to new AccentColorController()
    bind[PasswordController] to new PasswordController()
    bind[CallController] to new CallController()
    bind[GlobalCameraController] to new GlobalCameraController(new AndroidCameraFactory)
    bind[SoundController] to new SoundControllerImpl()
    bind[ThemeController] to new ThemeController
    bind[SpinnerController] to new SpinnerController()

    bind[UiStorage] to new UiStorage()

    bind[SyncRequestService] to new WorkManagerSyncRequestService()

    //notifications
    bind[MessageNotificationsController] to new MessageNotificationsController()
    bind[ImageNotificationsController] to new ImageNotificationsController()
    bind[CallingNotificationsController] to new CallingNotificationsController()

    bind[GlobalTrackingController] to new GlobalTrackingController()
    bind[PreferencesController] to new PreferencesController()
    bind[ImageController] to new ImageController()
    bind[UserAccountsController] to new UserAccountsController()

    bind[LocalThumbnailCache] to LocalThumbnailCache(ctx)

    bind[SharingController] to new SharingController()
    bind[ConversationController] to new ConversationController()
    bind[ConversationsUiControllerImpl] to new ConversationsUiControllerImpl()
    bind[NavigationController] to new NavigationController()
    bind[InvitationsController] to new InvitationsController()
    bind[ClientsController] to new ClientsController()
    bind[CreateTeamController] to new CreateTeamController()
    bind[CreateConversationController] to new CreateConversationController()

    // current conversation data
    bind[Signal[ConversationData]] to inject[ConversationController].currentConv

    // selected conversation id
    bind[Signal[Option[ConvId]]] to inject[Signal[SelectedConversationService]].flatMap(_.selectedConversationId)

    // accent color
    bind[Signal[AccentColor]] to inject[AccentColorController].accentColor

    // drafts
    bind[DraftMap] to new DraftMap()

    bind[MessagesController] to new MessagesController()

    bind[ClipboardUtils] to new ClipboardUtils(ctx)
    bind[ExternalFileSharing] to new ExternalFileSharing(ctx)

    bind[DeepLinkService] to new DeepLinkService()

    bind[DevicesDialogController] to new DevicesDialogController()
  }

  def controllers(implicit ctx: WireContext) = new Module {

    private implicit val eventContext = ctx.eventContext

    bind[Activity] to {
      def getActivity(ctx: Context): Activity = ctx match {
        case a: Activity => a
        case w: ContextWrapper => getActivity(w.getBaseContext)
      }

      getActivity(ctx)
    }
    bind[FragmentManager] to inject[Activity].asInstanceOf[FragmentActivity].getSupportFragmentManager

    bind[KeyboardController] to new KeyboardController()
    bind[CallStartController] to new CallStartController()
    bind[AssetsController] to new AssetsController()
    bind[BrowserController] to new BrowserController()
    bind[MessageViewFactory] to new MessageViewFactory()
    bind[ReplyController] to new ReplyController()

    bind[ScreenController] to new ScreenController()
    bind[MessageActionsController] to new MessageActionsController()
    bind[LikesController] to new LikesController()
    bind[CollectionController] to new CollectionController()
    bind[BackStackNavigator] to new BackStackNavigator()

    bind[CursorController] to new CursorController()
    bind[ConversationListController] to new ConversationListController()
    bind[ParticipantsController] to new ParticipantsController()
    bind[UsersController] to new UsersController()

    bind[ErrorsController] to new ErrorsController()

    /**
      * Since tracking controllers will immediately instantiate other necessary controllers, we keep them separated
      * based on the activity responsible for generating their events (we don't want to instantiate an uneccessary
      * MessageActionsController in the CallingActivity, for example
      */
    bind[UiTrackingController] to new UiTrackingController()

    bind[MessagePagedListController] to new MessagePagedListController()
  }

  protected def clearOldVideoFiles(context: Context): Unit = {
    val oneWeekAgo = Calendar.getInstance
    oneWeekAgo.add(Calendar.DAY_OF_YEAR, -7)
    Option(context.getExternalCacheDir).foreach { dir =>
      Option(dir.listFiles).fold[List[File]](Nil)(_.toList).foreach { file =>
        val fileName = file.getName
        val fileModified = Calendar.getInstance()
        fileModified.setTimeInMillis(file.lastModified)
        if (fileName.startsWith("VID_") &&
          fileName.endsWith(".mp4") &&
          fileModified.before(oneWeekAgo)
        ) file.delete()
      }
    }
  }
}

class WireApplication extends MultiDexApplication with WireContext with Injectable {
  type NetworkSignal = Signal[NetworkMode]

  import WireApplication._

  import scala.concurrent.ExecutionContext.Implicits.global

  WireApplication.APP_INSTANCE = this

  override def eventContext: EventContext = EventContext.Global

  lazy val module: Injector = Global

  protected var controllerFactory: IControllerFactory = _

  def contextModule(ctx: WireContext): Injector = controllers(ctx)

  private def enableTLS12OnOldDevices(): Unit = {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
      try {
        ProviderInstaller.installIfNeeded(getApplicationContext)
        val sslContext = SSLContext.getInstance("TLSv1.2")
        sslContext.init(null, null, null)
        sslContext.createSSLEngine
      } catch {
        case NonFatal(error) =>
          verbose(l"Error while enabling TLS 1.2 on old device. $error")
      }
    }
  }

  override def onCreate(): Unit = {
    super.onCreate()

    SafeBase64.setDelegate(new AndroidBase64Delegate)

    ZMessaging.globalReady.future.onSuccess {
      case _ =>
        if (BuildConfig.DEBUG) {
          InternalLog.setLogsService(inject[LogsService])
          InternalLog.add(new AndroidLogOutput(showSafeOnly = !BuildConfig.DEBUG))
        }
    }

    verbose(l"onCreate")

    enableTLS12OnOldDevices()

    controllerFactory = new ControllerFactory(getApplicationContext)

    ensureInitialized()

    messageCountListener()

  }

  private def messageCountListener(): Unit = {
    (for {
      Some(_) <- inject[Signal[Option[UserId]]]
      cStorage <- inject[Signal[ConversationStorage]]
      conversations <- cStorage.contents
    } yield conversations).orElse(Signal(Map.empty[ConvId, ConversationData])).onUi { conversations =>
      var count = 0
      conversations.values.filter(_.isActive)
        .filter(ConversationListController.messageCountFilter)
        .foreach { it =>
          count = count + it.unreadCount.total
        }
      ShortcutBadger.applyCount(ZApplication.getInstance().getZApplication, count)
    }(eventContext)
  }

  def ensureInitialized(): Boolean = {
    if (Option(ZMessaging.currentGlobal).isDefined) {
      verbose(l"the app is initialized, nothing to do here")
      true // the app is initialized, nothing to do here
    } else {
      verbose(l"ensureInitialized getStoredBackendConfig")
      try {
        inject[BackendController].getStoredBackendConfig.fold(false) { config =>
          ensureInitialized(config)
          true
        }
      } catch {
        case t: Throwable =>
          verbose(l"Failed to initialize the app ,t:${t.getMessage}")
          false
      }
    }
  }

  def ensureInitialized(isForce: Boolean): Boolean = {
    if (!isForce && Option(ZMessaging.currentGlobal).isDefined) {
      verbose(l"the app is initialized, nothing to do here isForce:$isForce")
      true // the app is initialized, nothing to do here
    } else {
      verbose(l"ensureInitialized getStoredBackendConfig isForce:$isForce")
      try {
        inject[BackendController].getStoredBackendConfig.fold(false) { config =>
          ensureInitialized(config)
          true
        }
      } catch {
        case t: Throwable =>
          verbose(l"Failed to initialize the app ,t:${t.getMessage}")
          false
      }
    }
  }

  def ensureInitialized(backend: BackendConfig) = {
    verbose(l"ensureInitialized BackendConfig backend: $backend")
    try {
      JobManager.create(this).addJobCreator(new JobCreator {
        override def create(tag: String) = {
          verbose(l"ensureInitialized addJobCreator tag: $tag")
          if (tag.contains(FetchJob.Tag)) new FetchJob
          else if (tag.contains(PushTokenCheckJob.Tag)) new PushTokenCheckJob
          else if (tag.contains(FetchSystemNotificationJob.Tag)) new FetchSystemNotificationJob
          else null
        }
      })
    } catch {
      case t: Throwable =>
        verbose(l"Failed to initialize the app backend ,t:${t.getMessage}")
    }

    val prefs = GlobalPreferences(this)
    val googleApi = GoogleApiImpl(this, backend, prefs)

    ZMessaging.onCreate(
      this,
      backend,
      prefs,
      googleApi,
      null, //TODO: Use sync engine's version for now
      inject[ConversationsUiControllerImpl],
      inject[MessageNotificationsController]
    )

    inject[NotificationManagerWrapper]
    inject[ImageNotificationsController]
    inject[CallingNotificationsController]

    //    //TODO [AN-4942] - is this early enough for app launch events?
    inject[GlobalTrackingController]
    inject[CrashController] //needs to register crash handler
    inject[ThemeController]
    inject[PreferencesController]
    Future(clearOldVideoFiles(getApplicationContext))(Threading.Background)
  }

  override def onTerminate(): Unit = {
    controllerFactory.tearDown()
    controllerFactory = null
    if (Build.VERSION.SDK_INT > 22) {
      RenderScript.releaseAllContexts()
    } else {
      inject[RenderScript].destroy()
    }

    InternalLog.flush()

    super.onTerminate()
  }

  def checkDayNightMode(): Unit ={
    try {
      val isFollowSystem = ThemeUtils.isFollowSystem(getApplicationContext)
      LogUtils.d("JACK8", "isFollowSystem:"+isFollowSystem)
      if(isFollowSystem){
          sendBroadcast(new Intent(Constants.ACTION_CHANGE_THEME))
      }
    }catch {
      case t: Throwable =>
        LogUtils.e("JACK8","checkDayNightMode,message:"+t.getMessage)
    }
  }

  override def onConfigurationChanged(newConfig: Configuration): Unit = {
    super.onConfigurationChanged(newConfig)
    LogUtils.d("JACK8","onConfigurationChanged")
    checkDayNightMode()
  }
}

