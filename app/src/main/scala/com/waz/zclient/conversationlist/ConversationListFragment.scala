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
package com.waz.zclient.conversationlist

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View.{GONE, VISIBLE}
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView, SimpleItemAnimator}
import com.jsy.common.ConversationApi
import com.jsy.common.acts.scan.ScanActivity
import com.jsy.common.fragment.SearchFragment
import com.jsy.common.listener.OnPopMenuItemClick
import com.jsy.common.moduleProxy.ProxyConversationListManagerFragment
import com.jsy.common.utils.{MessageUtils, PopMenuUtil}
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType._
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, Subscription}
import com.waz.utils.returning
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.views.{ConversationBadge, ConversationListRow2, NormalTopToolbar}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI.{verbose, _}
import com.waz.zclient.messages.{MessagesListLayoutManager, UsersController}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}

import scala.collection.immutable.ListSet
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

/**
  * Due to how we use the NormalConversationListFragment - it gets replaced by the ArchiveConversationListFragment or
  * PickUserFragment, thus destroying its views - we have to be careful about when assigning listeners to signals and
  * trying to instantiate things in onViewCreated - be careful to tear them down again.
  */
class ConversationListFragment extends BaseFragment[ConversationListFragment.Container] with FragmentHelper with DerivedLogTag with SearchFragment.Container {

  lazy val accounts = inject[AccountsService]
  lazy val userAccountsController = inject[UserAccountsController]
  lazy val conversationController = inject[ConversationController]
  lazy val usersController = inject[UsersController]
  lazy val screenController = inject[IConversationScreenController]
  lazy val pickUserController = inject[IPickUserController]
  lazy val convListController = inject[ConversationListController]
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val accentColor = inject[AccentColorController].accentColor
  private lazy val permissions        = inject[PermissionsService]

  import Threading.Implicits.Ui

  protected var subs = Set.empty[Subscription]
  private val adapterMode = ConversationListAdapter.Normal

  private var topToolbar: Option[NormalTopToolbar] = None
  private var loadingListView: Option[View] = None
  private var conversationListView: Option[SwipeListView] = None
  private var layoutManager: Option[MessagesListLayoutManager] = None

  private var popMenuUtil: PopMenuUtil = _

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle) = {
    returning(inflater.inflate(R.layout.fragment_conversation_list, container, false)) { rootView =>
      loadingListView = Option(ViewUtils.getView(rootView, R.id.conversation_list_loading_indicator))
      conversationListView = Option(ViewUtils.getView(rootView, R.id.conversation_list_view))
      topToolbar = Option(ViewUtils.getView(rootView, (R.id.conversation_list_top_toolbar)))
    }
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
//    else if (pickUserController.isHideWithoutAnimations)
//      new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter, 0, 0, false, 1f)
    else if (enter)
      new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter,
        getInt(R.integer.framework_animation_duration_long), getInt(R.integer.framework_animation_duration_medium), false, 1f)
    else new ConversationListAnimation(0, getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance), enter,
      getInt(R.integer.framework_animation_duration_medium), 0, false, 1f)
  }


  private val waitingAccount = Signal[Option[UserId]](None)

  private var adapter: Option[ConversationListAdapter] = None

  private var showToolBarAnimator: ObjectAnimator = _

  private val ANIM_DURATION = 350

  private val conversationsListScrollListener = new RecyclerView.OnScrollListener {
    override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = {
      topToolbar.foreach(_.setScrolledToTop(!recyclerView.canScrollVertically(-1)))
      if (dy > 0) {
        topToolbar.foreach(_.setVisibility(View.GONE))
        setMargins(recyclerView,top = 0)
      }
    }

    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
      super.onScrollStateChanged(recyclerView, newState)
      if(newState == RecyclerView.SCROLL_STATE_IDLE){
        topToolbar.foreach{
          topBar =>
            if(topBar.getVisibility != View.VISIBLE){
              //topBar.setVisibility(View.VISIBLE)
              showTopToolbar(topBar)
            }
            if(!recyclerView.canScrollVertically(-1)){
              topBar.post(new Runnable {
                override def run(): Unit = {
                  setMargins(recyclerView,top = topBar.getHeight)
                }
              })
            }
        }
      }
    }
  }

  def showTopToolbar(toolbar : NormalTopToolbar): Unit ={
    if (showToolBarAnimator != null && showToolBarAnimator.isRunning) {

    } else {
      if (showToolBarAnimator == null) {
        showToolBarAnimator = ObjectAnimator.ofFloat(toolbar, "alpha", 0, 1)
        showToolBarAnimator.setDuration(ANIM_DURATION)
      }
      toolbar.setVisibility(View.VISIBLE)
      showToolBarAnimator.start()
    }
  }

  def setMargins (v : View,l : Int = 0,top : Int,r : Int = 0,b : Int = 0) {
    v.getLayoutParams match {
      case params : ViewGroup.MarginLayoutParams =>
        params.setMargins(l, top, r, b)
        v.requestLayout()
      case _ =>
    }
  }

  override def onViewCreated(v: View, savedInstanceState: Bundle) = {
    super.onViewCreated(v, savedInstanceState)


    subs += (for {
      Some(accountData) <- ZMessaging.currentAccounts.activeAccount
      count <- userAccountsController.unreadCount.map(_.filter(_._1 == accountData.id).values.sum)
    } yield count).orElse(Signal.const(0)).onUi {
      case count =>
        getContainer.countUnreadMsg(count)
    }

    subs += userAccountsController.currentUser.onChanged.onUi(_ => conversationListView.foreach(_.scrollToPosition(0)))

    adapter = Option(returning(new ConversationListAdapter) { a =>
      a.setMaxAlpha(getResourceFloat(R.dimen.list__swipe_max_alpha))
      adapterMode match {
        case ConversationListAdapter.Normal =>
          verbose(l"start== ConversationListAdapter.setData")
          subs += (for {
            regular <- convListController.regularConversationListData.throttle(300.millis)
            incoming <- convListController.incomingConversationListData
            expand <- convListController.foldExpand
            conversations = {

              val resultData = mutable.ListBuffer[ItemBean]()

              if (incoming._2 != null && incoming._2.nonEmpty) {
                resultData.append(ItemBean.IncomingBean(incoming._1.headOption.map(_.id), incoming._2))
              }

              val (tops, normals) = regular.partition(_.place_top)
              val foldTopMinCount = 8
              if (tops.size >= foldTopMinCount && regular.size >= 30) {

                val (shows, folds) = tops.partition(_.unreadCount.total > 0)
                resultData.appendAll(shows.map(ItemBean.ConversationBean))
                resultData.append(ItemBean.TopStickFoldBean(folds.size, expand))
                if(expand) resultData.appendAll(folds.map(ItemBean.ConversationBean))
                resultData.appendAll(formatNormals(normals))
              } else {
                resultData.appendAll(tops.map(ItemBean.ConversationBean))
                resultData.appendAll(formatNormals(normals))
              }

              resultData
            }
          } yield (conversations, incoming)).onUi { case (conversations, incoming) =>

            a.setData(conversations)
            a.notifyDataSetChanged()

            getContainer.incommonConversations(incoming._2)
          }
      }

      a.onConversationLongClick { conv =>
        if (Set(Group, OneToOne, WaitForConnection, ThousandsGroup).contains(conv.convType))
          screenController.showConversationMenu(true, conv.id)
      }

      a.onItemClick { case (itemType, convId) =>
        itemType match {
          case ConversationListAdapter.TopStickFoldType                                           =>
            convListController.foldExpand ! !convListController.foldExpand.currentValue.getOrElse(false)
          case ConversationListAdapter.IncomingViewType | ConversationListAdapter.NormalViewType =>
            verbose(l"handleItemClick, switching conv to $convId")
            if (convId.isDefined) {
              conversationController.selectConv(convId, ConversationChangeRequester.CONVERSATION_LIST)
              if (getContext != null) MainActivityUtils.vibrator(getContext, 10)
            }
          case _                                                                                  =>
        }
      }
    })


    conversationListView.foreach { lv =>
      lv.setHasFixedSize(true)
      layoutManager=Option(new MessagesListLayoutManager(getContext, LinearLayoutManager.VERTICAL, false))
      layoutManager.foreach{ manager=>
        manager.snapToStart()
        lv.setLayoutManager(manager)
      }
      adapter.foreach(lv.setAdapter)
      lv.setAllowSwipeAway(true)
      lv.setOverScrollMode(View.OVER_SCROLL_NEVER)
      //lv.addOnScrollListener(conversationsListScrollListener)
      Option(lv.getItemAnimator).filter(_.isInstanceOf[SimpleItemAnimator])
        .map(_.asInstanceOf[SimpleItemAnimator]).foreach{it =>
        it.setRemoveDuration(0L)
        it.setChangeDuration(0L)
        it.setMoveDuration(0L)
        it.setAddDuration(0L)
        it.setSupportsChangeAnimations(false)
      }
    }

    subs += (for {
      Some(waitingAcc) <- waitingAccount
      z <- zms
      processing <- z.push.processing
    } yield processing || waitingAcc != z.selfUserId).onUi {
      case true => showLoading()
      case false =>
        hideLoading()
        waitingAccount ! None
    }

    topToolbar.foreach { view =>
      view.onClick(conversationListView.foreach(_.smoothScrollToPosition(0)))
      view.onRightButtonClick { v =>
        if (v.getId == R.id.vpAddIcon) {
          showPopMenu(v)
        }
      }
    }
  }

  private def formatNormals(conversations: Seq[ConversationData]): mutable.ListBuffer[ItemBean] = {
    val result = mutable.ListBuffer[ItemBean]()
    conversations.foreach { it =>
      result.append(ItemBean.ConversationBean(it))
    }
    result
  }

  def showPopMenu(v: View): Unit = {
    if (popMenuUtil == null) {
      popMenuUtil = new PopMenuUtil(getActivity, PopMenuUtil.initConversationListFragmentMenu())
    }
    popMenuUtil.showPopMenu(v, new OnPopMenuItemClick {
      override def onItemClick(view: View, position: Int): Unit = {
        position match {
          case 0 =>
            if (fragment != null) fragment.foreach(_.showCreateGroupConversationFragment()) // clickCreateGroup() //fragment.changeCurrentFragment(ListActionsView.STATE_FRIEND)
          case 1 =>
            if (fragment != null) fragment.foreach(_.showSearchFragment())
          case 2 =>
            scanPaymentQRCode()
          case _ =>
        }
      }
    })
  }

  /**
    * [[SearchFragment.Container]]
    *
    * @param conv
    */
  override def showIncomingPendingConnectRequest(conv: ConvId): Unit = {
    verbose(l"NormalConversationFragment showIncomingPendingConnectRequest $conv")
    conversationController.selectConv(conv, ConversationChangeRequester.INBOX) //todo stop doing this!!!
  }

  def fragment: Option[ProxyConversationListManagerFragment] = {
    if (getParentFragment != null && getParentFragment.isInstanceOf[ProxyConversationListManagerFragment]) {
      Some(getParentFragment.asInstanceOf[ProxyConversationListManagerFragment])
    } else None
  }

  private def scanPaymentQRCode() = {

    inject[PermissionsService].requestAllPermissions(ListSet(Manifest.permission.CAMERA)).map {
      case true =>
        ScanActivity.startSelf(getContext)
      case false =>
        showToast(R.string.toast_no_camera_permission)
    }
  }

  override def onDestroyView() = {
    super.onDestroyView()
    conversationListView.foreach(_.removeOnScrollListener(conversationsListScrollListener))
    subs.foreach(_.destroy())
    subs = Set.empty
  }

  private def showLoading(): Unit = {
    loadingListView.foreach { lv =>
      lv.setAlpha(1f)
      lv.setVisibility(VISIBLE)
    }
    topToolbar.foreach(_.setLoading(true))
  }

  private def hideLoading(): Unit = {
    loadingListView.foreach(v => v.animate().alpha(0f).setDuration(500).withEndAction(new Runnable {
      override def run() = {
        if (ConversationListFragment.this != null)
          v.setVisibility(GONE)
      }
    }))

    topToolbar.foreach(_.setLoading(false))
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    if (requestCode == MainActivityUtils.REQUET_CODE_SwitchAccountCode && data != null) {
      showLoading()
      waitingAccount ! Some(UserId(data.getStringExtra(MainActivityUtils.INTENT_KEY_SwitchAccountExtra)))
    }
  }

  def scrollToNextUnReadItem(): Unit = {
    val firstVisibleItem = layoutManager.fold(-1)(_.findFirstVisibleItemPosition())
    verbose(l"firstVisibleItem:${firstVisibleItem}")
    adapter.foreach{ a =>
      val showItemBeans = a.getData()
      var position = showItemBeans.indexWhere({ it =>
        val bool = it.isInstanceOf[ItemBean.ConversationBean]
        if (bool) {
          val itemConversation = it.asInstanceOf[ItemBean.ConversationBean].conversationData
          ConversationListRow2.badgeStatusForConversation(itemConversation, itemConversation.unreadCount, typing = false, null, null)
            .isInstanceOf[ConversationBadge.Count]
        } else {
          false
        }
      }, firstVisibleItem+1)
      verbose(l"position11:${position}")
      if (position < 0) {
        position = showItemBeans.indexWhere({ it =>
          val bool = it.isInstanceOf[ItemBean.ConversationBean]
          if (bool) {
            val itemConversation = it.asInstanceOf[ItemBean.ConversationBean].conversationData
            ConversationListRow2.badgeStatusForConversation(itemConversation, itemConversation.unreadCount, typing = false, null, null)
              .isInstanceOf[ConversationBadge.Count]
          } else {
            false
          }
        })
        verbose(l"position22:${position}")
      }
      if (position >= 0) {
        //layoutManager.foreach(_.scrollToPositionWithOffset(position, 0))
        conversationListView.foreach(_.smoothScrollToPosition(position))
      }
    }
  }

  def clearOnAllUnReadMsg(): Unit = {
    zms.head.map(_.convsStorage.clearUnread())
  }
}

object ConversationListFragment {

  val TAG = ConversationListFragment.getClass.getSimpleName

  trait Container {
    def countUnreadMsg(unreadCount: Int): Unit

    def incommonConversations(incommings: Seq[UserId]): Unit
  }

  def newNormalInstance(): ConversationListFragment = {
    new ConversationListFragment()
  }

}
