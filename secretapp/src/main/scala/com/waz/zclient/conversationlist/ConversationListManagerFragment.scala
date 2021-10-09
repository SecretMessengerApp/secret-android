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

import android.content.{Context, Intent}
import android.os.Bundle
import android.text.TextUtils
import android.view.inputmethod.InputMethodManager
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{FrameLayout, PopupWindow, RelativeLayout}
import androidx.annotation.NonNull
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.{Fragment, FragmentManager}
import com.jsy.common.fragment.{PreferencesUserFragment, SearchFragment}
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.listener.{OnPopMenuDismissListener, OnPopMenuItemClick}
import com.jsy.common.model.HttpResponseBaseModel
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.common.moduleProxy.{ProxyConversationListManagerFragment, ProxyConversationListManagerFragmentObject}
import com.jsy.common.popup.JoinGroupPopUpWindow.JoinGroupAllowCallBack
import com.jsy.common.popup.{JoinGroupPopUpWindow, TabMenuPopupWindow}
import com.jsy.common.utils.{MainHandler, PopConvMgrTabMenuUtil2, SoftInputUtils}
import com.jsy.common.views.CircleImageView
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.interfaces.OnBackPressedListener
import com.waz.api.SyncState._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.model.otr.Client
import com.waz.model.sync.SyncCommand._
import com.waz.service.ZMessaging
import com.waz.service.tracking.GroupConversationEvent
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.common.controllers.{SoundController, ThemeController, UserAccountsController}
import com.waz.zclient.connect.{PendingConnectRequestManagerFragment, SendConnectRequestFragment}
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.IConfirmationController
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{CreateConversationController, CreateConversationManagerFragment}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.pages.main.conversation.controller.{ConversationScreenControllerObserver, IConversationScreenController}
import com.waz.zclient.pages.main.conversationlist.views.ListActionsView
import com.waz.zclient.pages.main.pickuser.controller.{IPickUserController, PickUserControllerScreenObserver}
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants.{ConversationOptionsMenuController, OptionsMenu, UserRequester}
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.usersearch.SearchUIFragment
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils.{IntentUtils, MainActivityUtils, RichView, SpUtils, StringUtils}
import com.waz.zclient.views.LoadingIndicatorView
import com.waz.zclient.views.menus.ConfirmationMenu
import com.waz.zclient.{Constants, FragmentHelper, R}
import org.json.JSONObject

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Success, Try}

class ConversationListManagerFragment extends Fragment
  with FragmentHelper
  with PickUserControllerScreenObserver
  with SearchUIFragment.Container
  with NavigationControllerObserver
  with ConversationListFragment.Container
  with ConversationScreenControllerObserver
  with SendConnectRequestFragment.Container
  with BlockedUserProfileFragment.Container
  with OnBackPressedListener
  with ProxyConversationListManagerFragment
  with PendingConnectRequestManagerFragment.Container
  with DerivedLogTag {

  import ConversationListManagerFragment._
  import Threading.Implicits.Background

  implicit lazy val context = getContext

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val userAccounts = inject[UserAccountsController]
  lazy val users = inject[UsersController]
  lazy val themes = inject[ThemeController]
  lazy val sounds = inject[SoundController]
  lazy val convListController = inject[ConversationListController]
  //   lazy val devicesDialogController = inject[DevicesDialogController]

  private lazy val convController = inject[ConversationController]
  private lazy val accentColor = inject[AccentColorController]
  private lazy val pickUserController = inject[IPickUserController]
  private lazy val navController = inject[INavigationController]
  private lazy val convScreenController = inject[IConversationScreenController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val cameraController = inject[ICameraController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val KeyboardController = inject[KeyboardController]

  private var subs = Set.empty[Subscription]

  private var popConvMgrTabMenuUtil: PopConvMgrTabMenuUtil2 = _
  private val tabListMenuModels: java.util.List[TabListMenuModel] = new util.ArrayList[TabListMenuModel]()

  private var startUiLoadingIndicator: LoadingIndicatorView = _
  private var listLoadingIndicator: LoadingIndicatorView = _
  private var rlFragParent: RelativeLayout = _
  private var mainContainer: FrameLayout = _
  private var confirmationMenu: ConfirmationMenu = _
  private var status = ListActionsView.STATE_AVATAR
  private var statusIgnoreGroup = ListActionsView.STATE_AVATAR
  private val fragmentMap: java.util.HashMap[String, Fragment] = new java.util.HashMap[String, Fragment]
  private val fragStrTagMap: java.util.HashMap[Integer, String] = new java.util.HashMap[Integer, String]
  private var listActionsView: ListActionsView = _
  private var mDrawerView: DrawerLayout = _
  private var drawStatus = DrawerLayout.LOCK_MODE_LOCKED_CLOSED

  private var lastFragment: Fragment = null

  lazy val hasConvs = convListController.establishedConversations.map(_.nonEmpty)

  lazy val animationType = {
    import LoadingIndicatorView._
    hasConvs.map {
      case true => InfiniteLoadingBar
      case _ => Spinner
    }
  }
  /*lazy val archiveEnabled = hasConversationsAndArchive.map(_._2)*/

  private def stripToConversationList() = {
    pickUserController.hideUserProfile() // Hide possibly open self profile
  }

  private def animateOnIncomingCall() = {
    Option(getView).foreach {
      _.animate
        .alpha(0)
        .setInterpolator(new Quart.EaseOut)
        .setDuration(getInt(R.integer.calling_animation_duration_medium))
        .start()
    }

    CancellableFuture.delay(getInt(R.integer.calling_animation_duration_long).millis).map { _ =>
      Option(getView).foreach(_.setAlpha(1))
    }
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putString(INTENT_KEY_lastFragmentTag, lastFragment.getTag)

  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    verbose(l"onCreateView")

    val size = getChildFragmentManager.getFragments.size()
    verbose(l"frags-size:${size}")
    if (savedInstanceState != null) {
      fragmentMap.clear()
      fragStrTagMap.clear()
      val lastFragmentTag = savedInstanceState.getString(INTENT_KEY_lastFragmentTag, "")
      val frags = getChildFragmentManager.getFragments
      (0 until frags.size()).reverse.foreach { idx =>
        val frag = frags.get(idx)
        if (frag.getTag.equals(lastFragmentTag)) {
          lastFragment = frag
        }
        frag.getTag match {
          case ConversationListFragment.TAG =>
            fragmentMap.put(ConversationListFragment.TAG, frag)
            fragStrTagMap.put(ListActionsView.STATE_AVATAR, ConversationListFragment.TAG)
            verbose(l"fill idx:$idx  tag:${frag.getTag}")
          case SearchUIFragment.TAG =>
            fragmentMap.put(SearchUIFragment.TAG, frag)
            fragStrTagMap.put(ListActionsView.STATE_FRIEND, SearchUIFragment.TAG)
            verbose(l"fill idx:$idx  tag:${frag.getTag}")
          case PreferencesUserFragment.TAG =>
            fragmentMap.put(PreferencesUserFragment.TAG, frag)
            fragStrTagMap.put(ListActionsView.STATE_SETTINGS, PreferencesUserFragment.TAG)
            verbose(l"fill idx:$idx  tag:${frag.getTag}")
          case _ =>
            getChildFragmentManager.beginTransaction().remove(frag).commit()
            verbose(l"remove idx:$idx  tag:${frag.getTag}")
        }
      }
    }

    returning(inflater.inflate(R.layout.fragment_conversation_list_manager, container, false)) { view =>
      rlFragParent = findById(view, R.id.rl_frag_parent)
      mainContainer = findById(view, R.id.fl__conversation_list_main)
      listActionsView = findById(view, R.id.lav__conversation_list_actions)
      startUiLoadingIndicator = findById(view, R.id.liv__conversations__loading_indicator)
      listLoadingIndicator = findById(view, R.id.lbv__conversation_list__loading_indicator)
      mDrawerView = ViewUtils.getView(view, R.id.draw_view)
      confirmationMenu = returning(findById[ConfirmationMenu](view, R.id.cm__confirm_action_light)) { v =>
        v.setVisible(false)
        v.resetFullScreenPadding()
      }

      setDrawerView

      listActionsView.setVisibility(View.VISIBLE)
      subs += ProxyConversationListManagerFragmentObject.hideListActionsView.onUi {
        case true =>
          listActionsView.setVisibility(View.GONE)
        case false =>
          listActionsView.setVisibility(View.VISIBLE)
      }

      subs += convController.defConvs.onUi { models =>
        tabListMenuModels.clear()
        models.foreach(tabListMenuModels.add(_))
        if (models.size > 0) {
          listActionsView.setDefaultGroupStatus(true)
        } else {
          listActionsView.setDefaultGroupStatus(false)
        }
      }
      val oldConvIdStr = SpUtils.getString(context, SpUtils.SP_NAME_FOREVER_SAVED, SpUtils.getUserId(context), "")
      if (!StringUtils.isBlank(oldConvIdStr)) {
        listActionsView.setDefaultGroupStatus(true)
      } else {
        listActionsView.setDefaultGroupStatus(false)
      }

      subs += (for {
        z <- zms
        syncSate <- z.syncRequests.syncState(z.selfUserId, SyncMatchers)
        animType <- animationType
      } yield (syncSate, animType)).onUi { case (state, animType) =>
        state match {
          case SYNCING => listLoadingIndicator.show(animType)
          case _ => listLoadingIndicator.hide()
        }
      }

      subs += convController.convChanged.map(_.requester).onUi {
        case ConversationChangeRequester.START_CONVERSATION |
             ConversationChangeRequester.INCOMING_CALL |
             ConversationChangeRequester.INTENT =>
          stripToConversationList()
        case requester => //
          verbose(l"ignore requester:$requester")
      }

      subs += accentColor.accentColor.map(_.color).onUi { c =>
        Option(startUiLoadingIndicator).foreach(_.setColor(c))
        Option(listLoadingIndicator).foreach(_.setColor(c))
      }
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    verbose(l"onViewCreated")

    listActionsView.setCallback(new ListActionsView.Callback {
      override def onAvatarPress(): Unit = {
        SoftInputUtils.setSoftInputModeADJUST_RESIZE(getActivity)
        showCurrentFragment(ListActionsView.STATE_AVATAR)
      }

      override def onFriendPress(): Unit = {
        SoftInputUtils.setSoftInputModeADJUST_PAN(getActivity)
        showCurrentFragment(ListActionsView.STATE_FRIEND)
      }

      override def onSettingsPress(): Unit = {
        SoftInputUtils.setSoftInputModeADJUST_RESIZE(getActivity)
        showCurrentFragment(ListActionsView.STATE_SETTINGS)
      }

      override def onDefaultGroupPress(): Unit = {
        SoftInputUtils.setSoftInputModeADJUST_RESIZE(getActivity)
        listActionsView.setActionButtonStatus(ListActionsView.STATE_DEFAULT_GROUP)
        ConversationListManagerFragment.this.status = ListActionsView.STATE_DEFAULT_GROUP
        MainActivityUtils.vibrator(context, 10)
        if (popConvMgrTabMenuUtil != null && popConvMgrTabMenuUtil.isShowing()) {
          updateEditedTabMenu(false, true)
        } else {
          convController.defConvs.currentValue.foreach { list =>
            if (list.size == 1 && list.head.getSubType == Constants.USER_NOTICE_TYPE_MANUAL) {
              convController.selectConv(ConvId(list.apply(0).getConvId), ConversationChangeRequester.CONVERSATION_LIST)
            } else {
              showPopConvMgrTabMenu()
            }
          }
        }
      }

      override def onAvatarDoubleTap(): Unit = {
         if(lastFragment!=null && lastFragment.isInstanceOf[ConversationListFragment]){
            val fragment=lastFragment.asInstanceOf[ConversationListFragment]
            fragment.scrollToNextUnReadItem()
         }
      }

      override def onClearUnReadMsg(): Unit ={
        verbose(l"onClearUnReadMsg")
        val frag=getChildFragmentManager.findFragmentByTag(ConversationListFragment.TAG)
        if(frag!=null && frag.isInstanceOf[ConversationListFragment]){
          val fragment=frag.asInstanceOf[ConversationListFragment]
          fragment.clearOnAllUnReadMsg()
        }
      }

    })
    showCurrentFragment(ListActionsView.STATE_AVATAR)
  }


  private def setDrawerView(): Unit = {

    mDrawerView.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    mDrawerView.addDrawerListener(new DrawerLayout.DrawerListener() {
      override def onDrawerSlide(@NonNull drawerView: View, slideOffset: Float): Unit = {
        val contentView = mDrawerView.getChildAt(0)
        val offset = (drawerView.getWidth * slideOffset).toInt
        contentView.setTranslationX(offset)
      }

      override def onDrawerOpened(@NonNull drawerView: View): Unit = {
        drawStatus = DrawerLayout.LOCK_MODE_UNLOCKED
        KeyboardController.showKeyboardIfHidden()

        val imm = getActivity.getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager];
        imm.showSoftInput(mDrawerView, InputMethodManager.SHOW_IMPLICIT);
      }

      override def onDrawerClosed(@NonNull drawerView: View): Unit = {
        drawStatus = DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        KeyboardController.hideKeyboardIfVisible()
      }

      override def onDrawerStateChanged(newState: Int): Unit = {
      }
    })

  }

  def showCurrentFragment(status: Int): Unit = {
    verbose(l"[ConversationListManagerFragment] showCurrentFragment status->$status")
    status match {
      case ListActionsView.STATE_AVATAR
           | ListActionsView.STATE_FRIEND
           | ListActionsView.STATE_SETTINGS =>
        statusIgnoreGroup = status
        updateEditedTabMenu(false, true)
      case ListActionsView.STATE_DEFAULT_GROUP =>
    }
    if (getChildFragmentManager.findFragmentByTag(SendConnectRequestFragment.Tag) != null
      || getChildFragmentManager.findFragmentByTag(PendingConnectRequestManagerFragment.Tag) != null
      || getChildFragmentManager.findFragmentByTag(BlockedUserProfileFragment.Tag) != null) {
      pickUserController.hideUserProfile()
    }

    var tag = fragStrTagMap.get(status)
    if (tag != null && lastFragment != null && (lastFragment equals fragmentMap.get(tag)) && this.status != ListActionsView.STATE_DEFAULT_GROUP) {
      verbose(l"showCurrentFragment  return")
      listActionsView.setActionButtonStatus(status)
    } else {
      this.status = status
      MainActivityUtils.vibrator(context, 10)
      listActionsView.setActionButtonStatus(status)
      var fragment = fragmentMap.get(tag)
      if (null == fragment) {
        status match {
          case ListActionsView.STATE_AVATAR =>
            fragment = ConversationListFragment.newNormalInstance
            tag = ConversationListFragment.TAG

          case ListActionsView.STATE_FRIEND =>
            fragment = SearchUIFragment.newInstance()
            tag = SearchUIFragment.TAG

          case ListActionsView.STATE_SETTINGS =>
            fragment = PreferencesUserFragment.newInstance
            tag = PreferencesUserFragment.TAG
          case _ =>
        }
        if (fragmentMap.containsKey(tag)) {
          if (lastFragment != fragment) {
            getChildFragmentManager.beginTransaction.hide(lastFragment).commitAllowingStateLoss
            verbose(l"showCurrentFragment  hide ${lastFragment.getClass.getSimpleName}")
            lastFragment = fragment
            getChildFragmentManager.beginTransaction.show(lastFragment).commitAllowingStateLoss
            verbose(l"showCurrentFragment  show ${lastFragment.getClass.getSimpleName}")
          } else {
            verbose(l"showCurrentFragment  ignore")
          }
        } else {
          fragmentMap.put(tag, fragment)
          fragStrTagMap.put(status, tag)
          if (lastFragment != null) {
            getChildFragmentManager.beginTransaction.hide(lastFragment).commitAllowingStateLoss
            verbose(l"showCurrentFragment  hide ${lastFragment.getClass.getSimpleName}")
          }
          lastFragment = fragment
          val fragmentTransaction = getChildFragmentManager.beginTransaction.add(R.id.fl__conversation_list_main, lastFragment, tag)
          if (status != ListActionsView.STATE_AVATAR) {
            fragmentTransaction.setCustomAnimations(R.anim.slide_in_from_bottom_pick_user, R.anim.open_new_conversation__thread_list_out, R.anim.open_new_conversation__thread_list_in, R.anim.slide_out_to_bottom_pick_user)
          }
          fragmentTransaction.show(lastFragment).commitAllowingStateLoss
          verbose(l"showCurrentFragment  show ${lastFragment.getClass.getSimpleName}")
        }
      } else {
        if (lastFragment != fragment) {
          if (lastFragment != null) {
            getChildFragmentManager.beginTransaction.hide(lastFragment).commitAllowingStateLoss
            verbose(l"showCurrentFragment  hide ${lastFragment.getClass.getSimpleName}")
          }
          lastFragment = fragment
          getChildFragmentManager.beginTransaction.show(lastFragment).commitAllowingStateLoss
          verbose(l"showCurrentFragment  show ${lastFragment.getClass.getSimpleName}")
        } else {
          verbose(l"showCurrentFragment  ignore")
        }
      }
    }
  }

  override def showSearchFragment(): Unit = {
    verbose(l"showSearchFragment")
    showOverlayFragment(SearchFragment.newInstance, SearchFragment.TAG)
  }

  override def removeSearchFragment(): Unit = {
    verbose(l"removeSearchFragment")
    val frag = getChildFragmentManager.findFragmentByTag(SearchFragment.TAG)
    if (frag != null) {
      getChildFragmentManager.popBackStackImmediate(SearchFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      togglePeoplePicker(true)
    }
  }

  override def showCreateGroupConversationFragment(): Unit = {
    verbose(l"showCreateGroupConversationFragment")
    inject[CreateConversationController].setCreateConversation(from = GroupConversationEvent.StartUi)
    showOverlayFragment(CreateConversationManagerFragment.newInstance, CreateConversationManagerFragment.TAG)
  }

  override def removeCreateGroupConversationFragment(): Unit = {
    verbose(l"removeCreateGroupConversationFragment")
    val frag = getChildFragmentManager.findFragmentByTag(CreateConversationManagerFragment.TAG)
    if (frag != null) {
      getChildFragmentManager.popBackStackImmediate(CreateConversationManagerFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
      togglePeoplePicker(true)


    }
  }

  private def updateEditedTabMenu(saveEditedStatus: Boolean, toDismiss: Boolean): Unit = {
    verbose(l"updateEditedTabMenu 11")
    if (popConvMgrTabMenuUtil == null) {
      return
    }
    verbose(l"updateEditedTabMenu 22")
    if (popConvMgrTabMenuUtil.isShowing()) {
      convController.defConvs.currentValue.foreach { models =>
        if (saveEditedStatus) {
          popConvMgrTabMenuUtil.setEdited()
          if (tabListMenuModels.size() == models.size) {
            verbose(l"updateEditedTabMenu ignore saveEditedStatus->${saveEditedStatus}")
          } else {
            val other = for (elem <- models.filter(!tabListMenuModels.contains(_))) yield elem
            other.foreach(data => {
              verbose(l"updateEditedTabMenu remove data=${data}")
              zms.currentValue.foreach(_.userNoticeStorage.remove(Uid(data.getUuid)))
            })
          }
        } else {
          if (tabListMenuModels.size() == models.size) {
            verbose(l"updateEditedTabMenu ignore ")
            popConvMgrTabMenuUtil.setEdited()
          } else {
            tabListMenuModels.clear()
            models.foreach(tabListMenuModels.add(_))
            popConvMgrTabMenuUtil.notifyDataSetChanged()
          }

        }
      }
      if (toDismiss) {
        popConvMgrTabMenuUtil.dismiss()
        status = statusIgnoreGroup
        listActionsView.setActionButtonStatus(status)
      }
    }
  }

  private def showPopConvMgrTabMenu(): Unit = {
    if (popConvMgrTabMenuUtil == null) {
      popConvMgrTabMenuUtil = new PopConvMgrTabMenuUtil2(context, tabListMenuModels, false)
    }
    if (popConvMgrTabMenuUtil.isShowing()) {
      return
    }
    listActionsView.rotateGroupAnim(90)
    val layoutParams = new RelativeLayout.LayoutParams(-1, -1)
    layoutParams.setMargins(0, 0, 0, context.getResources.getDimension(R.dimen.conversation_list__action_view__height).toInt)
    popConvMgrTabMenuUtil.addToParentView(rlFragParent, layoutParams,
      new OnPopMenuItemClick {
        override def onItemLongClick(view: View, position: Int): Unit = {
          verbose(l"++++onLongClick")
          view.findViewById(R.id.ivIcon).asInstanceOf[CircleImageView].setBorderWidth(context.getResources.getDimensionPixelSize(R.dimen.dp2))
          val location = new Array[Int](2)
          view.getLocationOnScreen(location)
          val popUpWindow = new TabMenuPopupWindow(view.getContext)

          val itemMenuModel = if (position >= 0 && position < tabListMenuModels.size()) {
            Try(tabListMenuModels.get(position)).toOption
          } else Option.empty

          itemMenuModel.foreach { menuModel =>
            if (menuModel.getSubType == Constants.USER_NOTICE_TYPE_PUSH) {
              popUpWindow.setData(util.Arrays.asList(getString(R.string.secret_share), getString(R.string.secret_delete)))
              popUpWindow.setListener(new TabMenuPopupWindow.OnMenuClickListener {
                override def onMenuClick(index: Int): Unit = {
                  MainActivityUtils.vibrator(context, 10)
                  index match {
                    case 0 =>
                      val joinUrl = menuModel.getJoinUrl
                      if (!TextUtils.isEmpty(joinUrl)) {
                        IntentUtils.shareTextInApp(getActivity, joinUrl)
                        updateEditedTabMenu(saveEditedStatus = false, toDismiss = true)
                      }
                    case 1 =>
                      popConvMgrTabMenuUtil.remove(position)
                      updateEditedTabMenu(saveEditedStatus = true, toDismiss = tabListMenuModels.size() == 0)
                  }
                }
              })
            } else {
              popUpWindow.setData(util.Arrays.asList(getString(R.string.secret_delete)))
              popUpWindow.setListener(new TabMenuPopupWindow.OnMenuClickListener {
                override def onMenuClick(index: Int): Unit = {
                  MainActivityUtils.vibrator(context, 10)
                  popConvMgrTabMenuUtil.remove(position)
                  updateEditedTabMenu(saveEditedStatus = true, toDismiss = tabListMenuModels.size() == 0)
                }
              })
            }
            popUpWindow.setOnDismissListener(new PopupWindow.OnDismissListener {
              override def onDismiss(): Unit = {
                view.findViewById(R.id.ivIcon).asInstanceOf[CircleImageView].setBorderWidth(0)
              }
            })
            popUpWindow.showAtLocation(view, Gravity.NO_GRAVITY,
              location(0) + (view.getWidth - popUpWindow.getMeasuredWidth) / 2,
              location(1) - popUpWindow.getMeasuredHeight - context.getResources.getDimensionPixelSize(R.dimen.dp5))
          }
        }

        override def onItemClick(view: View, position: Int): Unit = {
          verbose(l"onItemClick position->${position}")
          view.getId match {
            case R.id.vAnim =>
              MainActivityUtils.vibrator(context, 10)
              updateEditedTabMenu(saveEditedStatus = false, toDismiss = true)
            case R.id.rlInnerContent | R.id.ivIcon =>
              if (popConvMgrTabMenuUtil.isEdting()) {
              } else {
                MainActivityUtils.vibrator(context, 10)

                val itemMenuModel = if (position >= 0 && position < tabListMenuModels.size()) {
                  Try(tabListMenuModels.get(position)).toOption
                } else Option.empty
                verbose(l"onItemClick position->${position},data:${itemMenuModel}")

                itemMenuModel.foreach { menuModel =>
                  if (!menuModel.isRead) {
                    menuModel.setRead(true)
                    popConvMgrTabMenuUtil.notifyDataSetChanged()
                    convController.updateGroupReadStatus(menuModel.getConvId)
                  }

                  convController.getConversation(ConvId(menuModel.getConvId)).map {
                    case Some(conversationData) =>
                      verbose(l"+++++onItemClick position->${position},success")
                      MainHandler.getInstance().post(new Runnable {
                        override def run(): Unit = {
                          updateEditedTabMenu(saveEditedStatus = false, toDismiss = true)
                          convController.selectConv(conversationData.id, ConversationChangeRequester.CONVERSATION_LIST)
                        }
                      })
                    case None                   =>
                      verbose(l"+++++onItemClick position->${position},none")
                      if (menuModel.getSubType == Constants.USER_NOTICE_TYPE_PUSH) {
                        convController.getByRemoteId(RConvId(menuModel.getConvId)) onComplete {
                          case Success(Some(conversationData)) =>
                            MainHandler.getInstance().post(new Runnable {
                              override def run(): Unit = {
                                updateEditedTabMenu(saveEditedStatus = false, toDismiss = true)
                                convController.selectConv(conversationData.id, ConversationChangeRequester.CONVERSATION_LIST)
                                convController.updateGroupConv(conversationData)
                              }
                            })
                          case _                               =>
                            MainHandler.getInstance().post(new Runnable {
                              override def run(): Unit = {
                                clickSpanUrl(menuModel)
                              }
                            })
                        }
                      }
                  }
                }
              }
            case _ =>
              verbose(l"onItemClick position->${position} ignore view->${view.getClass.getSimpleName}")
          }
        }
      }, new OnPopMenuDismissListener[Object] {
        override def onDismissed(t: Object): Unit = {
          listActionsView.rotateGroupAnim(-90)
        }
      }
    )
  }

  private def clickSpanUrl(tabListMenuModel: TabListMenuModel): Unit = {
    val urlContent = tabListMenuModel.getJoinUrl
    if (!StringUtils.isBlank(tabListMenuModel.getJoinUrl)) {
      val popUpWindow = new JoinGroupPopUpWindow(context, -1, -1)
      popUpWindow.setCallBack(new JoinGroupAllowCallBack {
        override def clickAllow(): Unit = {
          val id = urlContent.substring(urlContent.length - 10, urlContent.length)
          if (!StringUtils.isBlank(id)) {
            val urlPath = new StringBuilder().append("conversations/").append(id).append("/join_invite").toString
            SpecialServiceAPI.getInstance().post(urlPath, "", new OnHttpListener[HttpResponseBaseModel] {
              override def onFail(code: Int, err: String): Unit = {
                verbose(l"+++++++++SpecialServiceAPI onFail:${err}")
                showToast(err)
              }

              override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
                //{"data":{"conv":"1bddf284-cc4c-454c-a3b2-9c70a4085aa0"},"msg":"already in convsation","code":2001}
                verbose(l"+++++++++SpecialServiceAPI onSuccess:${orgJson}")
                val orgObj = new JSONObject(orgJson)
                val code = orgObj.optString("code")
                if (!StringUtils.isBlank(code) && "2002".equalsIgnoreCase(code)) {
                  showToast(getResources.getString(R.string.conversation_join_group_closed))
                } else {
                  val dataObj = orgObj.optJSONObject("data")
                  if (dataObj != null) {
                    val convRId = dataObj.optString("conv")
                    if (!StringUtils.isBlank(convRId)) {
                      convController.getByRemoteId(RConvId(convRId)) onComplete {
                        case Success(Some(conversationData)) =>
                          convController.selectConv(conversationData.id, ConversationChangeRequester.START_CONVERSATION)
                        case _ =>

                      }
                    }
                  }
                }
              }

              override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

              }
            })
          }
        }

        override def clickRefuse(): Unit = {

        }
      })
      popUpWindow.showAtLocation(getActivity.getWindow.getDecorView, Gravity.CENTER, 0, 0)
    }
  }

  override def onResume(): Unit = {
    super.onResume()
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    verbose(l"onDestroyView")
    subs.foreach(_.destroy())
    subs = Set.empty
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    verbose(l"onDestroy")
  }

  //  override def onShowPickUser() = {
  //    navController.getCurrentLeftPage match {
  //      // TODO: START is set as left page on tablet, fix
  //      case START | CONVERSATION_LIST =>
  //        withFragmentOpt(SearchUIFragment.TAG) {
  //          case Some(_: SearchUIFragment) => // already showing
  //          case _ =>
  //            getChildFragmentManager.beginTransaction
  //              .setCustomAnimations(
  //                R.anim.slide_in_from_bottom_pick_user,
  //                R.anim.open_new_conversation__thread_list_out,
  //                R.anim.open_new_conversation__thread_list_in,
  //                R.anim.slide_out_to_bottom_pick_user)
  //              .replace(R.id.fl__conversation_list_main, SearchUIFragment.newInstance(), SearchUIFragment.TAG)
  //              .addToBackStack(SearchUIFragment.TAG)
  //              .commit
  //        }
  //      case _ => //
  //    }
  //    navController.setLeftPage(Page.PICK_USER, Tag)
  //  }

  //  override def onHidePickUser() = {
  //    val page = navController.getCurrentLeftPage
  //    import Page._
  //
  //    def hide() = {
  //      getChildFragmentManager.popBackStackImmediate(SearchUIFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
  //      KeyboardUtils.hideKeyboard(getActivity)
  //    }
  //
  //    page match {
  //      case SEND_CONNECT_REQUEST | BLOCK_USER | PENDING_CONNECT_REQUEST =>
  //        pickUserController.hideUserProfile()
  //        hide()
  //      case PICK_USER | INTEGRATION_DETAILS => hide()
  //      case _ => //
  //    }
  //    navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  //  }

  override def onBackPressed: Boolean = {

    val frags = getChildFragmentManager.getFragments
    verbose(l"onBackPressed child--->${frags.asScala.map(_.getClass.getSimpleName).mkString(",")}")

    if (getChildFragmentManager.findFragmentByTag(SendConnectRequestFragment.Tag) != null
      || getChildFragmentManager.findFragmentByTag(PendingConnectRequestManagerFragment.Tag) != null
      || getChildFragmentManager.findFragmentByTag(BlockedUserProfileFragment.Tag) != null) {
      pickUserController.hideUserProfile()
      return true
    }

    Option(getChildFragmentManager.findFragmentByTag(SearchUIFragment.TAG))
      .filter(_.isInstanceOf[SearchUIFragment])
      .map(_.asInstanceOf[SearchUIFragment]).foreach { fragment =>
      if (fragment.onBackPressed()) return true
    }

    Option(getChildFragmentManager.findFragmentByTag(CreateConversationManagerFragment.TAG))
      .filter(_.isInstanceOf[CreateConversationManagerFragment])
      .map(_.asInstanceOf[CreateConversationManagerFragment]).foreach { fragment =>
      if (fragment.onBackPressed()) return true
    }

    Option(getChildFragmentManager.findFragmentByTag(SearchFragment.TAG))
      .filter(_.isInstanceOf[SearchFragment])
      .map(_.asInstanceOf[SearchFragment]).foreach { fragment =>
      if (fragment.onBackPressed()) return true
    }

    if (drawStatus == DrawerLayout.LOCK_MODE_UNLOCKED) {
      return true
    }
    if (popConvMgrTabMenuUtil != null && popConvMgrTabMenuUtil.isShowing()) {
      updateEditedTabMenu(false, true)
      return true
    }
    false
  }


  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    getChildFragmentManager.getFragments.asScala.foreach(_.onActivityResult(requestCode, resultCode, data))
  }

  private def showOverlayFragment(fragment: Fragment, tag: String): Unit = {
    getChildFragmentManager
      .beginTransaction
      .setCustomAnimations(
        R.anim.fragment_animation__send_connect_request__fade_in,
        R.anim.fragment_animation__send_connect_request__zoom_exit,
        R.anim.fragment_animation__send_connect_request__zoom_enter,
        R.anim.fragment_animation__send_connect_request__fade_out)
      .replace(R.id.fl__conversation_list__profile_overlay, fragment, tag)
      .addToBackStack(tag).commit
    togglePeoplePicker(false)
  }

  override def onShowUserProfile(userId: UserId, fromDeepLink: Boolean) =
    if (!pickUserController.isShowingUserProfile) {
      import com.waz.api.User.ConnectionStatus._

      zms.head.flatMap(_.usersStorage.get(userId)).foreach {
        case Some(userData) => userData.connection match {
          case CANCELLED | UNCONNECTED =>
            if (!userData.isConnected) {
              showOverlayFragment(SendConnectRequestFragment.newInstance(userId.str, UserRequester.SEARCH, true), SendConnectRequestFragment.Tag)
              //              navController.setLeftPage(Page.SEND_CONNECT_REQUEST, Tag)
            }

          case PENDING_FROM_OTHER | PENDING_FROM_USER | IGNORED =>
            showOverlayFragment(
              PendingConnectRequestManagerFragment.newInstance(userId, UserRequester.SEARCH),
              PendingConnectRequestManagerFragment.Tag
            )
          //            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)

          case BLOCKED =>
            showOverlayFragment(
              BlockedUserProfileFragment.newInstance(userId.str, UserRequester.SEARCH),
              BlockedUserProfileFragment.Tag
            )
          //            navController.setLeftPage(Page.PENDING_CONNECT_REQUEST, Tag)
          case _ => //
        }
        case _ => //
      }(Threading.Ui)
    }

  private def togglePeoplePicker(show: Boolean) = {
    if (show)
      mainContainer
        .animate
        .alpha(1)
        .scaleY(1)
        .scaleX(1)
        .setInterpolator(new Expo.EaseOut)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(getInt(R.integer.reopen_profile_source__delay))
        .start()
    else
      mainContainer
        .animate
        .alpha(0)
        .scaleY(2)
        .scaleX(2)
        .setInterpolator(new Expo.EaseIn)
        .setDuration(getInt(R.integer.reopen_profile_source__animation_duration))
        .setStartDelay(0)
        .start()
  }

  override def onHideUserProfile() = {
    if (pickUserController.isShowingUserProfile) {
      getChildFragmentManager.popBackStackImmediate
      togglePeoplePicker(true)
    }
  }

  override def showIncomingPendingConnectRequest(conv: ConvId) = {
    verbose(l"SearchUIFragment showIncomingPendingConnectRequest $conv")
    //    pickUserController.hidePickUser()
    convController.selectConv(conv, ConversationChangeRequester.INBOX) //todo stop doing this!!!
  }

  override def getLoadingViewIndicator =
    startUiLoadingIndicator

  override def onPageVisible(page: Page) = {
    /*if (page != Page.ARCHIVE && page != Page.CONVERSATION_MENU_OVER_CONVERSATION_LIST) closeArchive()*/
  }

  /*
  override def showArchive() = {
    import Page._
    navController.getCurrentLeftPage match {
      case START | CONVERSATION_LIST =>
        withFragmentOpt(ArchiveListFragment.TAG) {
          case Some(_: ArchiveListFragment) => // already showing
          case _ =>
            getChildFragmentManager.beginTransaction
              .setCustomAnimations(
                R.anim.slide_in_from_bottom_pick_user,
                R.anim.open_new_conversation__thread_list_out,
                R.anim.open_new_conversation__thread_list_in,
                R.anim.slide_out_to_bottom_pick_user)
              .replace(R.id.fl__conversation_list_main, ConversationListFragment.newArchiveInstance(), ArchiveListFragment.TAG)
              .addToBackStack(ArchiveListFragment.TAG)
              .commit
        }
      case _ => //
    }
    navController.setLeftPage(ARCHIVE, Tag)
  }


  override def closeArchive() = {
    getChildFragmentManager.popBackStackImmediate(ArchiveListFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    if (navController.getCurrentLeftPage == Page.ARCHIVE) navController.setLeftPage(Page.CONVERSATION_LIST, Tag)
  }
  */

  override def onStart() = {
    super.onStart()
    pickUserController.addPickUserScreenControllerObserver(this)
    convScreenController.addConversationControllerObservers(this)
    navController.addNavigationControllerObserver(this)
  }

  override def onStop() = {
    pickUserController.removePickUserScreenControllerObserver(this)
    convScreenController.removeConversationControllerObservers(this)
    navController.removeNavigationControllerObserver(this)
    super.onStop()
  }

  override def onViewStateRestored(savedInstanceState: Bundle) = {
    super.onViewStateRestored(savedInstanceState)
    //    import Page._
    //    navController.getCurrentLeftPage match { // TODO: START is set as left page on tablet, fix
    //      case PICK_USER =>
    //        pickUserController.showPickUser()
    //      case BLOCK_USER | PENDING_CONNECT_REQUEST | SEND_CONNECT_REQUEST | COMMON_USER_PROFILE =>
    //        togglePeoplePicker(false)
    //      case _ => //
    //    }
  }

  //  override def onBackPressed = {
  //    withBackstackHead {
  //      case Some(f: FragmentHelper) if f.onBackPressed() => true
  //      case _ if pickUserController.isShowingPickUser() =>
  //        pickUserController.hidePickUser()
  //        true
  //      case _ => false
  //    }
  //  }

  override def onAcceptedConnectRequest(userId: UserId) = {
    verbose(l"onAcceptedConnectRequest $userId")
    userAccountsController.getConversationId(userId).flatMap { convId =>
      convController.selectConv(convId, ConversationChangeRequester.START_CONVERSATION)
    }
  }

  override def onUnblockedUser(restoredConversationWithUser: ConvId) = {
    pickUserController.hideUserProfile()
    verbose(l"onUnblockedUser $restoredConversationWithUser")
    convController.selectConv(restoredConversationWithUser, ConversationChangeRequester.START_CONVERSATION)
  }

  override def onShowConversationMenu(inConvList: Boolean, convId: ConvId): Unit =
    if (inConvList) {
      OptionsMenu(getContext, new ConversationOptionsMenuController(convId, Mode.Normal(inConvList))).show()
    }

  override def dismissUserProfile() =
    pickUserController.hideUserProfile()

  override def onConnectRequestWasSentToUser() =
    pickUserController.hideUserProfile()

  override def dismissSingleUserProfile() =
    dismissUserProfile()

  override def onHideUser() = {}

  override def onHideOtrClient() = {}

  override def showRemoveConfirmation(userId: UserId) = {}

  override def changeCurrentFragment(tag: Int): Unit = showCurrentFragment(tag)

  var unreadCount: Int = 0;
  var incommings: Seq[UserId] = null

  override def countUnreadMsg(unreadCount: Int): Unit = {
    this.unreadCount = unreadCount
    listActionsView.showAvastarRedPoint(if (unreadCount > 0 || (incommings != null && incommings.size > 0)) View.VISIBLE else View.GONE)
  }

  override def incommonConversations(incommings: Seq[UserId]): Unit = {
    this.incommings = incommings
    listActionsView.showAvastarRedPoint(if (unreadCount > 0 || (incommings != null && incommings.size > 0)) View.VISIBLE else View.GONE)
  }

  private def getNewDevicesMessage(devices: Seq[Client]): String = {
    val deviceNames = devices.map { device =>
      val time =
        device.regTime match {
          case Some(regTime) =>
            TimeStamp(regTime).string
          case _ =>
            ""
        }
      s"${device.model}${if (device.label.isEmpty) "" else s" (${device.label})"}\n$time"
    }.mkString("\n\n")

    val infoMessage = context.getString(R.string.new_devices_dialog_info)

    Seq(deviceNames, infoMessage).mkString("\n\n")
  }

}

object ConversationListManagerFragment extends DerivedLogTag {

  trait Container {}

  lazy val SyncMatchers = Seq(SyncConversations, SyncSelf, SyncConnections)

  val INTENT_KEY_lastFragmentTag = "lastFragmentTag "

  val Tag = ConversationListManagerFragment.getClass.getSimpleName

  def newInstance() = {
    verbose(l"ConversationListManagerFragment#newInstance object")
    new ConversationListManagerFragment()
  }
}
