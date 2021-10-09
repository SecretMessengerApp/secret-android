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
import android.text.{Editable, TextUtils, TextWatcher}
import android.view.{KeyEvent, View}
import android.widget.TextView.OnEditorActionListener
import android.widget.{EditText, TextView}
import androidx.appcompat.widget.Toolbar
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{BaseActivity, R}
import org.json.JSONObject

import java.util
import scala.concurrent.ExecutionContext

class UserRemarkActivity extends BaseActivity with DerivedLogTag{

  import UserRemarkActivity._

  implicit val executionContext = ExecutionContext.Implicits.global

  val MAX_BYTE_COUNT = 32
  val ENCODING = "UTF-8"

  private var toolbar: Toolbar = null

  private var edittext: EditText = null

  private var remark: String = null

  private var otherUserId: String = _

  private var token: String = _

  private var tokenType: String = _

  private var saveText: TypefaceTextView = _

  private var inputClipedTxt: String = ""
  private var frontTxt: String = ""
  private var middleTxt: String = ""
  private var behindTxt: String = ""
  private var startCursorIdx = 0
  private var endCursorIdx = 0


  override def canUseSwipeBackLayout = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_userremark)

    val account = ZMessaging.currentAccounts.activeAccount.collect { case Some(accountData) if !StringUtils.isBlank(accountData.id.str) => accountData }

    token = account.currentValue.get.accessToken.head.accessToken
    tokenType = account.currentValue.get.accessToken.head.tokenType

    toolbar = ViewUtils.getView(this, R.id.user_remark_toolbar)

    edittext = ViewUtils.getView(this, R.id.edt_userremark)

    saveText = ViewUtils.getView(this, R.id.save)

    //val iConversation = getStoreFactory.conversationStore.getCurrentConversation
    otherUserId = getIntent.getStringExtra(INTENT_KEY_userId)
    remark = getIntent.getStringExtra(INTENT_KEY_remark)
    edittext.setText(remark)

    edittext.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean ={
        KeyboardUtils.hideSoftInput(edittext)
        true
      }

    })

    edittext.addTextChangedListener(new TextWatcher() {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {
        startCursorIdx = start
        inputClipedTxt = s.toString
        frontTxt = s.subSequence(0, startCursorIdx).toString
      }

      override def onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int): Unit = {
      }

      override def afterTextChanged(s: Editable): Unit = {
        var count = 0
        val inputTxt = s.toString().trim()
        try {
          endCursorIdx = edittext.getSelectionEnd()
          behindTxt = inputTxt.substring(endCursorIdx, inputTxt.length())
          middleTxt = inputTxt.substring(startCursorIdx, endCursorIdx)
          val sb = new StringBuilder(inputClipedTxt)
          var break = false
          if (inputTxt.getBytes(ENCODING).length > MAX_BYTE_COUNT && !TextUtils.isEmpty(middleTxt)) {
            for (i <- 0 until middleTxt.length() if !break) {
              if (sb.append(middleTxt.subSequence(i, i + 1)).toString().getBytes().length <= MAX_BYTE_COUNT) {
                count = i + 1
              } else {
                break = true
              }
            }
            s.delete(startCursorIdx + count, startCursorIdx + middleTxt.length())
            inputClipedTxt = s.toString

          } else {
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
            edittext.setText(remark)
        }
      }

    })

    saveText.setOnClickListener(new View.OnClickListener {
      override def onClick(view: View): Unit = {
        if (edittext.getText.toString == remark) finish()
        else {
          showProgressDialog()
          val jsonObject = new JSONObject()
          jsonObject.put("right", otherUserId)
          jsonObject.put("remark", edittext.getText.toString.trim)
          SpecialServiceAPI.getInstance().post(ImApiConst.USER_REMARK,jsonObject,new OnHttpListener[String] {

            override def onFail(code: Int, err: String): Unit = {
              verbose(l"code:${code} err:${err}")
              showToast(getResources.getString(R.string.conversation_detail_setting_failure))
            }

            override def onSuc(r: String, orgJson: String): Unit = {
              verbose(l" orgJson:${orgJson}")
              showToast(getResources.getString(R.string.conversation_detail_setting_success))
              finish()
            }

            override def onSuc(r: util.List[String], orgJson: String): Unit = {
              verbose(l"r:${r} orgJson:${orgJson}")
              showToast(getResources.getString(R.string.conversation_detail_setting_success))
              finish()
            }

            override def onComplete(): Unit = {
              super.onComplete()
              dismissProgressDialog()
            }
          })
        }
      }
    })

    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        finish()
      }
    })

  }

}

object UserRemarkActivity {

  val INTENT_KEY_userId = "userId"
  val INTENT_KEY_remark = "remark"

  def startSelfForResult(activity: Activity, userId: UserId, remark: String, requestCode: Int): Unit = {
    val intent = new Intent(activity, classOf[UserRemarkActivity])
    intent.putExtra(INTENT_KEY_userId, userId.str)
    intent.putExtra(INTENT_KEY_remark, remark)
    activity.startActivityForResult(intent, requestCode)
  }
}
