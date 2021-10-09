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

import java.util

import android.content.{ClipData, Context}
import android.os.{Bundle, Handler, Message}
import android.text.{Selection, TextUtils}
import android.view.inputmethod.EditorInfo
import android.view.{Gravity, KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.bumptech.glide.Glide
import com.jsy.common.ConversationApi
import com.jsy.common.acts._
import com.jsy.common.dialog.TitleMsgSureDialog.OnTitleMsgSureDialogClick
import com.jsy.common.dialog.{GroupShareLinkPopupWindow, TitleMsgSureDialog}
import com.jsy.common.fragment.ThousandsGroupUsersFragment
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model._
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.popup.NoGroupNoticePopupWindow
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.utils.rxbus2.{RxBus, Subscribe, ThreadMode}
import com.jsy.common.views.CircleImageView
import com.jsy.res.utils.ViewUtils
import com.waz.api.IConversation
import com.waz.content.UsersStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events._
import com.waz.zclient._
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.{SoundController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, IConfirmationController, TwoButtonConfirmationCallback}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.{AddParticipantsFragment, CreateConversationController}
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI.verbose
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.ConversationOptionsMenuController.Mode
import com.waz.zclient.participants._
import com.waz.zclient.preferences.views.{QrCodeButton, SwitchPreference, TextButton}
import com.waz.zclient.ui.text.{TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import org.json.{JSONException, JSONObject}
import com.waz.zclient.log.LogUI._

import scala.collection.immutable.Set

trait OnUserDataListener {
  def onSuccess(userData: UserData)
}

class GroupParticipantsFragment extends FragmentHelper with View.OnClickListener with DerivedLogTag {

  implicit def ctx: Context = getActivity

  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]
  private lazy val convScreenController = inject[IConversationScreenController]
  private lazy val userAccountsController = inject[UserAccountsController]
  /*private lazy val integrationsService = inject[Signal[IntegrationsService]]*/
  private lazy val spinnerController = inject[SpinnerController]
  private lazy val collectionController = inject[CollectionController]
  private lazy val confirmationController = inject[IConfirmationController]


  private var sPDefaultGroup: SwitchPreference = _
  private var memberCountBesideSelfSystem = 0
  private var menuController: ConversationOptionsMenuController = _
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private lazy val clipboard = inject[ClipboardUtils]

  //private var mRlGroupName: RelativeLayout = _
  private var mLLGroupLinkContent: LinearLayout = _
  private var tvIsThousands: TypefaceTextView = _
  //private var tvGroupName: TypefaceTextView = _
  private var swUpgradeStatus: Switch = _
  private var shareLinkPopupWindow: GroupShareLinkPopupWindow = _
  private var preference_subtitle_thousands_group: TextView = _

  private var groupNotice: Option[TextButton] = None
  private var burnAfterReading: Option[TextButton] = None
  private var groupChatHistory: Option[TextButton] = None
  private var groupChatSetting: Option[TextButton] = None
  private var groupInviteMembers: Option[TextButton] = None
  private var setChartBackground: Option[TextButton] = None
  private var reportGroupChat: Option[TextButton] = None
  private var deleteGroupChat: Option[TextButton] = None
  private var exitGroup: Option[TextButton] = None
  private var participantsView: Option[RecyclerView] = None
  private var clAddParticipant: Option[View] = None
  private var groupLink: Option[TextButton] = None

  private var groupNameAndMem: Option[RelativeLayout] = None

  private var groupImage: Option[CircleImageView] = None

  private var groupDisplayName: Option[TypefaceEditText] = None

  private var groupDisplayMem: Option[TextView] = None

  private var qrcodeJoinGroup: Option[QrCodeButton] = None
  private var sPvibration: Option[SwitchPreference] = None
  private var topChangeSwitchPreference: Option[SwitchPreference] = None

  private var subs = Set.empty[com.waz.utils.events.Subscription]

  private val THOUSANDS_GROUP_MIN_PEOPLE: Int = 100

  private val CLOSE_ALL = 100
  private val SET_URL = 101

  private val handler = new Handler(new Handler.Callback() {
    override def handleMessage(msg: Message): Boolean = {
      if (isAdded) {
        msg.what match {
          case CLOSE_ALL =>
            closeAllJoinView()
          case SET_URL =>
            setConversationJoinUrl(msg.obj.asInstanceOf[String])
        }
      }
      false
    }
  })

  private lazy val loadInviteUrl = for {
    remoteId <- convController.currentConv.map(_.remoteId)
    checkInvite <- convController.currentConv.map(_.url_invite)
  } yield (remoteId, checkInvite)

  lazy val showAddParticipantView = for {
    currUser <- currentUser
    creator <- convController.currentConv.map(_.creator)
    addright <- convController.currentConv.map(_.addright)
  } yield {
    currUser == creator || !addright
  }

  lazy val showAddParticipants = for {
    conv <- participantsController.conv
    isGroupOrBot <- participantsController.isGroupOrBot
    hasPerm <- userAccountsController.hasAddConversationMemberPermission(conv.id)
  } yield if (conv.isActive && isGroupOrBot && hasPerm) {
    if (conv.creator.str.equals(SpUtils.getUserId(getContext))) {
      true
    } else {
      !conv.addright
    }
  } else {
    false
  }

  private lazy val participantsAdapter = returning(new ParticipantsAdapter(participantsController.otherParticipants.map(_.toSeq), Some(5), from = ParticipantsAdapter.GroupParticipant)) { adapter =>
    new FutureEventStream[UserId, Option[UserData]](adapter.onClick, participantsController.getUser).onUi {
      case Some(user) => (user.providerId, user.integrationId) match {
        case (Some(pId), Some(iId)) =>
          for {
            conv <- participantsController.conv.head
            _ = spinnerController.showSpinner()
          } {
            spinnerController.hideSpinner()
          }
        case _ =>
          getParentFragment match {
            case parentFragment: ParticipantFragment =>
              parentFragment.showUser(user.id)
            case _ =>
              participantsController.onShowUser ! Some(user.id)
          }
      }
      case _ =>
    }


    adapter.onNotificationsClick.onUi { _ =>
      slideFragmentInFromRight(new NotificationsOptionsFragment(), NotificationsOptionsFragment.Tag)
    }

    adapter.onShowAllParticipantsClick.onUi { _ =>
      convController.currentConv.currentValue.foreach { conversationData =>
        if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          slideFragmentInFromRight(ThousandsGroupUsersFragment.newInstance(conversationData.remoteId, ThousandsGroupUsersFragment.THOUSANDS_GROUPUSER_MORE, conversationData.add_friend, conversationData.creator), ThousandsGroupUsersFragment.TAG)

        } else slideFragmentInFromRight(new AllGroupParticipantsFragment(), AllGroupParticipantsFragment.Tag)
      }
    }

    adapter.onChangeHeaaderClick.onUi { _ =>
      GroupHeadPortraitActivity.startSelf(getContext)
    }
  }



  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_group_participant, viewGroup, false)

  private def initView(view: View): Unit = {
    sPDefaultGroup = ViewUtils.getView(view, R.id.preferences_default_group)
    //mRlGroupName = ViewUtils.getView(view, R.id.rl_group_name)
    mLLGroupLinkContent = ViewUtils.getView(view, R.id.group_link_content)
    tvIsThousands = ViewUtils.getView(view, R.id.tv_isThousands)
    //tvGroupName = ViewUtils.getView(view, R.id.tv_group_name)
    swUpgradeStatus = ViewUtils.getView(view, R.id.sw_UpgradeStatus)
    shareLinkPopupWindow = new GroupShareLinkPopupWindow(getActivity, ViewGroup.LayoutParams.MATCH_PARENT)
    preference_subtitle_thousands_group = ViewUtils.getView(view, R.id.preference_subtitle_thousands_group)
    sPvibration = Option(ViewUtils.getView(view, R.id.preferences_vibration))
    topChangeSwitchPreference = Option(ViewUtils.getView(view, R.id.top_change_switchPreference))

    groupLink = Option(findById[TextButton](R.id.group_link))
    setChartBackground = Option(findById[TextButton](R.id.set_chart_background))
    burnAfterReading = Option(findById[TextButton](R.id.burn_after_reading))
    groupNotice = Option(findById[TextButton](R.id.group_notice))
    groupChatHistory = Option(findById[TextButton](R.id.group_chat_history))
    groupChatSetting = Option(findById[TextButton](R.id.group_chat_setting))
    groupInviteMembers = Option(findById[TextButton](R.id.group_invite_members))
    qrcodeJoinGroup = Option(findById[QrCodeButton](R.id.qrcode_join_group))
    exitGroup = Option(findById[TextButton](R.id.exit_group))
    deleteGroupChat = Option(findById[TextButton](R.id.delete_group_chat))
    reportGroupChat = Option(findById[TextButton](R.id.report_group_chat))

    participantsView = Option(findById[RecyclerView]((R.id.pgv__participants)))
    clAddParticipant = Option(findById[View](R.id.clAddParticipant))

    groupNameAndMem = Option(findById[RelativeLayout](R.id.rl_group_name_with_no_mems))
    groupImage = Option(findById[CircleImageView](R.id.civ_single_image))
    groupDisplayName = Option(findById[TypefaceEditText](R.id.conversation_name_edit_text))
    groupDisplayMem = Option(findById[TextView](R.id.conversation_count))

  }

  private val topChangeListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        convController.setPlaceTop(conversationData.id, place_top = isChecked)
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

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    RxBus.getDefault.register(this)
    super.onViewCreated(view, savedInstanceState)

    initView(view)

    subs += convController.currentConv.map(_.place_top).onUi { place_top =>
      topChangeSwitchPreference.foreach { it =>
        it.setChecked(place_top, disableListener = true)
        it.setChangeListener(topChangeListener)
      }
    }


    subs += convController.currentConv.map(_.isAllMuted).onUi { mute =>
      sPvibration.foreach { it =>
        it.setChecked(mute, disableListener = true)
        it.setChangeListener(sPvibrationListener)
      }
    }

    val advisorySignal = for {
      advisory <- convController.currentConv.map(_.advisory)
    } yield advisory

    subs += advisorySignal.onUi {
      case Some(advisoryStr) =>
        groupNotice.foreach(_.setSubtitle(advisoryStr))
      case _ =>
        groupNotice.foreach(_.setSubtitle(""))
    }

    convController.currentConv.currentValue.foreach { conversationData =>

      burnAfterReading.foreach { burnAfterReading =>
        subs += burnAfterReading.onClickEvent.onUi { _ =>
          toEphemeral()
        }
      }

      groupNotice.foreach { groupNotice =>
        subs += groupNotice.onClickEvent.onUi { _ =>
          convController.currentConv.currentValue.foreach { conversationData =>
            val isCreator = conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(getContext))
            val advisory = conversationData.advisory.getOrElse("")
            if (!isCreator && StringUtils.isBlank(advisory)) {
              if (popUpWindow == null) popUpWindow = new NoGroupNoticePopupWindow(getContext)
              popUpWindow.showAtLocation(getActivity.getWindow.getDecorView, Gravity.CENTER, 0, 0)
            } else {
              GroupNoticeActivity.startGroupNoticeActivitySelf(getContext, conversationData.remoteId.str)
            }
          }
        }
      }

      groupChatHistory.foreach { groupChatHistory =>
        subs += groupChatHistory.onClickEvent.onUi { _ =>
          collectionController.openCollection()
        }
      }

      groupChatSetting.foreach { groupChatSetting =>
        subs += groupChatSetting.onClickEvent.onUi { _ =>
          GroupSettingActivity.startSelf(getContext)
        }
      }

      groupInviteMembers.foreach { groupInviteMembers =>
        subs += groupInviteMembers.onClickEvent.onUi { _ =>
          convController.currentConv.currentValue.foreach { conversationData =>
            GroupInviteMembersActivity.startGroupInviteMembersActivitySelf(getContext, conversationData.remoteId.str)
          }
        }
      }

      setChartBackground.foreach { setChartBackground =>
        subs += setChartBackground.onClickEvent.onUi { _ =>
          convController.currentConv.currentValue.foreach { conversationData =>
            val spKey = SpUtils.getConversationBackgroundSpKey(getContext, conversationData.remoteId.str)
            SelectChatBackgroundActivity.startSelfForResult(getActivity, MainActivityUtils.REQUEST_CODE_SELECT_CHAT_BACKGROUND, spKey)
          }
        }
      }

      (for {
        currUser <- currentUser
        convType <- convController.currentConv.map(_.convType)
        creator <- convController.currentConv.map(_.creator)
      } yield {
        currUser != creator && convType == ConversationData.ConversationType.ThousandsGroup
      }).onUi { isShow =>
        reportGroupChat.foreach { reportGroupChat =>
          if (isShow) {
            reportGroupChat.setVisibility(View.VISIBLE)
            subs += reportGroupChat.onClickEvent.onUi { _ =>
              ConversationReportActivity.startSelf(getActivity, ConversationReportActivity.SHOW_TYPE_REPORT)
            }
          } else {
            reportGroupChat.setVisibility(View.GONE)
          }
        }
      }

      deleteGroupChat.foreach { deleteGroupChat =>
        subs += deleteGroupChat.onClickEvent.onUi { _ =>
          convController.currentConv.currentValue.foreach { conversationData =>
            menuController.deleteConversation(conversationData.id)
          }
        }
      }

      exitGroup.foreach { exitGroup =>
        subs += exitGroup.onClickEvent.onUi { _ =>
          convController.currentConv.currentValue.foreach { conversationData =>
            showLeaveConfirmation(conversationData)
          }
        }
      }

      clAddParticipant.foreach(_.onClick {
        showAddParticipants.head.map {
          case true =>
            participantsController.conv.head.foreach { conv =>
              inject[CreateConversationController].setAddToConversation(conv.id)
              getFragmentManager.beginTransaction
                .setCustomAnimations(
                  R.anim.in_from_bottom_enter,
                  R.anim.out_to_bottom_exit,
                  R.anim.in_from_bottom_pop_enter,
                  R.anim.out_to_bottom_pop_exit)
                .replace(R.id.fl__participant__container, AddParticipantsFragment.newInstance(true), AddParticipantsFragment.Tag)
                .addToBackStack(AddParticipantsFragment.Tag)
                .commit
            }
          case _ => //
        }
      })


      groupInviteMembers.foreach(_.setVisibility(if (isShowGroupInvite(conversationData)) View.VISIBLE else View.GONE))

      menuController = new ConversationOptionsMenuController(conversationData.id, Mode.Normal(true))
      preference_subtitle_thousands_group.setText(getQuantityString(R.plurals.conversation_detail_settings_thousands_group_notice, Integer.valueOf(THOUSANDS_GROUP_MIN_PEOPLE), Integer.valueOf(THOUSANDS_GROUP_MIN_PEOPLE)))

      val userId = SpUtils.getUserId(getContext)
      val oldConvIdStr = SpUtils.getString(getContext, SpUtils.SP_NAME_FOREVER_SAVED, userId, "")
      val oldConvIds: Array[String] = oldConvIdStr.split('|').filter(_.length > 0)
      val addedMainTabCheck = convController.existsGroupData(conversationData.id,conversationData.remoteId)

      sPDefaultGroup.setVisibility(if (convController.localGroupDataNum()< ConversationOptionsMenuController.COLLECT_GROUP_LIMIT_COUNT || addedMainTabCheck) View.VISIBLE else View.GONE)
      sPDefaultGroup.setChecked(checked =addedMainTabCheck , disableListener = true)

      shareLinkPopupWindow.setCallBack(new GroupShareLinkPopupWindow.GroupShareCallBack() {
        override def clickCopy(): Unit = {
          groupLink.foreach { groupLink =>
            val clip = ClipData.newPlainText("copy", groupLink.getTitle)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ZApplication.getInstance, getResources.getString(R.string.copy_success), Toast.LENGTH_LONG).show()
          }

        }

        override def clickSend(selectData: Set[UserData]): Unit = {
          groupLink.foreach { groupLink =>
            ConversationApi.sendMutiMessage(selectData, groupLink.getTitle(), getActivity)
          }

        }

        override def clickSearch(): Unit = {
          groupLink.foreach { groupLink =>
            GroupShareSelectUserActivity.start(getActivity, groupLink.getTitle(), conversationData.remoteId, conversationData.creator, conversationData.convType)
          }

        }
      })

      shareLinkPopupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
        override def onDismiss(): Unit = {
          shareLinkPopupWindow.removeAllSelectUser()
          shareLinkPopupWindow.resetUI()
        }
      })

      if (conversationData.viewmem) {
        groupNameAndMem.foreach(_.setVisibility(View.GONE))
        participantsView.foreach { v =>
          v.setVisibility(View.VISIBLE)
          v.setAdapter(participantsAdapter)
          v.setLayoutManager(new LinearLayoutManager(getActivity))
        }
      } else {
        participantsView.foreach(_.setVisibility(View.GONE))
        groupNameAndMem.foreach(_.setVisibility(View.VISIBLE))
        groupDisplayName.foreach(_.setText(conversationData.displayName))
        showImage(conversationData.smallRAssetId,conversationData.id.str)

      }

      closeAllJoinView()

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

      if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
        burnAfterReading.foreach(_.setVisibility(View.GONE))
        swUpgradeStatus.setVisibility(View.GONE)
        tvIsThousands.setVisibility(View.VISIBLE)
      } else {
        burnAfterReading.foreach(_.setVisibility(View.VISIBLE))
        swUpgradeStatus.setVisibility(View.VISIBLE)
        tvIsThousands.setVisibility(View.GONE)
        swUpgradeStatus.setChecked(false)
      }

      if (conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(getContext))) {
        groupChatSetting.foreach(_.setVisibility(View.VISIBLE))
      } else {
        groupChatSetting.foreach(_.setVisibility(View.GONE))
      }

      swUpgradeStatus.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
        override def onCheckedChanged(compoundButton: CompoundButton, check: Boolean): Unit = {
          if (compoundButton.isPressed) {
            if (check) {
              if (conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(getContext)) && memberCountBesideSelfSystem + 1 > THOUSANDS_GROUP_MIN_PEOPLE) {

                val urlPath = new StringBuilder().append("conversations/").append(conversationData.remoteId.str).append("/newtype").toString
                val jsonObject = new JSONObject

                try
                  jsonObject.put("type", IConversation.Type.THROUSANDS_GROUP.id)
                catch {
                  case e: JSONException =>
                    e.printStackTrace()
                }

                SpecialServiceAPI.getInstance().put(urlPath, jsonObject.toString(), new OnHttpListener[HttpResponseBaseModel] {

                  override def onFail(code: Int, err: String): Unit = {
                    swUpgradeStatus.setChecked(false)
                  }

                  override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
                    swUpgradeStatus.setVisibility(View.GONE)
                    tvIsThousands.setVisibility(View.VISIBLE)
                  }

                  override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

                  }
                })
              }
              else {
                swUpgradeStatus.setChecked(false)
                Toast.makeText(getContext, getResources.getString(R.string.conversation_detail_settings_thousands_group_error), Toast.LENGTH_SHORT).show()
              }
            }
          }

        }
      })

      subs += loadInviteUrl.onUi { parts =>
        getConversationLinkStatus(parts._1.str, parts._2)
      }

      subs += participantsController.otherParticipants.map(_.size).onUi { size =>
        memberCountBesideSelfSystem = size
        groupDisplayMem.foreach(_.setVisible(conversationData.isGroupShowNum))
        if (groupDisplayMem.fold(false){_.getVisibility == View.VISIBLE}) {
          if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
            groupDisplayMem.foreach(_.setText(getContext.getString(R.string.group_total_mems, conversationData.memsum.getOrElse(0).toString)))
          } else {
            groupDisplayMem.foreach(_.setText(getContext.getString(R.string.group_total_mems, size.toString)))
          }
        }
      }

      subs += showAddParticipantView.onUi { showOrNot =>
        clAddParticipant.foreach(_.setVisibility(if (showOrNot) View.VISIBLE else View.GONE))
      }

      val advisory = conversationData.advisory.getOrElse("")
      groupNotice.foreach(_.setSubtitle(advisory))

      val nickNameEditText = Option(view.findViewById[TypefaceEditText](R.id.nickname_typefaceEditText))
      nickNameEditText.foreach { view =>
        view.setOnEditorActionListener(new OnEditorActionListener {

          override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
            if(actionId == EditorInfo.IME_ACTION_DONE) {
              KeyboardUtils.hideSoftInput(view)
              view.setSelected(false)
              view.clearFocus()
              Selection.removeSelection(view.getText)

              convController.changeGroupNickname(view.getText.toString.trim)
            }
            false
          }
        })
      }
      inject[UiStorage].loadAlias(conversationData.id, UserId(userId)).onUi { aliasData =>
        nickNameEditText.foreach(_.setText(aliasData.map(_.getAliasName).getOrElse("")))
      }
    }
  }

  private var titleMsgCancelSureDialog: TitleMsgSureDialog = _

  private def showTitleMsgCancelSureDialog( OnTitleMsgSureDialogClick: OnTitleMsgSureDialogClick): Unit = {
    if (titleMsgCancelSureDialog == null) {
      titleMsgCancelSureDialog = new TitleMsgSureDialog(ctx).updateFields(false, true, false, true)
    }
    titleMsgCancelSureDialog.show(-1, R.string.conversation_leave_without_transfer_creator, OnTitleMsgSureDialogClick)
  }

  private def dismissTitleMsgCancelSureDialog(): Unit = {
    if (titleMsgCancelSureDialog != null) {
      titleMsgCancelSureDialog.dismiss()
    }
  }

  private def showLeaveConfirmation(conversationData: ConversationData): Unit = {

    val header = getString(R.string.confirmation_menu__meta_remove)
    val text = getString(R.string.confirmation_menu__meta_remove_text)
    val confirm = getString(R.string.confirmation_menu__confirm_leave)
    val cancel = getString(R.string.confirmation_menu__cancel)
    val checkboxLabel = getString(R.string.confirmation_menu__delete_conversation__checkbox__label)
    val request = new ConfirmationRequest.Builder().withHeader(header).withMessage(text).withPositiveButton(confirm)
      .withNegativeButton(cancel).withConfirmationCallback(new TwoButtonConfirmationCallback() {
      override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
        if (currentUser.currentValue.head == conversationData.creator) {
          showTitleMsgCancelSureDialog(new OnTitleMsgSureDialogClick {
            override def onClickConfirm(): Unit = {
              dismissTitleMsgCancelSureDialog()
            }
          })
        } else {
          convController.leave(conversationData.id)
          convController.setCurrentConversationToNext(ConversationChangeRequester.LEAVE_CONVERSATION)
        }
      }

      override def negativeButtonClicked(): Unit = {
      }

      override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {
      }
    }).withCheckboxLabel(checkboxLabel).withWireTheme(getActivity.asInstanceOf[BaseActivity].injectJava(classOf[ThemeController]).getThemeDependentOptionsTheme).withCheckboxSelectedByDefault.build
    confirmationController.requestConfirmation(request, IConfirmationController.PARTICIPANTS)
    inject[SoundController].playAlert()

  }


  def isShowGroupInvite(conversationData: ConversationData): Boolean = {
    conversationData.show_invitor_list
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    super.onDestroyView()
  }

  override def onDestroy(): Unit = {
    RxBus.getDefault.unregister(this)
    super.onDestroy()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  def onGroupSettingChanged(groupSettingEntity: GroupSettingEntity): Unit = {
    if (groupSettingEntity.openLinkJoin) {
      mLLGroupLinkContent.setVisibility(View.VISIBLE)
    } else {
      mLLGroupLinkContent.setVisibility(View.GONE)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  def onGroupViewMemChanged(entity: GroupChangeViewMemEntity): Unit = {
    if (entity.viewmem) {
      groupNameAndMem.foreach(_.setVisibility(View.GONE))
      participantsView.foreach { v =>
        v.setVisibility(View.VISIBLE)
        v.setAdapter(participantsAdapter)
        v.setLayoutManager(new LinearLayoutManager(getActivity))
      }
    } else {
      participantsView.foreach(_.setVisibility(View.GONE))
      groupNameAndMem.foreach(_.setVisibility(View.VISIBLE))
      convController.currentConv.currentValue.foreach{
        conversationData =>
          groupDisplayName.foreach(_.setText(conversationData.displayName))
          showImage(conversationData.smallRAssetId,conversationData.id.str)

      }
    }
  }

  def showImage(rAssetId: RAssetId,convId: String): Unit = {
    if (rAssetId != null) {
      groupImage.foreach{
        v =>
          Glide.`with`(getContext).load(CircleConstant.appendAvatarUrl(rAssetId.str, getContext)).into(v)
      }
    } else {
      val defaultRes = MessageContentUtils.getGroupDefaultAvatar(convId)
      groupImage.foreach(_.setImageResource(defaultRes))
    }
  }

  private def closeAllJoinView(): Unit = {
    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData.creator.str.equalsIgnoreCase(SpUtils.getUserId(ZApplication.getInstance))) {
        qrcodeJoinGroup.foreach(_.setVisibility(View.GONE))
        groupLink.foreach(_.setVisibility(View.GONE))
      }
      else {
        qrcodeJoinGroup.foreach(_.setVisibility(View.GONE))
        groupLink.foreach(_.setVisibility(View.GONE))
      }
      mLLGroupLinkContent.setVisibility(View.GONE)
    }
  }

  private def getConversationLinkStatus(remoteConvId: String, invite: Boolean): Unit = {
    if(invite) {
      getConversationLinkUrl(remoteConvId)
    } else {
      handler.obtainMessage(CLOSE_ALL).sendToTarget()
    }
  }

  private def getConversationLinkUrl(remoteConvId: String): Unit = {

    val api = String.format(ImApiConst.RECOMMEND_INVITE_URL, remoteConvId)

    SpecialServiceAPI.getInstance().post(api, "", new OnHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        handler.obtainMessage(CLOSE_ALL).sendToTarget()
      }

      override def onSuc(r: HttpResponseBaseModel, orgJson: String): Unit = {
        try {
          val jsonObject = new JSONObject(orgJson)
          val data = jsonObject.optJSONObject("data")
          val url = data.optString("inviteurl")
          if (!StringUtils.isBlank(url)) handler.obtainMessage(SET_URL, url).sendToTarget()
          else handler.obtainMessage(CLOSE_ALL).sendToTarget()
        } catch {
          case e: Exception =>
            e.printStackTrace()
            handler.obtainMessage(CLOSE_ALL).sendToTarget()
        }
      }

      override def onSuc(r: util.List[HttpResponseBaseModel], orgJson: String): Unit = {

      }
    })
  }

  private def setConversationJoinUrl(url: String): Unit = {
    mLLGroupLinkContent.setVisibility(View.VISIBLE)
    groupLink.foreach(_.setVisibility(View.VISIBLE))
    qrcodeJoinGroup.foreach(_.setVisibility(View.VISIBLE))
    groupLink.foreach(_.setTitle(url))

    groupLink.foreach { groupLink =>
      subs += groupLink.onClickEvent.onUi { view =>
        shareLinkPopupWindow.showAtLocationWithAnim(view, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0)
      }
    }

    qrcodeJoinGroup.foreach { qrcodeJoinGroup =>
      subs += qrcodeJoinGroup.onClickEvent.onUi { _ =>
        GroupQRCodeActivity.start(Option(getActivity), url)
      }
    }

  }

  private var popUpWindow: NoGroupNoticePopupWindow = _

  override def onClick(v: View): Unit = {
  }

  private def toEphemeral(): Unit = {
    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData.globalEphemeral.nonEmpty) {
        if (currentUser.currentValue.head == conversationData.creator) {
          slideFragmentInFromRight(EphemeralOptionsFragment.newInstance(EphemeralOptionsFragment.SOURCE_GROUP), EphemeralOptionsFragment.Tag)
        }
      } else {
        slideFragmentInFromRight(EphemeralOptionsFragment.newInstance(EphemeralOptionsFragment.SOURCE_GROUP), EphemeralOptionsFragment.Tag)
      }
    }
  }

  override def onResume() = {
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    super.onResume()

  }

  override def onPause() = {
    super.onPause()
  }

  override def onBackPressed(): Boolean = {
    super.onBackPressed()
    participantsAdapter.onBackPressed()
  }
}

object GroupParticipantsFragment {
  val Tag: String = classOf[GroupParticipantsFragment].getName

  val PARAMS_SOURCE = "params_source"

  def newInstance(): GroupParticipantsFragment = new GroupParticipantsFragment

  def newInstance(source: Int): GroupParticipantsFragment = returning(new GroupParticipantsFragment) { f =>
    val dataBundle = new Bundle()
    dataBundle.putInt(PARAMS_SOURCE, source)
    f.setArguments(dataBundle)
  }
}
