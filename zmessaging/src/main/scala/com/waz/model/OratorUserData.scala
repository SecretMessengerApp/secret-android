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

import com.waz.utils.JsonDecoder.{decodeOptString}
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try

case class OratorUserData(id: Option[String] = None, name: Option[String] = None) {}

object OratorUserData{


  def apply(implicit js:JSONObject): OratorUserData = {
    OratorUserData(
      id = decodeOptString('id),
      name = decodeOptString('name)
    )
  }

  def encodeOrator(orators:Seq[OratorUserData]):JSONArray = {
    val arr = new json.JSONArray()
    orators.foreach{  orator=>
      val objecj = JsonEncoder { o =>
        orator.id.foreach(o.put("id",_))
        orator.name.foreach(o.put("name",_))
      }
      arr.put(objecj)
    }
    arr
  }

  def getOrator(implicit jss: String): Seq[OratorUserData] = {
    import JsonDecoder._
    try {
      val js: JSONArray = new JSONArray(jss)
      Try(js).toOption.filter(_.length() > 0) flatMap { orator =>
        Some(Seq.tabulate(orator.length())(orator.getJSONObject).map { js =>
          OratorUserData(
            id = decodeOptString('id)(js),
            name = decodeOptString('name)(js)
          )
        })
      } match {
        case Some(x: Seq[OratorUserData]) => x
        case None => null
      }
    } catch {
      case e: Exception =>
        Seq.empty
    }
  }


}
