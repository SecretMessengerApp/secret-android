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

import com.waz.content.UserNoticeStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{RConvId, RemoteInstant, Uid, UserNoticeData, UserNoticeEvent}
import com.waz.service.EventScheduler.Stage
import com.waz.service.conversation.ConversationsService
import com.waz.threading.Threading
import com.waz.utils.ServerIdConst
import com.waz.utils.events.EventContext

import scala.concurrent.Future

trait UserNoticeService {
  def userNoticeUpdateEventsStage: Stage.Atomic
}

class UserNoticeServiceImpl(userNoticeStorage: UserNoticeStorage, conversationsService: ConversationsService) extends UserNoticeService with DerivedLogTag {

  import Threading.Implicits.Background

  private implicit val ec = EventContext.Global

  override val userNoticeUpdateEventsStage: Stage.Atomic = EventScheduler.Stage[UserNoticeEvent] { (_, e) =>
    val msgType = e.head.msgType
    val msgData = e.head.msgData
    if (ServerIdConst.USER_NOTICE.equals(msgType)) {
      val noticeId = msgData.optString("id")
      val conv = msgData.optString("conv")
      val name = msgData.optString("name")
      val img = msgData.optString("img")
      val joinUrl = msgData.optString("join_url")
      val now = ZMessaging.clock
      conversationsService.getByRemoteId(RConvId(conv)).map {
        case Some(_) =>
        case None =>
          userNoticeStorage.findByNoticeId(noticeId).map {
            case Some(userNoticeData) =>
              userNoticeStorage.update(userNoticeData.id, userNoticeData => userNoticeData.copy(msgType = msgType, noticeId = noticeId, conv = conv, name = name, img = img, joinUrl = joinUrl, updateTime = RemoteInstant(now.instant())))
            case None =>
              userNoticeStorage.insertData(UserNoticeData(Uid(), msgType, noticeId, conv, name, img, joinUrl, updateTime = RemoteInstant(now.instant())))
          }
      }
    }
    else {
      Future.successful({})
    }
  }

}

object UserNoticeService {
  val MAX_FIVE_ELEMENT_SIZE = 40
}
