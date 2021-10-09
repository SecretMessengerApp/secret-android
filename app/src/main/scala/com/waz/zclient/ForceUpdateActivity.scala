/**
 * Secret
 * Copyright (C) 2021 Secret
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

import android.Manifest
import android.app.{Activity, PendingIntent}
import android.content._
import android.content.pm.PackageManager
import android.net.Uri
import android.os._
import android.text.TextUtils
import android.view.KeyEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jsy.common.model.DownLoadNotifyModel
import com.jsy.common.srv.DownloadManagerNotificationService
import com.jsy.common.utils.DoubleUtils
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.startup.UpdateFragment
import com.waz.zclient.utils.{FlavorUtils, IntentUtils, MainActivityUtils}
import io.reactivex.annotations.NonNull


class ForceUpdateActivity extends BaseActivity with UpdateFragment.Container {

  import ForceUpdateActivity._

  private val TAG = classOf[ForceUpdateActivity].getSimpleName

  private val INTENT_KEY_notificationModelFromNotification = "notificationModelFromNotification"


  private var android_url: String = _
  private var isForceUpdate = false

  private var notificationModelFromNotification: DownLoadNotifyModel = null

  private val handler = new Handler() {
    override def handleMessage(msg: Message): Unit = {
      super.handleMessage(msg)
      val notificationModel = msg.obj.asInstanceOf[DownLoadNotifyModel]
      if (notificationModel.getUrl == android_url) msg.what match {
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_MUTUTABLE =>
          verbose(l"$TAG MUTUTABLE")
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_OVER_RANGE =>
          verbose(l"$TAG OVER_RANGE")
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_START =>
          val fragment = getSupportFragmentManager.findFragmentByTag(UpdateFragment.TAG)
          if (fragment != null && fragment.isInstanceOf[UpdateFragment]) fragment.asInstanceOf[UpdateFragment].updateDownloadText(notificationModel.getProgress, notificationModel.getLength, false)
          verbose(l"$TAG -------------START")
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_ING =>
          val fragment = getSupportFragmentManager.findFragmentByTag(UpdateFragment.TAG)
          if (fragment != null && fragment.isInstanceOf[UpdateFragment]) fragment.asInstanceOf[UpdateFragment].updateDownloadText(notificationModel.getProgress, notificationModel.getLength, false)
          verbose(l"$TAG ING")
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_FINISH =>
          val fragment = getSupportFragmentManager.findFragmentByTag(UpdateFragment.TAG)
          if (fragment != null && fragment.isInstanceOf[UpdateFragment]) fragment.asInstanceOf[UpdateFragment].updateDownloadText(notificationModel.getProgress, notificationModel.getLength, false)
          verbose(l"$TAG -----------FINISH")
        case DownloadManagerNotificationService.DOWN_SRV_DOWN_APP_FAIL =>
          val fragment = getSupportFragmentManager.findFragmentByTag(UpdateFragment.TAG)
          if (fragment != null && fragment.isInstanceOf[UpdateFragment]) fragment.asInstanceOf[UpdateFragment].updateDownloadText(0, 1, true)
          verbose(l"$TAG -----------FAIL")
      }
      else {
        // ...
      }
    }
  }

  private val REQUEST_CODE_SYSTEM_PERMISSION_WRITE_EXTERNAL_STORAGE = 100

  private var bindDownloadWithNotificationServiceSuc = false
  private var downloadWithNotificationService: DownloadManagerNotificationService = _

  private val downloadServiceConnection = new ServiceConnection() {
    override def onServiceConnected(name: ComponentName, service: IBinder): Unit = {
      downloadWithNotificationService = service.asInstanceOf[DownloadManagerNotificationService.ISub].getService
      initView()
    }

    override def onServiceDisconnected(name: ComponentName): Unit = {
      downloadWithNotificationService = null
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    initData(savedInstanceState)
    //getWindow.setBackgroundDrawableResource(R.color.transparent)
    setContentView(R.layout.activity_force_update)
    val startDownloadSrv = new Intent(this, classOf[DownloadManagerNotificationService])
    startService(startDownloadSrv)
    bindDownloadWithNotificationServiceSuc = bindService(startDownloadSrv, downloadServiceConnection, Context.BIND_AUTO_CREATE)
  }

  private def initData(savedInstanceState: Bundle): Unit = {
    if (savedInstanceState != null) {
      android_url = savedInstanceState.getString(INTENT_KEY_android_url)
      isForceUpdate = savedInstanceState.getBoolean(INTENT_KEY_isForceUpdate)
      notificationModelFromNotification = savedInstanceState.getParcelable(INTENT_KEY_notificationModelFromNotification)
    }
    else {
      android_url = getIntent.getStringExtra(INTENT_KEY_android_url)
      isForceUpdate = getIntent.getBooleanExtra(INTENT_KEY_isForceUpdate, false)
      notificationModelFromNotification = getIntent.getParcelableExtra(INTENT_KEY_notificationModelFromNotification)
    }
  }

  private def initView(): Unit = {
    if (downloadWithNotificationService != null) {
      downloadWithNotificationService.putUiHandler(android_url, handler)
      handler.post(new Runnable() {
        override def run(): Unit = {
          getSupportFragmentManager.beginTransaction.replace(R.id.fl_main_content, UpdateFragment.newInstance(android_url, isForceUpdate), UpdateFragment.TAG).commit
        }
      })
      if (notificationModelFromNotification != null) IntentUtils.installApk(this, DownloadManagerNotificationService.getApkFilePath(notificationModelFromNotification.getMd5FileName))
    }
  }

  override protected def onNewIntent(intent: Intent): Unit = {
    super.onNewIntent(intent)
    setIntent(intent)
    initData(null)
    initView()
  }

  override def onClickUpdate(): Unit = {
    if (IntentUtils.isInstalledFromGooglePlay(this) || FlavorUtils.isGooglePlay) {
      if (!IntentUtils.launchAppDetail(this, getPackageName, IntentUtils.GOOGLEPLAY_PACKAGENAME)) {
        IntentUtils.startWebGooglePlay(this, getPackageName)
      }
      return
    }

    if (!DoubleUtils.isFastDoubleClick()) {
      if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) launchDownloadLink()
      else ActivityCompat.requestPermissions(this, Array[String](Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_SYSTEM_PERMISSION_WRITE_EXTERNAL_STORAGE)
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array[String], @NonNull grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    requestCode match {
      case REQUEST_CODE_SYSTEM_PERMISSION_WRITE_EXTERNAL_STORAGE =>
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) launchDownloadLink()
        else showToast(R.string.toast_no_external_storage_permission)
      case _ =>

    }
  }

  private def launchDownloadLink(): Unit = {
    if (!TextUtils.isEmpty(android_url)) {
      if (downloadWithNotificationService.isDownloading(android_url)) return
      val notificationIntent = new Intent(this, classOf[ForceUpdateActivity])
      notificationIntent.putExtra(INTENT_KEY_android_url, android_url)
      notificationIntent.putExtra(INTENT_KEY_isForceUpdate, isForceUpdate)
      val notificationModel = DownLoadNotifyModel.createNotificationModel(android_url, true, MainActivityUtils.getMd5(android_url.replace("\\", "")),
        DownloadManagerNotificationService.createNotificationId, getResources.getString(R.string.app_name), R.drawable.ic_launcher_wire)
      notificationIntent.putExtra(INTENT_KEY_notificationModelFromNotification, notificationModel.asInstanceOf[Parcelable])
      notificationModel.setDownloadSuccessPendingIntent(PendingIntent.getActivity(downloadWithNotificationService, 0, notificationIntent, 0))
      downloadWithNotificationService.downNewFile(notificationModel, handler)
    }
    else {
      val appPackageName = getPackageName
      try {
        val launchIntent = getPackageManager.getLaunchIntentForPackage("com.android.vending")
        val comp = new ComponentName("com.android.vending", "com.google.android.finsky.activities.LaunchUrlHandlerActivity")
        launchIntent.setComponent(comp)
        launchIntent.setData(Uri.parse("market://details?id=" + appPackageName))
        startActivity(launchIntent)
      } catch {
        case anfe: ActivityNotFoundException =>
          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)))
        case e: Exception =>
          startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + appPackageName)))
      }
    }
  }

  override def onClickQuit(): Unit = {
    finish()
    overridePendingTransition(0, R.anim.fade_out)
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      finish()
      overridePendingTransition(0, R.anim.fade_out)
      return true
    }
    super.onKeyDown(keyCode, event)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    if (bindDownloadWithNotificationServiceSuc) {
      downloadWithNotificationService.removeUiHandler(android_url, handler)
      unbindService(downloadServiceConnection)
    }
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putString(INTENT_KEY_android_url, android_url)
    outState.putBoolean(INTENT_KEY_isForceUpdate, isForceUpdate)
  }
}


object ForceUpdateActivity {
  //  def checkBlacklist(activity: Activity)(implicit ecxt: EventContext): Unit =
  //    ZMessaging.currentGlobal.blacklist.upToDate
  //        .ifFalse
  //        .filter(_ => BuildConfig.ENABLE_BLACKLIST)
  //        .onUi { _ =>
  //          activity.startActivity(
  //              new Intent(activity.getApplicationContext, classOf[ForceUpdateActivity]))
  //          activity.finish()
  //        }

  private val INTENT_KEY_android_url = "android_url"
  private val INTENT_KEY_isForceUpdate = "isForceUpdate"

  def startSelf(activity: Activity, android_url: String, isForceUpdate: Boolean): Unit = {
    val intent = new Intent(activity, classOf[ForceUpdateActivity])
    intent.putExtra(INTENT_KEY_android_url, android_url)
    intent.putExtra(INTENT_KEY_isForceUpdate, isForceUpdate)
    activity.startActivity(intent)
    activity.overridePendingTransition(R.anim.fade_in, 0)
  }
}
