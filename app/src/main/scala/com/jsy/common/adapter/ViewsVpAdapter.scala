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
package com.jsy.common.adapter

import android.view.{View, ViewGroup}
import androidx.viewpager.widget.PagerAdapter

class ViewsVpAdapter(views: java.util.List[View]) extends PagerAdapter {

  override def getCount: Int = {
    if (views==null) 0 else views.size()
  }

  override def isViewFromObject(view: View, o: Any): Boolean = {
    o == view
  }

  override def instantiateItem(container: ViewGroup, position: Int): AnyRef = {
    container.addView(views.get(position))
    views.get(position)
  }

  override def destroyItem(container: ViewGroup, position: Int, `object`: Any): Unit = {
    container.removeView(views.get(position))
  }
}
