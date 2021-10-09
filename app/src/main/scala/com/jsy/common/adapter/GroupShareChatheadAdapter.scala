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
import android.view.{View, ViewGroup}
import android.widget.BaseAdapter
import com.jsy.common.dialog.GroupShareLinkPopupWindow
import com.jsy.common.views.ChatheadWithTextFooter
import com.waz.api.User.ConnectionStatus
import com.waz.model.UserData
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.utils.MainActivityUtils
import com.waz.zclient.{Injectable, WireContext, ZApplication}

import scala.concurrent.ExecutionContext
import scala.util.Success

/**
  * Created by eclipse on 2018/12/20.
  */

trait OnChatheadWithTextClick {
  def clickItem(selectedUesr: Set[UserData])
}


class GroupShareChatheadAdapter(context: Context) extends BaseAdapter with Injectable {


  private lazy implicit val wContext = WireContext(ZApplication.getInstance)
  private lazy implicit val injector = wContext.injector
  private lazy implicit val executionContext = ExecutionContext.Implicits.global
  implicit val eventContext = EventContext.Implicits.global
  val zms = inject[Signal[ZMessaging]]
  private var contacts = Seq.empty[UserData]
  //private var contactsForSelect = Seq.empty[GroupShareUserData]
  private var selectedUser = Set.empty[UserData]
  private var onChatheadWithTextClick: OnChatheadWithTextClick = null


  for(z <- zms){
    val data = z.storage.usersStorage.list()
    data onComplete{
      case Success(x) =>
        contacts = x.filter(_.connection == ConnectionStatus.ACCEPTED)
        notifyDataSetChanged()
      case _ =>
    }

  }

  def setOnChatheadItemClickCallBack(callBack: OnChatheadWithTextClick) = {
    this.onChatheadWithTextClick = callBack
  }


  def removeAllSelectUser = {
    selectedUser = Set.empty
  }

  override def getItem(i: Int): UserData = {
    contacts.apply(i)
  }

  override def getItemId(i: Int): Long = i

  override def getView(i: Int, view: View, viewGroup: ViewGroup): View = getChatheadLabel(i, view, viewGroup)

  override def getCount: Int = {

    val maxCount = GroupShareLinkPopupWindow.COLUMNS * GroupShareLinkPopupWindow.ROWS
    if (contacts.size > maxCount) {
      maxCount
    } else {
      contacts.size
    }
  }


  def getChatheadLabel(position: Int, convertView: View, parent: ViewGroup) = {
    var mView: ChatheadWithTextFooter = null
    if (convertView == null) mView = new ChatheadWithTextFooter(parent.getContext)
    else mView = convertView.asInstanceOf[ChatheadWithTextFooter]
    val user = getItem(position)
    mView.setUserId(user.id)
    mView.setVisibility(View.VISIBLE)
    mView.setOnClickListener(new View.OnClickListener {
      override def onClick(view: View): Unit = {
        mView.setSelected(!mView.isSelected)
        if (mView.isSelected) {
          selectedUser = selectedUser + user
        } else {
          if (selectedUser.nonEmpty && selectedUser.contains(user)) {
            selectedUser = selectedUser - user
          }
        }

        if (onChatheadWithTextClick != null) {
          onChatheadWithTextClick.clickItem(selectedUser)
        }
      }
    })
    mView
  }


  //case class GroupShareUserData(user: UserData,isSelect : Boolean)
}
