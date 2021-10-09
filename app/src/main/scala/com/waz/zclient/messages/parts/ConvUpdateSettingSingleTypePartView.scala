/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.model.ConversationUpdateSettingModel
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils.{SpUtils, StringUtils, UiStorage, UserVectorSignal}
import com.waz.zclient.{R, ViewHelper}
import org.json.JSONObject

/**
 * Created by eclipse on 2019/1/24.
 */
class ConvUpdateSettingSingleTypePartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  implicit lazy val uiStorage = inject[UiStorage]
  override val tpe = MsgPart.ConvUpdateSettingSingleType

  setOrientation(LinearLayout.VERTICAL)

  inflate(R.layout.message_update_settings)

  val contentView: TextView = findById(R.id.ttv_update_content)

  private val sendUserBlockedUserIds = Signal[Vector[UserId]]

  private val sendUserBlockedUsers = Signal(zms, sendUserBlockedUserIds).flatMap {
    case (z, ids) => UserVectorSignal(ids)
    case _        => Signal const Vector.empty[UserData]
  }

  sendUserBlockedUsers.onUi { users =>
    messageAndLikes.currentValue.foreach { messageAndLikes =>
      users.find(_.id == messageAndLikes.message.userId).foreach { sendUser =>
        users.find(_.id == block_user).foreach { blockUser =>
          setText(sendUser, blockUser)
        }
      }
    }
  }

  private var block_user: UserId = _
  private var blockEndTime: Long = _
  private var blockDuration: Int = _
  private var dataModel: ConversationUpdateSettingModel = _

  def setText(sendUser: UserData, blockUser: UserData): Unit = {
    if(SpUtils.getUserId(getContext).equalsIgnoreCase(sendUser.id.str)) {
      val blockName = blockUser.getDisplayName
      if(blockEndTime == -1) {
        contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_open2, blockName))
      } else if(blockEndTime == 0) {
        contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_close2, blockName))
      } else {
        contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_params2, blockName, formatTime(blockDuration)))
      }
    } else {
      if (convController.currentUserIsGroupManager(sendUser.id).currentValue.get) {
        if (blockEndTime == -1) {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_open3, getResources.getString(R.string.conversation_setting_you)))
        } else if (blockEndTime == 0) {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_close3, getResources.getString(R.string.conversation_setting_you)))
        } else {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_params3, getResources.getString(R.string.conversation_setting_you), formatTime(blockDuration)))
        }
      } else {
        if (blockEndTime == -1) {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_open, getResources.getString(R.string.conversation_setting_you)))
        } else if (blockEndTime == 0) {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_close, getResources.getString(R.string.conversation_setting_you)))
        } else {
          contentView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_params, getResources.getString(R.string.conversation_setting_you), formatTime(blockDuration)))
        }
      }
    }
  }

  private def formatTime(time: Long): String = {
    var forbiddenSecond = time

    val stringBuilder = new StringBuilder()

    val days = forbiddenSecond / DAY_SECOND
    if(days > 0) {
      stringBuilder.append(days)
      stringBuilder.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_day))
      forbiddenSecond = forbiddenSecond % DAY_SECOND
    }

    val hours = forbiddenSecond / HOUR_SECOND
    if(hours > 0) {
      stringBuilder.append(hours)
      stringBuilder.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_hour))
      forbiddenSecond = forbiddenSecond % HOUR_SECOND
    }

    val minutes = forbiddenSecond / MINUTE_SECOND
    if(minutes > 0) {
      val surplusSecond = forbiddenSecond % MINUTE_SECOND
      stringBuilder.append(if(surplusSecond > 0) minutes + 1 else minutes)
      stringBuilder.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_minute))
    }

    stringBuilder.toString()
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    var contentString = msg.message.name.getOrElse(Name("")).str

    if(StringUtils.isBlank(contentString) || !contentString.contains("block_user")){
      contentString = msg.message.content.headOption.fold("")(_.content)
    }

    if(!StringUtils.isBlank(contentString)){
        try {
          val optModel = new JSONObject(contentString)
          block_user = UserId(optModel.optString("block_user"))
          blockEndTime = optModel.optLong("block_time")
          blockDuration = optModel.optInt("block_duration")
          sendUserBlockedUserIds ! Vector(msg.message.userId, block_user)
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
    }

  }

  private val MINUTE_SECOND = 60
  private val HOUR_SECOND = MINUTE_SECOND * 60
  private val DAY_SECOND = HOUR_SECOND * 24

}
