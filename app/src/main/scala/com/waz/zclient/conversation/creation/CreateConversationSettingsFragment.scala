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
/**
  * Wire
  * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.conversation.creation

import android.os.Bundle
import android.text.InputFilter.LengthFilter
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.fragment.app.Fragment
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.common.views.InputBox
import com.waz.zclient.common.views.InputBox.GroupNameValidator
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}

class CreateConversationSettingsFragment extends Fragment with FragmentHelper {
  private lazy val createConversationController = inject[CreateConversationController]

  private var inputBox: Option[InputBox] = None

  var rootView: View = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.create_conv_settings_fragment, container, false)
      rootView
    }
  }

  override def onViewCreated(v: View, savedInstanceState: Bundle): Unit = {
    inputBox = Option(ViewUtils.getView(rootView, R.id.input_box))
    inputBox.foreach { box =>
      box.text.onUi(createConversationController.name ! _)
      box.editText.setFilters(Array(new LengthFilter(64)))
      box.setValidator(GroupNameValidator)
      createConversationController.name.currentValue.foreach(text => box.editText.setText(text))
      box.errorLayout.setVisible(false)
    }

  }



  override def onDestroyView(): Unit = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
    }
    super.onDestroyView()
  }
}

object CreateConversationSettingsFragment {
  val Tag: String = getClass.getSimpleName
}
