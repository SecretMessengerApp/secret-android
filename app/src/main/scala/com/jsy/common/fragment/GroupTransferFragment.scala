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
  * Secret
  * Copyright (C) 2019 Secret
  */
package com.jsy.common.fragment

import android.Manifest
import android.content.{Context, DialogInterface}
import android.os.{Bundle, Handler}
import android.view.View.OnClickListener
import android.view._
import android.widget.{CheckBox, CompoundButton}
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.TransferGroupAdapter
import com.jsy.common.adapter.TransferGroupAdapter.TransferGroupAdapterCallback
import com.jsy.common.listener.{OnSelectUserDataListener, SearchResultTransferGroupOnItemTouchListener}
import com.jsy.common.utils.PermissionUtils
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener
import com.waz.api._
import com.waz.content.{UserPreferences, UsersStorage}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient._
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.controllers.globallayout.KeyboardVisibilityObserver
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.{UiStorage, UserSignal}
import timber.log.Timber

object GroupTransferFragment {
  val TAG: String = classOf[GroupTransferFragment].getName
  val ARGUMENT_ADD_TO_CONVERSATION: String = "ARGUMENT_ADD_TO_CONVERSATION"
  val ARGUMENT_GROUP_CONVERSATION: String = "ARGUMENT_GROUP_CONVERSATION"
  val ARGUMENT_CONVERSATION_ID: String = "ARGUMENT_CONVERSATION_ID"
  val NUM_SEARCH_RESULTS_LIST: Int = 30
  val NUM_SEARCH_RESULTS_TOP_USERS: Int = 24
  val NUM_SEARCH_RESULTS_ADD_TO_CONV: Int = 1000
  private val DEFAULT_SELECTED_INVITE_METHOD: Int = 0
  private val SHOW_KEYBOARD_THRESHOLD: Int = 10

  def newInstance(): GroupTransferFragment = {
    val fragment: GroupTransferFragment = new GroupTransferFragment
    fragment
  }

  trait Container {
    def onSelectedUsers(users: java.util.List[User], requester: ConversationChangeRequester): Unit
  }

}

class GroupTransferFragment extends BaseFragment[GroupTransferFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with KeyboardVisibilityObserver
  with OnBackPressedListener
  with SearchResultTransferGroupOnItemTouchListener.Callback
  with Injectable {

  private var searchResultAdapter: TransferGroupAdapter = _

  private var isKeyboardVisible: Boolean = false
  private var searchBoxIsEmpty: Boolean = true

  private var lastInputIsKeyboardDoneAction: Boolean = false

  private var searchResultRecyclerView: RecyclerView = _
  private var searchBoxView: SearchEditText = _

  private var toastView: View = _
  private var toolbar: Toolbar = _
  private implicit lazy val uiStorage = inject[UiStorage]
  private implicit lazy val context = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val self = zms.flatMap(z => UserSignal(z.selfUserId))
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val convController = inject[ConversationController]
  private lazy val themeController = inject[ThemeController]
  private var conversationData: ConversationData = _
  private var selectUserDataListener: OnSelectUserDataListener = null
  private case class PickableUserContact(userId: UserId, userName: String) extends PickableElement {
    def id: String = userId.str
    def name: String = userName
  }

  def hindSeekBarToast(parentView: ViewGroup): Unit = {
    if (toastView.getParent != null) {
      parentView.removeView(toastView)
    }
  }


  override protected def onPostAttach(context: Context): Unit = {
    super.onPostAttach(context)
    if (context.isInstanceOf[OnSelectUserDataListener]) selectUserDataListener = context.asInstanceOf[OnSelectUserDataListener]
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val rootView: View = inflater.inflate(R.layout.fragment_select_user_to_group_transfer, viewContainer, false)
    Timber.i("[SelectContactsFragment][onCreateView] 0")
    conversationData = convController.currentConv.currentValue.get
    val userIds = participantsController.otherParticipants.map(_.toSeq)
    searchResultAdapter = new TransferGroupAdapter(themeController.isDarkTheme || !isAddingToConversation, new TransferGroupAdapterCallback {
      override def onContactListUserClicked(userData: UserData): Unit = {
        if(null != selectUserDataListener && null != userData){
          selectUserDataListener.onNormalData(userData)
        }
      }
    })
    val users = for {
      usersStorage <- usersStorage
      userIds <- userIds
      users <- usersStorage.listSignal(userIds)
    } yield {
      users.seq
    }

    users.onUi {
      data =>
        val participants = scala.collection.JavaConversions.seqAsJavaList(data)
        searchResultAdapter.setUserData(participants);
    }
    toolbar = ViewUtils.getView(rootView, R.id.group_share_tool)

    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(view: View): Unit = getActivity.finish()
    })


    searchResultRecyclerView = ViewUtils.getView(rootView, R.id.searchResultRecyclerView)
    searchResultRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity))
    searchResultRecyclerView.setAdapter(searchResultAdapter)
    searchResultRecyclerView.addOnItemTouchListener(new SearchResultTransferGroupOnItemTouchListener(getActivity, this))
    if (isAddingToConversation) {
      searchResultRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
          if (newState == RecyclerView.SCROLL_STATE_DRAGGING && getControllerFactory.getGlobalLayoutController.isKeyboardVisible) {
            KeyboardUtils.hideKeyboard(getActivity)
          }
        }
      })
    }
    searchBoxView = ViewUtils.getView(rootView, R.id.searchBoxView)

    //    accentColor.on(Threading.Ui) { color =>
    //      searchBoxView.setCursorColor(color)
    //    }

    Timber.i("[SelectContactsFragment][onCreateView] 3")

    ColorUtils.setBackgroundColor(rootView)
    rootView
  }

  override def onStart(): Unit = {
    super.onStart()
    Timber.i("[SelectContactsFragment][onStart] 0")
    getControllerFactory.getGlobalLayoutController.addKeyboardVisibilityObserver(this)
    Timber.i("[SelectContactsFragment][onStart] 1")

    if (!isAddingToConversation && isPrivateAccount) {
      implicit val ec = Threading.Ui
      zms.head.flatMap(_.userPrefs.preference(UserPreferences.ShareContacts).apply()).map {
        showShareContactsDialog
      }
    }

    Timber.i("[SelectContactsFragment][onStart] 2")
  }

  override def onResume(): Unit = {
    super.onResume()
    Timber.i("[SelectContactsFragment][onResume] 0")
    if (!isAddingToConversation) {
      new Handler().postDelayed(new Runnable() {
        def run(): Unit = {
          if (isTeamAccount) {
            searchBoxView.setFocus()
            KeyboardUtils.showKeyboard(getActivity)
          }
        }
      }, getResources.getInteger(R.integer.people_picker__keyboard__show_delay))
    }
    Timber.i("[SelectContactsFragment][onResume] 1")
  }

  override def onPause(): Unit = {
    super.onPause()
  }

  override def onStop(): Unit = {
    //    getContainer.getLoadingViewIndicator.hide()
    getControllerFactory.getGlobalLayoutController.removeKeyboardVisibilityObserver(this)
    super.onStop()
  }

  override def onDestroyView(): Unit = {
    searchResultRecyclerView = null
    searchBoxView = null
    super.onDestroyView()
  }

  override def onBackPressed: Boolean = {
    if (isKeyboardVisible) {
      KeyboardUtils.hideKeyboard(getActivity)
    }
    else if (getControllerFactory.getPickUserController.isShowingUserProfile) {
      getControllerFactory.getPickUserController.hideUserProfile()
      return true
    }
    isKeyboardVisible
  }

  override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit = {
    isKeyboardVisible = keyboardIsVisible
    if (isAddingToConversation) {
      return
    }
  }

  def onSearchBoxIsEmpty(): Unit = {
    searchBoxIsEmpty = true
    lastInputIsKeyboardDoneAction = false
    //    setConversationQuickMenuVisible(false)
  }

  def onSearchBoxHasNewSearchFilter(filter: String): Unit = {
    searchBoxIsEmpty = filter.isEmpty
    lastInputIsKeyboardDoneAction = false
  }

  override def onUserClicked(userId: UserId, position: Int, anchorView: View): Unit = {

  }

  override def onConversationClicked(conversationData: ConversationData, position: Int): Unit = {
    KeyboardUtils.hideKeyboard(getActivity)
    showToast("onContactListContactClicked")
  }

  private def isAddingToConversation: Boolean = {
    //getArguments.getBoolean(GroupTransferFragment.ARGUMENT_ADD_TO_CONVERSATION)
    false
  }

  private def isPrivateAccount: Boolean = !isTeamAccount

  private def isTeamAccount: Boolean = userAccountsController.isTeam.currentValue.get

  // XXX Only show contact sharing dialogs for PERSONAL START UI
  private def showShareContactsDialog(hasShareContactsEnabled: Boolean): Unit = {
    val prefController = getControllerFactory.getUserPreferencesController
    // Doesn't have _our_ contact sharing setting enabled, maybe show dialog
    if (!hasShareContactsEnabled && !prefController.hasPerformedAction(IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)) {
      // show initial dialog
      val checkBoxView = View.inflate(getContext, R.layout.dialog_checkbox, null)
      val checkBox = checkBoxView.findViewById(R.id.checkbox).asInstanceOf[CheckBox]
      val checkedItems = new java.util.HashSet[Integer]
      checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          if (isChecked)
            checkedItems.add(1)
          else
            checkedItems.remove(1)
        }
      })
      checkBox.setText(R.string.people_picker__share_contacts__nevvah)
      val dialog = new AlertDialog.Builder(getContext).setTitle(R.string.people_picker__share_contacts__title).setMessage(R.string.people_picker__share_contacts__message).setView(checkBoxView).setPositiveButton(R.string.people_picker__share_contacts__yay, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          requestShareContactsPermissions()
        }
      }).setNegativeButton(R.string.people_picker__share_contacts__nah, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          if (getControllerFactory != null && !getControllerFactory.isTornDown && checkedItems.size > 0) getControllerFactory.getUserPreferencesController.setPerformedAction(IUserPreferencesController.DO_NOT_SHOW_SHARE_CONTACTS_DIALOG)
        }
      }).create
      dialog.show()
    }
  }

  private def requestShareContactsPermissions(): Unit = {
    if (getControllerFactory == null || getControllerFactory.isTornDown || userAccountsController.isTeam.currentValue.get) {
      return
    }
    if (PermissionUtils.hasSelfPermissions(getContext, Manifest.permission.READ_CONTACTS)) {
      updateShareContacts(true)
    } else {
      ActivityCompat.requestPermissions(getActivity, Array[String](Manifest.permission.READ_CONTACTS), PermissionUtils.REQUEST_READ_CONTACTS)
    }
  }

  private def updateShareContacts(share: Boolean): Unit = {
    Timber.i("[SelectContactsFragment][updateShareContacts] 0")
    zms.head.flatMap(_.userPrefs.preference(UserPreferences.ShareContacts).update(share))(Threading.Background)
    Timber.i("[SelectContactsFragment][updateShareContacts] 1")
  }

  override def onClick(v: View): Unit = {}

  override def onUserDoubleClicked(userId: UserId, position: Int, anchorView: View): Unit = {}

}
