/**
 * Secret
 * Copyright (C) 2021 Secret
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

import java.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.View.OnClickListener
import android.widget.{LinearLayout, RelativeLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.httpapi.{NormalServiceAPI, OnHttpListener}
import com.jsy.common.model.SingleEditVerifyMode
import com.jsy.common.utils.MessageUtils
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.{BaseActivity, R, ViewHelper}
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.utils.StringUtils
import org.json.{JSONException, JSONObject}

class ConvMsgEditVerifyPartView(context: Context, attrs: AttributeSet, style: Int)
  extends RelativeLayout(context, attrs, style)
    with MessageViewPart
    with ViewHelper
    with DerivedLogTag {
  implicit lazy val executionContext = Threading.Background

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.ConvMsgEditVerify
  private lazy val convController = inject[ConversationController]

  inflate(R.layout.message_conv_edit_verify_content)
  val contentText: TextView = findById(R.id.conv_msg_edit_verify_text)
  val verifyLayout: LinearLayout = findById(R.id.conv_msg_edit_verify_layout)
  val agreeBtn: TextView = findById(R.id.conv_msg_edit_agree_btn)
  val rejectBtn: TextView = findById(R.id.conv_msg_edit_reject_btn)
  val revokeLayout: LinearLayout = findById(R.id.conv_msg_edit_revoke_layout)
  val revokeBtn: TextView = findById(R.id.conv_msg_edit_revoke_btn)

  agreeBtn.setOnClickListener(new OnClickListener {

    override def onClick(v: View): Unit = {
      if (null != v.getTag && v.getTag.isInstanceOf[String]) {
        val msgType = v.getTag.asInstanceOf[String]
        val operateType: String = if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE
        } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE
        } else {
          ""
        }
        singleMsgEditReply(messageId, msgType, operateType)
      }
    }
  })

  rejectBtn.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if (null != v.getTag && v.getTag.isInstanceOf[String]) {
        val msgType = v.getTag.asInstanceOf[String]
        val operateType: String = if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT
        } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT
        } else {
          ""
        }
        singleMsgEditReply(messageId, msgType, operateType)
      }
    }
  })

  revokeBtn.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {
      if (null != v.getTag && v.getTag.isInstanceOf[String]) {
        val msgType = v.getTag.asInstanceOf[String]
        val operateType: String = if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE
        } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE.equals(msgType)) {
          MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE
        } else {
          ""
        }
        singleMsgEditReply(messageId, msgType, operateType)
      }
    }
  })

  private var messageId: MessageId = _
  private var model: SingleEditVerifyMode = _
  private var isSelf: Boolean = false

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    verifyLayout.setVisibility(View.VISIBLE)
    messageId = msg.message.id
    opts.map(_.isSelf).foreach { it =>
      isSelf = it
      updateContentState(msg.message.contentType, msg.message.contentString)
    }
  }

  def updateContentState(contentType: Option[String], messgeContent: String): Unit = {
    verbose(l"updateContentState isSelf:${isSelf}, contentType:$contentType, messgeContent:$messgeContent")
    verifyLayout.setVisibility(View.GONE)
    //        revokeLayout.setVisibility(View.GONE)
    revokeBtn.setTag("")
    agreeBtn.setTag("")
    rejectBtn.setTag("")
    model = SingleEditVerifyMode.parseJson(messgeContent)
    val content = if (null != model) {
      val you = getResources.getString(R.string.content__system__you)
      val other = getResources.getString(R.string.conversation_edit_single_other)
      val edit = getResources.getString(R.string.conversation_detail_settings_edit_msg_settings)
      val msgType = model.msgType
      if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_open, you, edit)
        } else {

          updateConvMsgEdit(true)
          getResources.getString(R.string.conversation_edit_single_open, other, edit)
        }
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_open_cancel, you, edit)
        } else {
          getResources.getString(R.string.conversation_edit_single_open_cancel, other, edit)
        }
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE.equals(msgType)) {
        getResources.getString(R.string.conversation_edit_single_open_agree, edit)
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_open_reject, you, edit)
        } else {
          getResources.getString(R.string.conversation_edit_single_open_reject, other, edit)
        }
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_close, you, edit)
        } else {
          verifyLayout.setVisibility(View.VISIBLE)
          agreeBtn.setTag(msgType)
          rejectBtn.setTag(msgType)
          getResources.getString(R.string.conversation_edit_single_close_sure, other, edit)
        }
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_close_cancel, you, edit)
        } else {
          getResources.getString(R.string.conversation_edit_single_close_cancel, other, edit)
        }
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE.equals(msgType)) {
        getResources.getString(R.string.conversation_edit_single_close_agree, edit)
      } else if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT.equals(msgType)) {
        if (isSelf) {
          getResources.getString(R.string.conversation_edit_single_close_reject, you, edit)
        } else {
          getResources.getString(R.string.conversation_edit_single_close_reject, other, edit)
        }
      } else {
        ""
      }
    } else {
      ""
    }
    if (StringUtils.isNotBlank(content)) {
      contentText.setText(content)
      contentText.setVisibility(View.VISIBLE)
    } else {
      contentText.setVisibility(View.GONE)
    }
  }

  def updateConvMsgEdit(enabled_edit_msg: Boolean) = {
    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData.enabled_edit_msg != enabled_edit_msg) {
        convController.updateConvMsgEdit(conversationData.id, enabled_edit_msg).foreach { c =>
          c.foreach { conv =>
            verbose(l"updateConvMsgEdit updateConvMsgEdit enabled_edit_msg:${conv.enabled_edit_msg}, conv:$conv")
          }
        }
      }
    }
  }

  def singleMsgEditReply(messageId: MessageId, msgType: String, operateType: String) = {
    if (StringUtils.isNotBlank(msgType) && StringUtils.isNotBlank(operateType)) {
      convController.currentConv.currentValue.foreach { conversationData =>
        //reqSingleMsgEditReply(conversationData.id, conversationData.remoteId, messageId, msgType, operateType)
        val msgData = new JSONObject
        val jsonContent = new JSONObject
        try {
          msgData.put("reply", msgType)
          jsonContent.put(MessageUtils.KEY_TEXTJSON_MSGTYPE, operateType)
          jsonContent.put(MessageUtils.KEY_TEXTJSON_MSGDATA, msgData)
        } catch {
          case e: JSONException =>
            e.printStackTrace()
        }
        val (tpe, ct) = MessageData.messageContentJson(jsonContent.toString, mentions = Seq.empty[Mention])

        convController.updateMessageCus(conversationData.id, messageId) { msg =>
          msg.copy(
            contentType = Option(msgType),
            content = ct,
            protos = Seq(GenericMessage(msg.id.uid, GenericContent.TextJson(jsonContent.toString)))
          )
        }.foreach {
          case Some(message) => ConvMsgEditVerifyPartView.this.post(new Runnable {
            override def run(): Unit = {
              updateContentState(message.contentType, message.contentString)
            }
          })
          case _             => error(l"singleMsgEditReply updateMessageCus :${jsonContent.toString}")
        }

        if (MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE.equals(operateType) || MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE.equals(operateType)) {
          convController.updateConvMsgEdit(conversationData.id, MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE.equals(operateType)).foreach { c =>
            c.foreach { conv =>
              verbose(l"singleMsgEditReply updateConvMsgEdit enabled_edit_msg:${conv.enabled_edit_msg}, conv:$conv")
            }
          }
        }
      }
    }
  }

  private def reqSingleMsgEditReply(convId: ConvId, remoteId: RConvId, messageId: MessageId, msgType: String, operateType: String): Unit = {
    if (StringUtils.isBlank(msgType) || StringUtils.isBlank(operateType)) {
      return
    }
    Option(getContext).filter(_.isInstanceOf[BaseActivity])
      .map(_.asInstanceOf[BaseActivity]).foreach(_.showProgressDialog())
    val isOpen = msgType.equals(MessageContentUtils.CONV_SINGLE_EDIT_VERIFY_OPEN)
    NormalServiceAPI.getInstance().reqSingleMsgEditReply(remoteId.str, isOpen, operateType, new OnHttpListener[String] {

      override def onFail(code: Int, err: String): Unit = {
        Option(getContext).filter(_.isInstanceOf[BaseActivity])
          .map(_.asInstanceOf[BaseActivity]).foreach(_.dismissProgressDialog())
      }

      override def onSuc(r: String, orgJson: String): Unit = {
        Option(getContext).filter(_.isInstanceOf[BaseActivity])
          .map(_.asInstanceOf[BaseActivity]).foreach(_.dismissProgressDialog())
      }

      override def onSuc(r: util.List[String], orgJson: String): Unit = {
        Option(getContext).filter(_.isInstanceOf[BaseActivity])
          .map(_.asInstanceOf[BaseActivity]).foreach(_.dismissProgressDialog())
      }
    })
  }
}
