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
package com.jsy.common.moduleProxy

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.waz.service.AccountManager.ClientRegistrationState

trait ProxyAppEntryActivity extends AppCompatActivity {

  def onEnterApplication(openSettings: Boolean, clientRegState: Option[ClientRegistrationState] = None, isBack: Boolean = false): Unit

  def showFragment(f: => Fragment, tag: String, animated: Boolean = true): Unit

  /*def getCountryController: CountryController*/

  def enableProgress(enabled: Boolean): Unit

  def isShowingProgress():Boolean

  /*def openCountryBox(): Unit*/

  def abortAddAccount(): Unit

}
