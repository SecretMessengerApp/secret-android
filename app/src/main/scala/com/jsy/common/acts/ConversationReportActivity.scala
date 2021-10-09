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
package com.jsy.common.acts

import android.annotation.TargetApi
import android.content.{Context, Intent}
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.jsy.common.fragment.{ConvApplyUnblockFragment, ConversationReportFragment}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{BaseActivity, R}

class ConversationReportActivity extends BaseActivity with DerivedLogTag {

  import ConversationReportActivity._

  override def canUseSwipeBackLayout = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_conversation_report)
    val intent = getIntent
    addShowTypeFragment(intent)
  }

  def addShowTypeFragment(intent: Intent): Unit = {
    val showType = intent.getIntExtra(SHOW_TYPE, SHOW_TYPE_REPORT)
    showType match {
      case SHOW_TYPE_REPORT =>
        addFragmentContainer(ConversationReportFragment.newInstance(), ConversationReportFragment.Tag)
      case SHOW_TYPE_UNBLOCK =>
        addFragmentContainer(ConvApplyUnblockFragment.newInstance(), ConvApplyUnblockFragment.Tag)
      case _ =>
    }
  }

  private def addFragmentContainer(fragment: Fragment, tag: String): Unit = {
    getSupportFragmentManager.beginTransaction.replace(R.id.report_container, fragment, tag).commitAllowingStateLoss
  }

  @TargetApi(23)
  override def onRequestPermissionsResult(requestCode: Int, permissions: Array[String], grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    val fragments = getSupportFragmentManager.getFragments
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
    val fragments = getSupportFragmentManager.getFragments
    val size = if (null == fragments) 0 else fragments.size
    for (i <- 0 until size) {
      val fragment = fragments.get(i)
      val tag = if (isDetachFragment(fragment)) "" else fragment.getTag
      if (ConvApplyUnblockFragment.Tag == tag)
        return fragment.asInstanceOf[ConvApplyUnblockFragment].onBackPressed
      else if (ConversationReportFragment.Tag == tag)
        return fragment.asInstanceOf[ConversationReportFragment].onBackPressed
    }
    false
  }

  override def onBackPressed(): Unit = {
    val isChildBack: Boolean = getChildBackPressed
    if (!isChildBack) super.onBackPressed()
  }
}

object ConversationReportActivity {
  val SHOW_TYPE = "showType"
  val SHOW_TYPE_REPORT = 1
  val SHOW_TYPE_UNBLOCK = 2

  def startSelf(context: Context, showType: Int): Unit = {
    val intent = new Intent(context, classOf[ConversationReportActivity])
    intent.putExtra(SHOW_TYPE, showType)
    context.startActivity(intent)
  }
}
