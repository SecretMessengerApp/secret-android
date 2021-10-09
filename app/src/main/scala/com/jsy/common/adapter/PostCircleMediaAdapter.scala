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
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.User.ConnectionStatus
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.utils.events.{EventContext, EventStream}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.AddUserListState.Users
import com.waz.zclient.{R, _}

class PostCircleMediaAdapter()(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[RecyclerView.ViewHolder]
  with Injectable
  with DerivedLogTag {

  setHasStableIds(true)

  private lazy val userAccountsController = inject[UserAccountsController]

  private lazy val searchController = new SearchController()

//  private var currentUser = Option.empty[UserData]

  val filter = searchController.filter
  val searchResults = searchController.addUserOrServices

  private var localResults = Seq.empty[UserData]

  var checkedUser: Set[UserData] = Set.empty[UserData]
  val onSelectionChanged = EventStream[(UserId, Boolean)]()

  (for {
    res <- searchResults
  } yield res).onUi {
    res =>
      res match {
        case Users(search) =>
          localResults = search.filter(_.connection != ConnectionStatus.BLOCKED).filter(_.nature.getOrElse(NatureTypes.Type_Normal) == NatureTypes.Type_Normal)
        case _             =>
          localResults = Seq.empty
      }
      verbose(l"curUser <- userAccountsController.currentUser:localResults:${localResults.size}")
      notifyDataSetChanged()
  }

  def getCheckedUser: Set[UserData] = {
    return checkedUser
  }

  override def getItemId(position: Int): Long = position

  override def getItemCount =
    localResults.size

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = {
    val item = localResults(position)
    holder.asInstanceOf[UserViewHolder].bind(item /*, team.map(_.id)*/ , checkedUser.exists(_.id == item.id))
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false)
    new UserViewHolder(view.asInstanceOf[SingleUserRowView])
  }

  class UserViewHolder(view: SingleUserRowView) extends RecyclerView.ViewHolder(view) {

    private var userData = Option.empty[UserData]
    view.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        v.asInstanceOf[SingleUserRowView].toogleChecked()
        userData.foreach {
          ud =>
            val isChecked: Boolean = v.asInstanceOf[SingleUserRowView].isChecked()
            if (isChecked) {
              checkedUser += ud
            } else {
              checkedUser -= ud
            }
            onSelectionChanged ! (ud.id, isChecked)
        }
      }
    })
    view.showArrow(false)
    view.showCheckbox(true)
    view.setBackground(null)

    def bind(userData: UserData /*, teamId: Option[TeamId] = None*/ , selected: Boolean): Unit = {
      this.userData = Some(userData)
      view.setUserData(userData /*, teamId*/)
      view.setChecked(selected)
    }
  }

  def removeSelectUser(id: String): Unit = {
    checkedUser = checkedUser.filterNot(_.id.str.equalsIgnoreCase(id))
    this.notifyDataSetChanged()
    verbose(l"removeSelectUser localResults id:$id ,checkedUser:${checkedUser.size}")
  }
}

