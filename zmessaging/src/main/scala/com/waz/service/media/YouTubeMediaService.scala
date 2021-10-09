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
package com.waz.service.media

import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.model.messages.media.MediaAssetData
import com.waz.model.messages.media.MediaAssetData.MediaWithImages
import com.waz.service.assets.AssetService
import com.waz.sync.client.YouTubeClient
import com.waz.threading.Threading
import com.waz.utils.wrappers.URI
import com.waz.sync.client.ErrorOr

import scala.concurrent.Future

class YouTubeMediaService(client: YouTubeClient, assets: AssetService) extends DerivedLogTag {
  import Threading.Implicits.Background

  def updateMedia(msg: MessageData, content: MessageContent): ErrorOr[MessageContent] = {
    RichMediaContentParser.youtubeVideoId(content.content) match {
      case Some(vId) =>
        client.loadVideo(vId) flatMap {
          case Right(MediaWithImages(media, images)) =>
            verbose(l"got youtube track: $media, images: $images")
            assets.updateAssets(images.to[Vector]) map { _ =>
              Right(content.copy(tpe = Message.Part.Type.YOUTUBE, richMedia = Some(media)))
            }

          case Left(error) if error.isFatal =>
            warn(l"snippet loading for ${redactedString(content.content)} failed fatally: $error, switching back to text")
            Future successful Right(content.copy(tpe = Message.Part.Type.TEXT, richMedia = None))

          case Left(error) =>
            warn(l"snippet loading failed: $error")
            Future successful Left(error)
        }
      case None =>
        warn(l"no video id found in message: $content")
        Future.successful(Right(content.copy(tpe = Message.Part.Type.TEXT, richMedia = None)))
    }
  }

  def prepareStreaming(media: MediaAssetData): ErrorOr[Vector[URI]] = Future.successful(Right(media.tracks flatMap (_.streamUrl) map URI.parse))
}
