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
import android.text.TextUtils
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jsy.common.listener.OnPopMenuItemClick
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.views.CircleImageView
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.{ServerIdConst, returning}
import com.waz.zclient.R
import com.waz.zclient.log.LogUI.{verbose, _}

class ConvMgrTabListMenuAdp(context: Context, parentWidth: Int, column: Int, minItemWidth: Int, itemHeight: Int, listItems: java.util.List[TabListMenuModel]) extends RecyclerView.Adapter[ConvTabListMenuVh2] {

  private var startCount: Int = 0;
  private var itemCount: Int = 0;

  def refreshNotify(startCount: Int, itemCount: Int): Unit = {
    this.startCount = startCount;
    this.itemCount = itemCount;
    notifyDataSetChanged()
  }

  private var onPopMenuItemClick: OnPopMenuItemClick = _

  override def getItemCount: Int = itemCount

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ConvTabListMenuVh2 = {
    new ConvTabListMenuVh2(LayoutInflater.from(context).inflate(R.layout.lay_tab_menu_list_item, parent, false))
  }

  override def onBindViewHolder(holder: ConvTabListMenuVh2, position: Int): Unit = {
    holder.setData(listItems, position + startCount, onPopMenuItemClick, parentWidth / column, minItemWidth, itemHeight)
  }

  def setOnPopMenuItemClick(onPopMenuItemClick: OnPopMenuItemClick): Unit = {
    this.onPopMenuItemClick = onPopMenuItemClick
  }

}

class ConvTabListMenuVh2(itemView: View) extends RecyclerView.ViewHolder(itemView) with DerivedLogTag {

  val rlInnerContent: View = ViewUtils.getView(itemView, R.id.rlInnerContent)
  val ivIcon: CircleImageView = ViewUtils.getView(itemView, R.id.ivIcon)
  val tvTitle: TextView = ViewUtils.getView(itemView, R.id.tvTitle)
  val vNotify: View = ViewUtils.getView(itemView, R.id.vNotification)

  def setData(tabListMenuModels: java.util.List[TabListMenuModel], position: Int, onPopMenuItemClick: OnPopMenuItemClick, itemWidth: Int, minItemWidth: Int, itemHeight: Int): Unit = {
    returning(itemView.getLayoutParams) { lp =>
      if (lp.width != itemWidth || lp.height != itemHeight) {
        lp.width = itemWidth
        lp.height = itemHeight
        verbose(l"update layoutParams itemWidth->${itemWidth} itemHeight->${itemHeight}")
      }
    }

    returning(rlInnerContent.getLayoutParams) { lp =>
      if (lp.width != minItemWidth || lp.height != itemHeight) {
        lp.width = minItemWidth
        lp.height = itemHeight
        verbose(l"update layoutParams minItemWidth->${minItemWidth} itemHeight->${itemHeight}")
      }
    }

    val tabListMenuModel: TabListMenuModel = tabListMenuModels.get(position)
    tvTitle.setText(tabListMenuModel.getText)
    ivIcon.setImageResource(MessageContentUtils.getGroupDefaultAvatar(tabListMenuModel.getConvId))
    if (!TextUtils.isEmpty(tabListMenuModel.getRassetId)) {
      Glide.`with`(itemView.getContext).load(CircleConstant.appendAvatarUrl(tabListMenuModel.getRassetId, itemView.getContext))
        .placeholder( MessageContentUtils.getGroupDefaultAvatar(tabListMenuModel.getConvId)).into(ivIcon)
    }

    if (tabListMenuModel.isRead) {
      vNotify.setVisibility(View.GONE)
    } else {
      vNotify.setVisibility(View.VISIBLE)
    }
    itemView.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        if (onPopMenuItemClick != null) {
          onPopMenuItemClick.onItemClick(rlInnerContent, position)
        }
      }
    })
    itemView.setOnLongClickListener(new View.OnLongClickListener {
      override def onLongClick(v: View): Boolean  = {
        if(onPopMenuItemClick!=null){
          onPopMenuItemClick.onItemLongClick(rlInnerContent,position)
        }
        true
      }
    })
  }
}
