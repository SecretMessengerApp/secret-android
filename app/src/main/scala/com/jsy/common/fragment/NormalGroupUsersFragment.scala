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

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.TransferGroupAdapter
import com.jsy.common.adapter.TransferGroupAdapter.TransferGroupAdapterCallback
import com.jsy.common.listener.OnSelectUserDataListener
import com.waz.content.UsersStorage
import com.waz.model.{RConvId, UserData}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.{FragmentHelper, R}
import timber.log.Timber

class NormalGroupUsersFragment extends /*BaseFragment[NormalGroupUsersFragment.Container]*/ FragmentHelper {
  implicit def ctx: Context = getActivity

  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]
  private lazy val themeController = inject[ThemeController]
  private lazy val participantsView = view[RecyclerView](R.id.pgv__participants)

  private var selectUserDataListener: OnSelectUserDataListener = null
  private var groupUsersAdapter: TransferGroupAdapter = _

  override def onAttach(context: Context): Unit = {
    super.onAttach(context)
    if (context.isInstanceOf[OnSelectUserDataListener]) {
      selectUserDataListener = context.asInstanceOf[OnSelectUserDataListener]
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_group_normalusers, viewGroup, false)
  }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    groupUsersAdapter = new TransferGroupAdapter(themeController.isDarkTheme || !isAddingToConversation, new TransferGroupAdapterCallback {
      override def onContactListUserClicked(userData: UserData): Unit = {
        if (null != selectUserDataListener) {
          selectUserDataListener.onNormalData(userData);
        }
      }
    })
    convController.currentConv.currentValue.foreach { conversationData =>
      //      val userId = SpUtils.getUserId(getContext)
      participantsView.foreach { v =>
        v.setVisibility(View.VISIBLE)
        v.setLayoutManager(new LinearLayoutManager(getActivity))
        v.setAdapter(groupUsersAdapter)
        getUserListData()
      }
    }
  }

  def getUserListData(): Unit = {
    val userIds = participantsController.otherParticipants.map(_.toSeq)

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
        groupUsersAdapter.setUserData(participants);
    }
  }

  private def isAddingToConversation: Boolean = {
    //getArguments.getBoolean(GroupTransferFragment.ARGUMENT_ADD_TO_CONVERSATION)
    false
  }
}

object NormalGroupUsersFragment {

  trait Container {}

  val TAG = NormalGroupUsersFragment.getClass.getSimpleName

  def newInstance() = {
    Timber.d("NormalGroupUsersFragment#newInstance object")
    new NormalGroupUsersFragment()
  }

  def newInstance(rConvId: RConvId): NormalGroupUsersFragment = returning(new NormalGroupUsersFragment) { f =>
    val bundle = new Bundle
    bundle.putSerializable(classOf[RConvId].getSimpleName, rConvId)
    f.setArguments(bundle)
  }

}
