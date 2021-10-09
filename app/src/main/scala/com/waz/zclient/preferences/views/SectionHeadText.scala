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
import android.util.{AttributeSet, TypedValue}
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.RelativeLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.{R, ViewHelper}
import com.waz.utils.returning

class SectionHeadText(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preference_text_section_head)

  import TextButton._
  import SectionHeadText._

  private val ttvTitle = Option(findById[TypefaceTextView](R.id.ttvTitle))
  private val vTopLine = Option(findById[View](R.id.vTopLine))
  private val vBottomLine = Option(findById[View](R.id.vBottomLine))

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.SectionHeadText, 0, 0)
  private val titleAttr = Option(attributesArray.getString(R.styleable.SectionHeadText_title))
  private val showTopLine = Option(attributesArray.getBoolean(R.styleable.SectionHeadText_showTopLine, false))
  private val showBottomLine = Option(attributesArray.getBoolean(R.styleable.SectionHeadText_showBottomLine, false))
  private val titleSize = Option(attributesArray.getDimension(R.styleable.SectionHeadText_titleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val titleColor = Option(attributesArray.getColor(R.styleable.SectionHeadText_titleColor, ContextCompat.getColor(context, R.color.color_999)))
  private val titleGravity = Option(attributesArray.getInteger(R.styleable.SectionHeadText_titleGravity, -1))
  private val titleFont = Option(attributesArray.getString(R.styleable.SectionHeadText_titleFont))

  attributesArray.recycle()

  ttvTitle.foreach { ttvTitle =>
    titleAttr.foreach(ttvTitle.setText)
    titleSize.foreach { titleSize =>
      ttvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize)
    }
    titleColor.foreach { titleColor =>
      ttvTitle.setTextColor(titleColor)
    }

    boldTitle()
  }

  showTopLine.foreach {
    case true =>
      vTopLine.foreach { vTopLine =>
        vTopLine.setVisibility(View.VISIBLE)
      }
    case false =>
      vTopLine.foreach(_.setVisibility(View.GONE))
  }

  showBottomLine.foreach {
    case true =>
      vBottomLine.foreach { vBottomLine =>
        vBottomLine.setVisibility(View.VISIBLE)
      }
    case false =>
      vBottomLine.foreach(_.setVisibility(View.GONE))
  }

  titleGravity.foreach { titleGravity =>
    verbose(l"titleGravity = $titleGravity")

    titleGravity match {
      case GRAVITY_BOTTOM =>
        ttvTitle.foreach { ttvTitle =>
          returning(ttvTitle.getLayoutParams.asInstanceOf[MarginLayoutParams]) { lp =>
            lp.setMargins(0, dp20, 0, dp10)
          }
        }
      case GRAVITY_TOP =>
        ttvTitle.foreach { ttvTitle =>
          returning(ttvTitle.getLayoutParams.asInstanceOf[MarginLayoutParams]) { lp =>
            lp.setMargins(0, dp10, 0, dp20)
          }
        }
      case -1 => // xml
      case _ => //
    }
  }

  titleFont.foreach { titleFont =>
    ttvTitle.foreach { ttvTitle =>
      ttvTitle.setTypeface(titleFont)
    }
  }

  def boldTitle(): Unit = {
    ttvTitle.foreach(TextViewUtils.boldText)
  }

  def alphaTitle(alpha: Float): Unit = {
    ttvTitle.foreach(_.setAlpha(alpha))
  }

  def setTitle(text: String): Unit =
    ttvTitle.foreach(_.setText(text))

}

object SectionHeadText {

  val GRAVITY_BOTTOM = 0
  val GRAVITY_TOP = 1

  def dp20(implicit context: Context) = context.getResources.getDimension(R.dimen.wire__padding__20).toInt

  def dp10(implicit context: Context) = context.getResources.getDimension(R.dimen.wire__padding__10).toInt

}

