/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.participants

import android.app.Activity
import android.content.Context
import android.graphics.{Canvas, RectF}
import android.os.Bundle
import android.view.{View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gyf.immersionbar.ImmersionBar
import com.jsy.common.utils.StausBarUtils
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.paintcode.WireStyleKit.ResizingBehavior
import com.waz.zclient.ui.animation.interpolators.penner.{Expo, Quart}
import com.waz.zclient.ui.text.GlyphTextView
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.{ColorUtils, ViewUtils}
import com.waz.zclient.utils.ContextUtils._
import com.jsy.res.utils.ViewUtils.getView
import com.waz.zclient.utils._
import com.waz.zclient.{DialogHelper, R}

case class OptionsMenu(context: Context, controller: OptionsMenuController) extends BottomSheetDialog(context, R.style.message__bottom_sheet__base) with DialogHelper {
  private implicit val ctx: Context = context

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val view = getLayoutInflater.inflate(R.layout.message__bottom__menu, null).asInstanceOf[LinearLayout]
    setContentView(view)
    StausBarUtils.INSTANCE.setNagivationBarColor(getWindow,ColorUtils.getAttrColor(context,R.attr.DialogBackgroundColor))

    val container = view.findViewById[LinearLayout](R.id.container)
    val title = view.findViewById[TextView](R.id.title)

    def params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewUtils.toPx(getContext, 48))

    controller.title.onUi {
      case Some(text) =>
        title.setVisible(true)
        title.setText(text)
      case _ =>
        title.setVisible(false)
    }

    Signal(controller.optionItems, controller.selectedItems).onUi { case (items, selected) =>

      container.removeAllViews()

      items.foreach { item =>
        container.addView(returning(getLayoutInflater.inflate(R.layout.message__bottom__menu__row, container, false)) { itemView =>

          returning(getView[GlyphTextView](itemView, R.id.icon)) { v =>
            item.iconId.fold(v.setVisibility(View.GONE)) { g =>
              v.setVisibility(View.VISIBLE)
              /*
              val drawable = new WireDrawable {
                override def draw(canvas: Canvas): Unit ={
                  OptionsMenu.drawForId(g)(canvas, getDrawingRect, ResizingBehavior.AspectFit, this.paint.getColor)
                }
              }
              drawable.setPadding(new Rect(v.getPaddingLeft, v.getPaddingTop, v.getPaddingRight, v.getPaddingBottom))
              val color = item.colorId.map(getColor).getOrElse(getColor(R.color.graphite))
              drawable.setColor(color)
              v.setBackground(drawable)
              */
              //val color = item.colorId.map(getColor).getOrElse(getColor(R.color.graphite))
              //v.setTextColor(color)
              item.iconId.foreach(v.setText)
            }
          }
          returning(getView[TextView](itemView, R.id.text)) { v =>
            v.setText(item.titleId)
            //item.colorId.map(getColor).foreach(v.setTextColor(_))
          }
          itemView.onClick {
            controller.onMenuItemClicked ! item
            dismiss()
          }
          item.iconId.foreach(itemView.setId)

          returning(getView[View](itemView, R.id.tick)) { v =>
            v.setVisible(selected.contains(item))
          }
        }, params)
      }
    }
  }
}

object OptionsMenu {

  lazy val quartOut = new Quart.EaseOut
  lazy val expoOut  = new Expo.EaseOut
  lazy val expoIn   = new Expo.EaseIn

  trait AnimState
  case object Open    extends AnimState
  case object Opening extends AnimState
  case object Closing extends AnimState
  case object Closed  extends AnimState

}
