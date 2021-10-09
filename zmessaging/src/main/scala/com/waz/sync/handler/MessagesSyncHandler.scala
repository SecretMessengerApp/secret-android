/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.sync.handler

import com.waz.log.LogSE._
import com.waz.api.{IConversation, Message}
import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.{Cancelled, internalError}
import com.waz.content.MessagesStorage
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetData.{ProcessingTaskKey, UploadTaskKey}
import com.waz.model.AssetStatus.{Syncable, UploadCancelled, UploadFailed}
import com.waz.model.GenericContent.{Ephemeral, Knock, Location, MsgEdit}
import com.waz.model.GenericMessage._
import com.waz.model._
import com.waz.model.sync.ReceiptType
import com.waz.service._
import com.waz.service.assets._
import com.waz.service.conversation.ConversationsContentUpdater
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.otr.OtrClientsService
import com.waz.sync.SyncHandler.RequestInfo
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.{ErrorOr, ErrorOrResponse}
import com.waz.sync.otr.OtrSyncHandler
import com.waz.sync.{SyncResult, SyncServiceHandle}
import com.waz.threading.CancellableFuture
import com.waz.utils.{RichFutureEither, _}
import com.waz.znet2.http.ResponseCode
import org.json.JSONArray

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.concurrent.duration.FiniteDuration

class MessagesSyncHandler(selfUserId: UserId,
                          service:    MessagesService,
                          msgContent: MessagesContentUpdater,
                          clients:    OtrClientsService,
                          otrSync:    OtrSyncHandler,
                          convs:      ConversationsContentUpdater,
                          storage:    MessagesStorage,
                          assetSync:  AssetSyncHandler,
                          sync:       SyncServiceHandle,
                          assets:     AssetService,
                          errors:     ErrorsService) extends DerivedLogTag {

  import com.waz.threading.Threading.Implicits.Background

  def postDeleted(convId: ConvId, msgId: MessageId): Future[SyncResult] =
    convs.convById(convId).flatMap {
      case Some(conv) =>
        val msg = GenericMessage(Uid(), Proto.MsgDeleted(conv.remoteId, msgId))
        otrSync
          .postOtrMessageIgnoreMissing(ConvId(selfUserId.str), msg)
          .map(SyncResult(_))
      case None =>
        successful(Failure("conversation not found"))
    }


  def postRecalled(convId: ConvId, msgId: MessageId, recalled: MessageId): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        val msg = GenericMessage(msgId.uid, Proto.MsgRecall(recalled))
        otrSync.postOtrMessage(conv.id, msg).flatMap {
          case Left(e) => successful(SyncResult(e))
          case Right(time) =>
            msgContent
              .updateMessage(msgId)(_.copy(editTime = time, state = Message.Status.SENT))
              .map(_ => SyncResult.Success)
        }
      case None =>
        successful(Failure("conversation not found"))
    }

  def postReceipt(convId: ConvId, msgs: Seq[MessageId], userId: UserId, tpe: ReceiptType): Future[SyncResult] =
    convs.convById(convId) flatMap {
      case Some(conv) =>
        if(conv.convType == IConversation.Type.THROUSANDS_GROUP){
          successful(Failure("throusands group don't post receipt"))
        }else{
          val (msg, recipients) = tpe match {
            case ReceiptType.Delivery         => (GenericMessage(msgs.head.uid, Proto.DeliveryReceipt(msgs))(Proto.DeliveryReceipt), Set(userId))
            case ReceiptType.Read             => (GenericMessage(msgs.head.uid, Proto.ReadReceipt(msgs))(Proto.ReadReceipt), Set(userId))
            case ReceiptType.EphemeralExpired => (GenericMessage(msgs.head.uid, Proto.MsgRecall(msgs.head)), Set(selfUserId, userId))
          }
          otrSync
            .postOtrMessage(conv.id, msg, Some(recipients), nativePush = false)
            .map(SyncResult(_))
        }

      case None =>
        successful(Failure("conversation not found"))
    }

  def postMessage(convId: ConvId, id: MessageId, editTime: RemoteInstant, isRetry: Boolean = false)(implicit info: RequestInfo): Future[SyncResult] =
    storage.getMessage(id).flatMap { message =>
      message
        .fold(successful(None: Option[ConversationData]))(msg => convs.convById(msg.convId))
        .map(conv => (message, conv))
    }.flatMap {
      case (Some(msg), Some(conv)) =>
        postMessage(conv, msg, editTime, isRetry).flatMap {
          case Right(timeAndId) =>
            verbose(l"postOtrMessage($msg) successful $timeAndId")
            for {
              _ <- service.messageSent(convId, timeAndId._2, timeAndId._1, if(msg.editTime.isEpoch) conv.isSingleMsgEdit else msg.enabled_edit_msg)
              (prevLastTime, lastTime) <- msgContent.updateLocalMessageTimes(convId, msg.time, timeAndId._1)
                .map(_.lastOption.map { case (p, c) => (p.time, c.time)}.getOrElse((msg.time, timeAndId._1)))
              // update conv lastRead time if there is no unread message after the message that was just sent
              _ <- convs.storage.update(convId, c => if (!c.lastRead.isAfter(prevLastTime)) c.copy(lastRead = lastTime) else c)
              _ <- convs.updateLastEvent(convId, timeAndId._1)
            } yield SyncResult.Success

          case Left(error) =>
            verbose(l"postOtrMessage($msg) Left(error): $error")
            for {
              _ <- error match {
                case ErrorResponse(ResponseCode.Forbidden, _, "unknown-client") =>
                  clients.onCurrentClientRemoved()
                case _ =>
                  Future.successful({})
              }
              syncResult = error match {
                case ErrorResponse(ErrorResponse.ConnectionErrorCode, _, _) =>
                  Failure.apply(error)
                case _ =>
                  SyncResult(error)
              }
              result <- syncResult match {
                case r: SyncResult.Failure =>
                  service
                    .messageDeliveryFailed(convId, msg, error)
                    .map(_ => r)
                case r =>
                  Future.successful(r)
              }
            } yield result
        }

      case (Some(msg), None) =>
        service
          .messageDeliveryFailed(msg.convId, msg, internalError("conversation not found"))
          .map(_ => Failure("postMessage failed, couldn't find conversation for msg"))

      case _ =>
        successful(Failure("postMessage failed, couldn't find either message or conversation"))
    }



  def postMessageForRecipients(convId: ConvId, id: MessageId, editTime: RemoteInstant, uids: String, unblock: Boolean)(implicit info: RequestInfo): Future[SyncResult] =
    storage.getMessage(id).flatMap { message =>
      message
        .fold(successful(None: Option[ConversationData]))(msg => convs.convById(msg.convId))
        .map(conv => (message, conv))
    }.flatMap {
      case (Some(msg), Some(conv)) =>
        postMessageForRecipients(conv, msg, editTime, uids, unblock).flatMap {
          case Right(timeAndId) =>
            verbose(l"postOtrMessage($msg) successful $timeAndId")
            for {
              _ <- service.messageSent(convId, timeAndId._2, timeAndId._1, conv.isSingleMsgEdit)
              (prevLastTime, lastTime) <- msgContent.updateLocalMessageTimes(convId, msg.time, timeAndId._1)
                .map(_.lastOption.map { case (p, c) => (p.time, c.time)}.getOrElse((msg.time, timeAndId._1)))
              // update conv lastRead time if there is no unread message after the message that was just sent
              _ <- convs.storage.update(convId, c => if (!c.lastRead.isAfter(prevLastTime)) c.copy(lastRead = lastTime) else c)
              _ <- convs.updateLastEvent(convId, timeAndId._1)
            } yield SyncResult.Success

          case Left(error) =>
            for {
              _ <- error match {
                case ErrorResponse(ResponseCode.Forbidden, _, "unknown-client") =>
                  clients.onCurrentClientRemoved()
                case _ =>
                  Future.successful({})
              }
              syncResult = error match {
                case ErrorResponse(ErrorResponse.ConnectionErrorCode, _, _) =>
                  Failure.apply(error)
                case _ =>
                  SyncResult(error)
              }
              result <- syncResult match {
                case r: SyncResult.Failure =>
                  service
                    .messageDeliveryFailed(convId, msg, error)
                    .map(_ => r)
                case r =>
                  Future.successful(r)
              }
            } yield result
        }

      case (Some(msg), None) =>
        service
          .messageDeliveryFailed(msg.convId, msg, internalError("conversation not found"))
          .map(_ => Failure("postMessage failed, couldn't find conversation for msg"))

      case _ =>
        successful(Failure("postMessage failed, couldn't find either message or conversation"))
    }

  /**
    * Sends a message to the given conversation. If the message is an edit, it will also update the message
    * in the database to the new message ID.
    * @return either error, or the remote timestamp and the new message ID
    */
  private def postMessage(conv: ConversationData, msg: MessageData, reqEditTime: RemoteInstant, isRetry: Boolean):
    Future[Either[ErrorResponse, (RemoteInstant, MessageId)]] = {

    val replyMessageId:MessageId = if (msg.replyMessageId.isEmpty) null else msg.replyMessageId.head

    def postTextMessage() = {
      val adjustedMsg = msg.adjustMentions(true).getOrElse(msg)

      val (gm, isEdit) =
        adjustedMsg.protos.lastOption match {
          case Some(m@GenericMessage(id, MsgEdit(ref, text))) if !reqEditTime.isEpoch =>
            (m, true) // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ =>
            (TextMessage(adjustedMsg), false)
        }
      gm.replyMessageId = GenericMessage.getReplyMessageId(replyMessageId)
      otrSync.postOtrMessage(conv.id, gm).flatMap {
        case Right(time) if isEdit =>
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm) map {
            case Some(m) => Right(m)
            case _ => Right(msg.copy(time = RemoteInstant(time.instant)))
          }
        case Right(time) => successful(Right(msg.copy(time = time)))
        case Left(err) => successful(Left(err))
      }
    }

    def postTextJsonMessage() = {
      val adjustedMsg = msg.adjustMentions(true).getOrElse(msg)

      val (gm, isEdit) =
        adjustedMsg.protos.lastOption match {
          case Some(m@GenericMessage(id, MsgEdit(ref, text))) if !reqEditTime.isEpoch =>
            (m, true) // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ =>
            (TextJsonMessage(adjustedMsg), false)
        }
      gm.replyMessageId = GenericMessage.getReplyMessageId(replyMessageId)
      otrSync.postOtrMessage(conv.id, gm).flatMap {
        case Right(time) if isEdit =>
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm) map {
            case Some(m) => Right(m)
            case _ => Right(msg.copy(time = RemoteInstant(time.instant)))
          }
        case Right(time) => successful(Right(msg.copy(time = time)))
        case Left(err) => successful(Left(err))
      }
    }

    import Message.Type._
    verbose(l"postMessage msg.msgType: ${msg.msgType},msg.assetId:${msg.assetId}")
    msg.msgType match {
      case MessageData.IsAsset() => Cancellable(UploadTaskKey(msg.assetId))(uploadAsset(conv, msg, isRetry)).future
        .map(_.map((_, msg.id)))
      case KNOCK => otrSync.postOtrMessage(
        conv.id,
        GenericMessage(msg.id.uid, msg.ephemeral, Proto.Knock(msg.expectsRead.getOrElse(false)))
      ).map(_.map((_, msg.id)))
      case TEXT | TEXT_EMOJI_ONLY => postTextMessage().map(_.map(data => (data.time, data.id)))
      case TEXTJSON => postTextJsonMessage().map(_.map(data => (data.time, data.id)))
      case RICH_MEDIA =>
        postTextMessage().flatMap {
          case Right(m) => sync.postOpenGraphData(conv.id, m.id, m.editTime).map(_ => Right(m.time, m.id))
          case Left(err) => successful(Left(err))
        }
      case LOCATION =>
        msg.protos.headOption match {
          case Some(GenericMessage(id, loc: Location)) if msg.isEphemeral =>
            otrSync.postOtrMessage(conv.id, GenericMessage(id, Ephemeral(msg.ephemeral, loc))).map(_.map((_, msg.id)))
          case Some(proto) =>
            otrSync.postOtrMessage(conv.id, proto).map(_.map((_, msg.id)))
          case None =>
            successful(Left(internalError(s"Unexpected location message content: $msg")))
        }
      case tpe =>
        msg.protos.headOption match {
          case Some(proto) if !msg.isEphemeral =>
            verbose(l"sending generic message: $proto")
            otrSync.postOtrMessage(conv.id, proto).map(_.map((_, msg.id)))
          case Some(_) =>
            successful(Left(internalError(s"Can not send generic ephemeral message: $msg")))
          case None =>
            successful(Left(internalError(s"Unsupported message type in postOtrMessage: $tpe")))
        }
    }
  }

  private def postMessageForRecipients(conv: ConversationData, msg: MessageData, reqEditTime: RemoteInstant, uids: String, unblock: Boolean) = {

    val replyMessageId: MessageId = if (msg.replyMessageId.isEmpty) null else msg.replyMessageId.head

    def getMembersFormUids(uids: String) ={

      val mutableSet = scala.collection.mutable.Set[UserId]()
      val jSONArray  = new JSONArray(uids)

      if(jSONArray.length() > 0){
        for(i <- 0 until jSONArray.length()){
          mutableSet.add(UserId(jSONArray.getString(i)))
        }
      }

      mutableSet.toSet
    }

    def postTextMessage(uids: String) = {
      val adjustedMsg = msg.adjustMentions(true).getOrElse(msg)

      val (gm, isEdit) =
        adjustedMsg.protos.lastOption match {
          case Some(m@GenericMessage(id, MsgEdit(ref, text))) if !reqEditTime.isEpoch =>
            (m, true) // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ =>
            (TextMessage(adjustedMsg), false)
        }
      gm.replyMessageId = GenericMessage.getReplyMessageId(replyMessageId)
      otrSync.postBgpMessageForRecipients(conv.id, gm, uids = uids, unblock = unblock,recipients = Some(getMembersFormUids(uids))).flatMap {
        case Right(time) if isEdit =>
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm) map {
            case Some(m) => Right(m)
            case _ => Right(msg.copy(time = RemoteInstant(time.instant)))
          }
        case Right(time) => successful(Right(msg.copy(time = time)))
        case Left(err) => successful(Left(err))
      }
    }

    def postTextJsonMessage(uids: String) = {
      val adjustedMsg = msg.adjustMentions(true).getOrElse(msg)

      val (gm, isEdit) =
        adjustedMsg.protos.lastOption match {
          case Some(m@GenericMessage(id, MsgEdit(ref, text))) if !reqEditTime.isEpoch =>
            (m, true) // will send edit only if original message was already sent (reqEditTime > EPOCH)
          case _ =>
            (TextJsonMessage(adjustedMsg), false)
        }
      gm.replyMessageId = GenericMessage.getReplyMessageId(replyMessageId)
      otrSync.postBgpMessageForRecipients(conv.id, gm, uids = uids, unblock = unblock,recipients = Some(getMembersFormUids(uids))).flatMap {
        case Right(time) if isEdit =>
          // delete original message and create new message with edited content
          service.applyMessageEdit(conv.id, msg.userId, RemoteInstant(time.instant), gm) map {
            case Some(m) => Right(m)
            case _ => Right(msg.copy(time = RemoteInstant(time.instant)))
          }
        case Right(time) => successful(Right(msg.copy(time = time)))
        case Left(err) => successful(Left(err))
      }
    }

    import Message.Type._

    msg.msgType match {
      case MessageData.IsAsset() => Cancellable(UploadTaskKey(msg.assetId))(uploadAsset(conv, msg)).future
        .map(_.map((_, msg.id)))
      case KNOCK => otrSync.postBgpMessageForRecipients(
        conv.id,
        GenericMessage(msg.id.uid, msg.ephemeral, Proto.Knock(msg.expectsRead.getOrElse(false))), uids = uids,
        unblock = unblock,
        recipients = Some(getMembersFormUids(uids))
      ).map(_.map((_, msg.id)))
      case TEXT | TEXT_EMOJI_ONLY => postTextMessage(uids).map(_.map(data => (data.time, data.id)))
      case TEXTJSON => postTextJsonMessage(uids).map(_.map(data => (data.time, data.id)))
      case RICH_MEDIA =>
        postTextMessage(uids).flatMap {
          case Right(m) => sync.postOpenGraphData(conv.id, m.id, m.editTime).map(_ => Right(m.time, m.id))
          case Left(err) => successful(Left(err))
        }
      case LOCATION =>
        msg.protos.headOption match {
          case Some(GenericMessage(id, loc: Location)) if msg.isEphemeral =>
            otrSync.postOtrMessage(conv.id, GenericMessage(id, Ephemeral(msg.ephemeral, loc))).map(_.map((_, msg.id)))
          case Some(proto) =>
            otrSync.postOtrMessage(conv.id, proto).map(_.map((_, msg.id)))
          case None =>
            successful(Left(internalError(s"Unexpected location message content: $msg")))
        }
      case tpe =>
        msg.protos.headOption match {
          case Some(proto) if !msg.isEphemeral =>
            verbose(l"sending generic message: $proto")
            otrSync.postOtrMessage(conv.id, proto).map(_.map((_, msg.id)))
          case Some(_) =>
            successful(Left(internalError(s"Can not send generic ephemeral message: $msg")))
          case None =>
            successful(Left(internalError(s"Unsupported message type in postOtrMessage: $tpe")))
        }
    }
  }

  private def uploadAsset(conv: ConversationData, msg: MessageData, isRetry: Boolean = false): ErrorOrResponse[RemoteInstant] = {
    verbose(l"uploadAsset($conv, $msg)")

    def postAssetMessage(asset: AssetData, preview: Option[AssetData], origTime: Option[RemoteInstant] = None): ErrorOrResponse[RemoteInstant] = {
      val proto = GenericMessage(msg.id.uid, msg.ephemeral, Proto.Asset(asset, preview, msg.expectsRead.getOrElse(false)))
      CancellableFuture.lift(otrSync.postOtrMessage(conv.id, proto).flatMap {
        case Right(time) =>
          val updateTime = origTime.getOrElse(time)
          verbose(l"posted asset message for: $asset, with update time: $updateTime (origTime: $origTime)")
          msgContent.updateMessage(msg.id)(_.copy(protos = Seq(proto), time = updateTime)).map(_ => Right(updateTime))
        case Left(err) =>
          warn(l"posting asset message failed: $err")
          Future.successful(Left(err))
      })
    }

    //TODO Dean: Update asset status to UploadInProgress after posting original - what about images...?
    def postOriginal(asset: AssetData): ErrorOrResponse[RemoteInstant] =
      if (asset.status != AssetStatus.UploadNotStarted) CancellableFuture successful Right(msg.time)
      else asset.mime match {
        case _ => CancellableFuture.successful(Right(msg.time))
//        case _ =>
//          verbose(l"send original")
//          postAssetMessage(asset, None)
      }

    def sendWithV3(asset: AssetData) = {
      postOriginal(asset).flatMap {
        case Left(err) =>
          verbose(l"postOriginal err:$err")
          CancellableFuture successful Left(err)
        case Right(origTime) =>
          //send preview
          verbose(l"postOriginal  send preview origTime:$origTime,asset.previewId:${asset.previewId}")
          CancellableFuture.lift(asset.previewId.map(assets.getAssetData).getOrElse(Future successful None)).flatMap {
            case Some(prev) =>
              verbose(l"send preview $prev")
              service.retentionPolicy(conv).flatMap { retention =>
                assetSync.uploadAssetData(prev.id, retention = retention, isRetry = isRetry).flatMap {
                  case Right(updated) =>
                    verbose(l"assetSync.uploadAssetData updated:$updated")
//                    postAssetMessage(asset, Some(updated), Some(origTime)).map {
//                      case (Right(_)) =>
//                        verbose(l"assetSync.postAssetMessage updated:$updated")
//                        Right(Some(updated))
//                      case (Left(err)) =>
//                        verbose(l"assetSync.postAssetMessage err:$err")
//                        Left(err)
//                    }
                    CancellableFuture successful Right(Some(updated))
                  case Left(err) =>
                    verbose(l"assetSync.uploadAssetData err:$err")
                    CancellableFuture successful Left(err)
                }
              }
            case None =>
              verbose(l"send preview Right(None)")
              CancellableFuture successful Right(None)
          }.flatMap { //send asset
            case Right(prev) =>
              service.retentionPolicy(conv).flatMap { retention =>
                verbose(l"send asset retention:$retention")
                assetSync.uploadAssetData(asset.id, retention = retention, isRetry = isRetry).flatMap {
                  case Right(updated) if asset.isImage =>
                    verbose(l"send image asset")
                    postAssetMessage(updated, prev).map(_.fold(Left(_), updateTime => Right(updateTime)))
                  case Right(updated) =>
                    verbose(l"send non-image asset")
                    postAssetMessage(updated, prev, Some(origTime)).map(_.fold(Left(_), _ => Right(origTime)))
                  case Left(err) if err.message.contains(AssetSyncHandler.AssetTooLarge) =>
                    verbose(l"send asset err.message.contains(AssetSyncHandler.AssetTooLarge)")
                    CancellableFuture.lift(errors.addAssetTooLargeError(conv.id, msg.id).map { _ => Left(err) })
                  case Left(err) =>
                    verbose(l"send asset Left(err)")
                    CancellableFuture successful Left(err)
                }
              }
            case Left(err) =>
              verbose(l"CancellableFuture.lift(ass err:$err")
              CancellableFuture successful Left(err)
          }
      }
    }

    //want to wait until asset meta and preview data is loaded before we send any messages
    AssetProcessing.get(ProcessingTaskKey(msg.assetId)).flatMap { _ =>
      CancellableFuture lift assets.getAssetData(msg.assetId).flatMap {
        case None =>
          verbose(l"Sending asset: None")
          CancellableFuture successful Left(internalError(s"no asset found for msg: $msg"))
        case Some(asset) if asset.status == AssetStatus.UploadCancelled =>
          verbose(l"Sending asset: AssetStatus.UploadCancelled")
          CancellableFuture successful Left(Cancelled)
        case Some(asset) =>
          verbose(l"Sending asset: $asset")
          sendWithV3(asset)
      }
    }
  }

  def postAssetStatus(cid: ConvId, mid: MessageId, expiration: Option[FiniteDuration], status: Syncable): Future[SyncResult] = {
    def post(conv: ConversationData, asset: AssetData): ErrorOr[Unit] =
      if (asset.status != status) successful(Left(internalError(s"asset $asset should have status $status")))
      else status match {
        case UploadCancelled => otrSync.postOtrMessage(conv.id, GenericMessage(mid.uid, expiration, Proto.Asset(asset, expectsReadConfirmation = false))).flatMapRight(_ => storage.remove(mid))
        case UploadFailed if asset.isImage => successful(Left(internalError(s"upload failed for image $asset")))
        case UploadFailed => otrSync.postOtrMessage(conv.id, GenericMessage(mid.uid, expiration, Proto.Asset(asset, expectsReadConfirmation = false))).mapRight(_ => ())
      }

    for {
      conv   <- convs.storage.get(cid) or internalError(s"conversation $cid not found")
      msg    <- storage.get(mid) or internalError(s"message $mid not found")
      aid    = msg.right.map(_.assetId)
      asset  <- aid.flatMapFuture(id => assets.getAssetData(id).or(internalError(s"asset $id not found")))
      result <- conv.flatMapFuture(c => asset.flatMapFuture(a => post(c, a)))
    } yield SyncResult(result)
  }
}
