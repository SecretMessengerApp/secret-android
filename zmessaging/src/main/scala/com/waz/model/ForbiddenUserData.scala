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

import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json.JSONArray

import scala.util.Try

case class ForbiddenUserData(user: UserId, duration: Int, endTime: String)

object ForbiddenUserData {

  def encode(users: Seq[ForbiddenUserData]): String = {
    val contentJsonArray = new JSONArray()

    users.foreach(itemUser =>
      JsonEncoder { encoder =>
        encoder.put("user", itemUser.user)
        encoder.put("duration", itemUser.duration)
        encoder.put("end_time", itemUser.endTime)

        contentJsonArray.put(encoder)
      }
    )
    contentJsonArray.toString
  }

  def decode(content: String): Seq[ForbiddenUserData] = {
    Try(new JSONArray(content)).toOption.filter(_.length() > 0) flatMap { arrays =>
      Some(Seq.tabulate(arrays.length())(arrays.getJSONObject).map { itemJson =>
        ForbiddenUserData(JsonDecoder.decodeUserId('user)(itemJson)
          , JsonDecoder.decodeInt('duration)(itemJson)
          , JsonDecoder.decodeString('end_time)(itemJson))
      })
    } match {
      case Some(data) => data
      case _ => Seq.empty
    }
  }
}
