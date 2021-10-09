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
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.annotation.Nullable
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.moduleProxy.ProxyConversationActivity
import com.waz.content.{MessagesStorage, ReactionsStorage, ReadReceiptsStorage}
import com.waz.model.{MessageData, RemoteInstant, UserData, UserId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.messages.LikesController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.paintcode.{GenericStyleKitView, WireStyleKit}
import com.waz.zclient.participants.ParticipantsAdapter
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.utils.ContextUtils.getColor
import com.waz.zclient.utils.RichView
import com.waz.zclient.utils.Time.SameDayTimeStamp
import com.waz.zclient.views.ConversationFragment
import com.waz.zclient.{FragmentHelper, R}

class LikesAndReadsFragment extends FragmentHelper {

  implicit def ctx: Context = getActivity

  private lazy val screenController = inject[ScreenController]
  private lazy val reactionsStorage = inject[Signal[ReactionsStorage]]
  private lazy val messagesStorage = inject[Signal[MessagesStorage]]
  private lazy val convController      = inject[ConversationController]

  private lazy val likes: Signal[Seq[UserId]] =
    Signal(reactionsStorage, screenController.showMessageDetails)
      .collect { case (storage, Some(msgId)) => (storage, msgId) }
      .flatMap { case (storage, MessageDetailsParams(msgId, _)) => storage.likes(msgId).map(_.likers.keys.toSeq) }

  private lazy val message = for {
    messagesStorage <- messagesStorage
    Some(msgParams) <- screenController.showMessageDetails
    msg <- messagesStorage.signal(msgParams.messageId)
  } yield msg

  private lazy val isOwnMessage = for {
    selfUserId <- inject[Signal[UserId]]
    msg <- message
  } yield selfUserId == msg.userId

  private lazy val isEphemeral = message.map(_.isEphemeral)

  private lazy val isLikeable = message.map(LikesController.isLikeable)

  private lazy val closeButton = view[GlyphTextView](R.id.likes_close_button)

  private lazy val likesView = view[RecyclerView](R.id.likes_recycler_view)

  private lazy val title = returning(view[TypefaceTextView](R.id.message_details_title)) { vh =>
    vh.foreach(_.setText(R.string.message_liked_title))
  }

  private lazy val timestamp = returning(view[TypefaceTextView](R.id.message_timestamp)) { vh =>
    message.onUi { msg =>

      val ts = SameDayTimeStamp(msg.time.instant).string
      val editTs = SameDayTimeStamp(msg.editTime.instant).string
      val text =
        s"${getString(R.string.message_details_sent)}: $ts" +
          (if (msg.editTime != RemoteInstant.Epoch) s"\n${getString(R.string.message_details_last_edited)}: $editTs" else "")
      vh.foreach(_.setText(text))
    }
  }

  private var readTimestamps = Map.empty[UserId, RemoteInstant]

  private def createSubtitle(user: UserData): String =
    readTimestamps.get(user.id).fold("")(time => SameDayTimeStamp(time.instant).string)

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_likes_and_reads, viewGroup, false)

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    title
    timestamp
    closeButton
    likesView

    likesView.foreach { rv =>
      rv.setLayoutManager(new LinearLayoutManager(getContext))
      convController.currentConv.currentValue.foreach{
         conversationData =>
           rv.setAdapter(new ParticipantsAdapter(likes, showPeopleOnly = true, showArrow = false, showSubtitle = conversationData.add_friend,from = ParticipantsAdapter.ReadAndLikes))
      }
    }

    closeButton.foreach(_.setOnClickListener(new OnClickListener {
      def onClick(v: View): Unit = onBackPressed()
    }))
  }

  override def onBackPressed(): Boolean = {
    Option(getActivity) match {
      case Some(proxyConversationActivity: ProxyConversationActivity) =>
        screenController.showMessageDetails ! None
        true
      case _ =>
        false
    }
  }
}

object LikesAndReadsFragment {

  val Tag: String = getClass.getSimpleName

  sealed trait ViewToDisplay

  case object NoReads extends ViewToDisplay

  case object ReadsOff extends ViewToDisplay

  case object NoLikes extends ViewToDisplay

  sealed trait Tab extends ViewToDisplay {
    val str: String
    val pos: Int
  }

  case object ReadsTab extends Tab {
    override val str: String = s"${classOf[LikesAndReadsFragment].getName}/reads"
    override val pos: Int = 0
  }

  case object LikesTab extends Tab {
    override val str: String = s"${classOf[LikesAndReadsFragment].getName}/likes"
    override val pos: Int = 1
  }

  object Tab {
    val tabs = List(ReadsTab, LikesTab)

    def apply(str: Option[String] = None): Tab = str match {
      case Some(LikesTab.str) => LikesTab
      case _ => ReadsTab
    }
  }

  sealed trait DetailsCombination

  case object JustLikes extends DetailsCombination

  case object JustReads extends DetailsCombination

  case object ReadsAndLikes extends DetailsCombination

  case object NoDetails extends DetailsCombination

  private val ArgPageToOpen: String = "ARG_PAGE_TO_OPEN"

  def newInstance(tabToOpen: Tab = ReadsTab): LikesAndReadsFragment =
    returning(new LikesAndReadsFragment) { f =>
      f.setArguments(returning(new Bundle) {
        _.putString(ArgPageToOpen, tabToOpen.str)
      })
    }

  def detailsCombination(message: MessageData, isOwnMessage: Boolean, isTeam: Boolean): DetailsCombination =
    (isOwnMessage, !message.isEphemeral && LikesController.isLikeable(message), isTeam) match {
      case (true, true, true) => ReadsAndLikes
      case (true, false, true) => JustReads
      case (_, true, _) => JustLikes
      case _ => NoDetails
    }
}

