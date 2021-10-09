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
package com.waz.zclient.conversationlist

import android.view.View.OnLongClickListener
import android.view.{View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.conversationlist.ConversationListAdapter._
import com.waz.zclient.conversationlist.ItemBean._
import com.waz.zclient.conversationlist.views.{IncomingConversationListRow2, NormalConversationListRow2, TopStickFoldListRow}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

import scala.collection.mutable.ListBuffer

class ConversationListAdapter(implicit injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ConversationRowViewHolder]
    with Injectable
    with DerivedLogTag {

  setHasStableIds(true)

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val userAccountsController = inject[UserAccountsController]

  val onItemClick = EventStream[(Int, Option[ConvId])]()
  val onConversationLongClick = EventStream[ConversationData]()

  var maxAlpha = 1.0f

  var isScrolling: Boolean = false

  private val showData = ListBuffer[ItemBean]()

  def getData() = showData

  def setData(items: ListBuffer[ItemBean]): Unit = {
    showData.clear()
    showData.appendAll(items)
  }

  def scrollStopRefresh(linearLayoutManager: Some[LinearLayoutManager]): Unit = {
    linearLayoutManager.foreach { layoutManager =>
      val firstV = layoutManager.findFirstVisibleItemPosition()
      val lastV = layoutManager.findLastVisibleItemPosition()
      this.notifyItemRangeChanged(firstV, lastV - firstV + 1)
    }
  }

  private def getItem(position: Int): ItemBean = showData(position)

  override def getItemCount = showData.size

  override def onBindViewHolder(holder: ConversationRowViewHolder, position: Int) = {
    (getItem(position), holder) match {
      case (incomingBean: IncomingBean, incomingConversationRowViewHolder: IncomingConversationRowViewHolder)     =>
        incomingConversationRowViewHolder.bind(incomingBean.convId, incomingBean.userIds)
      case (conversationBean: ConversationBean, normalConversationRowViewHolder: NormalConversationRowViewHolder) =>
        normalConversationRowViewHolder.bind(conversationBean.conversationData, isScrolling)
      case (foldBean: TopStickFoldBean, foldRowViewHolder: TopStickFoldRowViewHolder)                             =>
        foldRowViewHolder.bind(foldBean.count, foldBean.expand)
      case _                                                                                                      =>
        verbose(l"holder = ${holder}, position = ${position}, data = ${getItem(position)}")
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationRowViewHolder = {
    viewType match {
      case NormalViewType =>
        NormalConversationRowViewHolder(returning(ViewHelper.inflate[NormalConversationListRow2](R.layout.normal_conv_list_item, parent, addToParent = false)) { r =>
          r.setAlpha(1f)
          r.setMaxAlpha(maxAlpha)
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = {
              onItemClick ! (NormalViewType, r.conversationData.map(_.id))
            }
          })
          r.setOnLongClickListener(new OnLongClickListener {
            override def onLongClick(view: View): Boolean = {
              r.conversationData.foreach(onConversationLongClick ! _)
              true
            }
          })
          r.setConversationCallback(new ConversationCallback {
            override def onConversationListRowSwiped(convId: String, view: View) =
              r.conversationData.foreach(onConversationLongClick ! _)
          })
        })
      case IncomingViewType =>
        IncomingConversationRowViewHolder(returning(ViewHelper.inflate[IncomingConversationListRow2](R.layout.incoming_conv_list_item, parent, addToParent = false)) { r =>
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = {
              onItemClick ! (IncomingViewType, r.convId)
            }
          })
        })
      case TopStickFoldType =>
        TopStickFoldRowViewHolder(returning(ViewHelper.inflate[TopStickFoldListRow](R.layout.sticktop_fold_conv_list_item, parent, addToParent = false)) { r =>
          r.setOnClickListener(new View.OnClickListener {
            override def onClick(view: View): Unit = {
              onItemClick ! (TopStickFoldType, null)
            }
          })
        })
    }
  }

  override def getItemId(position: Int): Long = showData(position) match {
    case IncomingBean(convId, _)            => convId.map(_.str).getOrElse("").hashCode
    case ConversationBean(conversationData) => conversationData.id.str.hashCode
    case TopStickFoldBean(_, _)             => "TopStickFoldBean".hashCode
  }

  override def getItemViewType(position: Int): Int = {
    showData(position) match {
      case _: IncomingBean     => IncomingViewType
      case _: ConversationBean => NormalViewType
      case _: TopStickFoldBean => TopStickFoldType
    }
  }

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
    notifyDataSetChanged()
  }
}

object ConversationListAdapter {

  val NormalViewType = 0
  val IncomingViewType = 1
  val TopStickFoldType = 2

  trait ListMode {
    val nameId: Int
    val filter: (ConversationData) => Boolean
    val sort = ConversationData.ConversationDataOrdering
  }

  case object GroupOrThousandGroup extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__title
    override val filter = ConversationListController.GroupOrThousandsGroupListFilter
  }

  case object Normal extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__title
    override val filter = ConversationListController.RegularListFilter
  }

  case object Archive extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.ArchivedListFilter
  }

  case object Incoming extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.IncomingListFilter
  }

  case object Integration extends ListMode {
    override lazy val nameId = R.string.conversation_list__header__archive_title
    override val filter = ConversationListController.IntegrationFilter
  }

  trait ConversationRowViewHolder extends RecyclerView.ViewHolder

  case class NormalConversationRowViewHolder(view: NormalConversationListRow2) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(conversation: ConversationData, isScrolling: Boolean = false): Unit =
      view.setConversation(conversation, isScrolling)
  }

  case class IncomingConversationRowViewHolder(view: IncomingConversationListRow2) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(convId: Option[ConvId], userIds: Seq[UserId]): Unit =
      view.setIncomingUsers(convId, userIds)
  }

  case class TopStickFoldRowViewHolder(view: TopStickFoldListRow) extends RecyclerView.ViewHolder(view) with ConversationRowViewHolder {
    def bind(count: Int, expand:Boolean): Unit =
      view.setData(count, expand)
  }
}

sealed trait ItemBean {}

object ItemBean {

  case class IncomingBean(convId: Option[ConvId], userIds: Seq[UserId]) extends ItemBean

  case class ConversationBean(conversationData: ConversationData) extends ItemBean

  case class TopStickFoldBean(count: Int, expand:Boolean) extends ItemBean
}
