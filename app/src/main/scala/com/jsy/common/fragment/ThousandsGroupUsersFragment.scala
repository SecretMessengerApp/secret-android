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
package com.jsy.common.fragment

import java.util

import android.content.Context
import android.os.Bundle
import android.text.{InputType, TextUtils}
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import androidx.recyclerview.widget.{LinearLayoutManager, OrientationHelper, RecyclerView}
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.chad.library.adapter.base.BaseQuickAdapter
import com.jsy.common.acts.SendConnectRequestActivity
import com.jsy.common.adapter.ThousandsGroupUsersRecycleAdapter
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.listener.{OnRecyclerItemClickDataListener, OnSelectUserDataListener}
import com.jsy.common.model.ThousandGroupUserModel.ThousandGroupUserItemModel
import com.jsy.common.model.ThousandGroupUserModel
import com.jsy.res.utils.ViewUtils
import com.waz.api.User.ConnectionStatus._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{RConvId, UserId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.views.PickableElement
import com.waz.zclient.connect.{PendingConnectRequestFragment, SendConnectRequestFragment}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.connect.BlockedUserProfileFragment
import com.waz.zclient.participants.fragments.{ParticipantFragment, SingleParticipantFragment}
import com.waz.zclient.participants.{ParticipantsController, UserRequester}
import com.waz.zclient.usersearch.views.SearchEditText
import com.waz.zclient.utils.{SpUtils, StringUtils}
import com.waz.zclient.{BaseActivity, FragmentHelper, R}

import scala.concurrent.duration._
import scala.collection.JavaConversions._

class ThousandsGroupUsersFragment extends FragmentHelper with BaseQuickAdapter.RequestLoadMoreListener with DerivedLogTag {

  import ThousandsGroupUsersFragment._
  import Threading.Implicits.Ui

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]
  private lazy val keyboard = inject[KeyboardController]

  private var rConvId: RConvId = _
  private var selfUserId: String = _
  private var allowUserAddFriend: Boolean = false

  private var swipeRefreshLayout: SwipeRefreshLayout = _
  private var recyclerView: RecyclerView = _

  private var thousandsGroupUsersRecycleAdapter: ThousandsGroupUsersRecycleAdapter = _
  private val thousandGroupUserItemModels: util.List[ThousandGroupUserModel.ThousandGroupUserItemModel] = new util.ArrayList[ThousandGroupUserModel.ThousandGroupUserItemModel]
  private val thousandGroupUserSearchResultItemModels: util.List[ThousandGroupUserModel.ThousandGroupUserItemModel] = new util.ArrayList[ThousandGroupUserModel.ThousandGroupUserItemModel]
  private var thousandGroupUserAdapterDataModels: util.List[ThousandGroupUserModel.ThousandGroupUserItemModel] = new util.ArrayList[ThousandGroupUserModel.ThousandGroupUserItemModel]

  private var isLoadingData: Boolean = false

  private var groupUserType: Int = 0
  private var creatorId: UserId = _
  private var selectUserDataListener: OnSelectUserDataListener = _

  private var searchBox: Option[SearchEditText] = None
  //val filter = Signal("")

  private val searchBoxViewCallback = new SearchEditText.Callback {
    override def onRemovedTokenSpan(element: PickableElement): Unit = {}

    override def onFocusChange(hasFocus: Boolean): Unit = {}

    override def onClearButton(): Unit = {}

    override def afterTextChanged(s: String): Unit = {

      if(StringUtils.isBlank(s)){
        swipeRefreshLayout.setEnabled(true)
        thousandGroupUserAdapterDataModels.clear()
        thousandGroupUserAdapterDataModels.addAll(thousandGroupUserItemModels)
        thousandsGroupUsersRecycleAdapter.notifyDataSetChanged()
      }else{
        swipeRefreshLayout.setEnabled(false)
        //filter ! s
      }

    }
  }

//  filter.throttle(500.millis).onUi{
//    case key =>
//      if(StringUtils.isNotBlank(key)) searchThousandsGroupConversationUsers(rConvId,key.trim)
//  }

  override def onAttach(context: Context): Unit = {
    super.onAttach(context)
    if (context.isInstanceOf[OnSelectUserDataListener]) selectUserDataListener = context.asInstanceOf[OnSelectUserDataListener]
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    verbose(l"onCreate")

    val bundle = if (savedInstanceState == null) {
      getArguments
    } else {
      savedInstanceState
    }
    rConvId = bundle.getSerializable(classOf[RConvId].getSimpleName).asInstanceOf[RConvId]
    groupUserType = bundle.getInt(INTENT_KEY_show_thousands_groupuser)
    creatorId = bundle.getSerializable(classOf[UserId].getSimpleName).asInstanceOf[UserId]
    allowUserAddFriend = bundle.getBoolean(INTENT_KEY_allowUserAddFriend, false)

  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)

    outState.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    outState.putInt(INTENT_KEY_show_thousands_groupuser, groupUserType)
    outState.putSerializable(classOf[UserId].getSimpleName, creatorId)
    outState.putBoolean(INTENT_KEY_allowUserAddFriend, allowUserAddFriend)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    verbose(l"onCreateView")
    val rootView = inflater.inflate(R.layout.fragment_thousands_group_users, container, false)
    selfUserId = SpUtils.getUserId(getContext)
    swipeRefreshLayout = ViewUtils.getView(rootView, R.id.swipeRefreshLayout)
    recyclerView = ViewUtils.getView(rootView, R.id.recyclerView)
    searchBox = Option(ViewUtils.getView(rootView, R.id.sbv__search_box))

    rootView
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    searchBox.foreach { it =>
      it.setCallback(searchBoxViewCallback)
      it.setInputType(InputType.TYPE_CLASS_TEXT)
      it.applyDarkTheme(true)
      it.setOnEditorActionListener(new OnEditorActionListener {
        override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean =
          if (actionId == EditorInfo.IME_ACTION_SEARCH){
            val searchText = it.getSearchFilter.trim
            if(StringUtils.isNotBlank(searchText)) searchThousandsGroupConversationUsers(rConvId,searchText)
            keyboard.hideKeyboardIfVisible()
          } else false
      })
    }

    recyclerView.setLayoutManager(new LinearLayoutManager(getActivity, OrientationHelper.VERTICAL, false))

    thousandsGroupUsersRecycleAdapter = new ThousandsGroupUsersRecycleAdapter(recyclerView, getActivity, thousandGroupUserAdapterDataModels, selfUserId, groupUserType, creatorId.str, new OnRecyclerItemClickDataListener[ThousandGroupUserModel.ThousandGroupUserItemModel]() {
      override def onItemViewsClick(parentView: View, viewIdClicked: Int, position: Int, thousandGroupUserItemModel: ThousandGroupUserModel.ThousandGroupUserItemModel): Unit = {
        if (groupUserType == THOUSANDS_GROUPUSER_SPEAKER || groupUserType == THOUSANDS_GROUPUSER_ADMIN || groupUserType == THOUSANDS_GROUPUSER_TRANSFER) {
          if (null != selectUserDataListener) {
            selectUserDataListener.onThousandsData(thousandGroupUserItemModel.getId, thousandGroupUserItemModel.getName, thousandGroupUserItemModel.getAsset)
          }
        } else if (groupUserType == THOUSANDS_GROUPUSER_MORE) {
          if (!selfUserId.equalsIgnoreCase(thousandGroupUserItemModel.getId)) {

            val showUserId = UserId(thousandGroupUserItemModel.getId)
            for {
              userOpt <- participantsController.getUser(showUserId)
              conversationData <- convController.currentConv.head
            } userOpt match {
              case Some(user) if user.connection == ACCEPTED || user.expiresAt.isDefined /*|| isTeamMember*/ =>
                participantsController.selectParticipant(showUserId)
                getParentFragment.getTag match {
                  case ParticipantFragment.TAG =>
                    getParentFragment.asInstanceOf[ParticipantFragment]
                      .openUserProfileFragment(SingleParticipantFragment.newInstance(), SingleParticipantFragment.Tag)
                }
              case Some(user) if user.connection == PENDING_FROM_OTHER || user.connection == PENDING_FROM_USER || user.connection == IGNORED =>
                getParentFragment.getTag match {
                  case ParticipantFragment.TAG =>
                    getParentFragment.asInstanceOf[ParticipantFragment]
                      .openUserProfileFragment(PendingConnectRequestFragment.newInstance(showUserId, UserRequester.PARTICIPANTS), PendingConnectRequestFragment.Tag)
                }

              case Some(user) if user.connection == BLOCKED =>
                getParentFragment.getTag match {
                  case ParticipantFragment.TAG =>
                    getParentFragment.asInstanceOf[ParticipantFragment]
                      .openUserProfileFragment(BlockedUserProfileFragment.newInstance(showUserId.str, UserRequester.PARTICIPANTS), BlockedUserProfileFragment.Tag)
                }

              case Some(user) if user.connection == CANCELLED || user.connection == UNCONNECTED =>
                participantsController.selectParticipant(showUserId)
                getParentFragment.getTag match {
                  case ParticipantFragment.TAG =>
                    if (SpUtils.getUserId(getContext).equalsIgnoreCase(conversationData.creator.str) || ParticipantsController.isManager(conversationData,UserId(SpUtils.getUserId(getContext)))) {
                      getParentFragment.asInstanceOf[ParticipantFragment]
                        .openUserProfileFragment(GroupUserInfoFragment.newInstance(conversationData.add_friend), GroupUserInfoFragment.TAG)
                    } else if (!conversationData.add_friend) {
                      getParentFragment.asInstanceOf[ParticipantFragment]
                        .openUserProfileFragment(GroupUserNormalFragment.newInstance(showUserId.str), GroupUserNormalFragment.TAG)
                    } else {
                      getParentFragment.asInstanceOf[ParticipantFragment]
                        .openUserProfileFragment(SendConnectRequestFragment.newInstance(showUserId.str, UserRequester.PARTICIPANTS, conversationData.add_friend), SendConnectRequestFragment.Tag)
                    }
                }
              case _ =>
                SendConnectRequestActivity.startSelf(thousandGroupUserItemModel.getId, getActivity, allowUserAddFriend, thousandGroupUserItemModel)
            }
          } else {
          }
        }
      }

      override def onItemViewsLongClick(parentView: View, viewIdClicked: Int, thousandGroupUserItemModel: ThousandGroupUserModel.ThousandGroupUserItemModel): Unit = {
      }
    })
    thousandsGroupUsersRecycleAdapter.setHeaderAndEmpty(true)
    thousandsGroupUsersRecycleAdapter.setOnLoadMoreListener(this, recyclerView)
    thousandsGroupUsersRecycleAdapter.setEnableLoadMore(false)
    recyclerView.setAdapter(thousandsGroupUsersRecycleAdapter)
    swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
      override def onRefresh(): Unit = {
        loadThousandsGroupConversationUsers(rConvId, null)
      }
    })
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    swipeRefreshLayout.setRefreshing(true)
    loadThousandsGroupConversationUsers(rConvId, null)
  }

  private def closeStartUI(): Unit = {
    keyboard.hideKeyboardIfVisible()
  }

  private def loadThousandsGroupConversationUsers(rConvId: RConvId, startUserId: String): Unit = {
    if (isLoadingData) return
    isLoadingData = true
    //activity.foreach(_.showProgressDialog(R.string.loading))
    val urlPath = new StringBuilder().append("conversations/").append(rConvId).append("/members")
    val params = new util.HashMap[String, Any]
    if (!TextUtils.isEmpty(startUserId)) params.put("start", startUserId)
    params.put("size", PAGE_SIME)
    SpecialServiceAPI.getInstance.get(urlPath.toString, params, new OnHttpListener[ThousandGroupUserModel]() {
      override def onFail(code: Int, err: String): Unit = {

        activity.foreach { activity =>
          //activity.dismissProgressDialog()
          activity.showToast(err)
        }

      }

      override def onSuc(thousandGroupUserModel: ThousandGroupUserModel, orgJson: String): Unit = {
        //activity.foreach(_.dismissProgressDialog())
        if (TextUtils.isEmpty(startUserId)) thousandGroupUserItemModels.clear()
        //Gson gson = new Gson();
        //ThousandGroupUserModel thousandGroupUserModel = gson.fromJson(orgJson, ThousandGroupUserModel.class);
        if (thousandGroupUserModel != null) {
          thousandsGroupUsersRecycleAdapter.setEnableLoadMore(thousandGroupUserModel.isHas_more)
          if (thousandGroupUserModel.getConversations != null) {
            thousandGroupUserItemModels.addAll(thousandGroupUserModel.getConversations)
          }
          thousandGroupUserAdapterDataModels .clear()
          thousandGroupUserAdapterDataModels.addAll(thousandGroupUserItemModels)
          thousandsGroupUsersRecycleAdapter.notifyDataSetChanged()
        }
      }

      override def onSuc(r: util.List[ThousandGroupUserModel], orgJson: String): Unit = {
      }

      override def onComplete(): Unit = {
        super.onComplete()
        isLoadingData = false
        swipeRefreshLayout.setRefreshing(false)
        thousandsGroupUsersRecycleAdapter.loadMoreComplete()
      }
    })
  }

  /**
    * https://account.isecret.im/conversations/5d9e7196-7de4-4c83-8e2f-f10fb14b68c1/search?q=aa&size=100
    * @param rConvId
    * @param key
    */
  private def searchThousandsGroupConversationUsers(rConvId: RConvId, key: String): Unit = {

    def searchPath(rconvId : String) = s"conversations/$rconvId/search"

    val params = new util.HashMap[String, Any]
    params.put("q", key)
    params.put("size", 100)

    activity.foreach(_.showProgressDialog(R.string.loading))
    SpecialServiceAPI.getInstance.get(searchPath(rConvId.str),params, new OnHttpListener[ThousandGroupUserItemModel]() {
      override def onFail(code: Int, err: String): Unit = {

        activity.foreach { activity =>
          activity.dismissProgressDialog()
          activity.showToast(err)
        }

      }

      override def onSuc(model: ThousandGroupUserItemModel, orgJson: String): Unit = {

      }

      override def onSuc(r: util.List[ThousandGroupUserItemModel], orgJson: String): Unit = {
        activity.foreach(_.dismissProgressDialog())

        if (r != null) {
          thousandGroupUserSearchResultItemModels.clear()
          thousandGroupUserSearchResultItemModels.addAll(r)
        }
        thousandGroupUserAdapterDataModels.clear()
        thousandGroupUserAdapterDataModels.addAll(thousandGroupUserSearchResultItemModels)
        thousandsGroupUsersRecycleAdapter.notifyDataSetChanged()
        thousandsGroupUsersRecycleAdapter.loadMoreEnd()
      }

      override def onComplete(): Unit = {
        super.onComplete()
      }
    })
  }

  def activity: Option[BaseActivity] = if (getActivity != null && getActivity.isInstanceOf[BaseActivity]) Option(getActivity.asInstanceOf[BaseActivity]) else None

  override def onLoadMoreRequested(): Unit = {
    val nextLoadId = thousandGroupUserItemModels.lastOption.fold("")(_.getId)
    loadThousandsGroupConversationUsers(rConvId, nextLoadId)
  }
}

object ThousandsGroupUsersFragment {
  val TAG: String = classOf[ThousandsGroupUsersFragment].getSimpleName

  val INTENT_KEY_allowUserAddFriend = "allowUserAddFriend"
  val INTENT_KEY_show_thousands_groupuser = "show_thousands_groupuser"

  val THOUSANDS_GROUPUSER_MORE = 1

  val THOUSANDS_GROUPUSER_SPEAKER = 2

  val THOUSANDS_GROUPUSER_ADMIN = 3

  val THOUSANDS_GROUPUSER_TRANSFER = 4

  private val PAGE_SIME = 50


  def newInstance(rConvId: RConvId, groupUserType: Int, allowUserAddFriend: Boolean = false, creatorId: UserId = null): ThousandsGroupUsersFragment = {
    val fragment = new ThousandsGroupUsersFragment
    val bundle = new Bundle
    bundle.putInt(INTENT_KEY_show_thousands_groupuser, groupUserType)
    bundle.putBoolean(INTENT_KEY_allowUserAddFriend, allowUserAddFriend)
    bundle.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    bundle.putSerializable(classOf[UserId].getSimpleName, creatorId)
    fragment.setArguments(bundle)
    fragment
  }


}
