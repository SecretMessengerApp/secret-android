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
package com.waz.model

import com.waz.api.NotificationsHandler.NotificationType
import com.waz.api.NotificationsHandler.NotificationType.LikedContent
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model.ConversationData.ConversationType
import com.waz.utils.wrappers.DBCursor
import com.waz.utils.{EnumCodec, Identifiable, JsonDecoder, JsonEncoder}
import org.json.JSONObject

case class NotificationData(override val id:   NotId                = NotId(),
                            msg:               String               = "",
                            conv:              ConvId               = ConvId(),
                            user:              UserId               = UserId(),
                            msgType:           NotificationType     = NotificationType.TEXT,
                            convType:          Int                  = ConversationType.Unknown.id,
                            time:              RemoteInstant        = RemoteInstant.Epoch,
                            ephemeral:         Boolean              = false,
                            isSelfMentioned:   Boolean              = false,
                            likedContent:      Option[LikedContent] = None,
                            isReply:           Boolean              = false,
                            isShowNotify:      Boolean              = true,
                            hasBeenDisplayed:  Boolean              = false) extends Identifiable[NotId]

object NotificationData {

  implicit lazy val Decoder: JsonDecoder[NotificationData] = new JsonDecoder[NotificationData] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): NotificationData =
      NotificationData(
        id               = NotId('id: String),
        msg              = 'message,
        conv             ='conv,
        user             ='user,
        msgType          = NotificationCodec.decode('msgType),
        convType         = 'convType,
        time             = 'time,
        ephemeral        ='ephemeral,
        isSelfMentioned  = 'isSelfMentioned,
        likedContent     = decodeOptString('likedContent).map(LikedContentCodec.decode),
        isReply          = 'isReply,
        isShowNotify     = 'isShowNotify,
        hasBeenDisplayed = 'hasBeenDisplayed)
  }

  implicit lazy val Encoder: JsonEncoder[NotificationData] = new JsonEncoder[NotificationData] {
    override def apply(v: NotificationData): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("message", v.msg)
      o.put("conv", v.conv.str)
      o.put("user", v.user.str)
      o.put("msgType", NotificationCodec.encode(v.msgType))
      o.put("convType", v.convType)
      o.put("time", v.time.toEpochMilli)
      o.put("ephemeral", v.ephemeral)
      o.put("isSelfMentioned", v.isSelfMentioned)
      o.put("isReply", v.isReply)
      o.put("isShowNotify", v.isShowNotify)
      o.put("hasBeenDisplayed", v.hasBeenDisplayed)
      v.likedContent.foreach(v => o.put("likedContent", LikedContentCodec.encode(v)))
    }
  }

  implicit object NotificationDataDao extends Dao[NotificationData, NotId] {
    val Id = id[NotId]('_id, "PRIMARY KEY").apply(_.id)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("NotificationData", Id, Data)

    override def apply(implicit cursor: DBCursor): NotificationData = JsonDecoder.decode(cursor.getString(1))
  }

  implicit val LikedContentCodec: EnumCodec[LikedContent, String] = EnumCodec.injective {
    case LikedContent.TEXT_OR_URL => "TextOrUrl"
    case LikedContent.PICTURE => "Picture"
    case LikedContent.OTHER => "Other"
    case _ => "Other"
  }

  implicit val NotificationOrdering: Ordering[NotificationData] = Ordering.by((data: NotificationData) => (data.time, data.id))

  implicit lazy val NotificationCodec: EnumCodec[NotificationType, String] = EnumCodec.injective {
    case NotificationType.CONNECT_REQUEST => "ConnectRequest"
    case NotificationType.CONNECT_ACCEPTED => "ConnectAccepted"
    case NotificationType.CONTACT_JOIN => "ContactJoin"
    case NotificationType.ASSET => "Asset"
    case NotificationType.ANY_ASSET => "AnyAsset"
    case NotificationType.VIDEO_ASSET => "VideoAsset"
    case NotificationType.AUDIO_ASSET => "AudioAsset"
    case NotificationType.TEXT => "Text"
    case NotificationType.MEMBER_JOIN => "MemberJoin"
    case NotificationType.MEMBER_LEAVE => "MemberLeave"
    case NotificationType.RENAME => "Rename"
    case NotificationType.KNOCK => "Knock"
    case NotificationType.MISSED_CALL => "MissedCall"
    case NotificationType.LIKE => "Like"
    case NotificationType.LOCATION => "Location"
    case NotificationType.MESSAGE_SENDING_FAILED => "MessageSendingFailed"
    case NotificationType.TEXTJSON => "TextJson"
    case _ => "Text"
  }
}
