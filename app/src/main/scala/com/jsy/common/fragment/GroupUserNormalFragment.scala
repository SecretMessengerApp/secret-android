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
package com.jsy.common.fragment

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import com.waz.model.UserId
import com.waz.utils.returning
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.messages.UsersController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.connect.UserProfileContainer
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{FragmentHelper, R}

class GroupUserNormalFragment extends BaseFragment[GroupUserNormalFragment.Container]
  with FragmentHelper {

  implicit def context: Context = getActivity

  private lazy val userToConnectId = UserId(getArguments.getString(GroupUserNormalFragment.ArgumentUserId))

  private lazy val usersController = inject[UsersController]

  private lazy val user = usersController.user(userToConnectId)

  private lazy val imageViewProfile = view[ChatHeadViewNew](R.id.send_connect)

  private lazy val userNameView = returning(view[TypefaceTextView](R.id.user_name)) { vh =>
    user.map(_.getDisplayName).onUi(t => vh.foreach(_.setText(t)))
  }

  //private lazy val userHandleView = returning(view[TypefaceTextView](R.id.user_handle)) { vh =>
  //  user.map(user => StringUtils.formatHandle(user.handle.map(_.string).getOrElse("")))
  //    .onUi(t => vh.foreach(_.setText(t)))
  //}

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_group_normal_user_info, viewContainer, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {

    userNameView
    //userHandleView

    Option(view.findViewById[View](R.id.user_handle)).foreach(_.setVisibility(View.GONE))

    (for {
      user <- user
    } yield user).onUi(it => imageViewProfile.foreach(_.setUserData(it)))

    val backgroundContainer = findById[View](R.id.background_container)
    backgroundContainer.setClickable(true)
  }
}

object GroupUserNormalFragment {

  val TAG: String = classOf[GroupUserNormalFragment].getName

  val ArgumentUserId = "ARGUMENT_USER_ID"

  def newInstance(userId: String): GroupUserNormalFragment =
    returning(new GroupUserNormalFragment)(fragment =>
      fragment.setArguments(returning(new Bundle) { args =>
        args.putString(ArgumentUserId, userId)
      })
    )

  trait Container extends UserProfileContainer {
  }

}
