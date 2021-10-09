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
package com.waz.model

import android.text.TextUtils
import android.util.Base64
import com.waz.api.IConversation
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.log.LogShow.SafeToLog
import com.waz.model.ConversationEvent.ConversationEventDecoder
import com.waz.model.Event.EventDecoder
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.otr.{Client, ClientId}
import com.waz.service.PropertyKey
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.OtrClient
import com.waz.utils.JsonDecoder._
import com.waz.utils.crypto.AESUtils
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.{JSONException, JSONObject}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

sealed trait Event {

  //FIXME do we still need this separation?
  var localTime: LocalInstant = LocalInstant.Epoch

  def withCurrentLocalTime(): this.type = {
    localTime = LocalInstant.Now
    this
  }

  def withLocalTime(time: LocalInstant): this.type = {
    localTime = time
    this
  }

  def maybeLocalTime: Option[LocalInstant] = if (localTime.isEpoch) None else Some(localTime)
}

sealed trait UserEvent extends Event
sealed trait OtrClientEvent extends UserEvent

sealed trait RConvEvent extends Event {
  val convId: RConvId
}
object RConvEvent extends (Event => RConvId) {
  def apply(ev: Event): RConvId = ev match {
    case ev: RConvEvent => ev.convId
    case _              => RConvId.Empty
  }
}
case class UserUpdateEvent(user: UserInfo, removeIdentity: Boolean = false) extends UserEvent
case class UserConnectionEvent(convId: RConvId, from: UserId, to: UserId, message: Option[String], status: ConnectionStatus, lastUpdated: RemoteInstant, fromUserName: Option[Name] = None) extends UserEvent with RConvEvent
case class UserDeleteEvent(user: UserId) extends UserEvent
case class OtrClientAddEvent(client: Client) extends OtrClientEvent
case class OtrClientRemoveEvent(client: ClientId) extends OtrClientEvent
case class OtrUserPasswordResetEvent(id: UserId) extends OtrClientEvent

case class ContactJoinEvent(user: UserId, name: Name) extends Event

case class PushTokenRemoveEvent(token: PushToken, senderId: String, client: Option[String]) extends Event

sealed trait ConversationEvent extends RConvEvent {
  val time: RemoteInstant
  val from: UserId
  val convType: Int
}

// events that affect conversation state
sealed trait ConversationStateEvent extends ConversationEvent

// events that add or modify some message
sealed trait MessageEvent extends ConversationEvent

case class UnknownEvent(json: JSONObject) extends Event

case class UnknownMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId,json : JSONObject) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class UnknownConvEvent(json: JSONObject) extends ConversationEvent {
  override val convId: RConvId = RConvId()
  override val from: UserId = UserId()
  override val time: RemoteInstant = RemoteInstant.Epoch //TODO: epoch?
  override val convType: Int = -1
}

case class CreateConversationEvent(convId: RConvId, time: RemoteInstant, from: UserId, data: ConversationResponse) extends ConversationStateEvent {
  override val convType: Int = -1
}

case class MessageTimerEvent(convId: RConvId, time: RemoteInstant, from: UserId, duration: Option[FiniteDuration], eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class RenameConversationEvent(convId: RConvId, time: RemoteInstant, from: UserId, name: Name, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class GenericMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, content: GenericMessage, name: String, avatar_key: String) extends MessageEvent {
  override val convType: Int = -1
}

case class CallMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId, content: String) extends MessageEvent {
  override val convType: Int = -1
}

sealed trait OtrError
case object Duplicate extends OtrError
case class DecryptionError(msg: String, from: UserId, sender: ClientId) extends OtrError
case class IdentityChangedError(from: UserId, sender: ClientId) extends OtrError
case class UnknownOtrErrorEvent(json: JSONObject) extends OtrError

case class OtrErrorEvent(convId: RConvId, time: RemoteInstant, from: UserId, error: OtrError) extends MessageEvent {
  override val convType: Int = -1
}

case class GenericAssetEvent(convId: RConvId, time: RemoteInstant, from: UserId, content: GenericMessage, dataId: RAssetId, data: Option[Array[Byte]], name: String, asset: String) extends MessageEvent {
  override val convType: Int = -1
}

case class TypingEvent(convId: RConvId, time: RemoteInstant, from: UserId, isTyping: Boolean) extends ConversationEvent {
  override val convType: Int = -1
}

case class ConvChangeTypeEvent(convId: RConvId, convType: Int, time: RemoteInstant, from: UserId, newType: Int, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent

case class ConvInviteMembersEvent(convId: RConvId, convType: Int, time: RemoteInstant, from: UserId, contentString: String, code: String, inviteType: Int, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent

case class ConvUpdateSettingEvent(convId: RConvId, convType: Int, time: RemoteInstant, from: UserId, contentString: String, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent

case class ConvUpdateSettingSingleEvent(convId: RConvId, convType: Int, time: RemoteInstant, from: UserId, contentString: String, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent

case class MemberJoinEvent(convId: RConvId, time: RemoteInstant, from: UserId, userIds: Seq[UserId], existIds: Seq[UserId], firstEvent: Boolean = false, eid: Option[MessageId], memsum: Option[Int]) extends MessageEvent with ConversationStateEvent with ConversationEvent {
  override val convType: Int = -1
}

case class MemberLeaveEvent(convId: RConvId, time: RemoteInstant, from: UserId, userIds: Seq[UserId], eid: Option[MessageId],memsum: Option[Int]) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class MemberUpdateEvent(convId: RConvId, time: RemoteInstant, from: UserId, state: ConversationState, eid: Option[MessageId]) extends ConversationStateEvent {
  override val convType: Int = -1
}

case class ConversationReceiptModeEvent(convId: RConvId, time: RemoteInstant, from: UserId, receiptMode: Int, eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class ConnectRequestEvent(convId: RConvId, time: RemoteInstant, from: UserId, message: String, recipient: UserId, name: Name, email: Option[String], eid: Option[MessageId]) extends MessageEvent with ConversationStateEvent {
  override val convType: Int = -1
}

case class ConversationAccessEvent(convId: RConvId, time: RemoteInstant, from: UserId, access: Set[Access], accessRole: AccessRole, eid: Option[MessageId]) extends ConversationStateEvent {
  override val convType: Int = -1
}

case class ConversationCodeUpdateEvent(convId: RConvId, time: RemoteInstant, from: UserId, link: ConversationData.Link, eid: Option[MessageId]) extends ConversationStateEvent {
  override val convType: Int = -1
}

case class ConversationCodeDeleteEvent(convId: RConvId, time: RemoteInstant, from: UserId, eid: Option[MessageId]) extends ConversationStateEvent {
  override val convType: Int = -1
}

sealed trait OtrEvent extends ConversationEvent {
  val sender: ClientId
  val recipient: ClientId
  val ciphertext: Array[Byte]
}

case class OtrMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId, recipient: ClientId, ciphertext: Array[Byte], externalData: Option[Array[Byte]] = None) extends OtrEvent {
  override val convType: Int = -1
}

case class BgpMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId, recipient: ClientId, ciphertext: Array[Byte], externalData: Option[Array[Byte]] = None, name: String, asset: String) extends OtrEvent {
  override val convType: Int = IConversation.Type.THROUSANDS_GROUP.id
}

case class BasicNotificationMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, sender: ClientId,
                                         recipient: ClientId, ciphertext: Array[Byte], data: Option[JSONObject],convType: Int, eid: Option[MessageId]) extends OtrEvent

case class AliasEvent(convId: RConvId, time: RemoteInstant, from: UserId, contentString: String) extends ConversationStateEvent {
  override val convType: Int = -1
}

case class UserNoticeEvent(msgType:String,msgData:JSONObject) extends UserEvent

sealed trait PropertyEvent extends UserEvent

case class ReadReceiptEnabledPropertyEvent(value: Int) extends PropertyEvent
case class UnknownPropertyEvent(key: PropertyKey, value: String) extends PropertyEvent

case class ConversationState(archived: Option[Boolean] = None,
                             archiveTime: Option[RemoteInstant] = None,
                             muted: Option[Boolean] = None,
                             muteTime: Option[RemoteInstant] = None,
                             mutedStatus: Option[Int] = None,
                             place_top: Option[Boolean] = None,
                             auto_reply: Option[String] = None,
                             auto_reply_ref: Option[String] = None,
                             alias_name: Option[Boolean] = None,
                             alias_name_ref: Option[Name] = None) extends SafeToLog

object ConversationState {
  private def encode(state: ConversationState, o: JSONObject) = {
    state.archived foreach (o.put("otr_archived", _))
    state.archiveTime foreach { time => o.put("otr_archived_ref", JsonEncoder.encodeISOInstant(time.instant)) }
    state.muted.foreach(o.put("otr_muted", _))
    state.muteTime foreach { time => o.put("otr_muted_ref", JsonEncoder.encodeISOInstant(time.instant)) }
    state.place_top.foreach { place_top => o.put("place_top", place_top) }
    state.mutedStatus.foreach { status => o.put("otr_muted_status", status) }
    state.auto_reply.foreach(o.put("auto_reply", _))
    state.auto_reply_ref.foreach(o.put("auto_reply_ref", _))
    state.alias_name.foreach(o.put("alias_name", _))
    state.alias_name_ref.foreach(o.put("alias_name_ref", _))
  }

  implicit lazy val Encoder: JsonEncoder[ConversationState] = new JsonEncoder[ConversationState] {
    override def apply(state: ConversationState): JSONObject = JsonEncoder { o => encode(state, o) }
  }

  implicit lazy val Decoder: JsonDecoder[ConversationState] = new JsonDecoder[ConversationState] {
    import com.waz.utils.JsonDecoder._

    override def apply(implicit js: JSONObject): ConversationState = {
      val archiveTime = decodeOptISOInstant('otr_archived_ref).map(RemoteInstant(_))
      val archived = archiveTime.map( _ => decodeBool('otr_archived))

      val (muted, muteTime) = (decodeOptISOInstant('otr_muted_ref).map(RemoteInstant(_)),
        decodeOptISOInstant('muted_time).map(RemoteInstant(_))) match {
        case (Some(t), Some(t1)) if t1.isAfter(t) => (decodeOptBoolean('muted), Some(t1))
        case (t @ Some(_), _)                     => (decodeOptBoolean('otr_muted), t)
        case (_, t @ Some(_))                     => (decodeOptBoolean('muted), t)
        case _                                    => (None, None)
      }

      val mutedStatus = decodeOptInt('otr_muted_status)
      val place_top = decodeOptBoolean('place_top)
      ConversationState(archived, archiveTime, muted, muteTime, mutedStatus, place_top
        , 'auto_reply, 'auto_reply_ref, 'alias_name, 'alias_name_ref)
    }
  }

}

object Event {

  implicit object EventDecoder extends JsonDecoder[Event] with DerivedLogTag {

    import com.waz.utils.JsonDecoder._

    def connectionEvent(implicit js: JSONObject, name: Option[Name]) = UserConnectionEvent('conversation, 'from, 'to, 'message, ConnectionStatus('status), JsonDecoder.decodeISORemoteInstant('last_update), fromUserName = name)

    def contactJoinEvent(implicit js: JSONObject) = ContactJoinEvent('id, 'name)

    def gcmTokenRemoveEvent(implicit js: JSONObject) = PushTokenRemoveEvent(token = 'token, senderId = 'app, client = 'client)

    override def apply(implicit js: JSONObject): Event = Try {

      decodeString('type) match {
        case tpe if tpe.startsWith("conversation") => ConversationEventDecoder(js)
        case tpe if tpe.startsWith("team") => TeamEvent.TeamEventDecoder(js)
        case "user.update" => UserUpdateEvent(JsonDecoder[UserInfo]('user))
        case "user.identity-remove" => UserUpdateEvent(JsonDecoder[UserInfo]('user), true)
        case "user.connection" => connectionEvent(js.getJSONObject("connection"), JsonDecoder.opt('user, _.getJSONObject("user")) flatMap (JsonDecoder.decodeOptName('name)(_)))
        case "user.contact-join" => contactJoinEvent(js.getJSONObject("user"))
        case "user.push-remove" => gcmTokenRemoveEvent(js.getJSONObject("token"))
        case "user.delete" => UserDeleteEvent(user = 'id)
        case "user.client-add" => OtrClientAddEvent(OtrClient.ClientsResponse.client(js.getJSONObject("client")))
        case "user.client-remove" => OtrClientRemoveEvent(decodeId[ClientId]('id)(js.getJSONObject("client"), implicitly))
        case "user.properties-set" => PropertyEvent.Decoder(js)
        case "user.password-reset" =>
          OtrUserPasswordResetEvent(decodeUserId('id)(js.getJSONObject("user")))
        case _ =>
          error(l"unhandled event: $js")
          UnknownEvent(js)
      }
    } .getOrElse(UnknownEvent(js))
  }
}

object UserConnectionEvent {
  implicit lazy val Decoder: JsonDecoder[UserConnectionEvent] = new JsonDecoder[UserConnectionEvent] {
    override def apply(implicit js: JSONObject): UserConnectionEvent = EventDecoder.connectionEvent(js, name = None)
  }
}

object ConversationEvent extends DerivedLogTag {

  import OtrErrorEvent._

  private var clientId: String = ""

  def unapply(e: ConversationEvent): Option[(RConvId, RemoteInstant, UserId)] =
    Some((e.convId, e.time, e.from))

  def setClientId(clientId: String): Unit = {
    this.clientId = clientId
  }

  implicit lazy val ConversationEventDecoder: JsonDecoder[ConversationEvent] = new JsonDecoder[ConversationEvent] {

    def decodeBytes(str: String) = Base64.decode(str, Base64.NO_WRAP)

    def bgpMessageEvent(convId: RConvId, time: RemoteInstant, from: UserId, name: String, asset: String)(implicit data: JSONObject) =
      BgpMessageEvent(convId, time, from, ClientId('sender), ClientId(clientId), decodeBytes('text), decodeOptString('data) map decodeBytes, name, asset)

    override def apply(implicit js: JSONObject): ConversationEvent = Try {

      lazy val contentJsonData = if (js.has("data") && !js.isNull("data")) Try(js.getJSONObject("data")).toOption else None

      val time = RemoteInstant(decodeISOInstant('time))
      val asset = contentJsonData.flatMap(it => Option(it.optJSONObject("asset"))).getOrElse(new JSONObject())
      val msgData = contentJsonData.flatMap(it => Option(it.optJSONObject(ServerIdConst.KEY_TEXTJSON_MSGDATA))).getOrElse(new JSONObject())

      val eid = if (js.has("eid")) Some(MessageId(js.getString("eid"))) else None

      decodeString('type) match {
        case "conversation.create" => CreateConversationEvent('conversation, time, 'from, ConversationResponse.Decoder('data))
        case "conversation.rename" => RenameConversationEvent('conversation, time, 'from, decodeName('name)(contentJsonData.get), eid)
        case "conversation.member-join" =>
          val userIds = decodeUserIdSeq('user_ids)(contentJsonData.get)
          val existIds = decodeUserIdSeq('users_exist)(contentJsonData.get)
          MemberJoinEvent('conversation, time, 'from, userIds, existIds, firstEvent = false, eid, decodeOptInt('memsum)(contentJsonData.get))
        case "conversation.member-leave" =>
          MemberLeaveEvent('conversation, time, 'from, decodeUserIdSeq('user_ids)(contentJsonData.get), eid, decodeOptInt('memsum)(contentJsonData.get))
        case "conversation.member-update" | "conversation.update-autoreply" =>
          MemberUpdateEvent('conversation, time, 'from, ConversationState.Decoder(contentJsonData.get), eid)
        case "conversation.connect-request" =>
          ConnectRequestEvent('conversation, time, 'from, decodeString('message)(contentJsonData.get),
            decodeUserId('recipient)(contentJsonData.get), decodeName('name)(contentJsonData.get), decodeOptString('email)(contentJsonData.get), eid)
        case "conversation.typing" =>
          TypingEvent('conversation, time, 'from, isTyping = contentJsonData.fold(false)(data => decodeString('status)(data) == "started"))
        case "conversation.otr-message-add" =>
          OtrMessageEvent('conversation, time, 'from, decodeClientId('sender)(contentJsonData.get),
            decodeClientId('recipient)(contentJsonData.get), decodeByteString('text)(contentJsonData.get), decodeOptByteString('data)(contentJsonData.get))
        case "conversation.access-update" =>
          ConversationAccessEvent('conversation, time, 'from, decodeAccess('access)(contentJsonData.get),
            decodeAccessRole('access_role)(contentJsonData.get), eid)
        case "conversation.code-update" =>
          ConversationCodeUpdateEvent('conversation, time, 'from, ConversationData.Link(contentJsonData.get.getString("uri")), eid)
        case "conversation.code-delete" =>
          ConversationCodeDeleteEvent('conversation, time, 'from, eid)
        case "conversation.receipt-mode-update" =>
          ConversationReceiptModeEvent('conversation, time, 'from, decodeInt('receipt_mode)(contentJsonData.get), eid)
        case "conversation.message-timer-update" =>
          MessageTimerEvent('conversation, time, 'from, decodeOptLong('message_timer)(contentJsonData.get).map(EphemeralDuration(_)), eid)
        case "conversation.change-convtype" =>
          ConvChangeTypeEvent('conversation, 'convType, time, 'from, decodeInt('type)(contentJsonData.get), eid)
        case "conversation.member-join-ask" =>
          ConvInviteMembersEvent('conversation, 'convType, time, 'from, 'data, decodeString('code)(msgData), decodeInt('type)(msgData), eid)
        case "conversation.update" =>
          ConvUpdateSettingEvent('conversation, 'convType, time, 'from, 'data, eid)
        case "conversation.update-blocktime" =>
          ConvUpdateSettingSingleEvent('conversation, 'convType, time, 'from, 'data, eid)
        case "conversation.bgp-message-add" =>
          bgpMessageEvent('conversation, time, 'from, decodeString('name)(asset), decodeString('avatar_key)(asset))(contentJsonData.get)
        //Note, the following events are not from the backend, but are the result of decrypting and re-encoding conversation.otr-message-add events - hence the different name for `convId
        case "conversation.generic-message" =>
          GenericMessageEvent('convId, time, 'from, 'content, null, null)
        case "conversation.generic-asset" =>
          GenericAssetEvent('convId, time, 'from, 'content, 'dataId, decodeOptByteString('data), null, null)
        case "conversation.otr-error" =>
          OtrErrorEvent('convId, time, 'from, decodeOtrError('error))
        case "conversation.update-aliasname" =>
          AliasEvent('conversation, time, 'from, 'data)
        case "conversation.json-message-add" =>
          BasicNotificationMessageEvent('conversation, time, 'from, ClientId(""), ClientId(""),
            ciphertext = Array.empty, contentJsonData, convType = 'convtype, eid)
        case _ =>
          error(l"unhandled event: $js")
          UnknownConvEvent(js)
      }
    } .getOrElse {
      error(l"unhandled event: $js")
      UnknownConvEvent(js)
    }
  }
}

object OtrErrorEvent extends DerivedLogTag {

  def decodeOtrError(s: Symbol)(implicit js: JSONObject): OtrError =
    OtrErrorDecoder(js.getJSONObject(s.name))

  implicit lazy val OtrErrorDecoder: JsonDecoder[OtrError] = new JsonDecoder[OtrError] {
    override def apply(implicit js: JSONObject): OtrError = Try {
      decodeString('type) match {
        case "otr-error.decryption-error" => DecryptionError('msg, 'from, 'sender)
        case "otr-error.identity-changed-error" => IdentityChangedError('from, 'sender)
        case "otr-error.duplicate" => Duplicate
        case _ =>
          error(l"unhandled event: $js")
          UnknownOtrErrorEvent(js)
      }
    }.getOrElse {
      error(l"unhandled event: $js")
      UnknownOtrErrorEvent(js)
    }
  }
}

object MessageEvent {
  import com.waz.utils._

  implicit lazy val MessageEventEncoder: JsonEncoder[MessageEvent] = new JsonEncoder[MessageEvent] {

    private def setFields(json: JSONObject, convId: RConvId, time: RemoteInstant, from: UserId, eventType: String, name: String = null, avatar_key: String = null) = {
      if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(avatar_key)) {
        val asset = new JSONObject()
        asset.put("name", name)
        asset.put("avatar_key", avatar_key)
        json.put("asset", asset)
      }
      json
        .put("convId", convId.str)
        .put("time", JsonEncoder.encodeISOInstant(time.instant))
        .put("from", from.str)
        .put("type", eventType)
        .setType(eventType)
    }

    override def apply(event: MessageEvent): JSONObject = JsonEncoder { json =>
      event match {
        case GenericMessageEvent(convId, time, from, content, name, asset) =>
          setFields(json, convId, time, from, "conversation.generic-message", name, asset)
            .put("content", AESUtils.base64(GenericMessage.toByteArray(content)))
        case GenericAssetEvent(convId, time, from, content, dataId, data, name, asset) =>
          setFields(json, convId, time, from, "conversation.generic-asset", name, asset)
            .put("dataId", dataId.str)
            .put("data", data match {
              case None => null
              case Some(d) => AESUtils.base64(d)
            })
            .put("content", AESUtils.base64(GenericMessage.toByteArray(content)))
        case OtrErrorEvent(convId, time, from, error) =>
          setFields(json, convId, time, from, "conversation.otr-error")
            .put("error", OtrError.OtrErrorEncoder(error))
        case CallMessageEvent(convId, time, from, sender, content) =>
          setFields(json, convId, time, from, "conversation.call-message")
            .put("sender", sender.str)
            .put("content", content)
        case e => throw new JSONException(s"Encoder for event $e not implemented")
      }
    }
  }
}

object OtrError {
  import com.waz.utils._

  implicit lazy val OtrErrorEncoder: JsonEncoder[OtrError] = new JsonEncoder[OtrError] {
    override def apply(error: OtrError): JSONObject = JsonEncoder { json =>
      error match {
        case DecryptionError(msg, from, sender) =>
          json
            .put("msg", msg)
            .put("from", from.str)
            .put("sender", sender.str)
            .setType("otr-error.decryption-error")
        case IdentityChangedError(from, sender) =>
          json
            .put("from", from.str)
            .put("sender", sender.str)
            .setType("otr-error.identity-changed-error")
        case Duplicate => json.setType("otr-error.duplicate")
        case e => throw new JSONException(s"Encoder for event $e not implemented")
      }
    }
  }
}

sealed trait TeamEvent extends Event {
  val teamId: TeamId
}

object TeamEvent extends DerivedLogTag {

  /**
    * See: https://github.com/wireapp/architecture/blob/master/teams/backend.md
    */

  case class Create(teamId: TeamId) extends TeamEvent
  case class Delete(teamId: TeamId) extends TeamEvent
  case class Update(teamId: TeamId, name: Option[Name], icon: Option[RAssetId], iconKey: Option[AESKey]) extends TeamEvent

  sealed trait MemberEvent extends TeamEvent {
    val userId: UserId
  }
  case class MemberJoin(teamId: TeamId, userId: UserId) extends MemberEvent
  case class MemberLeave(teamId: TeamId, userId: UserId) extends MemberEvent
  case class MemberUpdate(teamId: TeamId, userId: UserId) extends MemberEvent

  sealed trait ConversationEvent extends TeamEvent {
    val convId: RConvId
  }

  case class ConversationCreate(teamId: TeamId, convId: RConvId) extends ConversationEvent
  case class ConversationDelete(teamId: TeamId, convId: RConvId) extends ConversationEvent

  case class UnknownTeamEvent(js: JSONObject) extends TeamEvent { override val teamId = TeamId.Empty }

  implicit lazy val TeamEventDecoder: JsonDecoder[TeamEvent] = new JsonDecoder[TeamEvent] {

    override def apply(implicit js: JSONObject): TeamEvent =
      decodeString('type) match {
        case "team.create"              => Create('team)
        case "team.delete"              => Delete('team)
        case "team.update"              => Update('team, decodeOptName('name)('data), decodeOptString('icon)('data).map(RAssetId), decodeOptString('icon_key)('data).map(AESKey))
        case "team.member-join"         => MemberJoin ('team, UserId(decodeString('user)('data)))
        case "team.member-leave"        => MemberLeave('team, UserId(decodeString('user)('data)))
        case "team.member-update"       => MemberUpdate('team, UserId(decodeString('user)('data)))
        case "team.conversation-create" => ConversationCreate('team, RConvId(decodeString('conv)('data)))
        case "team.conversation-delete" => ConversationDelete('team, RConvId(decodeString('conv)('data)))
        case _ =>
          error(l"Unhandled event: $js")
          UnknownTeamEvent(js)
    }
  }
}

object OtrClientRemoveEvent {
  import com.waz.utils._
  implicit lazy val Encoder: JsonEncoder[OtrClientRemoveEvent] =
    new JsonEncoder[OtrClientRemoveEvent] {
      override def apply(error: OtrClientRemoveEvent): JSONObject = JsonEncoder { json =>
        json.setType("user.client-remove")
        json.put("client", new JSONObject().put("id", error.client.toString))
      }
    }
}

object PropertyEvent {
  lazy val Decoder: JsonDecoder[PropertyEvent] = new JsonDecoder[PropertyEvent] {
    override def apply(implicit js: JSONObject): PropertyEvent = {
      import PropertyKey._
      decodePropertyKey('key) match {
        case ReadReceiptsEnabled => ReadReceiptEnabledPropertyEvent('value)
        case key => UnknownPropertyEvent(key, 'value)
      }
    }
  }
}
