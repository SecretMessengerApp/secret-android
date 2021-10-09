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

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.FriendRequestListAdapter
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserId
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.{FragmentHelper, R}

import scala.util.Try

class FriendRequestListFragment extends BaseFragment[FriendRequestListFragment.Container] with FragmentHelper with DerivedLogTag {

  import FriendRequestListFragment._

  private val scrollToOnOpen = Signal(Option.empty[UserId])

  var listAdapter: FriendRequestListAdapter = null
  var recyclerView: RecyclerView = null
  var toolBar: Toolbar = null

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_friend_requestlist, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    toolBar = ViewUtils.getView(view, R.id.toolbar)
    recyclerView = ViewUtils.getView(view, R.id.friend_requestlist_view)
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity, LinearLayoutManager.VERTICAL, false))
    listAdapter = new FriendRequestListAdapter(getActivity)
    recyclerView.setAdapter(listAdapter)

    listAdapter.incomingRequests.map(_.isEmpty).onUi {
      case true => getContainer.dismissInboxFragment()
      case _ => //
    }

    toolBar.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(view: View) = getContainer.dismissInboxFragment()
    })

    (for {
      Some(u) <- scrollToOnOpen
      req <- listAdapter.incomingRequests
    } yield (u, req)).collect { case (u, req) if req.contains(u) => u }.onUi { u =>
      listAdapter.findPosition(u).foreach { p =>
        recyclerView.scrollToPosition(p)
        scrollToOnOpen ! None
      }
    }

    scrollToOnOpen ! Try(getArguments.getString(SelectedUser)).toOption.map(UserId)
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
  }
}

object FriendRequestListFragment {
  val Tag = classOf[FriendRequestListFragment].getName
  val SelectedUser = "ARG_SELECTED_USER"

  trait Container {
    def dismissInboxFragment(): Unit
  }

  def newInstance(userId: UserId) = {
    returning(new FriendRequestListFragment) { f =>
      f.setArguments(returning(new Bundle)(_.putString(SelectedUser, userId.str)))
    }
  }

}
