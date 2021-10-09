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
package com.waz.zclient.participants

import android.content.Context
import com.waz.api.IConversation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.common.controllers.{SoundController, ThemeController}
import com.waz.zclient.controllers.confirmation.{ConfirmationRequest, IConfirmationController, TwoButtonConfirmationCallback}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.conversation.controller.IConversationScreenController
import com.waz.zclient.participants.ParticipantsController.ParticipantRequest
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{Injectable, Injector, R}

import scala.concurrent.Future

class ParticipantsController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  private implicit lazy val uiStorage = inject[UiStorage]
  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val screenController = inject[IConversationScreenController]

  lazy val selectedParticipant = Signal(Option.empty[UserId])
  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  val onShowParticipants = EventStream[Option[String]]() //Option[String] = fragment tag //TODO use type?
  val onHideParticipants = EventStream[Boolean]() //Boolean represents with or without animations
  val onLeaveParticipants = EventStream[Boolean]() //Boolean represents with or without animations
  val onShowParticipantsWithUserId = EventStream[ParticipantRequest]()

  val onShowParticipantSource = EventStream[Int]()

  val onShowUser = EventStream[Option[UserId]]()

  lazy val otherParticipants = convController.currentConvMembers
  lazy val conv = convController.currentConv
  lazy val isGroup = convController.currentConvIsGroup

  lazy val otherParticipantId = otherParticipants.flatMap {
    case others if others.size == 1 => Signal.const(others.headOption)
    case others => selectedParticipant
  }

  lazy val otherParticipant = for {
//    z <- zms
    Some(id) <- otherParticipantId
    user <- UserSignal(id)
  } yield user

  lazy val otherParticipantExists = for {
    z <- zms
    groupOrBot <- isGroupOrBot
    userId <- if (groupOrBot) Signal.const(Option.empty[UserId]) else otherParticipantId
    participant <- userId.fold(Signal.const(Option.empty[UserData]))(id => z.usersStorage.optSignal(id))
  } yield groupOrBot || participant.exists(!_.deleted)

  lazy val isGroupOrBot = for {
    group <- isGroup
  } yield group

  // is the current user a guest in the current conversation
  lazy val isCurrentUserGuest: Signal[Boolean] = for {
    z <- zms
    currentUser <- UserSignal(z.selfUserId)
    currentConv <- conv
  } yield currentConv.team.isDefined && currentConv.team != currentUser.teamId

  lazy val currentUserBelongsToConversationTeam: Signal[Boolean] = for {
    z <- zms
    currentUser <- UserSignal(z.selfUserId)
    currentConv <- conv
  } yield currentConv.team.isDefined && currentConv.team == currentUser.teamId

  def selectParticipant(userId: UserId): Unit = selectedParticipant ! Some(userId)

  def unselectParticipant(): Unit = selectedParticipant ! None

  def getUser(userId: UserId): Future[Option[UserData]] = zms.head.flatMap(_.usersStorage.get(userId))

  def addMembers(userIds: Set[UserId]): Future[Unit] =
    convController.currentConvId.head.flatMap { convId => convController.addMembers(convId, userIds) }

  def blockUser(userId: UserId): Future[Option[UserData]] = zms.head.flatMap(_.connection.blockConnection(userId))

  def unblockUser(userId: UserId): Future[ConversationData] = zms.head.flatMap(_.connection.unblockConnection(userId))

  def showRemoveConfirmation(userId: UserId): Unit = getUser(userId).foreach {
    case Some(userData) =>
      val request = new ConfirmationRequest.Builder()
        .withHeader(getString(R.string.confirmation_menu__header))
        .withMessage(getString(R.string.confirmation_menu_text_with_name, userData.getDisplayName))
        .withPositiveButton(getString(R.string.confirmation_menu__confirm_remove))
        .withNegativeButton(getString(R.string.confirmation_menu__cancel))
        .withConfirmationCallback(new TwoButtonConfirmationCallback() {
          override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
            screenController.hideUser()
            convController.removeMember(userId)
          }

          override def negativeButtonClicked(): Unit = {}

          override def onHideAnimationEnd(confirmed: Boolean, canceled: Boolean, checkboxIsSelected: Boolean): Unit = {}
        })
        .withWireTheme(inject[ThemeController].getThemeDependentOptionsTheme)
        .build
      confirmationController.requestConfirmation(request, IConfirmationController.PARTICIPANTS)
      inject[SoundController].playAlert()
    case _ =>
  }(Threading.Ui)

  def isGroupRemoveAndForbiddenCurRight(): Signal[Boolean] = {
    for{
      Some(id) <- otherParticipantId
      isRight <- isGroupRemoveAndForbiddenMemberRight(id)
    }yield isRight
  }

  def isGroupRemoveAndForbiddenMemberRight(userId: UserId): Signal[Boolean] = {
    for {
      isGroup <- convController.currentConvIsGroup
      creatorId <- convController.currentConv.map(_.creator)
      selfId <- currentUser
      selfIsManager <- convController.currentGroupIsManager
      otherIsManager <- convController.currentUserIsGroupManager(userId)
    } yield {
      if (isGroup && selfId != userId) {
        if (selfId == creatorId) {
          true
        } else if (selfIsManager) {
          if (!otherIsManager && userId != creatorId) {
            true
          } else {
            false
          }
        } else {
          false
        }
      } else {
        false
      }
    }
  }
}

object ParticipantsController {

  case class ParticipantRequest(userId: UserId, fromDeepLink: Boolean = false)


  def isGroup(conversationData: ConversationData) = Seq(IConversation.Type.GROUP, IConversation.Type.THROUSANDS_GROUP).contains(conversationData.convType)

  def isManager(conversationData: ConversationData, selfUserId: UserId): Boolean = {
    conversationData.manager.contains(selfUserId)
  }

  def isGroupRemoveAndForbiddenMemberRightForThousandsGroup(otherUserId: UserId, selfUserId: UserId, conversationData: ConversationData): Boolean = {

    if (isGroup(conversationData) && selfUserId != otherUserId) {
      if (selfUserId == conversationData.creator) {
        true
      } else if (isManager(conversationData, selfUserId)) {
        if (!isManager(conversationData, otherUserId) && otherUserId != conversationData.creator) {
          true
        } else {
          false
        }
      } else {
        false
      }
    } else {
      false
    }
  }

}
