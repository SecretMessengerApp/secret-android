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

import android.content.{Context, Intent}
import android.os.{Bundle, Handler, Message}
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.Toolbar
import com.google.gson.{Gson, JsonObject}
import com.jsy.common.fragment.ThousandsGroupUsersFragment
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.{GroupChangeViewMemEntity, GroupSettingEntity, HttpResponseBaseModel, UpdateGroupSettingResponseModel}
import com.jsy.common.utils.ToastUtil
import com.jsy.common.utils.rxbus2.RxBus
import com.jsy.res.utils.ViewUtils
import com.waz.api.IConversation
import com.waz.model.ConversationData
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils._
import com.waz.zclient.{BaseActivity, R, ZApplication}

import java.util

class GroupSettingActivity extends BaseActivity with View.OnClickListener {

  private lazy val zMessaging = inject[Signal[ZMessaging]]
  private lazy val conversationController = inject[ConversationController]

  private var tool: Toolbar = _
  private var mSwOpenLinkJoin: SwitchPreference = _
  private var mSwViewMem: SwitchPreference = _
  private var mSwMemJoinConfirm: SwitchPreference = _

  private var mInviteVisibleSwitchPreference: SwitchPreference = _
  private var mAddFriendSwitchPreference: SwitchPreference = _
  private var mMsgOnlyManagerSwitchPreference: SwitchPreference = _

  private var groupAdminManage: TextButton = _
  private var groupSpeakerManage: TextButton = _
  private var confirmGroupInvite: SwitchPreference = _
  private var swOnlyCreatorInvite: SwitchPreference = _
  private var groupSelectCreator: TextButton = _
  private var forbiddenSettingLayout: SwitchPreference = _
  private var showMemsumLayout: SwitchPreference = _
  private var editMsgLayout: SwitchPreference = _

  private val mainHandler = new Handler(new Handler.Callback {
    override def handleMessage(msg: Message): Boolean = {
      msg.what match {
        case GroupSettingActivity.CLOSE_ALL =>
          closeAllJoinView()
        case GroupSettingActivity.SET_URL =>
          setConversationJoinUrl(msg.obj.asInstanceOf[String])
        case GroupSettingActivity.CHANGE_SW =>
          mSwOpenLinkJoin.setChecked(msg.obj.asInstanceOf[Boolean])
        case GroupSettingActivity.SET_SW_INVITE =>
          confirmGroupInvite.setChecked(msg.obj.asInstanceOf[Boolean])
        case GroupSettingActivity.SET_SW_PERMISSION =>
          swOnlyCreatorInvite.setChecked(msg.obj.asInstanceOf[Boolean])
        case GroupSettingActivity.SET_SW_VIEW_MEM =>
          mSwViewMem.setChecked(msg.obj.asInstanceOf[Boolean])
        case GroupSettingActivity.SET_SW_MEM_JOIN_CONFIRM =>
          mSwMemJoinConfirm.setChecked(msg.obj.asInstanceOf[Boolean])
        case GroupSettingActivity.SET_SW_INVITE_VISIBLE =>
          if (mInviteVisibleSwitchPreference != null) {
            mInviteVisibleSwitchPreference.setChecked(msg.obj.asInstanceOf[Boolean])
          }
        case GroupSettingActivity.SET_ALL_FORBIDDEN =>
          val isChecked = msg.obj.asInstanceOf[Boolean]
          forbiddenSettingLayout.switch.setChecked(isChecked)
          showHideSpeaker(isChecked)
//          if (forbiddenSwitch != null) {
//            forbiddenSwitch.setOnCheckedChangeListener(null)
//            forbiddenSwitch.setChecked(String.valueOf(msg.obj).toBoolean)
//            showHideSpeaker(forbiddenSwitch.isChecked)
//            forbiddenSwitch.setOnCheckedChangeListener(onCheckedChangeListener)
//          }
        case GroupSettingActivity.SET_ADD_FRIEND =>
          if (mAddFriendSwitchPreference != null) {
            mAddFriendSwitchPreference.setChecked(msg.obj.asInstanceOf[Boolean])
          }
        case _ =>
      }
      false
    }
  })


  private var conversationData: ConversationData = _

  override def canUseSwipeBackLayout: Boolean = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_chat_setting)
    conversationController.currentConv.currentValue.foreach {
      data =>
        conversationData = data;
        initViews()
    }

    //    conversationData = conversationController.currentConv.currentValue.head
    //    initViews()

  }

  def initViews(): Unit = {
    tool = ViewUtils.getView(this, R.id.group_chat_setting_tool)
    tool.setNavigationOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })

    mSwOpenLinkJoin = ViewUtils.getView(this, R.id.sw_link_join_group)
    mSwOpenLinkJoin.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(compoundButton: CompoundButton, b: Boolean): Unit = {
        if (compoundButton.isPressed) changeConversationLinkStatus(b)
      }
    })

    mSwViewMem = ViewUtils.getView(this, R.id.sw_view_mem)
    mSwViewMem.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) changeConversationViewmem(isChecked)
      }
    })

    mSwMemJoinConfirm = ViewUtils.getView(this, R.id.sw_member_join_confirm)
    mSwMemJoinConfirm.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) changeConversationMemJoinConfirm(isChecked)
      }
    })

    swOnlyCreatorInvite = ViewUtils.getView(this, R.id.sw_only_creator_invite)
    swOnlyCreatorInvite.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(compoundButton: CompoundButton, b: Boolean): Unit = {
        if (compoundButton.isPressed) changeConversationInvitePermission(b)
      }
    })

    groupSelectCreator = ViewUtils.getView(this, R.id.group_select_creator)
    groupSelectCreator.onClickEvent { v =>
      GroupUserSearchAddActivity.startSelf(this, conversationData.remoteId, conversationData.creator, conversationData.convType, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_TRANSFER)

    }

    groupSpeakerManage = ViewUtils.getView(this, R.id.group_speaker_manage)
    groupSpeakerManage.onClickEvent { v =>
      GroupSpeakerManageActivity.startSelf(this, conversationData.remoteId, conversationData.convType)
    }

    groupAdminManage = ViewUtils.getView(this, R.id.group_admin_manage)
    groupAdminManage.onClickEvent { v =>
      GroupAdminManageActivity.startSelf(this, conversationData.remoteId, conversationData.convType)
    }

    confirmGroupInvite = ViewUtils.getView(this, R.id.confirm_group_invite)
    confirmGroupInvite.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(compoundButton: CompoundButton, b: Boolean): Unit = {
        if (compoundButton.isPressed) changeConfirmInviteStatus(b)
      }
    })

    isShowAdminView()
    closeAllJoinView()
    getConversationLinkStatus()
    getConversationInviteStatus()
    getConversationInvitePermission()
    getConversationViewMemStatus()
    getConversationMemJoinCofirmStatus()

    mInviteVisibleSwitchPreference = findViewById(R.id.invite_visible_switchPreference)
    if (mInviteVisibleSwitchPreference != null) {
      val flag = if (conversationData != null) {
        conversationData.view_chg_mem_notify
      } else {
        false
      }
      mInviteVisibleSwitchPreference.setChecked(!flag)
      mInviteVisibleSwitchPreference.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          if (buttonView.isPressed) switchVisibleInviteNotify(!isChecked)
        }
      })
    }

    forbiddenSettingLayout = findById(R.id.forbidden_setting_layout)
    if (forbiddenSettingLayout != null) {
      forbiddenSettingLayout.setVisibility(if ((conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP)) View.VISIBLE else View.GONE)
      val flag = conversationData.block_time match {
        case Some(time) if "-1".equals(time) => true
        case _ => false
      }
      forbiddenSettingLayout.switch.setChecked(flag)
      showHideSpeaker(forbiddenSettingLayout.isChecked)

      forbiddenSettingLayout.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          switchBlockTime(isChecked)
          showHideSpeaker(isChecked)
        }
      })
//      forbiddenSettingLayout.setOnClickListener(new View.OnClickListener {
//        override def onClick(v: View): Unit = {
//          forbiddenSettingLayout.switch.setChecked(!forbiddenSettingLayout.switch.isChecked)
//        }
//      })
    }

    mAddFriendSwitchPreference = findById(R.id.add_friend_switchPreference)
    if (mAddFriendSwitchPreference != null) {
      val flag = conversationData.add_friend
      mAddFriendSwitchPreference.setChecked(!flag)
      mAddFriendSwitchPreference.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          if (buttonView.isPressed) switchAddFriend(!isChecked)
        }
      })
    }

    mMsgOnlyManagerSwitchPreference = ViewUtils.getView(this, R.id.msg_only_to_manager_switchPreference)
    val msgOnlyFlag = if (conversationData != null) {
      conversationData.msg_only_to_manager
    } else {
      false
    }
    mMsgOnlyManagerSwitchPreference.setChecked(msgOnlyFlag)
    mMsgOnlyManagerSwitchPreference.setChangeListener(new CompoundButton.OnCheckedChangeListener() {

      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) changeConversationMsgOnlyManager(isChecked)
      }
    })

    showMemsumLayout = ViewUtils.getView(this, R.id.show_memsum_switchPreference)
    val showMemsum = if (conversationData != null) {
      conversationData.isGroupShowNum
    } else {
      false
    }
    showMemsumLayout.setChecked(showMemsum)
    showMemsumLayout.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) changeConversationShowMemsum(isChecked)
      }
    })

    editMsgLayout = ViewUtils.getView(this, R.id.edit_msg_switchPreference)
    val isEditMsg = if (conversationData != null) {
      conversationData.isGroupMsgEdit
    } else {
      false
    }
    editMsgLayout.setChecked(isEditMsg)
    editMsgLayout.setChangeListener(new CompoundButton.OnCheckedChangeListener() {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) changeConversationEditMsg(isChecked)
      }
    })
  }

  def isShowAdminView(): Unit = {
    val visible = conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(ZApplication.getInstance))
    groupAdminManage.setVisibility(if (visible) View.VISIBLE else View.GONE)
  }

  private def showHideSpeaker(checked: Boolean): Unit = {
    val visible = checked
    groupSpeakerManage.setVisibility(if (visible) View.VISIBLE else View.GONE)
  }

  private def switchBlockTime(checked: Boolean): Unit = {
    val endTime = if (checked) {
      Some("-1")
    } else {
      Some("0")
    }
    for {
      zms <- zMessaging.head
      response <- zms.convsUi.changeBlockTime(conversationData.id, endTime)
    } yield response match {
      case Right(_) =>
      case Left(_) =>
        showToast(getString(R.string.generic_error_message))
        mainHandler.obtainMessage(GroupSettingActivity.SET_ALL_FORBIDDEN, !checked).sendToTarget()
    }
  }

  private def switchVisibleInviteNotify(checked: Boolean): Unit = {
    for {
      zms <- zMessaging.head
      response <- zms.convsUi.switchViewChgMemNotify(conversationData.id, checked)
    } yield response match {
      case Right(_) =>
      case Left(_) =>
        showToast(getString(R.string.generic_error_message))
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_INVITE_VISIBLE, checked).sendToTarget()
    }
  }

  private def switchAddFriend(checked: Boolean): Unit = {
    for {
      zms <- zMessaging.head
      response <- zms.convsUi.switchAddFriend(conversationData.id, checked)
    } yield response match {
      case Right(_) =>
      case Left(_) =>
        showToast(getString(R.string.generic_error_message))
        mainHandler.obtainMessage(GroupSettingActivity.SET_ADD_FRIEND, checked).sendToTarget()
    }
  }

  def changeConversationMsgOnlyManager(checked: Boolean) = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("msg_only_to_manager", checked)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {

      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {

      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  def changeConversationInvitorList(checked: Boolean) = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("show_invitor_list", checked)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {

      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {

      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  private def closeAllJoinView(): Unit = {
    if (conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(this))) {
      mSwOpenLinkJoin.setVisibility(View.VISIBLE)
      mSwOpenLinkJoin.setChecked(false)
    }
    else {
      mSwOpenLinkJoin.setVisibility(View.GONE)
    }
  }

  private def setConversationJoinUrl(url: String): Unit = {
    if (conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(getApplicationContext))) {
      mSwOpenLinkJoin.setVisibility(View.VISIBLE)
      mSwOpenLinkJoin.setChecked(true)
    } else {
      mSwOpenLinkJoin.setVisibility(View.GONE)
    }
  }

  private def changeConversationViewmem(isOpen: Boolean): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("viewmem", isOpen)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_VIEW_MEM, !isOpen).sendToTarget
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        RxBus.getDefault.post(new GroupChangeViewMemEntity(isOpen))
        val model = new Gson().fromJson(orgJson, classOf[UpdateGroupSettingResponseModel])
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_VIEW_MEM, model.getData.isViewmem).sendToTarget
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  private def changeConversationMemJoinConfirm(isOpen: Boolean): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("memberjoin_confirm", isOpen)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_MEM_JOIN_CONFIRM, !isOpen).sendToTarget()
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if (isOpen) {
          if (confirmGroupInvite.isChecked) changeConfirmInviteStatus(false)
        }
        val model = new Gson().fromJson(orgJson, classOf[UpdateGroupSettingResponseModel])
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_MEM_JOIN_CONFIRM, model.getData.isMemberjoin_confirm).sendToTarget()
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  private def changeConversationLinkStatus(checked: Boolean): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("url_invite", checked)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        mainHandler.obtainMessage(GroupSettingActivity.CHANGE_SW, !checked).sendToTarget()
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if (checked) {
          if (swOnlyCreatorInvite.isChecked()) changeConversationInvitePermission(false)
        }
        RxBus.getDefault.post(new GroupSettingEntity(checked))
        mainHandler.obtainMessage(GroupSettingActivity.CHANGE_SW, checked).sendToTarget()
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })

  }

  private def changeConfirmInviteStatus(isOpen: Boolean): Unit = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("confirm", isOpen)

    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_INVITE, !isOpen).sendToTarget()
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if (isOpen) {
          if (mSwMemJoinConfirm.isChecked()) changeConversationMemJoinConfirm(false)
        }
        val model = new Gson().fromJson(orgJson, classOf[UpdateGroupSettingResponseModel])
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_INVITE, model.getData.isConfirm).sendToTarget()
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })

  }

  private def changeConversationInvitePermission(checked: Boolean): Unit = {
    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("addright", checked)
    SpecialServiceAPI.getInstance().updateGroupInfo(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_PERMISSION, !checked).sendToTarget()
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if (checked) {
          if (mSwOpenLinkJoin.isChecked()) {
            changeConversationLinkStatus(false)
          }
        }
        val resultModle = new Gson().fromJson(orgJson, classOf[UpdateGroupSettingResponseModel])
        mainHandler.obtainMessage(GroupSettingActivity.SET_SW_PERMISSION, resultModle.getData.isAddright).sendToTarget()
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })

  }

  private def getConversationLinkStatus(): Unit = {
    if (conversationData.url_invite) {
      mainHandler.obtainMessage(GroupSettingActivity.SET_URL, "").sendToTarget()
    } else {
      mainHandler.obtainMessage(GroupSettingActivity.CLOSE_ALL).sendToTarget()
    }
  }

  private def getConversationInviteStatus(): Unit = {
    mainHandler.obtainMessage(GroupSettingActivity.SET_SW_INVITE, conversationData.confirm).sendToTarget()
  }

  private def getConversationInvitePermission(): Unit = {
    mainHandler.obtainMessage(GroupSettingActivity.SET_SW_PERMISSION, conversationData.addright).sendToTarget()
  }

  private def getConversationViewMemStatus(): Unit = {
    mainHandler.obtainMessage(GroupSettingActivity.SET_SW_VIEW_MEM, conversationData.viewmem).sendToTarget()
  }

  private def getConversationMemJoinCofirmStatus(): Unit = {
    mainHandler.obtainMessage(GroupSettingActivity.SET_SW_MEM_JOIN_CONFIRM, conversationData.memberjoin_confirm).sendToTarget()
  }

  override def onClick(v: View): Unit = {
  }

  def changeConversationShowMemsum(isChecked: Boolean) = {
    val dataJson = new JsonObject
    dataJson.addProperty("show_memsum", isChecked)
    SpecialServiceAPI.getInstance().updateGroupSetting(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[UpdateGroupSettingResponseModel] {

      override def onFail(code: Int, err: String): Unit = {
        showMemsumLayout.setChecked(!isChecked)
        ToastUtil.toastByResId(GroupSettingActivity.this, R.string.toast_conv_report_fail)
        verbose(l"changeConversationShowMemsum onFail code:$code, err:${Option(err)}")
      }

      override def onSuc(r: UpdateGroupSettingResponseModel, orgJson: String): Unit = {
        verbose(l"changeConversationShowMemsum onSuc 1 orgJson:${Option(orgJson)}")
        val dataBean = if(null == r) null else r.getData
        val isShowNum = if(null == dataBean) !isChecked else dataBean.isShow_memsum
        showMemsumLayout.setChecked(isShowNum)
        ToastUtil.toastByResId(GroupSettingActivity.this, R.string.conversation_detail_setting_success)
      }

      override def onSuc(r: util.List[UpdateGroupSettingResponseModel], orgJson: String): Unit = {
        verbose(l"changeConversationShowMemsum onSuc 2 orgJson:${Option(orgJson)}")
      }
    })

  }

  def changeConversationEditMsg(isChecked: Boolean) = {
    val dataJson = new JsonObject
    dataJson.addProperty("enabled_edit_msg", isChecked)
    SpecialServiceAPI.getInstance().updateGroupSetting(conversationData.remoteId.str, dataJson.toString, new OnHttpListener[UpdateGroupSettingResponseModel] {

      override def onFail(code: Int, err: String): Unit = {
        verbose(l"changeConversationEditMsg onFail code:$code, err:${Option(err)}")
        editMsgLayout.setChecked(!isChecked)
        ToastUtil.toastByResId(GroupSettingActivity.this, R.string.toast_conv_report_fail)
      }

      override def onSuc(r: UpdateGroupSettingResponseModel, orgJson: String): Unit = {
        verbose(l"changeConversationEditMsg onSuc 1 orgJson:${Option(orgJson)}")
        val dataBean = if(null == r) null else r.getData
        val isEditMsg = if(null == dataBean) !isChecked else dataBean.isEnabled_edit_msg
        editMsgLayout.setChecked(isEditMsg)
        ToastUtil.toastByResId(GroupSettingActivity.this, R.string.conversation_detail_setting_success)
      }

      override def onSuc(r: util.List[UpdateGroupSettingResponseModel], orgJson: String): Unit = {
        verbose(l"changeConversationEditMsg onSuc 2 orgJson:${Option(orgJson)}")
      }
    })

  }
}

object GroupSettingActivity {
  private val TAG = classOf[GroupSettingActivity].getName

  private val CLOSE_ALL = 100
  private val SET_URL = 101
  private val CHANGE_SW = 102
  private val SET_SW_INVITE = 103
  private val SET_SW_PERMISSION = 104
  private val SET_SW_VIEW_MEM = 105
  private val SET_SW_MEM_JOIN_CONFIRM = 106
  private val SET_SW_INVITE_VISIBLE = 107
  private val SET_ALL_FORBIDDEN = 108
  private val SET_ADD_FRIEND = 109

  def startSelf(context: Context): Unit = {
    context.startActivity(new Intent(context, classOf[GroupSettingActivity]))
  }
}
