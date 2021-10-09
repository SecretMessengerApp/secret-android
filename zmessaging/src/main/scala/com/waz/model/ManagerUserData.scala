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

import com.waz.utils.JsonDecoder.decodeOptString
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try


case class ManagerUserData(id: Option[String] = None, name: Option[String] = None, handle: Option[String] = None, asset: Option[String] = None) {}

object ManagerUserData {
  def apply(implicit js: JSONObject): ManagerUserData = {
    ManagerUserData(
      id = decodeOptString('id),
      name = decodeOptString('name),
      handle = decodeOptString('handle)(js),
      asset = decodeOptString('asset)(js)
    )
  }

  def encodeManager(managers: Seq[ManagerUserData]): JSONArray = {
    val arr = new json.JSONArray()
    managers.foreach { manager =>
      val objecj = JsonEncoder { o =>
        manager.id.foreach(o.put("id", _))
        manager.name.foreach(o.put("name", _))
        manager.handle.foreach(o.put("handle", _))
        manager.asset.foreach(o.put("asset", _))
      }
      arr.put(objecj)
    }
    arr
  }

  def getManager(implicit jss: String): Seq[ManagerUserData] = {
    import JsonDecoder._
    try {
      val js: JSONArray = new JSONArray(jss)
      Try(js).toOption.filter(_.length() > 0) flatMap { manager =>
        Some(Seq.tabulate(manager.length())(manager.getJSONObject).map { js =>
          ManagerUserData(
            id = decodeOptString('id)(js),
            name = decodeOptString('name)(js),
            handle = decodeOptString('handle)(js),
            asset = decodeOptString('asset)(js)
          )
        })
      } match {
        case Some(x: Seq[ManagerUserData]) => x
        case None => null
      }
    } catch {
      case e: Exception =>
        Seq.empty
    }
  }

}
