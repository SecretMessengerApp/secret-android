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
package com.jsy.common.utils

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.PopupWindow
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.adapter.NormalConvListMenuAdp
import com.jsy.common.listener.OnPopMenuItemClick
import com.jsy.common.model.conversation.NormalConvListMenuModel
import com.waz.utils.returning
import com.waz.zclient.R

import scala.collection.JavaConverters.seqAsJavaListConverter

class PopMenuUtil(context: Context, listItems: java.util.List[NormalConvListMenuModel]) {

  val popMenuContentView: View = LayoutInflater.from(context).inflate(R.layout.lay_normal_conv_list_menu, null)
  val popMenuRecyclerView: RecyclerView = popMenuContentView.findViewById(R.id.recyclerMenu)

  popMenuRecyclerView.setLayoutManager(new LinearLayoutManager(context))
  popMenuRecyclerView.setAdapter(new NormalConvListMenuAdp(context, listItems))
//  val popWidth = context.getResources.getDimension(R.dimen.conversation_list_top_menu_pop_item_width).toInt
  val popHeight = (context.getResources.getDimension(R.dimen.conversation_list_top_menu_pop_item_height) * listItems.size).toInt
  popMenuRecyclerView.setLayoutParams(returning(popMenuRecyclerView.getLayoutParams) { lp =>
//    lp.width = popWidth
    lp.height = popHeight
  })

  val popMenu: PopupWindow = new PopupWindow(popMenuContentView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
  popMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT))
  popMenu.setOnDismissListener(new PopupWindow.OnDismissListener {
    override def onDismiss(): Unit = {}
  })
  popMenu.setTouchable(true)
  popMenu.setOutsideTouchable(true)

  var toolBarHeight = 0
  val typeValue = new TypedValue
  if (context.getTheme.resolveAttribute(android.R.attr.actionBarSize, typeValue, true)) {
    toolBarHeight = TypedValue.complexToDimensionPixelSize(typeValue.data, context.getResources.getDisplayMetrics)
  }

  def showPopMenu(parentView: View, onPopMenuItemClick: OnPopMenuItemClick): Unit = {
    showPopMenu(parentView, onPopMenuItemClick, 0, -toolBarHeight / 4)
  }

  def showPopMenu(parentView: View, onPopMenuItemClick: OnPopMenuItemClick, offsetX: Int, offsetY: Int): Unit = {
    if (!popMenu.isShowing) {
      popMenuRecyclerView.getAdapter.asInstanceOf[NormalConvListMenuAdp].setOnPopMenuItemClick(new OnPopMenuItemClick {
        override def onItemClick(view: View, position: Int): Unit = {
          popMenu.dismiss()
          if (onPopMenuItemClick != null) {
            onPopMenuItemClick.onItemClick(view, position)
          }
        }
      })
      popMenu.showAsDropDown(parentView, offsetX, offsetY)
    }
  }
}

object PopMenuUtil {

  def initConversationListFragmentMenu(): java.util.List[NormalConvListMenuModel] = {
    List(
      (R.drawable.ico_top_menu_start_group_chat, R.string.conversation_top_menu_start_group_chat),
      (R.drawable.ico_top_menu_add_friend, R.string.conversation_top_menu_add_friend),
      (R.drawable.ico_top_menu_scan, R.string.conversation_top_menu_scan)
    ).map { it =>
      new NormalConvListMenuModel(it._2, it._1, R.color.transparent)
    }.asJava
  }
}
