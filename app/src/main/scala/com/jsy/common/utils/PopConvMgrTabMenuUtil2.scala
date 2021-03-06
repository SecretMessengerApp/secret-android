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

import android.animation.{Animator, ObjectAnimator}
import android.content.Context
import android.graphics.{Color, Rect}
import android.graphics.drawable.ColorDrawable
import android.view.View.OnClickListener
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.widget.{PopupWindow, TextView}
import androidx.recyclerview.widget.{GridLayoutManager, LinearLayoutManager, OrientationHelper, RecyclerView}
import com.jsy.common.adapter.{ConvMgrTabListMenuAdp, ConvMgrTabPagerIndicatorAdp}
import com.jsy.common.listener.{OnPopMenuDismissListener, OnPopMenuItemClick}
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.common.views.PagerScrollViewGroup
import com.jsy.common.views.OnPageScrollListener
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.returning
import com.waz.zclient.R
import com.waz.zclient.log.LogUI._
@Deprecated
class PopConvMgrTabMenuUtil2(context: Context, tabListMenuModels: java.util.List[TabListMenuModel], initPopMenu: Boolean) extends DerivedLogTag {
  self =>

  val column = 4
  val row=2
  val dp10 = context.getResources.getDimensionPixelSize(R.dimen.dp10)
  val dp5 = context.getResources.getDimensionPixelSize(R.dimen.dp5)
  val recyclerWidth = context.getResources.getDisplayMetrics.widthPixels - 2 * dp10 - 2 * dp5 // ml mr pl pr
  val minItemWidth = recyclerWidth / column
  val itemHeight = context.getResources.getDimensionPixelSize(R.dimen.dp90)
  var itemInterval=context.getResources.getDimensionPixelSize(R.dimen.padding_15)
  var pagerHeight=if(tabListMenuModels==null || tabListMenuModels.size()<=column) itemHeight+itemInterval else {
    (itemHeight+itemInterval)*row
  }

  private val popMenuContentView: View = LayoutInflater.from(context).inflate(R.layout.lay_tab_list_menu2, null)
  private val pagerScrollViewGroup: PagerScrollViewGroup = popMenuContentView.findViewById(R.id.pagerScrollViewGroup)
  returning(pagerScrollViewGroup.getLayoutParams) { lp =>
    lp.height = pagerHeight + pagerScrollViewGroup.getPaddingTop + pagerScrollViewGroup.getPaddingBottom
  }
  private val convTabListMenuAdps: java.util.List[ConvMgrTabListMenuAdp] = new util.ArrayList[ConvMgrTabListMenuAdp]()

  private val indicatorRecyclerView: RecyclerView = popMenuContentView.findViewById(R.id.indicatorRecyclerView)
  indicatorRecyclerView.setLayoutManager(new LinearLayoutManager(context, OrientationHelper.HORIZONTAL, false))
  private val indicatorPoints: java.util.List[Boolean] = new util.ArrayList[Boolean]()
  private val indicatorAdp: ConvMgrTabPagerIndicatorAdp = new ConvMgrTabPagerIndicatorAdp(context, indicatorPoints)
  indicatorRecyclerView.setAdapter(indicatorAdp)

  //private val gtvActionGlphyToEdit: TextView = popMenuContentView.findViewById(R.id.gtvActionGlphyToEdit)
  pagerScrollViewGroup.setOnPageScrollListener(new OnPageScrollListener {
    override def onPageScrolled(scrolledX: Int, width: Int, oldIndex: Int, toIndex: Int): Unit = {
      verbose(l"onPageScrolled oldIndex->${oldIndex} toIndex->${toIndex} scrolledX->${scrolledX}")
    }

    override def onPageSelected(toIndex: Int): Unit = {
      verbose(l"onPageSelected position onPageSelected ->${toIndex}")
      indicatorAdp.selectPositionNotify(toIndex)
    }
  })
  indicatorAdp.selectPositionNotify(pagerScrollViewGroup.getCurrentIndex)

  private val vAnim: View = popMenuContentView.findViewById(R.id.vAnim)
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

//  gtvActionGlphyToEdit.setOnClickListener(new OnClickListener {
//    override def onClick(v: View): Unit = {
//      if (onPopMenuItemClick != null) {
//        onPopMenuItemClick.onItemClick(v, -1)
//      }
//    }
//  })

  private def updateAdapterListeners(): Unit = {

    val currentPage: Int = pagerScrollViewGroup.getCurrentIndex
    val pages = if (tabListMenuModels == null) 0 else if (tabListMenuModels.size() % (column*row) == 0) tabListMenuModels.size() / (column*row) else tabListMenuModels.size() / (column*row) + 1
    if (pages == indicatorPoints.size()) {
      (0 until indicatorPoints.size()).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        val itemCount = Math.min(tabListMenuModels.size() - column*row * idx, column*row)
        convTabListMenuAdps.get(idx).refreshNotify(column*row * idx, itemCount)
      }
      indicatorAdp.notifyDataSetChanged()
    } else if (pages > indicatorPoints.size()) { // ++
      (0 until indicatorPoints.size()).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        val itemCount = Math.min(tabListMenuModels.size() - column*row * idx, column*row)
        convTabListMenuAdps.get(idx).refreshNotify(column*row * idx, itemCount)
      }
      (indicatorPoints.size() until pages).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.add(idx, true)
        } else {
          indicatorPoints.add(idx, false)
        }
        val itemCount = Math.min(tabListMenuModels.size() - column*row * idx, column*row)
        val convMgrTabListMenuAdp = new ConvMgrTabListMenuAdp(context, recyclerWidth.toInt, column, minItemWidth.toInt, itemHeight.toInt, tabListMenuModels)
        convMgrTabListMenuAdp.refreshNotify(column*row * idx, itemCount)
        val recyclerView = new RecyclerView(context)
        recyclerView.setLayoutManager(new GridLayoutManager(context, column) {
          override def generateDefaultLayoutParams = new RecyclerView.LayoutParams(minItemWidth.toInt,  pagerHeight)
        })
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration {
          override def getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State): Unit = {
            super.getItemOffsets(outRect, view, parent, state)
              outRect.top=itemInterval
          }
        })
        recyclerView.setAdapter(convMgrTabListMenuAdp)
        convMgrTabListMenuAdp.setOnPopMenuItemClick(onPopMenuItemClick)
        convTabListMenuAdps.add(convMgrTabListMenuAdp)
        pagerScrollViewGroup.addView(recyclerView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
      }
      indicatorAdp.notifyDataSetChanged()
    } else { // --  ??? pages < indicatorPoints.size() ???
      (0 until pages).foreach { idx =>
        if (idx == currentPage) {
          indicatorPoints.set(idx, true)
        } else {
          indicatorPoints.set(idx, false)
        }
        val itemCount = Math.min(tabListMenuModels.size() - column*row * idx, column*row)
        convTabListMenuAdps.get(idx).refreshNotify(column*row * idx, itemCount)
      }
      (pages until indicatorPoints.size()).reverse.foreach { idx =>
        indicatorPoints.remove(idx)
        pagerScrollViewGroup.removeViewAt(idx)
        convTabListMenuAdps.remove(idx)
      }
      indicatorAdp.notifyDataSetChanged()
      if (pages > 0 && pagerScrollViewGroup.getCurrentIndex >= pages) {
        val toIndex = pages - 1
        pagerScrollViewGroup.scrollToIndex(toIndex)
        indicatorAdp.selectPositionNotify(toIndex)
      }
    }
    val newPagerHeight=if(tabListMenuModels==null || tabListMenuModels.size()<=column) itemHeight+itemInterval else (itemHeight+itemInterval)*row
    if(newPagerHeight!=pagerHeight){
      pagerHeight=newPagerHeight
      pagerScrollViewGroup.getLayoutParams.height=pagerHeight+pagerScrollViewGroup.getPaddingTop+pagerScrollViewGroup.getPaddingBottom
      pagerScrollViewGroup.requestLayout()
    }

    indicatorRecyclerView.setVisibility(if (tabListMenuModels.size() <= column*row) View.INVISIBLE else View.VISIBLE)

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
    (0 until tabListMenuModels.size()).foreach { idx =>
      tabListMenuModels.get(idx).setEditing(true)
    }
    updateAdapterListeners()
    isEditing = true

  }

  def setEdited(): Unit = {
    //gtvActionGlphyToEdit.setText(R.string.empty_string)
    //gtvActionGlphyToEdit.setCompoundDrawables(Utils.createDrawableBounds(context, R.drawable.ico_group_tab_item_eidt), null, null, null)
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


