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
package com.waz.zclient.messages

import android.content.Context
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, ViewGroup}
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.api.{IConversation, Message}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.RichOption
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageViewLayout.PartDesc
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.ConvUpdateSettingTypePartView
import com.waz.zclient.messages.parts.footer.FooterPartView
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.DateConvertUtils.asZonedDateTime
import com.waz.zclient.utils._
import com.waz.zclient.{BuildConfig, R, ViewHelper}

class MessageView(context: Context, attrs: AttributeSet, style: Int)
  extends MessageViewLayout(context, attrs, style) with ViewHelper {

  import MessageView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  protected val factory = inject[MessageViewFactory]
  private val selection = inject[ConversationController].messages
  private lazy val messageActions = inject[MessageActionsController]
  private lazy val assetsController = inject[AssetsController]

  private var msgId: MessageId = _
  var msg: MessageData = MessageData.Empty
  private var data: MessageAndLikes = MessageAndLikes.Empty

  private var hasFooter = false

  private val signalMsg = Signal[Option[MessageData]]
  val canReply: Signal[Boolean] = for {
    Some(m) <- signalMsg
    result <- messageActions.isSupportReply(m)
  } yield {
    result
  }
  canReply.onUi {
    _ =>
  }

  setClipChildren(false)
  setClipToPadding(false)

  this.onClick {
    if (clickableTypes.contains(msg.msgType))
      selection.toggleFocused(msgId)
  }

  this.onLongClick {
    if (longClickableTypes.contains(msg.msgType)) {
      msg.msgType match {
        case Message.Type.TEXTJSON =>
          if (MessageContentUtils.getSystemTextJsonNotificationConversationSubTitleRes(MessageContentUtils.getTextJsonContentType(msg.contentType.getOrElse(""))) > 0) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            messageActions.showDialog(data)
          } else {
            false
          }
        case _ =>
          performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
          messageActions.showDialog(data)
      }
    } else false
  }

  def set(mAndL: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions, adapter: MessagesPagedListAdapter): Unit = {
    val animateFooter = msgId == mAndL.message.id && hasFooter != shouldShowFooter(mAndL, opts)
    hasFooter = shouldShowFooter(mAndL, opts)
    data = mAndL
    msg = mAndL.message
    msgId = msg.id

    signalMsg.publish(Some(msg), Threading.Background)

    val isTextJson = msg.msgType == Message.Type.TEXTJSON
    val isOneToOne = opts.convType == IConversation.Type.ONE_TO_ONE
    val isSameSide = MessageView.latestIsSameSide(mAndL, prev, next, None, opts)

    var isReceiptNotRelativeSelf = false
    val forbidType = getforbidType(mAndL)

    val contentParts = {
      if(msg.msgType == Message.Type.MEMBER_JOIN && msg.firstMessage) {
        (if(msg.name.nonEmpty) Seq(PartDesc(ConversationStart)) else Seq.empty) ++
          (if(msg.members.nonEmpty) Seq(PartDesc(MemberChange)) else Seq.empty) ++
          (if(opts.canHaveLink) Seq(PartDesc(WirelessLink)) else Seq.empty)
      }else if(forbidType._1){
        if(forbidType._2) Seq(PartDesc(ForbidWithSelf)) else Seq(PartDesc(ForbidOther))
      }else if(isTextJson) {
        isReceiptNotRelativeSelf = false
        customMessageType(msg, isOneToOne)
      } else {
        val quotePart = (mAndL.quote, mAndL.message.quote) match {
          case (Some(quote), Some(qInfo)) /*if qInfo.validity*/ =>
            if(quote.isForbid) Seq(PartDesc(Reply(Unknown))) else Seq(PartDesc(Reply(quote.msgType)))
          //case (Some(_), Some(_))                               => Seq(PartDesc(Reply(Unknown))) // the quote is invalid
          case (None, Some(_))                                  => Seq(PartDesc(Reply(Unknown))) // the quote was deleted
          case _                                                => Seq[PartDesc]()
        }

        if(quotePart.nonEmpty) {
          quotePart
        } else {
          quotePart ++
            (if(msg.msgType == Message.Type.RICH_MEDIA) {
//              val contentWithOG = msg.content.filter(_.openGraph.isDefined)
//              if(contentWithOG.size == 1 && msg.content.size == 1)
//                msg.content.map(content => PartDesc(MsgPart(content.tpe), Some(content)))
//              else
//                Seq(PartDesc(MsgPart(Message.Type.TEXT, isOneToOne))) ++ contentWithOG.map(content => PartDesc(MsgPart(content.tpe), Some(content))).filter(_.tpe == WebLink)
              if (msg.content.size > 1) {
                val s = Seq(PartDesc(MsgPart(Message.Type.TEXT, MsgPart.Text, isOneToOne))) ++ (msg.content map {
                  content =>
                    PartDesc(MsgPart(content.tpe), Some(content))
                }).filter(_.tpe == WebLink)
                s
              } else {
                msg.content map {
                  content =>
                    val desc = PartDesc(MsgPart(content.tpe), Some(content))
                    desc
                }
              }
            }
            else Seq(PartDesc(MsgPart(msg.msgType, isOneToOne))))
        }
      }
    }.filter { partDesc =>
      if(msg.isServerNotification) {
        if(isSystemTextJsonNotificationPartDesc(partDesc)) {
          partDesc.tpe != MsgPart.Empty
        } else {
          false
        }
      } else {
        partDesc.tpe != MsgPart.Empty
      }
    }

    if(isReceiptNotRelativeSelf) {
      setVisibility(android.view.View.GONE)
      setPadding(0, 0, 0, 0)
      setParts(mAndL, prev, next, Seq.empty, opts, adapter)
    }else {
      setVisibility(android.view.View.VISIBLE)
      val parts =
        if(!BuildConfig.DEBUG && msg.msgType != Message.Type.RECALLED && contentParts.forall(_.tpe == MsgPart.Unknown)) Nil // don't display anything for unknown message
        else {
          val builder = Seq.newBuilder[PartDesc]

          getSeparatorType(msg, prev, opts.isFirstUnread).foreach(sep => builder += PartDesc(sep))

          if(MessageView.shouldShowPartofChathead(opts.convType, prev, msg, opts.isSelf)){
            builder += PartDesc(MsgPart.User)
          }

          if(shouldShowInviteBanner(msg, opts)) {
            builder += PartDesc(MsgPart.InviteBanner)
          }

          builder ++= contentParts

          if(msg.isEphemeral) {
            builder += PartDesc(MsgPart.EphemeralDots)
          }

          if(msg.msgType == Message.Type.ASSET && !areDownloadsAlwaysEnabled)
            builder += PartDesc(MsgPart.WifiWarning)

          if(hasFooter || animateFooter) {
            if(isTextJson) {
              val childMsgType = MessageContentUtils.getTextJsonContentType(msg.contentType.getOrElse(""))
              if(MessageContentUtils.isShouldShowFooterTextJson(childMsgType)) builder += PartDesc(MsgPart.Footer)
            } else {
              builder += PartDesc(MsgPart.Footer)
            }
          }

          builder.result()
        }
      val (top, bottom) = if(parts.isEmpty) (0, 0) else getPaddingTopBottom(data, isSameSide._1, prev.map(_.msgType), next.map(_.msgType), parts.head.tpe, parts.last.tpe, isOneToOne)
      setPadding(0, top, 0, bottom)
      setParts(mAndL, prev, next, parts, opts, adapter)

      if(animateFooter)
        getFooter foreach { footer =>
          if(hasFooter) footer.slideContentIn()
          else footer.slideContentOut()
        }
    }
  }


  private def getforbidType(mAndL: MessageAndLikes) = {
    val isForbid = mAndL.isForbid
    val selfId = SpUtils.getUserId(context)
    val isForbidWithSelf = mAndL.message.userId.str.equalsIgnoreCase(selfId)
    (isForbid,isForbidWithSelf)
  }

  private def customMessageType(message: MessageData, isOneToOne: Boolean): Seq[MessageViewLayout.PartDesc] = {
    val childMsgType = MessageContentUtils.getTextJsonContentType(message.contentType.getOrElse(""))
    childMsgType match {
      case MessageContentUtils.INVALID_TYPE             =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_Display, isOneToOne)))
      case MessageContentUtils.INVALID_TYPE_EXCEPTION   =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_Display, isOneToOne)))
      case MessageContentUtils.GROUP_PARTICIPANT_INVITE =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_Group_Participant_Invite, isOneToOne)))
      case MessageContentUtils.EMOJI_GIF =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_EmojiGifPart, isOneToOne)))
      case MessageContentUtils.SCREEN_SHOT            =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_Screen_Shot, isOneToOne)))
      case _ =>
        Seq(PartDesc(MsgPart(Message.Type.TEXTJSON, MsgPart.TextJson_Display, isOneToOne)))
    }
  }

  def areDownloadsAlwaysEnabled = assetsController.downloadsAlwaysEnabled.currentValue.contains(true)

  def isFooterHiding = !hasFooter && getFooter.isDefined

  def isEphemeral = msg.isEphemeral

  private def getSeparatorType(msg: MessageData, prev: Option[MessageData], isFirstUnread: Boolean): Option[MsgPart] = msg.msgType match {
    case Message.Type.CONNECT_REQUEST | Message.Type.UPDATE_SETTING => None
    case _                                                          =>
      if(msg.isServerNotification) {
        None
      } else {
        prev.fold2(None, { p =>
          val prevDay = asZonedDateTime(p.time.instant).toLocalDate.atStartOfDay()
          val curDay = asZonedDateTime(msg.time.instant).toLocalDate.atStartOfDay()

          val isBeforeDay = prevDay.isBefore(curDay)
          if(isBeforeDay) Some(SeparatorLarge)
          //else if (p.time.isBefore(msg.time - 1800.seconds) || isFirstUnread) Some(Separator)
          else None
        })
      }
  }

  private def systemMessage(m: MessageData) = {
    import Message.Type._
    m.isSystemMessage || (m.msgType match {
      case OTR_DEVICE_ADDED | OTR_UNVERIFIED | OTR_VERIFIED | STARTED_USING_DEVICE | OTR_MEMBER_ADDED | MESSAGE_TIMER => true
      case _ => false
    })
  }

  private def shouldShowInviteBanner(msg: MessageData, opts: MsgBindOptions) =
    opts.position == 0 && msg.msgType == Message.Type.MEMBER_JOIN && (opts.convType == IConversation.Type.GROUP || opts.convType == IConversation.Type.THROUSANDS_GROUP)

  private def shouldShowFooter(mAndL: MessageAndLikes, opts: MsgBindOptions): Boolean = {
    val itemMessage = mAndL.message
    !systemMessage(itemMessage) && (mAndL.likes.nonEmpty ||
      selection.isFocused(itemMessage.id) ||
      (opts.isLastSelf && itemMessage.msgType != Message.Type.HISTORY_LOST) ||
      itemMessage.state == Message.Status.FAILED || itemMessage.state == Message.Status.FAILED_READ)
  }

  def getFooter = listParts.lastOption.collect { case footer: FooterPartView => footer }
}

object MessageView extends DerivedLogTag{

  import Message.Type._

  def isSystemTextJsonNotificationPartDesc(partDesc: PartDesc): Boolean = partDesc != null && {
    partDesc.tpe match {
      case TextJson_Display
      => true
      case _ => false
    }
  }

  def isSupportHeadMessage(messageData: MessageData): Boolean = {
    messageData.msgType match {
      case Message.Type.TEXTJSON =>
        val childType = MessageContentUtils.getTextJsonContentType(messageData.contentType.getOrElse(""))
        MessageContentUtils.maybeShowChatHead(childType)
      case Message.Type.TEXT |
           Message.Type.ASSET |
           Message.Type.ANY_ASSET |
           Message.Type.AUDIO_ASSET |
           Message.Type.VIDEO_ASSET |
           Message.Type.LOCATION |
           Message.Type.RICH_MEDIA |
           Message.Type.TEXT_EMOJI_ONLY =>
        true
      case _ => false
    }
  }

  def isEditedMsg(m: MessageData): Boolean = {
    m.editTime != RemoteInstant.Epoch
  }

  def isDeletedMsg(m: MessageData): Boolean = {
    m.msgType == Message.Type.RECALLED
  }

  def shouldShowChatheadAllChilds(conType: IConversation.Type, prev: Option[MessageData], msg: MessageData, isSelf: Boolean): Boolean = {
    val currSupportHead = isSupportHeadMessage(msg)
    val recalled = msg.msgType == Message.Type.RECALLED
    val knock = msg.msgType == Message.Type.KNOCK
    val isGroup = (conType == IConversation.Type.GROUP || conType == IConversation.Type.THROUSANDS_GROUP)
    val preIsNotSameUser = prev.isEmpty || prev.head.userId != msg.userId
    val preIsNotNormalMsg = preIsNotNormalMessage(prev)

    (!knock && !recalled && !systemMessage(msg) && !isSelf && isGroup && currSupportHead && preIsNotSameUser) ||
      (!knock && !recalled && !systemMessage(msg) && !isSelf && isGroup && currSupportHead && preIsNotNormalMsg)
  }

  def shouldShowPartofChathead(conType: IConversation.Type, prev: Option[MessageData], msg: MessageData, isSelf: Boolean): Boolean = {
    val currSupportHead = isSupportHeadMessage(msg)
    val recalled = msg.msgType == Message.Type.RECALLED
    val knock = msg.msgType == Message.Type.KNOCK
    val isGroup = conType == IConversation.Type.GROUP || conType == IConversation.Type.THROUSANDS_GROUP
    val preIsNotSameUser = prev.isEmpty || prev.head.userId != msg.userId
    //val isText = msg.msgType == Message.Type.TEXT || msg.msgType == Message.Type.TEXT_EMOJI_ONLY
    val currentIsNotNormalMsg = preIsNotNormalMessage(Some(msg))
    val preIsNotNormalMsg = preIsNotNormalMessage(prev)
    isEditedMsg(msg) || isDeletedMsg(msg) || (
      (!knock && !recalled && !systemMessage(msg) && !isSelf && isGroup && currSupportHead && preIsNotSameUser) ||
        (!knock && !recalled && !systemMessage(msg) && !isSelf && isGroup && currSupportHead && preIsNotNormalMsg)
      )
  }

  def shouldShowChatheadAllChildMargin(conType: IConversation.Type, msg: MessageData, isSelf: Boolean, prev: Option[MessageData]): Boolean = {
//    val recalled = msg.msgType == Message.Type.RECALLED
//    val knock = msg.msgType == Message.Type.KNOCK
    val isGroup = (conType == IConversation.Type.GROUP || conType == IConversation.Type.THROUSANDS_GROUP)
    (isGroup && !isSelf && !systemMessage(msg))
  }

  def preIsNotNormalMessage(prev: Option[MessageData]): Boolean = {
    prev.nonEmpty &&
      prev.head.msgType != Message.Type.TEXT &&
      prev.head.msgType != Message.Type.TEXTJSON &&
      prev.head.msgType != Message.Type.TEXT_EMOJI_ONLY &&
      prev.head.msgType != Message.Type.ANY_ASSET &&
      prev.head.msgType != Message.Type.VIDEO_ASSET &&
      prev.head.msgType != Message.Type.LOCATION &&
      prev.head.msgType != Message.Type.RICH_MEDIA &&
      prev.head.msgType != Message.Type.AUDIO_ASSET &&
      prev.head.msgType != Message.Type.ASSET

  }

  def latestIsSameSide(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): (Boolean, Boolean) =
    latestIsSameSide(msg, prev, next, None, opts)

  def latestIsSameSide(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: MsgBindOptions): (Boolean, Boolean) = {
    def isNormalSameUser(msg1: MessageData, msg2: MessageData): Boolean = {
      msg1.userId.str.toLowerCase.equals(msg2.userId.str.toLowerCase)
    }

    def getChildMsgType(msg: MessageData): String = {
      if (msg.msgType == Message.Type.TEXTJSON) {
        MessageContentUtils.getTextJsonContentType(msg.contentType.getOrElse(""))
      } else {
        null
      }
    }

    (if (prev.isEmpty) {
      false
    } else {
      val latestMsg = prev.get
      val latestChildType = getChildMsgType(latestMsg)
      val currChildType = getChildMsgType(msg.message)

      latestChildType match {
        case MessageContentUtils.SCREEN_SHOT=> false
        case _ =>
          currChildType match {
            case MessageContentUtils.SCREEN_SHOT=> false
            case _ => isNormalSameUser(latestMsg, msg.message)
          }
      }
    }, if (next.isEmpty) {
      false
    } else {
      val latestMsg = next.get
      val latestChildType = getChildMsgType(latestMsg)
      val currChildType = getChildMsgType(msg.message)

      latestChildType match {
        case MessageContentUtils.SCREEN_SHOT=> false
        case _ =>
          currChildType match {
            case MessageContentUtils.SCREEN_SHOT=> false
            case _ => isNormalSameUser(latestMsg, msg.message)
          }
      }
    })
  }


  def systemMessage(m: MessageData) = {

    import Message.Type._

    m.isSystemMessage || (m.msgType match {
      case OTR_DEVICE_ADDED | OTR_UNVERIFIED | OTR_VERIFIED | STARTED_USING_DEVICE | OTR_MEMBER_ADDED => true
      case _ => false
    })
  }

  //  val clickableTypes = Set(
  //    TEXT,
  //    TEXT_EMOJI_ONLY,
  //    ANY_ASSET,
  //    ASSET,
  //    AUDIO_ASSET,
  //    VIDEO_ASSET,
  //    LOCATION,
  //    RICH_MEDIA
  //  )
  //
  //  val longClickableTypes = clickableTypes ++ Set(
  //    KNOCK
  //  )

  val clickableTypes = Set(
    TEXT,
    TEXTJSON,
    TEXT_EMOJI_ONLY,
    ANY_ASSET,
    ASSET,
    AUDIO_ASSET,
    VIDEO_ASSET,
    LOCATION,
    RICH_MEDIA
  )

  val longClickableTypes = Set(
    TEXT,
    TEXTJSON,
    TEXT_EMOJI_ONLY,
    ANY_ASSET,
    ASSET,
    AUDIO_ASSET,
    VIDEO_ASSET,
    LOCATION,
    RICH_MEDIA,
    KNOCK
  )


  val GenericMessage = 0

  def viewType(tpe: Message.Type): Int = tpe match {
    case _ => GenericMessage
  }

  def apply(parent: ViewGroup, tpe: Int): MessageView = tpe match {
    case _ => ViewHelper.inflate[MessageView](R.layout.message_view, parent, addToParent = false)
  }

  trait MarginRule

  case object TextLike extends MarginRule

  case object SeparatorLike extends MarginRule

  case object ImageLike extends MarginRule

  case object FileLike extends MarginRule

  case object SystemLike extends MarginRule

  case object Ping extends MarginRule

  case object MissedCall extends MarginRule

  case object InviteBanner extends MarginRule

  case object Other extends MarginRule

  case object ConvUpdateSettingsRule extends MarginRule

  case object TextJson_TextJson_Display_MarginRule extends MarginRule

  case object TextJson_Screen_Shot_MarginRule extends MarginRule

  case object TextJson_Notification_USER_CLIENT_ID_In_MarginRule extends MarginRule

  case object TextJson_Group_Participant_Invite_MarginRule extends MarginRule

  case object TextJson_Server_Notification_MarginRule extends MarginRule

  case object TextJson_emojiGifPart_MarginRule extends  MarginRule

  object MarginRule {
    def apply(tpe: Message.Type, msgPartForTextJson: MsgPart, isOneToOne: Boolean): MarginRule = apply(MsgPart(tpe, msgPartForTextJson, isOneToOne))

    def apply(tpe: MsgPart): MarginRule = {
      tpe match {
        case TextJson_Screen_Shot => TextJson_Screen_Shot_MarginRule
        case TextJson_Group_Participant_Invite => TextJson_Group_Participant_Invite_MarginRule
        case TextJson_Display => TextJson_TextJson_Display_MarginRule
        case TextJson_EmojiGifPart => TextJson_emojiGifPart_MarginRule
        case Separator |
             SeparatorLarge |
             User |
             Text |
             Reply(_) |
             ForbidWithSelf
        => TextLike
        case MsgPart.Ping => Ping
        case FileAsset |
             AudioAsset |
             WebLink |
             YouTube |
             Location |
             SoundMedia => FileLike
        case Image | VideoAsset => ImageLike
        case MsgPart.OtrMessage |
             MsgPart.Rename |
             MsgPart.InviteMembersType |
             MsgPart.ChangeConversationType |
             ConversationStart |
             MessageTimer |
             ReadReceipts |
             ConnectRequest |
             MsgPart.ConvUpdateSettingSingleType |
             MsgPart.ForbidOther |
             MsgPart.StartedUsingDevice |
             MsgPart.ConvMsgEditVerify
        => SystemLike
        case MsgPart.ConvUpdateSettingType =>
          ConvUpdateSettingsRule
        case MsgPart.MissedCall => MissedCall
        case MsgPart.InviteBanner => InviteBanner
        case MsgPart.MemberChange => Other
        case _ => Other
      }
    }
  }

  def getPaddingTopBottom(data: MessageAndLikes, preIsSameSide: Boolean, prevTpe: Option[Message.Type], nextTpe: Option[Message.Type], topPart: MsgPart, bottomPart: MsgPart, isOneToOne: Boolean)(implicit context: Context): (Int, Int) = {
    val top =
      if(prevTpe.isEmpty) {
        MarginRule(topPart) match {
          case SystemLike =>
            24
          case _          =>
            0
        }
      } else {
        (MarginRule(prevTpe.get, topPart, isOneToOne), MarginRule(topPart)) match {
          case (TextLike, TextLike) | (_, TextLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (TextLike, FileLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (FileLike, FileLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (ImageLike, ImageLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (FileLike | ImageLike, _) | (_, FileLike | ImageLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (MissedCall, _) =>
            getHalfWhenIsSameSide(preIsSameSide, 16)
          case (SystemLike, _) | (_, SystemLike) =>
            getHalfWhenIsSameSide(preIsSameSide, 16) //24
          case (_, InviteBanner) =>
            if (nextTpe.isEmpty) {
              getHalfWhenIsSameSide(preIsSameSide, 16) //24
            } else {
              0
            }
          case (ConvUpdateSettingsRule, _) | (_, ConvUpdateSettingsRule) =>
            val contentStr = data.message.name.fold(data.message.contentString)(_.str)
            if(ConvUpdateSettingTypePartView.showSelf(contentStr)) 24
            else 0
          case (_, Ping) | (Ping, _) =>
            getHalfWhenIsSameSide(preIsSameSide, 16) //24
          case (_, MissedCall) =>
            getHalfWhenIsSameSide(preIsSameSide, 16) //24
          case (_, TextJson_Group_Participant_Invite_MarginRule) | (TextJson_Group_Participant_Invite_MarginRule, _)
               | (_, TextJson_TextJson_Display_MarginRule) | (TextJson_TextJson_Display_MarginRule, _)
               | (_, TextJson_emojiGifPart_MarginRule) | (TextJson_emojiGifPart_MarginRule, _)
          =>
            getHalfWhenIsSameSide(preIsSameSide, 16) //24
          case (_, TextJson_Screen_Shot_MarginRule) | (TextJson_Screen_Shot_MarginRule, _)=>
            8
          case _ =>
            0
        }
      }

    val bottom =
      if (nextTpe.isEmpty)
        MarginRule(bottomPart) match {
          case SystemLike =>
            24
          case _ =>
            0
        }
      else
        0

    (toPx(top), toPx(bottom))
  }

  def getHalfWhenIsSameSide(isSameSide: Boolean, height: Int): Int = {
    if (isSameSide) {
      height / 2
    } else {
      height
    }
  }

  // Message properties calculated while binding, may not be directly related to message state,
  // should not be cached in message view as those can be valid only while set method is called
  case class MsgBindOptions(position: Int,
                            isSelf: Boolean,
                            isLast: Boolean,
                            isLastSelf: Boolean, // last self message in conv
                            isFirstUnread: Boolean,
                            listDimensions: Dim2,
                            isGroup: Boolean,
                            teamId: Option[TeamId],
                            canHaveLink: Boolean,
                            selfId: Option[UserId],
                            convType: IConversation.Type)

}



