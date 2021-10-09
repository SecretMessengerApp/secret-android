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
package com.waz.zclient.preferences.pages

import android.content.{Context, Intent}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.{CompoundButton, LinearLayout, RelativeLayout}
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.Signal
import com.waz.zclient.preferences.views.SwitchPreference
import com.waz.zclient.ui.text.GlyphTextView
import com.jsy.res.theme.ThemeUtils
import com.waz.zclient.utils.BackStackKey
import com.waz.zclient.{Constants, R, ViewHelper}

class DarkModeView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_dark_mode_layout)

  val preferences_follow_system = findById[SwitchPreference](R.id.preferences_follow_system)
  val ll_manual = findById[LinearLayout](R.id.ll_manual)
  val rl_normal_mode = findById[RelativeLayout](R.id.rl_normal_mode)
  val normal_checkbox = findById[GlyphTextView](R.id.normal_checkbox)
  val rl_dark_mode = findById[RelativeLayout](R.id.rl_dark_mode)
  val dark_checkbox = findById[GlyphTextView](R.id.dark_checkbox)

  preferences_follow_system.setChangeListener(new CompoundButton.OnCheckedChangeListener{
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit ={
      LogUtils.d("onCheckedChanged:"+isChecked)
      followSystem ! isChecked
    }
  })
  var followSystem=Signal(ThemeUtils.isFollowSystem(context))
  followSystem.onUi{f=>
    if(f){
      ll_manual.setVisibility(View.GONE)
    }
    else{
      ll_manual.setVisibility(View.VISIBLE)
    }
  }
  followSystem.map(f => preferences_follow_system.setChecked(f,disableListener = true))

  preferences_follow_system.setChecked(ThemeUtils.isFollowSystem(context))

  var darkMode=Signal(ThemeUtils.isDarkMode(context))
  darkMode.onUi{
    setDarkMode
  }

  rl_normal_mode.setOnClickListener(new View.OnClickListener {
    override def onClick(v: View): Unit ={
      darkMode ! false
    }
  })
  rl_dark_mode.setOnClickListener(new View.OnClickListener {
    override def onClick(v: View): Unit = {
      darkMode ! true
    }
  })

  def setDarkMode(dark:Boolean): Unit ={
    if(!dark){
      normal_checkbox.setVisibility(View.VISIBLE)
      dark_checkbox.setVisibility(View.GONE)
    }
    else{
      normal_checkbox.setVisibility(View.GONE)
      dark_checkbox.setVisibility(View.VISIBLE)
    }
  }

  def applyChange(): Unit ={
    val before = ThemeUtils.isDarkTheme(context)

    val isFollowSystem = followSystem.currentValue.head
    if(isFollowSystem!=ThemeUtils.isFollowSystem(context)){
      ThemeUtils.setFollowSystem(context,isFollowSystem)
    }
    val isDarkMode=darkMode.currentValue.head
    if(isDarkMode!=ThemeUtils.isDarkMode(context)){
      ThemeUtils.setDarkMode(context,isDarkMode)
    }

    val after = ThemeUtils.isDarkTheme(context)

    if(before!=after){
      context.sendBroadcast(new Intent(Constants.ACTION_CHANGE_THEME))
    }
  }

}

object DarkModeView {

}

case class DarkModeBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.dark_mode

  override def layoutId = R.layout.preferences_dark_mode

  var darkModeView:Option[DarkModeView]=_

  override def onViewAttached(v: View) = {
    LogUtils.d("onViewAttached")
    darkModeView=Option(v.asInstanceOf[DarkModeView])
  }

  override def onViewDetached() = {
    LogUtils.d("onViewDetached")
    darkModeView.foreach { v =>
       v.applyChange()
    }
  }
}
