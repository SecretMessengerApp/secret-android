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

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import androidx.fragment.app.{Fragment, FragmentManager}
import com.jsy.common.fragment.{ConversationBlockedFragment, FriendRequestListFragment}
import com.jsy.common.moduleProxy.ProxyConversationActivity
import com.jsy.common.utils.ModuleUtils
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.{ActivityContainner, interfaces}
import com.waz.api.{IConversation, MessageContent}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.tracking.GroupConversationEvent
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.camera.CameraFragment
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.collection.fragments.CollectionFragment
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{ScreenController, UserAccountsController}
import com.waz.zclient.connect.{ConnectRequestFragment, PendingConnectRequestManagerFragment}
import com.waz.zclient.controllers.camera.{CameraActionObserver, ICameraController}
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.controllers.confirmation.{ConfirmationObserver, ConfirmationRequest, IConfirmationController}
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingDestination.CAMERA_PREVIEW_VIEW
import com.waz.zclient.controllers.location.{ILocationController, LocationObserver}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.controllers.singleimage.{ISingleImageController, SingleImageObserver}
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.conversation.{ConversationController, ImageFragment, LikesAndReadsFragment}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.drawing.DrawingFragment
import com.waz.zclient.giphy.GiphySharingPreviewFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.messages.controllers.NavigationController
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.pages.main.conversation.LocationFragment
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.participants.fragments.ParticipantFragment
import com.waz.zclient.participants.{ParticipantsController, UserRequester}
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.views.ConversationFragment
import com.waz.zclient.views.menus.ConfirmationMenu

import scala.util.{Failure, Success}

class ConversationActivity extends BaseActivity
  with LocationObserver
  with CameraActionObserver
  with UserProfileContainer
  with SingleImageObserver
  with ConfirmationObserver
  with ProxyConversationActivity
  with ConversationFragment.Container
  with PendingConnectRequestManagerFragment.Container
  with FriendRequestListFragment.Container
  with ConnectRequestFragment.Container
  with CollectionsObserver
  with DerivedLogTag {

  import ConversationActivity._
  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val callStartController = inject[CallStartController]
  private lazy val convController = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val userAccountsController = inject[UserAccountsController]

  private var subs = Set.empty[com.waz.utils.events.Subscription]
  private lazy val keyboard = inject[KeyboardController]
  private lazy val cameraController = inject[ICameraController]
  private lazy val locationController = inject[ILocationController]
  private lazy val navigationController = inject[INavigationController]
  private lazy val screenController = inject[ScreenController]
  private lazy val createConvController = inject[CreateConversationController]
  private lazy val singleImageController = inject[ISingleImageController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val messagesController = inject[MessagesController]
  private lazy val conversationListController = inject[ConversationListController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val collectionController = inject[CollectionController]

  private lazy val removedOrLeft = (for {
    unActivited <- convController.currentConv.map(!_.isActive)
  } yield {
    unActivited
  }).onUi { unActivited =>
    if (unActivited) {
      ActivityContainner.finishAndRemoveAllBeside(ModuleUtils.CLAZZ_MainActivity)
    }
  }

  private var flameConversationActFragsParent: View = _
  private var confirmationMenu: ConfirmationMenu = _

  override def enableWhiteStatusBar(): Boolean = false

  override protected def customInitStatusBar(): Unit = {
    val color = ColorUtils.getAttrColor(this, R.attr.ToolbarBackgroundColor);
    setStatusBarColorSpecial(color,color)
  }

  override protected def onPause(): Unit = {
    super.onPause()
    KeyboardUtils.closeKeyboardIfShown(this)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_conversation)
    findView()

    convController.currentConv.currentValue.foreach { conversationData =>
      val showFragment = if (conversationData.convType == IConversation.Type.INCOMING_CONNECTION) {
        val userId = if(null == conversationData.id) "" else conversationData.id.str
        FriendRequestListFragment.newInstance(new UserId(userId))
      } else if (conversationData.convType == IConversation.Type.WAIT_FOR_CONNECTION) {
        val userId = if(null == conversationData.id) "" else conversationData.id.str
        PendingConnectRequestManagerFragment.newInstance(new UserId(userId), UserRequester.CONVERSATION)
      } else if(conversationData.isGroupBlocked) {
        ConversationBlockedFragment.newInstance()
      } else {
        ConversationFragment.newInstance()
      }
      getSupportFragmentManager.beginTransaction()
        .replace(R.id.content_layout, showFragment, showFragment.getClass.getName)
        .commitAllowingStateLoss()
    }

    subs += removedOrLeft

    subs += convController.convChanged.onUi { change =>
      import ConversationChangeRequester._
      if (
        change.requester == START_CONVERSATION ||
          change.requester == LEAVE_CONVERSATION ||
          change.requester == DELETE_CONVERSATION ||
          change.requester == BLOCK_USER ||
          change.requester == CONVERSATION_LIST
      ) {

        if (getSupportFragmentManager.findFragmentByTag(CameraFragment.Tag) != null){
          cameraController.closeCamera(CameraContext.MESSAGE)
        }

        screenController.showMessageDetails ! None

        participantsController.onHideParticipants ! false
      } else if (!change.noChange) {
        collectionController.closeCollection()
      }
    }

    subs += screenController.showMessageDetails.onUi {
      case Some(ScreenController.MessageDetailsParams(_, tab)) =>
        showFragment(LikesAndReadsFragment.newInstance(tab), LikesAndReadsFragment.Tag)
      case None =>
        getSupportFragmentManager.popBackStack(LikesAndReadsFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    subs += participantsController.onShowParticipants.onUi { childTag =>
      keyboard.hideKeyboardIfVisible()
      showFragment(ParticipantFragment.newInstance(childTag), ParticipantFragment.TAG)
    }

    subs += participantsController.onShowParticipantsWithUserId.onUi { user =>
      keyboard.hideKeyboardIfVisible()
      participantsController.selectParticipant(user.userId)
      showFragment(ParticipantFragment.newInstance(Option(user.userId)), ParticipantFragment.TAG)
    }

    subs += participantsController.onShowParticipantSource.onUi { source =>
      keyboard.hideKeyboardIfVisible()
      showFragment(ParticipantFragment.newInstance(source = Option(source)), ParticipantFragment.TAG)
    }

    subs += participantsController.onHideParticipants.onUi { withAnimations =>

      if (withAnimations)
        getSupportFragmentManager.popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      else {
        FragmentHelper.allowAnimations = false
        getSupportFragmentManager.popBackStackImmediate(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        FragmentHelper.allowAnimations = true
      }
    }

    subs += screenController.showGiphy.onUi { searchTerm =>
      import GiphySharingPreviewFragment._
      showFragment(newInstance(searchTerm), Tag, Page.GIPHY)
    }
    subs += screenController.hideGiphy.onUi(_ => hideFragment(GiphySharingPreviewFragment.Tag))

    subs += screenController.showSketch.onUi { sketch =>
      import DrawingFragment._
      showFragment(newInstance(sketch), Tag, Page.DRAWING)
    }
    subs += screenController.hideSketch.onUi { dest =>
      hideFragment(DrawingFragment.Tag)
      if (dest == CAMERA_PREVIEW_VIEW) cameraController.closeCamera(CameraContext.MESSAGE)
    }

    subs += (for {
      convsUi <- zms.map(_.convsUi)
      convId <- convController.currentConv
      lastMessage <- conversationListController.lastMessage(convId.id)
      _ = lastMessage.lastMsg.foreach(it => convsUi.setLastRead(convId.id, it))
    } yield lastMessage).on(Threading.Implicits.Background) { lastMessage =>
      lastMessage.lastMsg.foreach(msg => messagesController.onMessageRead(msg))
    }
  }

  override def showCreateGroupConversationFragment(): Unit = {
    inject[CreateConversationController].setCreateConversation(from = GroupConversationEvent.StartUi)
    showFragment(CreateConversationManagerFragment.newInstance, CreateConversationManagerFragment.TAG)
  }

  override def removeCreateGroupConversationFragment(): Unit = {
    val frag = getSupportFragmentManager.findFragmentByTag(CreateConversationManagerFragment.TAG)
    if (frag != null) {
      getSupportFragmentManager.popBackStackImmediate(CreateConversationManagerFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
  }


  private def showFragment(fragment: Fragment, tag: String): Unit =
    showFragment(fragment, tag, None)

  private def showFragment(fragment: Fragment, tag: String, page: Page): Unit =
    showFragment(fragment, tag, Some(page))

  private def showFragment(fragment: Fragment, tag: String, page: Option[Page], withAnim: Boolean = true): Unit = {
    val transaction = getSupportFragmentManager.beginTransaction
    if (withAnim) {
      transaction.setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
    }
    transaction.replace(R.id.flameConversationActFragsParent, fragment, tag)
      .addToBackStack(tag)
      .commitAllowingStateLoss
  }

  private def hideFragment(tag: String): Unit = {
    getSupportFragmentManager.popBackStack(tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  }

  override def onStart(): Unit = {
    super.onStart()
    verbose(l"$TAG onStart--------${System.currentTimeMillis()}  acts.size:${ActivityContainner.getBaseActivities.size()}")
    inject[NavigationController].conversationActivityActive.mutate(_ + 1)
    cameraController.addCameraActionObserver(this)
    locationController.addObserver(this)
    singleImageController.addSingleImageObserver(this)
    confirmationController.addConfirmationObserver(this)
    collectionController.addObserver(this)
  }

  override def onStop(): Unit = {
    locationController.removeObserver(this)
    cameraController.removeCameraActionObserver(this)
    inject[NavigationController].conversationActivityActive.mutate(_ - 1)
    singleImageController.removeSingleImageObserver(this)
    confirmationController.removeConfirmationObserver(this)
    collectionController.removeObserver(this)
    super.onStop()
    verbose(l"$TAG onStop--------${System.currentTimeMillis()}")
  }

  override def onRequestConfirmation(confirmationRequest: ConfirmationRequest, requester: Int): Unit = {
    if (requester == IConfirmationController.PARTICIPANTS) confirmationMenu.onRequestConfirmation(confirmationRequest)
  }


  override def onDestroy(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    keyboard.hideKeyboardIfVisible()
    super.onDestroy()
    verbose(l"$TAG onDestroy--------${System.currentTimeMillis()}")
  }

  override def onBackPressed(): Unit = {

    if (confirmationMenu.getVisibility == View.VISIBLE) {
      confirmationMenu.animateToShow(false)
    }

    val fragment = getSupportFragmentManager.findFragmentById(R.id.flameConversationActFragsParent)
    if (fragment != null) {
      fragment match {
        case onBackPressedListener: interfaces.OnBackPressedListener =>
          if (!onBackPressedListener.onBackPressed()) {
            getSupportFragmentManager.popBackStack()
          }
        case _                                                       =>
          getSupportFragmentManager.popBackStack()
      }
    } else {
      getSupportFragmentManager.findFragmentById(R.id.content_layout) match {
        case fragment: ConversationFragment =>
          if (!fragment.onBackPressed()) finish()
        case _                              =>
          finish()
      }
    }
  }


  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    Option(getSupportFragmentManager.findFragmentById(R.id.flameConversationActFragsParent)).foreach(_.onActivityResult(requestCode, resultCode, data))
  }

  override def openCollection(): Unit =
    showFragment(CollectionFragment.newInstance(), CollectionFragment.TAG, Page.COLLECTION)

  override def closeCollection(toConv: Boolean): Unit = {
    KeyboardUtils.closeKeyboardIfShown(this)
    if (toConv) {
      getSupportFragmentManager.popBackStack(ParticipantFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }
    getSupportFragmentManager.popBackStack(CollectionFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    navigationController.setVisiblePage(Page.MESSAGE_STREAM, ConversationFragment.TAG)
  }

  override def onBitmapSelected(input: RawAssetInput, cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE) {
      inject[ConversationController].sendMessage(input, this)
      cameraController.closeCamera(CameraContext.MESSAGE)
    }

  override def onOpenCamera(cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE)
      showFragment(CameraFragment.newInstance(CameraContext.MESSAGE), CameraFragment.Tag, Page.CAMERA)

  override def onCloseCamera(cameraContext: CameraContext): Unit =
    if (cameraContext == CameraContext.MESSAGE) hideFragment(CameraFragment.Tag)

  override def onShowShareLocation(): Unit =
    showFragment(LocationFragment.newInstance, LocationFragment.TAG, Page.SHARE_LOCATION)

  override def onHideShareLocation(location: MessageContent.Location): Unit = {
    if (location != null) convController.sendMessage(location, this)
    hideFragment(LocationFragment.TAG)
  }

  override def onCameraNotAvailable(): Unit = {
  }

  override def showRemoveConfirmation(userId: UserId): Unit = {
  }

  override def dismissUserProfile(): Unit = dismissSingleUserProfile()

  override def dismissSingleUserProfile(): Unit = {
    getSupportFragmentManager.popBackStackImmediate
    navigationController.setVisiblePage(Page.MESSAGE_STREAM, ConversationFragment.TAG)
  }

  override def dismissInboxFragment(): Unit = {
    verbose(l"dismissInboxFragment")
    finish()
  }


  override def onAcceptedConnectRequest(userId: UserId): Unit = {
    verbose(l"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      conversationController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST)
    }
  }

  private def findView(): Unit = {
    flameConversationActFragsParent = ViewUtils.getView(this, R.id.flameConversationActFragsParent)
    confirmationMenu = ViewUtils.getView(this, R.id.cm__confirm_action_light)
    accentColorController.accentColor.map(_.color).onUi(color => confirmationMenu.setButtonColor(color))
    confirmationMenu.setVisibility(View.GONE)
  }

  override def onShowSingleImage(messageId: String): Unit = {

    import ImageFragment._

    keyboard.hideKeyboardIfVisible()
    getSupportFragmentManager
      .beginTransaction
      .add(R.id.flameConversationActFragsParent, ImageFragment.newInstance(messageId), ImageFragment.Tag)
      .addToBackStack(ImageFragment.Tag)
      .commitAllowingStateLoss
    navigationController.setVisiblePage(Page.SINGLE_MESSAGE, Tag)
  }

  override def onHideSingleImage(): Unit = ()

  override def canUseSwipeBackLayout: Boolean = true

  override def updateAdvisoryReadStatus(convId: ConvId, isRead: Boolean): Unit = {
    for {
      zms <- zms.head
    } yield {
      zms.convsStorage.update(convId, _.copy(advisory_is_read = true)).flatMap {
        case Some(r@(old, updated)) =>
          scala.concurrent.Future.successful(Some(r))
        case _ =>
          scala.concurrent.Future.successful(None)
      }
    }
  }

  override def updateAdvisoryDialogStatus(convId: ConvId, isShow: Boolean): Unit = {
    for {
      zms <- zms.head
    } yield {
      zms.convsStorage.update(convId, _.copy(advisory_show_dialog=isShow)).flatMap {
        case Some(r@(old, updated)) =>
          scala.concurrent.Future.successful(Some(r))
        case _ =>
          scala.concurrent.Future.successful(None)
      }
    }
  }

  override def deleteReportNoticeMessage(convId: ConvId, msgId: MessageId): Unit = {
    for {
      zms <- zms.head
    } yield {
      zms.messagesStorage.deleteReportNoticeMessage(convId).onComplete {
        case Failure(exception) =>
          verbose(l"deleteReportNoticeMessage Failure(exception) :${exception.getLocalizedMessage}")
        case Success(value) =>
          verbose(l"deleteReportNoticeMessage Success(value)")
      }
    }
  }
}

object ConversationActivity {

  val TAG = classOf[ConversationActivity].getSimpleName

  def start(context: Context): Unit = context.startActivity(new Intent(context, classOf[ConversationActivity]))
}
