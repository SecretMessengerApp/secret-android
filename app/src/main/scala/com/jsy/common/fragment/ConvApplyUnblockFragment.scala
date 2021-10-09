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

import android.os.Bundle
import android.text.{Editable, TextUtils, TextWatcher}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{EditText, TextView}
import androidx.appcompat.widget.Toolbar
import com.google.gson.JsonObject
import com.jsy.common.event.ReportFinishEvent
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.ConvReportModel
import com.jsy.common.utils.ToastUtil
import com.jsy.common.utils.rxbus2.RxBus
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.{FragmentHelper, R}

class ConvApplyUnblockFragment extends BaseFragment[ConvApplyUnblockFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with DerivedLogTag {

  import ConvApplyUnblockFragment._

  private lazy val convController = inject[ConversationController]

  private var rootView: View = null
  private var mToolbar: Toolbar = null
  private var unblockEdit: EditText = null
  private var unblockNum: TextView = null
  private var applyUnblockBtn: TextView = null
  private var isReqing: Boolean = false

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.fragment_conv_apply_unblock, container, false)
      rootView
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    mToolbar = view.findViewById(R.id.apply_unblock_toolbar)
    unblockEdit = view.findViewById(R.id.apply_unblock_edit)
    unblockNum = view.findViewById(R.id.apply_unblock_num)
    applyUnblockBtn = view.findViewById(R.id.apply_unblock_btn)
    mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        onBackPressed
      }
    })
    applyUnblockBtn.setOnClickListener(this)
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    unblockEdit.addTextChangedListener(new TextWatcher() {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {

      }

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {
        unblockNum.setText(s.length() + "/" + MaxText)
      }

      override def afterTextChanged(s: Editable): Unit = {
//        if (s.length() > 0) {
//          applyUnblockBtn.setClickable(true)
//        } else {
//          applyUnblockBtn.setClickable(false)
//        }
      }
    })
  }

  override def onClick(v: View): Unit = {
    if (v.getId == R.id.apply_unblock_btn) {
      val unblockStr = unblockEdit.getText.toString
      if (TextUtils.isEmpty(unblockStr)) {
        ToastUtil.toastByResId(getActivity, R.string.cursor__type_a_message)
      } else {
        reqApplyUnblock(unblockStr)
      }
    }
  }

  def reqApplyUnblock(unblockStr: String) = {
    if (!isReqing) {
      isReqing = true
      showProgressDialog()
      convController.currentConv.currentValue.foreach {
        conv =>
          val urlPath = String.format(ImApiConst.APP_CONV_APPLY_UNBLOCK, conv.remoteId.str)
          val contentJson = new JsonObject
          contentJson.addProperty("content", unblockStr)
          SpecialServiceAPI.getInstance().put(urlPath, contentJson.toString, new OnHttpListener[ConvReportModel] {

            override def onFail(code: Int, err: String): Unit = {
              verbose(l"reqApplyUnblock() contentStr onFail code:$code, err:${Option(err)}")
              val toastMsg: String = code match {
                case 512 =>
                  getString(R.string.toast_conv_report_nocreate)
                case 513 =>
                  getString(R.string.toast_conv_report_noblocked)
                case 514 =>
                  getString(R.string.toast_conv_report_noconv)
                case 515 =>
                  getString(R.string.toast_conv_report_appeal_always)
                case 516 =>
                  getString(R.string.toast_conv_report_always)
                case _ =>
                  getString(R.string.toast_conv_report_fail)
              }
              ToastUtil.toastByString(getActivity, toastMsg)
              isReqing = false
              dismissProgressDialog()
            }

            override def onSuc(r: ConvReportModel, orgJson: String): Unit = {
              verbose(l"reqApplyUnblock() contentStr onSuc 1 orgJson:${Option(orgJson)}")
              val isOk = if (null == r) false else r.isOk
              if (isOk) {
                ToastUtil.toastByResId(getActivity, R.string.toast_conv_report_appeal_suc)
                RxBus.getDefault.post(new ReportFinishEvent(true))
                isReqing = false
                dismissProgressDialog()
                onBackPressed()
              } else {
                onFail(if (null == r) -402 else r.getError_code, if (null == r) "null == r" else r.getDescription)
              }
            }

            override def onSuc(r: util.List[ConvReportModel], orgJson: String): Unit = {
              verbose(l"reqApplyUnblock() contentStr onSuc 2 orgJson:${Option(orgJson)}")
              isReqing = false
              dismissProgressDialog()
            }
          })
      }
    }
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
    isReqing = false
    dismissProgressDialog()
  }
}

object ConvApplyUnblockFragment {
  val Tag: String = getClass.getSimpleName
  val MaxText: Int = 200

  def newInstance(): ConvApplyUnblockFragment = {
    val fragment: ConvApplyUnblockFragment = new ConvApplyUnblockFragment
    fragment
  }

  trait Container {
  }

}


