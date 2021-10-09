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
import android.graphics.drawable.Drawable
import android.util.{AttributeSet, TypedValue}
import android.view.View.{OnClickListener, OnLongClickListener}
import android.view.{Gravity, View}
import android.widget.{ImageView, RelativeLayout}
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.OrientationHelper
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.EventStream
import com.waz.utils.returning
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.{ColorUtils, TextViewUtils}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class TextButton(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  def layoutId = R.layout.preference_text_button

  inflate(layoutId)

  setClickable(true)
  setLongClickable(true)

  import TextButton._

  private val rlIconStart = Option(findById[RelativeLayout](R.id.rlIconStart))
  private val gtvStart = Option(findById[GlyphTextView](R.id.gtvStart))
  private val ivStart = Option(findById[ImageView](R.id.ivStart))
  private val rlIconEnd = Option(findById[RelativeLayout](R.id.rlIconEnd))
  private val gtvEnd = Option(findById[GlyphTextView](R.id.gtvEnd))
  private val ivEnd = Option(findById[ImageView](R.id.ivEnd))
  private val rlCenterTitle = Option(findById[RelativeLayout](R.id.rlCenterTitle))
  private val ttvTitle = Option(findById[TypefaceTextView](R.id.ttvTitle))
  private val ttvSubTitle = Option(findById[TypefaceTextView](R.id.ttvSubTitle))
  private val vBottomLine = Option(findById[View](R.id.vBottomLine))

  val onClickEvent = EventStream[View]()
  val onLongClickEvent = EventStream[View]()

  private val attributesArray: TypedArray =
    context.getTheme.obtainStyledAttributes(attrs, R.styleable.TextButton, 0, 0)

  private val titleAttr = Option(attributesArray.getString(R.styleable.TextButton_title))
  private val subTitleAttr = Option(attributesArray.getString(R.styleable.TextButton_subTitle))
  private val iconStartAttr = Option(attributesArray.getResourceId(R.styleable.TextButton_iconStart, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val imageStartAttr = Option(attributesArray.getResourceId(R.styleable.TextButton_iconStartImg, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val iconEndAttr = Option(attributesArray.getResourceId(R.styleable.TextButton_iconEnd, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val imageEndAttr = Option(attributesArray.getResourceId(R.styleable.TextButton_iconEndImg, DEF_RES_ID)).filter(_ != DEF_RES_ID)
  private val textOrientation = Option(attributesArray.getInteger(R.styleable.TextButton_textOrientation, OrientationHelper.HORIZONTAL))
  private val showBottomLine = Option(attributesArray.getBoolean(R.styleable.TextButton_showBottomLine, false))
  private val bottomLineStyle = Option(attributesArray.getInteger(R.styleable.TextButton_bottomLineStyle, LINE_STYLE_alignParentStart))
  private val titleSize = Option(attributesArray.getDimension(R.styleable.TextButton_titleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val subTitleSize = Option(attributesArray.getDimension(R.styleable.TextButton_subTitleSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SUB_TITLE_TEXT_SIZE, context.getResources.getDisplayMetrics)))
  private val titleColor = Option(attributesArray.getColor(R.styleable.TextButton_titleColor, ColorUtils.getAttrColor(context, R.attr.SecretSubTextColor)))
  private val subTitleColor = Option(attributesArray.getColor(R.styleable.TextButton_subTitleColor, ColorUtils.getAttrColor(context, R.attr.SecretSubTextColor)))
  private val endDrawableWidth = Option(attributesArray.getDimension(R.styleable.TextButton_endDrawableWidth, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRAWABLE_WIDTH_HEIGHT, context.getResources.getDisplayMetrics)).toInt)
  private val endDrawableHeight = Option(attributesArray.getDimension(R.styleable.TextButton_endDrawableHeight, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DRAWABLE_WIDTH_HEIGHT, context.getResources.getDisplayMetrics)).toInt)
  attributesArray.recycle()

  private val startIconLayoutVisible = if (iconStartAttr.isEmpty && imageStartAttr.isEmpty) View.GONE else View.VISIBLE

  private val endIconLayoutVisible = if (iconEndAttr.isEmpty && imageEndAttr.isEmpty) View.GONE else View.VISIBLE

  rlIconStart.foreach { rlIconStart =>
    verbose(l"iconVisible start ${startIconLayoutVisible == View.VISIBLE}")
    rlIconStart.setVisibility(startIconLayoutVisible)
  }

  rlIconEnd.foreach { rlIconEnd =>
    verbose(l"iconVisible end${endIconLayoutVisible == View.VISIBLE}")
    rlIconEnd.setVisibility(endIconLayoutVisible)
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
  gtvStart.foreach(iconStart => setOptionGlyphId(iconStart, iconStartAttr))
  ivStart.foreach(imageStart => setOptionImageResource(imageStart, imageStartAttr))
  gtvEnd.foreach(iconEnd => setOptionGlyphId(iconEnd, iconEndAttr))
  ivEnd.foreach(imageEnd => setOptionImageResource(imageEnd, imageEndAttr))


  textOrientation.foreach {
    case OrientationHelper.HORIZONTAL =>
      verbose(l"textOrientation HORIZONTAL")
      ttvTitle.foreach { title =>
        returning(title.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
          lp.setMargins(0, 0, 0, 0)
          lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
        }
      }

      ttvSubTitle.foreach { subtitle =>
        returning(subtitle.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
          lp.removeRule(RelativeLayout.BELOW)

          lp.setMargins(textMarginSize(getContext), 0, 0, 0)
          ttvTitle.foreach { ttvTitle =>
            lp.addRule(RelativeLayout.END_OF, ttvTitle.getId)
            lp.addRule(RelativeLayout.ALIGN_BASELINE, ttvTitle.getId)
          }
          lp.addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)

          subtitle.setGravity(Gravity.END)
        }
      }

    case OrientationHelper.VERTICAL =>

      ttvTitle.foreach { ttvTitle =>
        returning(ttvTitle.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>

          verbose(l"textOrientation VERTICAL ttvTitle")

          lp.removeRule(RelativeLayout.CENTER_VERTICAL)

          lp.setMargins(0, 0, 0, 0)
          lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)

        }
      }

      ttvSubTitle.foreach { ttvSubTitle =>
        returning(ttvSubTitle.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>

          lp.removeRule(RelativeLayout.ALIGN_PARENT_END)
          lp.removeRule(RelativeLayout.ALIGN_BASELINE)
          lp.removeRule(RelativeLayout.END_OF)

          lp.setMargins(0, textMarginSize(getContext), 0, 0)
          ttvTitle.foreach { ttvTitle =>
            verbose(l"textOrientation VERTICAL ttvSubTitle")
            lp.addRule(RelativeLayout.BELOW, ttvTitle.getId)

          }
          lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)

          ttvSubTitle.setGravity(Gravity.START)
        }
      }
    case _ =>
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
              rlCenterTitle.foreach { rlCenterTitle =>
                lp.addRule(RelativeLayout.ALIGN_START, rlCenterTitle.getId)
              }
            case LINE_STYLE_alignParentStart =>
              verbose(l"LINE_STYLE_alignParentStart")
              lp.removeRule(RelativeLayout.ALIGN_START)
              lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
            case LINE_STYLE_alignIconStart =>
              verbose(l"LINE_STYLE_alignIconStart")
              lp.removeRule(RelativeLayout.ALIGN_PARENT_START)
              rlIconStart.foreach { rlIconStart =>
                lp.addRule(RelativeLayout.ALIGN_START, rlIconStart.getId)
              }
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

  def setSubtitle(text: String): Unit =
    ttvSubTitle.foreach(ttvSubTitle => setOptionText(ttvSubTitle, ttvSubTitle, Some(text)))

  def boldSubTitle(): Unit = {
    ttvSubTitle.foreach { ttvSubTitle =>
      TextViewUtils.boldText(ttvSubTitle)
    }
  }

  def setEndGlyphImageDrawable(resGlyphId: Option[Int], resImageDrawable: Option[Drawable], clearOtherWhenEmpty: Boolean, orientationOfEndGlyphImage: Int = TextButton.ORITATION_NONE): Unit = {

    verbose(l"setEndGlyphImageDrawable resGlyphId:${resGlyphId}  resImageDrawable:${resImageDrawable.map(d => ("width" -> (d.getIntrinsicWidth, d.getMinimumHeight), "height" -> (d.getIntrinsicHeight, d.getMinimumHeight)))}  clearOtherWhenEmpty:${clearOtherWhenEmpty}  orientationOfEndGlyphImage:${orientationOfEndGlyphImage}")

    rlIconEnd.foreach { rlIconEnd =>
      rlIconEnd.setVisibility(if (resGlyphId.isEmpty && resImageDrawable.isEmpty) View.GONE else View.VISIBLE)
    }
    if (resGlyphId.isEmpty && resImageDrawable.isEmpty) {
      // ...
    } else if (resGlyphId.nonEmpty && resImageDrawable.nonEmpty) {

      resGlyphId.foreach { resGlyphId =>
        gtvEnd.foreach { gtvEnd =>
          gtvEnd.setVisibility(View.VISIBLE)
          gtvEnd.setText(resGlyphId)
        }
      }

      resImageDrawable.foreach { resImageDrawable =>
        ivEnd.foreach { ivEnd =>
          ivEnd.setVisibility(View.VISIBLE)
          if (resImageDrawable.getIntrinsicWidth < 0 || resImageDrawable.getIntrinsicHeight < 0) {
            (endDrawableWidth, endDrawableHeight) match {
              case (Some(width), Some(height)) =>
                returning(ivEnd.getLayoutParams) { lp =>
                  lp.width = width
                  lp.height = height

                  verbose(l"setEndGlyphImageDrawable set drawable width:${width} height:${height}")
                }
              case _ =>
            }
          } else {
            returning(ivEnd.getLayoutParams) { lp =>
              lp.width = resImageDrawable.getIntrinsicWidth
              lp.height = resImageDrawable.getIntrinsicHeight

              verbose(l"setEndGlyphImageDrawable set drawable IntrinsicWidth:${resImageDrawable.getIntrinsicWidth} IntrinsicHeight:${resImageDrawable.getIntrinsicHeight}")
            }
          }
          ivEnd.setImageDrawable(resImageDrawable)
        }
      }

      orientationOfEndGlyphImage match {
        case ORITATION_IMAGE_LEFT__GLYPH_RIGHT =>
          ivEnd.foreach { ivEnd =>
            returning(ivEnd.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
              lp.removeRule(RelativeLayout.ABOVE)
              lp.removeRule(RelativeLayout.LEFT_OF)
              lp.removeRule(RelativeLayout.BELOW)
              lp.removeRule(RelativeLayout.RIGHT_OF)

              lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE)
              lp.setMargins(0, 0, imageGlyphMarginSize(getContext), 0)
            }
          }

          gtvEnd.foreach { gtvEnd =>
            returning(gtvEnd.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
              lp.removeRule(RelativeLayout.ABOVE)
              lp.removeRule(RelativeLayout.LEFT_OF)
              lp.removeRule(RelativeLayout.BELOW)
              lp.removeRule(RelativeLayout.RIGHT_OF)

            }
          }

          ivEnd.foreach { ivEnd =>
            gtvEnd.foreach { gtvEnd =>
              returning(gtvEnd.getLayoutParams.asInstanceOf[RelativeLayout.LayoutParams]) { lp =>
                lp.addRule(RelativeLayout.RIGHT_OF, ivEnd.getId)
              }
            }
          }

        case _ =>
      }

    } else {
      resGlyphId.foreach { resGlyphId =>
        gtvEnd.foreach { gtvEnd =>
          gtvEnd.setVisibility(View.VISIBLE)
          gtvEnd.setText(resGlyphId)
        }
        ivEnd.foreach { ivEnd =>
          ivEnd.setVisibility(if (clearOtherWhenEmpty) View.GONE else View.VISIBLE)
        }
      }

      resImageDrawable.foreach { resImageDrawable =>
        ivEnd.foreach { ivEnd =>
          ivEnd.setVisibility(View.VISIBLE)
          if (resImageDrawable.getIntrinsicWidth < 0 || resImageDrawable.getIntrinsicHeight < 0) {
            (endDrawableWidth, endDrawableHeight) match {
              case (Some(width), Some(height)) =>
                returning(ivEnd.getLayoutParams) { lp =>
                  lp.width = width
                  lp.height = height

                  verbose(l"setEndGlyphImageDrawable set drawable width:${width} height:${height}")
                }
              case _ =>
            }
          } else {
            returning(ivEnd.getLayoutParams) { lp =>
              lp.width = resImageDrawable.getIntrinsicWidth
              lp.height = resImageDrawable.getIntrinsicHeight

              verbose(l"setEndGlyphImageDrawable set drawable IntrinsicWidth:${resImageDrawable.getIntrinsicWidth} IntrinsicHeight:${resImageDrawable.getIntrinsicHeight}")
            }
          }
          ivEnd.setImageDrawable(resImageDrawable)
        }

        gtvEnd.foreach { gtvEnd =>
          gtvEnd.setVisibility(if (clearOtherWhenEmpty) View.GONE else View.VISIBLE)
        }
      }

    }
  }

  def setEndGlyphImageResource(resGlyphId: Option[Int], resImageId: Option[Int], clearOtherWhenEmpty: Boolean, orientationOfEndGlyphImage: Int = TextButton.ORITATION_NONE): Unit = {
    setEndGlyphImageDrawable(resGlyphId, resImageId.map(id => ContextCompat.getDrawable(getContext, id)), clearOtherWhenEmpty, orientationOfEndGlyphImage)
  }

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

object TextButton {

  val DEF_RES_ID = -1
  val LINE_STYLE_alignParentStart = 0
  val LINE_STYLE_alignTitleStart = 1
  val LINE_STYLE_alignIconStart = 2

  val TITLE_TEXT_SIZE = 16 // sp
  val SUB_TITLE_TEXT_SIZE = 11 // sp
  val DESC_TEXT_SIZE = 11 // sp



  val DRAWABLE_WIDTH_HEIGHT = 22 // dp

  val ORITATION_NONE = -1
  val ORITATION_IMAGE_LEFT__GLYPH_RIGHT = 1

  def setOptionText(textView: TypefaceTextView, visibilityGoneView: View, text: Option[String]): Unit = {
    text.collect { case str if str.nonEmpty => str }.fold {
      visibilityGoneView.setVisible(false)
    } { t =>
      visibilityGoneView.setVisible(true)
      textView.setText(t)
    }
  }


  def textMarginSize(context: Context): Int = context.getResources.getDimension(R.dimen.dp6).toInt

  def imageGlyphMarginSize(context: Context): Int = context.getResources.getDimension(R.dimen.dp16).toInt


}
