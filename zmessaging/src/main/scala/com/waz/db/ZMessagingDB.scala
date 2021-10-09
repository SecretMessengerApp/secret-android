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
package com.waz.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.waz.content.PropertiesDao
import com.waz.db.ZMessagingDB.{DbVersion, daos, migrations}
import com.waz.db.migrate._
import com.waz.model.AddressBook.ContactHashesDao
import com.waz.model.AliasData.AliasDataDao
import com.waz.model.AssetData.AssetDataDao
import com.waz.model.Contact.{ContactsDao, ContactsOnWireDao, EmailAddressesDao, PhoneNumbersDao}
import com.waz.model.ConversationData.ConversationDataDao
import com.waz.model.ConversationMemberData.ConversationMemberDataDao
import com.waz.model.EditHistory.EditHistoryDao
import com.waz.model.ErrorData.ErrorDataDao
import com.waz.model.ForbidData.ForbidDao
import com.waz.model.KeyValueData.KeyValueDataDao
import com.waz.model.Liking.LikingDao
import com.waz.model.MessageData.MessageDataDao
import com.waz.model.MsgDeletion.MsgDeletionDao
import com.waz.model.NotificationData.NotificationDataDao
import com.waz.model.PushNotificationEvents.PushNotificationEventsDao
import com.waz.model.ReadReceipt.ReadReceiptDao
import com.waz.model.SearchQueryCache.SearchQueryCacheDao
import com.waz.model.UserData.UserDataDao
import com.waz.model.UserNoticeData.UserNoticeDao
import com.waz.model.otr.UserClients.UserClientsDao
import com.waz.model.sync.SyncJob.SyncJobDao
import com.waz.model.{MessageActions, MessageContentIndexDao, NatureTypes}
import com.waz.repository.FCMNotificationStatsRepository.FCMNotificationStatsDao
import com.waz.repository.FCMNotificationsRepository.FCMNotificationsDao
import com.waz.service.tracking.TrackingService
import com.waz.utils.ServerIdConst

class ZMessagingDB(context: Context, dbName: String, tracking: TrackingService) extends DaoDB(context.getApplicationContext, dbName, null, DbVersion, daos, migrations, tracking) {

  override def onUpgrade(db: SQLiteDatabase, from: Int, to: Int): Unit = {
    super.onUpgrade(db, from, to)
  }
}

object ZMessagingDB {

  val DbVersion = 1

  lazy val daos = Seq(
    UserDataDao, SearchQueryCacheDao, AssetDataDao, ConversationDataDao,
    ConversationMemberDataDao, MessageDataDao, KeyValueDataDao,
    SyncJobDao, ErrorDataDao, NotificationDataDao,
    ContactHashesDao, ContactsOnWireDao, UserClientsDao, LikingDao,
    ContactsDao, EmailAddressesDao, PhoneNumbersDao, MsgDeletionDao,
    EditHistoryDao, MessageContentIndexDao, PushNotificationEventsDao,
    ReadReceiptDao, PropertiesDao, FCMNotificationsDao, FCMNotificationStatsDao,
    AliasDataDao, ForbidDao,UserNoticeDao
  )

  lazy val migrations = Seq()
}
