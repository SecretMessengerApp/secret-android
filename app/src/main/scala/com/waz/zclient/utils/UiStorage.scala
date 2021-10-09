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
package com.waz.zclient.utils

import android.util.LruCache
import com.waz.api
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.utils.UiStorage._
import com.waz.zclient.{Injectable, Injector}

import scala.collection.immutable.Set

class UiStorage(implicit inj: Injector) extends Injectable {

  private val zms = inject[Signal[ZMessaging]]

  val userCache = new LruCache[UserId, Signal[UserData]](UserCacheSize)
  val conversationCache = new LruCache[ConvId, Signal[ConversationData]](ConversationCacheSize)
  val conversationMembersCache = new LruCache[ConvId, Signal[Set[UserId]]](ConversationMembersCacheSize)

  val assetCache = new LruCache[AssetId, Signal[(AssetData, api.AssetStatus)]](AssetCacheSize)

  def loadUser(userId: UserId) = zms.flatMap(_.usersStorage.signal(userId))

  def loadConversation(conversationId: ConvId) = zms.flatMap(_.convsStorage.signal(conversationId))

  def loadConversationMembers(conversationId: ConvId) = zms.flatMap(_.membersStorage.activeMembers(conversationId).map(_.toSet))

  def loadAsset(assetId: AssetId) = zms.flatMap(_.assets.assetSignal(assetId))

  def loadAlias(convId: ConvId, userId: UserId): Signal[Option[AliasData]] = zms.flatMap(_.aliasStorage.optSignal(convId, userId))
}

object UiStorage {
  val UserCacheSize = 500
  val ConversationCacheSize = 200
  val ConversationMembersCacheSize = 200
  val AssetCacheSize = 200
}

object UserSignal {
  def apply(userId: UserId)(implicit uiStorage: UiStorage): Signal[UserData] = {
    Option(uiStorage.userCache.get(userId)).getOrElse(returning(uiStorage.loadUser(userId))(uiStorage.userCache.put(userId, _)))
  }
}

object ConversationSignal {
  def apply(conversationId: ConvId)(implicit uiStorage: UiStorage): Signal[ConversationData] = {
    Option(uiStorage.conversationCache.get(conversationId)).getOrElse(returning(uiStorage.loadConversation(conversationId))(uiStorage.conversationCache.put(conversationId, _)))
  }
}

object ConversationMembersSignal {
  def apply(conversationId: ConvId)(implicit uiStorage: UiStorage): Signal[Set[UserId]] = {
    Option(uiStorage.conversationMembersCache.get(conversationId)).getOrElse(returning(uiStorage.loadConversationMembers(conversationId))(uiStorage.conversationMembersCache.put(conversationId, _)))
  }
}

object UserSetSignal {
  def apply(ids: Set[UserId])(implicit uiStorage: UiStorage): Signal[Set[UserData]] = {
    Signal.sequence(ids.map(id => UserSignal(id)).toSeq: _*).map(_.toSet)
  }
}

object UserVectorSignal {
  def apply(ids: Vector[UserId])(implicit uiStorage: UiStorage): Signal[Vector[UserData]] = {
    Signal.sequence(ids.map(id => UserSignal(id)).toVector: _*).map(_.toVector)
  }
}

object AssetSignal {
  def apply(assetId: AssetId)(implicit uiStorage: UiStorage): Signal[(AssetData, api.AssetStatus)] = {
    Option(uiStorage.assetCache.get(assetId)).getOrElse(returning(uiStorage.loadAsset(assetId))(uiStorage.assetCache.put(assetId, _)))
  }
}

object AliasSignal {
  def apply(convId: ConvId, userId: UserId)(implicit uiStorage: UiStorage): Signal[Option[AliasData]] = uiStorage.loadAlias(convId, userId)
}
