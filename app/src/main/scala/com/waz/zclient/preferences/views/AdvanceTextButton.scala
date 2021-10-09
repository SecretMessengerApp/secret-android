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
package com.waz.zclient.preferences.views

import android.content.Context
import android.content.res.TypedArray
import android.text.TextUtils
import android.util.{AttributeSet, TypedValue}
import android.view.View
import android.view.View.{OnClickListener, OnLongClickListener}
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.EventStream
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.{R, ViewHelper}

class AdvanceTextButton(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  def layoutId = R.layout.preference_advance_text_button

  inflate(layoutId)

  import TextButton._

  private val rlCenterTitle = Option(findById[RelativeLayout](R.id.rlCenterTitle))
  private val ttvTitle = Option(findById[TypefaceTextView](R.id.ttvTitle))
  private val rlSubTitle = Option(findById[View](R.id.rlSubTitle))
  private val ttvSubTitle = Option(findById[TypefaceTextView](R.id.ttvSubTitle))
  private val vCenterLine = Option(findById[View](R.id.vCenterLine))
  private val vBottomLine = Option(findById[View](R.id.vBottomLine))

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.AdvanceTextButton, 0, 0)
  private val titleAttr = Option(attributesArray.getString(R.styleable.AdvanceTextButton_title))
  private val subTitleAttr = Option(attributesArray.getString(R.styleable.AdvanceTextButton_subTitle))
  private val showCenterLine = Option(attributesArray.getBoolean(R.styleable.AdvanceTextButton_showCenterLine, false))
  private val showBottomLine = Option(attributesArray.getBoolean(R.styleable.AdvanceTextButton_showBottomLine, false))

  private val titleSize = Option(attributesArray.getDimension(R.styleable.AdvanceTextButton_titleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val subTitleSize = Option(attributesArray.getDimension(R.styleable.AdvanceTextButton_subTitleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SUB_TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val titleColor = Option(attributesArray.getColor(R.styleable.AdvanceTextButton_titleColor, ContextCompat.getColor(context, R.color.color_999)))
  private val subTitleColor = Option(attributesArray.getColor(R.styleable.AdvanceTextButton_subTitleColor, ContextCompat.getColor(context, R.color.color_999)))

  attributesArray.recycle()

  ttvTitle.foreach { ttvTitle =>
    titleAttr.foreach(ttvTitle.setText)
    titleSize.foreach { titleSize =>
      ttvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize)
    }
    titleColor.foreach { titleColor =>
      ttvTitle.setTextColor(titleColor)
    }
  }
  rlSubTitle.foreach { rlSubTitle =>
    ttvSubTitle.foreach { ttvSubTitle =>
      setOptionText(ttvSubTitle, rlSubTitle, subTitleAttr)
      subTitleSize.foreach { subTitleSize =>
        ttvSubTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, subTitleSize)
      }
      subTitleColor.foreach { subTitleColor =>
        ttvSubTitle.setTextColor(subTitleColor)
      }
    }
  }


  showCenterLine.foreach {
    case true =>
      vCenterLine.foreach { vCenterLine =>
        vCenterLine.setVisibility(View.VISIBLE)
      }
    case false =>
      vCenterLine.foreach(_.setVisibility(View.GONE))
  }

  showBottomLine.foreach {
    case true =>
      vBottomLine.foreach { vBottomLine =>
        vBottomLine.setVisibility(View.VISIBLE)
      }
    case false =>
      vBottomLine.foreach(_.setVisibility(View.GONE))
  }

  rlCenterTitle.foreach{_.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = onClickEvent ! v
  })}

  rlCenterTitle.foreach{_.setOnLongClickListener(new OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      onLongClickEvent ! v
      true
    }
  })}

  def boldTitle(): Unit = {
    ttvTitle.foreach(TextViewUtils.boldText)
  }

  def alphaTitle(alpha: Float): Unit = {
    ttvTitle.foreach(_.setAlpha(alpha))
  }

  def setTitle(text: String): Unit =
    ttvTitle.foreach(_.setText(text))

  def setSubtitle(text: String): Unit = {
    rlSubTitle.foreach { rlSubTitle =>
      rlSubTitle.setVisibility(if (TextUtils.isEmpty(text)) View.GONE else View.VISIBLE)
    }
    ttvSubTitle.foreach(ttvSubTitle => setOptionText(ttvSubTitle, ttvSubTitle, Some(text)))
  }

  def boldSubTitle(color: Int = ContextCompat.getColor(context, R.color.color_333)): Unit = {
    ttvSubTitle.foreach { ttvSubTitle =>
      TextViewUtils.boldText(ttvSubTitle, color)
    }
  }



}

