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
package com.jsy.common.acts

import android.annotation.SuppressLint
import android.app.Activity
import android.content.{Context, Intent}
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view.{KeyEvent, MenuItem, View, ViewGroup}
import android.widget.{TextView, Toast}
import androidx.annotation.Nullable
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.{Fragment, FragmentManager, FragmentTransaction}
import com.waz.content.UserPreferences
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.SpinnerController.{Hide, Show}
import com.waz.zclient.camera.CameraFragment
import com.waz.zclient.controllers.camera.{CameraActionObserver, ICameraController}
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.preferences.pages._
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, DefaultTransition, RingtoneUtils}
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.{BaseActivity, R, SpinnerController}

class PreferencesAdaptActivity extends BaseActivity
  with BackStackNavigator.Container
  with CameraActionObserver {

  import PreferencesAdaptActivity._

  private lazy val cameraController = inject[ICameraController]
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val backStackNavigator = inject[BackStackNavigator]
  private lazy val spinnerController = inject[SpinnerController]

  lazy val titleView = findViewById[TextView](R.id.settings_adp_toolbar__title)


  @SuppressLint(Array("PrivateResource"))
  override def onCreate(@Nullable savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    val profileType = getIntent().getStringExtra(INTENT_KEY_profileType)

    val view = getLayoutInflater.inflate(R.layout.activity_settings_adapt, null)
    setContentView(view)

    val toolBar = findViewById[Toolbar](R.id.settings_adp_toolbar)

    toolBar.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        onBackPressed()
      }
    })

    backStackNavigator.setup(findViewById(R.id.fl__root__content).asInstanceOf[ViewGroup],this)
    if (profileType == INTENT_VAL_settings) {
      backStackNavigator.goTo(SettingsBackStackKey(), DefaultTransition())
    } else if (profileType == INTENT_VAL_profileInfo) {
      backStackNavigator.goTo(AccountBackStackKey())
    } else if (profileType == INTENT_VAL_devices) {
      backStackNavigator.goTo(DevicesBackStackKey())
    } else if(profileType == INTENT_VAL_advanced){
      backStackNavigator.goTo(AdvancedBackStackKey())
    }

    ColorUtils.setBackgroundColor(view)

    val loadingIndicator = findViewById[LoadingIndicatorView](R.id.progress_spinner)
    spinnerController.spinnerShowing.onUi {
      case Show(animation, forcedTheme) => loadingIndicator.show(animation, darkTheme = forcedTheme.getOrElse(true))
      case Hide(Some(message)) => loadingIndicator.hideWithMessage(message, 1000)
      case _ => loadingIndicator.hide()
    }
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      onBackPressed()
      return true
    }
    return super.onKeyDown(keyCode, event)

  }


  override def onSaveInstanceState(outState: Bundle) = {
    super.onSaveInstanceState(outState)

  }

  override def onStart(): Unit = {
    super.onStart()
    cameraController.addCameraActionObserver(this)
  }

  override protected def onPause() = {
    super.onPause()
    cameraController.removeCameraActionObserver(this)

  }

  override protected def onResume() = {
    super.onResume()

  }

  override def onStop(): Unit = {
    super.onStop()
  }


  //  override def getBaseTheme: Int = R.style.Theme_Dark_Preferences

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    val fragment: Fragment = getSupportFragmentManager.findFragmentById(R.id.fl__root__camera)
    if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data)

    if (resultCode == Activity.RESULT_OK && Seq(OptionsView.RingToneResultId, OptionsView.TextToneResultId, OptionsView.PingToneResultId).contains(requestCode)) {

      val pickedUri = Option(data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI).asInstanceOf[Uri])
      val key = requestCode match {
        case OptionsView.RingToneResultId => UserPreferences.RingTone
        case OptionsView.TextToneResultId => UserPreferences.TextTone
        case OptionsView.PingToneResultId => UserPreferences.PingTone
      }
      zms.head.flatMap(_.userPrefs.preference(key).update(pickedUri.fold(RingtoneUtils.getSilentValue)(_.toString)))(Threading.Ui)
    }
  }
  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    super.onOptionsItemSelected(item)
  }

  private var hasRemoveDevices = false

  def onDeviceRemoved(): Unit = {
    hasRemoveDevices = true
  }

  override def finish(): Unit = {
    if (hasRemoveDevices){
      setResult(Activity.RESULT_OK)
    }
    super.finish()
  }

  override def onBackPressed(): Unit = {
    Option(getSupportFragmentManager.findFragmentByTag(CameraFragment.Tag).asInstanceOf[CameraFragment]).fold {
      if (!spinnerController.spinnerShowing.currentValue.exists(_.isInstanceOf[Show]) && !backStackNavigator.back())
        finish()
    } { _ => getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE) }
  }


  override def onCurrentStackChange(fromShowStack: BackStackKey, toShowStack: BackStackKey, isNew: Boolean): Unit = {
    toShowStack match {
      case x: SettingsBackStackKey => titleView.setText(R.string.settings_title)
      case x: DarkModeBackStackKey => titleView.setText(R.string.dark_mode)
      case x: AccountBackStackKey => titleView.setText(R.string.pref_account_screen_title)
      case x: DevicesBackStackKey => titleView.setText(R.string.pref_devices_screen_title)
      case x: OptionsBackStackKey => titleView.setText(R.string.pref_options_screen_title)
      case x: AdvancedBackStackKey => titleView.setText(R.string.pref_advanced_screen_title)
      case x: SupportBackStackKey => titleView.setText(R.string.pref_support_screen_title)
      case c: AboutBackStackKey => titleView.setText(R.string.pref_about_screen_title)
      case x: DevSettingsBackStackKey => titleView.setText(R.string.pref_developer_screen_title)
      case x: AvsBackStackKey => titleView.setText(R.string.pref_dev_avs_screen_title)
      case x: DeviceDetailsBackStackKey => titleView.setText(R.string.device_detail_title)
      case x: ProfilePictureBackStackKey => titleView.setText(R.string.head_portrait_title)
    }
  }

  //  //TODO do we need to check internet connectivity here?
  override def onBitmapSelected(input: AssetService.RawAssetInput, cameraContext: CameraContext): Unit = {
    if (cameraContext == CameraContext.SETTINGS) {
      inject[Signal[ZMessaging]].head.map { zms =>
        zms.users.updateSelfPicture(input)
      }(Threading.Background)
      //getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      finish()
    }
  }

  override def onCameraNotAvailable() =
    Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()

  override def onOpenCamera(cameraContext: CameraContext) = {

    if (cameraContext == CameraContext.SETTINGS){
      Option(getSupportFragmentManager.findFragmentByTag(CameraFragment.Tag)) match {
        case None =>
          getSupportFragmentManager
            .beginTransaction
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            .add(R.id.fl__root__camera, CameraFragment.newInstance(cameraContext), CameraFragment.Tag)
            .addToBackStack(CameraFragment.Tag)
            .commit
        case Some(_) => //do nothing
      }
    }


  }

  def onCloseCamera(cameraContext: CameraContext) ={
    if (cameraContext == CameraContext.SETTINGS){
      getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }


}


object PreferencesAdaptActivity {

  val INTENT_KEY_profileType = "profileType"

  val INTENT_VAL_settings = "settings"
  val INTENT_VAL_profileInfo = "profileInfo"
  val INTENT_VAL_devices = "devices"
  val INTENT_VAL_advanced = "advanced"


  def getIntent(context: Context, profileType: String): Intent = new Intent(context, classOf[PreferencesAdaptActivity]).putExtra(INTENT_KEY_profileType, profileType)

  def getInstance(): this.type = this

}
