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
package com.waz.content

import com.waz.content.Preferences.PrefKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.specs.AndroidFreeSpec
import com.waz.testutils.TestUserPreferences
import com.waz.threading.Threading

class PreferencesSpec extends AndroidFreeSpec with DerivedLogTag {

  implicit val ec = Threading.Background

  val prefs = new TestUserPreferences
  val prefKey = PrefKey[Boolean]("test")

  scenario("Preference caching and updating") {

    val pref1 = prefs.preference(prefKey)
    val pref2 = prefs.preference(prefKey)
    pref2 := true

    result(pref1.signal.filter(_ == true).head)

  }

}
