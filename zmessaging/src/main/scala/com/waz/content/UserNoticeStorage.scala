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
package com.waz.content

import android.content.Context
import com.waz.log.BasicLogging.LogTag
import com.waz.model.UserNoticeData.UserNoticeDao
import com.waz.model.{Uid, UserNoticeData}
import com.waz.service.UserNoticeService
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.TrimmingLruCache.Fixed
import com.waz.utils.{CachedStorage, CachedStorageImpl, ServerIdConst, TrimmingLruCache}

import scala.concurrent.Future
import scala.util.Success

trait UserNoticeStorage extends CachedStorage[Uid, UserNoticeData] {
   def findByNoticeId(noticeId:String):Future[Option[UserNoticeData]]
   def findByConv(conv:String):Future[Option[UserNoticeData]]
   def insertData(data:UserNoticeData):Unit
}

class UserNoticeStorageImpl(context: Context, storage: ZmsDatabase)
  extends CachedStorageImpl[Uid, UserNoticeData](new TrimmingLruCache(context, Fixed(2000)), storage)(UserNoticeDao, LogTag("UserNoticeStorage_Cached"))
    with UserNoticeStorage {
  private implicit val dispatcher = new SerialDispatchQueue(name = "UserNoticeStorage")

  override def findByNoticeId(noticeId: String): Future[Option[UserNoticeData]] = {
    find(c => c.noticeId == noticeId, UserNoticeDao.findByNoticeId(noticeId)(_), identity).map(ss =>ss.headOption)
  }

  override def findByConv(conv: String): Future[Option[UserNoticeData]] = {
    find(c => c.conv == conv, UserNoticeDao.findByConv(conv)(_), identity).map(ss =>ss.headOption)
  }

  override def insertData(data: UserNoticeData) = {
    insert(data).onComplete(_ =>
    storage.read(implicit db =>UserNoticeData.UserNoticeDao.listByType(ServerIdConst.USER_NOTICE)).onComplete{
      case Success(list) =>{
        if(list.size>=UserNoticeService.MAX_FIVE_ELEMENT_SIZE){
          list.slice(UserNoticeService.MAX_FIVE_ELEMENT_SIZE,list.size).foreach(data => {
            remove(data.uuid)
          })
        }
      }
      case _ =>
    })
  }
}
