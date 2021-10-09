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
import com.jsy.common.{OnLoadUserListener, ConversationApi}
import com.jsy.res.utils.ViewUtils.showAlertDialog
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.SearchKey
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{BaseActivity, R}

import java.util

object GroupSpeakerManageActivity {

  val TAG = classOf[GroupSpeakerManageActivity].getName

  val SPEAKERMAXNUM: Int = 1

  def startSelf(context: Context, rConvId: RConvId, convType: ConversationType) = {
    val intent = new Intent(context, classOf[GroupSpeakerManageActivity])
    val bundle = new Bundle()
    bundle.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    bundle.putSerializable(classOf[ConversationType].getSimpleName, convType)
    intent.putExtras(bundle)
    context.startActivity(intent)
  }
}

class GroupSpeakerManageActivity extends BaseActivity with OnDeleteUserListener {

  private var mBackBtn: ImageView = _
  private var mAddText: TypefaceTextView = _
  private var mRecyclerView: RecyclerView = _
  private var mOratorData: java.util.List[UserData] = new util.ArrayList[UserData]()
  private lazy val conversationController = inject[ConversationController]


  private lazy val mSpeakerAdapter: GroupOperateMemberAdapter = new GroupOperateMemberAdapter(this, this, mOratorData, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN)
  private var rConvId: RConvId = _
  private var conversationType: ConversationType = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_speaker_manage)
    if (savedInstanceState == null) {
      rConvId = getIntent.getExtras.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId];
      conversationType = getIntent.getExtras.getSerializable(classOf[ConversationType].getSimpleName).asInstanceOf[ConversationType]
    } else {
      rConvId = savedInstanceState.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId];
      conversationType = savedInstanceState.getSerializable(classOf[ConversationType].getSimpleName).asInstanceOf[ConversationType]
    }

    mBackBtn = findViewById[ImageView](R.id.iv_tool_back)
    mAddText = findViewById[TypefaceTextView](R.id.tv_tool_right)
    mRecyclerView = findViewById[RecyclerView](R.id.speaker_manage_recyclerview)

    mBackBtn.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })

    mAddText.setOnClickListener(new View.OnClickListener {

      override def onClick(v: View): Unit = {
        if (mSpeakerAdapter.getItemCount < GroupSpeakerManageActivity.SPEAKERMAXNUM) {
          GroupUserSearchAddActivity.startSelf(GroupSpeakerManageActivity.this, rConvId, null, conversationType, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_SPEAKER)
        } else {

        }
      }
    })

    mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
    mRecyclerView.setAdapter(mSpeakerAdapter)

    setSpeakerData
  }


  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)

    outState.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    outState.putSerializable(classOf[ConversationType].getSimpleName, conversationType)
  }

  def setSpeakerData() = {
    conversationController.currentConv.currentValue.foreach {
      conversationData =>
        val orator = conversationData.orator
        if (orator.nonEmpty) {
          ConversationApi.loadUser(orator.head, new OnLoadUserListener {
            override def onSuc(userData: UserData): Unit = {
              mOratorData.clear()
              mOratorData.add(userData)
              mSpeakerAdapter.notifyDataSetChanged()
            }

            override def onFail(): Unit = {
              mOratorData.clear()
              mSpeakerAdapter.notifyDataSetChanged()
            }
          })
        }
    }
  }

  override def onItemClick(userId: UserId, pos: Int): Unit = {
    SendConnectRequestActivity.startSelf(userId.str, this, true, null, true)
  }

  override def onDelCurrentMember(userId: UserId, position: Int): Unit = {
    showAlertDialog(GroupSpeakerManageActivity.this, null,
      getString(R.string.conversation_detail_delete_member),
      getString(R.string.conversation__action__delete),
      getString(R.string.secret_cancel),
      new DialogInterface.OnClickListener() {

        def onClick(dialog: DialogInterface, which: Int) = {
          delCurrentSpeaker(rConvId.str, userId.str)
        }
      }, null)
  }

  def delCurrentSpeaker(conversationRid: String, memberId: String): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    val dataArray = new JsonArray
    dataJson.add("orator", dataArray)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationRid, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        ToastUtil.toastByCode(GroupSpeakerManageActivity.this, code)
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        mOratorData.clear()
        mSpeakerAdapter.notifyDataSetChanged()
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  def getExistSpeaker(): Array[String] = {
    new Array[String](0);
  }

  protected override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK) {
      if (requestCode == 1 && null != data) {
        val bundle: Bundle = data.getExtras
        val addUser: SearchUserInfo = bundle.getSerializable(classOf[SearchUserInfo].getSimpleName).asInstanceOf[SearchUserInfo]
        val userData: UserData = new UserData(id = UserId(addUser.getId),
          name = Name(addUser.getName),
          searchKey = SearchKey(addUser.getName),
          handle = Option(Handle(addUser.getHandle)),
          //rAssetId = Option(addUser.getAsset)
          picture = Option(AssetId(addUser.getAsset))
        )
        mOratorData.clear()
        mOratorData.add(userData)
        mSpeakerAdapter.notifyDataSetChanged()
      }
    }
  }

}

