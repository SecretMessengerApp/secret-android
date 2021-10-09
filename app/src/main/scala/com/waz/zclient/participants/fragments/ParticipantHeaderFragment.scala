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
package com.waz.zclient.participants.fragments

import java.util

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view._
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.jsy.common.dialog.InviteJoinGroupDialog
import com.jsy.common.dialog.InviteJoinGroupDialog.OnInviteClikeCallback
import com.jsy.common.fragment.{ForbiddenOptionsFragment, ParticipantDeviceFragment}
import com.jsy.common.httpapi.{OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.{GroupParticipantInviteConfirmModel, HttpResponseBaseModel}
import com.jsy.common.utils.{MessageUtils, ToastUtil}
import com.jsy.res.utils.ViewUtils
import com.waz.model.ConversationData.ConversationType
import com.waz.utils.events.Signal
import com.waz.zclient.ManagerFragment.Page
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{AddParticipantsFragment, CreateConversationController}
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, ManagerFragment, R, ZApplication}
import org.json.{JSONArray, JSONObject}

import scala.util.Success

class ParticipantHeaderFragment(fromDeepLink: Boolean = false) extends FragmentHelper {

  implicit def cxt: Context = getActivity

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController = inject[ThemeController]
  private lazy val newConvController = inject[CreateConversationController]
  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)
  private lazy val screenController = inject[IConversationScreenController]
  private lazy val convController = inject[ConversationController]

  private lazy val page = Option(getParentFragment) match {
    case Some(f: ManagerFragment) => f.currentContent
    case _ => Signal.const(Option.empty[Page])
  }

  private lazy val pageTag = page.map(_.map(_.tag))

  private lazy val addingUsers = pageTag.map(_.contains(AddParticipantsFragment.Tag))

  private var toolbar: Option[Toolbar] = None

  private lazy val potentialMemberCount =
    for {
      members <- participantsController.otherParticipants
      newUsers <- newConvController.users
      newIntegrations <- newConvController.integrations
    } yield (members ++ newUsers).size + newIntegrations.size + 1


  private var confButton: Option[TextView] = None

  private var inviteJoinGroupDialog: InviteJoinGroupDialog = _

  def showInviteConfirm(): Unit = {
    if (inviteJoinGroupDialog == null) {
      inviteJoinGroupDialog = new InviteJoinGroupDialog(getActivity, new OnInviteClikeCallback {

        override def onNotSaveClick(): Unit = {
          inviteJoinGroupDialog.dismiss()
        }

        override def onSaveClick(inviteExcuse: String): Unit = {
          inviteJoinGroupDialog.dismiss()
          newConvController.addConfirmUsersToConversation(inviteJoinGroupDialog.getEditText, true)
          getActivity.onBackPressed()
        }
      })
    }
    inviteJoinGroupDialog.show()
  }

  private def addMemberToTransferGroup(): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val users = newConvController.users.head
    //val tokenType = SpUtils.getTokenType(getActivity)
    //val token = SpUtils.getToken(getActivity)
    val array = new JSONArray()
    users onComplete {
      case Success(x) =>
        x.map {
          case uid =>
            array.put(uid.str)
        }
        val jsonObject = new JSONObject()
        jsonObject.put("users", array)
        jsonObject.put("reason", inviteJoinGroupDialog.getEditText)
        val userName = SpUtils.getUserName(ZApplication.getInstance(), SpUtils.SP_KEY_USERNAME)
        jsonObject.put("name", userName)

        val urlPath = new StringBuilder().append(String.format("conversations/%s/members/conf", participantsController.conv.currentValue.get.remoteId.str)).toString
        SpecialServiceAPI.getInstance().post(urlPath, jsonObject.toString, new OnHttpListener[HttpResponseBaseModel] {
          override def onFail(code: Int, err: String): Unit = {
            ToastUtil.toastByString(getContext, getString(R.string.conversation_send_failure))
          }

          override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
            if (!TextUtils.isEmpty(orgJson)) {
              if (null != r && r.getCode == 200) {
                ToastUtil.toastByString(getContext, getString(R.string.conversation_detail_add_member))
                getActivity.onBackPressed()
              } else {
                ToastUtil.toastByString(getContext, r.getMsg)
              }
            }
          }

          override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {
          }
        })
      case _ =>
    }
  }

  private var closeButton: Option[TextView] = None

  private var headerReadOnlyTextView: Option[TextView] = None

  private var headerUsername: Option[TextView] = None

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val rootView = inflater.inflate(R.layout.fragment_participants_header, container, false)
    toolbar = Option(ViewUtils.getView(rootView, R.id.t__participants__toolbar))
    toolbar.foreach { v =>
      (for {
        p <- page
        dark <- themeController.darkThemeSet
      } yield
        p match {
          case Some(Page(AddParticipantsFragment.Tag, _)) => Some(if (dark) R.drawable.ic_action_close_light else R.drawable.ic_action_close_dark)
          case Some(Page(_, false)) => Some(if (dark) R.drawable.action_back_light else R.drawable.action_back_dark)
          case _ => None
        })
        .onUi {
            case Some(res) => v.setNavigationIcon(res)
            case None => v.setNavigationIcon(null) //can't squash these calls - null needs to be of type Drawable, not int
        }
    }

    headerReadOnlyTextView = Option(ViewUtils.getView(rootView, R.id.participants__header))
    headerReadOnlyTextView.foreach { vh =>
      pageTag.flatMap {
        case Some(GroupParticipantsFragment.Tag | GuestOptionsFragment.Tag) =>
          Signal.const(getString(R.string.participants_details_header_title))
        case Some(EphemeralOptionsFragment.Tag) =>
          Signal.const(getString(R.string.ephemeral_message__options_header))
        case Some(AddParticipantsFragment.Tag) =>
          Signal(newConvController.users, newConvController.integrations).map {
            case (u, i) if u.isEmpty && i.isEmpty => getString(R.string.add_participants_empty_header)
            case (u, i) => getString(R.string.add_participants_count_header, (u.size + i.size).toString)
          }
        case Some(AllGroupParticipantsFragment.Tag) =>
          Signal.const(getString(R.string.participant_search_title))
        case Some(ForbiddenOptionsFragment.TAG) =>
          Signal.const(getString(R.string.conversation_detail_settings_forbidden_option_title))
        case Some(ParticipantDeviceFragment.Tag) =>
          participantsController.otherParticipant.map {
            userdata =>
              if (userdata.name.nonEmpty) {
                userdata.name.str
              } else {
                getString(R.string.empty_string)
              }
          }
        case _ =>
          Signal.const(getString(R.string.empty_string))
      }.onUi { t =>
        vh.setVisible(t.nonEmpty)
        vh.setText(t)
      }
    }

    headerUsername = Option(ViewUtils.getView(rootView, R.id.participants__header__username))

    closeButton = Option(ViewUtils.getView(rootView, R.id.close_button))
    closeButton.foreach { vh =>
      addingUsers.map(!_).onUi(vis => vh.setVisible(vis))
      vh.onClick {
        KeyboardUtils.hideSoftInput(getActivity)
        participantsController.onHideParticipants ! true
      }

    }


    confButton = Option(ViewUtils.getView(rootView, R.id.confirmation_button))
    confButton.foreach { vh =>

      val confButtonEnabled = Signal(newConvController.users.map(_.size), newConvController.integrations.map(_.size), potentialMemberCount).map {
        case (newUsers, newIntegrations, potential) =>
          (newUsers > 0 || newIntegrations > 0) && (participantsController.conv.currentValue.head.convType == ConversationType.ThousandsGroup || potential <= ConversationController.MaxParticipants)
      }
      confButtonEnabled.onUi(e => vh.setEnabled(e))

      confButtonEnabled.flatMap {
        case false => Signal.const(getColor(R.color.teams_inactive_button))
        case _ => accentColor
      }.onUi(c => vh.setTextColor(c))

      addingUsers.onUi(vis => vh.setVisible(vis))
      vh.onClick {
        val conv = participantsController.conv.currentValue.head
        val isCreator = SpUtils.getUserId(getContext).equalsIgnoreCase(participantsController.conv.currentValue.get.creator.str)
        val confirm = participantsController.conv.currentValue.get.confirm
        val addright = participantsController.conv.currentValue.get.addright

        val memberjoin_confirm = participantsController.conv.currentValue.get.memberjoin_confirm

        if (isCreator) {
          newConvController.addUsersToConversation(false)
          getActivity.onBackPressed()
        } else if (!isCreator && !addright && !confirm && !memberjoin_confirm) {
          newConvController.addUsersToConversation(false)
          getActivity.onBackPressed()
        } else if (!isCreator && !addright && confirm && !memberjoin_confirm) {
          showInviteConfirm()
        } else if (!isCreator && !addright && !confirm && memberjoin_confirm) {
          convController.currentConv.currentValue.foreach { conversationData =>
            val groupParticipantInviteConfirmModel: GroupParticipantInviteConfirmModel = GroupParticipantInviteConfirmModel.initByConversationData(conversationData)
            val textJson = MessageUtils.createGroupParticipantInviteMsgJson(groupParticipantInviteConfirmModel).toString()
            newConvController.addUsersToConversationByParticipant(textJson)
          }
          getActivity.onBackPressed()
        } else {
          newConvController.addUsersToConversation(false)
          getActivity.onBackPressed()
        }
      }
    }


    rootView
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    pageTag.onUi {
      case Some(SingleParticipantFragment.Tag) =>
        headerUsername.get.setVisible(true)
      case _ =>
        headerUsername.get.setVisible(false)
    }
  }

  override def onResume(): Unit = {
    super.onResume()
    toolbar.foreach(_.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = onBackPressed()
    }))
  }

  override def onPause(): Unit = {
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onPause()
  }

  override def onBackPressed(): Boolean = {
    getParentFragment match {
      case parentFragment: ParticipantFragment =>
        parentFragment.onBackPressed()
      case _ =>
        getActivity.onBackPressed()
    }
    true
  }
}

object ParticipantHeaderFragment {
  val TAG: String = classOf[ParticipantHeaderFragment].getName

  def newInstance: ParticipantHeaderFragment = new ParticipantHeaderFragment
}
