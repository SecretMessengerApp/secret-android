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
import android.widget.{FrameLayout, ImageView}
import com.jsy.common.views.pickuer.UserRowView
import com.waz.model.UserData
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{R, ViewHelper}

class SelectUserShareGroupRowView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends FrameLayout(context, attrs, defStyleAttr) with UserRowView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  inflate(R.layout.item_select_user_share_group, this)

  private val ivStatus = findById[ImageView](R.id.ivStatus)
  private val chathead = findById[ChatHeadViewNew](R.id.chvHead)
  private val tvName = findById[TypefaceTextView](R.id.tvName)

  var userData = Option.empty[UserData]

  def setUser(userData: UserData): Unit = {
    this.userData = Some(userData)
    chathead.clearImage()
    chathead.setUserData(userData)
    tvName.setText(userData.getDisplayName)
  }

  def getUser = userData.map(_.id)

  def onClicked(): Unit = {
    setSelected(!isSelected)
  }

  def setStatusVisible(status: Int): Unit = {
    ivStatus.setVisibility(status)
  }

  override def setSelected(selected: Boolean): Unit = {
    super.setSelected(selected)
    chathead.setSelected(selected)
    ivStatus.setImageResource(if (selected) R.drawable.invite_select else R.drawable.invite_drawable_normal)
  }

}
