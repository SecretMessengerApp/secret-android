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
package com.waz.zclient.usersearch

import android.Manifest.permission.READ_CONTACTS
import android.content.{DialogInterface, Intent}
import android.os.Bundle
import android.text.InputType
import android.view._
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.widget.TextView.OnEditorActionListener
import android.widget.{CheckBox, CompoundButton, ImageView, TextView}
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.fragment.SearchFragment
import com.jsy.common.moduleProxy.{ProxyConversationListManagerFragment, ProxyMainActivity}
import com.jsy.res.utils.ViewUtils
import com.waz.content.UserPreferences
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.tracking.TrackingService
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.controllers._
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationManagerFragment
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.participants.dialog.DialogLaunchMode
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.paintcode.ManageServicesIcon
import com.waz.zclient.search.SearchController.SearchUserListState
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views._

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

class SearchUIFragment extends BaseFragment[SearchUIFragment.Container]
  with FragmentHelper
  with SearchUIAdapter.Callback {

  import Threading.Implicits.Ui

  private implicit lazy val uiStorage = inject[UiStorage]

  private implicit def context = getContext

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val self = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)
  private lazy val conversationController = inject[ConversationController]
  private lazy val browser = inject[BrowserController]
  private lazy val convListController = inject[ConversationListController]
  private lazy val keyboard = inject[KeyboardController]
  private lazy val tracking = inject[TrackingService]

  private lazy val pickUserController = inject[IPickUserController]
  private lazy val convScreenController = inject[IConversationScreenController]

  private lazy val shareContactsPref = zms.map(_.userPrefs.preference(UserPreferences.ShareContacts))
  private lazy val showShareContactsPref = zms.map(_.userPrefs.preference(UserPreferences.ShowShareContacts))

  private lazy val adapter = new SearchUIAdapter(this)


  private var searchResultRecyclerView: Option[RecyclerView] = None
  private var startUiToolbar: Option[Toolbar] = None
  private var searchBox: Option[SearchEditText] = None
  private var emptyServicesIcon: Option[ImageView] = None
  private var errorMessageView: Option[TypefaceTextView] = None

  private val searchBoxViewCallback = new SearchEditText.Callback {
    override def onRemovedTokenSpan(element: PickableElement): Unit = {}

    override def onFocusChange(hasFocus: Boolean): Unit = {}

    override def onClearButton(): Unit = closeStartUI()

    override def afterTextChanged(s: String): Unit = searchBox.foreach { v =>
      val filter = v.getSearchFilter
      adapter.filter ! filter
    }


  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0 || getContainer == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (enter)
      new DefaultPageTransitionAnimation(0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_long),
        getInt(R.integer.framework_animation_duration_medium),
        1f)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        1f)
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_pick_user, viewContainer, false)) { rootView =>
      searchResultRecyclerView = Option(ViewUtils.getView(rootView, R.id.rv__pickuser__header_list_view))
      startUiToolbar = Option(ViewUtils.getView(rootView, R.id.pickuser_toolbar))
      searchBox = Option(ViewUtils.getView(rootView, R.id.sbv__search_box))
      errorMessageView = Option(ViewUtils.getView(rootView, R.id.pickuser__error_text))
      emptyServicesIcon = Option(ViewUtils.getView(rootView, R.id.empty_services_icon))
    }

  private var subs = Set.empty[Subscription] //TODO remove subscription...

  override def onViewCreated(rootView: View, savedInstanceState: Bundle): Unit = {
    searchResultRecyclerView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getActivity))
      rv.setAdapter(adapter)
    }

    searchBox.foreach { it =>
      it.setCallback(searchBoxViewCallback)
      it.setInputType(InputType.TYPE_CLASS_TEXT)
      it.applyDarkTheme(true)
      it.setOnEditorActionListener(new OnEditorActionListener {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
          if (actionId == EditorInfo.IME_ACTION_SEARCH) keyboard.hideKeyboardIfVisible() else false
      })
    }

    // Use constant style for left side start ui
    startUiToolbar.foreach { toolbar =>
      toolbar.setVisibility(View.VISIBLE)
    }

    adapter.filter ! ""

    subs += accentColor.onUi(color => searchBox.foreach(_.setCursorColor(color)))

    subs += userAccountsController.currentUser.onChanged.onUi { _ =>
      searchBox.foreach { it =>
        it.postDelayed(new Runnable {
          override def run(): Unit = it.reset()
        }, 600)
      }
    }

    subs += adapter.searchResults.map {
      case SearchUserListState.Users(_) => View.GONE
      case _ => View.VISIBLE
    }.onUi(vis => errorMessageView.foreach(_.setVisibility(vis)))

    subs += (for {
      isAdmin <- userAccountsController.isAdmin
      res <- adapter.searchResults
    } yield res match {
      case SearchUserListState.NoUsers => R.string.new_conv_no_contacts
      case SearchUserListState.NoUsersFound => R.string.new_conv_no_results
      case SearchUserListState.NoServices if isAdmin => R.string.empty_services_list_admin
      case SearchUserListState.NoServices => R.string.empty_services_list
      case SearchUserListState.NoServicesFound => R.string.no_matches_found
      case SearchUserListState.LoadingServices => R.string.loading_services
      case SearchUserListState.Error(_) => R.string.generic_error_header
      case _ => R.string.empty_string //TODO more informative header?
    }).onUi(txt => errorMessageView.foreach(_.setText(txt)))

    subs += adapter.searchResults.map {
      case SearchUserListState.NoServices => View.VISIBLE
      case _ => View.GONE
    }.onUi(vis => emptyServicesIcon.foreach(_.setVisibility(vis)))

    subs += (for {
      kb <- keyboard.isKeyboardVisible
      ac <- accentColor
      filterEmpty = !searchBox.flatMap(v => Option(v.getSearchFilter).map(_.isEmpty)).getOrElse(true)
    } yield if (kb || filterEmpty) getColor(R.color.people_picker__loading__color) else ac)
      .onUi(getContainer.getLoadingViewIndicator.setColor)

    emptyServicesIcon.foreach(_.setImageDrawable(ManageServicesIcon(ResColor.fromId(R.color.white_24))))
  }

  override def onStart(): Unit = {
    super.onStart()
    userAccountsController.isTeam.head.map {
      case true => //
      case _ => showShareContactsDialog()
    }
  }

  override def onResume(): Unit = {
    super.onResume()

    CancellableFuture.delay(getInt(R.integer.people_picker__keyboard__show_delay).millis).map { _ =>

      convListController.establishedConversations.head.map(_.size > SearchUIFragment.SHOW_KEYBOARD_THRESHOLD).flatMap {
        case true =>
          userAccountsController.isTeam.head.map {
            case true =>
              searchBox.foreach { v =>
                v.setFocus()
                keyboard.showKeyboardIfHidden()
              }
            case _ => //
          }
        case _ => Future.successful({})
      }
    }
  }

  override def onPause(): Unit = {
    super.onPause()
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty[Subscription]
    super.onDestroyView()
  }

  override def onBackPressed(): Boolean =
    if(keyboard.hideKeyboardIfVisible()) true
    else if(pickUserController.isShowingUserProfile) {
      pickUserController.hideUserProfile()
      true
    } else if(getFragmentManager.findFragmentByTag(CreateConversationManagerFragment.TAG) != null) {
      Option(getFragmentManager.findFragmentByTag(CreateConversationManagerFragment.TAG))
        .filter(_.isInstanceOf[CreateConversationManagerFragment])
        .map(_.asInstanceOf[CreateConversationManagerFragment]).fold(false)(_.onBackPressed())
    } else if(getFragmentManager.findFragmentByTag(SearchFragment.TAG) != null) {
      Option(getFragmentManager.findFragmentByTag(SearchFragment.TAG))
        .filter(_.isInstanceOf[SearchFragment])
        .map(_.asInstanceOf[SearchFragment]).fold(false)(_.onBackPressed())
    } else false

  override def onUserClicked(userId: UserId): Unit = {
    zms.head.flatMap { z =>
      z.usersStorage.get(userId).map {
        case Some(user) =>
          import ConnectionStatus._
          keyboard.hideKeyboardIfVisible()
          if (user.connection == Accepted || (user.connection == Unconnected && z.teamId.isDefined && z.teamId == user.teamId))
            userAccountsController.getOrCreateAndOpenConvFor(userId)
          else {
            Future {
              user.connection match {
                case PendingFromUser | Blocked | Ignored | Cancelled | Unconnected =>
                  convScreenController.setPopoverLaunchedMode(DialogLaunchMode.SEARCH)
                  pickUserController.showUserProfile(userId, false)
                case ConnectionStatus.PendingFromOther =>
                  getContainer.showIncomingPendingConnectRequest(ConvId(userId.str))
                case _ =>
              }
            }
          }
        case _ =>
      }
    }
  }

  override def onConversationClicked(conversationData: ConversationData): Unit = {
    keyboard.hideKeyboardIfVisible()
    verbose(l"onConversationClicked(${conversationData.id})")
    conversationController.selectConv(Some(conversationData.id), ConversationChangeRequester.START_CONVERSATION)
  }

  /*override def onManageServicesClicked(): Unit = browser.openManageServices()*/

  override def onCreateConvClicked(): Unit = {
    keyboard.hideKeyboardIfVisible()
    fragment.foreach(_.showCreateGroupConversationFragment())
  }


  override def onAddFriendClicked(): Unit = {
    fragment.foreach {
      _.showSearchFragment()
    }
  }

  override def onCreateSendGenericInvitelicked(): Unit = {
    keyboard.hideKeyboardIfVisible()
    self.head.map { self =>
      val sharingIntent = IntentUtils.getInviteIntent(
        getString(R.string.people_picker__invite__share_text__header, self.getDisplayName),
        getString(R.string.people_picker__invite__share_text__body, StringUtils.formatHandle(self.handle.map(_.string).getOrElse(""))))
      startActivity(Intent.createChooser(sharingIntent, getString(R.string.people_picker__invite__share_details_dialog)))
    }
  }

  private def closeStartUI(): Unit = {
    keyboard.hideKeyboardIfVisible()
    adapter.filter ! ""
  }

  // XXX Only show contact sharing dialogs for PERSONAL START UI
  private def showShareContactsDialog(): Unit = {
    (for {
      false <- shareContactsPref.head.flatMap(_.apply())(Threading.Background)
      true <- showShareContactsPref.head.flatMap(_.apply())(Threading.Background)
    } yield {}).map { _ =>
      val checkBoxView = View.inflate(getContext, R.layout.dialog_checkbox, null)
      val checkBox = checkBoxView.findViewById(R.id.checkbox).asInstanceOf[CheckBox]
      var checked = false

      checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit =
          checked = isChecked
      })
      checkBox.setText(R.string.people_picker__share_contacts__nevvah)

      new AlertDialog.Builder(getContext)
        .setTitle(R.string.people_picker__share_contacts__title)
        .setMessage(R.string.people_picker__share_contacts__message)
        .setView(checkBoxView)
        .setPositiveButton(R.string.people_picker__share_contacts__yay,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit =
              inject[PermissionsService].requestAllPermissions(ListSet(READ_CONTACTS)).map { granted =>
                shareContactsPref.head.flatMap(_ := granted)
                if (!granted && !shouldShowRequestPermissionRationale(READ_CONTACTS)) showShareContactsPref.head.flatMap(_ := false)
              }
          })
        .setNegativeButton(R.string.people_picker__share_contacts__nah,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int): Unit =
              if (checked) showShareContactsPref.head.flatMap(_ := false)
          }).create
        .show()
    }
  }

  def activity = if (getActivity != null && getActivity.isInstanceOf[ProxyMainActivity]) Some(getActivity.asInstanceOf[ProxyMainActivity]) else None

  def fragment = {
    if (getParentFragment != null && getParentFragment.isInstanceOf[ProxyConversationListManagerFragment]) {
      Some(getParentFragment.asInstanceOf[ProxyConversationListManagerFragment])
    } else None
  }

}

object TopUsersViewHolder {

}

object SearchUIFragment {
  val TAG: String = classOf[SearchUIFragment].getName
  private val SHOW_KEYBOARD_THRESHOLD: Int = 10

  def newInstance(): SearchUIFragment =
    new SearchUIFragment

  trait Container {
    def showIncomingPendingConnectRequest(conv: ConvId): Unit

    def getLoadingViewIndicator: LoadingIndicatorView
  }

}
