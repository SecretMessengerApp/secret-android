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
import android.widget.{ImageView, TextView}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.SearchAdapter.TopUsersViewHolder.TopUserAdapter
import com.jsy.res.utils.ViewUtils
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus._
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.common.views.{SingleUserRowView, TopUserChathead}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.SearchResultConversationRowView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

import scala.concurrent.duration._

class SearchAdapter(adapterCallback: SearchAdapter.Callback)(implicit injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[RecyclerView.ViewHolder]
    with Injectable
    with DerivedLogTag {

  import SearchAdapter._

  setHasStableIds(true)


  private lazy val zms = inject[Signal[ZMessaging]]

  def isSameHandle(filter: String, userData: UserData) = userData.handle.exists(_.string == Handle(filter).withSymbol)

  private var mergedResult = Seq[SearchResult]()
  private var collapsedContacts = true
  private var collapsedGroups = true

  private var directoryResults = Seq.empty[UserData]

  val filter = Signal[Handle](Handle(""))

  lazy val searchUser = for {
    sync <- zms.map(_.usersearchSync)
    userStorage <- zms.map(_.usersStorage)

    handle <- filter.throttle(500.millis)
    result <- if (TextUtils.isEmpty(handle.string)) {
      Signal.const((UserId(""), ErrorResponse.Cancelled))
    } else {
      Signal.future(sync.exactMatchHandleReturnUserId(handle))
    }
    user <- if (result._1 != null && !TextUtils.isEmpty(result._1.str)) {
      Signal.future(userStorage.get(result._1))
    } else {
      Signal.const(Option.empty[UserData])
    }
  } yield {
    user.filter { user =>
      user.connection match {
        case Accepted | Blocked | Self =>
          false
        case _ => true
      }
    }
  }

  (for {
    user <- searchUser
  } yield {
    user
  }).onUi { res =>
    directoryResults = res.fold {
      IndexedSeq.empty[UserData]
    } { result => IndexedSeq(result) }
    updateMergedResults()
  }


  private def updateMergedResults(): Unit = {
    mergedResult = Seq()

    def addConnections(): Unit = {
      if (directoryResults.nonEmpty) {
        mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, DirectorySection, 0))
        mergedResult = mergedResult ++ directoryResults.indices.map { i =>
          SearchResult(UnconnectedUser, DirectorySection, i, directoryResults(i).id.str.hashCode)
        }
      }
    }

    def addScanButton(): Unit = mergedResult = mergedResult ++ Seq(SearchResult(Scan, DirectorySection, 0))

    def addSendGenericInvite(): Unit = mergedResult = mergedResult ++ Seq(SearchResult(SendGenericInvite, DirectorySection, 0))

    addScanButton()
    addSendGenericInvite()
    addConnections()

    notifyDataSetChanged()
  }

  override def getItemCount =
    mergedResult.size

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = {
    val item = mergedResult(position)
    item.itemType match {
      case UnconnectedUser =>
        holder.asInstanceOf[UserViewHolder].bind(directoryResults(item.index))

      case SectionHeader =>
        holder.asInstanceOf[SectionHeaderViewHolder].bind(item.section, item.name)
      case _ =>
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(viewType match {
      case UnconnectedUser => R.layout.single_user_row
      case SectionHeader => R.layout.startui_section_header
      case Scan => R.layout.startui_button
      case SendGenericInvite => R.layout.startui_button
      case _ => -1
    }, parent, false)

    viewType match {
      case UnconnectedUser => new UserViewHolder(view.asInstanceOf[SingleUserRowView], adapterCallback)
      case SectionHeader => new SectionHeaderViewHolder(view)
      case SendGenericInvite => new CreateSendGenericInviteViewHolder(view, adapterCallback)
      case Scan => new ScanButtonViewHolder(view, adapterCallback)
      case _ => null
    }
  }

  override def getItemViewType(position: Int) =
    mergedResult.lift(position).fold(-1)(_.itemType)

  override def getItemId(position: Int) =
    mergedResult.lift(position).fold(-1L)(_.id)

  def getSectionIndexForPosition(position: Int) =
    mergedResult.lift(position).fold(-1)(_.index)

  private def expandContacts() = {
    collapsedContacts = false
    updateMergedResults()
  }

  private def expandGroups() = {
    collapsedGroups = false
    updateMergedResults()
  }
}

object SearchAdapter {

  //Item Types
  val UnconnectedUser: Int = 2
  val SectionHeader: Int = 4
  val SendGenericInvite: Int = 9
  val Scan: Int = 10

  //Sections
  val DirectorySection = 3

  trait Callback {
    def onUserClicked(userId: UserId): Unit

    /*def onIntegrationClicked(data: IntegrationData): Unit*/
    def onCreateConvClicked(): Unit

    def onScanClicked(): Unit

    def onCreateSendGenericInvitelicked(): Unit

    /*def onCreateGuestRoomClicked(): Unit*/
    def onConversationClicked(conversation: ConversationData): Unit

    /*def onManageServicesClicked(): Unit*/
  }

  case class SearchResult(itemType: Int, section: Int, index: Int, id: Long, name: Name)

  object SearchResult {
    def apply(itemType: Int, section: Int, index: Int, id: Long): SearchResult = new SearchResult(itemType, section, index, id, Name.Empty)

    def apply(itemType: Int, section: Int, index: Int, name: Name): SearchResult = new SearchResult(itemType, section, index, itemType + section + index, name)

    def apply(itemType: Int, section: Int, index: Int): SearchResult = SearchResult(itemType, section, index, Name.Empty)
  }

  class CreateConversationButtonViewHolder(view: View, callback: SearchAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_create_conversation)
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.create_group_conversation)
    view.onClick(callback.onCreateConvClicked())
    view.setId(R.id.create_group_button)
  }

  class ScanButtonViewHolder(view: View, callback: SearchAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_scan)
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.conversation_top_menu_scan)
    view.onClick(callback.onScanClicked())
    view.setId(R.id.scan_button)
  }

  class CreateSendGenericInviteViewHolder(view: View, callback: SearchAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_contacts)
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.conversation_mobile_contacts)
    view.onClick(callback.onCreateSendGenericInvitelicked())
    view.setId(R.id.invite_contacts_button)
  }

  class TopUsersViewHolder(view: View, topUserAdapter: TopUserAdapter, context: Context) extends RecyclerView.ViewHolder(view) {

    val topUsersRecyclerView = ViewUtils.getView[RecyclerView](view, R.id.rv_top_users)
    val layoutManager = new LinearLayoutManager(context)
    layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL)
    topUsersRecyclerView.setLayoutManager(layoutManager)
    topUsersRecyclerView.setHasFixedSize(false)
    topUsersRecyclerView.setAdapter(this.topUserAdapter)

    def bind(users: Seq[UserData]): Unit = topUserAdapter.setTopUsers(users)
  }

  object TopUsersViewHolder {

    class TopUserAdapter(callback: Callback) extends RecyclerView.Adapter[TopUserViewHolder] {
      private var topUsers = Seq[UserData]()

      def onCreateViewHolder(parent: ViewGroup, viewType: Int): TopUserViewHolder =
        new TopUserViewHolder(LayoutInflater.from(parent.getContext).inflate(R.layout.startui_top_user, parent, false).asInstanceOf[TopUserChathead], callback)

      def onBindViewHolder(holder: TopUserViewHolder, position: Int): Unit = holder.bind(topUsers(position))

      def getItemCount: Int = topUsers.length

      def setTopUsers(topUsers: Seq[UserData]): Unit = {
        this.topUsers = topUsers
        notifyDataSetChanged()
      }

      def reset(): Unit = topUsers = Seq()
    }

    class TopUserViewHolder(view: TopUserChathead, callback: Callback) extends RecyclerView.ViewHolder(view) {
      private var user = Option.empty[UserData]

      view.onClick(user.map(_.id).foreach(callback.onUserClicked))

      def bind(user: UserData): Unit = {
        this.user = Some(user)
        view.setUserTextColor(R.color.black);
        view.setUser(user)
      }
    }

  }

  class UserViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {

    private var userData = Option.empty[UserData]
    view.onClick(userData.map(_.id).foreach(callback.onUserClicked))
    view.showArrow(false)
    view.showCheckbox(false)

    def bind(userData: UserData, teamId: Option[TeamId] = None): Unit = {
      this.userData = Some(userData)
      view.setUserData(userData)
    }
  }

  class ConversationViewHolder(view: View, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private val conversationRowView = ViewUtils.getView[SearchResultConversationRowView](view, R.id.srcrv_startui_conversation)
    private var conv = Option.empty[ConversationData]

    view.onClick(conv.foreach(callback.onConversationClicked))

    def bind(conversationData: ConversationData): Unit = {
      conv = Some(conversationData)
      conversationRowView.setConversation(conversationData)
    }
  }


  class SectionExpanderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val viewAllTextView = ViewUtils.getView[TypefaceTextView](view, R.id.ttv_startui_section_header)

    def bind(itemCount: Int, clickListener: View.OnClickListener): Unit = {
      val title = getString(R.string.people_picker__search_result__expander_title, Integer.toString(itemCount))(view.getContext)
      viewAllTextView.setText(title)
      viewAllTextView.setOnClickListener(clickListener)
    }
  }

  class SectionHeaderViewHolder(val view: View) extends RecyclerView.ViewHolder(view) {
    private val sectionHeaderView: TextView = ViewUtils.getView(view, R.id.ttv_startui_section_header)
    private implicit val context = sectionHeaderView.getContext

    def bind(section: Int, teamName: Name): Unit = {
      val title = section match {
        case DirectorySection => getString(R.string.people_picker__search_result_others_header_title)
      }
      sectionHeaderView.setText(title)
    }
  }

}
