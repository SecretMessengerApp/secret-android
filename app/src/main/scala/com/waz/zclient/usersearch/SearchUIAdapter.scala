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
package com.waz.zclient.usersearch

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, TextView}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.res.utils.ViewUtils
import com.waz.api.User.ConnectionStatus
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.utils.events.EventContext
import com.waz.zclient._
import com.waz.zclient.common.controllers.ThemeController.Theme
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.{SingleUserRowView, TopUserChathead}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.search.SearchController
import com.waz.zclient.search.SearchController.SearchUserListState
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.SearchUIAdapter.TopUsersViewHolder.TopUserAdapter
import com.waz.zclient.usersearch.views.SearchResultConversationRowView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._

class SearchUIAdapter(adapterCallback: SearchUIAdapter.Callback)(implicit injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[RecyclerView.ViewHolder]
    with Injectable
    with DerivedLogTag {

  import SearchUIAdapter._

  setHasStableIds(true)

  private val userAccountsController = inject[UserAccountsController]
  private val searchController = new SearchController()

  private var mergedResult = Seq[SearchResult]()
  addFriendButton()
  addGroupCreationButton()
  addSendGenericInvite()
  notifyDataSetChanged()

  private var collapsedContacts = true
  private var collapsedGroups = true

  private var team = Option.empty[TeamData]
  private var topUsers = Seq.empty[UserData]
  private var localResults = Seq.empty[UserData]
  private var conversations = Seq.empty[ConversationData]
  private var directoryResults = Seq.empty[UserData]
  private var integrations = Seq.empty[IntegrationData]

  val filter = searchController.filter
  /*val tab    = searchController.tab*/
  val searchResults = searchController.searchUserOrServices

  (for {
    //curUser <- userAccountsController.currentUser
    res <- searchResults
  } yield res).onUi {
    case res =>
      verbose(l"Search user list state: ???")
      res match {
        case SearchUserListState.Users(search) =>
          topUsers = search.top
          localResults = search.local.filter(_.connection != ConnectionStatus.BLOCKED).filter(_.nature.getOrElse(NatureTypes.Type_Normal) == NatureTypes.Type_Normal)
          conversations = search.convs
          directoryResults = search.dir
        case _ =>
          topUsers = Seq.empty
          localResults = Seq.empty
          conversations = Seq.empty
          directoryResults = Seq.empty
      }
      updateMergedResults()
    case _=>
  }


  def addTopPeople(): Unit = {
    if (topUsers.nonEmpty) {
      mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, TopUsersSection, 0))
      mergedResult = mergedResult ++ Seq(SearchResult(TopUsers, TopUsersSection, 0))
    }
  }

  def addContacts(): Unit = {
    if (localResults.nonEmpty) {
      mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, ContactsSection, 0 /*, teamName*/))
      var contactsSection = Seq[SearchResult]()

      contactsSection = contactsSection ++ localResults.indices.map { i =>
        SearchResult(ConnectedUser, ContactsSection, i, localResults(i).id.str.hashCode, localResults(i).getDisplayName)
      }

      val shouldCollapse = filter.currentValue.exists(_.nonEmpty) && collapsedContacts && contactsSection.size > CollapsedContacts

      contactsSection = contactsSection.sortBy(_.name.str).take(if (shouldCollapse) CollapsedContacts else contactsSection.size)

      mergedResult = mergedResult ++ contactsSection
      if (shouldCollapse) {
        mergedResult = mergedResult ++ Seq(SearchResult(Expand, ContactsSection, 0))
      }
    }
  }

  private def addGroupConversations(): Unit = {
    if (conversations.nonEmpty) {
      mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, GroupConversationsSection, 0 /*, teamName*/))

      val shouldCollapse = collapsedGroups && conversations.size > CollapsedGroups

      mergedResult = mergedResult ++ conversations.indices.map { i =>
        SearchResult(GroupConversation, GroupConversationsSection, i, conversations(i).id.str.hashCode)
      }.take(if (shouldCollapse) CollapsedGroups else conversations.size)
      if (shouldCollapse) {
        mergedResult = mergedResult ++ Seq(SearchResult(Expand, GroupConversationsSection, 0))
      }
    }
  }

  private def addConnections(): Unit = {
    if (directoryResults.nonEmpty) {
      mergedResult = mergedResult ++ Seq(SearchResult(SectionHeader, DirectorySection, 0))
      mergedResult = mergedResult ++ directoryResults.indices.map { i =>
        SearchResult(UnconnectedUser, DirectorySection, i, directoryResults(i).id.str.hashCode)
      }
    }
  }

  private def addFriendButton(): Unit =
    mergedResult = mergedResult ++ Seq(SearchResult(AddFriend, TopUsersSection, 0))

  private def addGroupCreationButton(): Unit =
    mergedResult = mergedResult ++ Seq(SearchResult(NewConversation, TopUsersSection, 0))

  private def addSendGenericInvite(): Unit =
    mergedResult = mergedResult ++ Seq(SearchResult(SendGenericInvite, TopUsersSection, 0))

  private def updateMergedResults(): Unit = {
    mergedResult = Seq()
    addFriendButton()
    addGroupCreationButton()
    addSendGenericInvite()
    addTopPeople()
    addConnections()
    addContacts()
    addGroupConversations()
    notifyDataSetChanged()
  }

  override def getItemCount =
    mergedResult.size

  override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = {
    val item = mergedResult(position)
    item.itemType match {
      case TopUsers =>
        holder.asInstanceOf[TopUsersViewHolder].bind(topUsers)

      case GroupConversation =>
        holder.asInstanceOf[ConversationViewHolder].bind(conversations(item.index))

      case ConnectedUser =>
        val user = localResults(item.index)
        holder.asInstanceOf[UserViewHolder].bind(user /*team.map(_.id)*/)

      case UnconnectedUser =>
        holder.asInstanceOf[UserViewHolder].bind(directoryResults(item.index))

      case SectionHeader =>
        holder.asInstanceOf[SectionHeaderViewHolder].bind(item.section, item.name)

      case Expand =>
        val itemCount = if (item.section == ContactsSection) localResults.size else conversations.size
        holder.asInstanceOf[SectionExpanderViewHolder].bind(itemCount, new View.OnClickListener() {
          def onClick(v: View): Unit = {
            if (item.section == ContactsSection) expandContacts() else expandGroups()
          }
        })
      /*
    case Integration =>
      holder.asInstanceOf[IntegrationViewHolder].bind(integrations(item.index))*/

      case _ =>
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
    val view = LayoutInflater.from(parent.getContext).inflate(viewType match {
      case TopUsers          => R.layout.startui_top_users
      case ConnectedUser |
           UnconnectedUser
        /*|Integration*/     => R.layout.single_user_row
      case GroupConversation => R.layout.startui_conversation
      case SectionHeader     => R.layout.startui_section_header
      case Expand            => R.layout.startui_section_expander
      case NewConversation   => R.layout.startui_button
      case NewGuestRoom      => R.layout.startui_button
      /*case ManageServices    => R.layout.startui_button*/
      case SendGenericInvite => R.layout.startui_button
      case AddFriend         => R.layout.startui_button
      case _                        => -1
    }, parent, false)

    viewType match {
      case TopUsers          => new TopUsersViewHolder(view, new TopUserAdapter(adapterCallback), parent.getContext)
      case ConnectedUser |
           UnconnectedUser   => new UserViewHolder(view.asInstanceOf[SingleUserRowView], adapterCallback)
      case GroupConversation => new ConversationViewHolder(view, adapterCallback)
      case SectionHeader     => new SectionHeaderViewHolder(view)
      case Expand            => new SectionExpanderViewHolder(view)
      /*case Integration       => new IntegrationViewHolder(view.asInstanceOf[SingleUserRowView], adapterCallback)*/
      case NewConversation => new CreateConversationButtonViewHolder(view, adapterCallback)
      /*case NewGuestRoom      => new NewGuestRoomViewHolder(view, adapterCallback)*/
      /*case ManageServices    => new ManageServicesViewHolder(view, adapterCallback)*/
      case SendGenericInvite => new CreateSendGenericInviteViewHolder(view, adapterCallback)
      case AddFriend         => new AddFriendButtonViewHolder(view, adapterCallback)
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

object SearchUIAdapter {

  //Item Types
  val TopUsers: Int = 0
  val ConnectedUser: Int = 1
  val UnconnectedUser: Int = 2
  val GroupConversation: Int = 3
  val SectionHeader: Int = 4
  val Expand: Int = 5
  /*val Integration: Int = 6*/
  val NewConversation: Int = 7
  val NewGuestRoom: Int = 8
  /*val ManageServices: Int = 9*/
  val SendGenericInvite: Int = 9
  val AddFriend: Int = 10

  //Sections
  val TopUsersSection = 0
  val GroupConversationsSection = 1
  val ContactsSection = 2
  val DirectorySection = 3
  val IntegrationsSection = 4

  //Constants
  val CollapsedContacts = 5
  val CollapsedGroups = 5

  trait Callback {
    def onUserClicked(userId: UserId): Unit

    /*def onIntegrationClicked(data: IntegrationData): Unit*/
    def onCreateConvClicked(): Unit

    def onAddFriendClicked(): Unit

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

  class CreateConversationButtonViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_create_conversation)
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.create_group_conversation)
    view.onClick(callback.onCreateConvClicked())
    view.setId(R.id.create_group_button)
  }

  class AddFriendButtonViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_add_friend)


    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.conversation_top_menu_add_friend)
    view.onClick(callback.onAddFriendClicked())
    view.setId(R.id.add_friend_button)
  }

  class CreateSendGenericInviteViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
    private implicit val ctx = view.getContext
    private val iconView = view.findViewById[ImageView](R.id.icon)
    iconView.setImageResource(R.drawable.ico_search_contacts)
    view.findViewById[TypefaceTextView](R.id.title).setText(R.string.pref_invite_title)
    view.onClick(callback.onCreateSendGenericInvitelicked())
    view.setId(R.id.invite_contacts_button)
  }

  /*
    class NewGuestRoomViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
      private implicit val ctx = view.getContext
      private val iconView  = view.findViewById[View](R.id.icon)
      iconView.setBackground(returning(GuestIcon(R.color.white))(_.setPadding(new Rect(iconView.getPaddingLeft, iconView.getPaddingTop, iconView.getPaddingRight, iconView.getPaddingBottom))))
      view.findViewById[TypefaceTextView](R.id.title).setText(R.string.create_guest_room_conversation)
      view.onClick(callback.onCreateGuestRoomClicked())
      view.setId(R.id.create_guest_room_button)
    }

    class ManageServicesViewHolder(view: View, callback: SearchUIAdapter.Callback) extends RecyclerView.ViewHolder(view) {
      private implicit val ctx = view.getContext
      private val iconView  = view.findViewById[View](R.id.icon)
      iconView.setBackground(returning(ManageServicesIcon(ResColor.fromId(R.color.black))) {
        _.setPadding(new Rect(iconView.getPaddingLeft, iconView.getPaddingTop, iconView.getPaddingRight, iconView.getPaddingBottom))
      })
      view.findViewById[TypefaceTextView](R.id.title).setText(R.string.manage_services)
      view.onClick(callback.onManageServicesClicked())
      view.setId(R.id.manage_services_button)
    }*/

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
    //    conversationRowView.applyDarkTheme()

    def bind(conversationData: ConversationData): Unit = {
      conv = Some(conversationData)
      conversationRowView.setConversation(conversationData)
    }
  }

  /*
  class IntegrationViewHolder(view: SingleUserRowView, callback: Callback) extends RecyclerView.ViewHolder(view) {
    private var integrationData: Option[IntegrationData] = None

    view.onClick(integrationData.foreach(i => callback.onIntegrationClicked(i)))
    view.showArrow(false)
    view.showCheckbox(false)
    view.setTheme(Theme.Dark, background = false)
    view.setSeparatorVisible(true)

    def bind(integrationData: IntegrationData): Unit = {
      this.integrationData = Some(integrationData)
      view.setIntegration(integrationData)
    }
  }
  */

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
        case TopUsersSection => getString(R.string.people_picker__top_users_header_title)
        case GroupConversationsSection if teamName.isEmpty => getString(R.string.people_picker__search_result_conversations_header_title)
        case GroupConversationsSection => getString(R.string.people_picker__search_result_team_conversations_header_title, teamName)
        case ContactsSection => getString(R.string.people_picker__search_result_connections_header_title)
        case DirectorySection => getString(R.string.people_picker__search_result_others_header_title)
        case IntegrationsSection => getString(R.string.integrations_picker__section_title)
      }
      sectionHeaderView.setText(title)
    }
  }
}

