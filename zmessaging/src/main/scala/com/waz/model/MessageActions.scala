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

object MessageActions {
  val Action_UnKnown = 0
  val Action_Like = 1
  val Action_UnLike = 2
  val Action_Forbid = 3
  val Action_UnForbid = 4
  val Action_MsgRead = 5
  val Action_MsgUnRead = 6
  val Action_MsgClose = 7
}

object MessageTypes{
  val Type_UnKnown = 0
  val Type_Like = 1
  val Type_Forbid = 2
  val Type_MsgRead = 3
}

object NatureTypes{
  val Type_Normal = 0
  val Type_ServerNotifi = 3
}
