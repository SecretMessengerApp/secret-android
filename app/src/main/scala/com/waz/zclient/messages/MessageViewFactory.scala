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

import android.view.{View, ViewGroup}
import android.widget.LinearLayout
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.R
import com.waz.zclient.ViewHelper._
import com.waz.zclient.log.LogUI._

import scala.collection.mutable

class MessageViewFactory extends DerivedLogTag {

  val DefaultLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

  private val cache = new mutable.HashMap[MsgPart, mutable.Stack[MessageViewPart]]

  private val viewCache = new mutable.HashMap[Int, mutable.Stack[View]]

  def recycle(part: MessageViewPart): Unit = {
    verbose(l"recycling part: ${part.tpe}")
    cache.getOrElseUpdate(part.tpe, new mutable.Stack[MessageViewPart]()).push(part)
  }

  def get(tpe: MsgPart, parent: ViewGroup): MessageViewPart = {
    cache.get(tpe).flatMap(s => if(s.isEmpty) None else Some(s.pop())).getOrElse {
      verbose(l"there was no cached $tpe, building a new one")
      import MsgPart._
      tpe match {
        case User => inflate(R.layout.message_user, parent, false)
        case Separator => inflate(R.layout.message_separator, parent, false)
        case SeparatorLarge => inflate(R.layout.message_separator_large, parent, false)
        case Footer => inflate(R.layout.message_footer, parent, false)
        case Text => inflate(R.layout.message_text, parent, false)
        case Ping => inflate(R.layout.message_ping, parent, false)
        case Rename => inflate(R.layout.message_rename, parent, false)
        case Image => inflate(R.layout.message_image, parent, false)
        case YouTube => inflate(R.layout.message_youtube, parent, false)
        case WebLink => inflate(R.layout.message_link_preview, parent, false)
        case FileAsset => inflate(R.layout.message_file_asset, parent, false)
        case AudioAsset => inflate(R.layout.message_audio_asset, parent, false)
        case VideoAsset => inflate(R.layout.message_video_asset, parent, false)
        case Location => inflate(R.layout.message_location, parent, false)
        case MemberChange => inflate(R.layout.message_member_change, parent, false)
        case ReadReceipts => inflate(R.layout.message_readreceipts, parent, false)
        case ConnectRequest => inflate(R.layout.message_connect_request, parent, false)
        case ConversationStart => inflate(R.layout.message_conversation_start, parent, false)
        /*case WirelessLink => inflate(R.layout.message_wireless_link, parent, false)*/
        case SoundMedia => inflate(R.layout.message_soundmedia, parent, false)
        case MissedCall => inflate(R.layout.message_missed_call, parent, false)
        case EphemeralDots => inflate(R.layout.message_ephemeral_dots_view, parent, false)
        case WifiWarning => inflate(R.layout.message_wifi_warning, parent, false)
        case MessageTimer => inflate(R.layout.message_msg_timer_changed, parent, false)
        case OtrMessage => inflate(R.layout.message_otr_part, parent, false)
        case TextJson_Group_Participant_Invite=> inflate(R.layout.message_group_participant_invite, parent, false)
        case TextJson_Display => inflate(R.layout.message_text_display_textjson, parent, false)
        case InviteMembersType => inflate(R.layout.message_invite_member, parent, false)
        case ConvUpdateSettingType => inflate(R.layout.message_update_setting, parent, false)
        case ConvUpdateSettingSingleType => inflate(R.layout.message_update_setting_single, parent, false)
        case ChangeConversationType => inflate(R.layout.message_change_type, parent, false)
        case ForbidWithSelf => inflate(R.layout.message_forbid_with_self, parent, false)
        case ForbidOther => inflate(R.layout.message_forbid_other, parent, false)
        case TextJson_EmojiGifPart =>inflate(R.layout.message_emoji_gif, parent, false)
        case Reply(Text) => inflate(R.layout.message_reply_text, parent, false)
        case Reply(Image) => inflate(R.layout.message_reply_image, parent, false)
        case Reply(Location) => inflate(R.layout.message_reply_location, parent, false)
        case Reply(VideoAsset) => inflate(R.layout.message_reply_video, parent, false)
        case Reply(FileAsset) => inflate(R.layout.message_reply_file, parent, false)
        case Reply(AudioAsset) => inflate(R.layout.message_reply_audio, parent, false)
        case Reply(Unknown) => inflate(R.layout.message_reply_unknown, parent, false)
        case Reply(_) => new EmptyPartView(parent.getContext)
        case Empty => new EmptyPartView(parent.getContext)
        case StartedUsingDevice => inflate(R.layout.message_started_using_device, parent, false)
        case TextJson_Screen_Shot => inflate(R.layout.message_screen_shot_notify, parent, false)
        case ConvMsgEditVerify => inflate(R.layout.message_conv_single_edit_verify, parent, false)
        case Unknown => new EmptyPartView(parent.getContext) // TODO: display error msg, only used in internal
        case _ => new EmptyPartView(parent.getContext) // TODO: display error msg, only used in internal
      }
    }
  }

  def recycle(view: View, resId: Int): Unit = {
    verbose(l"recycling view: $resId")
    viewCache.getOrElseUpdate(resId, new mutable.Stack[View]()).push(view)
  }

  def get[A <: View](resId: Int, parent: ViewGroup): A =
    viewCache.get(resId).flatMap(s => if (s.isEmpty) None else Some(s.pop().asInstanceOf[A])).getOrElse {
      inflate[A](resId, parent, addToParent = false)
    }

}
