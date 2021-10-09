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

import android.annotation.TargetApi
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.fragment.app.Fragment
import com.jsy.common.fragment.ConvReportTypeFragment.ReportTypeBean
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.pages.BaseFragment

class ConversationReportFragment extends BaseFragment[ConversationReportFragment.Container]
  with FragmentHelper
  with DerivedLogTag {

  import ConversationReportFragment._

  private var rootView: View = null

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.fragment_conversation_report, container, false)
      rootView
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    addShowTypeFragment(SHOW_REPORT_TYPE)
  }

  def addShowTypeFragment(showType: Int, typeBean: ReportTypeBean = null): Unit = {
    showType match {
      case SHOW_REPORT_TYPE =>
        addFragmentContainer(ConvReportTypeFragment.newInstance(), ConvReportTypeFragment.Tag)
      case SHOW_REPORT_CONTENT =>
        addFragmentContainer(ConvReportContentFragment.newInstance(typeBean), ConvReportContentFragment.Tag)
      case _ =>
    }
  }

  private def addFragmentContainer(fragment: Fragment, tag: String): Unit = {
    getChildFragmentManager.beginTransaction
      .addToBackStack(tag)
      .replace(R.id.report_content, fragment, tag).commitAllowingStateLoss
  }

  @TargetApi(23)
  override def onRequestPermissionsResult(requestCode: Int, permissions: Array[String], grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    val fragments = getChildFragmentManager.getFragments
    val size = if (null == fragments) 0 else fragments.size
    for (i <- 0 until size) {
      val fragment = fragments.get(i)
      if (!isDetachFragment(fragment))
        fragment.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
  }

  private def isDetachFragment(fragment: Fragment): Boolean = {
    null == fragment || !fragment.isAdded() || fragment.isDetached()
  }

  private def getChildBackPressed: Boolean = {
    val fragments = getChildFragmentManager.getFragments
    val size = if (null == fragments) 0 else fragments.size
    for (i <- 0 until size) {
      val fragment = fragments.get(i)
      val tag = if (isDetachFragment(fragment)) "" else fragment.getTag
      if (ConvReportContentFragment.Tag == tag)
        return fragment.asInstanceOf[ConvReportContentFragment].onBackPressed
      else if (ConvReportTypeFragment.Tag == tag)
        return fragment.asInstanceOf[ConvReportTypeFragment].onBackPressed
    }
    false
  }


  override def onBackPressed(): Boolean = {
    val isChildBack: Boolean = getChildBackPressed
    if (!isChildBack) super.onBackPressed() else isChildBack
  }

  override def onDestroyView(): Unit = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
    }
    super.onDestroyView()
  }
}

object ConversationReportFragment {
  val Tag: String = getClass.getSimpleName
  val SHOW_REPORT_TYPE = 1
  val SHOW_REPORT_CONTENT = 2

  def newInstance(): ConversationReportFragment = {
    val fragment: ConversationReportFragment = new ConversationReportFragment
    fragment
  }

  trait Container {
  }

}
