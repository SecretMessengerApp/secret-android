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

import android.content.Context
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.R

class ConvMgrTabPagerIndicatorAdp(context: Context, indicatorPoints: java.util.List[Boolean]) extends RecyclerView.Adapter[ConvMgrTabPagerIndicationVh] {

  override def getItemCount: Int = if (indicatorPoints == null) 0 else indicatorPoints.size()

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConvMgrTabPagerIndicationVh = {
    new ConvMgrTabPagerIndicationVh(LayoutInflater.from(context).inflate(R.layout.lay_tab_menu_pager_indicator_item, parent, false))
  }

  override def onBindViewHolder(holder: ConvMgrTabPagerIndicationVh, position: Int): Unit = {
    holder.ivIndicator.setImageResource {
      if (indicatorPoints.get(position))
        R.drawable.shape_black_circle_diameter_5
      else
        R.drawable.shape_e5e5e5_circle_diameter_5
    }
  }

  def selectPositionNotify(currentPage: Int): Unit = {
    (0 until indicatorPoints.size()).foreach { idx =>
      indicatorPoints.set(idx, currentPage == idx)
    }
    notifyDataSetChanged()
  }
}

class ConvMgrTabPagerIndicationVh(itemView: View) extends RecyclerView.ViewHolder(itemView) with DerivedLogTag {
  val ivIndicator: ImageView = ViewUtils.getView(itemView, R.id.ivIndicator)

}
