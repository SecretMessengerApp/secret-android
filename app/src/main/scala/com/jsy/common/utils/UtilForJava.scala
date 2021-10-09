/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.utils

import com.waz.api.IConversation
import com.waz.model._

object UtilForJava {

  def getHandler(handle: Option[Handle]): String = {
    if (handle.nonEmpty) handle.head.string else ""
  }

  def getNameByUserData(userData: UserData): String = {
    if (userData == null || userData.displayName == null) ""
    else
      userData.displayName.str
  }

  def getAssetId(rAssetId: Option[String] = None, picture: Option[AssetId] = None): String = {
    val assetId = rAssetId match {
      case Some(a) =>
        Option(a)
      case _ =>
        picture match {
          case Some(p) =>
            Option(p.str)
          case _ =>
            Option.empty[String]
        }
    }
    if (assetId.isDefined) assetId.get else ""
  }

  def getNameByConversationData(conversationData: ConversationData): String = {
    if (conversationData == null || conversationData.displayName == null) ""
    else
      conversationData.displayName.str
  }

  def getConversationMemsum(conversationData: ConversationData): Int = {
    if (conversationData == null || conversationData.memsum.isEmpty) 0
    else conversationData.memsum.head
  }

  def getSmallRAssetId(conversationData: ConversationData, defStr: String): String = {
    if (conversationData.smallRAssetId == null) defStr else conversationData.smallRAssetId.str
  }

  def isSupportGroupChat(conversationData: ConversationData): Boolean = {
    conversationData != null && Set(IConversation.Type.GROUP, IConversation.Type.THROUSANDS_GROUP).contains(conversationData.convType)
  }

  def imageRAssetId(assets: Option[Seq[AssetData]], isSmall: Boolean): RAssetId = if (assets.nonEmpty) assets.filter(_ != null) match {
    case Some(assetDatas: Seq[AssetData]) => if (assetDatas.nonEmpty) {
      val rAssetId = if (isSmall) {
        assetDatas.head.remoteId
      } else {
        assetDatas.last.remoteId
      }
      rAssetId.filter(_ != null) match {
        case Some(rAssetId: RAssetId) => rAssetId
        case None => null
      }
    } else {
      null
    }
    case None => null
  } else null

}
