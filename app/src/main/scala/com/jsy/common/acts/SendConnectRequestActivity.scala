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

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.jsy.common.fragment.{GroupUserNormalFragment, SingleParticipantPaymentFragment, SinglePaticipantForCreatorFragment}
import com.jsy.common.model.ThousandGroupUserModel.ThousandGroupUserItemModel
import com.jsy.common.{OnLoadUserListener, ConversationApi}
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener
import com.waz.api.User.ConnectionStatus
import com.waz.model.{ConvId, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.controllers.{SoundController, ThemeController}
import com.waz.zclient.connect.{PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, TwoButtonConfirmationCallback}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.participants.fragments.SingleParticipantFragment
import com.waz.zclient.participants.{ParticipantsController, UserRequester}
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils._
import com.waz.zclient.views.menus.ConfirmationMenu

import scala.concurrent.ExecutionContext


class SendConnectRequestActivity extends BaseActivity with Injectable with SendConnectRequestFragment.Container {

  import SendConnectRequestActivity._

  private implicit val wContext = WireContext(ZApplication.getInstance())
  private implicit val executionContext = ExecutionContext.Implicits.global
  //  implicit val eventContext = EventContext.Implicits.global

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val convController = inject[ConversationController]
  private var tool: Toolbar = _

  private var confirmationMenu: ConfirmationMenu = _

  override def canUseSwipeBackLayout: Boolean = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_sendconnect_request)

    tool = findById[Toolbar](R.id.send_connect_tool)

    tool.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        onBackPressed()
      }
    })

    confirmationMenu = ViewUtils.getView(this, R.id.cm__confirm_action_light)
    accentColorController.accentColor.map(_.color).onUi(color => confirmationMenu.setButtonColor(color))
    confirmationMenu.setVisibility(View.GONE)

    val self = SpUtils.getUserId(this)
    val userId = getIntent.getStringExtra(classOf[UserId].getSimpleName)
    val uid = new UserId(userId)
    val allowUserAddFriend = getIntent.getBooleanExtra(INTENT_KEY_allowUserAddFriend, false)
    val friendIsDetails = getIntent.getBooleanExtra(INTENT_KEY_friendIsDetails, false)
    val clickUserChatHead = getIntent.getBooleanExtra(INTENT_KEY_clickUserChatHead, false)
    val thousandGroupUserItemModel = getIntent.getSerializableExtra(classOf[ThousandGroupUserItemModel].getSimpleName).asInstanceOf[ThousandGroupUserItemModel]
    val userName = if (thousandGroupUserItemModel == null) "" else thousandGroupUserItemModel.getName

    showProgressDialog(R.string.loading)
    ConversationApi.loadUser(uid, new OnLoadUserListener {
      override def onSuc(user: UserData): Unit = {

        import ConnectionStatus._
        dismissProgressDialog()
        if (user.connection == ACCEPTED || user.expiresAt.isDefined) {
          if (thousandGroupUserItemModel != null) {
            participantsController.selectParticipant(new UserId(userId))
            getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, new SingleParticipantPaymentFragment(new UserId(userId))).addToBackStack(SingleParticipantPaymentFragment.TAG).commit
          } else {
            if (friendIsDetails) {
              participantsController.selectParticipant(new UserId(userId))
              getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, SingleParticipantFragment.newInstance()).addToBackStack(SingleParticipantFragment.Tag).commit
              ColorUtils.setBackgroundColor(findById[FrameLayout](R.id.fl__participant__container))
            } else {
              convController.selectConv(ConvId(uid.str), ConversationChangeRequester.CONVERSATION_LIST)
            }
          }
        } else if (user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED) {
          getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container,  PendingConnectRequestFragment.newInstance(new UserId(userId),UserRequester.PARTICIPANTS)).addToBackStack(PendingConnectRequestFragment.Tag).commit
        } else if (user.connection == BLOCKED) {
          participantsController.selectParticipant(new UserId(userId))
          getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, new SingleParticipantPaymentFragment(new UserId(userId))).addToBackStack(SingleParticipantPaymentFragment.TAG).commit
        } else if (user.connection == CANCELLED || user.connection == UNCONNECTED) {
          if (thousandGroupUserItemModel != null || clickUserChatHead) {
            convController.currentConv.currentValue.foreach { conversationData =>
              if (self.equalsIgnoreCase(conversationData.creator.str) || ParticipantsController.isManager(conversationData,UserId(self))) {
                participantsController.selectParticipant(new UserId(userId))
                getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, new SinglePaticipantForCreatorFragment(new UserId(userId), userName)).commit
              } else if(!conversationData.add_friend){
                getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, GroupUserNormalFragment.newInstance(userId)).commit
              }else{
                getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, SendConnectRequestFragment.newInstance(userId, UserRequester.SEARCH, allowUserAddFriend)).commit
              }
            }
          } else {
            getSupportFragmentManager.beginTransaction.replace(R.id.fl__participant__container, SendConnectRequestFragment.newInstance(userId, UserRequester.SEARCH, allowUserAddFriend)).commit
          }
        } else {
          // ...
        }
      }

      override def onFail(): Unit = {
        dismissProgressDialog()
      }
    })
  }

  def slideFragmentInFromRight(f: Fragment, tag: String): Unit =
    getSupportFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.fl__participant__container, f, tag)
      .addToBackStack(tag)
      .commit


  def showConfirmationMenu(userId: UserId) = {

    ConversationApi.loadUser(userId, new OnLoadUserListener {
      override def onSuc(userData: UserData): Unit = {
        val request = new ConfirmationRequest.Builder()
          .withHeader(getString(R.string.confirmation_menu__header))
          .withMessage(getString(R.string.confirmation_menu_text_with_name, userData.getDisplayName))
          .withPositiveButton(getString(R.string.confirmation_menu__confirm_remove))
          .withNegativeButton(getString(R.string.confirmation_menu__cancel))
          .withConfirmationCallback(new TwoButtonConfirmationCallback() {
            override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
              convController.removeMember(userId)
              finish()
            }

            override def negativeButtonClicked(): Unit = {}

            override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
          })
          .withWireTheme(inject[ThemeController].getThemeDependentOptionsTheme)
          .build
        confirmationMenu.onRequestConfirmation(request)
        inject[SoundController].playAlert()
      }

      override def onFail(): Unit = {

      }
    })
  }


  override def onBackPressed(): Unit = {
    if (confirmationMenu.getVisibility == View.VISIBLE) {
      confirmationMenu.animateToShow(false)
    } else {
      val frag = Option(getSupportFragmentManager.findFragmentById(R.id.fl__participant__container))
      frag.fold {
        finish()
      } {
        case frag: OnBackPressedListener if (frag.onBackPressed()) =>
        // ...
        case frag =>
          if (frag.isInstanceOf[SingleParticipantFragment]) {
            participantsController.unselectParticipant()
          }
          finish()
      }
    }
  }

  override def onConnectRequestWasSentToUser(): Unit = {
    finish()
  }

  override def showRemoveConfirmation(userId: UserId): Unit = {}

  override def dismissUserProfile(): Unit = {}

  override def dismissSingleUserProfile(): Unit = {}
}


object SendConnectRequestActivity {

  import android.content.Context

  val INTENT_KEY_allowUserAddFriend = "allowUserAddFriend"
  val INTENT_KEY_friendIsDetails = "friendIsDetails"
  val INTENT_KEY_clickUserChatHead = "clickUserChatHead"

  def startSelf(userId: String, context: Context, allowUserAddFriend: Boolean, thousandGroupUserItemModel: ThousandGroupUserItemModel): Unit = {
    startSelf(userId, context, allowUserAddFriend, thousandGroupUserItemModel, false)
  }

  def startSelf(userId: String, context: Context, allowUserAddFriend: Boolean, thousandGroupUserItemModel: ThousandGroupUserItemModel, friendIsDetails: Boolean = false,clickUserChatHead : Boolean = false): Unit = {
    val intent = new Intent(context, classOf[SendConnectRequestActivity])
    intent.putExtra(classOf[UserId].getSimpleName, userId)
    intent.putExtra(INTENT_KEY_allowUserAddFriend, allowUserAddFriend)
    intent.putExtra(INTENT_KEY_friendIsDetails, friendIsDetails)
    intent.putExtra(INTENT_KEY_clickUserChatHead, clickUserChatHead)
    intent.putExtra(classOf[ThousandGroupUserItemModel].getSimpleName, thousandGroupUserItemModel)
    context.startActivity(intent)
  }
}

