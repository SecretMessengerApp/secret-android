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
package com.jsy.common.views

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.{Gravity, View, ViewGroup}
import android.widget.{EditText, TextView}
import com.waz.zclient.R

class LogoutPasswordDialog(context: Context,themeResId : Int) extends Dialog(context : Context,themeResId : Int) {

  private var et_verify_code : EditText = _

  private var confirm_send_code : TextView = _

  private var cancle_send_code  : TextView = _

  private var btn_confirm_listener  : BtnClickListener = _

  def this(context: Context) = this(context,R.style.Dialog_Msg)



  override protected def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.dialog_logout_password)
    //setCanceledOnTouchOutside(false);
    setCancelable(false)
    initView()
  }

  private def initView(): Unit = {
    et_verify_code = findViewById(R.id.et_verify_code)
    confirm_send_code = findViewById(R.id.confirm_send_code)
    cancle_send_code = findViewById(R.id.cancle_send_code)

    confirm_send_code.setOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        if (btn_confirm_listener != null) btn_confirm_listener.onBtnClick(et_verify_code.getText.toString)
      }
    })
    cancle_send_code.setOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        dismiss()
      }
    })
  }


  override def show(): Unit = {
    super.show()
    val layoutParams = getWindow.getAttributes
    layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    layoutParams.gravity = Gravity.CENTER
    getWindow.getDecorView.setPadding(0, 0, 0, 0)
    getWindow.setAttributes(layoutParams)
  }

  def setBtnClickListener(listerer: BtnClickListener): Unit = {
    this.btn_confirm_listener = listerer
  }
}

trait BtnClickListener {
  def onBtnClick(password: String): Unit
}
