/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.waz.zclient.emoji.fragment

import java.util

import com.waz.threading.Threading
import com.waz.zclient.emoji.OnEmojiChangeListener
import com.waz.zclient.emoji.bean.GifSavedItem
import com.waz.zclient.emoji.utils.GifSavedDaoHelper
import com.waz.zclient.utils.SpUtils

import scala.concurrent.Future

class GifFragment(override val onEmojiChangeListener:OnEmojiChangeListener) extends BaseGifFragment(onEmojiChangeListener) {
  override def getData: Future[util.List[GifSavedItem]] = Future {
    GifSavedDaoHelper.getSavedGIFs(SpUtils.getUserId(getContext),true)
  }(Threading.Background)
}
