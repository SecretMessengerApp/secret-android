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
package com.waz.zclient.messages.parts.footer

import android.animation.Animator.AnimatorListener
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.animation.{Animator, ValueAnimator}
import android.content.Context
import android.graphics._
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView
import android.view.{View, ViewGroup}
import android.widget.{FrameLayout, RelativeLayout, TextView}
import androidx.core.view.ViewCompat
import com.waz.model.{MessageContent, MessageData, MessageId}
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.conversation.LikesAndReadsFragment
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.footer.FooterPartView._
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.{R, ViewHelper}

//TODO tracking ?
class FooterPartView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ClickableViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Footer

  private lazy val screenController = inject[ScreenController]

  inflate(R.layout.message_footer_content)

  val controller = new FooterViewController
  val messageActionsController = inject[MessageActionsController]

  val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account }

  val height = Signal[Int]()

  val contentOffset = Signal[Float]()
  val contentTranslate = for {
    h <- height
    o <- contentOffset
  } yield h * o

  val closing = Signal(false)
  val switchOffset = Signal(0f)

  private var lastSwitchTranslate = 0
  val switchTranslate = for {
    h <- height
    c <- closing
    o <- switchOffset
  } yield {
    if (c) lastSwitchTranslate // disable switch if footer is closing now
    else {
      lastSwitchTranslate = (h * o).toInt
      lastSwitchTranslate
    }
  }

  val likesVisible = switchOffset.map(_ > .01f)
  val statusVisible = switchOffset.map(_ < .99f)

  val statusTranslate = for {
    t <- contentTranslate
    s <- switchTranslate
  } yield t - s

  val likesTranslate = statusTranslate map {
    _ + getHeight
  }

  val contentAnim = new ValueAnimator() {
    setFloatValues(0f, 1f)
    addUpdateListener(new AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator) =
        contentOffset ! animation.getAnimatedFraction - 1.0f
    })
  }

  val switchAnim = new ValueAnimator() {
    setFloatValues(0f, 1f)
    addUpdateListener(new AnimatorUpdateListener {
      override def onAnimationUpdate(animation: ValueAnimator) =
        switchOffset ! animation.getAnimatedValue.asInstanceOf[Float]
    })
  }
  messageActionsController.onLikedUserChanged.onUi{msgIdAndLikes=>
    if (message.currentValue.exists(_.id == msgIdAndLikes._1)){
      currentUser.currentValue.foreach{self=>
        //        likeButton.refresh(messageAndLikesAndLikedIds._2.contains(self.id))
        //        likeDetails.refresh(messageAndLikesAndLikedIds._2)
        val msg = msgLikes.head.copy(/*message: MessageData,*/ likes = msgIdAndLikes._2, likedBySelf = msgIdAndLikes._2.contains(self.id)/*, quote: Option[MessageData]*/)
        set(msg, prev, next, part, opts, adapter)
      }

    }
  }

  val hideAnim = new HideAnimator(this)

  private val likeButton: LikeButton = findById(R.id.like__button)
  private val timeStampAndStatus: TextView = findById(R.id.timestamp_and_status)
  private val likeDetails: LikeDetailsView = findById(R.id.like_details)

  controller.likesController.onLikedUserChanged.onUi { messageAndLikesAndLikedIds =>
    if (message.currentValue.exists(_.id == messageAndLikesAndLikedIds._1.message.id)){
      currentUser.currentValue.foreach{self=>
        val msg = messageAndLikesAndLikedIds._1.copy(likes = messageAndLikesAndLikedIds._2.toIndexedSeq, likedBySelf = messageAndLikesAndLikedIds._2.contains(self.id)/*, quote: Option[MessageData]*/)
        set(msg, prev, next, part, opts, adapter)
      }

    }
  }
  controller.likedBySelf.onUi{likeBySelf=>
  }

  likeDetails.onClickedLikedUser { userId =>
    message.currentValue.foreach { msg =>
      screenController.showMessageDetails ! Some(MessageDetailsParams(msg.id, LikesAndReadsFragment.LikesTab))
    }
  }

  setClipChildren(true)
  height { h =>
    ViewCompat.setClipBounds(this, new Rect(0, 0, getWidth, h))
  }

  contentTranslate {
    likeButton.setTranslationY
  }
  likesTranslate {
    likeDetails.setTranslationY
  }
  statusTranslate {
    timeStampAndStatus.setTranslationY
  }
  likesVisible {
    likeDetails.setVisible
  }
  statusVisible {
    timeStampAndStatus.setVisible
  }

  likeButton.init(controller)
  likeDetails.init(controller)

  controller.timestampText.zip(controller.linkColor).on(Threading.Ui) { case (string, color) =>
    timeStampAndStatus.setText(string)
    if (string.contains('_')) {
      TextViewUtils.linkifyText(timeStampAndStatus, color, false, controller.linkCallback)
    }
  }


  private var lastShow = false
  private var lastMsgId = MessageId()
  controller.showTimestamp.on(Threading.Ui) { st =>
    switchAnim.cancel()

    val msgId = message.currentValue.fold(lastMsgId)(_.id)
    val animate = lastMsgId == msgId && lastShow != st
    if (animate) {
      val current = switchOffset.currentValue.getOrElse(0f)
      if (st) switchAnim.setFloatValues(current, 0)
      else switchAnim.setFloatValues(current, 1)
      switchAnim.start()
    } else {
      switchOffset ! (if (st) 0 else 1)
    }

    lastShow = st
    lastMsgId = msgId
  }

  controller.expiring.on(Threading.Ui)(likeButton.setGone)

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int): Unit = {
    super.onLayout(changed, left, top, right, bottom)
    height ! (bottom - top)
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    //animator.end()
    super.set(msg, prev, next, part, opts, adapter)
    hideAnim.cancel()
    contentAnim.cancel()
    contentOffset ! 0
    hideAnim.size ! 1f
    closing ! false

    opts.foreach { opts =>
      controller.opts.publish(opts, Threading.Ui)

      if (opts.isSelf) {
        setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
      } else {
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
      }
    }
    controller.messageAndLikes.publish(msg, Threading.Ui)


  }

  def slideContentIn(): Unit = contentAnim.start()

  def slideContentOut(): Unit = hideAnim.start()
}

object FooterPartView {

  /**
    * Animates footer size to hide it.
    * Note: this animator changes view layout params, this is ugly, inefficient, causes layout pass on every frame,
    * but I could't find any better way to achieve desired result in RecyclerView.
    */
  class HideAnimator(footer: FooterPartView)(implicit context: Context, ec: EventContext) extends ValueAnimator with AnimatorListener with AnimatorUpdateListener {
    setFloatValues(0f, 1f)
    addListener(this)
    addUpdateListener(this)

    val expandedHeight = getDimenPx(R.dimen.content__footer__height)

    val size = Signal(1f)
    val lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, expandedHeight)

    size.map { s => (expandedHeight * s).toInt } { h =>
      lp.height = h
      footer.setLayoutParams(lp)
    }

    override def onAnimationUpdate(animation: ValueAnimator): Unit =
      size ! (1f - animation.getAnimatedFraction)

    override def onAnimationStart(animation: Animator): Unit = {
      footer.closing ! true
      footer.setVisibility(View.VISIBLE)
    }

    override def onAnimationEnd(animation: Animator): Unit = {
      footer.closing ! false
      size ! 1f
      footer.setVisibility(View.GONE)
    }

    override def onAnimationRepeat(animation: Animator): Unit = ()

    override def onAnimationCancel(animation: Animator): Unit = ()
  }

}
