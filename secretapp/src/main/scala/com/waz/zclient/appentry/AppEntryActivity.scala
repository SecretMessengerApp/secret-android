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
package com.waz.zclient.appentry

import android.content.res.Configuration
import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener
import androidx.fragment.app.{Fragment, FragmentManager, FragmentTransaction}
import com.jsy.common.fragment.SignInFragment2
import com.jsy.common.httpapi.{ImApiConst, RetrofitUtil, SimpleHttpListener, SpecialServiceAPI}
import com.jsy.common.model.VersionUpdateInfo
import com.jsy.common.moduleProxy.ProxyAppEntryActivity
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener
import com.waz.content.Preferences.Preference.PrefCodec
import com.waz.service.AccountManager.ClientRegistrationState
import com.waz.service.{AccountsService, GlobalModule, ZMessaging}
import com.waz.sync.client.CustomBackendClient
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient._
import com.waz.zclient.appentry.controllers.InvitationsController
import com.waz.zclient.appentry.fragments._
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.deeplinks.DeepLink.{Access, ConversationToken, CustomBackendToken, UserToken}
import com.waz.zclient.deeplinks.DeepLinkService.Error.{InvalidToken, UserLoggedIn}
import com.waz.zclient.deeplinks.DeepLinkService.{DoNotOpenDeepLink, OpenDeepLink}
import com.waz.zclient.deeplinks.{DeepLink, DeepLinkService}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.newreg.fragments.country.CountryController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.{showConfirmationDialog, showErrorDialog}
import com.waz.zclient.utils.{BackendController, ContextUtils, FlavorUtils, RichView}
import com.waz.zclient.views.LoadingIndicatorView
import okhttp3.{Call, Callback, Request, Response}

import java.io.IOException
import java.net.HttpURLConnection
import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.Right


class AppEntryActivity extends BaseActivity with ProxyAppEntryActivity {

  val TAG: String = classOf[AppEntryActivity].getName

  import Threading.Implicits.Ui

  implicit def ctx: Context = this

  private lazy val progressView = ViewUtils.getView(this, R.id.liv__progress).asInstanceOf[LoadingIndicatorView]
  private lazy val countryController: CountryController = new CountryController(this)
  private lazy val invitesController = inject[InvitationsController]
  private lazy val spinnerController = inject[SpinnerController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val deepLinkService: DeepLinkService = inject[DeepLinkService]
  private var createdFromSavedInstance: Boolean = false
  private var isPaused: Boolean = false

  private lazy val accountsService = inject[AccountsService]
  private lazy val attachedFragment = Signal[String]()
  private lazy val hasMoreAccount = accountsService.zmsInstances.map(_.nonEmpty)

  private lazy val closeButton = returning(ViewUtils.getView(this, R.id.close_button).asInstanceOf[GlyphTextView]) { v =>
    val fragmentTags = Set(
      SignInFragment.Tag,
      SignInFragment2.Tag,
      FirstLaunchAfterLoginFragment.Tag,
      VerifyEmailWithCodeFragment.Tag
    )

    Signal(hasMoreAccount, attachedFragment).map {
      case (false, _) => View.GONE
      case (true, fragment) if fragmentTags.contains(fragment) => View.GONE
      case _ => View.VISIBLE
    }.onUi(v.setVisibility)
  }

  private lazy val skipButton = returning(findById[TypefaceTextView](R.id.skip_button)) { v =>
    invitesController.invitations.map(_.isEmpty).map {
      case true => R.string.teams_invitations_skip
      case false => R.string.teams_invitations_done
    }.onUi(t => v.setText(t))
  }

  private var isFinished = false

  private var hasShowedUpdateToast = false

  private var isCheckingVersion: Boolean = false

  private def checkVersionUpdate(): Unit = {
    if (!isCheckingVersion) {
      isCheckingVersion = true
      SpecialServiceAPI.getInstance().get(ImApiConst.APP_VERSION_UPDATE_INFO, new SimpleHttpListener[VersionUpdateInfo] {

        override def onFail(code: Int, err: String): Unit = {
        }

        override def onSuc(r: VersionUpdateInfo, orgJson: String): Unit = {
          if (r != null) {
            val oldestAccepted = r.oldestAccepted
            val localVersion = getPackageManager.getPackageInfo(getPackageName(), 0).versionCode;
            if (localVersion < oldestAccepted) {
              if (FlavorUtils.isGooglePlay) {
                if (!hasShowedUpdateToast) {
                  hasShowedUpdateToast = true
                  ForceUpdateActivity.startSelf(AppEntryActivity.this, r.android_url, isForceUpdate = false)
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
            }
          }
        }

        override def onComplete(): Unit = {
          super.onComplete()
          isCheckingVersion = false
        }
      })
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
        ForceUpdateActivity.startSelf(AppEntryActivity.this, fileUrl, forceUpdate)
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
        ForceUpdateActivity.startSelf(AppEntryActivity.this, tempUrl, forceUpdate)
        if (forceUpdate) finish()
      }
    })
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    isFinished = true
  }

  override def onBackPressed(): Unit = {
    getSupportFragmentManager.getFragments.asScala.find {
      case f: OnBackPressedListener if f.onBackPressed() => true
      case _ => false
    }.fold(finish())(_ => ())
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    if (getActionBar != null) getActionBar.hide()
    super.onCreate(savedInstanceState)
    ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)
    setContentView(R.layout.activity_signup)
    enableProgress(false)
    createdFromSavedInstance = savedInstanceState != null

    closeButton.onClick(abortAddAccount())

    showFragment()

    getSupportFragmentManager.addOnBackStackChangedListener(new OnBackStackChangedListener {
      override def onBackStackChanged(): Unit = {

        val frags = getSupportFragmentManager.getFragments
        if (frags != null && frags.size() > 0) {
          verbose(l"OnBackStackChangedListener  tag:${frags.get(frags.size() - 1).getTag}")
          attachedFragment ! frags.get(frags.size() - 1).getTag
        }
      }
    })

    skipButton.setVisibility(View.GONE)

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedTheme) => progressView.show(animation, darkTheme = forcedTheme.getOrElse(true), 300)
      case Hide(Some(message)) => progressView.hideWithMessage(message, 750)
      case Hide(_) => progressView.hide()
    }

    deepLinkService.deepLink.collect { case Some(result) => result } onUi {
      case OpenDeepLink(UserToken(_), _) | DoNotOpenDeepLink(DeepLink.User, _) =>
        showErrorDialog(R.string.deep_link_user_error_title, R.string.deep_link_user_error_message)
        deepLinkService.deepLink ! None

      case OpenDeepLink(ConversationToken(_), _) | DoNotOpenDeepLink(DeepLink.Conversation, _) =>
        showErrorDialog(R.string.deep_link_conversation_error_title, R.string.deep_link_conversation_error_message)
        deepLinkService.deepLink ! None

      case OpenDeepLink(CustomBackendToken(configUrl), _) =>
        verbose(l"got custom backend url: $configUrl")
        deepLinkService.deepLink ! None

        inject[AccentColorController].accentColor.head.flatMap { color =>
          showConfirmationDialog(
            title = ContextUtils.getString(R.string.custom_backend_dialog_confirmation_title),
            msg = ContextUtils.getString(R.string.custom_backend_dialog_confirmation_message, configUrl.toString),
            positiveRes = R.string.custom_backend_dialog_connect,
            negativeRes = R.string.secret_cancel,
            color = color
          )
        }.foreach {
          case false =>
            verbose(l"cancelling backend switch")
          case true =>
            enableProgress(true)
            inject[CustomBackendClient].loadBackendConfig(configUrl).foreach {
              case Left(errorResponse) =>
                error(l"error trying to download backend config.", errorResponse)
                enableProgress(false)

                showErrorDialog(
                  R.string.custom_backend_dialog_network_error_title,
                  R.string.custom_backend_dialog_network_error_message)

              case Right(config) =>
                verbose(l"got config response: $config")
                enableProgress(false)

                inject[BackendController].switchBackend(inject[GlobalModule], config, configUrl)
                verbose(l"switched backend")

                // re-present fragment for updated ui.
                getSupportFragmentManager.popBackStackImmediate(AppLaunchFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
            }
        }

      case DoNotOpenDeepLink(Access, UserLoggedIn) =>
        verbose(l"do not open, Access, user logged in")
        showErrorDialog(
          R.string.custom_backend_dialog_logged_in_error_title,
          R.string.custom_backend_dialog_logged_in_error_message)
        deepLinkService.deepLink ! None

      case DoNotOpenDeepLink(Access, InvalidToken) =>
        verbose(l"do not open, Access, invalid token")
        showErrorDialog(
          R.string.custom_backend_dialog_network_error_title,
          R.string.custom_backend_dialog_network_error_message)
        deepLinkService.deepLink ! None

      case _ =>
    }

    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedTheme) => progressView.show(animation, darkTheme = forcedTheme.getOrElse(true), 300)
      case Hide(Some(message)) => progressView.hideWithMessage(message, 750)
      case Hide(_) => progressView.hide()
    }

    checkVersionUpdate
  }

  // It is possible to open the app through intents with deep links. If that happens, we can't just
  // show the fragment that was opened previously - we have to take the user to the fragment specified
  // by the intent (at this point the information about it should be already stored somewhere).
  // If this is the case, in `onResume` we can pop back the stack and show the new fragment.
  override def onResume(): Unit = {
    super.onResume()
    // if the SSO token is present we use it to log in the user
    userAccountsController.ssoToken.head.foreach {
      case Some(_) =>
        getSupportFragmentManager.popBackStackImmediate(AppLaunchFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)
      case _ =>
    }(Threading.Ui)
  }

  private def showFragment(): Unit = {
    showFragment(AppLaunchFragment(), AppLaunchFragment.Tag, animated = false)

  }


  override def onAttachFragment(fragment: Fragment): Unit = {
    super.onAttachFragment(fragment)
    verbose(l"onAttachFragment  tag:${fragment.getTag}")
  }

  override protected def onPostResume(): Unit = {
    super.onPostResume()
    isPaused = false
  }

  override protected def onPause(): Unit = {
    isPaused = true
    super.onPause()
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    info(l"OnActivity result: $requestCode, $resultCode")
    super.onActivityResult(requestCode, resultCode, data)
    getSupportFragmentManager.findFragmentById(R.id.fl_main_content).onActivityResult(requestCode, resultCode, data)
  }

  override def enableProgress(enabled: Boolean): Unit = {
    if (enabled)
      progressView.show(LoadingIndicatorView.SpinnerWithDimmedBackground(), darkTheme = true)
    else
      progressView.hide()
  }

  override def isShowingProgress(): Boolean = {
    progressView.isShowing()
  }

  override def abortAddAccount(): Unit = {
    def switchNextAccount(): Future[Boolean] = {
      ZMessaging.globalModule.map(_.accountsStorage).flatMap { accStore =>
        warn(l"ZMessaging.globalModule This shouldn't happen, going back to sign in...accStore")
        accStore.list()
      }.flatMap { accDataSeq =>
        val nextAccData = accDataSeq.headOption
        warn(l"ZMessaging.globalModule This shouldn't happen, going back to sign in...accDataSeq")
        Future.successful(nextAccData)
      }.flatMap { nextAccData =>
        warn(l"ZMessaging.globalModule This shouldn't happen, going back to sign in...nextAccData:${nextAccData}")
        if (nextAccData.nonEmpty && nextAccData.head.name.isDefined && nextAccData.head.handle.isDefined) {
          ZMessaging.currentAccounts.setAccount(nextAccData.map(_.id)).flatMap {
            case x =>
              warn(l"ZMessaging.globalModule This shouldn't happen, going back to sign in...setAccount ${nextAccData}")
              Future.successful(true)
          }
        } else {
          Future.successful(false)
        }
      }
    }

    if (hasMoreAccount.currentValue.getOrElse(false)) {
      accountsService.activeZms.currentValue.flatten.fold {
        switchNextAccount().flatMap {
          case true =>
            onEnterApplication(false, isBack = true)
            Future.successful({})
          case false =>
            Future.successful({})
        }
      } { activeZms =>
        activeZms.accounts.activeAccountId.currentValue.flatten match {
          case Some(x) =>
            onEnterApplication(false, isBack = true)
            Future.successful({})
          case None =>
            activeZms.accountStorage.list().flatMap { accs =>
              activeZms.accounts.setAccount(accs.headOption.map(_.id))
              onEnterApplication(false, isBack = true)
              Future.successful({})
            }
        }
      }
    } else {
      switchNextAccount().flatMap {
        case true =>
          onEnterApplication(false, isBack = true)
          Future.successful({})
        case false =>
          Future.successful({})
      }
    }
  }

  override def onEnterApplication(openSettings: Boolean, clientRegState: Option[ClientRegistrationState] = None, isBack: Boolean = false): Unit = {
    getControllerFactory.getVerificationController.finishVerification()

    val intent = Intents.EnterAppIntent(openSettings)(this)
    clientRegState.foreach(state => intent.putExtra(MainActivity.ClientRegStateArg, PrefCodec.SelfClientIdCodec.encode(state)))
    startActivity(intent)
    finish()
  }

  private def setDefaultAnimation(transaction: FragmentTransaction): FragmentTransaction = {
    transaction.setCustomAnimations(
      R.anim.fragment_animation_second_page_slide_in_from_right,
      R.anim.fragment_animation_second_page_slide_out_to_left,
      R.anim.fragment_animation_second_page_slide_in_from_left,
      R.anim.fragment_animation_second_page_slide_out_to_right)
    transaction
  }

  def dismissCountryBox(): Unit = {
    getSupportFragmentManager.popBackStackImmediate
    KeyboardUtils.showKeyboard(this)
  }

  override def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit = {
    val transaction = getSupportFragmentManager.beginTransaction()
    if (animated) setDefaultAnimation(transaction)
    transaction
      .replace(R.id.fl_main_content, f, tag)
      .addToBackStack(tag)
      .commit
    enableProgress(false)
  }
}
