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

import java.util

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.chad.library.adapter.base.{BaseQuickAdapter, BaseViewHolder}
import com.jsy.common.fragment.ConvReportTypeFragment.ReportTypeBean
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.pages.BaseFragment

class ConvReportTypeFragment extends BaseFragment[ConvReportTypeFragment.Container]
  with FragmentHelper
  with DerivedLogTag {

  import ConvReportTypeFragment._

  private var rootView: View = null
  private var mToolbar: Toolbar = null
  private var recyclerView: RecyclerView = null
  private var typeAdapter: ConvReportTypeAdapter = null

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.fragment_conv_report_type, container, false)
      rootView
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    mToolbar = view.findViewById(R.id.report_type_toolbar)
    recyclerView = view.findViewById(R.id.report_type_recycler)
    mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        onBackPressed
      }
    })
    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity))
    typeAdapter = new ConvReportTypeAdapter(getActivity)
    recyclerView.setAdapter(typeAdapter)
    typeAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener {
      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        val item: ReportTypeBean = adapter.getItem(position).asInstanceOf[ReportTypeBean]
        if (null != item && !TextUtils.isEmpty(item.typeName) && getParentFragment.isInstanceOf[ConversationReportFragment]) {
          getParentFragment.asInstanceOf[ConversationReportFragment].addShowTypeFragment(ConversationReportFragment.SHOW_REPORT_CONTENT, item)
        }
      }
    })
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    val typeList: util.List[ReportTypeBean] = new util.ArrayList[ReportTypeBean]
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_porn), 1))
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_contraband), 2))
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_gaming), 3))
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_rumor), 4))
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_terror), 5))
    typeList.add(ReportTypeBean(getString(R.string.report_type_item_other), 99))
    typeAdapter.setNewData(typeList)
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
  }
}

object ConvReportTypeFragment {
  val Tag: String = getClass.getSimpleName

  def newInstance(): ConvReportTypeFragment = {
    val fragment: ConvReportTypeFragment = new ConvReportTypeFragment
    fragment
  }

  trait Container {
  }

  case class ReportTypeBean(typeName: String,
                            typeId: Int)

}

class ConvReportTypeAdapter(context: Context) extends BaseQuickAdapter[ReportTypeBean, BaseViewHolder](R.layout.adapter_conv_report_type) {

  override def convert(helper: BaseViewHolder, item: ReportTypeBean): Unit = {
    helper.setText(R.id.report_type_text, item.typeName)
  }
}
