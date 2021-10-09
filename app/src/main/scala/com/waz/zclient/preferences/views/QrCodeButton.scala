/**
 * Secret
 * Copyright (C) 2019 Secret
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
import android.view.View
import android.view.View.{OnClickListener, OnLongClickListener}
import android.widget.{ImageView, RelativeLayout}
import androidx.core.content.ContextCompat
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.EventStream
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class QrCodeButton(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  def layoutId = R.layout.preference_qr_code_button

  inflate(layoutId)

  setClickable(true)
  setLongClickable(true)

  import TextButton._


  private val ttvTitle = Option(findById[TypefaceTextView](R.id.ttvTitle))
  private val ivQrCode = Option(findById[ImageView](R.id.ivQrCode))
  private val ivEndImg = Option(findById[ImageView](R.id.ivEndImg))
  private val vBottomLine = Option(findById[View](R.id.vBottomLine))

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val attributesArray: TypedArray = context.getTheme.obtainStyledAttributes(attrs, R.styleable.QrCodeButton, 0, 0)

  private val titleAttr = Option(attributesArray.getString(R.styleable.QrCodeButton_title))
  private val qrImgAttr = Option(attributesArray.getResourceId(R.styleable.QrCodeButton_qrImg, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val imageEndAttr = Option(attributesArray.getResourceId(R.styleable.QrCodeButton_iconEndImg, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val showBottomLine = Option(attributesArray.getBoolean(R.styleable.QrCodeButton_showBottomLine, false))
  private val bottomLineStyle = Option(attributesArray.getInteger(R.styleable.QrCodeButton_bottomLineStyle, LINE_STYLE_alignParentStart))
  private val titleSize = Option(attributesArray.getDimension(R.styleable.QrCodeButton_titleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val titleColor = Option(attributesArray.getColor(R.styleable.QrCodeButton_titleColor, ContextCompat.getColor(context, R.color.color_999)))
  private val qrWidth = Option(attributesArray.getDimension(R.styleable.QrCodeButton_qrImgWidth, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRAWABLE_WIDTH_HEIGHT, context.getResources.getDisplayMetrics)))
  private val qrHeight = Option(attributesArray.getDimension(R.styleable.QrCodeButton_qrImgHeight, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRAWABLE_WIDTH_HEIGHT, context.getResources.getDisplayMetrics)))


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

  ivQrCode.foreach { ivQrCode =>
    qrImgAttr.fold(ivQrCode.setVisibility(View.GONE)) { qrImgAttr =>
      ivQrCode.setVisibility(View.VISIBLE)
      ivQrCode.setImageResource(qrImgAttr)
    }
  }

  ivEndImg.foreach { ivEndImg =>
    imageEndAttr.fold(ivEndImg.setVisibility(View.GONE)) { imageEndAttr =>
      ivEndImg.setVisibility(View.VISIBLE)
      ivEndImg.setImageResource(imageEndAttr)
    }


  }

  showBottomLine.foreach {
    case true =>
      vBottomLine.foreach { vBottomLine =>
        vBottomLine.setVisibility(View.VISIBLE)
        returning(vBottomLine.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
          lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
          bottomLineStyle.foreach {
            case LINE_STYLE_alignTitleStart =>
              verbose(l"LINE_STYLE_alignTitleStart")
              lp.removeRule(RelativeLayout.ALIGN_PARENT_START)
              ttvTitle.foreach { ttvTitle =>
                lp.addRule(RelativeLayout.ALIGN_START, ttvTitle.getId)
              }
            case LINE_STYLE_alignParentStart =>
              verbose(l"LINE_STYLE_alignParentStart")
              lp.removeRule(RelativeLayout.ALIGN_START)
              lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
            case LINE_STYLE_alignIconStart =>
            //...
          }
        }
      }
    case false =>
      vBottomLine.foreach(_.setVisibility(View.GONE))
  }

  setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = onClickEvent ! v
  })

  setOnLongClickListener(new OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      onLongClickEvent ! v
      true
    }
  })


  def getTitle(): String = {
    ttvTitle.fold {
      ""
    } { ttvTitle =>
      ttvTitle.getText.toString
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

  private def setOptionImageResource(imageView: ImageView, imageEndAttr: Option[Int]): Unit = {
    imageEndAttr match {
      case Some(resId) if resId > DEF_RES_ID =>
        imageView.setImageResource(resId)
        imageView.setVisibility(View.VISIBLE)
      case _ =>
        imageView.setVisibility(View.GONE)
    }
  }

  private def setOptionGlyphId(glyphTextView: GlyphTextView, textId: Option[Int]): Unit = {
    textId match {
      case Some(t) if t > DEF_RES_ID =>
        glyphTextView.setVisible(true)
        glyphTextView.setText(t)
      case _ =>
        glyphTextView.setVisible(false)
    }
  }


}

