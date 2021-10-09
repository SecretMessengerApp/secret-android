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
package com.jsy.common.views

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.waz.model.UserId
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.messages.UsersController
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{R, ViewHelper}

class FriendRequestListRow(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val user = Signal[UserId]()
  lazy val users = inject[UsersController]

  inflate(R.layout.adapter_friend_requestlist, this, addToParent = true)

  val imageHead = findById[ChatHeadViewNew](R.id.imageHead)
  val tvName = findById[TypefaceTextView](R.id.tvName)
  val tvHandle = findById[TypefaceTextView](R.id.tvHandle)
  val btnCancel = findById[ZetaButton](R.id.btnCancel)
  val btnAdd = findById[ZetaButton](R.id.btnAdd)

  inject[AccentColorController].accentColor.map(_.color).onUi { c =>
    btnCancel.setIsFilled(false)
    btnCancel.setAccentColor(c)
    btnCancel.setTextColor(c)
    btnAdd.setAccentColor(c)
  }

  user.flatMap(users.user).onUi { u =>
    tvName.setText(u.getDisplayName)
    val handler = u.handle.map {
      case handler => StringUtils.formatHandle(handler.string)
      case _ => ""
    }
    tvHandle.setText(handler.getOrElse(""))
    imageHead.setUserData(u, R.drawable.circle_noname)
  }

  def setUser(user: UserId) = {
    this.user ! user
  }
}
