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
package com.waz.zclient.messages

import com.jsy.common.utils.ModuleUtils
import com.jsy.secret.sub.swipbackact.ActivityContainner
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.RichWireInstant
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.conversationpager.controller.ISlidingPaneController
import com.waz.zclient.{Injectable, Injector, WireContext}

import scala.concurrent.duration._

class MessagesController()(implicit injector: Injector, cxt: WireContext, ev: EventContext)
  extends Injectable with DerivedLogTag {
  
import com.waz.threading.Threading.Implicits.Background

import scala.concurrent.Future

  val zms = inject[Signal[ZMessaging]]
  val currentConvId = inject[ConversationController].currentConvId
  val slidingPaneController = inject[ISlidingPaneController]

  val scrolledToBottom = Signal(true)
  val onScrollToBottomRequested = EventStream[Int]

  val currentConvIndex = for {
    z       <- zms
    convId  <- currentConvId
    index   <- Signal.future(z.messagesStorage.msgsIndex(convId))
  } yield
    index

  val lastMessage: Signal[MessageData] =
    currentConvIndex.flatMap{ msgIndex =>
      msgIndex.signals.lastMessage
    } map { msgOpt =>
      msgOpt.getOrElse(MessageData.Empty)
    }

  val lastSelfMessage: Signal[MessageData] =
    currentConvIndex.flatMap { _.signals.lastMessageFromSelf } map { _.getOrElse(MessageData.Empty) }

  val uiActive = zms.flatMap { _.lifecycle.uiActive }

  // id of fully visible conv list, meaning that messages list for that conv is actually shown on screen (user sees messages)
  val fullyVisibleMessagesList: Signal[Option[ConvId]] = {

    (for {
      true <- uiActive
      true <- Signal.apply{ActivityContainner.activityIsInContainner(ModuleUtils.CLAZZ_ConversationActivity)}
      conv <- currentConvId.map(Option(_))
    } yield conv).orElse(Signal.const(Option.empty[ConvId]))
  }

  @volatile
  private var lastReadTime = RemoteInstant.Epoch

  currentConvIndex.flatMap(_.signals.lastReadTime) { lastReadTime = _ }

  fullyVisibleMessagesList.disableAutowiring()

  def isLastSelf(id: MessageId) = lastSelfMessage.currentValue.exists(_.id == id)

  // Throttling to avoid too many requests for read receipts.
  // Ideally the sync service would do the merge but the new WorkManager doesn't allow that yet.
  private val lastReadMessages = Signal(Map[ConvId, MessageData]())

  lastReadMessages.throttle(2.seconds) { messages =>
    messages.foreach { case (conv, msg) =>
      zms.head.foreach(_.convsUi.setLastRead(conv, msg))
    }
    lastReadMessages ! Map()
  }

  def onMessageRead(msg: MessageData) = {
    if (msg.isEphemeral && !msg.expired)
        zms.head.foreach(_.ephemeral.onMessageRead(msg.id))

    lastReadMessages.mutate { messages =>
      messages + (msg.convId -> messages.get(msg.convId).fold(msg)(m => if (m.time isAfter msg.time) m else msg))
    }

    if (msg.state == Message.Status.FAILED)
      zms.head.foreach(_.messages.markMessageRead(msg.convId, msg.id))
  }

  def getMessage(messageId: MessageId): Signal[Option[MessageData]] = {
    zms.flatMap(z => Signal.future(z.messagesStorage.get(messageId)))
  }

  def retryMessageSending(ids: Seq[MessageId]): Future[Seq[SyncId]] =
    for {
      zms <- zms.head
      messages <- zms.messagesStorage.getAll(ids).map(_.flatten)
      res <- Future.traverse(messages)(msg => zms.messages.retryMessageSending(msg.convId, msg.id))
    } yield res.flatten

}
