/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.waz.zclient.messages.parts

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.waz.api.IConversation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.returning
import com.waz.zclient.common.controllers.ThemeController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.{MessageViewPart, MsgPart, SystemMessageView}
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}

class InviteBannerPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.InviteBanner

  lazy val smv_header: SystemMessageView = findById(R.id.smv_header)

  private val themeController = inject[ThemeController]
  private val pickUserController = inject[IPickUserController]
  private lazy val convController = inject[ConversationController]

  lazy val showContactsButton: ZetaButton = findById[ZetaButton](R.id.zb__conversation__invite_banner__show_contacts)


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    showContactsButton.setIsFilled(themeController.isDarkTheme)
    smv_header.setIcon(R.drawable.red_alert)

    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData != null) {
        if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          smv_header.setText(getResources.getString(R.string.conversation_list_no_encrypte_notifications))
        } else {
          smv_header.setText(getResources.getString(R.string.conversation_list_encrypte_notifications))
        }
      }
    }

  }

}
