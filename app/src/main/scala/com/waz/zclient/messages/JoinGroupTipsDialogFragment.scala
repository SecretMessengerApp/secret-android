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
package com.waz.zclient.messages

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup, Window}
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.jsy.common.utils.ScreenUtils
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.R
import com.waz.zclient.messages.JoinGroupTipsDialogFragment._

class JoinGroupTipsDialogFragment extends DialogFragment {

  private lazy val level = getArguments.getString(PARAMS_LEVEL)

  private var clickListener: ClickListener = _

  def setClickListener(clickListener: ClickListener): Unit ={
    this.clickListener = clickListener
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.dialog_join_group_tips, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    getDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

    val contentTextView = ViewUtils.getView[TextView](view, R.id.content_textView)
    if (level == "warring") {
      contentTextView.setText(R.string.conversation_join_group_tips_warring)

      val leftTextView = ViewUtils.getView[TextView](view, R.id.left_textView)
      leftTextView.setText(R.string.conversation_join_group_confirm)
      leftTextView.setOnClickListener(new View.OnClickListener {
        override def onClick(v: View): Unit = {
          dismiss()
          Option(clickListener).foreach(_.confirm())
        }
      })

      val rightTextView = ViewUtils.getView[TextView](view, R.id.right_textView)
      rightTextView.setText(R.string.conversation_join_group_cancel)
      rightTextView.setOnClickListener(new View.OnClickListener {
        override def onClick(v: View): Unit = {
          dismiss()
          Option(clickListener).foreach(_.cancel())
        }
      })
    } else if (level == "error") {
      contentTextView.setText(R.string.conversation_join_group_tips_error)

      val okTextView = ViewUtils.getView[TextView](view, R.id.ok_textView)
      okTextView.setVisibility(View.VISIBLE)
      okTextView.setText(R.string.conversation_join_group_know)
      okTextView.setOnClickListener(new View.OnClickListener {
        override def onClick(v: View): Unit = {
          dismiss()
          Option(clickListener).foreach(_.cancel())
        }
      })
    }
  }

  override def onStart(): Unit = {
    super.onStart()
    getDialog.getWindow.getAttributes.height = ViewGroup.LayoutParams.WRAP_CONTENT
    getDialog.getWindow.getAttributes.width = ((ScreenUtils.getScreenWidth(getContext) * 0.8).toInt)
    getDialog.getWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT))
    getDialog.setCanceledOnTouchOutside(false)
  }
}

object JoinGroupTipsDialogFragment {
  private val PARAMS_LEVEL = "params_level"

  def apply(level: String): JoinGroupTipsDialogFragment = {

    val dataBundle: Bundle =new Bundle
    dataBundle.putString(PARAMS_LEVEL, level)

    val dialogFragment = new JoinGroupTipsDialogFragment()
    dialogFragment.setArguments(dataBundle)
    dialogFragment
  }

  abstract class ClickListener {
    def cancel(){}

    def confirm(){}
  }
}
