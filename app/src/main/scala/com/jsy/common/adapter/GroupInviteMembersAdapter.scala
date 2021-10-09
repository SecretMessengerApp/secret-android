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
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.acts.GroupInviteMembersActivity
import com.jsy.common.adapter.GroupInviteMembersAdapter._
import com.jsy.res.utils.ViewUtils
import com.waz.model.UserId
import com.waz.zclient.R
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.ui.text.TypefaceTextView

class GroupInviteMembersAdapter(context: Context, dataResult: Seq[GroupInviteMembersActivity.InviteMemberResult], childrenSize: Int) extends RecyclerView.Adapter[RecyclerView.ViewHolder] {

  setHasStableIds(true)

  override def onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view = LayoutInflater.from(viewGroup.getContext).inflate(viewType match {
      case GroupInviteMembersActivity.TopParent => R.layout.group_invite_item_title
      case GroupInviteMembersActivity.TopChildren => R.layout.group_invite_item_title
      case GroupInviteMembersActivity.Member => R.layout.group_invite_item_user_row
      case _ => -1
    }, viewGroup, false)

    viewType match {
      case GroupInviteMembersActivity.TopParent => new TopParentViewHolder(view)
      case GroupInviteMembersActivity.TopChildren => new TopChildrenViewHolder(view, childrenSize)
      case GroupInviteMembersActivity.Member => new UserRowViewHolder(view)
      case _ => null
    }
  }

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = {
    val item = dataResult(position)
    item.itemType match {
      case GroupInviteMembersActivity.TopParent =>
        holder.asInstanceOf[TopParentViewHolder].bind()
      case GroupInviteMembersActivity.Member =>
        holder.asInstanceOf[UserRowViewHolder].bind(item)
      case GroupInviteMembersActivity.TopChildren =>
        holder.asInstanceOf[TopChildrenViewHolder].bind(context)
      case _ =>
    }
  }

  override def getItemId(position: Int): Long = position

  override def getItemCount: Int = dataResult.size

  override def getItemViewType(position: Int) =
    dataResult.lift(position).fold(-1)(_.itemType)
}


object GroupInviteMembersAdapter {

  class TopParentViewHolder(view: View) extends RecyclerView.ViewHolder(view) {
    private val titleTextView = ViewUtils.getView[TextView](view, R.id.title_textView)

    def bind(): Unit = {
      titleTextView.setText(R.string.conversation_detail_settings_invite_me)
    }
  }

  class UserRowViewHolder(view: View) extends RecyclerView.ViewHolder(view) {
    private val chathead = ViewUtils.getView[ChatHeadViewNew](view, R.id.chathead)
    private val nameView = ViewUtils.getView[TypefaceTextView](view, R.id.name_text)
    private val subtitleView = ViewUtils.getView[TypefaceTextView](view, R.id.username_text)

    def bind(data: GroupInviteMembersActivity.InviteMemberResult): Unit = {
      nameView.setText(data.name)
      subtitleView.setText(data.handle)
      chathead.clearUser()
      chathead.loadUser(UserId(data.id))
    }
  }

  class TopChildrenViewHolder(view: View, childrenSize: Int) extends RecyclerView.ViewHolder(view) {
    private val titleTextView = ViewUtils.getView[TextView](view, R.id.title_textView)

    def bind(context: Context): Unit = {
      val format = context.getResources.getString(R.string.conversation_detail_settings_me_invite)
      titleTextView.setText(String.format(format, String.valueOf(childrenSize)))
    }
  }

}
