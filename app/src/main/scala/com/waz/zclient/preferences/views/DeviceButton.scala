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
package com.waz.zclient.preferences.views

import java.util.Locale

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import android.util.AttributeSet
import com.waz.model.otr.Client
import com.waz.zclient.R
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils.getDrawable
import com.waz.zclient.utils.ZTimeFormatter
import org.threeten.bp.{LocalDateTime, ZoneId}

class DeviceButton(context: Context, attrs: AttributeSet, style: Int) extends TextButton(context, attrs, style) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  def setDevice(client: Client, self: Boolean): Unit = {
    setTitle("\n" + client.model + "\n")
    setSubtitle(displayId(client))
    boldSubTitle()
    setEndGlyphImageDrawable(None, drawableForClient(client, self), true)
    /*
    title.foreach(_.setText("\n" + client.model + "\n"))
    title.foreach(_.setTextColor(Color.BLACK))
    subtitle.foreach(setOptionText(_, Some(displayId(client))))
    subtitle.foreach(subtitle => {
      TextViewUtils.boldText(subtitle, Color.parseColor("#33373a"))
    })
    setDrawableEnd(drawableForClient(client, self))
    */
  }

  private def drawableForClient(client: Client, self: Boolean): Option[Drawable] = {
    if (self)
      None
    else
      Option(getDrawable(if (client.isVerified) R.drawable.shield_full else R.drawable.shield_half))
  }

  private def displayId(client: Client): String = {
    //    val date = client.regTime match {
    //      case Some(regTime) =>
    //        getString(R.string.pref_devices_device_activation_subtitle, TimeStamp(regTime).string)
    //      case _ =>
    //        ""
    //    }
    //    s"ID: ${client.displayId}\n$date"
    val id = f"${client.id.str.toUpperCase(Locale.ENGLISH)}%16s" replace(' ', '0') grouped 4 map { group =>
      val (bold, normal) = group.splitAt(2)
      s"[[$bold]] $normal"
    } mkString " "

    val date = client.regTime match {
      case Some(regTime) =>
        val now = LocalDateTime.now(ZoneId.systemDefault)
        val time = ZTimeFormatter.getSeparatorTime(context, now, LocalDateTime.ofInstant(regTime, ZoneId.systemDefault), DateFormat.is24HourFormat(context), ZoneId.systemDefault, false)
        context.getString(R.string.pref_devices_device_activation_subtitle, time)
      case _ =>
        ""
    }
    s"ID: $id\n\n$date\n"
  }

}
