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

import com.waz.db.{Dao, Dao2}
import com.waz.utils._
import com.waz.db.Col._
import com.waz.utils.wrappers.{DB, DBCursor}
case class UserNoticeData(uuid: Uid,
                     msgType: String,
                     noticeId: String,
                     conv:String,
                     name:String,
                     img:String,
                     joinUrl:String,
                     updateTime:RemoteInstant          = RemoteInstant.Epoch,
                     read:Boolean=false,
                     subType: Int = 0
                    ) extends Identifiable[Uid] {

  override def id: Uid = uuid

}

object UserNoticeData {

  implicit object UserNoticeDao extends Dao[UserNoticeData, Uid] {
    val Uuid =  id[Uid]('_id).apply(_.uuid)
    val MsgType = text('msg_type).apply(_.msgType)
    val NoticeId =  text('notice_id).apply(_.noticeId)
    val Conv=text('conv).apply(_.conv)
    val Name = text('name).apply(_.name)
    val Image=text('img).apply(_.img)
    val JoinUrl=text('join_url).apply(_.joinUrl)
    val UpdateTime= remoteTimestamp('update_time)(_.updateTime)
    val Read = bool('read)(_.read)
    val SubType = int('sub_type)(_.subType)

    override val idCol=Uuid
    override val table = Table("UserNotice", Uuid, MsgType,NoticeId,Conv,Name,Image,JoinUrl,UpdateTime,Read,SubType)

    override def apply(implicit cursor: DBCursor): UserNoticeData = UserNoticeData(Uuid, MsgType,NoticeId,Conv,Name,Image,JoinUrl,UpdateTime,Read,SubType)

    def findByNoticeId(noticeId: String)(implicit db: DB) = iterating(find(NoticeId, noticeId))
    def findByConv(conv: String)(implicit db: DB) = iterating(find(Conv, conv))
    def listByType(msgType:String)(implicit db: DB)=list(db.query(table.name, null, s"${MsgType.name} = '$msgType'", null, null, null, "sub_type asc,update_time desc"))

  }

}
