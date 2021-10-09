/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
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
package com.waz.zclient.calling.views

import android.content.Context
import android.graphics.Color
import android.util.{AttributeSet, Log}
import android.view.Gravity
import android.widget.{FrameLayout, LinearLayout}
import com.jsy.res.theme.ThemeUtils
import com.waz.utils.returning
import com.waz.zclient.calling.views.CallControlButtonView.ButtonColor
import com.waz.zclient.common.controllers.{ThemeController, ThemedView}
import com.waz.zclient.paintcode.GenericStyleKitView
import com.waz.zclient.paintcode.StyleKitView.StyleKitDrawMethod
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.ContextUtils.{getStyledDrawable, _}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, RefreshingSignal, Signal}
import com.waz.zclient.calling.controllers.CallController
import com.waz.zclient.common.controllers.ThemeController.Theme

import scala.concurrent.Future
import scala.util.Try

class CallControlButtonView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper with ThemedView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val themeController  = inject[ThemeController]

  private val otherColor       = Signal(Option.empty[ButtonColor])

  private val enabledChanged   = EventStream[Boolean]()
  private val enabledSignal    = RefreshingSignal(Future{ isEnabled }(Threading.Ui), enabledChanged)

  private val activatedChanged = EventStream[Boolean]()
  private val activatedSignal  = RefreshingSignal(Future{ isActivated }(Threading.Ui), activatedChanged)

  private val controller = inject[CallController]

  inflate(R.layout.call_button_view)

  setOrientation(LinearLayout.VERTICAL)
  setGravity(Gravity.CENTER)

  private val iconDimension = Try(context.getTheme.obtainStyledAttributes(attrs, R.styleable.CallControlButtonView, 0, 0)).toOption.map { a =>
    returning { a.getDimensionPixelSize(R.styleable.CallControlButtonView_iconDimension, 0) }(_ => a.recycle())
  }.filter(_ != 0)

  private val buttonLabelView  = findById[TypefaceTextView](R.id.text)
  private val buttonBackground = findById[FrameLayout](R.id.icon_background)
  private val iconView         = returning(findById[GenericStyleKitView](R.id.icon)) { icon =>
    iconDimension.foreach { size =>
      icon.getLayoutParams.height = size
      icon.getLayoutParams.width = size
    }
  }

  val theme=if(ThemeUtils.isDarkTheme(context)) Theme.Dark else Theme.Light
  (for {
    otherColor <- otherColor
    isVideo    <- controller.isVideoCall
  } yield (otherColor,isVideo) match{
      case (Some(ButtonColor.Green),_) => getDrawable(R.drawable.selector__icon_button__background__green)
      case (Some(ButtonColor.Red),_)   => getDrawable(R.drawable.selector__icon_button__background__red)
      case (_,isVideo)  =>
        if(isVideo){
          getDrawable(R.drawable.selector__icon_button__background__calling_video)
        }
        else {
          getStyledDrawable(R.attr.callButtonBackground, themeController.getTheme(theme))
            .getOrElse(getDrawable(R.drawable.selector__icon_button__background__calling_dark))
        }
    }
  ).onUi(buttonBackground.setBackground(_))

  (for {
    otherColor <- otherColor
    enabled    <- enabledSignal
    activated  <- activatedSignal
    isVideo    <- controller.isVideoCall
  } yield (otherColor, enabled, activated,isVideo)).onUi {
    case (Some(_), _, _,_) =>
      iconView.setColor(getColor(R.color.white))
    case (None,enabled, activated,isVideo) =>
      val resTheme = themeController.getTheme(theme)
      val iconColor =
        if (!enabled && activated) getStyledColor(R.attr.callIconDisabledActivatedColor, resTheme)
        else if (!enabled) {
          if(isVideo){
            Color.parseColor("#696969")
          }
          else {
            getStyledColor(R.attr.callIconDisabledColor, resTheme)
          }
        }
        else if (activated) {
          if (isVideo) {
            Color.parseColor("#34373a")
          } else {
            getStyledColor(R.attr.wirePrimaryTextColorReverted, resTheme)
          }
        }
        else {
          if(isVideo){
            Color.parseColor("#fefefe")
          }
          else {
            getStyledColor(R.attr.wirePrimaryTextColor, resTheme)
          }
        }
        iconView.setColor(iconColor)
  }

  (for{
    enabled    <- enabledSignal
    isVideo    <- controller.isVideoCall
   }yield (enabled,isVideo)).onUi{
    case (enabled, isVideoCall) =>
      val textColor=if(isVideoCall){
        Color.WHITE
      }
      else {
        val resTheme = themeController.getTheme(theme)
        if (!enabled) getStyledColor(R.attr.callTextDisabledColor, resTheme)
        else getStyledColor(R.attr.wirePrimaryTextColor, resTheme)
      }
      buttonLabelView.setTextColor(textColor)
  }

  override def setEnabled(enabled: Boolean): Unit = {
    super.setEnabled(enabled)
    this.dispatchSetEnabled(enabled)
    enabledChanged ! enabled
  }

  override def setActivated(activated: Boolean): Unit = {
    super.setActivated(activated)
    activatedChanged ! activated
  }

  def setText(stringId: Int): Unit = buttonLabelView.setText(getResources.getText(stringId))

  def set(icon: StyleKitDrawMethod, labelStringId: Int, onClick: () => Unit, forceColor: Option[ButtonColor] = None): Unit = {
    iconView.setOnDraw(icon)
    setText(labelStringId)
    otherColor ! forceColor
    this.onClick { onClick() }
  }

}

object CallControlButtonView {

  object ButtonColor extends Enumeration {
    val Green, Red = Value
  }
  type ButtonColor = ButtonColor.Value

}
