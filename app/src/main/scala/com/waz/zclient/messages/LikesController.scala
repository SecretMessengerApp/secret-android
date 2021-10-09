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

import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient.{Injectable, Injector}
import com.waz.api.Message.Type._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageData, UserId}

import scala.concurrent.ExecutionContext

class LikesController(implicit ec: EventContext, injector: Injector) extends Injectable with DerivedLogTag{

  implicit val executionContext: ExecutionContext = Threading.Background

  val zms = inject[Signal[ZMessaging]]
  val reactions = zms.map(_.reactions)

  val onLikeButtonClicked = EventStream[MessageAndLikes]()
  val onViewDoubleClicked = EventStream[MessageAndLikes]()

  val onLikedUserChanged = EventStream[(MessageAndLikes, Seq[UserId])]()

  onLikeButtonClicked { msgAndLikes =>
    if (!msgAndLikes.message.isEphemeral) {
      toggleLike(msgAndLikes).onComplete {
        case scala.util.Success(likedIds) =>
          onLikedUserChanged ! (msgAndLikes, likedIds.likers.keys.toSeq)
        case _ =>
      }(Threading.Background)
    }
  }
  onViewDoubleClicked { msgAndLikes =>
    if (!msgAndLikes.message.isEphemeral) {
      toggleLike(msgAndLikes).onComplete {
        case scala.util.Success(likedIds) =>
          onLikedUserChanged ! (msgAndLikes, likedIds.likers.keys.toSeq)
        case _ =>
      }(Threading.Background)
    }
  }

  private def toggleLike(msgAndLikes: MessageAndLikes) = {
    //    if (!msgAndLikes.message.isEphemeral) {
    //      reactions.head.map { reacts =>
    //        val msg = msgAndLikes.message
    //        if (msgAndLikes.likedBySelf) reacts.unlike(msg.convId, msg.id)
    //        else reacts.like(msg.convId, msg.id)
    //      }(Threading.Background)
    //    }
    //    import Threading.Background
    for {
      reacts <- reactions.head
      r <- {
        val msg = msgAndLikes.message
        if (msgAndLikes.likedBySelf) reacts.unlike(msg.convId, msg.id)
        else reacts.like(msg.convId, msg.id)
      }
    } yield {
      r
    }
  }
}

object LikesController {
  val LikeableMessages = Set(TEXT, TEXT_EMOJI_ONLY, ASSET, ANY_ASSET, VIDEO_ASSET, AUDIO_ASSET, RICH_MEDIA, LOCATION)

  def isLikeable(m: MessageData): Boolean = LikeableMessages.contains(m.msgType)
}
