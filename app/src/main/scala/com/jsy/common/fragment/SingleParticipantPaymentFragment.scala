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
package com.jsy.common.fragment

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, RelativeLayout, TextView}
import com.jsy.common.acts.{SendConnectRequestActivity, UserRemarkActivity}
import com.jsy.common.utils.ModuleUtils
import com.waz.api.IConversation
import com.waz.model.{ConversationData, UserData, UserId}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.preferences.views.SwitchPreference
import com.waz.zclient.utils.{MainActivityUtils, SpUtils}
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future


class SingleParticipantPaymentFragment(userId: UserId) extends FragmentHelper with View.OnClickListener {

  import com.waz.threading.Threading.Implicits.Background

  private implicit lazy val ctx = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val users = inject[UsersController]
  private lazy val usersController = inject[UsersController]
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private var userHandle: TextView = _
  private var chatHead: ChatHeadViewNew = _
  private var userRemark: Option[String] = None
  private var blockUser: SwitchPreference = _

  private var mRlRemoveUser: RelativeLayout = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_participants_single_tabbed_payment, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    userHandle = findById[TextView](R.id.user_handle)
    chatHead = findById[ChatHeadViewNew](R.id.chathead)

    usersController.user(userId).onUi {
      case userData: UserData =>
        chatHead.setUserData(userData)
        userHandle.setText(userData.displayName)
        userRemark = userData.remark
    }

    findById[RelativeLayout](R.id.rl_user_chat_remark).setOnClickListener(this)
    findById[RelativeLayout](R.id.rl_user_start_chat).setOnClickListener(this)
    blockUser = findById[SwitchPreference](R.id.preferences_add_blacklist)

    mRlRemoveUser = findById[RelativeLayout](R.id.rl_remove_user)
    mRlRemoveUser.setOnClickListener(this)

    val forbiddenSettingLayout = Option(findById[ViewGroup](R.id.forbidden_setting_layout))
    forbiddenSettingLayout.foreach({ viewGroup =>
      viewGroup.setOnClickListener(this)
      viewGroup.setVisibility(View.GONE)
    })

    convController.currentConv.currentValue.foreach {
      conversationData =>
        if (conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          if (ParticipantsController.isGroupRemoveAndForbiddenMemberRightForThousandsGroup(userId, currentUser.currentValue.getOrElse(UserId(SpUtils.getUserId(getContext, ""))), conversationData)) {
            mRlRemoveUser.setVisibility(View.VISIBLE)
          } else {
            mRlRemoveUser.setVisibility(View.GONE)
          }
        } else {
          mRlRemoveUser.setVisibility(View.GONE)
        }

        if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP
          && ParticipantsController.isGroupRemoveAndForbiddenMemberRightForThousandsGroup(userId, currentUser.currentValue.getOrElse(UserId(SpUtils.getUserId(getContext, ""))), conversationData)) {
          forbiddenSettingLayout.foreach(_.setVisibility(View.VISIBLE))
        }
    }

    blockUser.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (isChecked) {
          blockUser(userId)
        } else {
          unblockUser(userId)
        }
      }
    })

  }

  override def onDestroy(): Unit = {
    participantsController.unselectParticipant()
    super.onDestroy()
  }


  def blockUser(userId: UserId): Future[Option[UserData]] = zms.head.flatMap(_.connection.blockConnection(userId))

  def unblockUser(userId: UserId): Future[ConversationData] = zms.head.flatMap(_.connection.unblockConnection(userId))


  override def onClick(v: View): Unit = {

    if (v.getId == R.id.rl_user_chat_remark) {
      UserRemarkActivity.startSelfForResult(getActivity, userId, userRemark.getOrElse(""), MainActivityUtils.REQUEST_CODE_CHANGE_CONVERSATION_ONE_TO_ONE_REMARK)
    } else if (v.getId == R.id.rl_user_start_chat) {

      val clazz: java.lang.Class[_] = ModuleUtils.classForName(ModuleUtils.CLAZZ_MainActivity)
      if (clazz != null) {
        startActivity(new Intent(getActivity.getApplicationContext, clazz)
          .putExtra(classOf[UserId].getSimpleName, userId)
          .putExtra(MainActivityUtils.INTENT_KEY_FROM_SCAN_PAYMENT, MainActivityUtils.INTENT_KEY_FROM_SCAN_PAYMENT))
      }
    } else if (v.getId == R.id.rl_remove_user) {
      //screenController.hideUser()
      convController.removeMember(userId)
      activity.collect {
        case activity: SendConnectRequestActivity =>
          activity.finish()
        case _ =>
      }

    } else if (v.getId == R.id.forbidden_setting_layout) {
      activity.collect {
        case activity: SendConnectRequestActivity =>
          activity.slideFragmentInFromRight(ForbiddenOptionsFragment.newInstance(), ForbiddenOptionsFragment.TAG)
        case _ =>
      }
    }
  }

  def activity = {
    val activity = inject[Activity]
    if (activity.isInstanceOf[SendConnectRequestActivity]) Some(activity.asInstanceOf[SendConnectRequestActivity]) else Some(activity)
  }


}

object SingleParticipantPaymentFragment {
  val TAG = classOf[SingleParticipantPaymentFragment].getSimpleName
}
