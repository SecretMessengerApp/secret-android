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

import java.util

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View.OnClickListener
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{PopupWindow, TextView}
import androidx.recyclerview.widget.{LinearLayoutManager, OrientationHelper, RecyclerView}
import com.jsy.common.adapter.{ConvMgrTabListMenuAdp, ConvMgrTabPagerIndicatorAdp}
import com.jsy.common.listener.{OnPopMenuDismissListener, OnPopMenuItemClick}
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.common.views.{OnPageScrollListener, PagerScrollViewGroup, PagerScrollViewGroup2}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.log.LogUI._

class PopConvMgrTabMenuUtil3(context: Context, tabListMenuModels: java.util.List[TabListMenuModel], initPopMenu: Boolean) extends DerivedLogTag {
  self =>

  val column = 4
  val dp10 = context.getResources.getDimension(R.dimen.dp10)
  val dp5 = context.getResources.getDimension(R.dimen.dp5)
  val recyclerWidth = context.getResources.getDisplayMetrics.widthPixels - 2 * dp10 - 2 * dp5 // ml mr pl pr
  val minItemWidth = recyclerWidth / column
  val itemHeight = context.getResources.getDimension(R.dimen.dp80)

  private val popMenuContentView: View = LayoutInflater.from(context).inflate(R.layout.lay_tab_list_menu3, null)

  private val indicatorRecyclerView: RecyclerView = popMenuContentView.findViewById(R.id.indicatorRecyclerView)
  indicatorRecyclerView.setLayoutManager(new LinearLayoutManager(context, OrientationHelper.HORIZONTAL, false))
  private val indicatorPoints: java.util.List[Boolean] = new util.ArrayList[Boolean]()
  private val indicatorAdp: ConvMgrTabPagerIndicatorAdp = new ConvMgrTabPagerIndicatorAdp(context, indicatorPoints)
  indicatorRecyclerView.setAdapter(indicatorAdp)


  private val pagerScrollViewGroup: PagerScrollViewGroup2 = popMenuContentView.findViewById(R.id.pagerScrollViewGroup)
  pagerScrollViewGroup.setPagerWidth(recyclerWidth.toInt)
  returning(pagerScrollViewGroup.getLayoutParams) { lp =>
    lp.height = itemHeight.toInt + pagerScrollViewGroup.getPaddingTop + pagerScrollViewGroup.getPaddingBottom
  }
  pagerScrollViewGroup.setOnPageScrollListener(new OnPageScrollListener {
    override def onPageScrolled(scrolledX: Int, width: Int, oldIndex: Int, toIndex: Int): Unit = {
    }

    override def onPageSelected(toIndex: Int): Unit = {
      verbose(l"updateAdapterListeners pagerScrollViewGroup-->${pagerScrollViewGroup.getWidth}  pagerScrollViewGroup->${pagerScrollViewGroup.getWidth}")
      if (indicatorAdp != null) {
        indicatorAdp.selectPositionNotify(toIndex)
      }
    }
  })
  indicatorAdp.selectPositionNotify(pagerScrollViewGroup.getCurrentIndex)

  private val recyclerMenu: RecyclerView = popMenuContentView.findViewById(R.id.recyclerMenu)
  recyclerMenu.setLayoutParams(returning(recyclerMenu.getLayoutParams) { lp =>
    lp.width = -2
    lp.height = itemHeight.toInt + recyclerMenu.getPaddingTop + recyclerMenu.getPaddingBottom
  })
  recyclerMenu.setLayoutManager(new LinearLayoutManager(context, OrientationHelper.HORIZONTAL, false) {
    override def generateDefaultLayoutParams = new RecyclerView.LayoutParams(minItemWidth.toInt, itemHeight.toInt)
  })
  private val convTabListMenuAdp: ConvMgrTabListMenuAdp = new ConvMgrTabListMenuAdp(context, recyclerWidth.toInt, column, minItemWidth.toInt, itemHeight.toInt, tabListMenuModels)
  recyclerMenu.setAdapter(convTabListMenuAdp)
  convTabListMenuAdp.setOnPopMenuItemClick(new OnPopMenuItemClick {
    override def onItemClick(view: View, position: Int): Unit = {
      if (onPopMenuItemClick != null) {
        onPopMenuItemClick.onItemClick(view, position)
      }
    }
  })

  private val gtvActionGlphyToEdit: TextView = popMenuContentView.findViewById(R.id.gtvActionGlphyToEdit)

  private val vAnim: View = popMenuContentView.findViewById(R.id.vAnim)
  vAnim.setBackgroundColor(Color.parseColor("#f6dddddd"))
  val animAlphaToShow = ObjectAnimator.ofFloat(vAnim, "alpha", 0f, 1.0f).setDuration(250)
  //val animAlphaToDismiss = ObjectAnimator.ofFloat(vAnim, "alpha", 1f, 0f).setDuration(250)

  private var isEditing = false
  private var onPopMenuItemClick: OnPopMenuItemClick = _
  private var onPopMenuDismissListener: OnPopMenuDismissListener[Object] = _

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

  private def updateAdapterListeners(): Unit = {

    val currentPage: Int = pagerScrollViewGroup.getCurrentIndex
    val pages = if (tabListMenuModels == null) 0 else if (tabListMenuModels.size() % column == 0) tabListMenuModels.size() / column else tabListMenuModels.size() / column + 1
    // pagerScrollViewGroup.invalidate()
    if (pages == indicatorPoints.size()) {
      verbose(l"updateAdapterListeners ... pagerScrollViewGroup-->${pagerScrollViewGroup.getWidth}  pagerScrollViewGroup->${pagerScrollViewGroup.getWidth}")
      (0 until indicatorPoints.size()).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        //        val itemCount = Math.min(tabListMenuModels.size() - column * idx, column)
        //        convTabListMenuAdps.get(idx).refreshNotify(column * idx, itemCount)
      }
      indicatorAdp.notifyDataSetChanged()
    } else if (pages > indicatorPoints.size()) { // ++
      recyclerMenu.setLayoutParams(returning(recyclerMenu.getLayoutParams) { lp =>
        lp.width = (recyclerWidth * pages).toInt
        lp.height = itemHeight.toInt + recyclerMenu.getPaddingTop + recyclerMenu.getPaddingBottom
      })
      pagerScrollViewGroup.setPagerCount(pages)
      verbose(l"updateAdapterListeners ++ pagerScrollViewGroup-->${pagerScrollViewGroup.getWidth}  pagerScrollViewGroup->${pagerScrollViewGroup.getWidth}")

      (0 until indicatorPoints.size()).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        //        val itemCount = Math.min(tabListMenuModels.size() - column * idx, column)
        //        convTabListMenuAdps.get(idx).refreshNotify(column * idx, itemCount)
      }
      (indicatorPoints.size() until pages).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.add(idx, true)
        } else {
          indicatorPoints.add(idx, false)
        }
      }
      indicatorAdp.notifyDataSetChanged()
    } else { // --  【 pages < indicatorPoints.size() 】
      recyclerMenu.setLayoutParams(returning(recyclerMenu.getLayoutParams) { lp =>
        lp.width = (recyclerWidth * pages).toInt
        lp.height = itemHeight.toInt + recyclerMenu.getPaddingTop + recyclerMenu.getPaddingBottom
      })
      pagerScrollViewGroup.setPagerCount(pages)
      verbose(l"updateAdapterListeners -- pagerScrollViewGroup-->${pagerScrollViewGroup.getWidth}  pagerScrollViewGroup->${pagerScrollViewGroup.getWidth}")
      (0 until pages).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        //        val itemCount = Math.min(tabListMenuModels.size() - column * idx, column)
        //        convTabListMenuAdps.get(idx).refreshNotify(column * idx, itemCount)
      }
      (pages until indicatorPoints.size()).reverse.foreach { idx =>
        indicatorPoints.remove(idx)
        //        pagerScrollViewGroup.removeViewAt(idx)
        //        convTabListMenuAdps.remove(idx)
      }
      indicatorAdp.notifyDataSetChanged()
      if (pages > 0 && pagerScrollViewGroup.getCurrentIndex >= pages) {
        val toIndex = pages - 1
        pagerScrollViewGroup.scrollToIndex(toIndex)
        indicatorAdp.selectPositionNotify(toIndex)
      }
    }
    convTabListMenuAdp.refreshNotify(0, tabListMenuModels.size())
    indicatorRecyclerView.setVisibility(if (tabListMenuModels.size() <= column) View.INVISIBLE else View.VISIBLE)

  }

  private def startAnimAlpha(): Unit = {
    vAnim.clearAnimation()
    animAlphaToShow.start()
  }

  def addToParentView(parentView: ViewGroup, layoutParams: ViewGroup.LayoutParams, onPopMenuItemClick: OnPopMenuItemClick, onPopMenuDismissListener: OnPopMenuDismissListener[Object]): Unit = {
    if (popMenuContentView.getParent != null) {
      return
    }
    this.onPopMenuItemClick = onPopMenuItemClick
    this.onPopMenuDismissListener = onPopMenuDismissListener
    //    verbose(l"addToParentView ids==============")
    //    (0 until tabListMenuModels.size()).foreach{idx=>
    //      verbose(l"addToParentView ids[${idx}]->${tabListMenuModels.get(idx).getConvId}")
    //    }

    setEdited()
    if (tabListMenuModels.size() > 0 && pagerScrollViewGroup.getCurrentIndex != 0) {
      pagerScrollViewGroup.scrollToIndex(0)
      indicatorAdp.selectPositionNotify(0)
    }
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
    gtvActionGlphyToEdit.setCompoundDrawables(null, null, null, null)
    (0 until tabListMenuModels.size()).foreach { idx =>
      tabListMenuModels.get(idx).setEditing(true)
    }
    updateAdapterListeners()
    isEditing = true

  }

  def setEdited(): Unit = {
    gtvActionGlphyToEdit.setText(R.string.empty_string)
    gtvActionGlphyToEdit.setCompoundDrawables(Utils.createDrawableBounds(context, R.drawable.ico_group_tab_item_eidt), null, null, null)
    (0 until tabListMenuModels.size()).foreach { idx =>
      tabListMenuModels.get(idx).setEditing(false)
    }
    updateAdapterListeners()
    isEditing = false

  }

  def isEdting() = isEditing

  def remove(position: Int): Unit = {
    tabListMenuModels.remove(position)
    updateAdapterListeners()
  }

  def notifyDataSetChanged(): Unit = {
    updateAdapterListeners()
  }
}


