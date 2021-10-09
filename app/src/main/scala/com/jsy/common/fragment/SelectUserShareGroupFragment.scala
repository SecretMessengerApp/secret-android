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

import android.graphics.Color
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.SelectUserShareGroupAdapter
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener
import com.waz.model.ConvId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.SharingController.{SharableContent, TextContent}
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}


class SelectUserShareGroupFragment extends BaseFragment[SelectUserShareGroupFragment.Container]
  with SelectUserShareGroupAdapter.Callback
  with FragmentHelper
  with OnBackPressedListener {

  private implicit def context = getContext

  private implicit lazy val uiStorage = inject[UiStorage]

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val multiConversationMsgSendController = inject[SharingController]
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)
  private lazy val keyboard = inject[KeyboardController]

  private lazy val pickUserController = inject[IPickUserController]
  private var tvSelectUserShareGroupFinish: TextView = null

  private var recycleListView: RecyclerView = _
  private lazy val adapter = new SelectUserShareGroupAdapter(this)
  private var url: String = _
  private var tool : Toolbar = _

  private val searchBoxViewCallback = new SearchEditText.Callback {
    override def onRemovedTokenSpan(element: PickableElement): Unit = {}

    override def onFocusChange(hasFocus: Boolean): Unit = {}

    override def onClearButton(): Unit = closeStartUI()

    override def afterTextChanged(s: String): Unit = searchBox.foreach { v =>
      val filter = v.getSearchFilter
      adapter.filter ! filter
    }
  }

  private lazy val searchBox = returning(view[SearchEditText](R.id.searchBoxView)) { vh =>
    accentColor.onUi(color => vh.foreach(_.setCursorColor(color)))
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_select_user_share_group, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    url = getArguments.getString(SelectUserShareGroupFragment.SHARE_URL)
    recycleListView = ViewUtils.getView(view, R.id.searchResultRecyclerView)
    tool = ViewUtils.getView(view,R.id.group_share_tool)
    tvSelectUserShareGroupFinish = ViewUtils.getView(view, R.id.tvConfirm);
    tvSelectUserShareGroupFinish.setTextColor(Color.parseColor("#1A86FE"))
    recycleListView.setLayoutManager(new LinearLayoutManager(getActivity))
    recycleListView.setAdapter(adapter)
    searchBox.foreach {
      view =>
        view.setCallback(searchBoxViewCallback)
        view.applyDarkTheme(true)
    }
    tvSelectUserShareGroupFinish.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (adapter.getPostData().nonEmpty) {
          postMessage(url)
        } else {
          getActivity.finish()
        }
      }
    })

    tool.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        getActivity.finish()
      }
    })
  }


  private def closeStartUI(): Unit = {
    keyboard.hideKeyboardIfVisible()
    adapter.filter ! ""
    /*adapter.tab ! Tab.People*/
//    pickUserController.hidePickUser()
  }


  override def onUserClicked(position: Int): Unit = {
    adapter.updateSendData(position)
    adapter.notifyItemChanged(position)
  }

  private def postMessage(content: String): Unit = {

    if (StringUtils.isBlank(content.toString())) {
      return
    }
    sendMultiMsg(Some(TextContent(content)))
    getActivity.finish()

  }

  def sendMultiMsg(textContent: Option[SharableContent]): Unit = {
    val convIds = adapter.getPostData().take(3).map(userId => ConvId(userId.id.str))
    multiConversationMsgSendController.targetConvs ! convIds
    multiConversationMsgSendController.sharableContent ! textContent
    //multiConversationMsgSendController.ephemeralExpiration ! FiniteDuration.
    multiConversationMsgSendController.sendContent(getActivity)
  }

}


object SelectUserShareGroupFragment {
  val TAG: String = classOf[SelectUserShareGroupFragment].getName
  val SHARE_URL = "share_url";

  def newInstance(shareUrl: String): SelectUserShareGroupFragment = {
    val fragment = new SelectUserShareGroupFragment
    val bundle = new Bundle()
    bundle.putString(SHARE_URL, shareUrl)
    fragment.setArguments(bundle)
    fragment
  }

  trait Container {

  }

}
