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
package com.waz.zclient.messages.controllers

import java.io.File
import java.{io, util}
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.{Activity, ProgressDialog}
import android.content.DialogInterface.OnDismissListener
import android.content._
import android.os.Build
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.jsy.common.httpapi.param.TranslateTextParam
import com.jsy.common.httpapi.{ConversationApiService, ImApiConst, NormalServiceAPI, OnHttpListener}
import com.jsy.common.model.EmojiGifModel
import com.jsy.common.utils.MD5Util
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.utils.dynamiclanguage.LocaleParser
import com.waz.api.Message
import com.waz.api.Message.Type.{ANY_ASSET, ASSET, AUDIO_ASSET, LOCATION, RICH_MEDIA, TEXT, TEXT_EMOJI_ONLY, VIDEO_ASSET}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils._
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.wrappers.{AndroidURI, AndroidURIUtil, URI}
import com.waz.zclient._
import com.waz.zclient.common.controllers.ScreenController
import com.waz.zclient.common.controllers.ScreenController.MessageDetailsParams
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.{LikesAndReadsFragment, ReplyController}
import com.waz.zclient.emoji.bean.GifSavedItem
import com.waz.zclient.emoji.utils.GifSavedDaoHelper
import com.waz.zclient.messages.MessageBottomSheetDialog
import com.waz.zclient.messages.MessageBottomSheetDialog.{MessageAction, Params, isForbidMsg, isMemberOfConversation}
import com.waz.zclient.notifications.controllers.ImageNotificationsController
import com.waz.zclient.participants.OptionsMenu
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.SpUtils
import org.json.JSONObject

import scala.collection.immutable.ListSet
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class MessageActionsController(implicit injector: Injector, ctx: Context, ec: EventContext) extends Injectable with DerivedLogTag{
  import MessageActionsController._
  import com.waz.threading.Threading.Implicits.Ui

  private val context                   = inject[Activity]
  private lazy val keyboardController   = inject[KeyboardController]
  private lazy val userPrefsController  = inject[IUserPreferencesController]
  private lazy val clipboard            = inject[ClipboardUtils]
  private lazy val permissions          = inject[PermissionsService]
  private lazy val imageNotifications   = inject[ImageNotificationsController]
  private lazy val replyController      = inject[ReplyController]
  private lazy val screenController = inject[ScreenController]

  private val zms = inject[Signal[ZMessaging]]

  val onMessageAction = EventStream[(MessageAction, MessageData)]()

  val onLikedUserChanged = EventStream[(MessageId, IndexedSeq[UserId])]()

  val onDeleteConfirmed = EventStream[(MessageData, Boolean)]() // Boolean == isRecall(true) or localDelete(false)
  val onAssetSaved = EventStream[AssetData]()

  val messageToReveal = Signal[Option[MessageData]](None)

  private var dialog = Option.empty[OptionsMenu]

  onMessageAction {
    case (MessageAction.Copy, message)             => copyMessage(message)
    case (MessageAction.DeleteGlobal, message)     => recallMessage(message)
    case (MessageAction.DeleteLocal, message)      => deleteMessage(message)
    case (MessageAction.Forward, message)          => forwardMessage(message, true)
    case (MessageAction.ForwardFriends, message)   => forwardMessage(message, false)
    case (MessageAction.Save, message)             => saveMessage(message)
    case (MessageAction.Reveal, message)           => revealMessageInConversation(message)
    case (MessageAction.Delete, message)           => promptDeleteMessage(message)
    case (MessageAction.Like, msg) =>
      val s = zms.head.flatMap(_.reactions.like(msg.convId, msg.id))
      s foreach { _ =>
        userPrefsController.setPerformedAction(IUserPreferencesController.LIKED_MESSAGE)
      }
      s.onComplete{
        case Success(likes)=>
          onLikedUserChanged ! (msg.id,likes.likers.keys.toIndexedSeq)
        case _=>
      }
    case (MessageAction.Unlike, msg) =>
      zms.head.flatMap(_.reactions.unlike(msg.convId, msg.id)).onComplete{
        case Success(likes)=>
          onLikedUserChanged ! (msg.id,likes.likers.keys.toIndexedSeq)
        case _=>
      }
    case (MessageAction.Reply, message)              => replyMessage(message)
    case (MessageAction.Details, message)            => showDetails(message)
    case (MessageAction.HideForbid, message)         => confirmHideForbid(message)
    case (MessageAction.AddFavorite, message)        => saveGif(message)
    case (MessageAction.RemoveFavorite, message)     => removeGif(message)
    case (MessageAction.Translate, message)          => translate(message)
    case (MessageAction.HideTranslate, message)      => hideTranslate(message)
    case _ => // should be handled somewhere else
  }

  private def translate(message: MessageData): Unit = {

    val text = new util.ArrayList[String]()
    text.add(message.contentString)
    ConversationApiService.getInstance.translate(new TranslateTextParam().text(text).target(getTarget),new OnHttpListener[String] {

      override def onFail(code: Int, err: String): Unit = {}


      override def onSuc(r: String, orgJson: String): Unit = {}


      override def onSuc(r: util.List[String], orgJson: String): Unit = {
        if (r != null && r.size() > 0) {
          zms.head.flatMap(_.messages.updateMessageTranslate(message.id,r.get(0))).onComplete{
            case Success(_)=>
                //onTranslateChanged ! msg
            case _=>
          }
        }

      }
    })

  }

  private def hideTranslate(message: MessageData): Unit = {

    zms.head.flatMap(_.messages.deleteMessageTranslate(message.id)).onComplete{
      case Success(_)=>
      //onTranslateChanged ! msg
      case _=>
    }

  }

  private def getTarget(): String = {

    val locale = LocaleParser.findBestMatchingLocaleForLanguage(SpUtils.getLanguage(context))

    var target = locale.getLanguage

    if ("zh".equalsIgnoreCase(target)){
      target = target + "-" + locale.getCountry
    }

    target
  }

  private def removeGif(message: MessageData): Unit = {
    if (MessageContentUtils.isEmojiGifJson(message.contentType.getOrElse(""))) {
      val userId = SpUtils.getUserId(ZApplication.getInstance())
      val emojiGifModel = EmojiGifModel.parseJson(message.contentString)
      val msgData = emojiGifModel.msgData
      GifSavedDaoHelper.deleteSavedGif(userId, isEmojiGif = true, msgData.url)
    }else if(message.msgType == Message.Type.ASSET){
      val saveFuture = for {
        z <- zms.head
        userId <- zms.head.map(_.selfUserId)
        asset <- z.assets.getAssetData(message.assetId) if asset.isDefined
        localData <- z.imageLoader.loadRawImageData(asset.get, forceDownload = true)
      } yield (userId, localData)

      saveFuture onComplete {
        case Success((userId, Some(localData))) =>
          Try {
            val inputStream = localData.inputStream
            val buff = new Array[Byte](inputStream.available())
            inputStream.read(buff)
            inputStream.close()

            GifSavedDaoHelper.deleteSavedGif(userId.str, isEmojiGif = false, MD5Util.MD5(buff))
          }
        case _                                  =>
      }
    }
  }

  private def saveGif(message: MessageData): Unit = {
    if (MessageContentUtils.isEmojiGifJson(message.contentType.getOrElse(""))) {
      val userId = SpUtils.getUserId(ZApplication.getInstance())
      val emojiGifModel = EmojiGifModel.parseJson(message.contentString)
      val msgData = emojiGifModel.msgData
      if (!GifSavedDaoHelper.existsSavedGif(userId, isEmojiGif = true, msgData.url) && msgData.folderName != null) {
        val data = new GifSavedItem()
        data.setUserId(userId)
        data.setUrl(msgData.url)
        data.setFolderId(msgData.id)
        data.setFolderName(msgData.folderName)
        data.setFolderIcon(msgData.icon)
        data.setName(msgData.name)
        data.setRecently(false)
        GifSavedDaoHelper.saveGif(data)
      }
    }else if(message.msgType == Message.Type.ASSET){
      val loadGif = for {
        z <- zms.head
        userId <- zms.head.map(_.selfUserId)
        asset <- z.assets.getAssetData(message.assetId) if asset.isDefined
        localData <- z.imageLoader.loadRawImageData(asset.get,forceDownload = true)
      } yield (userId,localData)

      loadGif onComplete {
        case Success((userId, Some(localData))) =>
          Try {
            val inputStream = localData.inputStream
            val buff = new Array[Byte](inputStream.available())
            inputStream.read(buff)
            inputStream.close()

            val sourceMD5 = MD5Util.MD5(buff)

            if (!GifSavedDaoHelper.existsSavedGif(userId.str, isEmojiGif = false, sourceMD5)) {
              val data = new GifSavedItem()
              data.setUserId(userId.str)
              data.setImage(buff)
              data.setMD5(sourceMD5)
              GifSavedDaoHelper.saveGif(data)
            }
          }
        case _                            =>
      }
    }
  }

  private val onDismissed = new OnDismissListener {
    override def onDismiss(dialogInterface: DialogInterface): Unit = dialog = None
  }

  def showDialog(data: MessageAndLikes, fromCollection: Boolean = false): Boolean = {
    // TODO: keyboard should be handled in more generic way
    (if (keyboardController.hideKeyboardIfVisible()) CancellableFuture.delayed(HideDelay){}.future else Future.successful({})).map { _ =>
      dialog.foreach(_.dismiss())
      dialog = Some(
        returning(OptionsMenu(context, new MessageBottomSheetDialog(data.message, Params(collection = fromCollection)))) { d =>
          d.setOnDismissListener(onDismissed)
          d.show()
        }
      )
    }.recoverWithLog()
    true
  }

  def showDeleteDialog(message: MessageData): Unit = {
    OptionsMenu(context, new MessageBottomSheetDialog(message,
                                 Params(collection = true, delCollapsed = false),
                                 Seq(MessageAction.DeleteLocal, MessageAction.DeleteGlobal))).show()

  }

  private def copyMessage(message: MessageData) =
    zms.head.flatMap(_.usersStorage.get(message.userId)) foreach {
      case Some(user) =>
        val clip = ClipData.newPlainText(getString(R.string.conversation__action_mode__copy__description, user.getDisplayName), message.contentString)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.conversation__action_mode__copy__toast, Toast.LENGTH_SHORT).show()
      case None =>
        // invalid message, ignoring
    }

  private def deleteMessage(message: MessageData) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_me) {
      zms.head.flatMap(_.convsUi.deleteMessage(message.convId, message.id)) foreach { _ =>
        onDeleteConfirmed ! (message, false)
      }
    }

  private def recallMessage(message: MessageData) =
    showDeleteDialog(R.string.conversation__message_action__delete_for_everyone) {
      zms.head.flatMap(_.convsUi.recallMessage(message.convId, message.id)) foreach { _ =>
        onDeleteConfirmed ! (message, true)
      }
    }

  private def promptDeleteMessage(message: MessageData) =
    zms.head.map(_.selfUserId) foreach {
      case user if user == message.userId => showDeleteDialog(message)
      case _ => deleteMessage(message)
    }

  def replyMessage(data: MessageData): Unit = replyController.replyToMessage(data.id, data.convId)

  private def showDetails(data: MessageData): Unit = screenController.showMessageDetails ! Some(MessageDetailsParams(data.id, LikesAndReadsFragment.LikesTab))

  private def showDeleteDialog(title: Int)(onSuccess: => Unit) =
    new AlertDialog.Builder(context)
      .setTitle(title)
      .setMessage(R.string.conversation__message_action__delete_details)
      .setCancelable(true)
      .setNegativeButton(R.string.conversation__message_action__delete__dialog__cancel, null)
      .setPositiveButton(R.string.conversation__message_action__delete__dialog__ok, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          onSuccess
          Toast.makeText(context, R.string.conversation__message_action__delete__confirmation, Toast.LENGTH_SHORT).show()
        }
      })
      .create()
      .show()

  private def getAsset(assetId: AssetId, isForceUrl: Boolean = false) = for {
    z <- zms.head
    asset <- z.assets.getAssetData(assetId)
    uri <- z.assets.getContentUri(assetId, isForceUrl)
  } yield (asset, uri)

  private def forwardMessage(message: MessageData, isShareAll: Boolean = false) = {
    verbose(l"forwardMessage isShareAll:$isShareAll,message:$message")
    val intentBuilder = ShareCompat.IntentBuilder.from(context)
    intentBuilder.setChooserTitle(R.string.conversation__action_mode__fwd__chooser__title)
    if (message.isAssetMessage) {
      val dialog = ProgressDialog.show(context,
        getString(R.string.conversation__action_mode__fwd__dialog__title),
        getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)

      getAsset(message.assetId, true) foreach {
        case (Some(data), Some(uri)) =>
          dialog.dismiss()
          val mime =
            if (data.mime.str.equals("text/plain"))
              "text/*"
            else if (data.mime == Mime.Unknown)
              //TODO: should be fixed on file receiver side
              Mime.Default.str
            else
              data.mime.str
          intentBuilder.setType(mime)
          val newUri = if (isShareAll && !"content".equals(uri.getScheme.toLowerCase) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val path = uri.getPath
            val file = if (TextUtils.isEmpty(path)) null else new File(path)
            val outputFileUri = if (null == file || !file.exists()) null else new AndroidURI(FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file))
            if (null == outputFileUri) uri else outputFileUri
          } else {
            uri
          }
          intentBuilder.addStream(AndroidURIUtil.unwrap(newUri))
          verbose(l"forwardMessage isShareAll:$isShareAll,AndroidURIUtil.unwrap(uri):${AndroidURIUtil.unwrap(newUri)}")
          if (isShareAll) {
            intentBuilder.startChooser()
          } else {
            val intent = intentBuilder.getIntent
            intent.setClass(context, classOf[ShareActivity])
            context.startActivity(intent)
          }
        case _ =>
          // TODO: show error info
          dialog.dismiss()
      }
    }else if(MessageContentUtils.isEmojiGifJson(message.contentType.getOrElse(""))){
      if (!isShareAll) {
        intentBuilder.setType("secret/textjson")
        intentBuilder.setText(message.contentString)
        val intent = intentBuilder.getIntent
        intent.setClass(context, classOf[ShareActivity])
        context.startActivity(intent)
      }
    } else { // TODO: handle location and other non text messages
      intentBuilder.setType("text/plain")
      intentBuilder.setText(message.contentString)
      if (isShareAll) {
        intentBuilder.startChooser()
      } else {
        val intent = intentBuilder.getIntent
        intent.setClass(context, classOf[ShareActivity])
        context.startActivity(intent)
      }
    }
  }

  private def saveMessage(message: MessageData) =
    permissions.requestAllPermissions(ListSet(WRITE_EXTERNAL_STORAGE)).map {  // TODO: provide explanation dialog - use requiring with message str
      case true =>
        if (message.msgType == Message.Type.ASSET) { // TODO: simplify once SE asset v3 is merged, we should be able to handle that without special conditions

          val saveFuture = for {
            z <- zms.head
            asset <- z.assets.getAssetData(message.assetId) if asset.isDefined
            uri <- z.imageLoader.saveImageToGallery(asset.get)
          } yield uri

          saveFuture onComplete {
            case Success(Some(uri)) =>
              imageNotifications.showImageSavedNotification(message.assetId, uri)
              Toast.makeText(context, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
            case _                  =>
              Toast.makeText(context, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
          }
        } else {
          val dialog = ProgressDialog.show(context, getString(R.string.conversation__action_mode__fwd__dialog__title), getString(R.string.conversation__action_mode__fwd__dialog__message), true, true, null)
          zms.head.flatMap(_.assets.saveAssetToDownloads(message.assetId)) foreach {
            case Some(file) =>
              zms.head.flatMap(_.assets.getAssetData(message.assetId)) foreach {
                case Some(data) => onAssetSaved ! data
                case None => // should never happen
              }
              Toast.makeText(context, getString(R.string.content__file__action__save_completed, file.getAbsolutePath), Toast.LENGTH_SHORT).show()
              context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(AndroidURIUtil.unwrap(URI.fromFile(file)))))
              dialog.dismiss()
            case None =>
              Toast.makeText(context, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
              dialog.dismiss()
          }
        }
      case false =>
    } (Threading.Ui)

  private def revealMessageInConversation(message: MessageData) = {
    zms.head.flatMap(z => z.messagesStorage.get(message.id)).onComplete{
      case Success(msg) =>  messageToReveal ! msg
      case _ =>
    }
  }

  def confirmHideForbid(message: MessageData): Unit = {
    new AlertDialog.Builder(context)
      .setTitle(R.string.picture_prompt)
      .setMessage(R.string.message_bottom_menu_action_hideforbid_prompt)
      .setCancelable(true)
      .setNegativeButton(R.string.conversation__message_action__delete__dialog__cancel, null)
      .setPositiveButton(R.string.secret_code_confirm, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          zms.head.flatMap(_.forbids.forbid(message.convId, message.id)).onComplete{
            case Success(_)=>
            case _=>
          }
        }
      })
      .create()
      .show()
  }

  def isSupportReply(msg:MessageData): Signal[Boolean] = {
    if (msg == null) {
      Signal.const(false)
    }
    else
      zms.flatMap { z =>
        msg.msgType match {
          case ANY_ASSET | ASSET | AUDIO_ASSET | LOCATION | TEXT | TEXT_EMOJI_ONLY | RICH_MEDIA | VIDEO_ASSET if (!msg.isEphemeral && !isForbidMsg(msg)) =>
            isMemberOfConversation(msg.convId, z)
          case _ =>
            Signal.const(false)
        }
      }
  }

}

object MessageActionsController {
  private val HideDelay = 200.millis
}
