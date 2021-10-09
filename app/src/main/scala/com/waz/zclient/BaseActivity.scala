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

import android.content.pm.PackageManager
import android.content.{BroadcastReceiver, Context, Intent, IntentFilter}
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.{Gravity, View}
import android.widget.{TextView, Toast}
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jsy.common.callback.OnUserDataGetCallBack
import com.jsy.common.dialog.PictureDialog
import com.jsy.common.httpapi.HttpObserver
import com.jsy.common.utils.ToastUtils
import com.jsy.common.utils.dynamiclanguage.DynamicLanguageContextWrapper
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.InternalLog
import com.waz.model.{ConversationData, UserId}
import com.waz.permissions.PermissionsService
import com.waz.permissions.PermissionsService.{Permission, PermissionProvider}
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.services.websocket.WebSocketService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.Intents.RichIntent
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.log.LogUI._
import com.waz.zclient.tracking.GlobalTrackingController
import com.waz.zclient.ui.text.TypefaceTextView

import scala.collection.breakOut
import scala.collection.immutable.ListSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class BaseActivity extends com.jsy.secret.sub.swipbackact.base.BaseActivity
  with ServiceContainer
  with ActivityHelper
  with PermissionProvider
  with DerivedLogTag {

  import BaseActivity._

  private implicit val wContext = WireContext(ZApplication.getInstance())

  def getInjector: Injector = {
    return wContext.injector
  }

  def getEventContext: EventContext = {
    EventContext.Implicits.global
  }

  private lazy val zms = inject[Signal[ZMessaging]]
  lazy val themeController = inject[ThemeController]
  lazy val globalTrackingController = inject[GlobalTrackingController]
  lazy val permissions = inject[PermissionsService]

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), injector)

  private val themeReceiver = new BroadcastReceiver {
    override def onReceive(context: Context, intent: Intent): Unit = {
      val action = intent.getAction
      if (Constants.ACTION_CHANGE_THEME.equals(action) || Constants.ACTION_CHANGE_LANGUAGE.equals(action)) {
        info(l"changed => $action")
        recreate()
      }
    }
  }

  override protected def isExtendScalaBase: Boolean = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    verbose(l"onCreate")
    super.onCreate(savedInstanceState)
    setTheme(getBaseTheme)
    val intentFilter=new IntentFilter(Constants.ACTION_CHANGE_THEME)
    intentFilter.addAction(Constants.ACTION_CHANGE_LANGUAGE)
    registerReceiver(themeReceiver,intentFilter)
  }

  override def onStart(): Unit = {
    verbose(l"onStart")
    super.onStart()
    onBaseActivityStart()
  }

  def getZmessaging(): Signal[ZMessaging] = {
    return zms
  }

  def onBaseActivityStart(): Unit = {
    getControllerFactory.getGlobalLayoutController.setActivity(this)
    ZMessaging.currentUi.onStart()
    inject[UiLifeCycle].acquireUi()
    permissions.registerProvider(this)
    //Option(ViewUtils.getContentView(getWindow)).foreach(getControllerFactory.setGlobalLayout)
  }

  override protected def onResume(): Unit = {
    verbose(l"onResume")
    super.onResume()
    onBaseActivityResume()
  }

  override def attachBaseContext(newBase: Context): Unit = {
    super.attachBaseContext(DynamicLanguageContextWrapper.updateContext(newBase))
  }

  def onBaseActivityResume(): Unit =
    CancellableFuture.delay(150.millis).foreach { _ =>
      WebSocketService(this.getApplicationContext)
    }(Threading.Ui)

  override protected def onResumeFragments(): Unit = {
    verbose(l"onResumeFragments")
    super.onResumeFragments()
  }

  override def onWindowFocusChanged(hasFocus: Boolean): Unit = {
    verbose(l"onWindowFocusChanged: $hasFocus")
  }

  private def getBaseTheme: Int = {
    val isDarkTheme = ThemeUtils.isDarkTheme(this)
    verbose(l"isDarkTheme:${isDarkTheme}")
    if(!isDarkTheme) {
      if (canUseSwipeBackLayout) {
         R.style.SecretAppThemeLight0
      }
      else {
         R.style.SecretAppThemeLight1
      }
    }
    else{
      if (canUseSwipeBackLayout) {
         R.style.SecretAppThemeDark0
      }
      else {
         R.style.SecretAppThemeDark1
      }
    }
  }

  override def canUseSwipeBackLayout = false

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    verbose(l"onActivityResult: requestCode: $requestCode, resultCode: $resultCode, data: ${RichIntent(data)}")
    super.onActivityResult(requestCode, resultCode, data)
    permissions.registerProvider(this)
  }

  override protected def onPause(): Unit = {
    verbose(l"onPause")
    super.onPause()
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    verbose(l"onSaveInstanceState")
    super.onSaveInstanceState(outState)
  }

  override def onStop() = {
    verbose(l"onStop")
    getControllerFactory.getGlobalLayoutController.tearDown()
    ZMessaging.currentUi.onPause()
    inject[UiLifeCycle].releaseUi()
    InternalLog.flush()
    super.onStop()
  }

  override def onDestroy() = {
    verbose(l"onDestroy")
    globalTrackingController.flushEvents()
    permissions.unregisterProvider(this)
    super.onDestroy()
    dismissProgressDialog()
    progressDialog = null

    try {
      unregisterReceiver(themeReceiver)
    } catch {
      case _: Throwable =>
    }

  }

  def getControllerFactory: IControllerFactory = ZApplication.from(this).getControllerFactory

  override def requestPermissions(ps: ListSet[Permission]) = {
    verbose(l"requestPermissions: $ps")
    ActivityCompat.requestPermissions(this, ps.map(_.key).toArray, PermissionsRequestId)
  }

  override def hasPermissions(ps: ListSet[Permission]) = ps.map { p =>
    returning(p.copy(granted = ContextCompat.checkSelfPermission(this, p.key) == PackageManager.PERMISSION_GRANTED)) { p =>
      verbose(l"hasPermission: $p")
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, keys: Array[String], grantResults: Array[Int]): Unit = {
    verbose(l"onRequestPermissionsResult: $requestCode, ${keys.toSet.map(redactedString)}, ${grantResults.toSet.map((r: Int) => r == PackageManager.PERMISSION_GRANTED)}")
    if (requestCode == PermissionsRequestId) {
      val ps = hasPermissions(keys.map(Permission(_))(breakOut))
      //if we somehow call requestPermissions twice, ps will be empty - so don't send results back to PermissionsService, as it will probably be for the wrong request.
      if (ps.nonEmpty) permissions.onPermissionsResult(ps)
    }
  }

  private var progressDialog: PictureDialog = _

  def showProgressDialog(msg:String,cancelable:Boolean,resId:Int,needUpdateView:Boolean): Unit ={
    if (isFinishing) return
    if (progressDialog == null) {
      progressDialog = new PictureDialog(this)
    }
    progressDialog.setCancelable(cancelable)
    progressDialog.show(msg,resId,needUpdateView)
  }

  def showProgressDialog(msg: String, cancelable: Boolean): Unit = {
      showProgressDialog(msg,cancelable,R.drawable.kprogresshud_spinner,needUpdateView = true)
  }

  def showProgressDialog(msg: String): Unit = {
    showProgressDialog(msg, true)
  }


  def showProgressDialog(cancelable: Boolean): Unit = {
    showProgressDialog("", cancelable)
  }

  def showProgressDialog(resId: Int, cancelable: Boolean): Unit = {
    showProgressDialog(getResources.getString(resId), cancelable)
  }

  def showProgressDialog(resId: Int): Unit = {
    showProgressDialog(getResources.getString(resId))
  }

  def showProgressDialog(): Unit = {
    showProgressDialog(R.string.empty_string)
  }

  @Deprecated
  def closeProgressDialog(): Unit = {
    dismissProgressDialog()
  }

  def dismissProgressDialog(): Unit = {
    if (isShowingProgressDialog) {
      progressDialog.dismiss()
    }
  }

  def isShowingProgressDialog(): Boolean = !isFinishing && progressDialog != null && progressDialog.isShowing

  def dealResponseError(code: Int): Boolean = {
    val msg = getResponseError(code)
    if (msg == null) {
      false
    } else {
      showToast(msg)
      true
    }
  }

  def isNetOk: Boolean = {
    val networkInfo = getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager].getActiveNetworkInfo
    networkInfo != null && networkInfo.isConnected && networkInfo.isAvailable
  }

  def getResponseError(code: Int): String = {
    code match {
      case HttpObserver.ERR_LOCAL =>
        if (isNetOk) {
          getResources.getString(R.string.serverError)
        } else {
          getResources.getString(R.string.netInvalid)
        }
      case HttpObserver.DATA_ERROR =>
        getResources.getString(R.string.dataError)
      case HttpObserver.HTTP_NOT_200 =>
        getResources.getString(R.string.serverError)
      case _ =>
        null
    }
  }

  private var toast: Toast = _

  def showToast(msgResId: Int): Unit = {
    showToast(getResources.getString(msgResId))
  }

  def showToast(msg: String): Unit = {
    showToast(msg, Toast.LENGTH_SHORT)
  }

  def showToast(msg: String, duration: Int): Unit = {
    showToast(msg, duration, Gravity.CENTER)
  }

  def showToast(msg: String, duration: Int, gravity: Int): Unit = {
    if (msg != null && msg.nonEmpty) {
      if (toast == null) {
        toast = ToastUtils.makeText(this, msg, Toast.LENGTH_SHORT)
      }
      Option(toast.getView.findViewById[TextView](R.id.toast_text)).fold {
        toast = ToastUtils.makeText(this, msg, Toast.LENGTH_SHORT)
      } { view =>
        view.setText(msg)
      }
      toast.setGravity(gravity, 0, 0)
      toast.setDuration(duration)
      toast.show()
    }
  }

  def setToolbarNavigtion(toolbar: Toolbar, activity: BaseActivity) = {
    toolbar.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(view: View) = activity.finish()
    })
  }

  def getUser(userId: UserId, callBack: OnUserDataGetCallBack): Unit = {
    zms.head.flatMap { z =>
      z.usersStorage.get(userId).map {
        case Some(user) =>
          callBack.getUserData(user)
        case _ =>
          callBack.getUserData(null)
      }
    }
  }
}

object BaseActivity {
  val PermissionsRequestId = 162


  var onConversationMemsunUpdateListener: OnConversationMemsunUpdateListener = _


  def setOnConversationMemsunUpdateListener(onConversationMemsunUpdateListener: OnConversationMemsunUpdateListener): Unit = {
    this.onConversationMemsunUpdateListener = onConversationMemsunUpdateListener
  }


  def updateConvMemsun(zms: ZMessaging, memsum: Int, conversationData: ConversationData): Future[Option[(ConversationData, ConversationData)]] = {
    try {
      zms.convsStorage.update(conversationData.id, _.copy(memsum = Some(memsum))).flatMap {
        case Some((old, updated)) =>
          if (onConversationMemsunUpdateListener != null) onConversationMemsunUpdateListener.onMemsunUpdate(old, updated)
          Future successful Some((old, updated))
        case None =>
          Future successful None
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        Future.successful(None)
    }

  }

}


trait OnConversationMemsunUpdateListener {
  def onMemsunUpdate(old: ConversationData, updated: ConversationData): Unit
}


trait GetUserNickNameCallBack {
  def getNickName(nickname: String)
}
