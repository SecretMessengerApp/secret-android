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
package com.jsy.common.fragment

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.waz.api.IConversation
import com.waz.api.User.ConnectionStatus._
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.returning
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.connect.{PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.{BlockedUserProfileFragment, UserProfileContainer}
import com.waz.zclient.participants.{ParticipantsController, UserRequester}
import com.waz.zclient.utils.{RichView, StringUtils}

class GroupUserInfoFragment extends BaseFragment[GroupUserNormalFragment.Container]
  with FragmentHelper {

  implicit def context: Context = getActivity

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]

  private lazy val userHandle = returning(view[TextView](R.id.user_handle)) { vh =>
    val handle = participantsController.otherParticipant.map(_.handle.map(_.string))

    handle
      .map(_.isDefined)
      .onUi(vis => vh.foreach(_.setVisible(vis)))

    handle
      .map {
        case Some(h) => StringUtils.formatHandle(h)
        case _       => ""
      }.onUi(str => vh.foreach(_.setText(str)))
  }

  private lazy val allowShowAddFriend = getArguments.getBoolean("allowShowAddFriend")

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_group_user_info, viewContainer, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {

    userHandle

    val imageView = Option(findById[ChatHeadViewNew](R.id.chathead))

    val addFriendLayout = Option(findById[ViewGroup](R.id.add_friend_layout))
    addFriendLayout.foreach(_.setVisibility(View.GONE))

    val forbiddenSettingLayout = Option(findById[ViewGroup](R.id.forbidden_setting_layout))
    forbiddenSettingLayout.foreach(_.setVisibility(View.GONE))

    val removeUserLayout = Option(findById[ViewGroup](R.id.rl_remove_user))
    removeUserLayout.foreach(_.setVisibility(View.GONE))

    for {
      Some(otherUserId) <- participantsController.otherParticipantId.head
      otherUserInfo <- participantsController.getUser(otherUserId)
      conversationData <- convController.currentConv.head
    } yield {
      if (conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
        participantsController.isGroupRemoveAndForbiddenCurRight().foreach {
          isRight =>
            if (isRight) {
              forbiddenSettingLayout.foreach { viewGroup =>
                viewGroup.setVisibility(View.VISIBLE)
                viewGroup.setOnClickListener(new View.OnClickListener {
                  override def onClick(v: View): Unit = {
                    openUserProfileFragment(ForbiddenOptionsFragment.newInstance(), ForbiddenOptionsFragment.TAG)
                  }
                })
              }

              removeUserLayout.foreach { viewGroup =>
                viewGroup.setVisibility(View.VISIBLE)
                viewGroup.setOnClickListener(new View.OnClickListener {
                  override def onClick(v: View): Unit = {
                    participantsController.showRemoveConfirmation(otherUserId)
                  }
                })
              }
            }
        }
      }

      otherUserInfo match {
        case Some(userInfo) =>
          imageView.foreach{
            view =>
              view.setUserData(userInfo)
          }
        case _ =>
      }

      otherUserInfo match {
        case Some(userInfo) if userInfo.connection == CANCELLED || userInfo.connection == UNCONNECTED =>
          addFriendLayout.foreach { viewGroup =>
            viewGroup.setVisibility(View.VISIBLE)
            viewGroup.setOnClickListener(new View.OnClickListener {
              override def onClick(v: View): Unit = {
                openUserProfileFragment(SendConnectRequestFragment.newInstance(userInfo.id.str, UserRequester.PARTICIPANTS, allowShowAddFriend), SendConnectRequestFragment.Tag)
              }
            })
          }
        case Some(userInfo) if userInfo.connection == PENDING_FROM_OTHER || userInfo.connection == PENDING_FROM_USER || userInfo.connection == IGNORED =>
          addFriendLayout.foreach { viewGroup =>
            viewGroup.setVisibility(View.VISIBLE)
            viewGroup.setOnClickListener(new View.OnClickListener {
              override def onClick(v: View): Unit = {
                openUserProfileFragment(PendingConnectRequestFragment.newInstance(userInfo.id, UserRequester.PARTICIPANTS), PendingConnectRequestFragment.Tag)
              }
            })
          }
        case Some(userInfo) if userInfo.connection == BLOCKED =>
          addFriendLayout.foreach { viewGroup =>
            viewGroup.setVisibility(View.VISIBLE)
            viewGroup.setOnClickListener(new View.OnClickListener {
              override def onClick(v: View): Unit = {
                openUserProfileFragment(BlockedUserProfileFragment.newInstance(userInfo.id.str, UserRequester.PARTICIPANTS), BlockedUserProfileFragment.Tag)
              }
            })
          }
      }
    }
  }

  override def onBackPressed(): Boolean = {
    getFragmentManager.popBackStack()
    true
  }

  def openUserProfileFragment(fragment: Fragment, tag: String): Unit = {
    getFragmentManager.beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.fl__participant__container, fragment, tag)
      .addToBackStack(tag)
      .commit
  }
}

object GroupUserInfoFragment {

  val TAG: String = classOf[GroupUserInfoFragment].getName

  def newInstance(allowShowAddFriend: Boolean): GroupUserInfoFragment = {
    val frag = new GroupUserInfoFragment
    val args = new Bundle()
    args.putBoolean("allowShowAddFriend", allowShowAddFriend)
    frag.setArguments(args)
    frag
  }

  trait Container extends UserProfileContainer {
  }

}

