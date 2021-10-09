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
package com.jsy.common.fragment

import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import com.jsy.common.fragment.ForbiddenCustomTimeDialogFragment.OnDateChooseListener
import com.waz.api.IConversation
import com.waz.service.ZMessaging
import com.waz.threading.Threading.Implicits.Ui
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.RichView
import com.waz.zclient.{BaseActivity, FragmentHelper, R}
import org.threeten.bp.Instant

import scala.util.Try

class ForbiddenOptionsFragment extends FragmentHelper {

  private lazy val zMessaging = inject[Signal[ZMessaging]]
  private lazy val conversationController = inject[ConversationController]
  private lazy val participantsController = inject[ParticipantsController]

  private val itemValues = Seq(0, 60 * 10, 60 * 60, 60 * 60 * 12, 60 * 60 * 24, -1)

  private var optionsListLayout: LinearLayout = _

  private var checkIndex = 0
  private var customTime: Long = 0L

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    //val oldDuration = Option(getArguments).fold(0)(_.getInt(ForbiddenOptionsFragment.PARAMS_DURATION))
    //checkIndex = itemValues.indexOf(oldDuration)

    conversationController.currentConv.currentValue.zip(participantsController.otherParticipant.currentValue).foreach { data =>
      data._1.forbiddenUsers.find(_.user == data._2.id).foreach { forbiddenUser =>
        val endTime = Try(forbiddenUser.endTime.toLong).getOrElse(0L)
        if(endTime == -1) {
          checkIndex = itemValues.size - 1
        } else {
          if(endTime - Instant.now().getEpochSecond > 0) {
            checkIndex = itemValues.indexOf(forbiddenUser.duration)
          }
        }
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.forbidden_options_fragment, container, false)

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    optionsListLayout = view.findViewById[LinearLayout](R.id.list_view)
    optionsListLayout.removeAllViews()

    getResources.getStringArray(R.array.forbidden_items).zipWithIndex.foreach[Unit] { case (itemName, index) =>
      val itemView = getLayoutInflater.inflate(R.layout.item_group_setting_forbidden, optionsListLayout, false)

      val textView = findById[TextView](itemView, R.id.text)
      val check = findById[GlyphTextView](itemView, R.id.glyph)

      textView.setText(itemName)
      check.setVisible(index == checkIndex)
      itemView.onClick {
        changeCheck(index)
      }

      optionsListLayout.addView(itemView)
    }

    view.findViewById[View](R.id.change_textView).setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        forbiddenUser()
      }
    })
  }

  private def addLastView(parentView: ViewGroup): Unit = {
    val lastView = getLayoutInflater.inflate(R.layout.layout_item_forbidden_custom, parentView, false)

    val textView = findById[TextView](lastView, R.id.text)
    val check = findById[GlyphTextView](lastView, R.id.glyph)
    val customTimeTextView = Option(findById[TextView](lastView, R.id.custom_time_textView))

    textView.setText(R.string.conversation_detail_settings_forbidden_option_custom)
    check.setVisible(false)
    customTimeTextView.foreach(_.setText(""))

    lastView.onClick({
      showCustomDuration(customTimeTextView)
    })

    parentView.addView(lastView)
  }

  private def showCustomDuration(timeTextView: Option[TextView]): Unit = {
    customTime = 0L
    val datePickerDialogFragment = new ForbiddenCustomTimeDialogFragment()
    datePickerDialogFragment.setOnDateChooseListener(new OnDateChooseListener {
      override def onDateChoose(day: Int, hour: Int, minute: Int): Unit = {
        val dayStr = if (day > 0) {
          day + getString(R.string.conversation_detail_settings_forbidden_option_time_day)
        } else ""
        val hourStr = if (hour > 0) {
          hour + getString(R.string.conversation_detail_settings_forbidden_option_time_hour)
        } else ""
        val minuteStr = if (minute > 0) {
          minute + getString(R.string.conversation_detail_settings_forbidden_option_time_minute)
        } else if (hourStr.isEmpty && dayStr.isEmpty) {
          "1" + getString(R.string.conversation_detail_settings_forbidden_option_time_minute)
        } else ""
        timeTextView.foreach(_.setText(s"$dayStr$hourStr$minuteStr"))

        customTime = day * 60 * 60 * 24 + hour * 60 * 60 + minute * 60
        if (customTime <= 0) {
          customTime = 60
        }

        changeCheck(itemValues.size)
      }
    })
    datePickerDialogFragment.show(getFragmentManager, "ForbiddenCustomTimeDialogFragment")
  }

  private def changeCheck(position: Int): Unit = {
    if (checkIndex != position) {
      checkIndex.synchronized {
        optionsListLayout.getChildAt(checkIndex).findViewById[GlyphTextView](R.id.glyph).setVisible(false)

        checkIndex = position
        optionsListLayout.getChildAt(position).findViewById[GlyphTextView](R.id.glyph).setVisible(true)
      }
    }
  }

  def forbiddenUser(): Unit = {
    conversationController.currentConv.currentValue.foreach {
      conversationData =>
        if (conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          participantsController.isGroupRemoveAndForbiddenCurRight().foreach {
            isRight =>
              if (isRight) {
                changeDuration()
              }
          }
        } else {
          changeDuration()
        }
    }
  }

  private def changeDuration(): Unit = {
    for {
      zms <- zMessaging.head
      conversation <- conversationController.currentConv.head
      currentUser <- participantsController.otherParticipant.head
      response <- {

        var endTime: Long = 0
        var duration: Int = 0

        if(checkIndex < 0) {
          endTime = 0
          duration = 0
        } else if(checkIndex >= itemValues.size) {
          endTime = -1
          duration = -1
        } else {
          val checkedTime = itemValues.apply(checkIndex)
          if(checkedTime > 0) {
            endTime = Instant.now().getEpochSecond + checkedTime
            duration = checkedTime
          } else {
            endTime = checkedTime
            duration = checkedTime
          }
        }
        zms.convsUi.changeBlockTime(conversation.id, currentUser.id, Some(endTime.toString),Some(duration))
      }
    } yield response match {
      case Right(_) =>
        tempToast(getString(R.string.conversation_detail_setting_success))
        getFragmentManager.popBackStack()
      case Left(_)  =>
        tempToast(getString(R.string.generic_error_message))
    }
  }

  private def tempToast(msg: String): Unit = {
    getActivity match {
      case activity: BaseActivity =>
        activity.showToast(msg)
      case _ =>
    }
  }
}

object ForbiddenOptionsFragment {
  val TAG = classOf[ForbiddenOptionsFragment].getSimpleName

  private val PARAMS_DURATION = "params_duration"

  def newInstance(oldDuration: Int = 0): ForbiddenOptionsFragment = {
    val fragment = new ForbiddenOptionsFragment
    val bundle = new Bundle()
    bundle.putInt(PARAMS_DURATION, oldDuration)
    fragment.setArguments(bundle)
    fragment
  }
}
