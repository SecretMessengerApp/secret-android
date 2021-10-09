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

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View.OnClickListener
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.{LinearLayoutManager, OrientationHelper, RecyclerView}
import com.jsy.common.adapter.ConvMgrTabListMenuAdp
import com.jsy.common.listener.{OnPopMenuDismissListener, OnPopMenuItemClick}
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.res.utils.ColorUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.GlyphTextView

@Deprecated
class PopConvMgrTabMenuUtil(context: Context, tabListMenuModels: java.util.List[TabListMenuModel], initPopMenu: Boolean) extends DerivedLogTag {
  self =>

  val column = 4
  val dp10 = context.getResources.getDimension(R.dimen.dp10)
  val dp5 = context.getResources.getDimension(R.dimen.dp5)
  val recyclerWidth = context.getResources.getDisplayMetrics.widthPixels - 2 * dp10 - 2 * dp5
  val minItemWidth = recyclerWidth / column
  val itemHeight = context.getResources.getDimension(R.dimen.dp80)

  private val popMenuContentView: View = LayoutInflater.from(context).inflate(R.layout.lay_tab_list_menu, null)
  private val popMenuRecyclerView: RecyclerView = popMenuContentView.findViewById(R.id.recyclerMenu)
  private val gtvActionGlphyToEdit: GlyphTextView = popMenuContentView.findViewById(R.id.gtvActionGlphyToEdit)
  private val convTabListMenuAdp: ConvMgrTabListMenuAdp = new ConvMgrTabListMenuAdp(context, recyclerWidth.toInt, column, minItemWidth.toInt, itemHeight.toInt, tabListMenuModels)
  private val vAnim: View = popMenuContentView.findViewById(R.id.vAnim)
  vAnim.setBackgroundColor(Color.parseColor("#f6dddddd"))
  val animAlpha = ObjectAnimator.ofFloat(vAnim, "alpha", 0f, 1.0f).setDuration(250)
  private var isEditing = false
  private var onPopMenuItemClick: OnPopMenuItemClick = _
  private var onPopMenuDismissListener: OnPopMenuDismissListener[Object] = _

  popMenuRecyclerView.setLayoutParams(returning(popMenuRecyclerView.getLayoutParams) { lp =>
    lp.width = -2
    lp.height = itemHeight.toInt + popMenuRecyclerView.getPaddingTop + popMenuRecyclerView.getPaddingBottom
  })
  popMenuRecyclerView.setLayoutManager(new LinearLayoutManager(context, OrientationHelper.HORIZONTAL, false) {
    override def generateDefaultLayoutParams = new RecyclerView.LayoutParams(minItemWidth.toInt, itemHeight.toInt)
  })
  popMenuRecyclerView.setAdapter(convTabListMenuAdp)

  private var popMenu: PopupWindow = _

  if (initPopMenu) {
    popMenu = new PopupWindow(popMenuContentView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true)
    popMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT))
    popMenu.setOnDismissListener(new PopupWindow.OnDismissListener {
      override def onDismiss(): Unit = {
        if (onPopMenuDismissListener != null) {
          onPopMenuDismissListener.onDismissed(self)
        }
      }
    })
    popMenu.setTouchable(true)
    popMenu.setOutsideTouchable(true)
  }

  vAnim.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if (onPopMenuItemClick != null) {
        onPopMenuItemClick.onItemClick(v, -1)
      }
    }
  })

  gtvActionGlphyToEdit.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if (onPopMenuItemClick != null) {
        onPopMenuItemClick.onItemClick(v, -1)
      }
    }
  })

  convTabListMenuAdp.setOnPopMenuItemClick(new OnPopMenuItemClick {
    override def onItemClick(view: View, position: Int): Unit = {
      if (onPopMenuItemClick != null) {
        onPopMenuItemClick.onItemClick(view, position)
      }
    }
  })

  private def startAnimAlpha(): Unit = {
    vAnim.clearAnimation()
    animAlpha.start()
  }

  def addToParentView(parentView: ViewGroup, layoutParams: ViewGroup.LayoutParams, onPopMenuItemClick: OnPopMenuItemClick, onPopMenuDismissListener: OnPopMenuDismissListener[Object]): Unit = {
    if (popMenuContentView.getParent != null) {
      return
    }
    this.onPopMenuItemClick = onPopMenuItemClick
    this.onPopMenuDismissListener = onPopMenuDismissListener
    setEdited()
    parentView.addView(popMenuContentView, layoutParams)
    startAnimAlpha()
  }

  def showPopMenu(parentView: View, onPopMenuItemClick: OnPopMenuItemClick, onPopMenuDismissListener: OnPopMenuDismissListener[Object]): Unit = {
    showPopMenu(parentView, onPopMenuItemClick, onPopMenuDismissListener, 0, 0)
  }

  def showPopMenu(parentView: View, onPopMenuItemClick: OnPopMenuItemClick, onPopMenuDismissListener: OnPopMenuDismissListener[Object], offsetX: Int, offsetY: Int): Unit = {
    if (popMenu == null) {
      return
    }
    this.onPopMenuItemClick = onPopMenuItemClick
    this.onPopMenuDismissListener = onPopMenuDismissListener
    setEdited()
    if (!popMenu.isShowing) {
      verbose(l"showPopMenu offsetX->${offsetX} offsetY->${offsetY}")
      popMenu.showAtLocation(parentView, Gravity.CENTER_HORIZONTAL | Gravity.TOP, offsetX, offsetY)
    }
    startAnimAlpha()
  }

  def isShowing(): Boolean = if (popMenu != null) {
    popMenu.isShowing
  } else if (popMenuContentView != null) {
    popMenuContentView.getParent != null
  } else false

  def dismiss(): Unit = {
    if (popMenu != null) {
      popMenu.dismiss()
    } else if (popMenuContentView.getParent != null) {
      popMenuContentView.getParent.asInstanceOf[ViewGroup].removeView(popMenuContentView)
      if (onPopMenuDismissListener != null) {
        onPopMenuDismissListener.onDismissed(self)
      }
    }
  }

  def setEditing(): Unit = {
    gtvActionGlphyToEdit.setText(R.string.complete)
    gtvActionGlphyToEdit.setTextColor(ColorUtils.getColor(context,R.color.SecretBlue))
    (0 until tabListMenuModels.size()).foreach { idx =>
      tabListMenuModels.get(idx).setEditing(true)
    }
    if (convTabListMenuAdp != null) {
      convTabListMenuAdp.refreshNotify(0, tabListMenuModels.size())
    }
    isEditing = true

  }

  def setEdited(): Unit = {
    gtvActionGlphyToEdit.setText(R.string.glyph__more)
    gtvActionGlphyToEdit.setTextColor(ContextCompat.getColor(context, R.color.graphite))
    (0 until tabListMenuModels.size()).foreach { idx =>
      tabListMenuModels.get(idx).setEditing(false)
    }
    if (convTabListMenuAdp != null) {
      convTabListMenuAdp.refreshNotify(0, tabListMenuModels.size())
    }
    isEditing = false

  }

  def isEdting() = isEditing

  def remove(position: Int): Unit = {
    tabListMenuModels.remove(position)
    if (convTabListMenuAdp != null) {
      convTabListMenuAdp.refreshNotify(0, tabListMenuModels.size())
    }
  }

  def notifyDataSetChanged(): Unit = {
    if (convTabListMenuAdp != null) {
      convTabListMenuAdp.refreshNotify(0, tabListMenuModels.size())
    }

  }
}


