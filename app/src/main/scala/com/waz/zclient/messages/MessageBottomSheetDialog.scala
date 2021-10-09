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

package com.waz.zclient.messages

import android.content.Context
import com.jsy.common.model.EmojiGifModel
import com.jsy.common.utils.MD5Util
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.{AssetStatus, Message}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType.isGroupConv
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.emoji.utils.GifSavedDaoHelper
import com.waz.zclient.messages.MessageBottomSheetDialog.{Actions, MessageAction, Params}
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.participants.OptionsMenuController
import com.waz.zclient.participants.OptionsMenuController.MenuItem
import com.waz.zclient.utils.SpUtils

import scala.concurrent.ExecutionContext

class MessageBottomSheetDialog(message: MessageData,
                               params: Params,
                               operations: Seq[MessageAction] = Seq.empty)(implicit injector: Injector, context: Context, ec: EventContext)
  extends OptionsMenuController with Injectable with DerivedLogTag{

  lazy val zmessaging = inject[Signal[ZMessaging]]
  lazy val messageActionsController = inject[MessageActionsController]
  private lazy val convController = inject[ConversationController]

  override val title: Signal[Option[String]] = Signal.const(None)
  override val optionItems: Signal[Seq[OptionsMenuController.MenuItem]] =
    zmessaging.flatMap { zms =>
      val all = if (operations.isEmpty) Actions else operations
      for {
        isMsgEdit <- convController.currentConv.map(_.isGroupMsgEdit)
        allAction <- Signal.sequence(all.map { action =>
          action.enabled(message, zms, params,messageActionsController) map {
            case true =>
              if ((action == MessageAction.Edit || action == MessageAction.Delete) && (!isMsgEdit || !message.enabled_edit_msg)) {
                Option.empty[MessageAction]
              } else {
                Some(action)
              }
            case false => Option.empty[MessageAction]
          }
        }: _*).map(_.flatten)
      } yield {
        allAction
      }
//
//      Signal.sequence(all.map { action =>
//        action.enabled(message, zms, params, messageActionsController) map {
//          case true => if(!message.enabled_edit_msg && (action == MessageAction.Edit || action == MessageAction.Delete)) Option.empty[MessageAction] else Some(action)
//          case false => Option.empty[MessageAction]
//        }
//      } :_*).map(_.flatten)
    }

  override val onMenuItemClicked: SourceStream[OptionsMenuController.MenuItem] = EventStream()
  override val selectedItems: Signal[Set[OptionsMenuController.MenuItem]] = Signal.const(Set())

  onMenuItemClicked {
    case action: MessageAction =>
      messageActionsController.onMessageAction ! (action, message)
    case _ =>
  }
}


object MessageBottomSheetDialog {
  val CollectionExtra = "COLLECTION_EXTRA"
  val DelCollapsedExtra = "DEL_COLLAPSED_EXTRA"
  implicit val executionContext = ExecutionContext.Implicits.global
  // all possible actions
  val Actions = {
    import MessageAction._
    Seq(Copy, OpenFile, Edit, Like, Unlike, Reply, Details, Save, Forward, ForwardFriends
      , AddFavorite, RemoveFavorite, Delete, DeleteLocal, DeleteGlobal, Reveal, HideForbid, Translate, HideTranslate)
  }

  case class Params(collection: Boolean = false, delCollapsed: Boolean = true)

  def isMemberOfConversation(conv: ConvId, zms: ZMessaging) =
    zms.membersStorage.optSignal((zms.selfUserId, conv)) map (_.isDefined)

  def isAssetDataReady(asset: AssetId, zms: ZMessaging) =
    zms.assets.assetSignal(asset) map {
      case (_, AssetStatus.UPLOAD_DONE | AssetStatus.DOWNLOAD_DONE) => true
      case _ => false
    }

  def isLikedBySelf(msg: MessageId, zms: ZMessaging) =
    zms.reactionsStorage.optSignal((msg, zms.selfUserId)) map {
      case Some(liking) => liking.action == Liking.Action.Like
      case None => false
    }

  def isGroupManagerByMsg(id: MessageId, conv: ConvId, sendUserId: UserId, zms: ZMessaging) = {
    zms.convsStorage.optSignal(conv).map {
      case Some(data) if (isGroupConv(data.convType)) =>
        val manager: Seq[UserId] = data.manager
        val creator: UserId = data.creator
        val userId: UserId = zms.selfUserId
        !creator.str.equalsIgnoreCase(sendUserId.str) && !manager.exists(_.str.equalsIgnoreCase(sendUserId.str)) && (creator.str.equalsIgnoreCase(userId.str) || manager.exists(_.str.equalsIgnoreCase(userId.str)))
      case _ =>
        false
    }
  }

  def isForbidMsg(msg: MessageData): Boolean ={
    msg.isForbid
  }

  def isHideForbidByMsg(msg: MessageId, zms: ZMessaging) = {
    zms.forbidsStorage.optSignal((msg, ForbidData.Types.Forbid)) map {
      case Some(forbidData) =>
        LogUtils.i("MessageBottomSheetDialog", "forbidData:" + forbidData)
        forbidData.action == ForbidData.Action.Forbid
      case None => false
    }
  }

  //TODO: Remove glyphId
  abstract class MessageAction(val resId: Int, val glyphResId: Int, val stringId: Int) extends MenuItem {

    override val titleId: Int = stringId
    override val iconId: Option[Int] = Some(resId)
    override val colorId: Option[Int] = None

    def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean]
  }

  object MessageAction {
    import Message.Type._

    case object Forward extends MessageAction(R.string.glyph__share, R.string.glyph__share, R.string.message_bottom_menu_action_forward) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        if (msg.isEphemeral || isForbidMsg(msg)) Signal.const(false)
        else msg.msgType match {
          case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | ASSET =>
            // TODO: Once https://wearezeta.atlassian.net/browse/CM-976 is resolved, we should handle image asset like any other asset
            Signal.const(true)
          case ANY_ASSET | AUDIO_ASSET | VIDEO_ASSET =>
            isAssetDataReady(msg.assetId, zms)
          case _ =>
            Signal.const(false)
        }
      }
    }

    case object ForwardFriends extends MessageAction(R.string.glyph__share, R.string.glyph__share, R.string.secret_message_forward_to_friends) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        if (msg.isEphemeral || isForbidMsg(msg)) Signal.const(false)
        else msg.msgType match {
          case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | ASSET =>
            // TODO: Once https://wearezeta.atlassian.net/browse/CM-976 is resolved, we should handle image asset like any other asset
            Signal.const(true)
          case ANY_ASSET | AUDIO_ASSET | VIDEO_ASSET =>
            isAssetDataReady(msg.assetId, zms)
          case TEXTJSON =>
            if (MessageContentUtils.isEmojiGifJson(msg.contentType.getOrElse(""))) {
              Signal.const(true)
            } else {
              Signal const (false)
            }
          case _ =>
            Signal.const(false)
        }
      }
    }

    case object Copy extends MessageAction(R.string.glyph__copy, R.string.glyph__copy, R.string.message_bottom_menu_action_copy) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA if (!msg.isEphemeral && !isForbidMsg(msg)) => Signal.const(true)
          case TEXTJSON  if (BuildConfig.DEBUG && !msg.isEphemeral && !isForbidMsg(msg)) => Signal.const(true)
          case _ => Signal.const(false)
        }
    }

    case object Delete extends MessageAction(R.string.glyph__trash, R.string.glyph__trash, R.string.message_bottom_menu_action_delete) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT | ANY_ASSET | ASSET | AUDIO_ASSET | VIDEO_ASSET | KNOCK | LOCATION | RICH_MEDIA | TEXT_EMOJI_ONLY | TEXTJSON if p.delCollapsed =>
            if (msg.userId != zms.selfUserId) Signal.const(true)
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal.const(false)
        }
    }

    case object DeleteLocal extends MessageAction(R.string.glyph__delete_me, R.string.glyph__delete_me, R.string.message_bottom_menu_action_delete_local) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = Signal const !p.delCollapsed
    }

    case object DeleteGlobal extends MessageAction(R.string.glyph__delete_everywhere, R.string.glyph__delete_everywhere, R.string.message_bottom_menu_action_delete_global) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        msg.msgType match {
          case TEXT | ANY_ASSET | ASSET | AUDIO_ASSET | VIDEO_ASSET | KNOCK | LOCATION | RICH_MEDIA | TEXT_EMOJI_ONLY | TEXTJSON if !p.delCollapsed =>
            if (msg.userId != zms.selfUserId) Signal const true
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal const false
        }
      }
    }

    case object Like extends MessageAction(R.string.glyph__like, R.string.glyph__like, R.string.message_bottom_menu_action_like) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if !msg.isEphemeral =>
            for {
              isMember <- isMemberOfConversation(msg.convId, zms)
              isLiked <- isLikedBySelf(msg.id, zms)
            } yield isMember && !isLiked
          case _ =>
            Signal const false
        }
    }

    case object Unlike extends MessageAction(R.string.glyph__liked, R.string.glyph__liked, R.string.message_bottom_menu_action_unlike) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if !msg.isEphemeral =>
            for {
              isMember <- isMemberOfConversation(msg.convId, zms)
              isLiked <- isLikedBySelf(msg.id, zms)
            } yield isMember && isLiked
          case _ =>
            Signal const false
        }
    }

    case object Save extends MessageAction(R.string.glyph__download, R.string.glyph__download, R.string.message_bottom_menu_action_save) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        if (isForbidMsg(msg)) Signal const false
        else msg.msgType match {
          case ASSET => Signal.const(zms.selfUserId == msg.userId || !msg.isEphemeral)
          case AUDIO_ASSET | VIDEO_ASSET if zms.selfUserId == msg.userId || !msg.isEphemeral => isAssetDataReady(msg.assetId, zms)
          case _ => Signal const false
        }
    }

    case object OpenFile extends MessageAction(R.string.glyph__file, R.string.glyph__file, R.string.message_bottom_menu_action_open) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        msg.msgType match {
          case ANY_ASSET if !msg.isEphemeral && !p.collection && !isForbidMsg(msg) =>
            isAssetDataReady(msg.assetId, zms)
          case _ =>
            Signal const false
        }
      }
    }

    case object Reveal extends MessageAction(R.string.glyph__view, R.string.glyph__view, R.string.message_bottom_menu_action_reveal) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = Signal const (p.collection && !msg.isEphemeral && !isForbidMsg(msg))
    }

    case object Edit extends MessageAction(R.string.glyph__edit, R.string.glyph__edit, R.string.message_bottom_menu_action_edit) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case TEXT_EMOJI_ONLY | TEXT | RICH_MEDIA if !msg.isEphemeral && !isForbidMsg(msg) && msg.userId == zms.selfUserId =>
            if (p.collection) Signal const false
            else isMemberOfConversation(msg.convId, zms)
          case _ =>
            Signal const false
        }
    }

    case object Reply extends MessageAction(R.string.glyph__message_reply, R.string.glyph__message_reply, R.string.message_bottom_menu_action_reply) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = messageActionsController.isSupportReply(msg)
//        msg.msgType match {
//        case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if (!msg.isEphemeral && !isForbidMsg(msg)) =>
//          isMemberOfConversation(msg.convId, zms)
//        case _ =>
//          Signal.const(false)
//      }
    }

    case object Details extends MessageAction(R.string.glyph__view, R.string.glyph__view, R.string.message_bottom_menu_action_details) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = msg.msgType match {
        case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if (!msg.isEphemeral && !isForbidMsg(msg)) =>
          for {
            isGroup <- zms.conversations.groupConversation(msg.convId)
            isMember <- isMemberOfConversation(msg.convId, zms)
          } yield isGroup && isMember && !p.collection
        case _ =>
          Signal.const(false)
      }
    }

    case object HideForbid extends MessageAction(R.string.glyph__message_forbid, R.string.glyph__message_forbid, R.string.message_bottom_menu_action_hideforbid) {

      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] =
        msg.msgType match {
          case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET | TEXTJSON =>
            for {
              isGroupManager <- isGroupManagerByMsg(msg.id, msg.convId, msg.userId, zms)
              isHideForbid <- isHideForbidByMsg(msg.id, zms)
            } yield {
              isGroupManager && !isHideForbid && (if (msg.msgType == Message.Type.TEXTJSON) false else true)
            }
          case _ =>
            Signal const false
        }
    }

    case object AddFavorite extends MessageAction(R.string.glyph__favorite_add, R.string.glyph__favorite_add, R.string.message_bottom_menu_action_favorite_add) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        if (msg.isEphemeral) {
          Signal const false
        } else msg.msgType match {
          case ASSET    =>
            (for {
              asset <- zms.assetsStorage.signal(msg.assetId) if asset.status == com.waz.model.AssetStatus.UploadDone && asset.mime == Mime.Image.Gif
              Some(localData) <- Signal.future(zms.imageLoader.loadRawImageData(asset))
            } yield localData).map { it =>
              val inputStream = it.inputStream
              val buff = new Array[Byte](inputStream.available())
              inputStream.read(buff)
              inputStream.close()

              !GifSavedDaoHelper.existsSavedGif(SpUtils.getUserId(ZApplication.getInstance()), isEmojiGif = false, MD5Util.MD5(buff))
            }.orElse(Signal const false)
          case TEXTJSON =>
            if (MessageContentUtils.isEmojiGifJson(msg.contentType.getOrElse(""))) {
              val emojiGifModel = EmojiGifModel.parseJson(msg.contentString)
              val gifUrl = emojiGifModel.msgData.url
              Signal const !GifSavedDaoHelper.existsSavedGif(SpUtils.getUserId(ZApplication.getInstance()), isEmojiGif = true, gifUrl)
            } else {
              Signal const false
            }
          case _        =>
            Signal const false
        }
      }
    }

    case object RemoveFavorite extends MessageAction(R.string.glyph__favorite_remove, R.string.glyph__favorite_remove, R.string.message_bottom_menu_action_favorite_remove) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        if (msg.isEphemeral) {
          Signal const false
        } else msg.msgType match {
          case ASSET    =>
            (for {
              asset <- zms.assetsStorage.signal(msg.assetId) if asset.status == com.waz.model.AssetStatus.UploadDone && asset.mime == Mime.Image.Gif
              Some(localData) <- Signal.future(zms.imageLoader.loadRawImageData(asset))
            } yield localData).map { it =>
              val inputStream = it.inputStream
              val buff = new Array[Byte](inputStream.available())
              inputStream.read(buff)
              inputStream.close()

              GifSavedDaoHelper.existsSavedGif(SpUtils.getUserId(ZApplication.getInstance()), isEmojiGif = false, MD5Util.MD5(buff))
            }.orElse(Signal const false)
          case TEXTJSON =>
            if (MessageContentUtils.isEmojiGifJson(msg.contentType.getOrElse(""))) {
              val emojiGifModel = EmojiGifModel.parseJson(msg.contentString)
              val gifUrl = emojiGifModel.msgData.url
              Signal const GifSavedDaoHelper.existsSavedGif(SpUtils.getUserId(ZApplication.getInstance()), isEmojiGif = true, gifUrl)
            } else {
              Signal const false
            }
          case _        =>
            Signal const false
        }
      }
    }

    case object Translate extends MessageAction(R.string.glyph__translate, R.string.glyph__translate, R.string.message_bottom_menu_action_translate) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        msg.msgType match {
          case TEXT     =>
            Signal const msg.translateContent.isEmpty
          case _        =>
            Signal const false
        }
      }
    }

    case object HideTranslate extends MessageAction(R.string.glyph__translate, R.string.glyph__translate, R.string.message_bottom_menu_action_hide_translate) {
      override def enabled(msg: MessageData, zms: ZMessaging, p: Params,messageActionsController: MessageActionsController): Signal[Boolean] = {
        msg.msgType match {
          case TEXT     =>
            Signal const msg.translateContent.nonEmpty
          case _        =>
            Signal const false
        }
      }
    }
  }
}
