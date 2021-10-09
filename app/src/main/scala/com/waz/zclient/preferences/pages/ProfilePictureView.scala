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
package com.waz.zclient.preferences.pages

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{ImageView, LinearLayout}
import com.bumptech.glide.Glide
import com.jsy.common.acts.{GroupHeadPortraitActivity, PreferencesAdaptActivity}
import com.jsy.common.model.circle.CircleConstant
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.RAssetId
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.common.views.{GlyphButton, ImageAssetDrawable}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.preferences.PreferencesActivity
import com.waz.zclient.utils.{BackStackKey, SpUtils, UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

trait ProfilePictureView {
  def setPictureDrawable(drawable: Drawable): Unit

  def setImageResource(resId: Int): Unit

  def setRAssetId(remoteId: RAssetId): Unit

  def isShowCameraButton(isShow: Boolean)
}

class ProfilePictureViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ProfilePictureView with ViewHelper{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preference_profile_picture_layout)

  val image = findById[ImageView](R.id.profile_user_picture)
  val cameraButton = findById[GlyphButton](R.id.profile_user_camera)

  cameraButton.setOnClickListener(new OnClickListener {
    override def onClick(v: View) = {
//      Option(context.asInstanceOf[PreferencesActivity]).foreach(_.getControllerFactory.getCameraController.openCamera(CameraContext.SETTINGS))
      context match {
        case activity: PreferencesActivity =>
          Option(activity).foreach(_.getControllerFactory.getCameraController.openCamera(CameraContext.SETTINGS))
        case activity: PreferencesAdaptActivity =>
          Option(activity).foreach(_.getControllerFactory.getCameraController.openCamera(CameraContext.SETTINGS))
        case activity: GroupHeadPortraitActivity =>
          Option(activity).foreach(_.getControllerFactory.getCameraController.openCamera(CameraContext.GROUP_HEAD_PORTRAIT))
        case _ =>
      }
    }
  })

  override def setPictureDrawable(drawable: Drawable) = image.setImageDrawable(drawable)

  override def setImageResource(resId: Int) = image.post(new Runnable {
    override def run(): Unit = image.setImageResource(resId)
  })

  override def setRAssetId(remoteId: RAssetId) = image.post(new Runnable {
    override def run(): Unit = {
      Glide
        .`with`(context)
        .load(CircleConstant.appendAvatarUrl(if (null == remoteId) "" else remoteId.str, context))
        .into(image)
    }
  })

  override def isShowCameraButton(isShow: Boolean) = cameraButton.post(new Runnable {
    override def run(): Unit = {
      if (isShow) {
        cameraButton.setVisibility(View.VISIBLE)
      } else {
        cameraButton.setVisibility(View.GONE)
      }
    }
  })
}

case class ProfilePictureBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_account_picture_title

  override def layoutId = R.layout.preference_profile_picture

  var controller = Option.empty[PictureViewController]

  override def onViewAttached(v: View) = {
    if (args != null && args.getSerializable(classOf[From].getSimpleName) != null) {
      val from = args.getSerializable(classOf[From].getSimpleName)
      from match {
        case From.FromUploadGroupHead =>
          controller = Option(v.asInstanceOf[ProfilePictureViewImpl]).map(v => new GroupHeadPictureViewController(v)(v.wContext.injector, v))
        case From.FromUploadSelfHead =>
          controller = Option(v.asInstanceOf[ProfilePictureViewImpl]).map(v => new ProfilePictureViewController(v)(v.wContext.injector, v))
        case _ =>

      }
    }
  }

  override def onViewDetached() = {
    controller = None
  }
}

class PictureViewController(view: ProfilePictureView)(implicit inj: Injector, ec: EventContext) extends Injectable {

}

class ProfilePictureViewController(view: ProfilePictureView)(implicit inj: Injector, ec: EventContext) extends PictureViewController(view: ProfilePictureView) {
  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]

  val image = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
    image <- self.picture.map(WireImage).fold(Signal.empty[ImageSource])(Signal(_))
  } yield image

  view.setPictureDrawable(new ImageAssetDrawable(image, scaleType = ScaleType.CenterInside))
}

class GroupHeadPictureViewController(view: ProfilePictureView)(implicit inj: Injector, ec: EventContext) extends PictureViewController(view: ProfilePictureView) with DerivedLogTag {

  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  val conversationController = inject[ConversationController]

  view.setImageResource(R.drawable.upload_icon)
  view.isShowCameraButton(false)

  conversationController.currentConv.map(_.creator).onUi { creator =>
    currentAccount.currentValue.foreach { acc =>
      view.isShowCameraButton(acc.id == creator)
    }
  }

  conversationController.currentConv.map(_.bigRAssetId).onUi { rAssetId =>
    showBigImage(rAssetId)
  }

  def showBigImage(bigImageRAssetId: RAssetId): Unit = {
    if (bigImageRAssetId != null) {
      view.setRAssetId(bigImageRAssetId)
    } else {
      view.setImageResource(R.drawable.upload_icon)
    }
  }

}

object From extends Enumeration {
  val FromUploadGroupHead = Value(1, "group head")
  val FromUploadSelfHead = Value(2, "person head")

  def initArgs(from: From.Value): Bundle = {
    val args: Bundle = new Bundle()
    args.putSerializable(classOf[From].getSimpleName, from)
    args
  }

}

class From extends Enumeration {

}
