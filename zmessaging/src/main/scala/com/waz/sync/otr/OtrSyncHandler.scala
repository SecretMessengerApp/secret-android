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
package com.waz.sync.otr

import com.waz.api.impl.ErrorResponse
import com.waz.api.impl.ErrorResponse.internalError
import com.waz.api.{IConversation, Verification}
import com.waz.cache.LocalData
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetData.RemoteData
import com.waz.model._
import com.waz.model.otr.ClientId
import com.waz.service.conversation.ConversationsService
import com.waz.service.otr.OtrService
import com.waz.service.push.PushService
import com.waz.service.{ErrorsService, UserService}
import com.waz.sync.SyncResult
import com.waz.sync.SyncResult.Failure
import com.waz.sync.client.AssetClient.{Metadata, Retention, UploadResponse}
import com.waz.sync.client.OtrClient.{ClientMismatch, EncryptedContent, MessageResponse}
import com.waz.sync.client._
import com.waz.threading.CancellableFuture
import com.waz.utils.crypto.AESUtils
import com.waz.utils.events.EventContext
import org.json.{JSONArray, JSONObject}

import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.control.NonFatal

trait OtrSyncHandler {
  def postOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true): Future[Either[ErrorResponse, RemoteInstant]]

  def postVoipCallOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true,video: Boolean,call_conversation_id: String,call_type: String): Future[Either[ErrorResponse, RemoteInstant]]

  def postOtrMessageIgnoreMissing(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true): Future[Either[ErrorResponse, RemoteInstant]]

  def postBgpMessageForRecipients(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true, uids: String, unblock: Boolean = false): Future[Either[ErrorResponse, RemoteInstant]]

  def uploadAssetDataV3(data: LocalData, key: Option[AESKey], mime: Mime = Mime.Default, retention: Retention): CancellableFuture[Either[ErrorResponse, RemoteData]]
  def postSessionReset(convId: ConvId, user: UserId, client: ClientId): Future[SyncResult]
  def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]]
}

class OtrSyncHandlerImpl(selfUserId:         UserId,
                         teamId:             Option[TeamId],
                         selfClientId:       ClientId,
                         otrClient:          OtrClient,
                         msgClient:          MessagesClient,
                         assetClient:        AssetClient,
                         service:            OtrService,
                         convsService:       ConversationsService,
                         convStorage:        ConversationStorage,
                         users:              UserService,
                         members:            MembersStorage,
                         errors:             ErrorsService,
                         clientsSyncHandler: OtrClientsSyncHandler,
                         push:               PushService,
                         usersStorage:       UsersStorage,
                         messageStorage:     MessagesStorage,
                         aliasStorage:       AliasStorage) extends OtrSyncHandler with DerivedLogTag {

  import OtrSyncHandler._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val ec = EventContext.Global

  override def postVoipCallOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true,video: Boolean,call_conversation_id : String,call_type : String) = {
    import com.waz.utils.{RichEither, RichFutureEither}


    def encryptAndSend(msg: GenericMessage, external: Option[Array[Byte]] = None, retries: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): ErrorOr[MessageResponse] = {

      def contentForNeedEncrypt(conv: ConversationData) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful(EncryptedContent.Empty)
        } else {
          for {
            us <- recipients.fold(members.getActiveUsers(convId).map(_.toSet))(rs => Future.successful(rs))
            content <- if("2".equalsIgnoreCase(call_type)) service.encryptForUsers(us, msg, retries > 0, previous) else service.encryptForUsers(us -- Set(selfUserId), msg, retries > 0, previous)
          } yield content
        }

      def dealDeviceMismatch(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.deleted).mapFuture(service.deleteClients)
          } yield {}
        }

      def dealDeviceMissing(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.missing.keySet).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _, !conv.isServerNotification))
          } yield {}
        }

      for {
        _ <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        Some(self) <- usersStorage.get(selfUserId)
        _ = if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        contentOnlyForNeedEncrypt <- contentForNeedEncrypt(conv)
        resp <- if (contentOnlyForNeedEncrypt.estimatedSize < MaxContentSize) {
          if("2".equalsIgnoreCase(call_type)) msgClient.postMessage(conv.remoteId, OtrMessage(selfClientId, contentOnlyForNeedEncrypt, external, nativePush,false,video,selfUserId.str,self.getDisplayName,call_conversation_id,call_type), ignoreMissing = retries > 1, recipients).future
          else msgClient.postVoipMessage(conv.remoteId, OtrMessage(selfClientId, contentOnlyForNeedEncrypt, external, nativePush,false,video,selfUserId.str,self.getDisplayName,call_conversation_id,call_type), Some(Set(selfUserId))).future
        }
        else {
          verbose(l"Message content too big, will post as External. Estimated size: ${contentOnlyForNeedEncrypt.estimatedSize}")
          val key = AESKey()
          val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(msg))
          val newMessage = GenericMessage(Uid(msg.messageId), Proto.External(key, sha))
          encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
        }
        _ <- dealDeviceMismatch(conv, resp)
        _ <- dealDeviceMissing(conv, resp)
        retry <- resp.flatMapFuture {
          case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
            clientsSyncHandler.syncSessions(missing).flatMap { err =>
              if (err.isDefined)
                error(l"syncSessions for missing clients failed: $err")
              encryptAndSend(msg, external, retries, contentOnlyForNeedEncrypt)
            }
          case _: MessageResponse.Failure =>
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
          case resp => Future.successful(Right(resp))
        }
      } yield retry
    }

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.hasCalling)
          errors.addConvUnverifiedError(convId, MessageId(message.messageId))

        Left(ErrorResponse.Unverified)
      case NonFatal(e) => Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }

  override def postOtrMessage(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true) = {
    import com.waz.utils.{RichEither, RichFutureEither}

    def encryptAndSend(msg: GenericMessage, external: Option[Array[Byte]] = None, retries: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): ErrorOr[MessageResponse] = {

      def contentForNeedEncrypt(conv: ConversationData) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful(EncryptedContent.Empty)
        } else {
          for {
            us <- recipients.fold(members.getActiveUsers(convId).map(_.toSet))(rs => Future.successful(rs))
            content <- service.encryptForUsers(us, msg, retries > 0, previous)
          } yield content
        }

      def dealDeviceMismatch(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.deleted).mapFuture(service.deleteClients)
          } yield {}
        }

      def dealDeviceMissing(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.missing.keySet).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _, !conv.isServerNotification))
          } yield {}
        }

      for {
        _ <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        _ = if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        contentOnlyForNeedEncrypt <- contentForNeedEncrypt(conv)
        resp <- if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          for {
            messageData <- messageStorage.get(MessageId(message.messageId))
            userData <- messageData.fold(Future.successful(Option.empty[UserData]))(tempData => usersStorage.get(tempData.userId))
            aliasData <- messageData.fold(Future.successful(Option.empty[AliasData]))(tempData => aliasStorage.get(convId,tempData.userId))
            result <- {
              val jsonObject = new JSONObject()
              jsonObject.put("text", AESUtils.base64(GenericMessage.toByteArray(message)))
              jsonObject.put("sender", selfClientId.str)

              getAssetInfo(userData, aliasData).foreach(jsonObject.put("asset", _))

              verbose(l"processDecryptedRows send BgpMessage jsonObject:${jsonObject}")
              msgClient.postBgpMessage(conv.remoteId, jsonObject, ignoreMissing = retries > 1, recipients).future
            }
          } yield result
        } else {
          if (contentOnlyForNeedEncrypt.estimatedSize < MaxContentSize) {
            msgClient.postMessage(conv.remoteId, OtrMessage(selfClientId, contentOnlyForNeedEncrypt, external, nativePush), ignoreMissing = retries > 1, recipients).future
          }
          else {
            verbose(l"Message content too big, will post as External. Estimated size: ${contentOnlyForNeedEncrypt.estimatedSize}")
            val key = AESKey()
            val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(msg))
            val newMessage = GenericMessage(Uid(msg.messageId), Proto.External(key, sha))
            encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
          }
        }
        _ <- dealDeviceMismatch(conv, resp)
        _ <- dealDeviceMissing(conv, resp)
        retry <- resp.flatMapFuture {
          case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
            clientsSyncHandler.syncSessions(missing).flatMap { err =>
              if (err.isDefined)
                error(l"syncSessions for missing clients failed: $err")
              encryptAndSend(msg, external, retries, contentOnlyForNeedEncrypt)
            }
          case _: MessageResponse.Failure =>
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
          case resp => Future.successful(Right(resp))
        }
      } yield retry
    }

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.hasCalling)
          errors.addConvUnverifiedError(convId, MessageId(message.messageId))

        Left(ErrorResponse.Unverified)
      case NonFatal(e) => Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }


  override def postOtrMessageIgnoreMissing(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]] = None, nativePush: Boolean = true) = {
    import com.waz.utils.{RichEither, RichFutureEither}

    def encryptAndSend(msg: GenericMessage, external: Option[Array[Byte]] = None, retries: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): ErrorOr[MessageResponse] = {

      def contentForNeedEncrypt(conv: ConversationData) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful(EncryptedContent.Empty)
        } else {
          for {
            content <- service.encryptForUsers(Set(selfUserId), msg, retries > 0, previous)
          } yield content
        }


      def dealDeviceMismatch(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.deleted).mapFuture(service.deleteClients)
          } yield {}
        }

      def dealDeviceMissing(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.missing.keySet).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _, !conv.isServerNotification))
          } yield {}
        }

      for {
        _ <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        _ = if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        contentOnlyForNeedEncrypt <- contentForNeedEncrypt(conv)
        resp <- if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          for {
            messageData <- messageStorage.get(MessageId(message.messageId))
            userData <- messageData.fold(Future.successful(Option.empty[UserData]))(tempData => usersStorage.get(tempData.userId))
            aliasData <- messageData.fold(Future.successful(Option.empty[AliasData]))(tempData => aliasStorage.get(convId,tempData.userId))
            result <- {
              val jsonObject = new JSONObject()
              jsonObject.put("text", AESUtils.base64(GenericMessage.toByteArray(message)))
              jsonObject.put("sender", selfClientId.str)

              getAssetInfo(userData, aliasData).foreach(jsonObject.put("asset", _))

              verbose(l"processDecryptedRows send BgpMessage jsonObject:${jsonObject}")
              msgClient.postBgpMessage(RConvId(convId.str), jsonObject, ignoreMissing = retries > 1, recipients).future
            }
          } yield result
        } else {
          if (contentOnlyForNeedEncrypt.estimatedSize < MaxContentSize) {
            msgClient.postMessage(RConvId(convId.str), OtrMessage(selfClientId, contentOnlyForNeedEncrypt, external, nativePush), ignoreMissing = retries > 1, recipients).future
          }
          else {
            verbose(l"Message content too big, will post as External. Estimated size: ${contentOnlyForNeedEncrypt.estimatedSize}")
            val key = AESKey()
            val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(msg))
            val newMessage = GenericMessage(Uid(msg.messageId), Proto.External(key, sha))
            encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
          }
        }
        _ <- dealDeviceMismatch(conv, resp)
        _ <- dealDeviceMissing(conv, resp)
        retry <- resp.flatMapFuture {
          case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
            clientsSyncHandler.syncSessions(missing).flatMap { err =>
              if (err.isDefined)
                error(l"syncSessions for missing clients failed: $err")
              encryptAndSend(msg, external, retries, contentOnlyForNeedEncrypt)
            }
          case _: MessageResponse.Failure =>
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
          case resp => Future.successful(Right(resp))
        }
      } yield retry
    }

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.hasCalling)
          errors.addConvUnverifiedError(convId, MessageId(message.messageId))

        Left(ErrorResponse.Unverified)
      case NonFatal(e) => Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }

  override def broadcastMessage(message: GenericMessage, retry: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): Future[Either[ErrorResponse, RemoteInstant]] =
    push.waitProcessing.flatMap { _ =>
      def broadcastRecipients = for {
        acceptedOrBlocked <- users.acceptedOrBlockedUsers.head
        myTeam <- teamId.fold(Future.successful(Set.empty[UserData]))(id => usersStorage.getByTeam(Set(id)))
        myTeamIds = myTeam.map(_.id)
      } yield acceptedOrBlocked.keySet ++ myTeamIds

      broadcastRecipients.flatMap { recp =>
        verbose(l"recipients: $recp")
        for {
          content <- service.encryptForUsers(recp, message, useFakeOnError = retry > 0, previous)
          r <- otrClient.broadcastMessage(OtrMessage(selfClientId, content), ignoreMissing = retry > 1, recp).future
          res <- loopIfMissingClients(r, retry, () => broadcastMessage(message, retry + 1, content))
        } yield res
      }
    }

  private def loopIfMissingClients(arg: Either[ErrorResponse, MessageResponse], retry: Int, fn: () => Future[Either[ErrorResponse, RemoteInstant]]): Future[Either[ErrorResponse, RemoteInstant]] =
    arg match {
      case Right(MessageResponse.Success(ClientMismatch(_, _, deleted, time))) =>
        // XXX: we are ignoring redundant clients, we rely on members list to encrypt messages, so if user left the conv then we won't use his clients on next message
        service.deleteClients(deleted).map(_ => Right(time))
      case Right(MessageResponse.Failure(ClientMismatch(_, missing, deleted, _))) =>
        service.deleteClients(deleted).flatMap { _ =>
          if (retry > 2)
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries: $missing")))
          else
            clientsSyncHandler.syncSessions(missing).flatMap {
              case None => fn()
              case Some(err) if retry < 3 =>
                error(l"syncSessions for missing clients failed: $err")
                fn()
              case Some(err) =>
                successful(Left(err))
            }
        }
      case Left(err) =>
        error(l"postOtrMessage failed with error: $err")
        successful(Left(err))
    }

  override def uploadAssetDataV3(data: LocalData, key: Option[AESKey], mime: Mime = Mime.Default, retention: Retention = Retention.Persistent) =
    key match {
      case Some(k) => CancellableFuture.lift(service.encryptAssetData(k, data)) flatMap {
        case (sha, encrypted, encryptionAlg) => assetClient.uploadAsset(Metadata(retention = retention), encrypted, Mime.Default).map { //encrypted data => Default mime
          case Right(UploadResponse(rId, _, token)) => Right(RemoteData(Some(rId), token, key, Some(sha), Some(encryptionAlg)))
          case Left(err) => Left(err)
        }
      }
      case _ => assetClient.uploadAsset(Metadata(public = true), data, mime).map {
        case Right(UploadResponse(rId, _, _)) => Right(RemoteData(Some(rId)))
        case Left(err) => Left(err)
      }
    }

  override def postSessionReset(convId: ConvId, user: UserId, client: ClientId) = {

    val msg = GenericMessage(Uid(), Proto.ClientAction.SessionReset)

    val convData = convStorage.get(convId).flatMap {
      case None => convStorage.get(ConvId(user.str))
      case conv => successful(conv)
    }

    def msgContent = service.encryptTargetedMessage(user, client, msg).flatMap {
      case Some(ct) => successful(Some(ct))
      case None =>
        for {
          _       <- clientsSyncHandler.syncSessions(Map(user -> Seq(client)))
          content <- service.encryptTargetedMessage(user, client, msg)
        } yield content
    }

    convData.flatMap {
      case None =>
        successful(Failure(s"conv not found: $convId, for user: $user in postSessionReset"))
      case Some(conv) =>
        msgContent.flatMap {
          case None => successful(Failure(s"session not found for $user, $client"))
          case Some(content) =>
            msgClient
              .postMessage(conv.remoteId, OtrMessage(selfClientId, content), ignoreMissing = true).future
              .map(SyncResult(_))
        }
    }
  }


  override def postBgpMessageForRecipients(convId: ConvId, message: GenericMessage, recipients: Option[Set[UserId]], nativePush: Boolean, uids: String, unblock: Boolean): Future[Either[ErrorResponse, RemoteInstant]] = {
    import com.waz.utils.{RichEither, RichFutureEither}

    def encryptAndSend(msg: GenericMessage, external: Option[Array[Byte]] = None, retries: Int = 0, previous: EncryptedContent = EncryptedContent.Empty): ErrorOr[MessageResponse] = {

      def contentForNeedEncrypt(conv: ConversationData) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful(EncryptedContent.Empty)
        } else {
          for {
            us <- recipients.fold(members.getActiveUsers(convId).map(_.toSet))(rs => Future.successful(rs))
            content <- service.encryptForUsers(us, msg, retries > 0, previous)
          } yield content
        }

      def dealDeviceMismatch(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.deleted).mapFuture(service.deleteClients)
          } yield {}
        }

      def dealDeviceMissing(conv: ConversationData, resp: Either[ErrorResponse, OtrClient.MessageResponse]) =
        if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          Future.successful({})
        } else {
          for {
            _ <- resp.map(_.missing.keySet).mapFuture(convsService.addUnexpectedMembersToConv(conv.id, _, !conv.isServerNotification))
          } yield {}
        }

      for {
        _ <- push.waitProcessing
        Some(conv) <- convStorage.get(convId)
        _ = if (conv.verified == Verification.UNVERIFIED) throw UnverifiedException
        contentOnlyForNeedEncrypt <- contentForNeedEncrypt(conv)
        resp <- if (conv.convType == IConversation.Type.THROUSANDS_GROUP) {
          for {
            messageData <- messageStorage.get(MessageId(message.messageId))
            userData <- messageData.fold(Future.successful(Option.empty[UserData]))(tempData => usersStorage.get(tempData.userId))
            aliasData <- messageData.fold(Future.successful(Option.empty[AliasData]))(tempData => aliasStorage.get(convId,tempData.userId))
            result <- {
              val jsonObject = new JSONObject()
              jsonObject.put("text", AESUtils.base64(GenericMessage.toByteArray(message)))
              jsonObject.put("sender", selfClientId.str)
              jsonObject.put("recipients", new JSONArray(uids))

              getAssetInfo(userData, aliasData).foreach(jsonObject.put("asset", _))

              if (unblock) {
                jsonObject.put("unblock", unblock)
              }
              msgClient.postBgpMessage(conv.remoteId, jsonObject, ignoreMissing = retries > 1, None).future
            }
          } yield result
        } else {
          if (contentOnlyForNeedEncrypt.estimatedSize < MaxContentSize) {
            msgClient.postMessage(conv.remoteId, OtrMessage(selfClientId, contentOnlyForNeedEncrypt, external, nativePush, unblock), ignoreMissing = false, recipients).future
          }
          else {
            verbose(l"Message content too big, will post as External. Estimated size: ${contentOnlyForNeedEncrypt.estimatedSize}")
            val key = AESKey()
            val (sha, data) = AESUtils.encrypt(key, GenericMessage.toByteArray(msg))
            val newMessage = GenericMessage(Uid(msg.messageId), Proto.External(key, sha))
            encryptAndSend(newMessage, Some(data)) //abandon retries and previous EncryptedContent
          }
        }
        _ <- dealDeviceMismatch(conv, resp)
        _ <- dealDeviceMissing(conv, resp)
        retry <- resp.flatMapFuture {
          case MessageResponse.Failure(ClientMismatch(_, missing, _, _)) if retries < 3 =>
            clientsSyncHandler.syncSessions(missing).flatMap { err =>
              if (err.isDefined)
                error(l"syncSessions for missing clients failed: $err")
              encryptAndSend(msg, external, retries, contentOnlyForNeedEncrypt)
            }
          case _: MessageResponse.Failure =>
            successful(Left(internalError(s"postEncryptedMessage/broadcastMessage failed with missing clients after several retries")))
          case resp => Future.successful(Right(resp))
        }
      } yield retry

    }

    encryptAndSend(message).recover {
      case UnverifiedException =>
        if (!message.hasCalling)
          errors.addConvUnverifiedError(convId, MessageId(message.messageId))

        Left(ErrorResponse.Unverified)
      case NonFatal(e) => Left(ErrorResponse.internalError(e.getMessage))
    }.mapRight(_.mismatch.time)
  }

  private def getAssetInfo(userData: Option[UserData],aliasData: Option[AliasData]): Option[JSONObject] = {

    var resultJson: JSONObject = null

    userData.foreach { tempData =>
      resultJson = new JSONObject()
      resultJson.put("name", aliasData.filter(_.getAliasName.nonEmpty).map(_.getAliasName).getOrElse(tempData.name.str))
      tempData.rAssetId.foreach(resultJson.put("avatar_key", _))
    }
    Option(resultJson)
  }
}

object OtrSyncHandler {

  case object UnverifiedException extends Exception

  case class OtrMessage(sender: ClientId, recipients: EncryptedContent, external: Option[Array[Byte]] = None, nativePush: Boolean = true, unblock: Boolean = false,video: Boolean = false,call_user_id: String = null,call_user_name: String = null,call_conversation_id: String = null,call_type : String = null)

  val MaxInlineSize  = 10 * 1024
  val MaxContentSize = 256 * 1024 // backend accepts 256KB for otr messages, but we would prefer to send less
}
