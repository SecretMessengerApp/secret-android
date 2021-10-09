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

import java.util

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.acts.GroupInviteMembersActivity._
import com.jsy.common.adapter.GroupInviteMembersAdapter
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.{ConversationInviteMemberBaseModel, ConversationInviteMemberModel}
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.{BaseActivity, R}

import scala.collection.JavaConverters

class GroupInviteMembersActivity extends BaseActivity {

  override def canUseSwipeBackLayout: Boolean = true

  private var tool: Toolbar = _
  private var mInviteMembersRecycler: RecyclerView = _
  private var rConvId: String = _
  private var totalResult = Seq[InviteMemberResult]()


  override def onCreate(savedInstanceState: Bundle): Unit = {

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_invite_members)

    rConvId = getIntent.getStringExtra(ARG_RCONVID)

    findViews()
    getData(rConvId)
  }


  def findViews(): Unit = {

    tool = ViewUtils.getView(this, R.id.conversation_invite_member_tool)
    mInviteMembersRecycler = ViewUtils.getView(this, R.id.invite_members_recycler)

    tool.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })
  }

  def getData(rConvId: String): Unit = {

    showProgressDialog(R.string.secret_data_loading)

    val urlPath = new StringBuilder().append("conversations/").append(rConvId).append("/memref/self")

    SpecialServiceAPI.getInstance().get(urlPath.toString(),new OnHttpListener[ConversationInviteMemberBaseModel] {
      override def onFail(code: Int, err: String): Unit = {
        dismissProgressDialog()
      }

      override def onSuc(dataModel: ConversationInviteMemberBaseModel, orgJson: String): Unit = {
        dismissProgressDialog()
        if (null != dataModel && dataModel.getCode == 200 && dataModel.getData != null) {
          setData(dataModel.getData)
        }
      }

      override def onSuc(r: util.List[ConversationInviteMemberBaseModel], orgJson: String): Unit = {

      }
    })
  }


  def setData(dataModel: ConversationInviteMemberModel): Unit = {
    if (dataModel != null) {
      totalResult = Seq()
      val parent = dataModel.getParent
      if (parent != null) {
        totalResult = totalResult ++ Seq(InviteMemberResult(TopParent))
        totalResult = totalResult ++ Seq(InviteMemberResult(Member, parent.getHandle, parent.getAsset, parent.getName, parent.getId))
      }

      val children = dataModel.getChildren

      if (children != null && children.size() > 0) {
        totalResult = totalResult ++ Seq(InviteMemberResult(TopChildren))
        JavaConverters.asScalaIteratorConverter(children.iterator).asScala.toSeq.foreach(childrenMember =>
          totalResult = totalResult ++ Seq(InviteMemberResult(Member, childrenMember.getHandle, childrenMember.getAsset, childrenMember.getName, childrenMember.getId)))
      }

      val childrenSize = if (children == null) 0 else children.size()

      mInviteMembersRecycler.setLayoutManager(new LinearLayoutManager(GroupInviteMembersActivity.this, LinearLayoutManager.VERTICAL, false))
      mInviteMembersRecycler.setAdapter(new GroupInviteMembersAdapter(GroupInviteMembersActivity.this, totalResult, childrenSize))
    }
  }
}

object GroupInviteMembersActivity {

  val TopParent = 0
  val TopChildren = 1
  val Member = 2

  val ARG_RCONVID = "rConvId"

  def startGroupInviteMembersActivitySelf(context: Context, rConvId: String): Unit = {
    val intent = new Intent(context, classOf[GroupInviteMembersActivity])
    intent.putExtra(ARG_RCONVID, rConvId)
    context.startActivity(intent)
  }

  case class InviteMemberResult(itemType: Int, handle: String, asset: String, name: String, id: String)

  object InviteMemberResult {
    def apply(itemType: Int): InviteMemberResult = new InviteMemberResult(itemType, "", "", "", "")
  }


}
