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
package com.waz.zclient.conversation

import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, TextView}
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.utils.ContextUtils._


import com.waz.zclient.{R, ViewHelper}

class TypingIndicatorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val replyController = inject[ReplyController]
  inflate(R.layout.typing_indicator)

  private val nameTextView = findById[TextView](R.id.ttv__typing_indicator_names)
  private val dotsView = findById[View](R.id.gtv__is_typing_dots)
  private val penView = findById[View](R.id.gtv__is_typing_pen)
  private val topBackground = findById[View](R.id.top_background)

  private var animationRunning: Boolean = false
  private val handler = new Handler

  private lazy val typingUsers = for {
    z <- zms
    convId <- convController.currentConvId
    userIds <- z.typing.typingUsers(convId)
    users <- z.usersStorage.listSignal(userIds.filterNot(_ == z.selfUserId))
    aliasUsers <- z.aliasStorage.listSignal(convId)
  } yield (users,aliasUsers)

  typingUsers.onUi { parts =>
    if (parts._1.isEmpty) {
      nameTextView.setText("")
      setVisibility(View.GONE)
      endAnimation()
    } else {
      nameTextView.setText(parts._1.map{user =>
        parts._2.find(aliasData => aliasData.userId == user.id)
          .map(_.getAliasName).filter(_.nonEmpty).getOrElse(user.getShowName)
      }.mkString(", "))
      setVisibility(View.VISIBLE)
      startAnimation()
    }
  }

  private def startAnimation() =
    if(!animationRunning) {
      animationRunning = true
      runAnimation()
    }

  def endEditingAnim(): Unit = {
    nameTextView.setText("")
    setVisibility(View.GONE)
    endAnimation()
  }


  private def runAnimation(): Unit =
    if (animationRunning) {
      val stepDuration = getResources.getInteger(R.integer.animation_duration_medium_rare)
      val step1 = dotsView.getWidth / 3
      val step2 = step1 * 2
      val step3 = dotsView.getWidth

      def animateStep(step: Int) =
        penView.animate().translationX(step).setDuration(stepDuration).start()

      def getRunnable(step: Int) = new Runnable {
        override def run(): Unit = animateStep(step)
      }

      animateStep(step1)
      handler.postDelayed(getRunnable(step2), stepDuration * 2)

      handler.postDelayed(getRunnable(step3), stepDuration * 4)

      handler.postDelayed(new Runnable() {
        override def run(): Unit = {
          runAnimation()
        }
      }, stepDuration * 8)
    }

  private def endAnimation() =
    if (animationRunning) {
      handler.removeCallbacksAndMessages(null)
      animationRunning = false
    }
}
