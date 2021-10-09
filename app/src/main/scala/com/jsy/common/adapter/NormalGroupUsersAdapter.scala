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
package com.waz.zclient.participants

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jsy.res.utils.ViewUtils
import com.waz.api.{IConversation, Verification}
import com.waz.content.UsersStorage
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, _}
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.utils._
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._

class NormalGroupUsersAdapter(userIds: Signal[Seq[UserId]],
                              maxParticipants: Option[Int] = None,
                              showPeopleOnly: Boolean = false,
                              showArrow: Boolean = false,
                              allwaysShowCreatorAndSelf: Boolean = false,
                              createSubtitle: Option[(UserData) => String] = None
                             )(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {

  implicit val uiStorage = inject[UiStorage]

  import NormalGroupUsersAdapter._

  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private lazy val convController = inject[ConversationController]
  private lazy val themeController = inject[ThemeController]

  private var items = List.empty[Either[GroupUsersData, Int]]
  private var convName = Option.empty[String]
  private var readReceiptsEnabled = false
  private var convVerified = false
  private var peopleCount = 0

  private var selfId: UserId = _
  private var creatorId: UserId = _

  private var selfData: UserData = _
  private var creatorData: UserData = _

  val onClick = EventStream[UserId]()
  val filter = Signal("")

  lazy val zms = inject[Signal[ZMessaging]]

  val creatorDataSignal = for {
//    z <- zms
    creatorId <- convController.currentConv.map(_.creator)
    creatorData <- UserSignal(creatorId)
  } yield creatorData

  lazy val users = for {
    usersStorage <- usersStorage
    userIds <- userIds
    users <- usersStorage.listSignal(userIds)
    f <- filter
    fu = users.filter(_.matchesFilter(f))
    creatorId <- convController.currentConv.map(_.creator)
    creatorData <- creatorDataSignal
    selfId <- currentUser
    selfData <- UserSignal(selfId)
  } yield {
    this.selfData = selfData
    this.creatorData = creatorData
    this.selfId = selfId
    this.creatorId = creatorId
    //users.map(u => ParticipantData(u)).sortBy(_.userData.getDisplayName.str)
    val result = if (allwaysShowCreatorAndSelf) {
      val filteredUsers = fu.filter { user =>
        user.id != selfId && user.id != creatorId
      }
      if (selfId == creatorId) {
        GroupUsersData(creatorData) :: filteredUsers.map { u => GroupUsersData(u /*, u.isGuest(tId) && !u.isWireBot*/) }.sortBy(_.userData.getDisplayName.str).toList
      } else {
        GroupUsersData(creatorData) :: GroupUsersData(selfData) :: filteredUsers.map { u => GroupUsersData(u /*, u.isGuest(tId) && !u.isWireBot*/) }.sortBy(_.userData.getDisplayName.str).toList
      }
    } else {
      fu.map { u => GroupUsersData(u /*, u.isGuest(tId) && !u.isWireBot*/) }.sortBy(_.userData.getDisplayName.str).toList
    }
    result
  }

  private lazy val positions = for {
    users <- users
    convType <- conv.map(_.convType)
    memsum <- conv.map(_.memsum)
  } yield {
    if (convType == IConversation.Type.THROUSANDS_GROUP) {
      peopleCount = memsum.getOrElse(users.size)
    } else {
      peopleCount = users.size
    }

    val filteredPeople = users.take(maxParticipants.getOrElse(Integer.MAX_VALUE))
    filteredPeople.map(data => Left(data))
  }

  positions.onUi { list =>
    items = list
    notifyDataSetChanged()
  }

  private val conv = convController.currentConv

  (for {
    name <- conv.map(_.displayName)
    ver <- conv.map(_.verified == Verification.VERIFIED)
    read <- conv.map(_.readReceiptsAllowed)
    clock <- ClockSignal(5.seconds)
  } yield (name, ver, read, clock)).onUi {
    case (name, ver, read, _) =>
      convName = Some(name)
      convVerified = ver
      readReceiptsEnabled = read
      notifyDataSetChanged()
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(showArrow)
      GroupUsersViewHolder(view, onClick)
    case _ => SeparatorViewHolder(getSeparatorView(parent))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items(position), holder) match {
    case (Left(userData), h: GroupUsersViewHolder) =>
      h.bind(userData, /*teamId,*/ maxParticipants.forall(peopleCount <= _) && items.lift(position + 1).forall(_.isRight), showCreatorOrSelf = true, creatorId = creatorId, selfId = selfId, createSubtitle = createSubtitle)
    case _ =>
  }

  override def getItemCount: Int = items.size

  override def getItemId(position: Int): Long = items(position) match {
    case Left(user) => user.userData.id.hashCode()
    case Right(sepType) => sepType
  }

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int = items(position) match {
    case Right(sepType) => sepType
    case _ => UserRow
  }

  private def getSeparatorView(parent: ViewGroup): View =
    LayoutInflater.from(parent.getContext).inflate(R.layout.participants_separator_row, parent, false)

}

object NormalGroupUsersAdapter {

  val UserRow = 0

  case class GroupUsersData(userData: UserData /*, isGuest: Boolean*/)


  case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)

    def setTitle(title: String) = textView.setText(title)

    def setId(id: Int) = textView.setId(id)
  }

  case class GroupUsersViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId]) extends ViewHolder(view) {

    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(groupUsersData: GroupUsersData, /*teamId: Option[TeamId],*/ lastRow: Boolean, showCreatorOrSelf: Boolean = false, creatorId: UserId = null, selfId: UserId = null, createSubtitle: Option[(UserData) => String]): Unit = {
      userId = Some(groupUsersData.userData.id)
      createSubtitle match {
        case Some(f) => view.setUserData(groupUsersData.userData, /*teamId,*/ showCreatorOrSelf = showCreatorOrSelf, creatorId = creatorId, selfId = selfId, isGroupManager = false, createSubtitle = f)
        case None => view.setUserData(groupUsersData.userData, /*teamId*/ showCreatorOrSelf = showCreatorOrSelf, creatorId = creatorId, selfId = selfId, isGroupManager = false)
      }
      view.setSeparatorVisible(!lastRow)
    }
  }

}
