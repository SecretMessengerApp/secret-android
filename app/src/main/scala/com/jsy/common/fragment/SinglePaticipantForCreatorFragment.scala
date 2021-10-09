/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.fragment

import android.app.Activity
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{RelativeLayout, TextView}
import com.jsy.common.acts.SendConnectRequestActivity
import com.jsy.common.moduleProxy.ProxyConversationActivity
import com.waz.api.IConversation
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.UsersController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.{FragmentHelper, R}

class SinglePaticipantForCreatorFragment(userId: UserId, userName: String) extends FragmentHelper with View.OnClickListener {

  private implicit lazy val ctx = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val users = inject[UsersController]
  private lazy val usersController = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]

  private var userHandle: TextView = _
  private var chatHead: ChatHeadViewNew = _
  private var mRlRemoveUser: RelativeLayout = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_participants_single_for_creator, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    userHandle = findById[TextView](R.id.user_handle)
    chatHead = findById[ChatHeadViewNew](R.id.chathead)
    chatHead.loadUser(userId)
    userHandle.setText(userName)

    mRlRemoveUser = findById[RelativeLayout](R.id.rl_remove_user)
    mRlRemoveUser.setOnClickListener(this)

    findById[RelativeLayout](R.id.rl_add_friend).setOnClickListener(this)

    val forbiddenSettingLayout = Option(findById[ViewGroup](R.id.forbidden_setting_layout))
    forbiddenSettingLayout.foreach({ viewGroup =>
      viewGroup.setOnClickListener(this)
      viewGroup.setVisibility(View.GONE)
    })

    convController.currentConv.currentValue.foreach {
      conversationData =>
        if (conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          participantsController.isGroupRemoveAndForbiddenMemberRight(userId).foreach {
            isRight =>
              if (isRight) {
                mRlRemoveUser.setVisibility(View.VISIBLE)
                forbiddenSettingLayout.foreach(_.setVisibility(View.VISIBLE))
              } else {
                mRlRemoveUser.setVisibility(View.GONE)
                forbiddenSettingLayout.foreach(_.setVisibility(View.GONE))
              }
          }
        } else {
          mRlRemoveUser.setVisibility(View.GONE)
          forbiddenSettingLayout.foreach(_.setVisibility(View.GONE))
        }
    }
  }

  override def onDestroy(): Unit = {
    participantsController.unselectParticipant()
    super.onDestroy()
  }


  override def onClick(v: View): Unit = {

    if (v.getId == R.id.rl_remove_user) {
      convController.removeMember(userId)
      activity.collect {
        case activity: SendConnectRequestActivity =>
          activity.finish()
        case _ =>
      }
    } else if (v.getId == R.id.forbidden_setting_layout) {
      getActivity.asInstanceOf[SendConnectRequestActivity].slideFragmentInFromRight(ForbiddenOptionsFragment.newInstance(), ForbiddenOptionsFragment.TAG)
    } else if (v.getId == R.id.rl_add_friend) {
      usersController.connectToUser(userId)
    }

  }

  def activity = {
    val activity = inject[Activity]
    if (activity.isInstanceOf[ProxyConversationActivity]) Some(activity.asInstanceOf[ProxyConversationActivity]) else Some(activity)
  }
}
