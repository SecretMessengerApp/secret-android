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
package com.waz.zclient.connect

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import com.jsy.res.utils.ViewUtils
import com.waz.model.{ConversationData, UserId}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.connect.PendingConnectRequestFragment.ArgUserRequester
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.ProfileAnimation
import com.waz.zclient.participants.UserRequester
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.menus.{FooterMenu, FooterMenuCallback}
import com.waz.zclient.{FragmentHelper, R}

class SendConnectRequestFragment extends BaseFragment[SendConnectRequestFragment.Container]
  with FragmentHelper {

  import SendConnectRequestFragment._
  import Threading.Implicits.Ui

  implicit def context: Context = getActivity

  private lazy val allowShowAddFriend = getArguments.getBoolean(ArgumentAllowUserAddFriend)
  private lazy val userToConnectId = UserId(getArguments.getString(ArgumentUserId))
  private lazy val userRequester = UserRequester.valueOf(getArguments.getString(ArgumentUserRequester))

  private lazy val usersController = inject[UsersController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val conversationController = inject[ConversationController]
  private lazy val keyboardController = inject[KeyboardController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val themeController = inject[ThemeController]

  private lazy val user = usersController.user(userToConnectId)

  private var subs = Set.empty[com.waz.utils.events.Subscription]

  private lazy val removeConvMemberFeatureEnabled = for {
    convId <- conversationController.currentConvId
    permission <- userAccountsController.hasRemoveConversationMemberPermission(convId)
  } yield permission && userRequester == UserRequester.PARTICIPANTS

  private var connectButton: Option[ZetaButton] = None

  private var footerMenu: Option[FooterMenu] = None

  private var imageViewProfile: Option[ChatHeadViewNew] = None

  private var userNameView: Option[TypefaceTextView] = None

  private var ttvNoPermissionForHandler: Option[TypefaceTextView] = None

  private var userHandleView: Option[TypefaceTextView] = None

  private val loadedConversationData = Signal(Option.empty[ConversationData])

  loadedConversationData.onUi {
    case Some(conversationData) =>
      dismissProgressDialog()
      keyboardController.hideKeyboardIfVisible()
      getContainer.onConnectRequestWasSentToUser()
    case None =>

  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    def defaultAnimation = super.onCreateAnimation(transit, enter, nextAnim)

    def isConvRequester = {
      val userRequester = UserRequester.valueOf(getArguments.getString(ArgUserRequester))
      userRequester == UserRequester.CONVERSATION
    }

    if (isConvRequester || nextAnim != 0) defaultAnimation
    else {
      val centerX = getOrientationIndependentDisplayWidth(getActivity) / 2
      val centerY = getOrientationIndependentDisplayHeight(getActivity) / 2
      val duration =
        if (enter) getInt(R.integer.open_profile__animation_duration)
        else getInt(R.integer.close_profile__animation_duration)
      val delay =
        if (enter) getInt(R.integer.open_profile__delay)
        else 0

      new ProfileAnimation(enter, duration, delay, centerX, centerY)
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_send_connect_request, viewContainer, false)) { rootView =>
      connectButton = Option(ViewUtils.getView(rootView, R.id.zb__send_connect_request__connect_button))
      footerMenu = Option(ViewUtils.getView(rootView, R.id.fm__footer))
      imageViewProfile = Option(ViewUtils.getView(rootView, R.id.send_connect))
      userNameView = Option(ViewUtils.getView(rootView, R.id.user_name))
      ttvNoPermissionForHandler = Option(ViewUtils.getView(rootView, R.id.ttvNoPermissionForHandler))
      userHandleView = Option(ViewUtils.getView(rootView, R.id.user_handle))

    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {

    connectButton.foreach(_.onClick {
      doConnectFriend()
    })

    subs += accentColorController.accentColor.map(_.color).onUi { color => connectButton.foreach(_.setAccentColor(color)) }

    subs += user.map(_.expiresAt.isDefined).map {
      case true => ("", "")
      case _ => (getString(R.string.send_connect_request__connect_button__text), getString(R.string.glyph__plus))
    }.onUi { case (label, glyph) =>
      footerMenu.foreach { footer =>
        footer.setLeftActionLabelText(label)
        footer.setLeftActionText(glyph)
      }
    }

    subs += removeConvMemberFeatureEnabled.map {
      case true => {
        if (SpUtils.getUserId(getContext).equals(conversationController.currentConv.currentValue.get.creator.str)) {
          getString(R.string.glyph__minus)
        } else {
          ""
        }
      }
      case _ => ""
    }.onUi(text => footerMenu.foreach(_.setRightActionText(text)))

    subs += user.map(_.getDisplayName).onUi(t => userNameView.foreach(_.setText(t)))
    subs += user.map(user => StringUtils.formatHandle(user.handle.map(_.string).getOrElse("")))
      .onUi(t => userHandleView.foreach(_.setText(t)))

    userHandleView.foreach(_.setVisibility((if (allowShowAddFriend) View.VISIBLE else View.GONE)))

    ttvNoPermissionForHandler.foreach(_.setVisibility(if (allowShowAddFriend) View.GONE else View.VISIBLE))

    user.map(Some(_)).onUi{
      case Some(x) =>
        imageViewProfile.foreach(_.setUserData(x))
    }

    val backgroundContainer = findById[View](R.id.background_container)
    backgroundContainer.setClickable(true)

    if (userRequester == UserRequester.PARTICIPANTS) {
      backgroundContainer.setBackgroundColor(Color.TRANSPARENT)
      footerMenu.foreach(_.setVisibility(if (allowShowAddFriend) View.VISIBLE else View.GONE))
      connectButton.foreach(_.setVisibility(View.GONE))
    } else {
      footerMenu.foreach(_.setVisibility(View.GONE))
      connectButton.foreach(_.setVisibility((if (allowShowAddFriend) View.VISIBLE else View.GONE)))
    }

    footerMenu.foreach(_.setCallback(new FooterMenuCallback {
      override def onLeftActionClicked(): Unit = user.map(_.expiresAt.isDefined).head.foreach {
        case false =>
          doConnectFriend()
        case _ =>
      }

      override def onRightActionClicked(): Unit = removeConvMemberFeatureEnabled.head.foreach {
        case true =>
          conversationController.currentConv.head.foreach { conv =>
            if (conv.isActive)
              inject[IConversationScreenController].showConversationMenu(false, conv.id)
          }
        case _ =>
      }
    }))
  }

  private def doConnectFriend(): Unit ={
    showProgressDialog()
    for {
      conv <- usersController.connectToUser(userToConnectId)
    } yield {
      loadedConversationData ! conv
    }
  }

  override def onBackPressed(): Boolean = {
    false
  }

  override def onStop(): Unit = {
    keyboardController.hideKeyboardIfVisible()
    super.onStop()
  }
}

object SendConnectRequestFragment {
  val Tag: String = classOf[SendConnectRequestFragment].getName
  val ArgumentUserId = "ARGUMENT_USER_ID"
  val ArgumentUserRequester = "ARGUMENT_USER_REQUESTER"
  val ArgumentAllowUserAddFriend = "ARGUMENT_ALLOW_USER_ADD_FRIEND"

  def newInstance(userId: String, userRequester: UserRequester, allowShowAddFriend: Boolean): SendConnectRequestFragment =
    returning(new SendConnectRequestFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgumentUserId, userId)
        args.putString(ArgumentUserRequester, userRequester.toString)
        args.putBoolean(ArgumentAllowUserAddFriend, allowShowAddFriend)
      })
    )

  trait Container extends UserProfileContainer {
    def onConnectRequestWasSentToUser(): Unit

    override def showRemoveConfirmation(userId: UserId): Unit
  }

}
