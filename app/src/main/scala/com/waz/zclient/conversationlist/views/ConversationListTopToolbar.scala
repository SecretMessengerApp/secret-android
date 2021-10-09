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
package com.waz.zclient.conversationlist.views

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.{View, ViewGroup}
import android.view.View.OnClickListener
import android.widget.FrameLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Availability, IntegrationData, UserData}
import com.waz.service.ZMessaging
import com.waz.utils.NameParts
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.GlyphButton
import com.waz.zclient.conversationlist.{ConversationListAdapter, ListSeparatorDrawable}
import com.waz.zclient.tracking.AvailabilityChanged
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{RichView, UiStorage, UserSignal}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.ViewHelper
import com.waz.zclient.R

abstract class ConversationListTopToolbar(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends FrameLayout(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {

  inflate(R.layout.view_conv_list_top)

  val title = findById[TypefaceTextView](R.id.conversation_list_title)
  val bottomBorder = findById[View](R.id.conversation_list__border)

  val vpAddIcon = findById[ViewGroup](R.id.vpAddIcon)

  val onRightButtonClick = EventStream[View]()

  protected var scrolledToTop = true
  protected val separatorDrawable = new ListSeparatorDrawable(getColor(R.color.white_24))
  protected val animationDuration = getResources.getInteger(R.integer.team_tabs__animation_duration)

  setClipChildren(false)
  bottomBorder.setBackground(separatorDrawable)

  vpAddIcon.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      onRightButtonClick ! v
    }
  })

  def setScrolledToTop(scrolledToTop: Boolean): Unit =
    if (this.scrolledToTop != scrolledToTop) {
      this.scrolledToTop = scrolledToTop
      if (!scrolledToTop) {
        separatorDrawable.animateCollapse()
      } else {
        separatorDrawable.animateExpand()
      }
    }

  def setTitle(integration: IntegrationData): Unit = {
    title.setText(integration.name)
    title.setOnClickListener(null)
  }

}

class NormalTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int) extends ConversationListTopToolbar(context, attrs, defStyleAttr) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  val zms = inject[Signal[ZMessaging]]
  val controller = inject[UserAccountsController]
  implicit val uiStorage = inject[UiStorage]

  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.setMinMax(0f, 1.0f)
  separatorDrawable.setClip(1.0f)

  vpAddIcon.setVisibility(View.VISIBLE)

  override def setScrolledToTop(scrolledToTop: Boolean): Unit =
    if (this.scrolledToTop != scrolledToTop) {
      super.setScrolledToTop(scrolledToTop)
    }

  def setLoading(loading: Boolean): Unit = {}

}


class ArchiveTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int) extends ConversationListTopToolbar(context, attrs, defStyleAttr) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.animateExpand()
  vpAddIcon.setVisibility(View.GONE)
}

class IntegrationTopToolbar(override val context: Context, override val attrs: AttributeSet, override val defStyleAttr: Int) extends ConversationListTopToolbar(context, attrs, defStyleAttr) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)

  /*backButton.setVisible(true)
  closeButtonEnd.setVisible(true)
  settingsIndicator.setVisible(false)*/
  title.setVisible(true)
  separatorDrawable.setDuration(0)
  separatorDrawable.animateExpand()
  vpAddIcon.setVisibility(View.GONE)
}
