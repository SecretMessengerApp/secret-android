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
package com.waz.zclient.participants.fragments

import android.content.Context
import android.os.Bundle
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.Nullable
import androidx.fragment.app.{Fragment, FragmentManager}
import com.jsy.common.fragment.{ForbiddenOptionsFragment, GroupUserInfoFragment, GroupUserNormalFragment, ParticipantDeviceFragment}
import com.jsy.res.utils.ViewUtils
import com.waz.api.User.ConnectionStatus._
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.threading.Threading
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.connect.{PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.integrations.IntegrationDetailsFragment
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, ParticipantsController, UserRequester}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, ManagerFragment, R}

import scala.concurrent.Future

class ParticipantFragment extends ManagerFragment
  with ConversationScreenControllerObserver
  with SendConnectRequestFragment.Container
  with BlockedUserProfileFragment.Container
  with PendingConnectRequestFragment.Container {

  import ParticipantFragment._

  implicit def ctx: Context = getActivity

  import Threading.Implicits.Ui

  override val contentId: Int = R.id.fl__participant__container

  private var bodyContainer: Option[View] = None
  private var participantsContainerView: Option[View] = None

  private lazy val convController = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val screenController = inject[IConversationScreenController]
  private lazy val singleImageController = inject[ISingleImageController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val convScreenController = inject[IConversationScreenController]

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    if (nextAnim == 0 || getParentFragment == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else new DefaultPageTransitionAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      if (enter) getInt(R.integer.framework_animation_duration_medium) else 0,
      1f
    )

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_participant, container, false)) { rootView =>
      bodyContainer = Option(ViewUtils.getView(rootView, (R.id.fl__participant__container)))
      participantsContainerView = Option(ViewUtils.getView(rootView, (R.id.ll__participant__container)))

      withChildFragment(R.id.fl__participant__overlay)(getChildFragmentManager.beginTransaction.remove(_).commitAllowingStateLoss)
    }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    verbose(l"onViewCreated.")

    withChildFragmentOpt(R.id.fl__participant__container) {
      case Some(_) => //no action to take, view was already set
      case _ =>
        (getStringArg(PageToOpenArg) match {
          case Some(GuestOptionsFragment.Tag) =>
            Future.successful((new GuestOptionsFragment, GuestOptionsFragment.Tag))
          case Some(SingleParticipantFragment.TagDevices) =>
            Future.successful((SingleParticipantFragment.newInstance(Some(SingleParticipantFragment.TagDevices)), SingleParticipantFragment.Tag))
          case _ =>
            participantsController.isGroupOrBot.head.map {
              case true if getStringArg(UserToOpenArg).isEmpty =>
                (GroupParticipantsFragment.newInstance(getIntArg2(PARAMS_SOURCE)), GroupParticipantsFragment.Tag)
              case _ =>
                (SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)
            }
        }).map {
          case (f, tag) =>
            getChildFragmentManager.beginTransaction
              .replace(R.id.fl__participant__header__container, ParticipantHeaderFragment.newInstance, ParticipantHeaderFragment.TAG)
              .replace(R.id.fl__participant__container, f, tag)
              .addToBackStack(tag)
              .commitAllowingStateLoss
        }
    }

    participantsController.onShowUser {
      case Some(userId) => showUser(userId)
      case _ =>
    }
  }

  override def onStart(): Unit = {
    super.onStart()
    screenController.addConversationControllerObservers(this)
  }

  override def onStop(): Unit = {
    screenController.removeConversationControllerObservers(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    singleImageController.clearReferences()
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean = {
    withChildFragmentOpt(R.id.fl__participant__overlay) {
      case Some(f: SingleOtrClientFragment) if f.onBackPressed() => true
      case _ =>
        withContentFragment {
          case tempFragment if screenController.isShowingUser && !tempFragment.exists(_.isInstanceOf[GroupParticipantsFragment]) =>
            verbose(l"onBackPressed with screenController.isShowingUser")
            if (tempFragment.isDefined && (tempFragment.get.isInstanceOf[BlockedUserProfileFragment]
              || tempFragment.get.isInstanceOf[PendingConnectRequestFragment]
              || tempFragment.get.isInstanceOf[SendConnectRequestFragment]
              || tempFragment.get.isInstanceOf[ForbiddenOptionsFragment]
              || tempFragment.get.isInstanceOf[ParticipantDeviceFragment])) {
              getChildFragmentManager.popBackStack()
            } else {
              screenController.hideUser()
              participantsController.unselectParticipant()
            }
            true
          case Some(f: FragmentHelper) if f.onBackPressed() => true
          case Some(_: FragmentHelper) =>
            if (getChildFragmentManager.getBackStackEntryCount <= 1) participantsController.onHideParticipants ! true
            else getChildFragmentManager.popBackStack()
            true
          case _ =>
            warn(l"OnBackPressed was not handled anywhere")
            false
        }
    }
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (!inConvList) OptionsMenu(getContext, new ConversationOptionsMenuController(convId, Mode.Normal(inConvList))).show()

  def showOtrClient(userId: UserId, clientId: ClientId): Unit =
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance(userId, clientId),
        SingleOtrClientFragment.Tag
      )
      .addToBackStack(SingleOtrClientFragment.Tag)
      .commitAllowingStateLoss

  def showCurrentOtrClient(): Unit =
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.slide_in_from_bottom_pick_user,
        R.anim.open_new_conversation__thread_list_out,
        R.anim.open_new_conversation__thread_list_in,
        R.anim.slide_out_to_bottom_pick_user)
      .add(
        R.id.fl__participant__overlay,
        SingleOtrClientFragment.newInstance,
        SingleOtrClientFragment.Tag
      )
      .addToBackStack(SingleOtrClientFragment.Tag)
      .commitAllowingStateLoss

  // TODO: AN-5980
  def showIntegrationDetails(service: IntegrationData, convId: ConvId, userId: UserId): Unit = {
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right
      )
      .replace(
        R.id.fl__participant__overlay,
        IntegrationDetailsFragment.newRemovingInstance(service, convId, userId),
        IntegrationDetailsFragment.Tag
      )
      .addToBackStack(IntegrationDetailsFragment.Tag)
      .commitAllowingStateLoss
  }

  override def onHideUser(): Unit = if (screenController.isShowingUser) {
    getChildFragmentManager.popBackStack()
  }

  override def showRemoveConfirmation(userId: UserId): Unit =
    participantsController.showRemoveConfirmation(userId)

  override def dismissUserProfile(): Unit = screenController.hideUser()

  override def dismissSingleUserProfile(): Unit = dismissUserProfile()

  override def onAcceptedConnectRequest(userId: UserId): Unit = {
    screenController.hideUser()
    verbose(l"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      convController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
    }
  }

  override def onUnblockedUser(restoredConversationWithUser: ConvId): Unit = {
//    screenController.hideUser()
    verbose(l"onUnblockedUser $restoredConversationWithUser")
    convController.selectConv(restoredConversationWithUser, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onConnectRequestWasSentToUser(): Unit = {
    screenController.hideUser()
    getChildFragmentManager.popBackStackImmediate(SendConnectRequestFragment.Tag,FragmentManager.POP_BACK_STACK_INCLUSIVE)

  }

  override def onHideOtrClient(): Unit = getChildFragmentManager.popBackStack()


  def openUserProfileFragment(fragment: Fragment, tag: String) = {
    getChildFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.fl__participant__container, fragment, tag)
      .addToBackStack(tag)
      .commitAllowingStateLoss
  }

  def showUser(userId: UserId): Unit = {
    verbose(l"onShowUser($userId)")
    convScreenController.showUser(userId)
    participantsController.selectParticipant(userId)

    KeyboardUtils.hideKeyboard(getActivity)

    for {
      userOpt <- participantsController.getUser(userId)
//      isTeamMember <- userAccountsController.isTeamMember(userId).head
      conversationData <- convController.currentConv.head
    } userOpt match {
      case Some(user) if user.connection == ACCEPTED || user.expiresAt.isDefined /*|| isTeamMember*/ =>
        participantsController.selectParticipant(userId)
        openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)

      case Some(user) if user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
        import com.waz.zclient.connect.PendingConnectRequestFragment._
        openUserProfileFragment(newInstance(userId, UserRequester.PARTICIPANTS), Tag)

      case Some(user) if user.connection == BLOCKED =>
        import BlockedUserProfileFragment._
        openUserProfileFragment(newInstance(userId.str, UserRequester.PARTICIPANTS), Tag)

      case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
        if (conversationData.convType == ConversationType.Group || conversationData.convType == ConversationType.ThousandsGroup) {
          if (SpUtils.getUserId(getContext).equalsIgnoreCase(conversationData.creator.str) || ParticipantsController.isManager(conversationData,UserId(SpUtils.getUserId(getContext)))) {
            openUserProfileFragment(GroupUserInfoFragment.newInstance(conversationData.add_friend), GroupUserInfoFragment.TAG)
          } else if (!conversationData.add_friend) {
            openUserProfileFragment(GroupUserNormalFragment.newInstance(userId.str), GroupUserNormalFragment.TAG)
          } else {
            openUserProfileFragment(SendConnectRequestFragment.newInstance(userId.str, UserRequester.PARTICIPANTS, conversationData.add_friend), SendConnectRequestFragment.Tag)
          }
        } else {
          openUserProfileFragment(SendConnectRequestFragment.newInstance(userId.str, UserRequester.PARTICIPANTS, conversationData.add_friend), SendConnectRequestFragment.Tag)
        }
      case _ =>
    }
  }




}

object ParticipantFragment {
  val TAG: String = classOf[ParticipantFragment].getName
  private val PageToOpenArg = "ARG__FIRST__PAGE"
  private val UserToOpenArg = "ARG__USER"
  private val FromDeepLinkArg = "ARG__FROM__DEEP__LINK"
  private val PARAMS_SOURCE = "params_source"

  def newInstance(page: Option[String]): ParticipantFragment =
    returning(new ParticipantFragment) { f =>
      page.foreach { p =>
        f.setArguments(returning(new Bundle)(_.putString(PageToOpenArg, p)))
      }
    }

  def newInstance(userId: Option[UserId] = None, source: Option[Int] = None, fromDeepLink: Boolean = false): ParticipantFragment =
    returning(new ParticipantFragment) { f =>
      f.setArguments(returning(new Bundle) { b =>
        userId.foreach(tempId => b.putString(UserToOpenArg, tempId.str))
        source.foreach(b.putInt(PARAMS_SOURCE, _))
        b.putBoolean(FromDeepLinkArg, fromDeepLink)
      })
    }
}
