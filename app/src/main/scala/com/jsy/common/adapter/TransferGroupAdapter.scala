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

import java.util

import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.adapter.TransferGroupAdapter.TransferGroupAdapterCallback
import com.jsy.common.views.SelectUserShareGroupRowView
import com.jsy.res.utils.ViewUtils
import com.waz.model._
import com.waz.utils.events.EventContext
import com.waz.zclient._

class TransferGroupAdapter(darkTheme: Boolean, callback: TransferGroupAdapterCallback)(implicit injector: Injector) extends RecyclerView.Adapter[RecyclerView.ViewHolder] with Injectable {
  def setUserData(participants: util.List[UserData]): Unit = {
    this.participants = participants
    notifyDataSetChanged()
  }


  implicit private val ec = EventContext.Implicits.global
  var participants: util.List[UserData] = new util.ArrayList[UserData]()
  setHasStableIds(true)



  lazy val activity = inject[android.app.Activity]

  override def getItemCount = {
    participants.size
  }

  override def getItemId(position: Int): Long = position

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = {
    val item:UserData = participants.get(position)
    holder.asInstanceOf[SelectUserToTransferGroupViewHolder].bind(item, false)
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view : View = LayoutInflater.from(parent.getContext).inflate(R.layout.item_transfer_group, parent, false)
    new SelectUserToTransferGroupViewHolder(view, true, darkTheme,callback)
  }
}

object TransferGroupAdapter {

  trait TransferGroupAdapterCallback {
    def onContactListUserClicked(userData: UserData): Unit
  }

}

class SelectUserToTransferGroupViewHolder(val view: View, val showContactInfo: Boolean, darkTheme: Boolean,callback:TransferGroupAdapterCallback) extends RecyclerView.ViewHolder(view) {
  val userRow: SelectUserShareGroupRowView = ViewUtils.getView(view, R.id.selectUserShareGroupRowView)

  def bind(userData: UserData, isSelected: Boolean): Unit = {
    view.setVisibility(View.VISIBLE)
    view.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit ={
        callback.onContactListUserClicked(userData)
      }
    })
    userRow.setUser(userData)
    userRow.setSelected(isSelected)
  }

  def unbind() = {
    view.setVisibility(View.GONE)
  }
}
