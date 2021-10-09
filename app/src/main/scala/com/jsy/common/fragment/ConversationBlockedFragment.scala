/**
 * Secret
 * Copyright (C) 2021 Secret
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

import android.os.Bundle
import android.view.View.OnTouchListener
import android.view.{LayoutInflater, MotionEvent, View, ViewGroup}
import android.widget.{FrameLayout, ImageView, TextView}
import com.jsy.common.acts.ConversationReportActivity
import com.jsy.common.event.ReportFinishEvent
import com.jsy.common.utils.rxbus2.{RxBus, Subscribe, ThreadMode}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.{FragmentHelper, R}

class ConversationBlockedFragment extends BaseFragment[ConversationBlockedFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with DerivedLogTag {

  import ConversationBlockedFragment._

  private lazy val convController = inject[ConversationController]
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private var rootView: View = null
  private var blockedView: FrameLayout = null
  private var backBtn: ImageView = null
  private var titleMsgTxt: TextView = null
  private var msgNumTxt: TextView = null
  private var groupApplyBtn: TextView = null
  private var nomalSeeBtn: TextView = null

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    if (!RxBus.getDefault.isRegistered(this)) RxBus.getDefault.register(this)
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.fragment_conversation_blocked, container, false)
      rootView
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    blockedView = view.findViewById(R.id.conversation_blocked)
    blockedView.setVisibility(View.GONE)
    backBtn = view.findViewById(R.id.back_image)
    titleMsgTxt = view.findViewById(R.id.blocked_title_text)
    msgNumTxt = view.findViewById(R.id.blocked_msg_text)
    msgNumTxt.setVisibility(View.GONE)
    groupApplyBtn = view.findViewById(R.id.apply_unblock_btn)
    nomalSeeBtn = view.findViewById(R.id.blocked_see_btn)
    backBtn.setOnClickListener(this)
    nomalSeeBtn.setOnClickListener(this)
    blockedView.setOnTouchListener(new OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = {
        true
      }
    })
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    (for {
      creatorId <- convController.currentConv.map(_.creator)
      isGroupBlocked <- convController.currentConv.map(_.isGroupBlocked)
      selfId <- currentUser
    } yield {
      creatorId == selfId && isGroupBlocked
    }).onUi {
      isToUnblock =>
        groupApplyBtn.setClickable(isToUnblock)
        //        nomalSeeBtn.setClickable(!isToUnblock)
        if (isToUnblock) {
          groupApplyBtn.setOnClickListener(this)
          groupApplyBtn.setVisibility(View.VISIBLE)
          nomalSeeBtn.setText(R.string.secret_cancel)
          //nomalSeeBtn.setVisibility(View.GONE)
        } else {
          groupApplyBtn.setVisibility(View.GONE)
          nomalSeeBtn.setText(R.string.markdown_link_dialog_confirmation)
          //nomalSeeBtn.setVisibility(View.VISIBLE)
          //nomalSeeBtn.setOnClickListener(this)
        }
        blockedView.setVisibility(View.VISIBLE)
    }
  }

  override def onClick(v: View): Unit = {
    if (v.getId == R.id.back_image || v.getId == R.id.blocked_see_btn) {
      onBackPressed
    } else if (v.getId == R.id.apply_unblock_btn && v.getVisibility == View.VISIBLE) {
      ConversationReportActivity.startSelf(getActivity, ConversationReportActivity.SHOW_TYPE_UNBLOCK)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  def onRedFinish(finishEvent: ReportFinishEvent): Unit = {
    this.onBackPressed()
  }

  override def onBackPressed(): Boolean = {
    getActivity.finish()
    true
  }

  override def onDestroyView(): Unit = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
    }
    super.onDestroyView()
    if (RxBus.getDefault.isRegistered(this)) RxBus.getDefault.unregister(this)
  }
}

object ConversationBlockedFragment {
  val Tag: String = getClass.getSimpleName

  def newInstance(): ConversationBlockedFragment = {
    val fragment: ConversationBlockedFragment = new ConversationBlockedFragment
    fragment
  }

  trait Container {
  }

}


