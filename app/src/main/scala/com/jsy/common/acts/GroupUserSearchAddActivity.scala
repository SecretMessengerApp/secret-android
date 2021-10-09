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
import android.content.Intent
import android.os.Bundle
import android.text.{InputType, TextUtils}
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, View}
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.google.gson.{JsonArray, JsonObject}
import com.jsy.common.dialog.TransferGroupDialog
import com.jsy.common.fragment.{GroupUserSearchAddFragment, NormalGroupUsersFragment, ThousandsGroupUsersFragment}
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.listener.OnSelectUserDataListener
import com.jsy.common.model.{HttpResponseBaseModel, SearchUserInfo}
import com.jsy.common.utils.ToastUtil
import com.waz.api.IConversation
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{RConvId, UserData, UserId}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{BaseActivity, R}

import java.util

object GroupUserSearchAddActivity {
  val TAG: String = classOf[GroupUserSearchAddActivity].getName
  val EXIST_MEMBERS: String = "exist_members"
  val EXIST_groupUserType: String = "groupUserType"

  def startSelf(activity: Activity, rConvId: RConvId, creator: UserId, convType: ConversationType, groupUserType: Int, exitMembers: Array[String] = Array.empty[String], requestCode: Int = 1): Unit = {
    val intent = new Intent(activity, classOf[GroupUserSearchAddActivity])
    val bundle = new Bundle
    bundle.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    bundle.putSerializable(classOf[UserId].getSimpleName, creator)
    bundle.putSerializable(classOf[ConversationType].getSimpleName, convType)
    bundle.putInt(EXIST_groupUserType, groupUserType)
    bundle.putStringArray(EXIST_MEMBERS, exitMembers)
    intent.putExtras(bundle)
    if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_SPEAKER
      || groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN) {
      activity.startActivityForResult(intent, requestCode)
    } else {
      activity.startActivity(intent)
    }
  }
}

class GroupUserSearchAddActivity extends BaseActivity with OnSelectUserDataListener {

  import GroupUserSearchAddActivity._

  private var mToolbar: Toolbar = _
  private var mToolTitle: TypefaceTextView = _
  private var searchBox: SearchEditText = _
  private lazy val bundle: Bundle = getIntent.getExtras
  private var convType: ConversationType = _
  private var rConvId: RConvId = _
  private var groupUserType: Int = _
  private var exitMembers: Array[String] = _
  private var groupCreateId: UserId = null
  private var transferGroupDialog: TransferGroupDialog = null
  private var new_creator: String = null
  private val PAGE_SIME: String = "15"
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_user_searchadd)
    convType = bundle.getSerializable(classOf[ConversationType].getSimpleName).asInstanceOf[ConversationType]
    rConvId = bundle.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId]
    groupUserType = bundle.getInt(EXIST_groupUserType)
    exitMembers = bundle.getStringArray(GroupUserSearchAddActivity.EXIST_MEMBERS)
    groupCreateId = bundle.getSerializable(classOf[UserId].getSimpleName).asInstanceOf[UserId]
    mToolbar = findViewById[Toolbar](R.id.group_speaker_add_tool)
    mToolTitle = findViewById[TypefaceTextView](R.id.tv_tool_title)
    if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_SPEAKER
      || groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN) {
      mToolTitle.setText(R.string.conversation_setting_group_speaker_add)
    } else {
      mToolTitle.setText(R.string.conversation_detail_select_transfer_group)
    }

    mToolbar.setNavigationOnClickListener(new View.OnClickListener {

      override def onClick(v: View): Unit = {
        titleBarBack();
      }
    })
    searchBox = findViewById[SearchEditText](R.id.searchBoxView)
    searchBoxSetListener
  }

  def searchBoxSetListener(): Unit = {
    searchBox.setInputType(InputType.TYPE_CLASS_TEXT)
    searchBox.applyDarkTheme(true)
    accentColor.onUi(color => searchBox.setCursorColor(color))
    searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
          KeyboardUtils.hideKeyboard(GroupUserSearchAddActivity.this)
          addSearchContainer(rConvId.str, searchBox.getSearchFilter)
          true
        }
        else false
    })

    searchBox.setCallback(new SearchEditText.Callback {
      override def onRemovedTokenSpan(element: PickableElement): Unit = {}

      override def onFocusChange(hasFocus: Boolean): Unit = {}

      override def onClearButton(): Unit = {}

      override def afterTextChanged(s: String): Unit = {}
    })
  }

  def addGroupUserContainer(): Unit = {
    if (convType == IConversation.Type.GROUP) {
      getSupportFragmentManager.beginTransaction
        .add(R.id.group_search_add_container, NormalGroupUsersFragment.newInstance(rConvId), NormalGroupUsersFragment.TAG)
        .commitAllowingStateLoss
    } else if (convType == IConversation.Type.THROUSANDS_GROUP) {
      getSupportFragmentManager.beginTransaction
        .add(R.id.group_search_add_container, ThousandsGroupUsersFragment.newInstance(rConvId, groupUserType), ThousandsGroupUsersFragment.TAG)
        .commitAllowingStateLoss
    }
  }

  def titleBarBack(): Unit = {
    finish()
  }

  def addSearchContainer(conversationRid: String, editStr: String): Unit = {
    if (TextUtils.isEmpty(editStr)) {
      ToastUtil.toastByResId(this, R.string.secret_data_load_empty);
    } else {
      val urlPath = new StringBuilder().append(String.format("conversations/%s/search", conversationRid)).toString
      val params = new util.HashMap[String, String]
      params.put("q", editStr)
      params.put("size", PAGE_SIME)
      showProgressDialog(R.string.loading)
      SpecialServiceAPI.getInstance().get(urlPath, params, new OnHttpListener[SearchUserInfo] {

        override def onFail(code: Int, err: String): Unit = {
          dismissProgressDialog()
          ToastUtil.toastByCode(GroupUserSearchAddActivity.this, code)
        }

        override def onSuc(userInfo: SearchUserInfo, orgJson: String): Unit = {
          dismissProgressDialog()
        }

        override def onSuc(userInfos: util.List[SearchUserInfo], orgJson: String): Unit = {
          dismissProgressDialog()
          if (null == userInfos || userInfos.isEmpty) {
            ToastUtil.toastByResId(GroupUserSearchAddActivity.this, R.string.secret_data_load_empty)
          } else {
            val mOratorData = userInfos.asInstanceOf[java.util.ArrayList[SearchUserInfo]]
            getSupportFragmentManager.beginTransaction
              .replace(R.id.group_search_add_container, GroupUserSearchAddFragment.newInstance(mOratorData), GroupUserSearchAddFragment.TAG)
              //.addToBackStack(GroupUserSearchAddFragment.TAG)
              .commitAllowingStateLoss
          }
        }
      })
    }
  }

  override def onSelectData(userData: SearchUserInfo): Unit = {
    addCurrentMember(userData.getId, userData.getName)
  }

  override def onNormalData(userData: UserData): Unit = {
    addCurrentMember(userData.id.str, userData.name.str)
  }

  override def onThousandsData(id: String, name: String, asset: String): Unit = {
    addCurrentMember(id, name)
  }

  /**
    *
    * @param id
    * @param name
    */
  def addCurrentMember(id: String, name: String): Unit = {
    if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_SPEAKER) {
      setCurrentSpeaker(rConvId.str, id, name)
    } else if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN) {
      setCurrentAdmin(rConvId.str, id, name)
    } else if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_TRANSFER){
      showTransferDialog(id, name)
    }
  }

  def setCurrentSpeaker(conversationRid: String, oratorUserId: String, name: String): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    val dataArray = new JsonArray
    dataArray.add(oratorUserId)

    dataJson.add("orator", dataArray)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationRid,dataJson.toString,new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        ToastUtil.toastByCode(GroupUserSearchAddActivity.this, code)
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        backFinish(oratorUserId, name)
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  def setCurrentAdmin(conversationRid: String, oratorUserId: String, name: String) = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    val dataArray = new JsonArray
    dataArray.add(oratorUserId)

    dataJson.add("man_add", dataArray)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationRid,dataJson.toString,new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        ToastUtil.toastByCode(GroupUserSearchAddActivity.this, code)
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        backFinish(oratorUserId, name)
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }


  private def showTransferDialog(userId: String, userName: String): Unit = {
    if ((groupCreateId != null && userId.equals(groupCreateId.str)) || (null != getControllerFactory && getControllerFactory.isTornDown)) return
    this.new_creator = userId
    if (transferGroupDialog == null) transferGroupDialog = new TransferGroupDialog(this, userName, new TransferGroupDialog.ClickCallback() {
      override def onNotSaveClick(): Unit = {
        dismissTransferGroupDialog()
      }

      override def onSaveClick(): Unit = {
        val job = new JsonObject
        job.addProperty("new_creator", new_creator)
        SpecialServiceAPI.getInstance.updateGroupInfo(rConvId.str, job.toString, new OnHttpListener[HttpResponseBaseModel]() {

          override def onFail(code: Int, err: String): Unit = {
            showToast(getString(R.string.conversation_detail_transfer_group_failed))
            dismissTransferGroupDialog()
          }

          override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
            if (!TextUtils.isEmpty(orgJson)) {
              showToast(getString(R.string.conversation_detail_transfer_group_success))
              dismissTransferGroupDialog()
              backFinish(new_creator, userName)
            }
          }

          override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {
          }
        })
      }
    })
    else transferGroupDialog.formatTitle(userName)
    showTransferGroupDialog()
  }

  private def dismissTransferGroupDialog(): Unit = {
    if (transferGroupDialog != null && transferGroupDialog.isShowing) transferGroupDialog.dismiss()
  }

  private def showTransferGroupDialog(): Unit = {
    if (transferGroupDialog != null) transferGroupDialog.show()
  }


  override def onDestroy(): Unit = {
    super.onDestroy()
    KeyboardUtils.hideKeyboard(GroupUserSearchAddActivity.this)
    dismissTransferGroupDialog()
  }

  def backFinish(oratorUserId: String, name: String): Unit = {
    if (groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_SPEAKER
      || groupUserType == ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_ADMIN) {
      val intent = new Intent()
      val bundle = new Bundle()
      val searchUserInfo = new SearchUserInfo
      searchUserInfo.setId(oratorUserId)
      searchUserInfo.setName(name)
      bundle.putSerializable(classOf[SearchUserInfo].getSimpleName, searchUserInfo);
      intent.putExtras(bundle)
      GroupUserSearchAddActivity.this.setResult(Activity.RESULT_OK, intent)
    }
    GroupUserSearchAddActivity.this.finish
  }
}
