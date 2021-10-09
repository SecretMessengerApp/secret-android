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

import android.app.AlertDialog
import android.content.{DialogInterface, Intent}
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import androidx.fragment.app.FragmentManager
import com.jsy.common.utils.ModuleUtils
import com.jsy.res.theme.ThemeUtils
import com.jsy.secret.sub.swipbackact.SwipBacActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{AccountsService, BackendConfig}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.{BackendController, SpUtils}


class LaunchActivity extends SwipBacActivity with ActivityHelper with
  LaunchGuideFragment.Containner with DerivedLogTag {

  import LaunchActivity._

  private lazy val backendController = inject[BackendController]

  override def onStart() = {
    super.onStart()
    val callback: BackendConfig => Unit = { be =>
      getApplication.asInstanceOf[WireApplication].ensureInitialized(be)

      startUI()
    }

    if (backendController.shouldShowBackendSelector) showDialog(callback)
    else backendController.getStoredBackendConfig match {
      case Some(be) => callback(be)
      case None =>
        backendController.setStoredBackendConfig(Backend.ProdBackend)
        callback(Backend.ProdBackend)
    }

  }

  private def showDialog(callback: BackendConfig => Unit): Unit = {
    val environments = Backend.byName
    val items: Array[CharSequence] = environments.keys.toArray

    val builder = new AlertDialog.Builder(this)
    builder.setTitle("Select Backend")

    builder.setItems(items, new DialogInterface.OnClickListener {
      override def onClick(dialog: DialogInterface, which: Int): Unit = {
        val choice = items.apply(which).toString
        val config = environments.apply(choice)
        backendController.setStoredBackendConfig(config)
        callback(config)
      }
    })

    builder.setCancelable(false)
    builder.create().show()

    // QA needs to be able to switch backends via intents. Any changes to the backend
    // preference while the dialog is open will be treated as a user selection.
    backendController.onPreferenceSet(callback)
  }

  override def onCreate(savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    if(ThemeUtils.isDarkTheme(this)){
      setTheme(R.style.Theme_Dark)
    } else {
      setTheme(R.style.Theme_Light)
    }
    setContentView(R.layout.activity_launch)
    val logo = findViewById(R.id.iv_logo).asInstanceOf[ImageView]
    if(ThemeUtils.isDarkTheme(this)){
      logo.setImageResource(R.drawable.launch_logo_dark)
    } else {
      logo.setImageResource(R.drawable.launch_logo_light)
    }

    var finishSelf = false
    if (!this.isTaskRoot()) {
      val intent = getIntent();
      if (intent != null) {
        val action = intent.getAction();
        if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN.equals(action)) {
          finish()
          verbose(l"onCreate finish self")
          finishSelf = true
        }
      }
    }

    if (!finishSelf) {
      verbose(l"onCreate start self")
      openState.onUi {
        case FirstOpenState | CloseGuideState =>
          val showedLaunchGuide = SpUtils.getBoolean(this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_LAUNCH_GUIDE_SHOWED, false)
          inject[AccountsService].activeAccountId.head.map {
            case Some(activeId) =>
              verbose(l"activeId:$activeId")
              if (showedLaunchGuide) {
                startMainOrThirdLogin()
              } else {
                getSupportFragmentManager.beginTransaction().replace(R.id.flOverLayer, LaunchGuideFragment.newInstance(), LaunchGuideFragment.TAG).addToBackStack(LaunchGuideFragment.TAG).commit()
              }
              hasOpened = true
            case _ =>
              verbose(l"activeId:None")
              if (showedLaunchGuide) {
                startSignUpOrThirdLogin()
              } else {
                getSupportFragmentManager.beginTransaction().replace(R.id.flOverLayer, LaunchGuideFragment.newInstance(), LaunchGuideFragment.TAG).addToBackStack(LaunchGuideFragment.TAG).commit()
              }
              hasOpened = true
          }(Threading.Ui)
        case _ =>
      }
    }
  }

  override def onNewIntent(intent: Intent) = {
    super.onNewIntent(intent)
    setIntent(intent)
  }

  override def clickStart(): Unit = {
    SpUtils.putBoolean(this, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.SP_KEY_LAUNCH_GUIDE_SHOWED, true)
    getSupportFragmentManager.popBackStackImmediate(LaunchGuideFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    openState ! LaunchActivity.CloseGuideState
  }

  private val openState = Signal[LaunchActivity.OpenState]

  private var sleepCount = 0;
  private var hasOpened = false
  private val SLEEP_COUNT = 10
  private val SLEEP_STEP_TIME = 200

  private def startUI(): Unit = {
    if (hasOpened) {
      // ...
    } else {
      sleepCount = 0;
      new Thread(new Runnable {
        override def run(): Unit = {
          while (!isFinishing && sleepCount < SLEEP_COUNT) {
            Thread.sleep(SLEEP_STEP_TIME)
            sleepCount += 1
            if (sleepCount == SLEEP_COUNT) {
              openState ! LaunchActivity.FirstOpenState
            }
          }
        }
      }).start()

    }
  }

  private def startMainOrThirdLogin() = {
    val intent = new Intent(this, classOf[MainActivity])
    startActivity(intent)
    finish()
  }

  private def startSignUpOrThirdLogin(): Unit = {
    val intent = new Intent(this, ModuleUtils.classForName(ModuleUtils.CLAZZ_AppEntryActivity))
    startActivity(intent)
    finish()
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match {
      case KeyEvent.KEYCODE_BACK =>
        val frag = getSupportFragmentManager.findFragmentByTag(LaunchGuideFragment.TAG)
        if (frag != null) {
          true
        } else {
          super.onKeyDown(keyCode, event)
        }
      case _ =>
        super.onKeyDown(keyCode, event)

    }
  }

}

object LaunchActivity {
  val Tag = classOf[LaunchActivity].getName

  sealed trait OpenState

  case object FirstOpenState extends OpenState

  case object CloseGuideState extends OpenState

}
