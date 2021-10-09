/**
 * Secret
 * Copyright (C) 2019 Secret
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
import android.content.res.Configuration
import android.content.{Context, Intent}
import android.os.Bundle
import android.view.{KeyEvent, View, ViewGroup}
import android.widget.{TextView, Toast}
import androidx.annotation.Nullable
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.{FragmentManager, FragmentTransaction}
import com.jsy.res.utils.ViewUtils
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.camera.CameraFragment
import com.waz.zclient.controllers.camera.{CameraActionObserver, ICameraController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.preferences.pages.{From, ProfilePictureBackStackKey}
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils._
import com.waz.zclient.{BaseActivity, R}

class GroupHeadPortraitActivity extends BaseActivity
  with BackStackNavigator.Container
  with CameraActionObserver {


  private lazy val backStackNavigator = inject[BackStackNavigator]
  private lazy val cameraController = inject[ICameraController]
  private lazy val conversationController = inject[ConversationController]

  lazy val titleView = findViewById[TextView](R.id.settings_adp_toolbar__title)

  override def canUseSwipeBackLayout: Boolean = true

  @SuppressLint(Array("PrivateResource"))
  override def onCreate(@Nullable savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    val view = getLayoutInflater.inflate(R.layout.activity_settings_adapt, null)
    setContentView(view)
    val toolBar = findViewById[Toolbar](R.id.settings_adp_toolbar)

    toolBar.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (getSupportFragmentManager.findFragmentByTag(CameraFragment.Tag) != null) {
          getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } else {
          if (!backStackNavigator.back()) {
            finish()
          }
        }
      }
    })

    if (LayoutSpec.isPhone(this)) ViewUtils.lockScreenOrientation(Configuration.ORIENTATION_PORTRAIT, this)

    backStackNavigator.setup(findViewById(R.id.fl__root__content).asInstanceOf[ViewGroup], this)
    backStackNavigator.goTo(ProfilePictureBackStackKey(From.initArgs(From.FromUploadGroupHead)))
    ColorUtils.setBackgroundColor(view)


  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (getSupportFragmentManager.findFragmentByTag(CameraFragment.Tag) != null) {
        getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      } else {
        if (!backStackNavigator.back()) {
          finish()
        }
      }
    }
    super.onKeyDown(keyCode, event)
  }


  override def onSaveInstanceState(outState: Bundle) = {
    super.onSaveInstanceState(outState)

  }

  override def onStart(): Unit = {
    super.onStart()
    cameraController.addCameraActionObserver(this)
  }

  override def onStop(): Unit = {
    super.onStop()
    cameraController.removeCameraActionObserver(this)
  }


  override def onCurrentStackChange(fromShowStack: BackStackKey, toShowStack: BackStackKey, isNew: Boolean): Unit = toShowStack match {
    case _: ProfilePictureBackStackKey => titleView.setText(R.string.conversation_group_profile_photo)
  }

  //  //TODO do we need to check internet connectivity here?
  override def onBitmapSelected(input: AssetService.RawAssetInput, cameraContext: CameraContext): Unit = {
    if (cameraContext == CameraContext.GROUP_HEAD_PORTRAIT) {
      inject[Signal[ZMessaging]].head.map { zms =>
        conversationController.currentConv.currentValue.foreach { conversationData =>
          zms.conversations.updateGroupPicture(input, conversationData.remoteId)
        }
      }(Threading.Background)
      getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }

  override def onCameraNotAvailable() =
    Toast.makeText(this, "Camera not available", Toast.LENGTH_SHORT).show()

  override def onOpenCamera(cameraContext: CameraContext) = {
    if (cameraContext == CameraContext.GROUP_HEAD_PORTRAIT) {
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

  def onCloseCamera(cameraContext: CameraContext) = {
    if (cameraContext == CameraContext.GROUP_HEAD_PORTRAIT) {
      getSupportFragmentManager.popBackStack(CameraFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }

}

object GroupHeadPortraitActivity {
  def startSelf(context: Context): Unit = {
    val intent = new Intent(context, classOf[GroupHeadPortraitActivity])
    //      .putExtra(classOf[ConversationInfo].getSimpleName, conversationInfo)
    context.startActivity(intent)
  }

}

