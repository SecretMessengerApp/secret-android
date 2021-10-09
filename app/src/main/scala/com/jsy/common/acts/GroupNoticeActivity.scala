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

import android.content.{Context, Intent}
import android.os.Bundle
import android.text.{Editable, TextUtils, TextWatcher}
import android.view.View
import android.view.View.OnClickListener
import android.widget.{EditText, LinearLayout}
import androidx.appcompat.widget.Toolbar
import com.google.gson.JsonObject
import com.jsy.common.acts.GroupNoticeActivity._
import com.jsy.common.dialog.TitleMsgSureDialog
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.HttpResponseBaseModel
import com.jsy.common.utils.{MainHandler, Utils}
import com.jsy.common.{OnLoadUserListener, ConversationApi}
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.model.UserData
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.{SpUtils, StringUtils}
import com.waz.zclient.{BaseActivity, R}

import java.util

class GroupNoticeActivity extends BaseActivity with View.OnClickListener {

  private var tool: Toolbar = _
  private var mTvNoticePublish: TypefaceTextView = _
  private var mEdtNotice: EditText = _
  private var mTvUserName:TypefaceTextView=_
  private var mIvAvatar:ChatHeadViewNew=_
  private var mLLUserInfo:LinearLayout=_
  private var rConvId: String = _
  private lazy val convController = inject[ConversationController]
  private var isCreator:Boolean=false
  private var advisory: String = _
  private var titleMsgCancelSureDialog: TitleMsgSureDialog = _

  private val publishDialogListener=new TitleMsgSureDialog.OnTitleMsgSureDialogClick{
    override def onClickCancel(): Unit = {
      closeMsgCancelSureDialog()
      showKeyboard()

    }
    override def onClickConfirm(): Unit = {
      closeMsgCancelSureDialog()
      publishGroupNotice(mEdtNotice.getText.toString)
    }
  }

  private val exitDialogListener=new TitleMsgSureDialog.OnTitleMsgSureDialogClick{
    override def onClickCancel(): Unit = {
      closeMsgCancelSureDialog()
      showKeyboard()
    }

    override def onClickConfirm(): Unit = {
      closeMsgCancelSureDialog()
      finish()
    }
  }

  private def closeMsgCancelSureDialog(): Unit ={
     if(titleMsgCancelSureDialog!=null && titleMsgCancelSureDialog.isShowing){
       titleMsgCancelSureDialog.dismiss()
     }
  }

  override def canUseSwipeBackLayout: Boolean = true

  override def onBackPressed(): Unit = {
    if(isCreator){
        if(!mEdtNotice.getText.toString.equals(advisory)){
          showExitDialog()
          return
        }
    }
    super.onBackPressed()
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {

    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_notice)
    rConvId = getIntent.getStringExtra(ARG_RCONVID)
    titleMsgCancelSureDialog = new TitleMsgSureDialog(this).updateFields(false, true, true, true)
    titleMsgCancelSureDialog.setCancelable(true)
    titleMsgCancelSureDialog.setCanceledOnTouchOutside(true)

    findViews()
  }

  def findViews(): Unit = {

    tool = ViewUtils.getView(this, R.id.conversation_notice_tool)

    mTvNoticePublish = ViewUtils.getView(this, R.id.group_notice_publish)
    mEdtNotice = ViewUtils.getView(this, R.id.edt_notice)
    mLLUserInfo=ViewUtils.getView(this,R.id.ll_userInfo)
    mIvAvatar=ViewUtils.getView(this,R.id.iv_avatar)
    mTvUserName=ViewUtils.getView(this,R.id.tv_user_name)

    mTvNoticePublish.setOnClickListener(this)

    tool.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        onBackPressed()
      }
    })


    convController.currentConv.currentValue.foreach{
      conversationData =>
        isCreator = conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(GroupNoticeActivity.this))
        advisory = conversationData.advisory.getOrElse("")
        mEdtNotice.addTextChangedListener(new TextWatcher {
          override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {}

          override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {}

          override def afterTextChanged(s: Editable): Unit = {
            val afterEditNotice = mEdtNotice.getText.toString
            if (afterEditNotice.equals(advisory)) {
              mTvNoticePublish.setVisibility(View.INVISIBLE)
            }
            else {
              mTvNoticePublish.setText(R.string.complete)
              mTvNoticePublish.setVisibility(View.VISIBLE)
            }
          }
        })

        ConversationApi.loadUser(conversationData.creator, new OnLoadUserListener() {
          override def onFail(): Unit = {
            LogUtils.d("JACK","onFail")
          }
          override def onSuc(userData: UserData): Unit = {
            MainHandler.getInstance().post(new Runnable {
              override def run(): Unit ={
                if(!Utils.isDestroyed(GroupNoticeActivity.this)){
                  mIvAvatar.setUserData(userData)
                }
                mTvUserName.setTransformedText(userData.getShowName)
              }
            })
          }
        })


        if (isCreator) {
          if (!StringUtils.isBlank(advisory)) {
            mLLUserInfo.setVisibility(View.VISIBLE)
            mEdtNotice.setText(advisory)
            mTvNoticePublish.setText(R.string.compile)
            mTvNoticePublish.setVisibility(View.VISIBLE)
          }
          else {
            mLLUserInfo.setVisibility(View.GONE)
            mTvNoticePublish.setVisibility(View.INVISIBLE)
            showKeyboard()
          }

        } else {
          mLLUserInfo.setVisibility(View.VISIBLE)
          mEdtNotice.setText(advisory)
          mTvNoticePublish.setVisibility(View.INVISIBLE)
        }

    }
  }

  def showKeyboard(): Unit = {
    MainHandler.getInstance().postDelayed(new Runnable {
      override def run(): Unit = {
        mEdtNotice.setSelection(mEdtNotice.getText.length)
        KeyboardUtils.showSoftInput(mEdtNotice)
      }
    }, 200L)

  }


  def publishGroupNotice(notice: String): Unit = {

    val tokenType = SpUtils.getTokenType(this)
    val token = SpUtils.getToken(this)
    val dataJson = new JsonObject
    dataJson.addProperty("advisory", notice)

    showProgressDialog(getString(R.string.group_notice_saving))
    SpecialServiceAPI.getInstance().updateGroupInfo(rConvId, dataJson.toString, new OnHttpListener[HttpResponseBaseModel] {
      override def onFail(code: Int, err: String): Unit = {
        GroupNoticeActivity.this.dismissProgressDialog()
        showToast(err)
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        if(StringUtils.isBlank(notice)){
          showProgressDialog(getString(R.string.group_notice_already_clear),cancelable = true,R.drawable.duigou,needUpdateView =false)
        }
        else{
          showProgressDialog(getString(R.string.group_notice_already_publish),cancelable = true,R.drawable.duigou,needUpdateView =false)
        }
        MainHandler.getInstance().postDelayed(new Runnable {
          override def run(): Unit = {
            GroupNoticeActivity.this.dismissProgressDialog()
            finish()
          }
        },2000)
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })

  }


  override def onStop(): Unit = {
    KeyboardUtils.hideSoftInput(this)
    super.onStop()
  }

  override def onClick(v: View): Unit = {

    if (v.getId == R.id.group_notice_publish) {
      if (mTvNoticePublish.getText.toString.equals(getResources.getString(R.string.compile))) {
        mTvNoticePublish.setText(R.string.complete)
        showKeyboard()
        return
      }

      val noticeNew = mEdtNotice.getText.toString

      if (!TextUtils.isEmpty(noticeNew)) {
        if (StringUtils.isBlank(advisory)) {
          showFirstPublishDialog()
        }
        else {
          publishGroupNotice(noticeNew)
        }
      }
      else {
        if (!StringUtils.isBlank(advisory)) {
          showClearNoticeDialog();
        }
        else {
          publishGroupNotice(noticeNew)
        }
      }

    }

  }

  def showExitDialog():Unit={
    titleMsgCancelSureDialog.setCancelText(R.string.group_notice_continue_edit)
    titleMsgCancelSureDialog.setConfirmText(R.string.exit)
    titleMsgCancelSureDialog.show(0,R.string.group_notice_tip_exit,exitDialogListener)
  }
  def showFirstPublishDialog(): Unit = {
    titleMsgCancelSureDialog.setCancelText(R.string.secret_cancel)
    titleMsgCancelSureDialog.setConfirmText(R.string.secret_publish)
    titleMsgCancelSureDialog.show(0, R.string.group_notice_tip_publish,publishDialogListener)
  }

  def showClearNoticeDialog(): Unit = {
    titleMsgCancelSureDialog.setCancelText(R.string.secret_cancel)
    titleMsgCancelSureDialog.setConfirmText(R.string.group_notice_clear)
    titleMsgCancelSureDialog.show(0, R.string.group_notice_tip_clear, publishDialogListener)
  }


}


object GroupNoticeActivity {

  val ARG_RCONVID = "rConvId"

  def startGroupNoticeActivitySelf(context: Context, rConvId: String): Unit = {
    val intent = new Intent(context, classOf[GroupNoticeActivity])
    intent.putExtra(ARG_RCONVID, rConvId)
    context.startActivity(intent)
  }
}
