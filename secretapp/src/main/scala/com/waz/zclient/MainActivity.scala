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
package com.waz.zclient

import android.annotation.SuppressLint
import android.app.{Activity, ActivityManager}
import android.content.Intent._
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.{ComponentName, Context, Intent}
import android.graphics.{Paint, PixelFormat}
import android.os.{Build, Bundle, PowerManager}
import android.text.TextUtils
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.jsy.common.acts.PreferencesAdaptActivity
import com.jsy.common.acts.scan.ScanAuthorizeLoginActivity
import com.jsy.common.event.AccountDataEvent
import com.jsy.common.fragment.SearchFragment
import com.jsy.common.httpapi._
import com.jsy.common.model._
import com.jsy.common.moduleProxy.ProxyMainActivity
import com.jsy.common.utils.rxbus2.RxBus
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.ActivityContainner
import com.waz.content.UserPreferences
import com.waz.content.UserPreferences._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus.{apply => _}
import com.waz.model._
import com.waz.service.AccountManager.ClientRegistrationState.{LimitReached, PasswordMissing, Registered, Unregistered}
import com.waz.service.ZMessaging.clock
import com.waz.service.{AccountManager, AccountsService, NetworkModeService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal, Subscription}
import com.waz.utils.{RichInstant, returning}
import com.waz.zclient.Intents.{RichIntent, _}
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient.appentry.AppEntryActivity
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController, PasswordController}
import com.waz.zclient.common.controllers.{SharingController, UserAccountsController}
import com.waz.zclient.controllers.navigation.{NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListManagerFragment
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.deeplinks.DeepLink.{logTag => _}
import com.waz.zclient.fragment.AppUpdateInfoFragment
import com.waz.zclient.fragments.ConnectivityFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.preferences.dialogs.ChangeHandleFragment
import com.waz.zclient.tracking.UiTrackingController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.StringUtils.TextDrawing
import com.waz.zclient.utils.{BackendController, Emojis, FlavorUtils, IntentUtils, MainActivityUtils, SpUtils, StringUtils}
import com.waz.zclient.views.LoadingIndicatorView
import okhttp3.{Call, Callback, Request, Response}
import org.json.JSONObject

import java.io.IOException
import java.net.HttpURLConnection
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.{Breaks, NonFatal}

class MainActivity extends BaseActivity
  with CallingBannerActivity
  with NavigationControllerObserver
  with OtrDeviceLimitFragment.Container
  with SetHandleFragment.Container
  with ProxyMainActivity
  with SearchFragment.Container
  with AppUpdateInfoFragment.Container
  with DerivedLogTag {

  implicit val cxt: MainActivity = this

  import MainActivity._
  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val account = inject[Signal[Option[AccountManager]]]
  private lazy val accountsService = inject[AccountsService]
  private lazy val sharingController = inject[SharingController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val spinnerController = inject[SpinnerController]
  private lazy val passwordController = inject[PasswordController]
  //private lazy val deepLinkService = inject[DeepLinkService]
  private lazy val usersController = inject[UsersController]
  private lazy val userPreferences = inject[Signal[UserPreferences]]
  private lazy val PERMISSION_REQUEST = 1

  private var subscriptions = Set.empty[Subscription]

  override def onAttachedToWindow(): Unit = {
    super.onAttachedToWindow()
    getWindow.setFormat(PixelFormat.RGBA_8888)
  }

  private var accountId: UserId = _
  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }
  currentAccount.map(_.id).onUi { uid =>
    verbose(l"userNoticeStorage updateGroupData++++++++++++")
    convController.updateGroupData()
  }

  private lazy val convController = inject[ConversationController]

  override protected def keyboardEnable(): Boolean = false

  override def onCreate(savedInstanceState: Bundle) = {
    Option(getActionBar).foreach(_.hide())
    super.onCreate(savedInstanceState)
    verbose(l"onCreate====savedInstanceState")
    //Prevent drawing the default background to reduce overdraw
    //getWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT))
    setContentView(R.layout.main)

    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)

    val fragmentManager = getSupportFragmentManager
    initializeControllers()

    if (savedInstanceState == null) {
      val fragmentTransaction = fragmentManager.beginTransaction
      fragmentTransaction.add(R.id.fl__offline__container, ConnectivityFragment(), ConnectivityFragment.FragmentTag)
      fragmentTransaction.commit
    } else getControllerFactory.getNavigationController.onActivityCreated(savedInstanceState)

    accentColorController.accentColor.map(_.color).onUi(
      getControllerFactory.getUserPreferencesController.setLastAccentColor
    )

    for {
      zmessage <- zms.head
    } yield {
      verbose(l"zms55:${zmessage}")
      EventStream.union(
        zmessage.userNoticeStorage.onAdded.map(_ => true),
        zmessage.userNoticeStorage.onUpdated.map(_ => true),
        zmessage.userNoticeStorage.onDeleted.map(_ => true)
      ) onUi {
        _ =>
          verbose(l"userNoticeStorage data onChanged++++++++++++")
          convController.updateGroupData()
      }
      val oldConvIdStr = SpUtils.getString(cxt, SpUtils.SP_NAME_FOREVER_SAVED, accountId.str, "")
      verbose(l"+++++++++++++++++GroupData:${oldConvIdStr}")
      if (!TextUtils.isEmpty(oldConvIdStr)) {
        verbose(l"+++++++++++++++++migrateGroupData")
        convController.addGroupData(oldConvIdStr.split('|').map(ConvId(_)))
        SpUtils.putString(cxt, SpUtils.SP_NAME_FOREVER_SAVED, accountId.str, "")
      }
    }

    handleIntent(getIntent)

    accountsService.activeAccountManager.onUi {
      case Some(am) => // switchAccount
        am.clientId.head.foreach {
          case Some(value) =>
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_CLIENTID, value.str)
          case _ =>
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_CLIENTID, "")
        }
      case None =>
        getControllerFactory.getPickUserController.hideUserProfile()
        finish()
        startActivity(returning(new Intent(this, classOf[AppEntryActivity]))(_.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)))
    }

    currentAccount.onUi {
      case accountData: AccountData if accountData != null && accountData.accessToken.nonEmpty && accountData.cookie.isValid =>
        if (accountData.accessToken.isDefined) {
          accountData.accessToken.foreach { x =>
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, x.accessToken)
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, x.tokenType)
          }
        } else {
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, "")
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, "")
        }

        SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_COOKIES, accountData.cookie.str)
        SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_USERID, accountData.id.str)
        accountData.name.foreach { name =>
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_USERNAME, name)
        }
        accountData.handle.foreach { handle =>
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_HANDLE, handle.string)
        }
        if (accountData.rAssetId.isDefined) {
          accountData.rAssetId.foreach { id =>
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_REMOTE_ASSET_ID, id)
          }
        } else {
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_REMOTE_ASSET_ID, "")
        }
        if (accountData.email.isDefined) {
          accountData.email.foreach { x =>
            SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_EMAIL, x.str)
          }
        } else {
          SpUtils.putString(this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_EMAIL, "")
        }
        if (accountId == null) {
          startFirstFragment()
        } else if (accountId != accountData.id) {
          ActivityContainner.finishAndRemoveAllBeside(classOf[MainActivity].getName)
          startFirstFragment()
          RxBus.getDefault.post(new AccountDataEvent(accountData.id.str))
        } else {
        }
        if (accountId != accountData.id) {
          accountId = accountData.id
        }
      case _ =>
    }

    val loadingIndicator = findViewById[LoadingIndicatorView](R.id.progress_spinner)

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedIsDarkTheme) =>
        themeController.darkThemeSet.head.foreach(theme => loadingIndicator.show(animation, forcedIsDarkTheme.getOrElse(theme), 300))(Threading.Ui)
      case Hide(Some(message)) => loadingIndicator.hideWithMessage(message, 750)
      case Hide(_) => loadingIndicator.hide()
    }

    conversationController.convChanged.map(_.requester).onUi {
      case
        ConversationChangeRequester.START_CONVERSATION |
        ConversationChangeRequester.CONVERSATION_LIST |
        ConversationChangeRequester.INBOX |
        ConversationChangeRequester.INTENT =>
        toConversation()
      case ConversationChangeRequester.BLOCK_USER =>
      case requester => //match may not be exhaustive.
        verbose(l"ignore requester:$requester")
    }

    inject[BackendController].getCustomBackend()
    checkVersionUpdate(true)

    showUpdateInfoFragment()

    if (null == savedInstanceState) {
      startNotifiSetting()
      checkRequestPermissions()
    }

    subscriptions += inject[NetworkModeService].isOnline.onUi {
      case true =>
        zms.head.map(_.wsPushService.restartWebSocket())
    }(EventContext.Global)
  }


  private def toConversation(): Unit = {
    ActivityContainner.finishAndRemoveAllBeside(Array(this.getClass.getName, classOf[ConversationActivity].getName))

    ConversationActivity.start(this)
  }


  override def onStart(): Unit = {
    getControllerFactory.getNavigationController.addNavigationControllerObserver(this)

    super.onStart()

    if (!getControllerFactory.getUserPreferencesController.hasCheckedForUnsupportedEmojis(Emojis.VERSION))
      Future(checkForUnsupportedEmojis())(Threading.Background)

    val intent = getIntent
    intent.setData(null)
    setIntent(intent)
  }

  def startNotifiSetting(): Unit = {
    import com.google.firebase.messaging.FirebaseMessaging
    FirebaseMessaging.getInstance.setAutoInitEnabled(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val am: ActivityManager = getSystemService(Context.ACTIVITY_SERVICE).asInstanceOf[ActivityManager]
      if (am.isBackgroundRestricted()) {
        verbose(l"startNotifiSetting isBackgroundRestricted = true")
      }
    }
    val pm: PowerManager = getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
    if (pm.isPowerSaveMode()) {
      verbose(l"startNotifiSetting isPowerSaveMode = true")
    }
  }

  override protected def onResume(): Unit = {
    super.onResume()
    checkVersionUpdate(false)
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.checkGooglePlayServicesAvailable(this))
  }


  override def onDestroy(): Unit = {
    subscriptions.foreach(_.destroy())
    super.onDestroy()
  }

  private def openSignUpPage(ssoToken: Option[String] = None): Unit = {
    verbose(l"openSignUpPage(${ssoToken.map(showString)})")
    userAccountsController.ssoToken ! ssoToken
    startActivity(new Intent(getApplicationContext, classOf[AppEntryActivity]))
    finish()
  }

  private var isFinished = false

  private var hasShowedUpdateToast = false

  private var isCheckingVersion: Boolean = false

  private def checkVersionUpdate(forceCheck: Boolean = false): Unit = {
    def doCheck(): Unit = {
      if (isCheckingVersion) {
      } else {
        isCheckingVersion = true
        SpecialServiceAPI.getInstance().get(ImApiConst.APP_VERSION_UPDATE_INFO, new SimpleHttpListener[VersionUpdateInfo] {

          override def onFail(code: Int, err: String): Unit = {
            verbose(l"$TAG code-$code err->$err")
          }

          override def onSuc(r: VersionUpdateInfo, orgJson: String): Unit = {
            verbose(l"$TAG orgJson-$orgJson")
            SpUtils.putLong(MainActivity.this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_CHECK_VERSION_UPDATE_TIME, System.currentTimeMillis())
            if (r != null) {
              val oldestAccepted = r.oldestAccepted
              val localVersion = getPackageManager.getPackageInfo(getPackageName(), 0).versionCode;
              if (localVersion < oldestAccepted) {
                if (FlavorUtils.isGooglePlay) {
                  if (!hasShowedUpdateToast) {
                    hasShowedUpdateToast = true
                    ForceUpdateActivity.startSelf(MainActivity.this, r.android_url, isForceUpdate = false)
                  }
                } else {
                  val maxVersion = if (r.blacklisted == null || r.blacklisted.size() == 0) 0 else r.blacklisted.asScala.max.toInt
                  if (maxVersion > localVersion) {
                    requestActualUrl(r.android_url, forceUpdate = true)
                  } else {
                    if (!hasShowedUpdateToast) {
                      hasShowedUpdateToast = true
                      requestActualUrl(r.android_url, forceUpdate = false)
                    }
                  }
                }
              } else {
              }
            } else {
            }

          }

          override def onComplete(): Unit = {
            super.onComplete()
            isCheckingVersion = false
          }
        })
      }
    }

    if (forceCheck) {
      doCheck()
    } else {
      val lastCheckVersionUpdateTime = SpUtils.getLong(this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_CHECK_VERSION_UPDATE_TIME, 0)
      if (System.currentTimeMillis() - lastCheckVersionUpdateTime > INVARAL_CHECK_UPDATE_VERSION) {
        doCheck()
      } else {
      }
    }
  }

  /**
   * get the real download url
   * @param fileUrl
   * @param forceUpdate true means force update version
   */
  private def requestActualUrl(fileUrl: String, forceUpdate: Boolean): Unit = {
    RetrofitUtil.initClient().newBuilder().followRedirects(false)
      .build().newCall(new Request.Builder().head().url(fileUrl).build()).enqueue(new Callback {
      override def onFailure(call: Call, e: IOException): Unit = {
        ForceUpdateActivity.startSelf(MainActivity.this, fileUrl, forceUpdate)
        if (forceUpdate) finish()
      }

      override def onResponse(call: Call, response: Response): Unit = {
        var tempUrl = fileUrl
        if (response.code() == HttpURLConnection.HTTP_MOVED_TEMP) {
          val actualFileUrl = response.header("Location")
          if (actualFileUrl != null && actualFileUrl.nonEmpty) {
            tempUrl = actualFileUrl
          }
        }
        ForceUpdateActivity.startSelf(MainActivity.this, tempUrl, forceUpdate)
        if (forceUpdate) finish()
      }
    })
  }

  /**
    * [[ProxyMainActivity.startFirstFragment]]
    */
  override def startFirstFragment(): Unit = {
    verbose(l"startFirstFragment, intent: ${RichIntent(getIntent)}")
    account.head.flatMap {
      case Some(am) =>
        am.getOrRegisterClient().map {
          case Right(Registered(_)) =>
            for {
              _ <- passwordController.setPassword(None)
              z <- zms.head
              self <- z.users.selfUser.head
              isLogin <- z.userPrefs(IsLogin).apply()
              isNewClient <- z.userPrefs(IsNewClient).apply()
              pendingPw <- z.userPrefs(PendingPassword).apply()
              pendingEmail <- z.userPrefs(PendingEmail).apply()
              ssoLogin <- accountsService.activeAccount.map(_.exists(_.ssoId.isDefined)).head
            } yield {
              val (f, t) =
                if (ssoLogin) {
                  if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                  else {
                    (new ConversationListManagerFragment, ConversationListManagerFragment.Tag)
                  }
                }
                else if (self.email.isDefined && pendingPw) (SetOrRequestPasswordFragment(self.email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get), VerifyEmailFragment.Tag)
                else if (self.email.isEmpty && isLogin && isNewClient && self.phone.isDefined)
                  (AddEmailFragment(), AddEmailFragment.Tag)
                else if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                else {
                  (new ConversationListManagerFragment, ConversationListManagerFragment.Tag)

                }
              replaceMainFragment(f, t, addToBackStack = false)
            }

          case Right(LimitReached) =>
            for {
              self <- am.getSelf
              pendingPw <- am.storage.userPrefs(PendingPassword).apply()
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
              ssoLogin <- accountsService.activeAccount.map(_.exists(_.ssoId.isDefined)).head
            } yield {
              val (f, t) =
                if (ssoLogin) (OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.Tag)
                else if (self.email.isDefined && pendingPw) (SetOrRequestPasswordFragment(self.email.get), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get), VerifyEmailFragment.Tag)
                else if (self.email.isEmpty) (AddEmailFragment(), AddEmailFragment.Tag)
                else (OtrDeviceLimitFragment.newInstance, OtrDeviceLimitFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }

          case Right(PasswordMissing) =>
            for {
              self <- am.getSelf
              pendingEmail <- am.storage.userPrefs(PendingEmail).apply()
              ssoLogin <- accountsService.activeAccount.map(_.exists(_.ssoId.isDefined)).head
            } {
              val (f, t) =
                if (ssoLogin) {
                  if (self.handle.isEmpty) (SetHandleFragment(), SetHandleFragment.Tag)
                  else {
                    (new ConversationListManagerFragment, ConversationListManagerFragment.Tag)
                  }
                }
                else if (self.email.isDefined) (SetOrRequestPasswordFragment(self.email.get, hasPassword = true), SetOrRequestPasswordFragment.Tag)
                else if (pendingEmail.isDefined) (VerifyEmailFragment(pendingEmail.get, hasPassword = true), VerifyEmailFragment.Tag)
                else (AddEmailFragment(hasPassword = true), AddEmailFragment.Tag)
              replaceMainFragment(f, t, addToBackStack = false)
            }
          case Right(Unregistered) => warn(l"This shouldn't happen, going back to sign in..."); Future.successful(openSignUpPage())
          case Left(_) => showGenericErrorDialog()
        }
      case _ =>
        warn(l"No logged in account, sending to Sign in")
        Future.successful(openSignUpPage())
    }
  }

  /**
    * [[ProxyMainActivity.replaceMainFragment()]]
    *
    * @param fragment
    * @param newTag
    * @param reverse
    * @param addToBackStack
    */
  override def replaceMainFragment(fragment: Fragment, newTag: String, reverse: Boolean = false, addToBackStack: Boolean = true): Unit = {

    import scala.collection.JavaConverters._
    val oldTag = getSupportFragmentManager.getFragments.asScala.toList.flatMap(Option(_)).lastOption.flatMap {
      case _: SetOrRequestPasswordFragment => Some(SetOrRequestPasswordFragment.Tag)
      case _: VerifyEmailFragment => Some(VerifyEmailFragment.Tag)
      case _: AddEmailFragment => Some(AddEmailFragment.Tag)
      case _ => None
    }
    verbose(l"replaceMainFragment: ${oldTag.map(redactedString)} -> ${redactedString(newTag)}")

    val (in, out) = (MainActivity.isSlideAnimation(oldTag, newTag), reverse) match {
      case (true, true) => (R.anim.fragment_animation_second_page_slide_in_from_left_no_alpha, R.anim.fragment_animation_second_page_slide_out_to_right_no_alpha)
      case (true, false) => (R.anim.fragment_animation_second_page_slide_in_from_right_no_alpha, R.anim.fragment_animation_second_page_slide_out_to_left_no_alpha)
      case _ => (R.anim.fade_in, R.anim.fade_out)
    }

    val frag = Option(getSupportFragmentManager.findFragmentByTag(newTag)) match {
      case Some(f) => returning(f)(_.setArguments(fragment.getArguments))
      case _ => fragment
    }

    val transaction = getSupportFragmentManager
      .beginTransaction
      .setCustomAnimations(in, out)
      .replace(R.id.fl_main_content, frag, newTag)
    if (addToBackStack) transaction.addToBackStack(newTag)
    transaction.commit
    spinnerController.hideSpinner()
  }

  /**
    * [[ProxyMainActivity.startScanLoginAct()]]
    */
  override def startScanLoginAct(): Unit = {
    startActivityForResult(new Intent(this, classOf[ScanAuthorizeLoginActivity]), MainActivityUtils.REQUEST_CODE_SCAN_LOGIN)
  }

  override def showIncomingPendingConnectRequest(conv: ConvId): Unit = {
    verbose(l"SearchFragment showIncomingPendingConnectRequest $conv")
    inject[ConversationController].selectConv(conv, ConversationChangeRequester.INBOX) //todo stop doing this!!!
  }


  def removeFragment(fragment: Fragment): Unit = {
    val transaction = getSupportFragmentManager
      .beginTransaction
      .remove(fragment)
    transaction.commit
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    getControllerFactory.getNavigationController.onSaveInstanceState(outState)
    super.onSaveInstanceState(outState)
  }

  override def onStop(): Unit = {
    super.onStop()
    getControllerFactory.getNavigationController.removeNavigationControllerObserver(this)
  }

  override def onBackPressed(): Unit = {
    def matchFrag(f: Option[Fragment]) {
      f.fold {
        super.onBackPressed()
      } {
        case f: com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener if f.onBackPressed() => //
        case _ => super.onBackPressed()
      }
    }

    val authLoginFrag = Option(getSupportFragmentManager.findFragmentById(R.id.fl__auth_login))
    if (authLoginFrag.nonEmpty) {
      matchFrag(authLoginFrag)
    } else {
      val updateInfoFrag = Option(getSupportFragmentManager.findFragmentById(R.id.fl__app_update_info))
      if (updateInfoFrag.nonEmpty) {
        matchFrag(updateInfoFrag)
      } else {
        val mainFrag = Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content))
        matchFrag(mainFrag)
      }
    }

  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    Option(ZMessaging.currentGlobal).foreach(_.googleApi.onActivityResult(requestCode, resultCode))
    Option(getSupportFragmentManager.findFragmentById(R.id.fl_main_content)).foreach(_.onActivityResult(requestCode, resultCode, data))

    if (requestCode == MainActivityUtils.REQUEST_CODE_ManageDevices) {
      if (resultCode == Activity.RESULT_OK) {
        startFirstFragment()
      }
    } else if (requestCode == MainActivityUtils.REQUET_CODE_SwitchAccountCode && data != null) {
      Option(data.getStringExtra(MainActivityUtils.INTENT_KEY_SwitchAccountExtra)).foreach { extraStr =>
        accountsService.setAccount(Some(UserId(extraStr)))
      }
    } else if (resultCode == Activity.RESULT_OK && requestCode == MainActivityUtils.REQUEST_CODE_SCAN_LOGIN) {
      if (data != null) {
        qrCodeLogin(data.getStringExtra("scan_result"))
      } else {
      }
    }
  }

  private def checkRequestPermissions(): Unit = {
    var register = true
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val permissions = Array[String](android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_PHONE_STATE)
      val loop = new Breaks
      loop.breakable {
        for (permission <- permissions) {
          if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions, PERMISSION_REQUEST)
            register = false
            loop.break
          }
        }
      }
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, keys: Array[String], grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, keys, grantResults)
    if (requestCode == PERMISSION_REQUEST) {
      var granted = false
      for (grantResult <- grantResults) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) granted = true
      }
    }
  }

  private def qrCodeLogin(content: String): Unit = {
    val qrCodeContentModel = QrCodeContentModel.parseQrCodeForContent(content)
    val qrType = if (null == qrCodeContentModel) "" else qrCodeContentModel.getType
    if (QrCodeContentModel.SECRET_LOGIN_PREFIX.equalsIgnoreCase(qrType)
      || QrCodeContentModel.QRCODE_TYPE_LOGIN.equalsIgnoreCase(qrType)) {
      val qrCode = if (QrCodeContentModel.QRCODE_TYPE_LOGIN.equalsIgnoreCase(qrType)) {
        val uriPaths = qrCodeContentModel.getUriPaths
        if (null != uriPaths && uriPaths.nonEmpty) {
          uriPaths.head
        } else ""
      } else qrCodeContentModel.getLoginKey
      verbose(l"qrCodeLogin qrCode:$qrCode, qrType:$qrType, qrContent:$content")
      currentAccount.currentValue.foreach { accountData =>
        showProgressDialog(R.string.empty_string)
        SpecialServiceAPI.getInstance().put(ImApiConst.SCAN_LOGIN, qrCode, new SimpleHttpListener[HttpResponseBaseModel] {

          override def onFail(code: Int, err: String): Unit = {
            verbose(l"qrCodeLogin code:$code  err:$err")
            showToast(getString(R.string.secret_scan_login_failure))
          }

          override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
            verbose(l"qrCodeLogin onSuc orgJson:$orgJson")
            showToast(getString(R.string.secret_scan_login_successful))
          }

          override def onComplete(): Unit = {
            super.onComplete()
            dismissProgressDialog()
          }
        })
      }
    } else {
      showToast(getString(R.string.invalid_qrcode))
    }
  }

  override protected def onNewIntent(intent: Intent): Unit = {
    super.onNewIntent(intent)
    verbose(l"onNewIntent: ${RichIntent(intent)}")

    if (IntentUtils.isPasswordResetIntent(intent)) onPasswordWasReset()

    setIntent(intent)

    if (MainActivityUtils.INTENT_KEY_FROM_SCAN_PAYMENT.equals(intent.getStringExtra(MainActivityUtils.INTENT_KEY_FROM_SCAN_PAYMENT))) {
      val userId = intent.getSerializableExtra(classOf[UserId].getSimpleName).asInstanceOf[UserId]
      if (userId != null) {
        userAccountsController.getOrCreateAndOpenConvFor(userId)
      }
    }
    val toConversationModel = intent.getSerializableExtra(classOf[ToConversationModel].getSimpleName).asInstanceOf[ToConversationModel]
    if (toConversationModel != null) {
      if (!TextUtils.isEmpty(toConversationModel.user_id)) {
        userAccountsController.getOrCreateAndOpenConvFor(UserId(toConversationModel.user_id))
      } else {
        userAccountsController.startConversation(RConvId(toConversationModel.group_id))
      }
    }

    handleIntent(intent) map {
      case false => startFirstFragment()
      case _ =>
    }
  }

  private def initializeControllers(): Unit = {
    //Ensure tracking is started
    inject[UiTrackingController]
    inject[KeyboardController]
    // Here comes code for adding other dependencies to controllers...
    //    getControllerFactory.getNavigationController.setIsLandscape(isInLandscape(this))
  }

  private def onPasswordWasReset() =
    for {
      Some(am) <- accountsService.activeAccountManager.head
      _ <- am.auth.onPasswordReset(emailCredentials = None)
    } yield {}

  def handleIntent(intent: Intent): Future[Boolean] = {
    verbose(l"handleIntent: ${RichIntent(intent)}")

    intent match {
      case NotificationIntent(accountId, convId, startCall) =>
        verbose(l"notification intent, accountId: $accountId, convId: $convId")
        val switchAccount = {
          accountsService.activeAccount.head.flatMap {
            case Some(acc) if intent.accountId.contains(acc.id) => Future.successful(false)
            case _ => accountsService.setAccount(intent.accountId).map(_ => true)
          }
        }

        val res = switchAccount.flatMap { _ =>
          (intent.convId match {
            case Some(id) => conversationController.switchConversation(id, startCall)
            case _ => Future.successful({})
          }).map(_ => clearIntent())(Threading.Ui)
        }

        try {
          val t = clock.instant()
          if (Await.result(switchAccount, 2.seconds)) verbose(l"Account switched before resuming activity lifecycle. Took ${t.until(clock.instant()).toMillis} ms")
        } catch {
          case NonFatal(e) => error(l"Failed to switch accounts", e)
        }

        res.map(_ => true)

      case SharingIntent() =>
        (for {
          convs <- sharingController.targetConvs.head
          exp <- sharingController.ephemeralExpiration.head
          _ <- sharingController.sendContent(this)
          _ <- if (convs.size == 1) conversationController.switchConversation(convs.head) else Future.successful({})
        } yield clearIntent())
          .map(_ => true)
      case _ => {
        if (IntentUtils.isLaunchModIntent(intent)) {
          checkGroupJoinIntent()
          clearIntent()
          Future.successful(true)
        } else if (Intents.Page.Conversation.equalsIgnoreCase(intent.getStringExtra(Intents.OpenPageExtra))) {
          val convId = intent.getStringExtra(Intents.ConvIdExtra)
          conversationController.selectConv(ConvId(convId), ConversationChangeRequester.START_CONVERSATION)
          clearIntent()
          Future.successful(true)
        } else {
          clearIntent()
          Future.successful(false)
        }
      }
    }
  }

  def onPageVisible(page: Page): Unit =
    getControllerFactory.getGlobalLayoutController.setSoftInputModeForPage(page)

  def onInviteRequestSent(conversation: String): Future[Unit] = {
    info(l"onInviteRequestSent(${redactedString(conversation)})")
    conversationController.selectConv(Option(new ConvId(conversation)), ConversationChangeRequester.INVITE)
  }

  override def logout(): Unit = {
    accountsService.activeAccountId.head.flatMap(_.fold(Future.successful({}))(accountsService.logout)).map { _ =>
      startFirstFragment()
    }(Threading.Ui)
  }

  def manageDevices(): Unit = {
    this.startActivityForResult(PreferencesAdaptActivity.getIntent(this, PreferencesAdaptActivity.INTENT_VAL_devices), MainActivityUtils.REQUEST_CODE_ManageDevices)
  }

  def dismissOtrDeviceLimitFragment(): Unit = withFragmentOpt(OtrDeviceLimitFragment.Tag)(_.foreach(removeFragment))

  private def checkForUnsupportedEmojis() =
    for {
      cf <- Option(getControllerFactory) if !cf.isTornDown
      prefs <- Option(cf.getUserPreferencesController)
    } {
      val paint = new Paint
      val template = returning(new TextDrawing)(_.set("\uFFFF")) // missing char
      val check = new TextDrawing

      val missing = Emojis.getAllEmojisSortedByCategory.asScala.flatten.filter { emoji =>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
          !paint.hasGlyph(emoji)
        else {
          check.set(emoji)
          template == check
        }
      }

      if (missing.nonEmpty) prefs.setUnsupportedEmoji(missing.asJava, Emojis.VERSION)
    }

  override def onChooseUsernameChosen(): Unit =
    getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(ChangeHandleFragment.newInstance("", cancellable = false), ChangeHandleFragment.Tag)
      .addToBackStack(ChangeHandleFragment.Tag)
      .commit

  override def onUsernameSet(): Unit = {

    replaceMainFragment(new ConversationListManagerFragment, ConversationListManagerFragment.Tag, addToBackStack = false)
  }

  private val INVARAL_CHECK_UPDATE_VERSION = 2 * 60 * 60 * 1000

  def showUpdateInfoFragment(): Unit = {
    val lastSavedVersionCode = SpUtils.getInt(this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_APP_LAST_SAVED_VERSION, 0);
    try {
      val pkgInfo = getPackageManager.getPackageInfo(getPackageName(), 0)
      val currentVersionCode = pkgInfo.versionCode
      val currentVersionName = pkgInfo.versionName
      if (currentVersionCode > lastSavedVersionCode) {
        getSupportFragmentManager.beginTransaction.replace(R.id.fl__app_update_info, AppUpdateInfoFragment.newInstance(currentVersionName), AppUpdateInfoFragment.TAG).commit()
      } else {
      }
    } catch {
      case e: PackageManager.NameNotFoundException =>
        e.printStackTrace();
    }
  }

  private def checkGroupJoinIntent(): Unit = {

    val id = IntentUtils.getLUNCH_MODIntentId(getIntent)
    if (!StringUtils.isBlank(id)) {
      HttpRequestUtils.joinGroupConversation(ZApplication.getInstance(), id, new HttpRequestUtils.CommonHttpCallBack {
        override def onSuc(orgJson: Intents.Page): Unit = {
          val orgObj = new JSONObject(orgJson)
          val code = orgObj.optString("code")
          if (!StringUtils.isBlank(code) && "2002".equalsIgnoreCase(code)) {
            showToast(getResources.getString(R.string.conversation_join_group_closed))
          }
        }

        override def onFail(errCode: Int, msg: Intents.Page): Unit = {}
      })
    }
  }

  private def clearIntent(): Unit = {
    val intent: Intent = getIntent
    intent.clearExtras()
    setIntent(intent)
  }

  private def getLauncherActByPkg(packageName: String): Intent = {
    var launcherAct: String = null
    val intent = new Intent(Intent.ACTION_MAIN)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    intent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED | Intent.FLAG_ACTIVITY_NEW_TASK)
    @SuppressLint(Array("WrongConstant"))
    val resolveInfos = getPackageManager.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES).asScala
    resolveInfos.foreach { info =>
      if (info.activityInfo.packageName == packageName) {
        launcherAct = info.activityInfo.name
      }
    }
    if (TextUtils.isEmpty(launcherAct)) {
      null
    } else {
      intent.setComponent(new ComponentName(packageName, launcherAct))
    }
  }

  private def removeFragmentByTag(tag: String): Unit = {
    val fragment = getSupportFragmentManager.findFragmentByTag(tag)
    if (fragment != null) {
      getSupportFragmentManager.beginTransaction().remove(fragment).commitAllowingStateLoss();
    }
  }

  private def saveCurrentVersion(): Unit = {
    try {
      val currentVersionCode = getPackageManager.getPackageInfo(getPackageName(), 0).versionCode;
      SpUtils.putInt(this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_APP_LAST_SAVED_VERSION, currentVersionCode)
    } catch {
      case e: PackageManager.NameNotFoundException =>
        e.printStackTrace();
    }
  }

  override def onClickCloseAppUpdateInfo(): Unit = {
    removeFragmentByTag(AppUpdateInfoFragment.TAG)
    saveCurrentVersion()
  }
}

object MainActivity {

  val TAG = classOf[MainActivity].getSimpleName

  val ClientRegStateArg: String = "ClientRegStateArg"

  private val slideAnimations = Set(
    (SetOrRequestPasswordFragment.Tag, VerifyEmailFragment.Tag),
    (SetOrRequestPasswordFragment.Tag, AddEmailFragment.Tag),
    (VerifyEmailFragment.Tag, AddEmailFragment.Tag)
  )

  private def isSlideAnimation(oldTag: Option[String], newTag: String) = oldTag.fold(false) { old =>
    slideAnimations.contains((old, newTag)) || slideAnimations.contains((newTag, old))
  }
}

