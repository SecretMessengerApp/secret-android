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
package com.waz.zclient.preferences.pages

import android.app.Activity
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import android.widget.{ImageView, LinearLayout}
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jsy.common.acts._
import com.jsy.common.moduleProxy.ProxyMainActivity
import com.jsy.common.utils.DoubleUtils
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.content.UserPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.otr.Client
import com.waz.model.{AccentColor, UserData}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.messages.UsersController
import com.waz.zclient.preferences.pages.ProfileViewController.MaxAccountsCount
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, IntentUtils, MainActivityUtils, StringUtils, UiStorage, UserSignal}

trait ProfileView {
  def setUserName(name: String): Unit

  def setHandle(handle: String): Unit

  def setChatHead(userData: UserData): Unit

  def setAccentColor(color: Int): Unit
}

class ProfileViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfileView with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_profile_layout)

  setOrientation(LinearLayout.VERTICAL)

  def activity = {
    val activity = inject[Activity]
    if (activity.isInstanceOf[ProxyMainActivity]) Some(activity.asInstanceOf[ProxyMainActivity])
    else Some(activity)
  }

  val navigator = inject[BackStackNavigator]

  val rlProfileInfo = findById[ViewGroup](R.id.rlProfileInfo)
  val currentChatHead = findById[ChatHeadViewNew](R.id.currentChatHead)
  val userNameText = findById[TypefaceTextView](R.id.tvUserName)
  val userHandleText = findById[TypefaceTextView](R.id.tvHandle)
  val ivQrCode = findById[ImageView](R.id.ivQrCode)
  val rl_scan_login = findViewById[ViewGroup](R.id.rl_scan_login)


  val newTeamButton = findById[ViewGroup](R.id.rl_user_account)
  val settingsButton = findById[ViewGroup](R.id.rl_user_settings)
  val rl_contact_us = findById[ViewGroup](R.id.rl_contact_us)
  val rl_user_evaluation = findById[ViewGroup](R.id.rl_user_evaluation)

  lazy val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account }

  private var userData: UserData = _

  override def setUserName(name: String): Unit = userNameText.setText(name)

  override def setHandle(handle: String): Unit = userHandleText.setText(handle)

  override def setChatHead(userData: UserData): Unit = {
    currentChatHead.setUserData(userData)
    this.userData = userData
  }

  override def setAccentColor(color: Int): Unit = {}

  rlProfileInfo.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if(!DoubleUtils.isFastDoubleClick()){
        context.startActivity(PreferencesAdaptActivity.getIntent(v.getContext, PreferencesAdaptActivity.INTENT_VAL_profileInfo))
      }
    }
  })

  rl_scan_login.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if(!DoubleUtils.isFastDoubleClick()){
        activity.collect {
          case activity: ProxyMainActivity =>
            if (ContextCompat.checkSelfPermission(getContext, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
              ActivityCompat.requestPermissions(activity, Array[String](android.Manifest.permission.CAMERA), 1)
            } else {
              activity.asInstanceOf[ProxyMainActivity].startScanLoginAct()
            }
          case _ =>
        }
      }
    }
  })

  rl_contact_us.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = {
      if(!DoubleUtils.isFastDoubleClick()){
        val intent = new Intent(v.getContext, classOf[ContactUsActivity])
        context.startActivity(intent)
      }
    }
  })

  rl_user_evaluation.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = {
      if(!DoubleUtils.isFastDoubleClick()){
        if (!IntentUtils.isAvilible(context,IntentUtils.GOOGLEPLAY_PACKAGENAME)) {
          IntentUtils.startWebGooglePlay(context, context.getPackageName)
        }else {
          if (!IntentUtils.launchAppDetail(context, context.getPackageName, IntentUtils.GOOGLEPLAY_PACKAGENAME)) {
            IntentUtils.startWebGooglePlay(context, context.getPackageName)
          }
        }
      }
    }
  })

  settingsButton.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = {
      if(!DoubleUtils.isFastDoubleClick()){
        context.startActivity(PreferencesAdaptActivity.getIntent(v.getContext, PreferencesAdaptActivity.INTENT_VAL_settings))
      }
    }
  })

  newTeamButton.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if(!DoubleUtils.isFastDoubleClick()){
        if (MaxAccountsCount > 1 && BuildConfig.ACCOUNT_CREATION_ENABLED) {
          context.startActivity(new Intent(context, classOf[AccountMgrActivity]))
        }
      }
    }
  })

}

object ProfileView {
  val Tag: String = getClass.getSimpleName
}

case class ProfileBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_profile_screen_title

  override def layoutId = R.layout.preferences_profile

  var controller = Option.empty[ProfileViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[ProfileViewImpl]).map(view => new ProfileViewController(view)(view.wContext.injector, view))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class ProfileViewController(view: ProfileView)(implicit inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag {

  implicit val uiStorage = inject[UiStorage]

  lazy val accounts = inject[AccountsService]
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val tracking = inject[TrackingService]
  lazy val usersController = inject[UsersController]
  lazy val usersAccounts = inject[UserAccountsController]
  private lazy val userPrefs = zms.map(_.userPrefs)

  val currentUser = accounts.activeAccountId.collect { case Some(id) => id }

  val self = for {
    userId <- currentUser
    self <- UserSignal(userId)
  } yield self

  val incomingClients = for {
    z <- zms
    client <- z.userPrefs(UserPreferences.SelfClient).signal
    clients <- client.clientId.fold(Signal.empty[Seq[Client]])(aid => z.otrClientsStorage.incomingClientsSignal(z.selfUserId, aid))
  } yield clients

  self.on(Threading.Ui) { self =>
    view.setAccentColor(AccentColor(self.accent).color)
    self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
    view.setUserName(self.getDisplayName)
    view.setChatHead(self)
  }

  for {
    userId <- currentUser
    av <- usersController.availability(userId)
  } yield av
}

object ProfileViewController {
  val MaxAccountsCount = BuildConfig.MAX_ACCOUNTS
}
