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

import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.res.utils.ViewUtils
import com.waz.api.User.ConnectionStatus
import com.waz.model.UserData
import com.waz.utils.events.EventContext
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.SearchUserListState
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._
import com.waz.zclient.{Injectable, Injector, R}



class SelectUserShareGroupAdapter (adapterCallback: SelectUserShareGroupAdapter.Callback)
                                  (implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[SelectUserShareGroupViewHolder] with Injectable {

  private val searchController       = new SearchController()

  private var localResults       = Seq.empty[UserData]
  private val postData = scala.collection.mutable.Buffer.empty[UserData]

  val filter = searchController.filter
  val searchResults = searchController.searchUserOrServices

  (for {
    res     <- searchResults
  } yield res).onUi {
    case res =>
      res match {
        case SearchUserListState.Users(search) =>
          localResults     = search.local.filter(_.connection != ConnectionStatus.BLOCKED)
        case _ =>
          localResults     = Seq.empty
      }
      notifyDataSetChanged()
  }


  def updateSendData(position: Int): Boolean = {
    val userData = localResults(position)
    val selectedPositionOfPostData = postData.indexWhere(_.id == userData.id)
    if (selectedPositionOfPostData > -1) {
      postData.remove(selectedPositionOfPostData)
      false
    } else {
      postData.append(userData)
      true
    }

  }

  def getPostData() = {
    postData
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectUserShareGroupViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(R.layout.item_select_user_share_group, parent, false)
    new SelectUserShareGroupViewHolder(view)
  }

  override def onBindViewHolder(holder: SelectUserShareGroupViewHolder, position: Int): Unit = {
    val user = localResults(position)
    holder.bind(user,postData.exists(_.id == user.id))
    holder.itemView.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        val selected = updateSendData(position)
        holder.updateUserRowStatus(selected)
      }
    })
  }

  override def getItemCount: Int = {
    localResults.size
  }

}

class SelectUserShareGroupViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {

  private val ivStatus : ImageView = ViewUtils.getView(view, R.id.ivStatus)
  private val chathead : ChatHeadViewNew = ViewUtils.getView(view, R.id.chvHead)
  private val tvName : TypefaceTextView = ViewUtils.getView(view, R.id.tvName)

  def bind(userData: UserData, isSelected: Boolean): Unit = {
    chathead.clearImage()
    chathead.setUserData(userData, R.drawable.circle_noname)
    tvName.setText(userData.getDisplayName)
    ivStatus.setVisibility(View.VISIBLE)
    setSelected(isSelected)
  }

  def unbind() = {
    view.setVisibility(View.GONE)
  }

  def setSelected(selected : Boolean) = {
    chathead.setSelected(selected)
    ivStatus.setImageResource(if (selected) R.drawable.invite_select else R.drawable.invite_drawable_normal)
  }

  def updateUserRowStatus(isSelected: Boolean): Unit = {
    setSelected(isSelected)
  }
}

object SelectUserShareGroupAdapter {
  val ConnectedUser: Int = 1

  trait Callback {
    def onUserClicked(position: Int): Unit
  }
}
