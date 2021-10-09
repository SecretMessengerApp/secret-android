/**
 * Secret
 * Copyright (C) 2019 Secret
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
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.jsy.res.utils.ViewUtils
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events._
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{Injectable, Injector, R}

import java.util

/**
  * Copy from [[com.waz.zclient.participants.ParticipantsAdapter]]
  *
  * @param userIds
  * @param showArrow
  * @param allwaysShowCreatorAndSelf
  * @param createSubtitle
  * @param context
  * @param injector
  * @param eventContext
  */
class ParticipantsLikesAdapter(userIds: Signal[Seq[UserId]],
                               showArrow: Boolean = true,
                               allwaysShowCreatorAndSelf: Boolean = false,
                               createSubtitle: Option[(UserData) => String] = None
                              )(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable with DerivedLogTag{

  import ParticipantsLikesAdapter._

  implicit val uiStorage = inject[UiStorage]

  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private lazy val convController = inject[ConversationController]
  private lazy val themeController = inject[ThemeController]
  private lazy val accountsController = inject[UserAccountsController]

  private val items: java.util.List[Either[UserData, Int]] = new util.ArrayList[Either[UserData, Int]]()

  private var selfId: UserId = _
  private var creatorId: UserId = _
  private var selfData: UserData = _
  private var creatorData: UserData = _

  val onClick = EventStream[UserId]()

  (for {
    usersStorage <- usersStorage
    userIds <- userIds
    users <- usersStorage.listSignal(userIds)
    creatorId <- convController.currentConv.map(_.creator)
    creatorData <- UserSignal(creatorId)
    selfId <- currentUser
    selfData <- UserSignal(selfId)
  } yield {
    this.selfData = selfData
    this.creatorData = creatorData
    this.selfId = selfId
    this.creatorId = creatorId
    users.sortBy(_.getDisplayName.str).map(Left(_)).toList ::: List(Right(users.size))
  }).onUi { list =>
    items.clear()
    list.foreach(userData =>
      items.add(userData)
    )
    notifyDataSetChanged()
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(showArrow)
      ParticipantRowViewHolder(view, onClick)
    case _ =>
      SeparatorViewHolder(getSeparatorView(parent))
  }

  def isRight(position: Int) = items.get(position) match {
    case Left(_) => false
    case Right(_) => true
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items.get(position), holder) match {
    case (Left(userData), h: ParticipantRowViewHolder) =>
      h.bind(userData, lastRow = isRight(position), showCreatorOrSelf = true, creatorId = creatorId, selfId = selfId, createSubtitle = createSubtitle,
        if (allwaysShowCreatorAndSelf) convController.currentUserIsGroupManager(userData.id).currentValue.get else false)
    case (Right(sepType), h: SeparatorViewHolder) if Set(PeopleSeparator).contains(sepType) =>
      val count = if (sepType == PeopleSeparator) items.size else 0 /*botCount*/
      h.setTitle(getString(R.string.participants_divider_people, count.toString))
      h.setId(R.id.participants_section)
  }

  override def getItemCount: Int = if (items == null) 0 else items.size()

  override def getItemId(position: Int): Long = position

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int = items.get(position) match {
    case Right(sepType) =>
      sepType
    case _ =>
      UserRow
  }

  private def getSeparatorView(parent: ViewGroup): View =
    LayoutInflater.from(parent.getContext).inflate(R.layout.participants_separator_row, parent, false)

}


object ParticipantsLikesAdapter {
  val UserRow = 0
  val PeopleSeparator = 1

  case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)

    def setTitle(title: String) = textView.setText(title)

    def setId(id: Int) = textView.setId(id)
  }

  case class ParticipantRowViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId]) extends ViewHolder(view) {

    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(userData: UserData, lastRow: Boolean, showCreatorOrSelf: Boolean = false, creatorId: UserId = null, selfId: UserId = null, createSubtitle: Option[(UserData) => String], isGroupManager: Boolean = false): Unit = {
      userId = Some(userData.id)
      createSubtitle match {
        case Some(f) => view.setUserData(userData, showCreatorOrSelf = showCreatorOrSelf, creatorId = creatorId, selfId = selfId, isGroupManager, createSubtitle = f)
        case None => view.setUserData(userData, showCreatorOrSelf = showCreatorOrSelf, creatorId = creatorId, selfId = selfId, isGroupManager)
      }
      view.setSeparatorVisible(!lastRow)
    }
  }

}
