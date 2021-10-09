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
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.listener.OnPopMenuItemClick
import com.jsy.common.model.conversation.NormalConvListMenuModel
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.R


class NormalConvListMenuAdp(context: Context, listItems: java.util.List[NormalConvListMenuModel]) extends RecyclerView.Adapter[NormalConvListMenuVh] {

  private var onPopMenuItemClick: OnPopMenuItemClick = _

  override def getItemCount: Int = if (listItems == null) 0 else listItems.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): NormalConvListMenuVh = {
    new NormalConvListMenuVh(LayoutInflater.from(context).inflate(R.layout.lay_normal_conv_menu_list_item, parent, false))
  }

  override def onBindViewHolder(holder: NormalConvListMenuVh, position: Int): Unit = {
    holder.setData(listItems, position, onPopMenuItemClick)
  }

  def setOnPopMenuItemClick(onPopMenuItemClick: OnPopMenuItemClick): Unit = {
    this.onPopMenuItemClick = onPopMenuItemClick
    notifyDataSetChanged()
  }

}

class NormalConvListMenuVh(itemView: View) extends RecyclerView.ViewHolder(itemView) {

  val ivIcon: ImageView = ViewUtils.getView(itemView, R.id.ivIcon)
  val tvTitle: TextView = ViewUtils.getView(itemView, R.id.tvTitle)
  val vLine: View = ViewUtils.getView(itemView, R.id.vLine)

  def setData(normalConvListMenuModels: java.util.List[NormalConvListMenuModel], position: Int, onPopMenuItemClick: OnPopMenuItemClick): Unit = {
    val normalConvListMenuModel: NormalConvListMenuModel = normalConvListMenuModels.get(position)
    if (normalConvListMenuModel.getResText > -1) {
      tvTitle.setText(normalConvListMenuModel.getResText)
    }
    if (normalConvListMenuModel.getResIcon > -1) {
      ivIcon.setImageResource(normalConvListMenuModel.getResIcon)
    }
    vLine.setVisibility(if (normalConvListMenuModels.size - 1 == position) View.GONE else View.VISIBLE)
    if (normalConvListMenuModel.getResItemBg > -1) {
      itemView.setBackgroundResource(normalConvListMenuModel.getResItemBg)
    }
    itemView.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (onPopMenuItemClick != null) {
          onPopMenuItemClick.onItemClick(itemView, position)
        }
      }
    })
  }
}
