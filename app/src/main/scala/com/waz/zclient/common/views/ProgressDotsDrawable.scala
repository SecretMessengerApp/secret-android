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
package com.waz.zclient.common.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.os.SystemClock
import com.waz.utils.returning
import com.waz.zclient.R
import com.jsy.res.theme.ThemeUtils
import com.waz.zclient.utils.ContextUtils._

import scala.concurrent.duration._

class ProgressDotsDrawable(duration: FiniteDuration = (350 * 3).millis)(implicit context: Context) extends Drawable {

  private val lightPaint = returning(new Paint) { _.setColor(if(ThemeUtils.isDarkTheme(context)) getColor(R.color.black) else getColor(R.color.graphite_16)) }
  private val darkPaint = returning(new Paint) { _.setColor(if(ThemeUtils.isDarkTheme(context)) getColor(R.color.white) else getColor(R.color.graphite_40)) }

  private val dotSpacing = getDimenPx(R.dimen.progress_dot_spacing_and_width)
  private val dotRadius = dotSpacing / 2

  private val frameMillis = duration.toMillis / 3
  private val invalidate = new Runnable {
    override def run(): Unit = invalidateSelf()
  }

  override def setColorFilter(colorFilter: ColorFilter): Unit = {
    lightPaint.setColorFilter(colorFilter)
    darkPaint.setColorFilter(colorFilter)
  }

  override def setAlpha(alpha: Int): Unit = {
    lightPaint.setAlpha(alpha)
    darkPaint.setAlpha(alpha)
  }

  override def getOpacity: Int = PixelFormat.TRANSLUCENT

  override def draw(canvas: Canvas): Unit = {
    val uptime = SystemClock.uptimeMillis()
    val darkDotIndex = (uptime / frameMillis) % 3

    val centerY = canvas.getHeight / 2
    val left = canvas.getWidth / 2 - 2 * dotSpacing
    for (i <- 0 until 3) {
      canvas.drawCircle(left + i * 2 * dotSpacing, centerY, dotRadius, if (darkDotIndex == i) darkPaint else lightPaint)
    }

    scheduleSelf(invalidate, frameMillis - uptime % frameMillis + 1)
  }
}
