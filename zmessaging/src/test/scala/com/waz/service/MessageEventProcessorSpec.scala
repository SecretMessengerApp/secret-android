/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.api.Message.Status
import com.waz.api.Message.Type._
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model.GenericContent.Text
import com.waz.model._
import com.waz.model.otr.UserClients
import com.waz.service.assets.AssetService
import com.waz.service.conversation.{ConversationsContentUpdater, ConversationsService}
import com.waz.service.messages.{MessageEventProcessor, MessagesContentUpdater, MessagesService}
import com.waz.service.otr.OtrService
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.TestGlobalPreferences
import com.waz.utils.crypto.ReplyHashing
import com.waz.utils.events.{EventStream, Signal}
import org.scalatest.Inside
import com.waz.threading.Threading

import scala.concurrent.Future
import scala.concurrent.duration._

class MessageEventProcessorSpec extends AndroidFreeSpec with Inside with DerivedLogTag {


  val selfUserId        = UserId("self")
  val storage           = mock[MessagesStorage]
  val convsStorage      = mock[ConversationStorage]
  val otrClientsStorage = mock[OtrClientsStorage]
  val deletions         = mock[MsgDeletionStorage]
  val assets            = mock[AssetService]
  val replyHashing      = mock[ReplyHashing]
  val msgsService       = mock[MessagesService]
  val convs             = mock[ConversationsContentUpdater]
  val otr               = mock[OtrService]
  val convsService      = mock[ConversationsService]
  val prefs             = new TestGlobalPreferences()

  val messagesInStorage = Signal[Seq[MessageData]](Seq.empty)
  (storage.getMessages _).expects(*).anyNumberOfTimes.onCall { ids: Traversable[MessageId] =>
    messagesInStorage.head.map(msgs => ids.map(id => msgs.find(_.id == id)).toSeq)(Threading.Background)
  }

  (replyHashing.hashMessages _).expects(*).anyNumberOfTimes.onCall { msgs: Seq[MessageData] =>
    Future.successful(msgs.filter(m => m.quote.isDefined && m.quote.flatMap(_.hash).isDefined).map(m => m.id -> m.quote.flatMap(_.hash).get).toMap)
  }

  feature("Push events processing") {
    scenario("Process text message event") {
      val sender = UserId("sender")
      val text = "hello"

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)

      clock.advance(5.seconds)
      val event = GenericMessageEvent(conv.remoteId, RemoteInstant(clock.instant()), sender, GenericMessage(Uid("uid"), Text(text)))

      (storage.updateOrCreateAll _).expects(*).onCall { updaters: Map[MessageId, Option[MessageData] => MessageData] =>
        Future.successful(updaters.values.map(_.apply(None)).toSet)
      }

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) {
        case m =>
          m.msgType              shouldEqual TEXT
          m.convId               shouldEqual conv.id
          m.userId               shouldEqual sender
          m.content              shouldEqual MessageData.textContent(text)
          m.time                 shouldEqual event.time
          m.localTime            shouldEqual event.localTime
          m.state                shouldEqual Status.SENT
          m.protos.head.toString shouldEqual event.asInstanceOf[GenericMessageEvent].content.toString
      }
    }

    scenario("Process MemberJoin events sent from other user") {
      val sender = UserId("sender")

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val membersAdded = Set(
        UserId("user1"),
        UserId("user2")
      )

      clock.advance(5.seconds)
      val event = MemberJoinEvent(conv.remoteId, RemoteInstant(clock.instant()), sender, membersAdded.toSeq)

      (storage.hasSystemMessage _).expects(conv.id, event.time, MEMBER_JOIN, sender).returning(Future.successful(false))
      (storage.lastLocalMessage _).expects(conv.id, MEMBER_JOIN).returning(Future.successful(None))
      (storage.addMessage _).expects(*).once().onCall { m: MessageData =>
        messagesInStorage.mutate(_ ++ Seq(m))
        Future.successful(m)
      }

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) {
        case m =>
          m.msgType       shouldEqual MEMBER_JOIN
          m.convId        shouldEqual conv.id
          m.userId        shouldEqual sender
          m.time          shouldEqual event.time
          m.localTime     shouldEqual event.localTime
          m.state         shouldEqual Status.SENT
          m.members       shouldEqual membersAdded
      }
    }

    scenario("Discard duplicate system message events") {
      val sender = UserId("sender")

      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val membersAdded = Set(
        UserId("user1"),
        UserId("user2")
      )

      (storage.hasSystemMessage _).expects(*, *, *, *).repeated(3).returning(Future.successful(true))
      (storage.addMessage _).expects(*).never()

      val processor = getProcessor

      def testRound(event: MessageEvent) =
        result(processor.processEvents(conv, isGroup = false, Seq(event))) shouldEqual Set.empty

      clock.advance(1.second) //conv will have time EPOCH, needs to be later than that
      testRound(MemberJoinEvent(conv.remoteId, RemoteInstant(clock.instant()), sender, membersAdded.toSeq))
      clock.advance(1.second)
      testRound(MemberLeaveEvent(conv.remoteId, RemoteInstant(clock.instant()), sender, membersAdded.toSeq))
      clock.advance(1.second)
      testRound(RenameConversationEvent(conv.remoteId, RemoteInstant(clock.instant()), sender, Name("new name")))
    }

    scenario("System message events are overridden if only local version is present") {
      val conv = ConversationData(ConvId("conv"), RConvId("r_conv"), None, UserId("creator"), ConversationType.OneToOne)
      val membersAdded = Set(
        UserId("user1"),
        UserId("user2")
      )

      clock.advance(1.second) //here, we create a local message
      val localMsg = MessageData(MessageId(), conv.id, RENAME, selfUserId, time = RemoteInstant(clock.instant()), localTime = LocalInstant(clock.instant()), state = Status.PENDING)

      clock.advance(1.second) //some time later, we get the response from the backend
      val event = RenameConversationEvent(conv.remoteId, RemoteInstant(clock.instant()), selfUserId, Name("new name"))

      (storage.hasSystemMessage _).expects(conv.id, event.time, RENAME, selfUserId).returning(Future.successful(false))
      (storage.lastLocalMessage _).expects(conv.id, RENAME).returning(Future.successful(Some(localMsg)))
      (storage.remove (_: MessageId)).expects(localMsg.id).returning(Future.successful({}))
      (storage.addMessage _).expects(*).onCall { msg : MessageData =>
        messagesInStorage.mutate(_ ++ Seq(msg))
        Future.successful(msg)
      }
      (convs.updateConversationLastRead _).expects(conv.id, event.time).onCall { (convId: ConvId, instant: RemoteInstant) =>
        Future.successful(Some((conv, conv.copy(lastRead = instant))))
      }

      val processor = getProcessor
      inside(result(processor.processEvents(conv, isGroup = false, Seq(event))).head) { case msg =>
        msg.msgType shouldEqual RENAME
        msg.time    shouldEqual event.time
      }
    }
  }


  def getProcessor = {
    val content = new MessagesContentUpdater(storage, convsStorage, deletions, prefs)

    //TODO make VerificationStateUpdater mockable
    (otrClientsStorage.onAdded _).expects().anyNumberOfTimes().returning(EventStream[Seq[UserClients]]())
    (otrClientsStorage.onUpdated _).expects().anyNumberOfTimes().returning(EventStream[Seq[(UserClients, UserClients)]]())
    (convsService.addUnexpectedMembersToConv _).expects(*, *).anyNumberOfTimes().returning(Future.successful({}))

    //often repeated mocks
    (deletions.getAll _).expects(*).anyNumberOfTimes().returning(Future.successful(Seq.empty))

    new MessageEventProcessor(selfUserId, storage, content, assets, replyHashing, msgsService, convsService, convs, otr)
  }

}
