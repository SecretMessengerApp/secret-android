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
package com.waz.zclient.common.views

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.utils.events.Signal
import com.waz.zclient.ViewHelper
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{UiStorage, UserSignal}

class UserNameTextView(context: Context, attrs: AttributeSet, defStyleAttr: Int)
  extends TypefaceTextView(context, attrs, defStyleAttr) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  private lazy val userId = Signal[Option[UserId]]()

  private lazy val options = for {
    Some(uId) <- userId
    user <- UserSignal(uId)(inject[UiStorage])
    _ = setTag(uId.str)
  } yield user

  options.onUi { user =>
    if(TextUtils.equals(String.valueOf(getTag), user.id.str)) {
      setText(user.getDisplayName)
    }
  }

  def loadUser(userId: UserId): Unit = {
    this.userId ! Some(userId)
  }
}


