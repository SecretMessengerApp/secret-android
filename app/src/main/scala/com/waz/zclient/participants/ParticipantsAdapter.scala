/**
 * Secret
 * Copyright (C) 2019 Secret
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

import android.content.Context
import android.graphics.Color
import android.text.{Selection, TextUtils}
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.TextView.OnEditorActionListener
import android.widget.{CompoundButton, ImageView, TextView}
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.views.CircleImageView
import com.jsy.res.utils.ViewUtils
import com.waz.api.{IConversation, Verification}
import com.waz.content.{AliasStorage, UsersStorage}
import com.waz.log.BasicLogging.LogTag
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.zclient.common.controllers.{ThemeController, UserAccountsController}
import com.waz.zclient.common.views.SingleUserRowView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.paintcode._
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{AliasSignal, RichView, UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.duration._

//TODO Maybe it will be better to split this adapter in two? One for participants and another for options?
class ParticipantsAdapter(userIds: Signal[Seq[UserId]],
                          maxParticipants: Option[Int] = None,
                          showPeopleOnly: Boolean = false,
                          showArrow: Boolean = true,
                          showSubtitle: Boolean = true,
                          from: ParticipantsAdapter.From = ParticipantsAdapter.GroupParticipant,
                          createSubtitle: Option[(UserData) => String] = None
                         )(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder] with Injectable {

  implicit val uiStorage = inject[UiStorage]
  implicit val logTag: LogTag = LogTag(getClass.getSimpleName)

  import ParticipantsAdapter._

  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val aliasStorage = inject[Signal[AliasStorage]]

  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private lazy val participantsController = inject[ParticipantsController]
  private lazy val convController = inject[ConversationController]
  private lazy val themeController = inject[ThemeController]
  private lazy val accountsController = inject[UserAccountsController]

  private var items = List.empty[Either[ParticipantData, Int]]
  private var readReceiptsEnabled = false
  private var convVerified = false
  private var peopleCount = 0
  private var show_memsum = true

  private var convName = Option.empty[String]
  private var selfId: UserId = _
  private var creatorId: UserId = _
  private var selfData: UserData = _
  private var creatorData: UserData = _

  private var convNameViewHolder = Option.empty[ConversationNameViewHolder]

  val onClick = EventStream[UserId]()
  val onShowAllParticipantsClick = EventStream[Unit]()
  val onNotificationsClick = EventStream[Unit]()
  val onReadReceiptsClick = EventStream[Unit]()
  val onChangeHeaaderClick = EventStream[Unit]()
  val filter = Signal("")

  def compareIngoreEmpty(data: ParticipantData) = {
    val name: String = data.userData.getDisplayName.str
    if (TextUtils.isEmpty(name) || data.userData.connection == ConnectionStatus.Blocked) {
      (true, "")
    } else {
      (false, name)
    }
  }

  lazy val users = for {
    usersStorage <- usersStorage
    resultAliasStorage <- aliasStorage

    userIds <- userIds
    users <- usersStorage.listSignal(userIds)
    f <- filter
    filterUsers = users.filter(_.matchesFilter(f))

    convId <- convController.currentConv.map(_.id)

    creatorId <- convController.currentConv.map(_.creator)
    creatorData <- UserSignal(creatorId)
    creatorAliasData <- AliasSignal(convId,creatorId)

    selfId <- currentUser
    selfData <- UserSignal(selfId)
    selfAliasData <-  AliasSignal(convId,selfId)

    managerNoSelf_Creator <- convController.currentConvGroupManagerNoSelf_Creator
    managerNoSelf_CreatorData <- usersStorage.listSignal(managerNoSelf_Creator)
    convAliasData <- resultAliasStorage.listSignal(convId)

  } yield {
    this.selfData = selfData
    this.creatorData = creatorData
    this.selfId = selfId
    this.creatorId = creatorId

    val result = from match {
      case ParticipantsAdapter.ReadAndLikes        =>
        filterUsers.map { u =>
          ParticipantData(u, convAliasData.find(_.userId == u.id))
        }.sortBy(compareIngoreEmpty).toList
      case ParticipantsAdapter.GroupParticipant |
           ParticipantsAdapter.AllGroupParticipant =>

        val noManagerUsers = filterUsers.filter { user =>
          user.id != selfId && user.id != creatorId && !convController.currentUserIsGroupManager(user.id).currentValue.get
        }.map { u => ParticipantData(u, convAliasData.find(_.userId == u.id), "normal")
        }.sortBy(compareIngoreEmpty).toList

        if (!TextUtils.isEmpty(f)) {
          val managerUsers = filterUsers.filter { user =>
            user.id != selfId && user.id != creatorId && convController.currentUserIsGroupManager(user.id).currentValue.get
          }.map { u =>
            ParticipantData(u, convAliasData.find(_.userId == u.id), "manager")
          }.sortBy(compareIngoreEmpty).toList

          val isSelf = (selfData.matchesFilter(f) || filterUsers.exists(_.id == selfId))
          val isCreator = (creatorData.matchesFilter(f) || filterUsers.exists(_.id == creatorId))
          val filteredUsers = managerUsers ++ noManagerUsers

          if (isCreator && selfId == creatorId) {
            ParticipantData(creatorData, creatorAliasData, "creator&myself") :: filteredUsers
          } else if (isCreator && isSelf) {
            ParticipantData(creatorData, creatorAliasData, "creator") :: ParticipantData(selfData, selfAliasData, "myself") :: filteredUsers
          } else if (isCreator) {
            ParticipantData(creatorData, creatorAliasData, "creator") :: filteredUsers
          } else if (isSelf) {
            ParticipantData(selfData, selfAliasData, "myself") :: filteredUsers
          } else {
            filteredUsers
          }
        } else {
          val managerUsers = managerNoSelf_CreatorData.map { u =>
            ParticipantData(u, convAliasData.find(_.userId == u.id), "manager")
          }.sortBy(compareIngoreEmpty).toList

          val filteredUsers = managerUsers ++ noManagerUsers
          if (selfId == creatorId) {
            ParticipantData(creatorData, creatorAliasData, "creator&myself") :: filteredUsers
          } else {
            ParticipantData(creatorData, creatorAliasData, "creator") :: ParticipantData(selfData, selfAliasData, "myself") :: filteredUsers
          }
        }
    }
    result
  }

  private lazy val positions = for {
    users <- users
    convType <- conv.map(_.convType)
    memsum <- conv.map(_.memsum)
    isShowNum<- conv.map(_.isGroupShowNum)
  } yield {
    val list = from match {
      case ParticipantsAdapter.ReadAndLikes =>
        show_memsum = isShowNum
        peopleCount = users.size
          List(Right(PeopleSeparator)) :::
            users.map(data => Left(data))
      case ParticipantsAdapter.GroupParticipant |
           ParticipantsAdapter.AllGroupParticipant =>
        show_memsum = isShowNum
        if (convType == IConversation.Type.THROUSANDS_GROUP) {
          peopleCount = memsum.getOrElse(users.size)
        } else {
          peopleCount = users.size
        }
        val filteredPeople = users.take(maxParticipants.getOrElse(Integer.MAX_VALUE))
        (if (!showPeopleOnly) List(Right(if (creatorId == selfId) ConversationName else ConversationNameReadOnly)) else Nil) :::
          List(Right(PeopleSeparator)) :::
          filteredPeople.map(data => Left(data)) :::
          (if (maxParticipants.exists(peopleCount > _)) List(Right(AllParticipants)) else Nil)
    }
    list
  }

  positions.onUi { list =>
    items = list
    notifyDataSetChanged()
  }

  private val conv = convController.currentConv

  (for {
    name <- conv.map(_.displayName)
    ver <- conv.map(_.verified == Verification.VERIFIED)
    read <- conv.map(_.readReceiptsAllowed)
    clock <- ClockSignal(5.seconds)
  } yield (name, ver, read, clock)).onUi {
    case (name, ver, read, _) =>
      convName = Some(name)
      convVerified = ver
      readReceiptsEnabled = read
      notifyDataSetChanged()
  }

  def onBackPressed(): Boolean = convNameViewHolder.exists(_.onBackPressed())

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = viewType match {
    case UserRow =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.single_user_row, parent, false).asInstanceOf[SingleUserRowView]
      view.showArrow(showArrow)
      ParticipantRowViewHolder(view, onClick)
    case ConversationName =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, convController)) { vh =>
        convNameViewHolder = Option(vh)
        vh.setEditingEnabled(true)
        vh.civ_single_image.onClick(onChangeHeaaderClick ! {})
      }
    case ConversationNameReadOnly =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.conversation_name_row, parent, false)
      returning(ConversationNameViewHolder(view, convController)) { vh =>
        convNameViewHolder = Option(vh)
        vh.setEditingEnabled(false)
      }
    case AllParticipants =>
      val view = LayoutInflater.from(parent.getContext).inflate(R.layout.list_options_button, parent, false)
      view.onClick(onShowAllParticipantsClick ! {})
      ShowAllParticipantsViewHolder(view)
    case _ => SeparatorViewHolder(getSeparatorView(parent))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = (items(position), holder) match {
    case (Right(AllParticipants), h: ShowAllParticipantsViewHolder) =>
      h.bind(peopleCount, show_memsum)
    case (Left(userData), h: ParticipantRowViewHolder) =>
      val allwaysShowCreatorAndSelf = from match {
        case ParticipantsAdapter.ReadAndLikes =>
          false
        case ParticipantsAdapter.GroupParticipant |
             ParticipantsAdapter.AllGroupParticipant =>
          true
      }
      h.bind(userData, maxParticipants.forall(peopleCount <= _) && items.lift(position + 1).forall(_.isRight),
        showCreatorOrSelf = true, creatorId = creatorId, selfId = selfId, createSubtitle = createSubtitle,
        if (allwaysShowCreatorAndSelf) convController.currentUserIsGroupManager(userData.userData.id).currentValue.get else false,showSubtitle,!allwaysShowCreatorAndSelf)
    case (Right(ConversationName), h: ConversationNameViewHolder) =>
      convName.foreach(name => h.bind(name, convVerified, peopleCount))
    case (Right(ConversationNameReadOnly), h: ConversationNameViewHolder) =>
      convName.foreach(name => h.bind(name, convVerified, peopleCount))
    case (Right(sepType), h: SeparatorViewHolder) if Set(PeopleSeparator, ServicesSeparator).contains(sepType) =>
      val count = if (sepType == PeopleSeparator) peopleCount else 0 /*botCount*/
      val showTitle = if(show_memsum) {
        getString(if (sepType == PeopleSeparator) R.string.participants_divider_people else R.string.participants_divider_services, count.toString)
      }else {
        getString(if (sepType == PeopleSeparator) R.string.participants_divider_people_no_num else R.string.participants_divider_services_no_num)
      }
      h.setTitle(showTitle)
      h.setId(if (sepType == PeopleSeparator) R.id.participants_section else R.id.services_section)
    case _ =>
  }

  override def getItemCount: Int = items.size

  override def getItemId(position: Int): Long = items(position) match {
    case Left(user) => user.userData.id.hashCode()
    case Right(sepType) => sepType
  }

  setHasStableIds(true)

  override def getItemViewType(position: Int): Int = items(position) match {
    case Right(sepType) => sepType
    case _ => UserRow
  }

  private def getSeparatorView(parent: ViewGroup): View =
    LayoutInflater.from(parent.getContext).inflate(R.layout.participants_separator_row, parent, false)

}

object ParticipantsAdapter {
  val UserRow = 0
  val PeopleSeparator = 1
  val ServicesSeparator = 2
  //val GuestOptions      = 3
  val ConversationName = 4
  //val EphemeralOptions  = 5
  val AllParticipants = 6
  //val Notifications     = 7
  //val ReadReceipts      = 8
  val ConversationNameReadOnly = 9

  sealed trait From

  case object ReadAndLikes extends From

  case object GroupParticipant extends From

  case object AllGroupParticipant extends From

  case class ParticipantData(userData: UserData,aliasData: Option[AliasData], from: String = "")

  case class SeparatorViewHolder(separator: View) extends ViewHolder(separator) {
    private val textView = ViewUtils.getView[TextView](separator, R.id.separator_title)

    def setTitle(title: String) = textView.setText(title)

    def setId(id: Int) = textView.setId(id)
  }

  case class ParticipantRowViewHolder(view: SingleUserRowView, onClick: SourceStream[UserId]) extends ViewHolder(view) {

    private var userId = Option.empty[UserId]

    view.onClick(userId.foreach(onClick ! _))

    def bind(participant: ParticipantData, lastRow: Boolean, showCreatorOrSelf: Boolean = false, creatorId: UserId = null
             , selfId: UserId = null, createSubtitle: Option[(UserData) => String], isGroupManager: Boolean = false,showSubTitle : Boolean = true,formLike : Boolean): Unit = {
      userId = Some(participant.userData.id)
      createSubtitle match {
        case Some(f) => view.setUserData(participant.userData, showCreatorOrSelf = showCreatorOrSelf
          , creatorId = creatorId, selfId = selfId, isGroupManager, createSubtitle = f, aliasData = participant.aliasData)
        case None    => view.setUserData(participant.userData, showCreatorOrSelf = showCreatorOrSelf
          , creatorId = creatorId, selfId = selfId, isGroupManager, aliasData = participant.aliasData)
      }
      view.setSeparatorVisible(!lastRow || formLike)
      view.setSubtitleVisbility(showSubTitle)
    }
  }

  case class ReadReceiptsViewHolder(view: View, convController: ConversationController)(implicit eventContext: EventContext) extends ViewHolder(view) {
    private implicit val ctx = view.getContext

    private val switch = view.findViewById[SwitchCompat](R.id.participants_read_receipts_toggle)
    private var readReceipts = Option.empty[Boolean]

    view.findViewById[ImageView](R.id.participants_read_receipts_icon).setImageDrawable(ViewWithColor(getStyledColor(R.attr.wirePrimaryTextColor)))

    switch.setOnCheckedChangeListener(new OnCheckedChangeListener {
      override def onCheckedChanged(buttonView: CompoundButton, readReceiptsEnabled: Boolean): Unit =
        if (!readReceipts.contains(readReceiptsEnabled)) {
          readReceipts = Some(readReceiptsEnabled)
          convController.setCurrentConvReadReceipts(readReceiptsEnabled)
        }
    })

    def bind(readReceiptsEnabled: Boolean): Unit =
      if (!readReceipts.contains(readReceiptsEnabled)) switch.setChecked(readReceiptsEnabled)
  }

  case class ConversationNameViewHolder(view: View, convController: ConversationController) extends ViewHolder(view) {

    implicit val logTag: LogTag = LogTag(getClass.getSimpleName)

    /*private val callInfo = view.findViewById[TextView](R.id.call_info)*/
    private val editText = view.findViewById[TypefaceEditText](R.id.conversation_name_edit_text)
    private val penGlyph = view.findViewById[GlyphTextView](R.id.conversation_name_edit_glyph)
    /*private val verifiedShield = view.findViewById[ImageView](R.id.conversation_verified_shield)*/
    val civ_single_image = view.findViewById[CircleImageView](R.id.civ_single_image)
    private val conversation_count = view.findViewById[TextView](R.id.conversation_count)

    private var convName = Option.empty[String]

    private var isBeingEdited = false

    def setEditingEnabled(enabled: Boolean): Unit = {
      val penVisibility = if (enabled) View.VISIBLE else View.GONE
      penGlyph.setVisibility(penVisibility)
      editText.setEnabled(enabled)
    }

    private def stopEditing() = {
      KeyboardUtils.hideSoftInput(editText)
      editText.setSelected(false)
      editText.clearFocus()
      Selection.removeSelection(editText.getText)
    }

    editText.setAccentColor(Color.BLACK)

    editText.setOnEditorActionListener(new OnEditorActionListener {
      override def onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean = {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
          stopEditing()
          convController.setCurrentConvName(v.getText.toString)
        }
        false
      }
    })

    editText.setOnSelectionChangedListener(new OnSelectionChangedListener {
      override def onSelectionChanged(selStart: Int, selEnd: Int): Unit = {
        isBeingEdited = selStart > 0
        penGlyph.animate().alpha(if (selStart >= 0) 0.0f else 1.0f).start()
      }
    })

    def bind(displayName: String, verified: Boolean, peopleCount: Int): Unit = {
      /*if (verifiedShield.isVisible != verified) verifiedShield.setVisible(verified)*/
      if (!convName.contains(displayName)) {
        convName = Some(displayName)
        editText.setText(displayName)
        Selection.removeSelection(editText.getText)
      }

      /*callInfo.setText(R.string.empty_string)
      callInfo.setMarginTop(getDimenPx(R.dimen.wire__padding__16)(view.getContext))
      callInfo.setMarginBottom(getDimenPx(R.dimen.wire__padding__16)(view.getContext))*/
      convController.currentConv.currentValue.foreach { conversationData =>
        if (conversationData.viewmem && conversationData.isGroupShowNum) {
          conversation_count.setVisibility(View.VISIBLE)
          conversation_count.setText(view.getContext.getString(R.string.group_total_mems, peopleCount.toString))
        } else {
          conversation_count.setVisibility(View.GONE)
        }
        showImage(conversationData.smallRAssetId,conversationData.id.str)
      }
    }

    def showImage(rAssetId: RAssetId,convId: String): Unit = {
      if (civ_single_image != null) {
        if (rAssetId != null) {
          Glide.`with`(view.getContext).load(CircleConstant.appendAvatarUrl(rAssetId.str, view.getContext)).into(civ_single_image)
        } else {
          val defaultRes = MessageContentUtils.getGroupDefaultAvatar(convId)
          civ_single_image.setImageResource(defaultRes)
        }
      }
    }

    def onBackPressed(): Boolean =
      if (isBeingEdited) {
        convName.foreach(editText.setText)
        stopEditing()
        true
      } else false
  }

  case class ShowAllParticipantsViewHolder(view: View) extends ViewHolder(view) {
    private implicit val ctx: Context = view.getContext
    view.findViewById[ImageView](R.id.next_indicator).setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40))
    view.setClickable(true)
    view.setFocusable(true)
    view.setMarginTop(0)
    private lazy val nameView = view.findViewById[TypefaceTextView](R.id.name_text)

    def bind(numOfParticipants: Int, isShowNum:Boolean): Unit = {
      if(isShowNum){
        nameView.setText(getString(R.string.show_all_participants, numOfParticipants.toString))
      }else{
        nameView.setText(getString(R.string.show_all_participants_no_num, numOfParticipants.toString))
      }
    }
  }

}
