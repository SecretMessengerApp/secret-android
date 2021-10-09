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
package com.waz.sync.client

import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.AssetMetaData.Image
import com.waz.model.ConversationData.{ConversationType, Link}
import com.waz.model._
import com.waz.sync.client.ConversationsClient.ConversationResponse.ConversationsResult
import com.waz.utils.JsonEncoder.{encodeAccess, encodeAccessRole}
import com.waz.utils.{Json, JsonDecoder, JsonEncoder, _}
import com.waz.znet2.AuthRequestInterceptor
import com.waz.znet2.http.Request.UrlCreator
import com.waz.znet2.http.{Request, _}
import org.json.{JSONArray, JSONObject}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal

trait ConversationsClient {
  import ConversationsClient._

  def loadConversationWithRConvId(start: Option[RConvId] = None): ErrorOrResponse[ConversationsResult]

  def loadConversationIds(start: Option[RConvId] = None): ErrorOrResponse[ConversationsResult]
  def loadConversations(start: Option[RConvId] = None, limit: Int = ConversationsPageSize): ErrorOrResponse[ConversationsResult]
  def loadConversations(ids: Seq[RConvId]): ErrorOrResponse[Seq[ConversationResponse]]
  def postName(convId: RConvId, name: Name): ErrorOrResponse[Option[RenameConversationEvent]]
  def postConversationState(convId: RConvId, state: ConversationState): ErrorOrResponse[Unit]
  def postMessageTimer(convId: RConvId, duration: Option[FiniteDuration]): ErrorOrResponse[Unit]
  def postMemberJoin(conv: RConvId, members: Set[UserId], needConfirm: Boolean = false, inviteStr: String = "", selfName: Option[String] = None): ErrorOrResponse[Option[MemberJoinEvent]]
  def postMemberLeave(conv: RConvId, user: UserId): ErrorOrResponse[Option[MemberLeaveEvent]]
  def createLink(conv: RConvId): ErrorOrResponse[Link]
  def removeLink(conv: RConvId): ErrorOrResponse[Unit]
  def getLink(conv: RConvId): ErrorOrResponse[Option[Link]]
  def postAccessUpdate(conv: RConvId, access: Set[Access], accessRole: AccessRole): ErrorOrResponse[Unit]
  def postReceiptMode(conv: RConvId, receiptMode: Int): ErrorOrResponse[Unit]
  def postConversation(state: ConversationInitState): ErrorOrResponse[ConversationResponse]

  def updateGroupPicture(rConvId: RConvId, info: GroupHeadPortraitInfo): ErrorOrResponse[Unit]

  def changeBlockTime(rConvId: RConvId, userId: Option[UserId], endTime: Option[String], duration: Option[Int] = None): ErrorOrResponse[Unit]

  def switchGroupSettings(rConvId: RConvId, key: String, checked: Boolean): ErrorOrResponse[Unit]

  def changGroupNickname(rConvId: RConvId, nickname: String): ErrorOrResponse[Unit]
}

class ConversationsClientImpl(implicit
                              urlCreator: UrlCreator,
                              httpClient: HttpClient,
                              authRequestInterceptor: AuthRequestInterceptor) extends ConversationsClient with DerivedLogTag {

  import ConversationsClient._
  import HttpClient.AutoDerivation._
  import HttpClient.dsl._
  import com.waz.threading.Threading.Implicits.Background

  private implicit val ConversationIdsResponseDeserializer: RawBodyDeserializer[ConversationsResult] =
    RawBodyDeserializer[JSONObject].map { json =>
      val (ids, hasMore) = ConversationsResult.unapply(JsonObjectResponse(json)).get
      ConversationsResult(ids, hasMore)
    }

  override def loadConversationWithRConvId(start: Option[RConvId] = None): ErrorOrResponse[ConversationsResult] = {
    Request
      .Get(
        relativePath = conversationPathByRid(start.getOrElse(RConvId())),
        queryParameters = List.empty
      )
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadConversationIds(start: Option[RConvId] = None): ErrorOrResponse[ConversationsResult] = {
    Request
      .Get(
        relativePath = ConversationIdsPath,
        queryParameters = queryParameters("size" -> ConversationIdsPageSize, "start" -> start)
      )
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadConversations(start: Option[RConvId] = None, limit: Int = ConversationsPageSize): ErrorOrResponse[ConversationsResult] = {
    Request
      .Get(
        relativePath = ConversationsPath,
        queryParameters = queryParameters("size" -> limit, "start" -> start)
      )
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def loadConversations(ids: Seq[RConvId]): ErrorOrResponse[Seq[ConversationResponse]] = {
    Request
      .Get(relativePath = ConversationsPath, queryParameters = queryParameters("ids" -> ids.mkString(",")))
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe
      .map(_.map(_.conversations))
  }

  private implicit val EventsResponseDeserializer: RawBodyDeserializer[List[ConversationEvent]] =
    RawBodyDeserializer[JSONObject].map(json => EventsResponse.unapplySeq(JsonObjectResponse(json)).get)

  override def postName(convId: RConvId, name: Name): ErrorOrResponse[Option[RenameConversationEvent]] = {
    Request.Put(relativePath = s"$ConversationsPath/$convId", body = Json("name" -> name))
      .withResultType[List[ConversationEvent]]
      .withErrorType[ErrorResponse]
      .executeSafe {
        case (event: RenameConversationEvent) :: Nil => Some(event)
        case _ => None
      }
  }

  override def postMessageTimer(convId: RConvId, duration: Option[FiniteDuration]): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = s"$ConversationsPath/$convId/message-timer",
        body = Json("message_timer" -> duration.map(_.toMillis))
      )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postConversationState(convId: RConvId, state: ConversationState): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = s"$ConversationsPath/$convId/self", body = state)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def postMemberJoin(conv: RConvId, members: Set[UserId], needConfirm: Boolean = false, inviteStr: String = "", selfName: Option[String] = None): ErrorOrResponse[Option[MemberJoinEvent]] = {
    val addBody = if (needConfirm) Json("users" -> Json(members), "reason" -> inviteStr, "name" -> selfName.getOrElse("")) else Json("users" -> Json(members))
    Request.Post(relativePath = s"$ConversationsPath/$conv/members", body = addBody)
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberJoinEvent) :: Nil => event })
  }

  override def postMemberLeave(conv: RConvId, user: UserId): ErrorOrResponse[Option[MemberLeaveEvent]] = {
    Request.Delete(relativePath = s"$ConversationsPath/$conv/members/$user")
      .withResultType[Option[List[ConversationEvent]]]
      .withErrorType[ErrorResponse]
      .executeSafe(_.collect { case (event: MemberLeaveEvent) :: Nil => event })
  }

  override def createLink(conv: RConvId): ErrorOrResponse[Link] = {
    Request.Post(relativePath = s"$ConversationsPath/$conv/code", body = "")
      .withResultType[Response[JSONObject]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        val js = response.body
        if (response.code == ResponseCode.Success && js.has("uri"))
          Link(js.getString("uri"))
        else if (response.code == ResponseCode.Created && js.getJSONObject("data").has("uri"))
          Link(js.getJSONObject("data").getString("uri"))
        else
          throw new IllegalArgumentException(s"Can not extract link from json: $js")
      }

  }

  def removeLink(conv: RConvId): ErrorOrResponse[Unit] = {
    Request.Delete(relativePath = s"$ConversationsPath/$conv/code")
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def getLink(conv: RConvId): ErrorOrResponse[Option[Link]] = {
    Request.Get(relativePath = s"$ConversationsPath/$conv/code")
      .withResultHttpCodes(ResponseCode.SuccessCodes + ResponseCode.NotFound)
      .withResultType[Response[JSONObject]]
      .withErrorType[ErrorResponse]
      .executeSafe { response =>
        val js = response.body
        if (ResponseCode.isSuccessful(response.code) && js.has("uri"))
          Some(Link(js.getString("uri")))
        else if (response.code == ResponseCode.NotFound)
          None
        else
          throw new IllegalArgumentException(s"Can not extract link from json: $js")
      }
  }

  def postAccessUpdate(conv: RConvId, access: Set[Access], accessRole: AccessRole): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = accessUpdatePath(conv),
        body = Json(
          "access" -> encodeAccess(access),
          "access_role" -> encodeAccessRole(accessRole)
        )
      )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def postReceiptMode(conv: RConvId, receiptMode: Int): ErrorOrResponse[Unit] = {
    Request
      .Put(
        relativePath = receiptModePath(conv),
        body = Json(
          "receipt_mode" -> receiptMode
        )
      )
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  def postConversation(state: ConversationInitState): ErrorOrResponse[ConversationResponse] = {
    debug(l"postConversation(${state.users}, ${state.name})")
    Request.Post(relativePath = ConversationsPath, body = state)
      .withResultType[ConversationsResult]
      .withErrorType[ErrorResponse]
      .executeSafe(_.conversations.head)
  }

  def updateGroupPicture(rConvId: RConvId, info: GroupHeadPortraitInfo): ErrorOrResponse[Unit] = {

    val body = JsonEncoder { o =>
      info.picture.foreach { asset =>
        o.put("assets", AssetData.encodeAsset(asset))
      }
    }
    Request.Put(relativePath = s"$ConversationsPath/$rConvId/update", body = body)
      .withResultType[Unit]
      .withErrorType[ErrorResponse]
      .executeSafe
  }

  override def changeBlockTime(rConvId: RConvId, userId: Option[UserId], endTime: Option[String], duration: Option[Int] = None): ErrorOrResponse[Unit] = {
    val relativePath = s"$ConversationsPath/$rConvId${
      userId match {
        case Some(uId) => s"/block/$uId"
        case _ => "/update"
      }
    }"
    Request.Put(relativePath = relativePath, body = Json(
      "block_time" -> endTime.fold(0L)(_.toLong),
      "block_duration" -> duration.getOrElse(0)
    )).withResultType[Unit].withErrorType[ErrorResponse]
      .executeSafe
  }

  override def switchGroupSettings(rConvId: RConvId, key: String, checked: Boolean): ErrorOrResponse[Unit] = {
    Request.Put(relativePath = s"$ConversationsPath/$rConvId/update", body = Json(key -> checked))
      .withResultType[Unit].withErrorType[ErrorResponse]
      .executeSafe
  }

  override def changGroupNickname(rConvId: RConvId, nickname: String): ErrorOrResponse[Unit] = {
    val contentJson = Json(
      "alias_name" -> Try(nickname.trim.nonEmpty).toOption.getOrElse(false),
      "alias_name_ref" -> nickname
    )
    Request.Put(relativePath = s"$ConversationsPath/$rConvId/selfalias", body = contentJson)
      .withResultType[Unit].withErrorType[ErrorResponse]
      .executeSafe
  }
}

object ConversationsClient {
  val ConversationsPath = "/conversations"
  //val ConversationsPath_PointRConvId = "/conversations/%s"
  val ConversationIdsPath = "/conversations/ids"
  val ConversationsPageSize = 100
  val ConversationIdsPageSize = 1000
  val IdsCountThreshold = 32

  def accessUpdatePath(id: RConvId) = s"$ConversationsPath/${id.str}/access"
  def receiptModePath(id: RConvId) = s"$ConversationsPath/${id.str}/receipt-mode"
  def conversationPathByRid(id: RConvId) = s"$ConversationsPath/${id.str}"

  case class ConversationInitState(users: Set[UserId],
                                   name: Option[Name] = None,
                                   team: Option[TeamId],
                                   access: Set[Access],
                                   accessRole: AccessRole,
                                   receiptMode: Option[Int],
                                   apps : String)

  object ConversationInitState {
    implicit lazy val Encoder: JsonEncoder[ConversationInitState] = new JsonEncoder[ConversationInitState] {
      override def apply(state: ConversationInitState): JSONObject = JsonEncoder { o =>
        o.put("users", Json(state.users))
        state.name.foreach(o.put("name", _))
        o.put("access", encodeAccess(state.access))
        o.put("access_role", encodeAccessRole(state.accessRole))
        o.put("top_apps",new JSONArray(state.apps))
        state.receiptMode.foreach(o.put("receipt_mode", _))
      }
    }
  }

  case class ConversationResponse(id: RConvId,
                                  name: Option[Name],
                                  creator: UserId,
                                  convType: ConversationType,
                                  team: Option[TeamId],
                                  muted: MuteSet,
                                  mutedTime: RemoteInstant,
                                  archived: Boolean,
                                  archivedTime: RemoteInstant,
                                  access: Set[Access],
                                  accessRole: Option[AccessRole],
                                  link: Option[Link],
                                  messageTimer: Option[FiniteDuration],
                                  members: Set[UserId],
                                  receiptMode: Option[Int],

                                  memsum: Option[Int] = Some(0),
                                  assets: Option[Seq[AssetData]] = None,
                                  apps: Option[Seq[WebAppId]] = None,
                                  url_invite: Option[Boolean] = None,
                                  confirm: Option[Boolean] = None,
                                  addright: Option[Boolean] = None,
                                  viewmem: Option[Boolean] = None,
                                  memberjoin_confirm: Option[Boolean] = None,
                                  block_time: Option[String] = None,
                                  view_chg_mem_notify: Boolean = true,
                                  add_friend: Boolean = true,
                                  orator: Option[Seq[UserId]] = None,
                                  place_top: Boolean = false,
                                  auto_reply: Option[String] = None,
                                  auto_reply_ref: Option[String] = None,
                                  manager: Option[Seq[UserId]] = None,
                                  advisory: Option[String] = None,
                                  msg_only_to_manager: Option[Boolean] = None,
                                  show_invitor_list: Option[Boolean] = None,
                                  aliasUsers: Option[Seq[AliasData]] = None,
                                  blocked: Boolean = false,
                                  show_memsum: Boolean = true,
                                  enabled_edit_msg: Boolean = true,
                                  request_edit_msg: Option[Int] = None
                                 )

  object ConversationResponse {

    import com.waz.utils.JsonDecoder._

    def isVerifiedArr(key: String)(implicit js: JSONObject) = {
      js.optJSONArray(key) != null && js.optJSONArray(key).length() > 0
    }

    implicit lazy val Decoder: JsonDecoder[ConversationResponse] = new JsonDecoder[ConversationResponse] {
      override def apply(implicit js: JSONObject): ConversationResponse = {

        val members = js.getJSONObject("members")
        val state = ConversationState.Decoder(members.getJSONObject("self"))
        val optAsset = if (isVerifiedArr("assets")(js)) getAssets(js) else None
        val optApps = if (isVerifiedArr("apps")(js)) Option(decodeWebAppIdSeq('apps)(js)) else None
        val orator = if (isVerifiedArr("orator")(js)) Option(decodeUserIdSeq('orator)(js)) else None
        val manager = if (isVerifiedArr("manager")(js)) Option(decodeUserIdSeq('manager)(js)) else None

        var aliasList: Seq[AliasData] = List()

        val selfAlias = JsonDecoder.lift { json =>
          (json.optString("id"), json.optString("alias_name_ref"), json.optBoolean("alias_name"))
        }(members.getJSONObject("self"))

        if (selfAlias._1 != null && selfAlias._2 != null && selfAlias._3) {
          aliasList = aliasList.+:(new AliasData('id, UserId(selfAlias._1), Option(Name(selfAlias._2)), selfAlias._3))
        }

        val otherAlias = JsonDecoder.decodeSeq('others)(members, JsonDecoder.lift { json =>
          val tempName = json.optString("aliasname")
          (json.optString("id"), tempName, tempName.nonEmpty)
        })

        otherAlias.foreach { parts =>
          if (parts._1 != null && parts._2 != null && parts._3) {
            aliasList = aliasList.+:(new AliasData('id, UserId(parts._1), Option(Name(parts._2)), parts._3))
          }
        }

        val conversationResponse = ConversationResponse(
          id = 'id,
          name = 'name,
          creator = 'creator,
          convType = 'type,
          team = 'team,
          muted = MuteSet.resolveMuted(state, isTeam = true),
          mutedTime = state.muteTime.getOrElse(RemoteInstant.Epoch),
          archived = state.archived.getOrElse(false),
          archivedTime = state.archiveTime.getOrElse(RemoteInstant.Epoch),
          access = 'access,
          accessRole = 'access_role,
          link = 'link,
          messageTimer = decodeOptLong('message_timer).map(EphemeralDuration(_)),
          members = JsonDecoder.arrayColl(members.getJSONArray("others"), { case (arr, i) =>
            UserId(arr.getJSONObject(i).getString("id"))
          }),
          receiptMode = decodeOptInt('receipt_mode),

          memsum = decodeOptInt('memsum)(js),
          assets = optAsset,
          apps = optApps,
          url_invite = decodeOptBoolean('url_invite)(js),
          confirm = decodeOptBoolean('confirm)(js),
          addright = decodeOptBoolean('addright)(js),
          viewmem = decodeOptBoolean('viewmem)(js),
          memberjoin_confirm = decodeOptBoolean('memberjoin_confirm)(js),
          block_time = decodeOptString('block_time)(js),
          view_chg_mem_notify = decodeBool('view_chg_mem_notify, default = true)(js),
          add_friend = decodeBool('add_friend, default = true)(js),
          orator = orator,
          place_top = state.place_top.getOrElse(false),
          auto_reply = state.auto_reply,
          auto_reply_ref = state.auto_reply_ref,
          manager = manager,
          advisory = decodeOptString('advisory),
          msg_only_to_manager = decodeOptBoolean('msg_only_to_manager)(js),
          show_invitor_list = decodeOptBoolean('show_invitor_list)(js),
          aliasUsers = if(aliasList.nonEmpty) Some(aliasList) else None,
          blocked = decodeBool('blocked, false)(js),
          show_memsum = decodeBool('show_memsum, true)(js),
          enabled_edit_msg = decodeBool('enabled_edit_msg, true)(js),
          request_edit_msg = decodeOptInt('request_edit_msg)
        )
        conversationResponse
      }
    }

    /**
      *
      * @param js
      * @return
      */
    def getAssets(implicit js: JSONObject): Option[Seq[AssetData]] = fromArray(js, "assets") flatMap { assets =>
      Some(Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
        AssetData(
          remoteId = decodeOptRAssetId('key)(js),
          metaData = Some(AssetMetaData.Image(Dim2(0, 0), Image.Tag(decodeString('size)(js))))
        )
      })
    }

    private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)


    case class ConversationsResult(conversations: Seq[ConversationResponse], hasMore: Boolean)

    object ConversationsResult extends DerivedLogTag {

      def unapply(response: ResponseContent): Option[(List[ConversationResponse], Boolean)] = try {
        response match {
          case JsonObjectResponse(js) if js.has("conversations") =>
            Some((array[ConversationResponse](js.getJSONArray("conversations")).toList, decodeBool('has_more)(js)))
          case JsonArrayResponse(js) => Some((array[ConversationResponse](js).toList, false))
          case JsonObjectResponse(js) => Some((List(Decoder(js)), false))
          case _ => None
        }
      } catch {
        case NonFatal(e) =>
          warn(l"couldn't parse conversations response", e)
          warn(l"json decoding failed", e)
          None
      }
    }
  }

  object EventsResponse extends DerivedLogTag {
    import com.waz.utils.JsonDecoder._

    def unapplySeq(response: ResponseContent): Option[List[ConversationEvent]] = try {
      response match {
        case JsonObjectResponse(js) if js.has("events") => Some(array[ConversationEvent](js.getJSONArray("events")).toList)
        case JsonArrayResponse(js) => Some(array[ConversationEvent](js).toList)
        case JsonObjectResponse(js) => Some(List(implicitly[JsonDecoder[ConversationEvent]].apply(js)))
        case _ => None
      }
    } catch {
      case NonFatal(e) =>
        warn(l"couldn't parse events response", e)
        None
    }
  }
}
