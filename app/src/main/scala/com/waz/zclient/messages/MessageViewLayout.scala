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
package com.waz.zclient.messages

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.view.ViewGroup.{LayoutParams, MarginLayoutParams}
import android.view.{Gravity, View, ViewGroup}
import android.widget.FrameLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.EventContext
import com.waz.zclient.R
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MsgPart.{Reply, WebLink}
import com.waz.zclient.messages.parts.ReplyPartView
import com.waz.zclient.utils.ContextUtils.getDimen

abstract class MessageViewLayout(context: Context, attrs: AttributeSet, style: Int)
  extends ViewGroup(context, attrs, style) with DerivedLogTag {

  import MessageViewLayout._

  protected val factory: MessageViewFactory

  var listParts = Seq.empty[MessageViewPart]
  protected var frameParts = Seq.empty[MessageViewPart]
  private var separatorHeight = 0

  private val defaultLayoutParams = generateDefaultLayoutParams()

  setClipChildren(false)

  var opts: Option[MsgBindOptions] = Option.empty
  private var msg: Option[MessageAndLikes] = Option.empty

  private var isSameSide@(preIsSameSide, nextIsSameSide) = (false, false)
  private var prev: Option[MessageData] = None

  //  protected def setParts(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], parts: Seq[PartDesc], opts: MsgBindOptions, repliedMessage: MessageAndLikes)
  def setParts(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], parts: Seq[PartDesc], opts: MsgBindOptions, adapter: MessagesPagedListAdapter)(implicit ec: EventContext): Unit = {
    this.msg = Some(msg)
    this.opts = Some(opts)
    this.prev = prev;

    isSameSide = MessageView.latestIsSameSide(msg, prev, next, None, opts)


    // recycle views in reverse order, recycled views are stored in a Stack, this way we will get the same views back if parts are the same
    // XXX: once views get bigger, we may need to optimise this, we don't need to remove views that will get reused, currently this seems to be fast enough
    (0 until getChildCount).reverseIterator.map(getChildAt) foreach {
      case pv: MessageViewPart => factory.recycle(pv)
      case _ =>
    }
    removeAllViewsInLayout() // TODO: avoid removing views if not really needed, compute proper diff with previous state

    val views = if (parts.exists(_.tpe == WebLink)) {

      var childIndex = 0
      val zip = parts.filter(_.tpe != null).zipWithIndex
      val userIndex = zip.indexWhere(_._1.tpe == MsgPart.User)
      val webLinkIndex = zip.indexWhere(_._1.tpe == MsgPart.WebLink)
      val footerIndex = zip.indexWhere(_._1.tpe == MsgPart.Footer)
      val separatorIndex = zip.indexWhere { p =>
        p._1.tpe == MsgPart.Separator || p._1.tpe == MsgPart.SeparatorLarge
      }

      var resultViews: Seq[MessageViewPart] = Seq.empty[MessageViewPart]

      if (separatorIndex >= 0) {
        val partDesc = zip.apply(separatorIndex)._1
        val view = factory.get(partDesc.tpe, this)
        view.setVisibility(View.VISIBLE)
        view.setFocusable(false)
        view.set(msg = msg, prev = prev, next = next, part = partDesc.content, opts = Some(opts), adapter = Option(adapter))
        addViewInLayout(view, childIndex, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
        childIndex += 1
        resultViews = resultViews ++ Seq(view)
      }

      if (userIndex >= 0) {
        val partDesc = zip.apply(userIndex)._1
        val view = factory.get(partDesc.tpe, this)
        view.setVisibility(View.VISIBLE)
        view.setFocusable(false)
        view.set(msg = msg, prev = prev, next = next, part = partDesc.content, opts = Some(opts), adapter = Option(adapter))
        addViewInLayout(view, childIndex, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
        childIndex += 1
        resultViews = resultViews ++ Seq(view)
      }
      if (webLinkIndex >= 0) {
        val partDesc = zip.apply(webLinkIndex)._1
        val view = factory.get(partDesc.tpe, this)
        view.setVisibility(View.VISIBLE)
        view.setFocusable(false)
        view.set(msg = msg, prev = prev, next = next, part = partDesc.content, opts = Some(opts), adapter = Option(adapter))
        addViewInLayout(view, childIndex, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
        childIndex += 1
        resultViews = resultViews ++ Seq(view)
      }
      if (footerIndex >= 0) {
        val partDesc = zip.apply(footerIndex)._1
        val view = factory.get(partDesc.tpe, this)
        view.setVisibility(View.VISIBLE)
        view.setFocusable(false)
        view.set(msg = msg, prev = prev, next = next, part = partDesc.content, opts = Some(opts), adapter = Option(adapter))
        addViewInLayout(view, childIndex, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
        childIndex += 1
        resultViews = resultViews ++ Seq(view)
      }
      resultViews
    } else {
      parts.zipWithIndex map { case (PartDesc(tpe, content), index) if tpe != null =>
        val view = factory.get(tpe, this)
        view.setVisibility(View.VISIBLE)
        view.setFocusable(false)
        view.tpe match {
          case Reply(_) =>
            Option(view.getLayoutParams).filter(_.isInstanceOf[MarginLayoutParams]).map(_.asInstanceOf[MarginLayoutParams]).foreach{params =>
              params.bottomMargin = 0
              view.setLayoutParams(params)
            }
          case _        =>
        }
        view.set(msg = msg, prev = prev, next = next, part = content, opts = Some(opts), adapter = Option(adapter))
        (view, msg.quote) match {
          case (v: ReplyPartView, Some(quote)) /*if msg.message.quote.exists(_.validity)*/ =>
            v.setQuote(quote)
            v.onQuoteClick.onUi { _ =>
              adapter.positionForMessage(quote.id).foreach { pos =>
                if (pos >= 0) adapter.onScrollRequested ! (quote, pos)
              }
            }
          case _                                                                           =>
        }
        addViewInLayout(view, index, Option(view.getLayoutParams).getOrElse(defaultLayoutParams))
        view
      }
    }

    val (fps, lps) = views.filter(_ != null).partition {
      case _: FrameLayoutPart => true
      case _ => false
    }
    frameParts = fps
    listParts = lps

    // TODO: request layout only if parts were actually changed
    requestLayout()



  }

  def removeListPart(view: MessageViewPart) = {
    listParts = listParts.filter(_ != view)
    removeView(view)
    factory.recycle(view)
  }

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    val w = View.getDefaultSize(getSuggestedMinimumWidth, widthMeasureSpec)
    var h = getPaddingTop + getPaddingBottom
    separatorHeight = 0
    listParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        measureChildWithMargins(v, widthMeasureSpec, 0, heightMeasureSpec, 0)
        val m = getMargin(v.getLayoutParams)
        h += v.getMeasuredHeight + m.top + m.bottom

        if (v.tpe.isInstanceOf[SeparatorPart])
          separatorHeight += v.getMeasuredHeight + m.top + m.bottom
      }
    }

    val hSpec = MeasureSpec.makeMeasureSpec(h - separatorHeight, MeasureSpec.AT_MOST)
    frameParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        measureChildWithMargins(v, widthMeasureSpec, 0, hSpec, 0)
        h = math.max(h, v.getMeasuredHeight + separatorHeight + getPaddingTop + getPaddingBottom)
      }
    }

    setMeasuredDimension(w, h)
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    //
    //    val w = r - l
    //    val h = b - t
    //    var top = getPaddingTop
    //    listParts foreach { v =>
    //      if (v.getVisibility != View.GONE) {
    //        val vh = v.getMeasuredHeight
    //        val m = getMargin(v.getLayoutParams)
    //
    //        top += m.top
    //        // TODO: handle RTL
    //        v.layout(m.left, top, m.left + v.getMeasuredWidth, top + vh)
    //        top += vh + m.bottom
    //      }
    //    }
    //
    //    frameParts foreach { v =>
    //      if (v.getVisibility != View.GONE) {
    //        val vw = v.getMeasuredWidth
    //        val vh = v.getMeasuredHeight
    //        val gravity = v.getLayoutParams match {
    //          case lp: FrameLayout.LayoutParams => lp.gravity
    //          case _ => Gravity.TOP | Gravity.START
    //        }
    //        val m = getMargin(v.getLayoutParams)
    //        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, getLayoutDirection)
    //
    //        val left = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK match {
    //          case Gravity.CENTER_HORIZONTAL =>
    //            (w - vw) / 2 + m.left - m.right
    //          case Gravity.RIGHT =>
    //            w - vw - m.right
    //          case _ =>
    //            m.left
    //        }
    //
    //        val top = gravity & Gravity.VERTICAL_GRAVITY_MASK match {
    //          case Gravity.CENTER_VERTICAL =>
    //            separatorHeight / 2 + (h - vh) / 2 + m.top - m.bottom
    //          case Gravity.BOTTOM =>
    //            h - vh - m.bottom - getPaddingBottom
    //          case _ =>
    //            m.top + separatorHeight + getPaddingTop
    //        }
    //        v.layout(left, top, left + vw, top + vh)
    //      }
    //    }

    val w = r - l
    val h = b - t
    var top = getPaddingTop
    listParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        val vh = v.getMeasuredHeight
        val m = getMargin(v)
        /*
        top += m.top
        v.layout(m.left, top, m.left + v.getMeasuredWidth, top + vh)
        top += vh + m.bottom
        */
        top += m.top
        // TODO: handle RTL
        if (opts.nonEmpty && msg.nonEmpty) {
          val isSelf = opts.head.isSelf
          if (v.tpe == MsgPart.MemberChange) {
            v.layout(w - m.left - v.getMeasuredWidth, top, w - m.left, top + vh)
          } else if (v.tpe == MsgPart.StartedUsingDevice || v.tpe == MsgPart.ConvMsgEditVerify) {
            v.layout(m.left, top, w - m.left, top + vh)
          } else {
            if (isSelf) {
              v.layout(w - m.left - v.getMeasuredWidth, top, w - m.left, top + vh)
            } else {
              v.layout(m.left, top, m.left + v.getMeasuredWidth, top + vh)
            }
          }
        }

        top += vh + m.bottom

      }
    }

    frameParts foreach { v =>
      if (v.getVisibility != View.GONE) {
        val vw = v.getMeasuredWidth
        val vh = v.getMeasuredHeight
        val gravity = v.getLayoutParams match {
          case lp: FrameLayout.LayoutParams => lp.gravity
          case _ => Gravity.TOP | Gravity.START
        }
        val m = getMargin(v)
        val absoluteGravity = Gravity.getAbsoluteGravity(gravity, getLayoutDirection)

        val left = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK match {
          case Gravity.CENTER_HORIZONTAL =>
            (w - vw) / 2 + m.left - m.right
          case Gravity.RIGHT =>
            w - vw - m.right
          case _ =>
            m.left
        }

        val top = gravity & Gravity.VERTICAL_GRAVITY_MASK match {
          case Gravity.CENTER_VERTICAL =>
            separatorHeight / 2 + (h - vh) / 2 + m.top - m.bottom
          case Gravity.BOTTOM =>
            h - vh - m.bottom - getPaddingBottom
          case _ =>
            m.top + separatorHeight + getPaddingTop
        }
        v.layout(left, top, left + vw, top + vh)

      }
    }
  }


  private def getMargin(v: MessageViewPart) = {
    val lp = getMarginLp(v.getLayoutParams)
    val t = lp.top
    val b = lp.bottom
    val mp = v.getLayoutParams
    if (mp.isInstanceOf[MarginLayoutParams] && opts.nonEmpty && msg.nonEmpty) {
      v.tpe match {

        case MsgPart.Text
             | MsgPart.WebLink
             | MsgPart.YouTube
             | MsgPart.Image
             | MsgPart.AudioAsset
             | MsgPart.FileAsset
             | MsgPart.VideoAsset
             | MsgPart.SoundMedia
             | MsgPart.Location
             | MsgPart.Footer

             | MsgPart.TextJson_EmojiGifPart

             | MsgPart.Reply(_)
        =>
          if (MessageView.shouldShowChatheadAllChildMargin(opts.head.convType, msg.head.message, opts.head.isSelf, prev)) {
            val l = dp_48(context)
            val r = dp_24(context)
            new Rect(l, t, r, b)
          } else {
            val lr = dp_24(context)
            new Rect(lr, t, lr, b)
          }
        case
          MsgPart.Ping
          | MsgPart.TextJson_Group_Participant_Invite
        =>

          if (MessageView.shouldShowChatheadAllChildMargin(opts.head.convType, msg.head.message, opts.head.isSelf, prev)) {
            val l = dp_48(context)
            val r = dp_24(context)
            new Rect(l + getANGLE_WIDTH, t, r, b)
          } else {
            val lr = dp_24(context)
            new Rect(lr + getANGLE_WIDTH, t, lr, b)
          }
        case MsgPart.TextJson_Screen_Shot =>
          val lr = dp_48(context)
          new Rect(lr, t, lr, b)
        case _ => lp
      }
    } else {
      lp
    }

  }

  private val EmptyMargin = new Rect(0, 0, 0, 0)

  private def getMargin(lp: LayoutParams) = lp match {
    case mp: MarginLayoutParams => new Rect(mp.leftMargin, mp.topMargin, mp.rightMargin, mp.bottomMargin)
    case _ => EmptyMargin
  }

  private def getMarginLp(lp: LayoutParams) = lp match {
    //case mp: MarginLayoutParams => new Rect(mp.leftMargin, mp.topMargin, mp.rightMargin, mp.bottomMargin)
    case mp: MarginLayoutParams => new Rect(mp.leftMargin, mp.topMargin, mp.rightMargin, mp.bottomMargin)
    case _ => EmptyMargin
  }


  override def shouldDelayChildPressedState(): Boolean = false

  override def generateLayoutParams(attrs: AttributeSet): LayoutParams =
    new FrameLayout.LayoutParams(getContext, attrs)

  override def generateLayoutParams(p: LayoutParams): LayoutParams =
    new FrameLayout.LayoutParams(p)

  override def generateDefaultLayoutParams(): LayoutParams =
    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}

object MessageViewLayout {

  case class PartDesc(tpe: MsgPart, content: Option[MessageContent] = None)

  /**
    * h = 8
    * xh xxh xxxh = 15
    * @return
    */
  def getANGLE_WIDTH(): Int = {
    if (Resources.getSystem.getDisplayMetrics.density < 2) 8 else 15
  }

  def dp_72(context: Context) = getDimen(R.dimen.message_content_padding_72)(context).toInt

  def dp_48(context: Context) = getDimen(R.dimen.message_content_padding_48)(context).toInt

  def dp_24(context: Context) = getDimen(R.dimen.message_content_padding_24)(context).toInt

  def dp_15(context: Context) = getDimen(R.dimen.system_notification_textjson_ml_mr)(context).toInt

  def dp_chatHeadMargin(context: Context) = getDimen(R.dimen.chathead__margin)(context).toInt

}
