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
package com.waz.utils

import android.text.TextUtils
import com.waz.api.Message
import org.json.JSONObject

object ServerTextJsonParseUtils {

  val emptyString = ""

  def getTextJsonContentTypeAndDetail(
      content: String,
      msgType: Message.Type = Message.Type.TEXTJSON
  ): (Option[String], (String, String)) =
    try {
      val obj         = if (msgType == Message.Type.TEXTJSON && !TextUtils.isEmpty(content)) new JSONObject(content) else null
      val contentType = if (null == obj) None else Option(obj.optString(ServerIdConst.KEY_TEXTJSON_MSGTYPE))
      (contentType, (emptyString, emptyString))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        (None, (emptyString, emptyString))
    }

  def getTextJsonContentType(content: String, msgType: Message.Type = Message.Type.TEXTJSON): Option[String] =
    try {
      val obj         = if (msgType == Message.Type.TEXTJSON && !TextUtils.isEmpty(content)) new JSONObject(content) else null
      val contentType = if (null == obj) None else Option(obj.optString(ServerIdConst.KEY_TEXTJSON_MSGTYPE))
      contentType
    } catch {
      case e: Exception =>
        e.printStackTrace()
        None
    }

  def isGroupReportBlocked(contentType: Option[String]): Boolean =
    ServerIdConst.CONV_NOTICE_REPORT_BLOCKED.equalsIgnoreCase(contentType.getOrElse(""))

  def isFilterContentType(contentType: Option[String]): Boolean = {
    val typ: String = contentType.getOrElse("")
    if (!TextUtils.isEmpty(typ) && ServerIdConst.CONV_NOTICE_REPORT_BLOCKED.equalsIgnoreCase(typ)) {
      true
    } else {
      false
    }
  }

}
