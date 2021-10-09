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
package com.waz.zclient.preferences.views

import android.content.Context
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.{AttributeSet, TypedValue}
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.{CompoundButton, RelativeLayout, Switch}
import androidx.core.content.ContextCompat
import com.waz.content.Preferences.PrefKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.{R, ViewHelper}
import com.waz.zclient.utils.RichView
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.utils.TextViewUtils

trait Switchable {
  val onCheckedChange: EventStream[Boolean]

  def setChecked(checked: Boolean): Unit

  def setChecked(checked: Boolean, disableListener: Boolean = false): Unit
}


class SwitchPreference(context: Context, attrs: AttributeSet, style: Int)
  extends RelativeLayout(context, attrs, style)
    with Switchable
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preference_switch)

  import TextButton._

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val vgTitle = Option(findById[View](R.id.rlCenterTitle))
  val switch = findById[Switch](R.id.preference_switch)
  private val ttvTitle = Option(findById[TypefaceTextView](R.id.ttvTitle))
  private val rlDesc = Option(findById[View](R.id.rlDesc))
  private val ttvDesc = Option(findById[TypefaceTextView](R.id.ttvDesc))
  private val ttvSubTitle = Option(findById[TypefaceTextView](R.id.ttvSubTitle))
  private val vCenterLine = Option(findById[View](R.id.vCenterLine))
  private val vBottomLine = Option(findById[View](R.id.vBottomLine))

  private val attributesArray: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.SwitchPreference, 0, 0)
  private val keyAttr = Option(attributesArray.getString(R.styleable.SwitchPreference_key))
  private val titleBackground = Option(attributesArray.getResourceId(R.styleable.SwitchPreference_titleBackground, DEF_RES_ID)).filter(_ != DEF_RES_ID)

  private val showCenterLine = Option(attributesArray.getBoolean(R.styleable.SwitchPreference_showCenterLine, false))
  private val centerLineStyle = Option(attributesArray.getInteger(R.styleable.SwitchPreference_centerLineStyle, LINE_STYLE_alignParentStart))
  private val showBottomLine = Option(attributesArray.getBoolean(R.styleable.SwitchPreference_showBottomLine, false))
  private val bottomLineStyle = Option(attributesArray.getInteger(R.styleable.SwitchPreference_bottomLineStyle, LINE_STYLE_alignParentStart))

  private val titleAttr = Option(attributesArray.getString(R.styleable.SwitchPreference_title))
  private val titleSize = Option(attributesArray.getDimension(R.styleable.SwitchPreference_titleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val titleColor = Option(attributesArray.getColor(R.styleable.SwitchPreference_titleColor, ContextCompat.getColor(context, R.color.color_999)))

  private val subTitleAttr = Option(attributesArray.getString(R.styleable.SwitchPreference_subTitle))
  private val subTitleSize = Option(attributesArray.getDimension(R.styleable.SwitchPreference_subTitleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SUB_TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val subTitleColor = Option(attributesArray.getColor(R.styleable.SwitchPreference_subTitleColor, ContextCompat.getColor(context, R.color.color_999)))

  private val descAttr = Option(attributesArray.getString(R.styleable.SwitchPreference_desc))
  private val descSizeAttr = Option(attributesArray.getDimension(R.styleable.SwitchPreference_descSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DESC_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val descColorAttr = Option(attributesArray.getColor(R.styleable.SwitchPreference_descColor, ContextCompat.getColor(context, R.color.color_999)))

  attributesArray.recycle()

  lazy val zms = inject[Signal[ZMessaging]]

  val prefInfo = Signal[PrefInfo]()
  override val onCheckedChange = EventStream[Boolean]()

  lazy val pref = for {
    z <- zms
    prefInfo <- prefInfo
  } yield {
    if (prefInfo.global)
      z.prefs.preference(prefInfo.key)
    else
      z.userPrefs.preference(prefInfo.key)
  }

  keyAttr.foreach { key =>
    prefInfo ! PrefInfo(PrefKey[Boolean](key, customDefault = false), global = false)
  }

  pref.flatMap(_.signal).onUi(setChecked(_))

  onCheckedChange.onUi { value =>
    pref.head.map(_.update(value))(Threading.Ui)
  }

  val checkChangeListener = new OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) = {
      pref.head.map(_.update(isChecked))(Threading.Ui)
      onCheckedChange ! isChecked
    }
  }

  ttvTitle.foreach { ttvTitle =>
    titleAttr.foreach(ttvTitle.setText)
    titleSize.foreach { titleSize =>
      ttvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize)
    }
    titleColor.foreach { titleColor =>
      ttvTitle.setTextColor(titleColor)
    }
  }

  ttvSubTitle.foreach { ttvSubTitle =>
    setOptionText(ttvSubTitle, ttvSubTitle, subTitleAttr)
    subTitleSize.foreach { subTitleSize =>
      ttvSubTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, subTitleSize)
    }
    subTitleColor.foreach { subTitleColor =>
      ttvSubTitle.setTextColor(subTitleColor)
    }
  }

  titleBackground.foreach { titleBackground =>
    vgTitle.foreach { vgTitle =>
      vgTitle.setBackgroundResource(titleBackground)
    }
  }

  rlDesc.foreach { rlDesc =>
    ttvDesc.foreach { ttvDesc =>
      setOptionText(ttvDesc, rlDesc, descAttr)
      descAttr.foreach { descAttr =>
        verbose(l"descAttr-->$descAttr")
        ttvDesc.setText(descAttr)
      }
      descSizeAttr.foreach { descSizeAttr =>
        verbose(l"descSizeAttr-->$descSizeAttr")
        ttvDesc.setTextSize(TypedValue.COMPLEX_UNIT_PX, descSizeAttr)
      }
      descColorAttr.foreach(ttvDesc.setTextColor)
    }
  }

  showCenterLine.foreach {
    case true =>
      vCenterLine.foreach { vCenterLine =>
        verbose(l"showCenterLine true")
        vCenterLine.setVisibility(View.VISIBLE)
        returning(vCenterLine.getLayoutParams.asInstanceOf[MarginLayoutParams]) { lp =>
          centerLineStyle.foreach {
            case LINE_STYLE_alignTitleStart =>
              verbose(l"LINE_STYLE_alignTitleStart")
              lp.setMargins(imageGlyphMarginSize(getContext), 0, 0, 0)
            case LINE_STYLE_alignParentStart =>
              verbose(l"LINE_STYLE_alignParentStart")
              lp.setMargins(0, 0, 0, 0)
            case unknow =>
              verbose(l"LINE_STYLE:${unknow}")
          }
        }

      }
    case false =>
      verbose(l"showCenterLine false")
      vCenterLine.foreach(_.setVisibility(View.GONE))
  }

  showBottomLine.foreach {
    case true =>
      vBottomLine.foreach { vBottomLine =>
        verbose(l"showBottomLine true")
        vBottomLine.setVisibility(View.VISIBLE)
        returning(vBottomLine.getLayoutParams.asInstanceOf[MarginLayoutParams]) { lp =>
          bottomLineStyle.foreach {
            case LINE_STYLE_alignTitleStart =>
              verbose(l"LINE_STYLE_alignTitleStart")
              lp.setMargins(imageGlyphMarginSize(getContext), 0, 0, 0)
            case LINE_STYLE_alignParentStart =>
              verbose(l"LINE_STYLE_alignParentStart")
              lp.setMargins(0, 0, 0, 0)
            case unknow =>
              verbose(l"LINE_STYLE:${unknow}")
          }
        }
      }

    case false =>
      verbose(l"showBottomLine false")
      vBottomLine.foreach(_.setVisibility(View.GONE))
  }


  def boldTitle(): Unit = {
    ttvTitle.foreach(TextViewUtils.boldText)
  }

  def alphaTitle(alpha: Float): Unit = {
    ttvTitle.foreach(_.setAlpha(alpha))
  }

  def setTitle(text: String): Unit =
    ttvTitle.foreach(_.setText(text))

  def setSubtitle(text: String): Unit = {
    rlDesc.foreach { rlDesc =>
      rlDesc.setVisibility(if (TextUtils.isEmpty(text)) View.GONE else View.VISIBLE)
    }
    ttvSubTitle.foreach(ttvSubTitle => setOptionText(ttvSubTitle, ttvSubTitle, Some(text)))
  }

  def boldSubTitle(color: Int = ContextCompat.getColor(context, R.color.color_333)): Unit = {
    ttvSubTitle.foreach { ttvSubTitle =>
      TextViewUtils.boldText(ttvSubTitle, color)
    }
  }

  def setSwitchButtonVisible(visible: Int): Unit = {
    switch.setVisibility(visible)
  }

  def setChangeListener(listener: OnCheckedChangeListener): Unit = {
    switch.setOnCheckedChangeListener(listener)
  }

  def isChecked(): Boolean = {
    switch.isChecked
  }

  switch.setOnCheckedChangeListener(checkChangeListener)
  this.onClick(setChecked(!switch.isChecked))

  override def setChecked(checked: Boolean): Unit = {
    switch.setChecked(checked)
  }

  override def setChecked(checked: Boolean, disableListener: Boolean = false): Unit = {
    if (disableListener) switch.setOnCheckedChangeListener(null)
    switch.setChecked(checked)
    if (disableListener) switch.setOnCheckedChangeListener(checkChangeListener)
  }

  def setPreference(prefKey: PrefKey[Boolean], global: Boolean = false): Unit = {
    this.prefInfo ! PrefInfo(prefKey, global)
  }


}


case class PrefInfo(key: PrefKey[Boolean], global: Boolean)
