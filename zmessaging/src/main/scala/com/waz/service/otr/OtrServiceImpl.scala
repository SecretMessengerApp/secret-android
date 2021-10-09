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
package com.waz.service.otr

import java.io._

import android.util.Base64
import com.waz.cache.{CacheService, LocalData}
import com.waz.content.{GlobalPreferences, MembersStorageImpl, OtrClientsStorage}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.GenericContent.ClientAction.SessionReset
import com.waz.model.GenericContent._
import com.waz.model._
import com.waz.model.otr._
import com.waz.service._
import com.waz.service.push.PushNotificationEventsStorage.PlainWriter
import com.waz.service.tracking.TrackingService
import com.waz.sync.SyncServiceHandle
import com.waz.sync.client.OtrClient
import com.waz.sync.client.OtrClient.EncryptedContent
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.events.{EventContext, Signal}
import com.wire.cryptobox.CryptoException
import javax.crypto.Mac
import org.json.JSONObject

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try


trait OtrService {
  def sessions: CryptoSessionService // only for tests

  def resetSession(conv: ConvId, user: UserId, client: ClientId): Future[SyncId]

  def decryptCloudMessage(data: Array[Byte], mac: Array[Byte]): Future[Option[JSONObject]]

  def encryptTargetedMessage(user: UserId, client: ClientId, msg: GenericMessage): Future[Option[OtrClient.EncryptedContent]]

  def deleteClients(userMap: Map[UserId, Seq[ClientId]]): Future[Any]

  def fingerprintSignal(userId: UserId, cId: ClientId): Signal[Option[Array[Byte]]]

  def encryptForUsers(users:          Set[UserId],
                      msg:            GenericMessage,
                      useFakeOnError: Boolean = false,
                      partialResult:  EncryptedContent): Future[EncryptedContent]

  def encryptAssetData(key: AESKey, data: LocalData):Future[(Sha256, LocalData, EncryptionAlgorithm)]

  def decryptAssetData(assetId:    AssetId,
                       otrKey:     Option[AESKey],
                       sha:        Option[Sha256],
                       data:       Option[Array[Byte]],
                       encryption: Option[EncryptionAlgorithm]): Option[Array[Byte]]

  def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter): Future[Either[OtrError, Unit]]

  def parseGenericMessage(msgEvent: OtrMessageEvent, msg: GenericMessage): Option[MessageEvent]

  def parseBgpGenericMessageFix(otrMsg: JSONObject, convEvent: ConversationEvent, genericMsg: GenericMessage): Option[MessageEvent]

}

class OtrServiceImpl(selfUserId:     UserId,
                     clientId:       ClientId,
                     clients:        OtrClientsService,
                     cryptoBox:      CryptoBoxService,
                     members:        MembersStorageImpl,
                     sync:           SyncServiceHandle,
                     cache:          CacheService,
                     metadata:       MetaDataService,
                     clientsStorage: OtrClientsStorage,
                     prefs:          GlobalPreferences,
                     tracking:       TrackingService) extends OtrService with DerivedLogTag {
  import OtrService._
  import Threading.Implicits.Background

  //  lazy val sessions = returning(cryptoBox.sessions) { sessions =>
  //    // request self clients sync to update prekeys on backend
  //    // we've just created a session from message, this means that some user had to obtain our prekey from backend (so we can upload it)
  //    // using signal and sync interval parameter to limit requests to one an hour
  //    Signal.wrap(sessions.onCreateFromMessage).throttle(15.seconds) { _ => clients.requestSyncIfNeeded(1.hour) }


  lazy val sessions = cryptoBox.sessions

  override def parseGenericMessage(otrMsg: OtrMessageEvent, genericMsg: GenericMessage): Option[MessageEvent] = {
    val conv = otrMsg.convId
    val time = otrMsg.time
    val from = otrMsg.from
    val sender = otrMsg.sender
    val extData = otrMsg.externalData
    val localTime = otrMsg.localTime
    if (!(GenericMessage.isBroadcastMessage(genericMsg) || from == selfUserId) && conv.str == selfUserId.str) {
      warn(l"Received a message to the self-conversation by someone else than self and it's not a broadcast")
      None
    } else {
      genericMsg match {
        case GenericMessage(_, External(key, sha)) =>
          decodeExternal(key, Some(sha), extData) match {
            case None =>
              error(l"External message could not be decoded External($key, $sha), data: $extData")
              Some(OtrErrorEvent(conv, time, from, DecryptionError("symmetric decryption failed-3", from, sender)))
            case Some(GenericMessage(_, Calling(content))) =>
              Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
            case Some(msg) =>
              Some(GenericMessageEvent(conv, time, from, msg, null, null).withLocalTime(localTime))
          }
        case GenericMessage(mId, SessionReset) if metadata.internalBuild => // display session reset notifications in internal build
          Some(GenericMessageEvent(conv, time, from, GenericMessage(mId, Text("System msg: session reset", Nil, Nil, expectsReadConfirmation = false)), null, null))
        case GenericMessage(_, SessionReset) => None // ignore session reset notifications
        case GenericMessage(_, Calling(content)) =>
          Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
        case msg =>
          Some(GenericMessageEvent(conv, time, from, msg, null, null).withLocalTime(localTime))
      }
    }
  }


  override def parseBgpGenericMessageFix(otrMsg: JSONObject, convEvent: ConversationEvent, genericMsg: GenericMessage): Option[MessageEvent] = {
    if (convEvent.isInstanceOf[BgpMessageEvent]) {
      parseBgpGenericMessage(convEvent.asInstanceOf[BgpMessageEvent], genericMsg)
    } else {
      import android.text.TextUtils
      val conv = convEvent.convId
      val time = convEvent.time
      val localTime = convEvent.localTime
      val from = convEvent.from

      val data = otrMsg.optJSONObject("data")
      val sender = ClientId(data.optString("sender"))
      val extData = Option(data.optString("text")).filter { x => !TextUtils.isEmpty(x)
      }.map { x =>
        Base64.decode(x, Base64.NO_WRAP)
      }
      val name = data.optString("name")
      val asset = data.optString("asset")

      if (!(GenericMessage.isBroadcastMessage(genericMsg) || from == selfUserId) && conv == selfUserId.str) {
        warn(l"Received a message to the self-conversation by someone else than self and it's not a broadcast")
        None
      } else {
        genericMsg match {
          case GenericMessage(_, External(key, sha)) =>
            decodeExternal(key, Some(sha), extData) match {
              case None =>
                error(l"External message could not be decoded External($key, $sha), data: $extData")
                Some(OtrErrorEvent(conv, time, from, DecryptionError("symmetric decryption failed-2", from, sender)))
              case Some(GenericMessage(_, Calling(content))) =>
                //Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
                Some(UnknownMessageEvent(conv,time,from,new JSONObject()))
              case Some(msg) =>
                Some(GenericMessageEvent(conv, time, from, msg, name, asset).withLocalTime(localTime))
            }
          case GenericMessage(mId, SessionReset) if metadata.internalBuild => // display session reset notifications in internal build
            Some(GenericMessageEvent(conv, time, from, GenericMessage(mId, Text("System msg: session reset", Nil, Nil, expectsReadConfirmation = false)), name, asset))
          case GenericMessage(_, SessionReset) => None // ignore session reset notifications
          case GenericMessage(_, Calling(content)) =>
            //Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
            Some(UnknownMessageEvent(conv,time,from,new JSONObject()))
          case msg =>
            Some(GenericMessageEvent(conv, time, from, msg, name, asset).withLocalTime(localTime))
        }
      }
    }
  }

  private def parseBgpGenericMessage(otrMsg: BgpMessageEvent, genericMsg: GenericMessage): Option[MessageEvent] = {
    val conv = otrMsg.convId
    val time = otrMsg.time
    val from = otrMsg.from
    val sender = otrMsg.sender
    val extData = otrMsg.externalData
    val name = otrMsg.name
    val asset = otrMsg.asset
    val localTime = otrMsg.localTime
    if (!(GenericMessage.isBroadcastMessage(genericMsg) || from == selfUserId) && conv.str == selfUserId.str) {
      warn(l"Received a message to the self-conversation by someone else than self and it's not a broadcast")
      None
    } else {
      genericMsg match {
        case GenericMessage(_, External(key, sha)) =>
          decodeExternal(key, Some(sha), extData) match {
            case None =>
              error(l"External message could not be decoded External($key, $sha), data: $extData")
              Some(OtrErrorEvent(conv, time, from, DecryptionError("symmetric decryption failed -1", from, sender)))
            case Some(GenericMessage(_, Calling(content))) =>
              //Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
              Some(UnknownMessageEvent(conv,time,from,new JSONObject()))
            case Some(msg) =>
              Some(GenericMessageEvent(conv, time, from, msg, name, asset).withLocalTime(localTime))
          }
        case GenericMessage(mId, SessionReset) if metadata.internalBuild => // display session reset notifications in internal build
          Some(GenericMessageEvent(conv, time, from, GenericMessage(mId, Text("System msg: session reset", Nil, Nil, expectsReadConfirmation = false)), name, asset))
        case GenericMessage(_, SessionReset) => None // ignore session reset notifications
        case GenericMessage(_, Calling(content)) =>
          //Some(CallMessageEvent(conv, time, from, sender, content)) //call messages need sender client id
          Some(UnknownMessageEvent(conv,time,from,new JSONObject()))
        case msg =>
          Some(GenericMessageEvent(conv, time, from, msg, name, asset).withLocalTime(localTime))
      }
    }
  }

  private def decodeExternal(key: AESKey, sha: Option[Sha256], extData: Option[Array[Byte]]) =
    for {
      data  <- extData if sha.forall(_.matches(data))
      plain <- Try(AESUtils.decrypt(key, data)).toOption
      msg  <- Try(GenericMessage(plain)).toOption
    } yield msg

  override def decryptStoredOtrEvent(ev: OtrEvent, eventWriter: PlainWriter)
  : Future[Either[OtrError, Unit]] =
    clients.getOrCreateClient(ev.from, ev.sender) flatMap { _ =>
      sessions.decryptMessage(SessionId(ev.from, ev.sender), ev.ciphertext, eventWriter)
        .map(Right(_))
        .recoverWith {
          case e: CryptoException =>
            import CryptoException.Code._
            e.code match {
              case DUPLICATE_MESSAGE =>
                verbose(l"detected duplicate message for event: $ev")
                Future successful Left(Duplicate)
              case OUTDATED_MESSAGE =>
                error(l"detected outdated message for event: $ev")
                //reportOtrError(e, ev)
                Future successful Left(DecryptionError("OUTDATED_MESSAGE", ev.from, ev.sender))
              case REMOTE_IDENTITY_CHANGED =>
                //reportOtrError(e, ev)
                Future successful Left(IdentityChangedError(ev.from, ev.sender))
              case _ =>
                //reportOtrError(e, ev)
                Future successful Left(DecryptionError(e.code.toString, ev.from, ev.sender))
            }

          case _ =>
            Future successful Left(DecryptionError("UnKnownCryptoException-2", ev.from, ev.sender))
        }
    }

  // update client info and send error report, we want client info to somehow track originating platform
  private def reportOtrError(e: CryptoException, ev: OtrEvent) = sync.syncClients(ev.from) map { _ =>
    clients.getClient(ev.from, ev.sender) foreach { _ => tracking.exception(e, "otr error") }
  }

  def resetSession(conv: ConvId, user: UserId, client: ClientId): Future[SyncId] =
    for {
      _ <- sessions.deleteSession(SessionId(user, client))
      _ <- clientsStorage.updateVerified(user, client, verified = false)
      _ <- sync.syncPreKeys(user, Set(client))
      syncId <- sync.postSessionReset(conv, user, client)
    } yield syncId

  def decryptCloudMessage(data: Array[Byte], mac: Array[Byte]): Future[Option[JSONObject]] = clients.getSelfClient map {
    case Some(client @ Client(_, _, _, _, _, _, Some(key), _, _)) =>
      verbose(l"decrypting gcm for client $client")
      if (hmacSha256(key, data).toSeq != mac.toSeq) {
        warn(l"gcm MAC doesn't match")
        None
      } else
        Try(new JSONObject(new String(AESUtils.decrypt(key.encKey, data), "utf8"))).toOption
    case c =>
      warn(l"can not decrypt gcm, no signaling key found: $c")
      None
  }

  def encryptTargetedMessage(user: UserId, client: ClientId, msg: GenericMessage): Future[Option[OtrClient.EncryptedContent]] = {
    val msgData = GenericMessage.toByteArray(msg)

    sessions.withSession(SessionId(user, client)) { session =>
      EncryptedContent(Map(user -> Map(client -> session.encrypt(msgData))))
    }
  }

  /**
    * @param useFakeOnError when true, we will return bomb emoji as msg content on encryption errors (for failing client)
    * @param partialResult partial content encrypted in previous run, we will use that instead of encrypting again when available
    */
  override def encryptForUsers(users:          Set[UserId],
                               msg:            GenericMessage,
                               useFakeOnError: Boolean = false,
                               partialResult:  EncryptedContent) = {

    val msgData = GenericMessage.toByteArray(msg)

    def encryptForClients(user: UserId, clients: Seq[Client], msgData: Array[Byte], useFakeOnError: Boolean, partialResult: EncryptedContent) =
      Future.traverse(clients) { client =>

        val previous = partialResult.content.get(user).flatMap(_.get(client.id)).filter(arr => arr.nonEmpty && arr.sameElements(EncryptionFailedMsg))

        previous match {
          case Some(bytes) => Future successful Some(client.id -> bytes)
          case None =>
            verbose(l"encrypt for client: $client")
            sessions.withSession(SessionId(user, client.id)) { session => client.id -> session.encrypt(msgData) }.recover {
              case e: Throwable =>
                tracking.exception(e, s"encryption failed")
                if (useFakeOnError) Some(client.id -> EncryptionFailedMsg) else None
            }
        }

      }.map(ms => user -> ms.flatten.toMap)

    Future.traverse(users) { user =>
      // list of clients to which the message should be sent for given user
      val targetClients = clientsStorage.getClients(user).map { cs =>
        if (user == selfUserId) cs.filter(_.id != clientId) else cs
      }

      targetClients.flatMap(encryptForClients(user, _, msgData, useFakeOnError, partialResult))
    } map (res => EncryptedContent(res.toMap.filter(_._2.nonEmpty)))
  }

  def deleteClients(userMap: Map[UserId, Seq[ClientId]]): Future[Any] = Future.traverse(userMap) {
    case (user, cs) => clients.removeClients(user, cs) flatMap { _ =>
      Future.traverse(cs) { c => sessions.deleteSession(SessionId(user, c)) }
    }
  }

  def fingerprintSignal(userId: UserId, cId: ClientId): Signal[Option[Array[Byte]]] =
    if (userId == selfUserId && cId == clientId) Signal.future(cryptoBox { cb => Future successful cb.getLocalFingerprint })
    else cryptoBox.sessions.remoteFingerprint(SessionId(userId, cId))

  def encryptAssetDataCBC(key: AESKey, data: LocalData): Future[(Sha256, LocalData, EncryptionAlgorithm)] = {
    import Threading.Implicits.Background

    def encryptFile() = cache.createForFile(length = Some(sizeWithPaddingAndIV(data.length))) map { entry =>
      val mac = AESUtils.encrypt(key, data.inputStream, entry.outputStream)
      (mac, entry, EncryptionAlgorithm.AES_CBC)
    }

    def encryptBytes() = {
      val bos = new ByteArrayOutputStream()
      val mac = AESUtils.encrypt(key, data.inputStream, bos)
      cache.addData(CacheKey(), bos.toByteArray) map { (mac, _, EncryptionAlgorithm.AES_CBC) }
    }

    data.byteArray.fold(encryptFile()){ _ => encryptBytes() }
  }

  // TODO: AN-5168. Right now throws a NotImplementedError when called; to be implemented later
  def encryptAssetDataGCM(key: AESKey, data: LocalData): Future[(Sha256, LocalData, EncryptionAlgorithm)] = ???

  def encryptAssetData(key: AESKey, data: LocalData):Future[(Sha256, LocalData, EncryptionAlgorithm)] =
    if(prefs.v31AssetsEnabled) encryptAssetDataGCM(key, data)
    else encryptAssetDataCBC(key, data)

  def decryptAssetDataCBC(assetId: AssetId, otrKey: Option[AESKey], sha: Option[Sha256], data: Option[Array[Byte]]): Option[Array[Byte]] = {
    data.flatMap { arr =>
      otrKey.map { key =>
        if (sha.forall(_.str == com.waz.utils.sha2(arr))) Try(AESUtils.decrypt(key, arr)).toOption else None
      }.getOrElse {
        warn(l"got otr asset event without otr key: $assetId")
        Some(arr)
      }
    }.filter(_.nonEmpty)
  }

  // TODO: AN-5167. Right now throws a NotImplementedError when called; to be implemented later
  def decryptAssetDataGCM(assetId: AssetId, otrKey: Option[AESKey], sha: Option[Sha256], data: Option[Array[Byte]]): Option[Array[Byte]] = ???

  override def decryptAssetData(assetId: AssetId, otrKey: Option[AESKey], sha: Option[Sha256], data: Option[Array[Byte]], encryption: Option[EncryptionAlgorithm]): Option[Array[Byte]] =
    (prefs.v31AssetsEnabled, encryption) match {
      case (true, Some(EncryptionAlgorithm.AES_GCM)) => decryptAssetDataGCM(assetId, otrKey, sha, data)
      case _ => decryptAssetDataCBC(assetId, otrKey, sha, data)
    }

}

object OtrService {

  val EncryptionFailedMsg = "\uD83D\uDCA3".getBytes("utf8")

  case class SessionId(user: UserId, client: ClientId) {
    override def toString: String = s"${user}_$client"
  }

  def hmacSha256(key: SignalingKey, data: Array[Byte]) = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(key.mac)
    mac.doFinal(data)
  }

  def sizeWithPaddingAndIV(size: Long) = size + (32L - (size % 16L))
}
