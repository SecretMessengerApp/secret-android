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
package com.waz.zclient.common.views

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{Gravity, View, ViewGroup}
import android.widget.{ImageView, LinearLayout, RelativeLayout}
import androidx.appcompat.widget.AppCompatCheckBox
import com.waz.model._
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.returning
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.controllers.ThemedView
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.paintcode.{ForwardNavigationIcon, GuestIcon, VideoIcon}
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils.{GuestUtils, StringUtils, UiStorage}
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

class SingleUserRowView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper with ThemedView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.single_user_row_view)

  implicit lazy val uiStorage = inject[UiStorage]
  private lazy val convController = inject[ConversationController]

  private lazy val chathead = findById[ChatHeadViewNew](R.id.chathead)
  private lazy val nameView = findById[TypefaceTextView](R.id.name_text)
  private lazy val subtitleView = findById[TypefaceTextView](R.id.username_text)
  private lazy val checkbox = findById[AppCompatCheckBox](R.id.checkbox)
  private lazy val verifiedShield = findById[ImageView](R.id.verified_shield)
  private lazy val guestIndicator = returning(findById[ImageView](R.id.guest_indicator))(_.setImageDrawable(GuestIcon(R.color.light_graphite)))
  private lazy val videoIndicator = returning(findById[ImageView](R.id.video_indicator))(_.setImageDrawable(VideoIcon(R.color.light_graphite)))
  private lazy val nextIndicator = returning(findById[ImageView](R.id.next_indicator))(_.setImageDrawable(ForwardNavigationIcon(R.color.light_graphite_40)))
  private lazy val separator = findById[View](R.id.separator)
  private lazy val auxContainer = findById[ViewGroup](R.id.aux_container)
  private lazy val ttvIsCreatorOrSelf = findById[TypefaceTextView](R.id.ttvIsCreatorOrSelf)

  val onSelectionChanged: SourceStream[Boolean] = EventStream()
  private var solidBackground = false

  def setCheckBoxClickListener(isAutoChange: Boolean = true): Unit = {
    this.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        val isCheck = isChecked
        if (isAutoChange) {
          setChecked(!isCheck)
        }
        onSelectionChanged ! !isCheck
      }
    })
  }

  def setTitle(text: String): Unit = {
    nameView.setText(text)
  }

  def setSubtitle(text: String): Unit =
    if (text.isEmpty) subtitleView.setVisibility(View.GONE)
    else {
      subtitleView.setVisibility(View.VISIBLE)
      subtitleView.setText(text)
    }

  def setSubtitleVisbility(vis : Boolean) = {
    subtitleView.setVisibility(if(vis) View.VISIBLE else View.GONE)
  }

  def setChecked(checked: Boolean): Unit = {
    checkbox.setChecked(checked)
  }

  def toogleChecked(): Unit = {
    val checked = checkbox.isChecked
    setChecked(!checked)
  }

  def isChecked(): Boolean = checkbox.isChecked

  private def setVerified(verified: Boolean) = verifiedShield.setVisibility(if (verified) View.VISIBLE else View.GONE)

  def showArrow(show: Boolean): Unit = nextIndicator.setVisibility(if (show) View.VISIBLE else View.GONE)

  def setCallParticipantInfo(user: CallParticipantInfo): Unit = {
    chathead.clearImage()
    //chathead.loadUser(user.userId)
    chathead.setCallParticipant(user)
    setTitle(user.displayName)
    setVerified(user.isVerified)
    subtitleView.setVisibility(View.GONE)
    //setIsGuest(user.isGuest)
    videoIndicator.setVisibility(if (user.isVideoEnabled) View.VISIBLE else View.GONE)
  }

  def setUserData(userData: UserData, showCreatorOrSelf: Boolean = false, creatorId: UserId = null, selfId: UserId = null
                  , isGroupManager: Boolean = false, createSubtitle: (UserData) => String = SingleUserRowView.defaultSubtitle
                  , aliasData: Option[AliasData] = None): Unit = {
    chathead.clearImage()
    chathead.setUserData(userData, R.drawable.circle_noname)
    val showName = userData.remark.fold(userData.getDisplayName.str) {
      str => s"${str}(${userData.getDisplayName.str})"
    }
    setTitle(aliasData.map(_.getAliasName).filter(_.nonEmpty).getOrElse(showName))
    setVerified(userData.isVerified)
    setSubtitle(createSubtitle(userData))
    setIsGuest(false)
    if(showCreatorOrSelf) {
      ttvIsCreatorOrSelf.setVisibility(View.VISIBLE)
      if(userData.id == selfId && userData.id == creatorId) {
        ttvIsCreatorOrSelf.setText(R.string.group_participant_user_row_creator_and_me)
      } else if(userData.id == selfId && isGroupManager && userData.id != creatorId) {
        ttvIsCreatorOrSelf.setText(R.string.conversation_setting_group_manage_and_me)
      } else if(userData.id == creatorId) {
        ttvIsCreatorOrSelf.setText(R.string.group_participant_user_row_creator)
      } else if(isGroupManager && userData.id != creatorId) {
        ttvIsCreatorOrSelf.setText(R.string.conversation_setting_group_admin_manage)
      } else if(userData.id == selfId) {
        ttvIsCreatorOrSelf.setText(R.string.group_participant_user_row_me)
      } else {
        ttvIsCreatorOrSelf.setText("")
      }
    } else {
      ttvIsCreatorOrSelf.setVisibility(View.GONE)
    }
  }

  def setIntegration(integration: IntegrationData): Unit = {
    chathead.clearImage()
    chathead.setIntegration(integration, R.drawable.circle_noname)
    setTitle(integration.name)
    setAvailability(Availability.None)
    setVerified(false)
    setSubtitle(integration.summary)
  }

  def setIsGuest(guest: Boolean): Unit = guestIndicator.setVisibility(if (guest) View.VISIBLE else View.GONE)

  def showCheckbox(show: Boolean): Unit = checkbox.setVisibility(if (show) View.VISIBLE else View.GONE)

  def setAvailability(availability: Availability): Unit =
    AvailabilityView.displayLeftOfText(nameView, availability, nameView.getCurrentTextColor, pushDown = true)

  def setSeparatorVisible(visible: Boolean): Unit = separator.setVisibility(if (visible) View.VISIBLE else View.GONE)

  def setCustomViews(views: Seq[View]): Unit = {
    auxContainer.removeAllViews()
    views.foreach { v =>
      val params = returning(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))(_.gravity = Gravity.CENTER)
      v.setLayoutParams(params)
      auxContainer.addView(v)
    }
  }
}

object SingleUserRowView {
  def defaultSubtitle(user: UserData)(implicit context: Context): String = {
    val handle = user.handle.map(h => StringUtils.formatHandle(h.string))
    val expiration = user.expiresAt.map(ea => GuestUtils.timeRemainingString(ea.instant, Instant.now))
    expiration.orElse(handle).getOrElse("")
  }
}
