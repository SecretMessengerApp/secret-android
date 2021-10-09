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

import com.waz.db.Dao2
import com.waz.utils._
import com.waz.db.Col._
import com.waz.model
import com.waz.utils.wrappers.{DB, DBCursor}

case class AliasData(convId: ConvId,
                     userId: UserId,
                     aliasName: Option[Name] = None,
                     is_enable: Boolean = false
                    ) extends Identifiable[(ConvId, UserId)] {

  override def id: (ConvId, UserId) = (convId, userId)

  def getAliasName: String = if (is_enable) {
    aliasName.map(_.str).getOrElse("")
  } else {
    ""
  }
}

object AliasData {

  implicit object AliasDataDao extends Dao2[AliasData, ConvId, UserId] {
    val ConvId = id[ConvId]('conv_id).apply(_.convId)
    val UserId = id[UserId]('user_id).apply(_.userId)
    val AliasName = opt(text[model.Name]('alias_name, _.str, model.Name(_)))(_.aliasName.filterNot(_.isEmpty))
    val IsEnable = bool('is_enable)(_.is_enable)

    override val idCol = (ConvId, UserId)
    override val table = Table("Alias", ConvId, UserId, AliasName, IsEnable)

    override def apply(implicit cursor: DBCursor): AliasData = AliasData(ConvId, UserId, AliasName, IsEnable)

    def getAlias(convId: ConvId)(implicit db: DB) = {
      list(db.query(table.name, null, s"${ConvId.name} = ?", Array(convId.str), null, null, null))
    }

    def getAlias(convId: ConvId, userId: UserId)(implicit db: DB): Option[AliasData] = single({
      db.query(table.name, null, s"${ConvId.name} = ? AND ${UserId.name} = ?", Array(convId.str, userId.str)
        , null, null, null, "1")
    })
  }

}
