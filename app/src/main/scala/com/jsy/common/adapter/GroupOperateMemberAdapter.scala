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
package com.jsy.common.adapter

import android.content.Context
import android.text.TextUtils
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.RelativeLayout
import androidx.recyclerview.widget.RecyclerView
import com.jsy.res.utils.ViewUtils
import com.mcxtzhang.swipemenulib.SwipeMenuLayout
import com.waz.model.{UserData, UserId}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{Injectable, Injector, R}

class GroupOperateMemberAdapter(context: Context, listener: OnDeleteUserListener, mMemberData: java.util.List[UserData], groupUserType: Int = 0)(implicit injector: Injector) extends RecyclerView.Adapter[ManagerHolder] with Injectable {

  private lazy val mInflater: LayoutInflater = LayoutInflater.from(context)

  override def onCreateViewHolder(viewGroup: ViewGroup, i: Int): ManagerHolder = {
    val itemView = mInflater.inflate(R.layout.adapter_group_manager, viewGroup, false);
    new ManagerHolder(itemView)
  }

  override def onBindViewHolder(holder: ManagerHolder, position: Int): Unit = {
    val userData: UserData = mMemberData.get(position)
    if (!TextUtils.isEmpty(userData.name.str)) {
      holder.tvAdminName.setText(userData.name.str)
    } else {
      holder.tvAdminName.setText(userData.handle.head.string)
    }

    holder.tvDelete.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (null != listener) {
          listener.onDelCurrentMember(userData.id, position)
        }
      }
    })
    holder.itemContent.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (null != listener) {
          listener.onItemClick(userData.id, position)
        }
      }
    })
    holder.itemContent.setOnLongClickListener(new View.OnLongClickListener {
      override def onLongClick(v: View): Boolean = {
        if (null != listener) {
          listener.onDelCurrentMember(userData.id, position)
        }
        return true
      }
    })
  }

  override def getItemCount: Int = {
    return mMemberData.size()
  }

  def getData(position: Int): UserData = {
    mMemberData.get(position)
  }
}

class ManagerHolder(itemView: View) extends RecyclerView.ViewHolder(itemView) {
  val itemSwipe = ViewUtils.getView[SwipeMenuLayout](itemView, R.id.item_swipe)
  val itemContent = ViewUtils.getView[RelativeLayout](itemView, R.id.admin_content)
  val tvAdminName = ViewUtils.getView[TypefaceTextView](itemView, R.id.tv_admin_name)
  val tvDelete = ViewUtils.getView[TypefaceTextView](itemView, R.id.tvDeleteAccount)
}

trait OnDeleteUserListener {
  def onItemClick(userId: UserId, pos: Int)
  def onDelCurrentMember(userId: UserId, pos: Int)
}
