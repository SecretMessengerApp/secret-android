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
package com.waz.zclient.participants.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CompoundButton, LinearLayout, TextView}
import androidx.annotation.Nullable
import androidx.appcompat.app.{AlertDialog, AppCompatActivity}
import com.jsy.common.acts.{SelectChatBackgroundActivity, UserRemarkActivity}
import com.jsy.common.fragment.{ForbiddenOptionsFragment, ParticipantDeviceFragment}
import com.jsy.common.httpapi.{NormalServiceAPI, OnHttpListener}
import com.jsy.common.moduleProxy.ProxyConversationActivity
import com.jsy.common.utils.MessageUtils
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.res.utils.ViewUtils
import com.waz.api.IConversation
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.{BrowserController, ThemeController, UserAccountsController}
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.UsersController
import com.waz.zclient.messages.UsersController.DisplayName.Other
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants._
import com.waz.zclient.preferences.views.{SwitchPreference, TextButton}
import com.waz.zclient.utils.{MainActivityUtils, RichView, SpUtils, StringUtils}
import com.waz.zclient.{BaseActivity, FragmentHelper, R}
import org.json.{JSONException, JSONObject}
import timber.log.Timber

import java.util
import scala.concurrent.Future


class SingleParticipantFragment extends FragmentHelper with View.OnClickListener {

  import Threading.Implicits.Ui

  private implicit lazy val ctx = getContext
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val users = inject[UsersController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val themeController = inject[ThemeController]
  private lazy val screenController = inject[IConversationScreenController]
  private lazy val userAccountsController = inject[UserAccountsController]
  private lazy val browserController = inject[BrowserController]
  private lazy val createConvController = inject[CreateConversationController]
  private lazy val collectionController = inject[CollectionController]

  private var subs = Set.empty[com.waz.utils.events.Subscription]

  private var blockUser: Option[SwitchPreference] = None
  private var sPvibration: Option[SwitchPreference] = None
  private var topChangeSwitchPreference: Option[SwitchPreference] = None


  private var userName: Option[TextView] = None
  private var userHandle: Option[TextView] = None
  private var userRemark: Option[TextButton] = None
  private var groupChatHistory: Option[TextButton] = None
  private var userChatBackground: Option[TextButton] = None
  private var removeUser: Option[TextButton] = None
  private var startChat: Option[TextButton] = None
  private var createGroupChat: Option[TextButton] = None
  private var forbiddenSettingLayout: Option[TextButton] = None
  private var burnAfterReadingLayout: Option[TextButton] = None
  private var sPDefaultGroup: SwitchPreference = _
  private var singleChatMsgEdit: SwitchPreference = _

  private val topChangeListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        if (isChecked) {
          convController.setPlaceTop(conversationData.id, place_top = isChecked)
        } else {
          convController.setPlaceTop(conversationData.id, place_top = isChecked)
        }
      }
    }
  }

  private val sPvibrationListener: CompoundButton.OnCheckedChangeListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        if (isChecked) {
          convController.setMuted(conversationData.id, muted = MuteSet.AllMuted)
        } else {
          convController.setMuted(conversationData.id, muted = MuteSet.AllAllowed)
        }
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_participants_single_tabbed, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)


    userName = Option(findById[TextView](R.id.user_name))
    userHandle = Option(findById[TextView](R.id.user_handle))
    userRemark = Option(findById[TextButton](R.id.user_chat_remark))
    groupChatHistory = Option(findById[TextButton](R.id.group_chat_history))
    userChatBackground = Option(findById[TextButton](R.id.user_chat_background))
    removeUser = Option(findById[TextButton](R.id.remove_user))
    startChat = Option(findById[TextButton](R.id.user_start_chat))
    createGroupChat = Option(findById[TextButton](R.id.user_create_group_chat))
    forbiddenSettingLayout = Option(findById[TextButton](R.id.forbidden_setting_layout))
    burnAfterReadingLayout = Option(findById[TextButton](R.id.burn_after_reading))
    sPDefaultGroup = ViewUtils.getView(v, R.id.preferences_default_group)
    singleChatMsgEdit = findById[SwitchPreference](R.id.single_chat_message_edit)


    convController.currentConv.currentValue.foreach { conversationData =>
      val addedMainTabCheck = convController.existsGroupData(conversationData.id,conversationData.remoteId)
      sPDefaultGroup.setVisibility(if (convController.localGroupDataNum()< ConversationOptionsMenuController.COLLECT_GROUP_LIMIT_COUNT || addedMainTabCheck) View.VISIBLE else View.GONE)
      sPDefaultGroup.setChecked(checked = addedMainTabCheck, disableListener = true)
      singleChatMsgEdit.setVisibility(View.GONE)
      setViewListener(conversationData)
    }

    userName.foreach { v =>
      subs += participantsController.otherParticipant.map(u => (u.remark, u.getDisplayName)).onUi { parts =>
        val showName = parts._1.fold(parts._2.str) {
          str => s"${str}(${parts._2.str})"
        }
        v.setText(showName)
      }
    }

    userHandle.foreach { v =>
      val handle = participantsController.otherParticipant.map(_.handle.map(_.string))

      subs += handle
        .map(_.isDefined)
        .onUi(vis => v.setVisible(vis))

      subs += handle
        .map {
          case Some(h) => StringUtils.formatHandle(h)
          case _ => ""
        }.onUi(str => v.setText(str))
    }

    userRemark.foreach { v =>
      subs += participantsController.otherParticipant.map(_.remark).onUi {
        case Some(remark) =>
          v.setSubtitle(remark)
      }
      subs += v.onClickEvent.onUi { _ =>
        participantsController.otherParticipant.currentValue.foreach { other =>
          UserRemarkActivity.startSelfForResult(getActivity, other.id, other.remark.getOrElse(""), MainActivityUtils.REQUEST_CODE_CHANGE_CONVERSATION_ONE_TO_ONE_REMARK)
        }
      }
    }

    groupChatHistory.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        collectionController.openCollection()
      }
    }

    userChatBackground.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        convController.currentConv.currentValue.foreach { conversationData =>
          val spKey = SpUtils.getConversationBackgroundSpKey(getContext, conversationData.remoteId.str)
          SelectChatBackgroundActivity.startSelfForResult(getActivity, MainActivityUtils.REQUEST_CODE_SELECT_CHAT_BACKGROUND, spKey)
        }
      }
    }


    removeUser.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        participantsController.otherParticipantId.currentValue.foreach {
          case Some(userId) => participantsController.showRemoveConfirmation(userId)
          //screenController.hideUser()
          //convController.removeMember(userId)
          case _ =>
        }
      }
    }

    startChat.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        Timber.d("startChat")
        convController.currentConv.currentValue.foreach {
          case conversation =>
            if (conversation.convType == IConversation.Type.ONE_TO_ONE) {
              screenController.hideUser()
              userAccountsController.getOrCreateAndOpenConvFor(UserId(conversation.id.str))
            } else {
              if (participantsController.selectedParticipant.currentValue.nonEmpty) {
                screenController.hideUser()
                userAccountsController.getOrCreateAndOpenConvFor(participantsController.selectedParticipant.currentValue.get.get)
              }
            }
        }
      }
    }

    createGroupChat.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        Timber.d("createGroupChat")
        participantsController.otherParticipant.map(_.expiresAt.isDefined).head.foreach {
          case false => participantsController.isGroup.head.flatMap {
            case false => userAccountsController.hasCreateConvPermission.head.map {
              case true =>
                activity.foreach(_.showCreateGroupConversationFragment())
              case _ => //
            }
            case _ => Future.successful {
              participantsController.onHideParticipants ! true
              participantsController.otherParticipantId.head.foreach {
                case Some(userId) =>
                  screenController.hideUser()
                  userAccountsController.getOrCreateAndOpenConvFor(userId)
                case _ =>
              }
            }
          }
          case _ =>
        }
      }
    }

    forbiddenSettingLayout.foreach { v =>
      subs += v.onClickEvent.onUi { _ =>
        slideFragmentInFromRight(ForbiddenOptionsFragment.newInstance(), ForbiddenOptionsFragment.TAG)
      }
    }

    burnAfterReadingLayout.foreach { burnAfterReadingLayout =>
      subs += burnAfterReadingLayout.onClickEvent.onUi { _ =>
        slideFragmentInFromRight(EphemeralOptionsFragment.newInstance(EphemeralOptionsFragment.SOURCE_SINGLE), EphemeralOptionsFragment.Tag)
      }
    }

    blockUser = Option(findById[SwitchPreference](R.id.preferences_add_blacklist))
    sPvibration = Option(findById[SwitchPreference](R.id.preferences_vibration))
    topChangeSwitchPreference = Option(findById[SwitchPreference](R.id.top_change_switchPreference))

    val imageView = findById[ChatHeadViewNew](R.id.chathead)
    participantsController.otherParticipant.map(Some(_)).onUi{
      case Some(userData) =>
        imageView.setUserData(userData)
      case _ =>
    }

    convController.currentConv.map(_.place_top).onUi { place_top =>
      topChangeSwitchPreference.foreach{it =>
        it.setChecked(place_top, disableListener = true)
        it.setChangeListener(topChangeListener)
      }
    }

    convController.currentConv.map(_.isAllMuted).onUi { mute =>
      sPvibration.foreach { it =>
        it.setChecked(mute, disableListener = true)
        it.setChangeListener(sPvibrationListener)
      }
    }

    participantsController.otherParticipant.map(_.connection).onUi { status =>
      if (status == UserData.ConnectionStatus.Blocked) {
        getActivity.finish()
        convController.setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER)
      }

    }

    topChangeSwitchPreference.foreach {
      _.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          convController.currentConv.currentValue.foreach { conversationData =>
            if (isChecked) {
              convController.setPlaceTop(conversationData.id, place_top = isChecked)
            } else {
              convController.setPlaceTop(conversationData.id, place_top = isChecked)
            }
          }
        }
      })
    }

    sPvibration.foreach {
      _.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          convController.currentConv.currentValue.foreach { conversationData =>
            if (isChecked) {
              convController.setMuted(conversationData.id, muted = MuteSet.AllMuted)
            } else {
              convController.setMuted(conversationData.id, muted = MuteSet.AllAllowed)
            }
          }
        }
      })
    }

    blockUser.foreach {
      _.switch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener {
        override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
          participantsController.otherParticipant.currentValue.foreach { other =>
            if (isChecked) {
              (for {
                curConvId <- convController.currentConvId.map(Option(_)).orElse(Signal.const(Option.empty[ConvId])).head
                displayName <- users.displayName(other.id).collect { case Other(name) => name }.head //should be impossible to get Me in this case
              } yield (curConvId, displayName)).map {
                case (curConvId, displayName) =>
                  val dialog = new AlertDialog.Builder(getContext, R.style.Theme_Light_Dialog_Alert_Destructive)
                    .setCancelable(true)
                    .setTitle(R.string.confirmation_menu__block_header)
                    .setMessage(getString(R.string.confirmation_menu__block_text_with_name, displayName))
                    .setNegativeButton(R.string.confirmation_menu__confirm_block, new DialogInterface.OnClickListener {
                      override def onClick(dialog: DialogInterface, which: Int): Unit = {
                        zms.head.flatMap(_.connection.blockConnection(other.id)).map { userData =>

                          userData.fold({}) { userData =>
                            if (userData.connection == UserData.ConnectionStatus.Blocked) {
                              getActivity.finish()
                              convController.setCurrentConversationToNext(ConversationChangeRequester.BLOCK_USER)
                            }
                          }
                        }(Threading.Ui)
                      }

                    }).create
                  dialog.show()
              }(Threading.Ui)
            } else {
              zms.head.map(_.connection.unblockConnection(other.id))(Threading.Background)
            }
          }
        }
      })
    }

    findById[LinearLayout](R.id.rl_fingerprint).setOnClickListener(this)


    convController.currentConv.currentValue.foreach {
      conversationData =>

        val isGroup = Seq(IConversation.Type.THROUSANDS_GROUP, IConversation.Type.GROUP).contains(conversationData.convType)

        burnAfterReadingLayout.foreach(_.setVisibility(if (isGroup) View.GONE else View.VISIBLE))

        if (isGroup) {
          participantsController.isGroupRemoveAndForbiddenCurRight().foreach {
            isRight =>
              if (isRight) {
                getActivity.runOnUiThread(new Runnable {
                  override def run(): Unit = {
                    removeUser.foreach(_.setVisibility(View.VISIBLE))
                    forbiddenSettingLayout.foreach(_.setVisibility(View.VISIBLE))
                  }
                })
              } else {
                getActivity.runOnUiThread(new Runnable {
                  override def run(): Unit = {
                    removeUser.foreach(_.setVisibility(View.GONE))
                  }
                })
              }
          }
        } else {
          getActivity.runOnUiThread(new Runnable {
            override def run(): Unit = {
              removeUser.foreach(_.setVisibility(View.GONE))
            }
          })
        }
    }
  }

  def setViewListener(conversationData: ConversationData) = {
    sPDefaultGroup.setChangeListener(new CompoundButton.OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        if (buttonView.isPressed) {
          if (isChecked) {
            convController.addGroupData(Array(conversationData.id.str).map(ConvId))
          } else {
            convController.removeGroupData(conversationData.id)
          }
        }
      }
    })

    singleChatMsgEdit.setChangeListener(new CompoundButton.OnCheckedChangeListener {

      override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
        convController.currentConv.currentValue.foreach { conversationData =>
          if (isChecked) {

          } else {

          }
          sendSingleMsgEditMsg(conversationData.id, conversationData.remoteId, isChecked)
        }
      }
    })
  }

  private def sendSingleMsgEditMsg(convId: ConvId, remoteId: RConvId, isOpen: Boolean): Unit ={
    val msgData = new JSONObject
    val js = new JSONObject
    try {
      msgData.put("isOpen", isOpen)
      js.put(MessageUtils.KEY_TEXTJSON_MSGTYPE, if(isOpen) MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN else MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE)
      js.put(MessageUtils.KEY_TEXTJSON_MSGDATA, msgData)
    } catch {
      case e: JSONException =>
        e.printStackTrace()
    }
    convController.sendTextJsonMessage(js.toString, activity = getActivity).foreach { m =>
      m.foreach { msg =>
        verbose(l"reqSingleMsgEdit sendTextJsonMessage :${js.toString}, msg:$msg")
      }
    }
    convController.updateConvMsgEdit(convId, isOpen).foreach { c =>
      c.foreach { conv =>
        verbose(l"reqSingleMsgEdit updateConvMsgEdit enabled_edit_msg:${conv.enabled_edit_msg}, conv:$conv")
      }
    }
  }

  private def reqSingleMsgEdit(convId: ConvId, remoteId: RConvId, isOpen: Boolean): Unit ={
    getActivity match {
      case tempActivity: BaseActivity =>
        tempActivity.showProgressDialog()
      case _                       =>
    }
    NormalServiceAPI.getInstance().reqSingleMsgEdit(remoteId.str, isOpen, new OnHttpListener[String]{

      override def onFail(code: Int, err: String): Unit = {
        getActivity match {
          case tempActivity: BaseActivity =>
            tempActivity.dismissProgressDialog()
          case _                       =>
        }
      }

      override def onSuc(r: String, orgJson: String): Unit = {
        getActivity match {
          case tempActivity: BaseActivity =>
            tempActivity.dismissProgressDialog()
          case _                       =>
        }
      }

      override def onSuc(r: util.List[String], orgJson: String): Unit = {
        getActivity match {
          case tempActivity: BaseActivity =>
            tempActivity.dismissProgressDialog()
          case _                       =>
        }
      }
    })
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    subs.foreach(_.destroy())
    subs = Set.empty
    Timber.d(s"onDestroyView")
  }

  override def onBackPressed(): Boolean = {
    participantsController.selectedParticipant ! None
    super.onBackPressed()
  }

  override def onClick(v: View): Unit = {
    if (v.getId == R.id.rl_fingerprint) {
      slideFragmentInFromRight(new ParticipantDeviceFragment(getContext), ParticipantDeviceFragment.Tag)
    }
  }


  def activity: Option[ProxyConversationActivity] = {
    getActivity match {
      case tempActivity: ProxyConversationActivity => Some(tempActivity)
      case _                                       => Option.empty
    }
  }
}

object SingleParticipantFragment {
  val Tag: String = classOf[SingleParticipantFragment].getName
  val TagDevices: String = s"${classOf[SingleParticipantFragment].getName}/devices"

  private val ArgPageToOpen: String = "ARG_PAGE_TO_OPEN"

  def newInstance(pageToOpen: Option[String] = None): SingleParticipantFragment =
    returning(new SingleParticipantFragment) { f =>
      pageToOpen.foreach { p =>
        f.setArguments(returning(new Bundle) {
          _.putString(ArgPageToOpen, p)
        })
      }
    }
}
