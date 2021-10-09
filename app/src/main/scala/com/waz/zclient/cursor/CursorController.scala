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
package com.waz.zclient.cursor

import android.Manifest.permission.{CAMERA, READ_EXTERNAL_STORAGE, RECORD_AUDIO, WRITE_EXTERNAL_STORAGE}
import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.{MotionEvent, View}
import com.google.android.gms.common.{ConnectionResult, GoogleApiAvailability}
import com.waz.api.{IConversation, NetworkMode}
import com.waz.content.{GlobalPreferences, UserPreferences}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.conversation.{TypingService, TypingUser}
import com.waz.service.{NetworkModeService, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{AggregatingSignal, EventContext, EventStream, Signal}
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.common.controllers._
import com.waz.zclient.controllers.location.ILocationController
import com.waz.zclient.conversation.{ConversationController, ReplyController}
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.drawing.DrawingFragment
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor.{CursorMenuItem => JCursorMenuItem}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.views.DraftMap
import com.waz.zclient.{Injectable, Injector, R}
import org.json.JSONArray

import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

class CursorController(implicit inj: Injector, ctx: Context, evc: EventContext)
  extends Injectable with DerivedLogTag {

  import CursorController._
  import Threading.Implicits.Ui

  val zms = inject[Signal[ZMessaging]]
  val conversationController = inject[ConversationController]
  lazy val convListController = inject[ConversationListController]
  lazy val callController = inject[CallController]
  private lazy val replyController = inject[ReplyController]
  private lazy val callStartController = inject[CallStartController]
  private lazy val convController = inject[ConversationController]

  val conv = conversationController.currentConv

  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  val keyboard = Signal[KeyboardState](KeyboardState.Hidden)
  val editingMsg = Signal(Option.empty[MessageData])

  val secondaryToolbarVisible = Signal(false)
  val enteredText = Signal[(CursorText, EnteredTextSource)]((CursorText.Empty, EnteredTextSource.FromController))
  val cursorWidth = Signal[Int]()
  val editHasFocus = Signal(false)
  var cursorCallback = Option.empty[CursorCallback]
  val onEditMessageReset = EventStream[Unit]()

  val extendedCursor = keyboard map {
    case KeyboardState.ExtendedCursor(tpe) => tpe
    case _ => ExtendedCursorContainer.Type.NONE
  }

  val ephemeralSelected = extendedCursor.map(_ == ExtendedCursorContainer.Type.EPHEMERAL)

  val convIsEphemeral = conv.map { conv =>
    conv.localEphemeral.exists(_._1 != 0) && conv.convType != IConversation.Type.THROUSANDS_GROUP
  }
  val isEphemeralMode = convIsEphemeral.zip(ephemeralSelected) map { case (ephConv, selected) => ephConv || selected }


  val selectedItem = extendedCursor map {
    case ExtendedCursorContainer.Type.IMAGES => Some(CursorMenuItem.Camera)
    case ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING => Some(CursorMenuItem.AudioMessage)
    case _ => Option.empty[CursorMenuItem]
  }
  val isEditingMessage = editingMsg.map(_.isDefined)

  val ephemeralExp = conv.map(_.ephemeralExpiration)

  val isEphemeral = ephemeralExp.map(_.isDefined)

  //val emojiKeyboardVisible = extendedCursor.map(_ == ExtendedCursorContainer.Type.EMOJIS)

  val curSorMainViewVisible = Signal(false)

  val convAvailability = for {
    convId <- conv.map(_.id)
    av <- convListController.availability(convId)
  } yield av

  val convIsActive = conv.map { conv =>
    conv.isActive
  }

  val convIsActiveThousands = conv.map { conv =>
    (conv.isActive, conv.convType)
  }


  val onCursorItemClick = EventStream[CursorMenuItem]()

  val onMessageSent = EventStream[MessageData]()
  val onMessageEdited = EventStream[MessageData]()
  val onEphemeralExpirationSelected = EventStream[Option[FiniteDuration]]()

  val sendButtonEnabled: Signal[Boolean] = zms.map(_.userPrefs).flatMap(_.preference(UserPreferences.SendButtonEnabled).signal)

  val enteredTextEmpty = enteredText.map(_._1.isEmpty).orElse(Signal const true)
  val sendButtonVisible = Signal(enteredTextEmpty, sendButtonEnabled) map {
    case (empty, enabled) => enabled && (!empty)
  }

  val ephemeralBtnVisible = Signal(isEditingMessage, convIsActiveThousands).flatMap {
    case (false, (true, convType)) =>
      sendButtonVisible.map(!_)
//      if (convType == IConversation.Type.THROUSANDS_GROUP) {
//        Signal.const(false)
//      } else {
//        //isEphemeral.flatMap {
//        //  case true => Signal.const(true)
//        //  case _ => sendButtonVisible.map(!_)
//        //}
//        sendButtonVisible.map(!_)
//      }
    case _ => Signal.const(false)
  }

  val recordBtnVisible = isEditingMessage.flatMap {
    case false =>
      sendButtonVisible.map(!_)
    case _ => Signal.const(false)
  }

  val onShowTooltip = EventStream[(CursorMenuItem, View)] // (item, anchor)

  private val actionsController = inject[MessageActionsController]

  actionsController.onMessageAction {
    case (MessageAction.Edit, message) =>
      editingMsg ! Some(message)
      CancellableFuture.delayed(100.millis) {
        keyboard ! KeyboardState.Shown
      }
    case _ =>
    // ignore
  }

  // notify SE about typing state
  private var prevEnteredText = ""
  enteredText {
    case (CursorText(text, _), EnteredTextSource.FromView) if text != prevEnteredText =>
      for {
        typing <- zms.map(_.typing).head
        conversationData <- conversationController.currentConv.head
      } {
        if (!text.isEmpty) {
          typing__selfChangedInput(typing, conversationData)
        } else {
          typing__selfClearedInput(typing, conversationData)
        }
      }
      prevEnteredText = text
    case _ =>
  }

  val typingIndicatorVisible = for {
    conversationData <- conversationController.currentConv
    typing <- zms.map(_.typing)
    users <- if (inteceptTypingInConversation(conversationData)) {
      Signal.const(IndexedSeq.empty)
    } else {
      typing__typingUsers(typing, conversationData.id)
    }
  } yield
    users.nonEmpty && inteceptTypingInConversation(conversationData)


  def typing__typingUsers(typing: TypingService, convId: ConvId): AggregatingSignal[IndexedSeq[TypingUser], IndexedSeq[UserId]] = {
    typing.typingUsers(convId)
  }

  def typing__selfChangedInput(typing: TypingService, conversationData: ConversationData): Future[Unit] = {
    if (inteceptTypingInConversation(conversationData)) {
      Future.successful({})
    } else {
      typing.selfChangedInput(conversationData.id)
    }
  }

  def typing__selfClearedInput(typing: TypingService, conversationData: ConversationData): Future[Unit] = {
    if (inteceptTypingInConversation(conversationData)) {
      Future.successful({})
    } else {
      typing.selfClearedInput(conversationData.id)
    }
  }

  def inteceptTypingInConversation(conversationData: ConversationData): Boolean = {
    conversationData != null && conversationData.convType == IConversation.Type.THROUSANDS_GROUP
  }

  def notifyKeyboardVisibilityChanged(keyboardIsVisible: Boolean): Unit = {
    keyboard.mutate {
      case KeyboardState.Shown if !keyboardIsVisible => KeyboardState.Hidden
      case _ if keyboardIsVisible => KeyboardState.Shown
      case state => state
    }

    if (keyboardIsVisible) editHasFocus.currentValue.foreach { hasFocus =>
      if (hasFocus) {
        cursorCallback.foreach(_.onCursorClicked())
      }
    }
  }

  screenController.hideGiphy.onUi {
    case true =>
      // giphy worked, so no need for the draft text to reappear
      inject[DraftMap].resetCurrent().map { _ =>
        enteredText ! (CursorText.Empty, EnteredTextSource.FromController)
      }
    case false =>
  }

  editHasFocus {
    case true => // TODO - reimplement for tablets
    case false => // ignore
  }

  //  private val msgBeingSendInConv = Signal(Set.empty[ConvId])

  def submit(msg: String, mentions: Seq[Mention] = Nil): Boolean = {
    if (isEditingMessage.currentValue.contains(true)) {
      onApproveEditMessage()
      true
    }
    else if (TextUtils.isEmpty(msg.trim)) false
    else {
      (for {
        convId <- conv.map(_.id).head
        quote <- replyController.currentReplyContent.map(_.map(_.message.id)).head
      } yield (convId, quote)).foreach {
        case (convId, quote) =>
          conversationController.sendMessage(msg, mentions, quote, activity).foreach { m =>
            m.foreach { msg =>
              onMessageSent ! msg
              cursorCallback.foreach(_.onMessageSent(msg))
              replyController.clearMessage(msg.convId)

            }
          }
        case _ =>
      }
      true
    }
  }

  def submitTextJson(json: String, mentions: Seq[Mention] = Nil): Boolean = {
    if (isEditingMessage.currentValue.contains(true)) {
      onApproveEditMessage()
      true
    }
    else if (json.toString().length() == 2) false
    else {
      (for {
        convId <- conv.map(_.id).head
        //quote <- replyController.currentReplyContent.map(_.map(_.message.id)).head
      } yield (convId)).foreach { convId =>
        conversationController.sendTextJsonMessage(json, mentions, activity).foreach { m =>
          m.foreach { msg =>
            onMessageSent ! msg
            cursorCallback.foreach(_.onMessageSent(msg))
            replyController.clearMessage(msg.convId)
            //              Future {
            //                msgBeingSendInConv.mutate { msgSeq =>
            //                  msgSeq - msg.convId
            //                }
            //              }
          }
        }
      }
      true
    }
  }

  def submitTextJsonForRecipients(json: String, mentions: Seq[Mention] = Nil, uids: JSONArray, unblock: Boolean = false): Boolean = {
    if(isEditingMessage.currentValue.contains(true)) {
      onApproveEditMessage()
      true
    }
    else if(json.toString().length() == 2) false
    else {
      (for {
        convId <- conv.map(_.id).head
        //quote <- replyController.currentReplyContent.map(_.map(_.message.id)).head
      } yield (convId)).foreach { convId =>
        conversationController.sendTextJsonMessageForRecipients(json, mentions, activity, uids, unblock).foreach { m =>
          m.foreach { msg =>
            onMessageSent ! msg
            cursorCallback.foreach(_.onMessageSent(msg))
            replyController.clearMessage(msg.convId)
          }
        }
      }
      true
    }
  }


  def onApproveEditMessage(): Unit =
    for {
      cId <- conversationController.currentConvId.head
      cs <- zms.head.map(_.convsUi)
      m <- editingMsg.head if m.isDefined
      msg = m.get
      (CursorText(text, mentions), _) <- enteredText.head
    } {
      if (text.trim.isEmpty) {
        cs.recallMessage(cId, msg.id)
        showToast(R.string.conversation__message_action__delete__confirmation)
      } else {
        cs.updateMessage(cId, msg.id, text, mentions)
      }
      editingMsg ! None
      keyboard ! KeyboardState.Hidden
    }

  private val lastEphemeralValue = inject[GlobalPreferences].preference(GlobalPreferences.LastEphemeralValue).signal

  def toggleEphemeralMode(): Unit =
    for {
      lastExpiration <- lastEphemeralValue.head
      c <- conv.head
      z <- zms.head
      userId <- currentUser.head
      eph = c.ephemeralExpiration
    } yield {

      val checkGroupManager = c.convType == ConversationType.Group && c.creator == userId
      val flag = eph.fold(true)(it => if(it.isInstanceOf[ConvExpiry]) checkGroupManager else true)

      if(lastExpiration.isDefined && flag) {
        val current = if(eph.isEmpty) lastExpiration else None
        if(checkGroupManager) {
          z.convsUi.setEphemeralGlobal(c.id, current)
        } else {
          z.convsUi.setEphemeral(c.id, current)
        }
        if(eph != lastExpiration) onEphemeralExpirationSelected ! current
        keyboard mutate {
          case KeyboardState.ExtendedCursor(_) => KeyboardState.Hidden
          case state                           => state
        }
      }
    }

  private lazy val locationController = inject[ILocationController]
  private lazy val soundController = inject[SoundController]
  private lazy val permissions = inject[PermissionsService]
  private lazy val activity = inject[Activity]
  private lazy val screenController = inject[ScreenController]

  import CursorMenuItem._

  onCursorItemClick {

    case CallGroup =>
      callStartController.startCallInCurrentConv(withVideo = false, forceOption = true)
      editingMsg ! None
    case CursorMenuItem.More => secondaryToolbarVisible ! true
    case CursorMenuItem.Less => secondaryToolbarVisible ! false
    case AudioMessage =>
      checkIfCalling(isVideoMessage = false)(keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING))
    case Camera =>
      keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.IMAGES)
    case Ping =>
      for {
        true <- inject[NetworkModeService].networkMode.map(m => m != NetworkMode.OFFLINE && m != NetworkMode.UNKNOWN).head
        z <- zms.head
        cId <- conversationController.currentConvId.head
        _ <- z.convsUi.knock(cId)
      } soundController.playPingFromMe(true)
    case Sketch =>
      screenController.showSketch ! DrawingFragment.Sketch.BlankSketch
    case File =>
      cursorCallback.foreach(_.openFileSharing())
    case VideoMessage =>
      checkIfCalling(isVideoMessage = true)(cursorCallback.foreach(_.captureVideo()))
    case Location =>
      val googleAPI = GoogleApiAvailability.getInstance
      if (ConnectionResult.SUCCESS == googleAPI.isGooglePlayServicesAvailable(ctx)) {
        KeyboardUtils.hideKeyboard(activity)
        locationController.showShareLocation()
      }
      else showToast(R.string.location_sharing__missing_play_services)
    case Gif =>
      enteredText.head.foreach { case (CursorText(text, _), _) => screenController.showGiphy ! Some(text) }
    case Send =>
      enteredText.head.foreach { case (CursorText(text, mentions), _) => submit(text, mentions) }
    case Emoji =>
      keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EMOJIS)
    case _ =>
    // ignore
  }

  private def checkIfCalling(isVideoMessage: Boolean)(f: => Unit) =
    callController.isCallActive.head.foreach {
      case true => showErrorDialog(R.string.calling_ongoing_call_title, if (isVideoMessage) R.string.calling_ongoing_call_video_message else R.string.calling_ongoing_call_audio_message)
      case false => f
    }
}

object CursorController {

  sealed trait EnteredTextSource

  object EnteredTextSource {

    case object FromView extends EnteredTextSource

    case object FromController extends EnteredTextSource

  }

  sealed trait KeyboardState

  object KeyboardState {

    case object Hidden extends KeyboardState

    case object Shown extends KeyboardState

    case class ExtendedCursor(tpe: ExtendedCursorContainer.Type) extends KeyboardState

    case object EmojiHidden extends KeyboardState
  }

  val KeyboardPermissions = Map(
    ExtendedCursorContainer.Type.IMAGES -> ListSet(CAMERA, READ_EXTERNAL_STORAGE),
    ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING -> ListSet(RECORD_AUDIO, WRITE_EXTERNAL_STORAGE)
  )

  def keyboardPermissions(tpe: ExtendedCursorContainer.Type): ListSet[PermissionsService.PermissionKey] = KeyboardPermissions.getOrElse(tpe, ListSet.empty)
}

trait CursorCallback {
  def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit

  def hideExtendedCursor(immediate: Boolean): Unit

  def openFileSharing(): Unit

  def captureVideo(): Unit

  def onMessageSent(msg: MessageData): Unit

  def onCursorButtonLongPressed(cursorMenuItem: JCursorMenuItem): Unit

  def onMotionEventFromCursorButton(cursorMenuItem: JCursorMenuItem, motionEvent: MotionEvent): Unit

  def onCursorClicked(): Unit

  def expandCursorItems(): Unit

  def collapseCursorItems(): Unit

}
