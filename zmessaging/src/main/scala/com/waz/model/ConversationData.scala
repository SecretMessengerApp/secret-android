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

import com.waz.api.IConversation.Access.{CODE, INVITE}
import com.waz.api.IConversation.AccessRole._
import com.waz.api.IConversation.{Access, AccessRole}
import com.waz.api.{IConversation, Message, Verification}
import com.waz.db.Col._
import com.waz.db.{Dao, Dao2}
import com.waz.log.LogShow.SafeToLog
import com.waz.model
import com.waz.model.ConversationData.{ConversationType, Link, UnreadCount}
import com.waz.model.GenericMessage.TextMessage
import com.waz.model.MessageData.{MessageDataDao, MessageTypeCodec}
import com.waz.service.SearchKey
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{JsonDecoder, JsonEncoder, _}
import org.json.JSONArray

import scala.collection.breakOut
import scala.concurrent.duration._

/**
  * alias_name & alias_name_ref deprecated
  *
  */
case class ConversationData(override val id:      ConvId                 = ConvId(),
                            remoteId:             RConvId                = RConvId(),
                            name:                 Option[Name]           = None,
                            creator:              UserId                 = UserId(),
                            convType:             ConversationType       = ConversationType.Group,
                            team:                 Option[TeamId]         = None,
                            lastEventTime:        RemoteInstant          = RemoteInstant.Epoch,
                            isActive:             Boolean                = true,
                            lastRead:             RemoteInstant          = RemoteInstant.Epoch,
                            muted:                MuteSet                = MuteSet.AllAllowed,
                            muteTime:             RemoteInstant          = RemoteInstant.Epoch,
                            archived:             Boolean                = false,
                            archiveTime:          RemoteInstant          = RemoteInstant.Epoch,
                            cleared:              Option[RemoteInstant]  = None,
                            generatedName:        Name                   = Name.Empty,
                            searchKey:            Option[SearchKey]      = None,
                            unreadCount:          UnreadCount            = UnreadCount(0, 0, 0, 0, 0),
                            failedCount:          Int                    = 0,
                            missedCallMessage:    Option[MessageId]      = None,
                            incomingKnockMessage: Option[MessageId]      = None,
                            hidden:               Boolean                = false,
                            verified:             Verification           = Verification.UNKNOWN,
                            localEphemeral:       Option[FiniteDuration] = None,
                            globalEphemeral:      Option[FiniteDuration] = None,
                            access:               Set[Access]            = Set.empty,
                            accessRole:           Option[AccessRole]     = None, //option for migration purposes only - at some point we do a fetch and from that point it will always be defined
                            link:                 Option[Link]           = None,
                            receiptMode:          Option[Int]            = None,

                            lastMsgId:            Option[MessageId]      = None,
                            lastMsgType:          Message.Type           = Message.Type.TEXT,
                            lastMsgContent:       Seq[MessageContent]    = Seq.empty,
                            lastMsgProtos:        Seq[GenericMessage]    = Seq.empty,
                            lastMsgTime:          RemoteInstant          = RemoteInstant.Epoch,
                            lastMsgUserId:        UserId                 = UserId(),
                            lastMsgMembers:       Set[UserId]            = Set.empty[UserId],
                            lastMsgAction:        Int                    = MessageActions.Action_UnKnown,
                            lastMsgContentType:   Option[String]         = None,

                            replyMessageId: Option[MessageId] = None,
                            memsum: Option[Int] = Some(0),
                            nid: Option[Uid] = None,
                            assets: Option[Seq[AssetData]] = None,
                            apps: Seq[WebAppId] = Seq.empty,
                            url_invite: Boolean = false,
                            confirm: Boolean = false,
                            addright: Boolean = false,
                            viewmem: Boolean = false,
                            memberjoin_confirm: Boolean = false,
                            place_top: Boolean = false,
                            block_time: Option[String] = None,
                            view_chg_mem_notify: Boolean = true,
                            add_friend: Boolean = true,
                            single_block_time: Option[String] = None,
                            orator: Seq[UserId] = Seq.empty,
                            manager: Seq[UserId] = Seq.empty,
                            block_duration: Option[Int] = None,
                            single_block_duration: Option[Int] = None,
                            forbiddenUsers: Seq[ForbiddenUserData] = Seq.empty,
                            auto_reply: Option[String] = None,
                            auto_reply_ref: Option[String] = None,
                            alias_name: Boolean = false,
                            alias_name_ref: Option[Name] = None,
                            advisory: Option[String] = None,
                            advisory_is_read :Boolean = false,
                            msg_only_to_manager: Boolean = false,
                            show_invitor_list: Boolean = false,
                            nature: Option[Int] = None,
                            blocked: Boolean = false,
                            show_memsum: Boolean = true,
                            enabled_edit_msg: Boolean = true,
                            request_edit_msg: Option[Int] = None,
                            advisory_show_dialog:Boolean=false
                           ) extends Identifiable[ConvId] {

  /*def displayName = if (convType == ConversationType.Group) name.getOrElse(generatedName) else generatedName*/

  def displayName = if (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) name.getOrElse(generatedName) else generatedName

  def withFreshSearchKey = copy(searchKey = freshSearchKey)
  def savedOrFreshSearchKey = searchKey.orElse(freshSearchKey)
  /*def freshSearchKey = if (convType == ConversationType.Group) name.map(SearchKey(_)) else None*/
  def freshSearchKey = if (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) name.map(SearchKey(_)) else None

  def smallRAssetId: RAssetId = if (assets.nonEmpty) assets.filter(_ != null) match {
    case Some(assetDatas: Seq[AssetData]) => if (assetDatas.nonEmpty) {
      assetDatas.head.remoteId.filter(_ != null) match {
        case Some(rAssetId: RAssetId) => rAssetId
        case None => null
      }
    } else {
      null
    }
    case None => null
  } else null

  def bigRAssetId: RAssetId = if (assets.nonEmpty) assets.filter(_ != null) match {
    case Some(assetDatas: Seq[AssetData]) => if (assetDatas.nonEmpty) {
      assetDatas.last.remoteId.filter(_ != null) match {
        case Some(rAssetId: RAssetId) => rAssetId
        case None => null
      }
    } else null
    case None => null
  } else null

  lazy val completelyCleared = cleared.exists(!_.isBefore(lastEventTime))

  val isManaged = team.map(_ => false) //can be returned to parameter list when we need it.

  lazy val ephemeralExpiration: Option[EphemeralDuration] = (globalEphemeral, localEphemeral) match {
    case (Some(d), _) => Some(ConvExpiry(d)) //global ephemeral takes precedence over local
    case (_, Some(d)) => Some(MessageExpiry(d))
    case _ => None
  }

  def withLastRead(time: RemoteInstant) = copy(lastRead = lastRead max time)

  def withCleared(time: RemoteInstant) = copy(cleared = Some(cleared.fold(time)(_ max time)))

  def isTeamOnly: Boolean = accessRole match {
    case Some(TEAM) if access.contains(Access.INVITE) => true
    case _ => false
  }

  def isGuestRoom: Boolean = accessRole match {
    case Some(NON_ACTIVATED) if access == Set(Access.INVITE, Access.CODE) => true
    case _ => false
  }

  def isWirelessLegacy: Boolean = !(isTeamOnly || isGuestRoom)

  def isUserAllowed(userData: UserData): Boolean =
    !(userData.isGuest(team) && isTeamOnly)

  def isMemberFromTeamGuest(teamId: Option[TeamId]): Boolean = team.isDefined && teamId != team

  def isAllAllowed: Boolean = muted.isAllAllowed

  def isAllMuted: Boolean = muted.isAllMuted

  def onlyMentionsAllowed: Boolean = muted.onlyMentionsAllowed

  def readReceiptsAllowed: Boolean = team.isDefined && receiptMode.exists(_ > 0)

  def isEphemeral: Boolean = {
    ephemeralExpiration.isDefined
  }

  lazy val contentString = lastMsgProtos.lastOption match {
    case Some(TextMessage(ct, _, _, _, _)) => ct
    case _ if lastMsgType == Message.Type.RICH_MEDIA => lastMsgContent.map(_.content).mkString(" ")
    case _ => lastMsgContent.headOption.fold("")(_.content)
  }

  def isForbid = lastMsgAction == MessageActions.Action_Forbid

  lazy val mentions = lastMsgContent.flatMap(_.mentions)

  def hasMentionOf(userId: UserId): Boolean = mentions.exists(_.userId.forall(_ == userId)) // a mention with userId == None is a "mention" of everyone, so it counts

  def isNormalConversation = nature.getOrElse(NatureTypes.Type_Normal) == NatureTypes.Type_Normal

  def isServerNotification = nature.getOrElse(NatureTypes.Type_Normal) == NatureTypes.Type_ServerNotifi

  def isGroupBlocked = (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) && blocked

  def isGroupShowNum = if (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) show_memsum else false

  def isGroupMsgEdit = if (convType == ConversationType.Group || convType == ConversationType.ThousandsGroup) enabled_edit_msg else true

  def isSingleMsgEdit = if (convType == ConversationType.OneToOne) enabled_edit_msg else true

  def isConvMsgEdit = isGroupMsgEdit && isSingleMsgEdit
}


/**
 * Conversation user binding.
 */
case class ConversationMemberData(userId: UserId, convId: ConvId) extends Identifiable[(UserId, ConvId)] {
  override val id: (UserId, ConvId) = (userId, convId)
}

object ConversationData {

  val Empty = ConversationData(ConvId(), RConvId(), None, UserId(), IConversation.Type.UNKNOWN)

  case class UnreadCount(normal: Int, call: Int, ping: Int, mentions: Int, quotes: Int) extends SafeToLog {
    def total = normal + call + ping + mentions + quotes
    def messages = normal + ping
  }

  // total (!) ordering for use in ordered sets; handwritten (instead of e.g. derived from tuples) to avoid allocations
  implicit val ConversationDataOrdering: Ordering[ConversationData] = new Ordering[ConversationData] {
    override def compare(b: ConversationData, a: ConversationData): Int =
      if (a.id == b.id) 0
      else {
        val c = a.lastEventTime.compareTo(b.lastEventTime)
        if (c != 0) c
        else a.id.str.compareTo(b.id.str)
      }
  }

  type ConversationType = IConversation.Type
  object ConversationType {
    val Unknown = IConversation.Type.UNKNOWN
    val Group = IConversation.Type.GROUP
    val OneToOne = IConversation.Type.ONE_TO_ONE
    val Self = IConversation.Type.SELF
    val WaitForConnection = IConversation.Type.WAIT_FOR_CONNECTION
    val Incoming = IConversation.Type.INCOMING_CONNECTION
    val ThousandsGroup = IConversation.Type.THROUSANDS_GROUP

    def apply(id: Int) = IConversation.Type.withId(id)

    def isOneToOne(tp: IConversation.Type) = tp == OneToOne || tp == WaitForConnection || tp == Incoming

    def isGroupConv(tp: IConversation.Type) = tp == ThousandsGroup || tp == Group

    def values = Set(Unknown, Group, OneToOne, Self, WaitForConnection, Incoming, ThousandsGroup)
  }

  def getAccessAndRoleForGroupConv(teamOnly: Boolean, teamId: Option[TeamId]): (Set[Access], AccessRole) = {
    teamId match {
      case Some(_) if teamOnly => (Set(INVITE), TEAM)
      case Some(_)             => (Set(INVITE, CODE), NON_ACTIVATED)
      case _                   => (Set(INVITE), ACTIVATED)
    }
  }

  case class Link(url: String)
  import GenericMessage._
  implicit object ConversationDataDao extends Dao[ConversationData, ConvId] {
    val Id                  = id[ConvId]('_id, "PRIMARY KEY").apply(_.id)
    val RemoteId            = id[RConvId]('remote_id).apply(_.remoteId)
    val Name                = opt(text[model.Name]('name, _.str, model.Name(_)))(_.name.filterNot(_.isEmpty))
    val Creator             = id[UserId]('creator).apply(_.creator)
    val ConvType            = int[ConversationType]('conv_type, _.id, ConversationType(_))(_.convType)
    val Team                = opt(id[TeamId]('team))(_.team)
    val IsManaged           = opt(bool('is_managed))(_.isManaged)
    val LastEventTime       = remoteTimestamp('last_event_time)(_.lastEventTime)
    val IsActive            = bool('is_active)(_.isActive)
    val LastRead            = remoteTimestamp('last_read)(_.lastRead)
    val MutedStatus         = int('muted_status)(_.muted.toInt)
    val MutedTime           = remoteTimestamp('mute_time)(_.muteTime)
    val Archived            = bool('archived)(_.archived)
    val ArchivedTime        = remoteTimestamp('archive_time)(_.archiveTime)
    val Cleared             = opt(remoteTimestamp('cleared))(_.cleared)
    val GeneratedName       = text[model.Name]('generated_name, _.str, model.Name(_))(_.generatedName)
    val SKey                = opt(text[SearchKey]('search_key, _.asciiRepresentation, SearchKey.unsafeRestore))(_.searchKey)
    val UnreadCount         = int('unread_count)(_.unreadCount.normal)
    val UnreadCallCount     = int('unread_call_count)(_.unreadCount.call)
    val UnreadPingCount     = int('unread_ping_count)(_.unreadCount.ping)
    val FailedCount         = int('unsent_count)(_.failedCount)
    val Hidden              = bool('hidden)(_.hidden)
    val MissedCall          = opt(id[MessageId]('missed_call))(_.missedCallMessage)
    val IncomingKnock       = opt(id[MessageId]('incoming_knock))(_.incomingKnockMessage)
    val Verified            = text[Verification]('verified, _.name, Verification.valueOf)(_.verified)
    val LocalEphemeral      = opt(finiteDuration('ephemeral))(_.localEphemeral)
    val GlobalEphemeral     = opt(finiteDuration('global_ephemeral))(_.globalEphemeral)
    val Access              = set[Access]('access, JsonEncoder.encodeAccess(_).toString(), v => JsonDecoder.array[Access](new JSONArray(v), (arr: JSONArray, i: Int) => IConversation.Access.valueOf(arr.getString(i).toUpperCase)).toSet)(_.access)
    val AccessRole          = opt(text[IConversation.AccessRole]('access_role, JsonEncoder.encodeAccessRole, v => IConversation.AccessRole.valueOf(v.toUpperCase)))(_.accessRole)
    val Link                = opt(text[Link]('link, _.url, v => ConversationData.Link(v)))(_.link)
    val UnreadMentionsCount = int('unread_mentions_count)(_.unreadCount.mentions)
    val UnreadQuotesCount   = int('unread_quote_count)(_.unreadCount.quotes)
    val ReceiptMode         = opt(int('receipt_mode))(_.receiptMode)

    val LastMsgId = opt(id[MessageId]('last_msg_id))(_.lastMsgId)
    val LastMsgType = text[Message.Type]('last_msg_type, MessageTypeCodec.encode, MessageTypeCodec.decode)(_.lastMsgType)
    val LastMsgContent = jsonArray[MessageContent, Seq, Vector]('last_msg_content).apply(_.lastMsgContent)
    val LastMsgProtos  = protoSeq[GenericMessage, Seq, Vector]('last_msg_protos).apply(_.lastMsgProtos)
    val LastMsgTime = remoteTimestamp('last_msg_time)(_.lastMsgTime)
    val LastMsgUser = id[UserId]('last_msg_user_id).apply(_.lastMsgUserId)
    val LastMsgMembers = set[UserId]('last_msg_members, _.mkString(","), _.split(",").filter(!_.isEmpty).map(UserId(_))(breakOut))(_.lastMsgMembers)
    val LastMsgAction = int('last_msg_action)(_.lastMsgAction)
    val LastMsgContentType = opt(text('last_msg_content_type))(_.lastMsgContentType)

    val ReplyMessageId = opt(id[MessageId]('reply_message_id))(_.replyMessageId)
    val Memsum = opt(int('memsum))(_.memsum)
    val Nid = opt(uid('nid))(_.nid)
    val Assets = opt(seq[AssetData]('assets, AssetData.encodeAsset(_).toString(), AssetData.getAssets(_)))(_.assets)
    val Apps = seq[String]('apps, _.mkString(","), _.split(','))(_.apps.map(_.str))
    val Url_invite = bool('url_invite)(_.url_invite)
    val Confirm = bool('confirm)(_.confirm)
    val Addright = bool('addright)(_.addright)
    val Viewmem = bool('viewmem)(_.viewmem)
    val Memberjoin_Confirm = bool('memberjoin_confirm)(_.memberjoin_confirm)
    val Place_top = bool('is_top)(_.place_top)
    val Block_time = opt(text('block_time))(_.block_time)
    val View_chg_mem_notify = bool('view_chg_mem_notify)(_.view_chg_mem_notify)
    val Add_friend = bool('add_friend)(_.add_friend)
    val Single_block_time = opt(text('single_block_time))(_.single_block_time)
    val Manager = seq[String]('manager, _.mkString(","), _.split(','))(_.manager.map(_.str))
    //seq[ManagerUserData]('manager, ManagerUserData.encodeManager(_).toString(), ManagerUserData.getManager(_))(_.manager)
    val Block_duration = opt(int('block_duration))(_.block_duration)
    val Single_block_duration = opt(int('single_block_duration))(_.single_block_duration)
    val Forbidden_users = seq[ForbiddenUserData]('forbidden_users, ForbiddenUserData.encode, ForbiddenUserData.decode)(_.forbiddenUsers)
    val Orator = seq[String]('orator, _.mkString(","), _.split(','))(_.orator.map(_.str))
    val Auto_reply = opt(text('auto_reply))(_.auto_reply)
    val Auto_reply_ref = opt(text('auto_reply_ref))(_.auto_reply_ref)
    val Alias_name = bool('alias_name)(_.alias_name)
    val Alias_name_ref = opt(text[model.Name]('alias_name_ref, _.str, model.Name(_)))(_.alias_name_ref.filterNot(_.isEmpty))
    val Advisory = opt(text('advisory))(_.advisory)
    val Advisory_is_read = bool('advisory_is_read)(_.advisory_is_read)
    val Msg_only_to_manager = bool('msg_only_to_manager)(_.msg_only_to_manager)
    val Show_invitor_list = bool('show_invitor_list)(_.show_invitor_list)
    val Nature = opt(int('nature))(_.nature)
    val Blocked = bool('blocked)(_.blocked)
    val Show_memsum = bool('show_memsum)(_.show_memsum)
    val Enabled_edit_msg = bool('enabled_edit_msg)(_.enabled_edit_msg)
    val Request_edit_msg = opt(int('request_edit_msg))(_.request_edit_msg)
    val Advisory_show_dialog = bool('advisory_show_dialog)(_.advisory_show_dialog)

    override val idCol = Id

    override val table = Table(
      "Conversations",
      Id,
      RemoteId,
      Name,
      Creator,
      ConvType,
      Team,
      IsManaged,
      LastEventTime,
      IsActive,
      LastRead,
      MutedStatus,
      MutedTime,
      Archived,
      ArchivedTime,
      Cleared,
      GeneratedName,
      SKey,
      UnreadCount,
      FailedCount,
      Hidden,
      MissedCall,
      IncomingKnock,
      Verified,
      LocalEphemeral,
      GlobalEphemeral,
      UnreadCallCount,
      UnreadPingCount,
      Access,
      AccessRole,
      Link,
      UnreadMentionsCount,
      UnreadQuotesCount,
      ReceiptMode,

      LastMsgId,
      LastMsgType,
      LastMsgContent,
      LastMsgProtos,
      LastMsgTime,
      LastMsgUser,
      LastMsgMembers,
      LastMsgAction,
      LastMsgContentType,

      ReplyMessageId,
      Memsum,
      Nid,
      Assets,
      Apps,
      Url_invite,
      Confirm,
      Addright,
      Viewmem,
      Memberjoin_Confirm,
      Place_top,
      Block_time,
      View_chg_mem_notify,
      Add_friend,
      Single_block_time,
      Orator,
      Manager,
      Block_duration,
      Single_block_duration,
      Forbidden_users,
      Auto_reply,
      Auto_reply_ref,
      Alias_name,
      Alias_name_ref,
      Advisory,
      Advisory_is_read,
      Msg_only_to_manager,
      Show_invitor_list,
      Nature,
      Blocked,
      Show_memsum,
      Enabled_edit_msg,
      Request_edit_msg,
      Advisory_show_dialog
    )

    override def apply(implicit cursor: DBCursor): ConversationData =

      ConversationData(
        id = Id,
        remoteId = RemoteId,
        name = Name,
        creator = Creator,
        convType = ConvType,
        team = Team,
        lastEventTime = LastEventTime,
        isActive = IsActive,
        lastRead = LastRead,
        muted = MuteSet(MutedStatus),
        muteTime = MutedTime,
        archived = Archived,
        archiveTime = ArchivedTime,
        cleared = Cleared,
        generatedName = GeneratedName,
        searchKey = SKey,
        unreadCount = ConversationData.UnreadCount(UnreadCount, UnreadCallCount, UnreadPingCount, UnreadMentionsCount, UnreadQuotesCount),
        failedCount = FailedCount,
        missedCallMessage = MissedCall,
        incomingKnockMessage = IncomingKnock,
        hidden = Hidden,
        verified = Verified,
        localEphemeral = LocalEphemeral,
        globalEphemeral = GlobalEphemeral,
        access = Access,
        accessRole = AccessRole,
        link = Link,
        receiptMode = ReceiptMode,

        lastMsgId = LastMsgId,
        lastMsgType = LastMsgType,
        lastMsgContent = LastMsgContent,
        lastMsgProtos = LastMsgProtos,
        lastMsgTime = LastMsgTime,
        lastMsgUserId = LastMsgUser,
        lastMsgMembers = LastMsgMembers,
        lastMsgAction = LastMsgAction,
        lastMsgContentType = LastMsgContentType,

        replyMessageId = ReplyMessageId,
        memsum = Memsum,
        nid = Nid,
        assets = Assets,
        apps = Apps.map(WebAppId),
        url_invite = Url_invite,
        confirm = Confirm,
        addright = Addright,
        viewmem = Viewmem,
        memberjoin_confirm = Memberjoin_Confirm,
        place_top = Place_top,
        block_time = Block_time,
        view_chg_mem_notify = View_chg_mem_notify,
        add_friend = Add_friend,
        single_block_time = Single_block_time,
        orator = Orator.map(UserId),
        manager = Manager.map(UserId),
        block_duration = Block_duration,
        single_block_duration = Single_block_duration,
        forbiddenUsers = Forbidden_users,
        auto_reply = Auto_reply,
        auto_reply_ref = Auto_reply_ref,
        alias_name = Alias_name,
        alias_name_ref = Alias_name_ref,
        advisory = Advisory,
        advisory_is_read =Advisory_is_read,
        msg_only_to_manager = Msg_only_to_manager,
        show_invitor_list = Show_invitor_list,
        nature = Nature,
        blocked = Blocked,
        show_memsum = Show_memsum,
        enabled_edit_msg = Enabled_edit_msg,
        request_edit_msg = Request_edit_msg,
        advisory_show_dialog=Advisory_show_dialog
      )
    
    import com.waz.model.ConversationData.ConversationType._

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_search_key on Conversations (${SKey.name})")
    }

    def establishedConversations(implicit db: DB) = iterating(db.rawQuery(
      s"""SELECT *
         |  FROM ${table.name}
         | WHERE (${ConvType.name} = ${ConvType(ConversationType.OneToOne)} OR ${ConvType.name} = ${ConvType(ConversationType.Group)})
         |   AND ${IsActive.name} = ${IsActive(true)}
         |   AND ${Hidden.name} = 0
      """.stripMargin, null))

    def allConversations(implicit db: DB) =
      db.rawQuery(s"SELECT *, ${ConvType.name} = ${Self.id} as is_self, ${ConvType.name} = ${Incoming.id} as is_incoming, ${Archived.name} = 1 as is_archived FROM ${table.name} WHERE ${Hidden.name} = 0 ORDER BY is_self DESC, is_archived ASC, is_incoming DESC, ${LastEventTime.name} DESC", null)

    import ConversationMemberData.{ConversationMemberDataDao => CM}
    import UserData.{UserDataDao => U}

    def search(prefix: SearchKey, self: UserId, handleOnly: Boolean, teamId: Option[TeamId])(implicit db: DB) = {
      val select =
        s"""SELECT c.* ${if (teamId.isDefined) ", COUNT(*)" else ""}
            |  FROM ${table.name} c
            |  JOIN ${CM.table.name} cm ON cm.${CM.ConvId.name} = c.${Id.name}
            |  JOIN ${U.table.name} u ON cm.${CM.UserId.name} = u.${U.Id.name}
            | WHERE c.${ConvType.name} = ${ConvType(ConversationType.Group)}
            |   AND c.${Hidden.name} = ${Hidden(false)}
            |   AND u.${U.Id.name} != '${U.Id(self)}'
            |   AND (c.${Cleared.name} IS NULL OR c.${Cleared.name} < c.${LastEventTime.name} OR c.${IsActive.name} = ${IsActive(true)})""".stripMargin
      val handleCondition =
        if (handleOnly){
          s"""AND u.${U.Handle.name} LIKE '${prefix.asciiRepresentation}%'""".stripMargin
        } else {
          s"""AND (    c.${SKey.name}   LIKE '${SKey(Some(prefix))}%'
              |     OR c.${SKey.name}   LIKE '% ${SKey(Some(prefix))}%'
              |     OR u.${U.SKey.name} LIKE '${U.SKey(prefix)}%'
              |     OR u.${U.SKey.name} LIKE '% ${U.SKey(prefix)}%'
              |     OR u.${U.Handle.name} LIKE '%${prefix.asciiRepresentation}%')""".stripMargin
        }
      val teamCondition = teamId.map(_ =>
        s"""AND c.${Team.name} = ${Team(teamId)}
           | GROUP BY cm.${CM.ConvId.name}
           | HAVING COUNT(*) > 2
         """.stripMargin)

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""), null))
    }

    def searchForConversations(prefix: SearchKey, self: UserId, handleOnly: Boolean, teamId: Option[TeamId])(implicit db: DB) = {
      val select =
        s"""SELECT c.* ${if (teamId.isDefined) ", COUNT(*)" else ""}
           |  FROM ${table.name} c
           |  JOIN ${CM.table.name} cm ON cm.${CM.ConvId.name} = c.${Id.name}
           |  JOIN ${U.table.name} u ON cm.${CM.UserId.name} = u.${U.Id.name}
           | WHERE c.${ConvType.name} = ${ConvType(ConversationType.Group)}
           |   AND c.${Hidden.name} = ${Hidden(false)}
           |   AND u.${U.Id.name} = '${U.Id(self)}'
           |   AND (c.${Cleared.name} IS NULL OR c.${Cleared.name} < c.${LastEventTime.name} OR c.${IsActive.name} = ${IsActive(true)})""".stripMargin
      val handleCondition =
        if (handleOnly){
          s"""AND u.${U.Handle.name} LIKE '${prefix.asciiRepresentation}%'""".stripMargin
        } else {
          s"""AND (    c.${SKey.name}   LIKE '${SKey(Some(prefix))}%'
             |     OR c.${SKey.name}   LIKE '% ${SKey(Some(prefix))}%'
             |     OR u.${U.SKey.name} LIKE '${U.SKey(prefix)}%'
             |     OR u.${U.SKey.name} LIKE '% ${U.SKey(prefix)}%'
             |     OR u.${U.Handle.name} LIKE '%${prefix.asciiRepresentation}%')""".stripMargin
        }
      val teamCondition = teamId.map(_ =>
        s"""AND c.${Team.name} = ${Team(teamId)}
           | GROUP BY cm.${CM.ConvId.name}
           | HAVING COUNT(*) > 2
         """.stripMargin)

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""), null))
    }

    def findByTeams(teams: Set[TeamId])(implicit db: DB) = iterating(findInSet(Team, teams.map(Option(_))))

    def findByRemoteId(remoteId: RConvId)(implicit db: DB) = iterating(find(RemoteId, remoteId))
    def findByRemoteIds(remoteIds: Set[RConvId])(implicit db: DB) = iterating(findInSet(RemoteId, remoteIds))
    def findByConvIds(convIds: Set[ConvId])(implicit db: DB) = iterating(findInSet(Id, convIds))

    def findByConversationType(convType: Set[ConversationType])(implicit db: DB) = iterating(findInSet(ConvType, convType))

    def clearUnread()(implicit db: DB) = {
      db.execSQL(s"UPDATE ${table.name} SET ${UnreadCount.name}=0,${UnreadQuotesCount.name}=0,${UnreadCallCount.name}=0,${UnreadPingCount.name}=0,${UnreadMentionsCount.name}=0")
      val condition = s"(SELECT MAX(${MessageDataDao.Time.name}) from ${MessageDataDao.table.name} where ${MessageDataDao.Conv.name}=${table.name}.${Id.name})"
      db.execSQL(s"UPDATE ${table.name} SET ${LastRead.name}=$condition where ${LastRead.name} < $condition")
    }
  }
}

object ConversationMemberData {

  implicit object ConversationMemberDataDao extends Dao2[ConversationMemberData, UserId, ConvId] {
    val UserId = id[UserId]('user_id).apply(_.userId)
    val ConvId = id[ConvId]('conv_id).apply(_.convId)

    override val idCol = (UserId, ConvId)
    override val table = Table("ConversationMembers", UserId, ConvId)
    override def apply(implicit cursor: DBCursor): ConversationMemberData = ConversationMemberData(UserId, ConvId)

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_conv on ConversationMembers (${ConvId.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS ConversationMembers_userid on ConversationMembers (${UserId.name})")
    }

    def findForConv(convId: ConvId)(implicit db: DB) = iterating(find(ConvId, convId))
    def findForConvs(convs: Set[ConvId])(implicit db: DB) = iterating(findInSet(ConvId, convs))
    def findForUser(userId: UserId)(implicit db: DB) = iterating(find(UserId, userId))
    def findForUsers(users: Set[UserId])(implicit db: DB) = iterating(findInSet(UserId, users))

    def listLimitForConv(convId: ConvId, limit: String)(implicit db: DB) = {
      list(db.query(table.name, null, s"${ConvId.name} = ?", Array(convId.str), null, null,null, limit))
    }

    def listLimitForUser(userId: UserId, limit: String)(implicit db: DB) = {
      list(db.query(table.name, null, s"${UserId.name} = ?", Array(userId.str), null, null,null, limit))
    }
  }
}
