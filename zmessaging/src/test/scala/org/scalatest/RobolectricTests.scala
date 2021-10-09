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
package org.scalatest

import com.waz.threading.Threading
import org.robolectric.shadows._

trait RobolectricTests extends RobolectricSuite { self: Suite =>

  Threading.AssertsEnabled = false

  var testName = ""

  override def useInstrumentation(name: String): Option[Boolean] =
    if (name.startsWith("com.github") || name.startsWith("com.wire.cryptobox")) Some(false) else super.useInstrumentation(name)

  override def robolectricShadows: Seq[Class[_]] = Seq(classOf[ShadowApplication], classOf[ShadowAudioManager2],
    classOf[ShadowLooper2], classOf[ShadowSQLiteConnection2], classOf[ShadowGeocoder2], classOf[ShadowCursorWindow2], classOf[ShadowFileProvider],
    classOf[ShadowContentResolver2], classOf[ShadowMediaMetadataRetriever2])

  abstract override protected def runTest(testName: String, args: Args): Status = {
    this.testName = testName
    super.runTest(testName, args)
  }
}
