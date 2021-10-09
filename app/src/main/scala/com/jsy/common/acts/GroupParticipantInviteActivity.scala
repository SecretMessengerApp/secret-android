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
package com.waz.zclient.participants

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.widget.Toolbar
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.model.{GroupParticipantInviteConfirmModel, HttpResponseBaseModel}
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.conversationlist.{ConversationListAdapter, ConversationListController}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.participants.fragments.ParticipantHeaderFragment.TAG
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{BaseActivity, R}
import org.json.JSONObject


class GroupParticipantInviteActivity extends BaseActivity
  with View.OnClickListener
  with DerivedLogTag {

  private var toolbar: Toolbar = _
  private var header: TypefaceTextView = _

  private var llEnterConversation: View= _
  private var tvEnterConversation: TypefaceTextView = _

  private var llToJoinOrNot: View = _
  private var tvConfirmJoin: TypefaceTextView = _
  private var tvRefuse: TypefaceTextView = _

  private var civConversationHead: ChatHeadViewNew = _
  private var tvConvName: TypefaceTextView = _
  private var tvConvMemsum: TypefaceTextView = _


  private lazy val convListController = inject[ConversationListController]
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val createConvController = inject[CreateConversationController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]

  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account }

  private var groupParticipantInviteConfirmModel: GroupParticipantInviteConfirmModel = _
  private var other: UserData = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      groupParticipantInviteConfirmModel = getIntent.getSerializableExtra(classOf[GroupParticipantInviteConfirmModel].getSimpleName).asInstanceOf[GroupParticipantInviteConfirmModel]
      other = getIntent.getSerializableExtra(classOf[UserData].getSimpleName).asInstanceOf[UserData]
    } else {
      groupParticipantInviteConfirmModel = savedInstanceState.getSerializable(classOf[GroupParticipantInviteConfirmModel].getSimpleName).asInstanceOf[GroupParticipantInviteConfirmModel]
      other = savedInstanceState.getSerializable(classOf[UserData].getSimpleName).asInstanceOf[UserData]
    }

    setContentView(R.layout.activity_group_participant_invite)
    header = findViewById(R.id.header)
    toolbar = findViewById(R.id.toolbar)
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        finish()
      }
    })
    tvConfirmJoin = findViewById(R.id.tvConfirmJoin)

    tvEnterConversation = findViewById(R.id.tvEnterConversation)
    llEnterConversation = findViewById(R.id.llEnterConversation)
    tvRefuse = findViewById(R.id.tvRefuse)
    civConversationHead = findById(R.id.civConversationHead)
    tvConvName = findViewById(R.id.tvConvName)
    tvConvMemsum = findViewById(R.id.tvConvMemsum)
    llToJoinOrNot = findViewById(R.id.llToJoinOrNot)

    tvEnterConversation.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        convListController.conversationGroupOrThousandsGroupList(ConversationListAdapter.GroupOrThousandGroup).currentValue.foreach { selfId_groups =>
          val existedConve = selfId_groups._2.filter { c =>
            c.remoteId.str.equals(groupParticipantInviteConfirmModel.msgData.conversationId) && c.isActive
          }
          existedConve.foreach{toCconv=>
            convController.selectConv(Option(existedConve.head.id), ConversationChangeRequester.CONVERSATION_LIST)
            finish()
          }
        }
      }
    })

    val you = getResources.getString(R.string.content__system__you)
    header.setText(String.format(getResources.getString(R.string.group_participant_invite_title), you))

    tvConvName.setText(groupParticipantInviteConfirmModel.msgData.name)
    val s = String.format(getResources.getString(R.string.group_participant_invite_total_memsum), String.valueOf(groupParticipantInviteConfirmModel.msgData.memberCount))
    tvConvMemsum.setText(s)

    tvConfirmJoin.setOnClickListener(this)
    tvRefuse.setOnClickListener(this)

    civConversationHead.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(groupParticipantInviteConfirmModel.msgData.asset, this), MessageContentUtils.getGroupDefaultAvatar(groupParticipantInviteConfirmModel.msgData.conversationId))

    def showJoinAndRefuse(): Unit = {
      llToJoinOrNot.setVisibility(View.VISIBLE)
      llEnterConversation.setVisibility(View.GONE)
    }

    def showJoined(): Unit = {
      llToJoinOrNot.setVisibility(View.GONE)
      llEnterConversation.setVisibility(View.VISIBLE)
    }

    convListController.conversationGroupOrThousandsGroupList(ConversationListAdapter.GroupOrThousandGroup).onUi {
      case (aId, groupOrThousandsGroup) =>
        if (groupOrThousandsGroup.exists { conv =>
          conv.remoteId.str.equals(groupParticipantInviteConfirmModel.msgData.conversationId) && conv.isActive
        }) {
          showJoined()
        } else {
          showJoinAndRefuse()
        }
    }

    val groupOrThousandsGroup = convListController.conversationGroupOrThousandsGroupList(ConversationListAdapter.GroupOrThousandGroup).currentValue
    if (groupOrThousandsGroup.isEmpty) {
      showJoinAndRefuse()
    } else {
      groupOrThousandsGroup.foreach { groupOrThousandsGroup =>
        if (groupOrThousandsGroup._2.exists { conv =>
          conv.remoteId.str.equals(groupParticipantInviteConfirmModel.msgData.conversationId) && conv.isActive
        }) {
          showJoined()
        } else {
          showJoinAndRefuse()
        }
      }
    }

  }

  override def canUseSwipeBackLayout: Boolean = true

  override def onClick(v: View): Unit = {
    val vId = v.getId
    if (vId == R.id.tvRefuse) {
      finish()
    } else if (vId == R.id.tvConfirmJoin) {
      currentUser.currentValue.foreach { currentUser =>
        SpecialServiceAPI.getInstance().post(String.format(ImApiConst.GroupParticipantInviteConfirmToJoin, groupParticipantInviteConfirmModel.msgData.conversationId, other.id.str),
          new JSONObject().toString(), new OnHttpListener[HttpResponseBaseModel] {

            override def onFail(code: Int, err: String): Unit = {
              verbose(l"$TAG code-$code err->$err")
              runOnUiThread(new Runnable {
                override def run(): Unit = {
                  showToast(err)
                }
              })
            }

            override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
              verbose(l"$TAG orgJson-$orgJson")
              runOnUiThread(new Runnable {
                override def run(): Unit = {
                  finish()
                }
              })
            }

            override def onSuc(r: java.util.List[HttpResponseBaseModel], orgJson: String): Unit = {
              verbose(l"$TAG orgJson-$orgJson")

            }

            override def onComplete(): Unit = {
              super.onComplete()
            }
          })

      }

    } else {
      //...
    }
  }

  override protected def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putSerializable(classOf[GroupParticipantInviteConfirmModel].getSimpleName, groupParticipantInviteConfirmModel)
    outState.putSerializable(classOf[UserData].getSimpleName, other)
  }

}

object GroupParticipantInviteActivity {

  def startSelf(context: Context, groupParticipantInviteConfirmModel: GroupParticipantInviteConfirmModel, other: UserData): Unit = {
    val intent = new Intent(context, classOf[GroupParticipantInviteActivity])
    intent.putExtra(classOf[GroupParticipantInviteConfirmModel].getSimpleName, groupParticipantInviteConfirmModel)
    intent.putExtra(classOf[UserData].getSimpleName, other)
    context.startActivity(intent)
  }

}
