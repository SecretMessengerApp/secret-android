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
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.jsy.common.model.ConversationUpdateSettingModel
import com.waz.model.{MessageContent, MessageData, Name}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.JsonDecoder._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart, UsersController}
import com.waz.zclient.utils.{SpUtils, StringUtils}
import com.waz.zclient.{R, ViewHelper}
import org.json.JSONObject

import scala.collection.JavaConverters._

/**
  * Created by eclipse on 2019/1/24.
  */
class ConvUpdateSettingTypePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.ConvUpdateSettingType

  import ConvUpdateSettingTypePartView._

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_update_settings)

  val users = inject[UsersController]
  var dataModel: ConversationUpdateSettingModel = _
  var optModel: JSONObject = _

  val contentView: TextView = findById(R.id.ttv_update_content)

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)


    var contentString = msg.message.name.getOrElse(Name("")).str
    if(StringUtils.isBlank(contentString) || !contentString.contains(OPT_ID)){
      contentString = msg.message.content.headOption.fold("")(_.content)
    }

    if (!StringUtils.isBlank(contentString)) {
      dataModel = new Gson().fromJson(contentString, classOf[ConversationUpdateSettingModel])
      optModel = new JSONObject(contentString)
    }

    if (dataModel != null) {
      val optId = dataModel.opt_id
      if (SpUtils.getUserId(getContext).equalsIgnoreCase(optId)) {
        setMessageContent(getResources.getString(R.string.conversation_setting_you), true)
      } else {
        setMessageContent("\"" + dataModel.opt_name + "\"", false)
      }


    }
  }

  private def setMessageContent(name: String, isOwn: Boolean) = {
    if (showSelf(optModel)) {
      if (optModel.has(URL_INVITE)) {
        if (optModel.optBoolean(URL_INVITE)) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_open_link_join, name))
        } else {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_close_link_join, name))
        }
      } else if (optModel.has(CONFIRM)) {
        if (optModel.optBoolean(CONFIRM)) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_open_confirm_join, name))
        } else {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_close_confirm_join, name))
        }
      } else if (optModel.has(ADDRIGHT)) {
        if (optModel.optBoolean(ADDRIGHT)) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_open_only_creator, name))
        } else {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_close_only_creator, name))
        }
      } else if (optModel.has(Viewmem)) {
        if (optModel.optBoolean(Viewmem)) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_open_show_mem, name))
        } else {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_close_show_mem, name))
        }
      } else if (optModel.has(Memberjoin_Confirm)) {
        if (optModel.optBoolean(Memberjoin_Confirm)) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_open_mem_confirm, name))
        } else {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_close_mem_confirm, name))
        }
      } else if (optModel.has(NEW_CREATOR)) {
        val newCreator = optModel.optString(NEW_CREATOR)
        val newName = optModel.optString(NEW_NAME)
        if (newCreator.equalsIgnoreCase(SpUtils.getUserId(getContext))) {
          contentView.setText(getContext.getResources.getString(R.string.conversation_setting_be_new_creator, getResources.getString(R.string.content__system__you)))
        } else {
          if (dataModel.opt_id.equalsIgnoreCase(SpUtils.getUserId(getContext))) {
            contentView.setText(getContext.getResources.getString(R.string.conversation_setting_change_new_creator, name, newName))
          } else {
            contentView.setText(getContext.getResources.getString(R.string.conversation_setting_be_new_creator, newName))
          }
        }
      } else if (optModel.has(Block_time)) {
        val blockTime = optModel.optLong(Block_time)
        contentView.setText(if (blockTime == -1L) {
          getResources.getString(R.string.conversation_detail_settings_forbidden_open, name)
        } else {
          getResources.getString(R.string.conversation_detail_settings_forbidden_close, name)
        })
      } else if (optModel.has(View_chg_mem_notify)) {
        val checked = optModel.optBoolean(View_chg_mem_notify)
        contentView.setText(if (checked) {
          getResources.getString(R.string.conversation_detail_settings_invite_visible_open, name)
        } else {
          getResources.getString(R.string.conversation_detail_settings_invite_visible_close, name)
        })
      } else if (optModel.has(Add_friend)) {
        val checked = optModel.optBoolean(Add_friend)
        contentView.setText(if (checked) {
          getResources.getString(R.string.conversation_detail_settings_add_friend_open, name)
        } else {
          getResources.getString(R.string.conversation_detail_settings_add_friend_close, name)
        })
      } else if (optModel.has(Manager_del)) {

        val manager = decodeManagerUserDataSeq('man_del)(optModel)
        manager.foreach { user =>
          if(SpUtils.getUserId(getContext).equalsIgnoreCase(user.id.get)){
            contentView.setText(getResources.getString(R.string.conversation_detail_item_del_group_manager, getResources.getString(R.string.conversation_setting_you)))
          }else{
            contentView.setText(getResources.getString(R.string.conversation_detail_item_del_group_manager, user.name.get))
          }
        }
      } else if (optModel.has(Manager_add)) {

        val manager = decodeManagerUserDataSeq('man_add)(optModel)
        manager.foreach { user =>
          if(SpUtils.getUserId(getContext).equalsIgnoreCase(user.id.get)){
            contentView.setText(getResources.getString(R.string.conversation_detail_item_add_group_manager, getResources.getString(R.string.conversation_setting_you)))
          }else{
            contentView.setText(getResources.getString(R.string.conversation_detail_item_add_group_manager, user.name.get))
          }
        }
      } else if(optModel.has(Msg_only_to_manager)){
        val msg_only_to_manager = optModel.optBoolean(Msg_only_to_manager)
        contentView.setText(if (msg_only_to_manager) {
          getResources.getString(R.string.conversation_detail_settings_msg_only_to_manager_open, name)
        } else {
          getResources.getString(R.string.conversation_detail_settings_msg_only_to_manager_close, name)
        })
      } else if (optModel.has(Enabled_edit_msg)) {
        val isEditMsg = optModel.optBoolean(Enabled_edit_msg)
        val formatStr = if (isOwn) name else getResources.getString(R.string.group_participant_user_row_creator)
        contentView.setText(if (isEditMsg) {
          getResources.getString(R.string.conversation_detail_settings_edit_msg_open, formatStr)
        } else {
          getResources.getString(R.string.conversation_detail_settings_edit_msg_close, formatStr)
        })
      } else if (optModel.has(Show_memsum)) {
        val isShowNum = optModel.optBoolean(Show_memsum)
        val formatStr = if (isOwn) name else getResources.getString(R.string.group_participant_user_row_creator)
        contentView.setText(if (isShowNum) {
          getResources.getString(R.string.conversation_detail_settings_show_memsum_open, formatStr)
        } else {
          getResources.getString(R.string.conversation_detail_settings_show_memsum_close, formatStr)
        })
      }
      else {
        //...
      }
      setVisibility(View.VISIBLE)
    } else {
      setVisibility(View.GONE)
    }

  }
}

object ConvUpdateSettingTypePartView {

  val OPT_ID  = "opt_id"
  val URL_INVITE = "url_invite"
  val CONFIRM = "confirm"
  val ADDRIGHT = "addright"
  val APPS = "apps"
  val APPS_DEL = "apps_del"
  val ASSETS = "assets"
  val NEW_CREATOR = "new_creator"
  val NEW_NAME = "new_name"
  val Viewmem = "viewmem"
  val Memberjoin_Confirm = "memberjoin_confirm"

  val Block_time = "block_time"
  val View_chg_mem_notify = "view_chg_mem_notify"
  val Add_friend = "add_friend"
  val Manager_del = "man_del"
  val Manager_add = "man_add"
  val Msg_only_to_manager = "msg_only_to_manager"
  val Enabled_edit_msg = "enabled_edit_msg"
  val Show_memsum = "show_memsum"

  def showSelf(js: String): Boolean = {
    try {
      val optModel: JSONObject = new JSONObject(js)
      showSelf(optModel)
    } catch {
      case e: Exception =>
        false
    }
  }

  def showSelf(optModel: JSONObject): Boolean = {
    optModel.keys().asScala.toSeq.intersect(Seq(URL_INVITE, CONFIRM, ADDRIGHT, Viewmem, Memberjoin_Confirm, NEW_CREATOR, Block_time, View_chg_mem_notify, Add_friend, Manager_del, Manager_add, Msg_only_to_manager, Enabled_edit_msg, Show_memsum)).nonEmpty
  }

}
