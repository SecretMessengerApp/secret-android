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

import android.content.Context
import androidx.paging.PagedList
import com.waz.api.IConversation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageData.MessageDataDao
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.threading.Threading.Implicits.Background
import com.waz.utils.events._
import com.waz.utils.returning
import com.waz.utils.wrappers.DBCursor
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessagePagedListController._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{Injectable, Injector}

import java.util.concurrent.Executor
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MessagePagedListController()(implicit inj: Injector, ec: EventContext, cxt: Context)
  extends Injectable with DerivedLogTag {

  private val zms = inject[Signal[ZMessaging]]
  private val convController = inject[ConversationController]
  private val storage = zms.map(_.storage.db)
  private val messageActionsController = inject[MessageActionsController]

  private def loadCursor(convId: ConvId): Future[Option[DBCursor]] = {
    var view_chg_mem_notify = false

    convController.currentConv.currentValue.foreach{
      case conversationData =>
        if(conversationData.view_chg_mem_notify){
           view_chg_mem_notify = true
        }else{
          view_chg_mem_notify = false
        }
    }

    if(view_chg_mem_notify){
      storage.head.flatMap(_.read(implicit db => MessageDataDao.msgCursor(convId))).map {
        Option(_)
      }
    }else{
      val userId = UserId(SpUtils.getUserId(cxt))
      storage.head.flatMap(_.read(implicit db => MessageDataDao.msgCursorNotMemberEvent(convId,userId))).map {
        Option(_)
      }
    }
  }

  @volatile private var _pagedList = Option.empty[PagedList[MessageAndLikes]]

  private def getPagedList(cursor: Option[DBCursor]): PagedList[MessageAndLikes] = {
    def createPagedList(config: PagedListConfig) =
      new PagedList.Builder[Integer, MessageAndLikes](new MessageDataSource(cursor), config.config)
        .setFetchExecutor(ExecutorWrapper(Threading.Background))
        .setNotifyExecutor(ExecutorWrapper(Threading.Ui))
        .build()

    _pagedList.foreach(_.getDataSource.invalidate())
    verbose(l"getPagedList")
    returning(
      Try(createPagedList(NormalPagedListConfig)).getOrElse(createPagedList(MinPagedListConfig))
    ) { pl =>
      _pagedList = Option(pl)
    }
  }

  private def cursorRefreshEvent(zms: ZMessaging, convId: ConvId): EventStream[_] = {
    EventStream.union(
      zms.messagesStorage.onMessagesDeletedInConversation.map(_.contains(convId)),
      zms.messagesStorage.onAdded.map(messages => messages.exists(_.convId == convId)),
      zms.messagesStorage.onUpdated.map(_.exists { case (prev, updated) =>
        updated.convId == convId && !MessagesPagedListAdapter.areMessageContentsTheSame(prev, updated)
      }),
//      new FutureEventStream(zms.forbidsStorage.onChanged.map(_.map(_.message)), { msgs: Seq[MessageId] =>
//        zms.messagesStorage.getMessages(msgs: _*).map(_.flatten.exists(_.convId == convId))
//      }),
      new FutureEventStream(zms.reactionsStorage.onChanged.map(_.map(_.message)), { msgs: Seq[MessageId] =>
        zms.messagesStorage.getMessages(msgs: _*).map(_.flatten.exists(_.convId == convId))
      })
    ).filter(identity)
  }

  lazy val pagedListData: Signal[(MessageAdapterData, PagedListWrapper[MessageAndLikes], Option[MessageId])] = for {
    z <- zms
    (cId, cTeam, teamOnly, convType) <- convController.currentConv.map(c => (c.id, c.team, c.isTeamOnly, c.convType))
    isGroup <- Signal.future(z.conversations.isGroupConversation(cId))
    canHaveLink = isGroup && cTeam.exists(z.teamId.contains(_)) && !teamOnly
    cursor <- RefreshingSignal(loadCursor(cId), cursorRefreshEvent(z, cId))
    _ = verbose(l"cursor changed")
    list = PagedListWrapper(getPagedList(cursor))
    lastRead <- convController.currentConv.map(_.lastRead)
    messageToReveal <- messageActionsController.messageToReveal.map(_.map(_.id))
  } yield {
    (MessageAdapterData(cId, lastRead, isGroup, canHaveLink, z.selfUserId, z.teamId, convType), list, messageToReveal)
  }

  val messageToCurrentConvAdded: EventStream[Seq[MessageData]] = (for {
    z <- zms
    conv <- convController.currentConvId
    added <- Signal.wrap(z.messagesStorage.onAdded.map(_.filter(_.convId == conv)).filter(_.nonEmpty))
  } yield added).onChanged

  Threading.Ui
}

object MessagePagedListController {
  case class PagedListConfig(pageSize: Int, initialLoadSizeHint: Int, prefetchDistance: Int) {
    lazy val config = new PagedList.Config.Builder()
      .setPageSize(pageSize)
      .setInitialLoadSizeHint(initialLoadSizeHint)
      .setEnablePlaceholders(true)
      //.setPrefetchDistance(prefetchDistance)
      .build()
  }

  val NormalPagedListConfig = PagedListConfig(50, 50, 50)
  val MinPagedListConfig    = PagedListConfig(20, 20, 20)
}

case class PagedListWrapper[T](pagedList: PagedList[T]) {
  override def equals(obj: scala.Any): Boolean = false
}

case class ExecutorWrapper(ec: ExecutionContext) extends Executor {
  override def execute(command: Runnable): Unit = ec.execute(command)
}

case class MessageAdapterData(convId: ConvId, lastRead: RemoteInstant, isGroup: Boolean, canHaveLink: Boolean, selfId: UserId, teamId: Option[TeamId], convType: IConversation.Type)

object MessageAdapterData {
  val Empty = MessageAdapterData(ConvId(), RemoteInstant.Epoch, isGroup = false, canHaveLink = false, UserId(), None, IConversation.Type.UNKNOWN)
}
