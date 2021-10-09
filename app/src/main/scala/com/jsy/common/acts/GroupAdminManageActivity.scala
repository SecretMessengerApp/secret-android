/**
 * Secret
 * Copyright (C) 2019 Secret
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

import android.app.Activity
import android.content.{Context, DialogInterface, Intent}
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.google.gson.{JsonArray, JsonObject}
import com.jsy.common.adapter.{GroupOperateMemberAdapter, OnDeleteUserListener}
import com.jsy.common.fragment.ThousandsGroupUsersFragment
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.{HttpResponseBaseModel, SearchUserInfo}
import com.jsy.common.utils.ToastUtil
import com.jsy.common.{OnLoadUsersListener, ConversationApi}
import com.jsy.res.utils.ViewUtils.showAlertDialog
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{BaseActivity, R}

import java.util
import scala.collection.JavaConversions._

object GroupAdminManageActivity {
  val TAG = classOf[GroupAdminManageActivity].getName
  val ADMINMAXNUM: Int = 10

  def startSelf(context: Context, rConvId: RConvId, convType: ConversationType) = {
    val intent = new Intent(context, classOf[GroupAdminManageActivity])
    val bundle = new Bundle();
    bundle.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    bundle.putInt("ConversationType", convType.id)
    intent.putExtras(bundle)
    context.startActivity(intent);
  }
}

class GroupAdminManageActivity extends BaseActivity with OnDeleteUserListener {

  private var mBackBtn: ImageView = _
  private var mAddText: TypefaceTextView = _
  private var mRecyclerView: RecyclerView = _

  private var rConvId: RConvId = _
  private var conversationType: ConversationType = _

  private var mAdminData: java.util.List[UserData] = new util.ArrayList[UserData]()
  private lazy val conversationController = inject[ConversationController]
  private lazy val mAdminAdapter: GroupOperateMemberAdapter = new GroupOperateMemberAdapter(this, this, mAdminData, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_admin_manage)
    if (savedInstanceState == null) {
      val bundle: Bundle = getIntent.getExtras
      rConvId = bundle.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId];
      conversationType = bundle.getSerializable(classOf[ConversationType].getSimpleName).asInstanceOf[ConversationType];
    } else {
      rConvId = savedInstanceState.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId];
      conversationType = savedInstanceState.getSerializable(classOf[ConversationType].getSimpleName).asInstanceOf[ConversationType];
    }

    mBackBtn = findViewById[ImageView](R.id.iv_tool_back)
    mAddText = findViewById[TypefaceTextView](R.id.tv_tool_right)
    mRecyclerView = findViewById[RecyclerView](R.id.admin_manage_recyclerview)

    mBackBtn.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })

    mAddText.setOnClickListener(new View.OnClickListener {

      override def onClick(v: View): Unit = {
        if (mAdminAdapter.getItemCount < GroupAdminManageActivity.ADMINMAXNUM) {
          GroupUserSearchAddActivity.startSelf(GroupAdminManageActivity.this, rConvId, null, conversationType, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN, getExistAdmin(), 1)
        } else {

        }
      }
    })

    mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
    mRecyclerView.setAdapter(mAdminAdapter)

    getCurGroupAdmins();
  }


  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    outState.putSerializable(classOf[ConversationType].getSimpleName, conversationType)
  }

  def getCurGroupAdmins(): Unit = {
    conversationController.currentConv.currentValue.foreach {
      conversationData =>
        val manager = conversationData.manager
        ConversationApi.loadUsers(manager.toSet, new OnLoadUsersListener {
          override def onSuc(userData: IndexedSeq[UserData]): Unit = {
            mAdminData.clear()
            mAdminData.addAll(scala.collection.JavaConversions.seqAsJavaList(userData))
            mAdminAdapter.notifyDataSetChanged()
          }

          override def onFail(): Unit = {
            mAdminData.clear()
            mAdminAdapter.notifyDataSetChanged()
          }
        })
    }

    //    conversationController.currentConvGroupManager.currentValue.foreach {
    //      manager =>
    //        manager.foreach {
    //          user =>
    //            val userInfo: SearchUserInfo = new SearchUserInfo
    //            user.id.foreach(userInfo.setId(_));
    //            user.name.foreach(userInfo.setName(_));
    //            user.handle.foreach(userInfo.setHandle(_));
    //            user.asset.foreach(userInfo.setAsset(_));
    //            mAdminData.add(userInfo);
    //        }
    //        mAdminAdapter.notifyDataSetChanged()
    //    }

  }

  def getExistAdmin(): Array[String] = {
    val length: Int = mAdminAdapter.getItemCount;
    val admins: Array[String] = new Array[String](length);
    var i = 0;
    for (i <- 0 until length) {
      admins(i) = mAdminAdapter.getData(i).id.str;
    }
    admins
  }

  protected override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == 1 && null != data) {
        val bundle: Bundle = data.getExtras
        val addUser: SearchUserInfo = bundle.getSerializable(classOf[SearchUserInfo].getSimpleName).asInstanceOf[SearchUserInfo]
        val isExist = mAdminData.exists(_.id.str.equalsIgnoreCase(addUser.getId))
        if (!isExist) {
          val userData: UserData = new UserData(id = UserId(addUser.getId),
            name = Name(addUser.getName),
            searchKey = SearchKey(addUser.getName),
            handle = Option(Handle(addUser.getHandle)),
            //         rAssetId = Option(addUser.getAsset)
            picture = Option(AssetId(addUser.getAsset))
          )
          mAdminData.add(userData)
          mAdminAdapter.notifyDataSetChanged()
        }
      }
    }
  }

  override def onItemClick(userId: UserId, pos: Int): Unit = {
    SendConnectRequestActivity.startSelf(userId.str, this, true, null, true)
  }

  override def onDelCurrentMember(userId: UserId, position: Int): Unit = {
    showAlertDialog(GroupAdminManageActivity.this, null,
      getString(R.string.conversation_detail_delete_member),
      getString(R.string.conversation__action__delete),
      getString(R.string.secret_cancel),
      new DialogInterface.OnClickListener() {

        def onClick(dialog: DialogInterface, which: Int) = {
          delCurrentAdmin(rConvId.str, userId.str, position)
        }
      }, null)
  }

  def delCurrentAdmin(conversationRid: String, delId: String, position: Int): Unit = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    val dataArray = new JsonArray

    dataArray.add(delId)
    dataJson.add("man_del", dataArray)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationRid, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        ToastUtil.toastByCode(GroupAdminManageActivity.this, code)
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if (position < mAdminData.size()) {
          mAdminData.remove(position)
          mAdminAdapter.notifyItemRemoved(position)
        } else {
          mAdminAdapter.notifyDataSetChanged()
        }
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }
}

