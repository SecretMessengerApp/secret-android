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

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.GroupUserSearchAddAdapter
import com.jsy.common.listener.OnSelectUserDataListener
import com.jsy.common.model.SearchUserInfo
import com.waz.zclient.R
import com.waz.zclient.pages.BaseFragment
import timber.log.Timber

object GroupUserSearchAddFragment {

  trait Container {}

  var TAG: String = GroupUserSearchAddFragment.getClass.getSimpleName

  def newInstance() = {
    Timber.d("GroupSpeakerAddFragment#newInstance object")
    new GroupUserSearchAddFragment()
  }

  def newInstance(mOratorData: java.util.ArrayList[SearchUserInfo]): Fragment = {
    val bundle = new Bundle()
    bundle.putSerializable("mOratorData", mOratorData)
    val f = new GroupUserSearchAddFragment
    f.setArguments(bundle)
    return f
  }

}

class GroupUserSearchAddFragment extends BaseFragment[GroupUserSearchAddFragment.Container] {

  var mRecyclerView: RecyclerView = _
  var mOratorData: java.util.ArrayList[SearchUserInfo] = _
  var selectUserDataListener: OnSelectUserDataListener = _
  var userAdapter: GroupUserSearchAddAdapter = _

  override def onAttach(context: Context): Unit = {
    super.onAttach(context)
    if (context.isInstanceOf[OnSelectUserDataListener]) {
      selectUserDataListener = context.asInstanceOf[OnSelectUserDataListener]
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val bundle: Bundle = getArguments
    mOratorData = bundle.getSerializable("mOratorData").asInstanceOf[java.util.ArrayList[SearchUserInfo]]
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_group_normalusers, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    mRecyclerView = view.findViewById[RecyclerView](R.id.pgv__participants)
    mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity, LinearLayoutManager.VERTICAL, false))
    userAdapter = new GroupUserSearchAddAdapter(mOratorData)
    userAdapter.setSelectUserDataListener(selectUserDataListener)
//    userAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
//      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
//
//      }
//    })
    mRecyclerView.setAdapter(userAdapter)
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
  }
}
