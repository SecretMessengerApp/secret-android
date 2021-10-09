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

import com.waz.api.Verification
import com.waz.db.Col._
import com.waz.db.Dao
import com.waz.model
import com.waz.model.ManagedBy.ManagedBy
import com.waz.model.UserData.ConnectionStatus
import com.waz.model.UserPermissions._
import com.waz.service.SearchKey
import com.waz.service.UserSearchService.UserSearchEntry
import com.waz.utils._
import com.waz.utils.wrappers.{DB, DBCursor}

import scala.concurrent.duration._

case class UserData(override val id:       UserId,
                    teamId:                Option[TeamId]         = None,
                    name:                  Name,
                    email:                 Option[EmailAddress]   = None,
                    phone:                 Option[PhoneNumber]    = None,
                    trackingId:            Option[TrackingId]     = None,
                    picture:               Option[AssetId]        = None,
                    accent:                Int                    = 0, // accent color id
                    searchKey:             SearchKey,
                    connection:            ConnectionStatus       = ConnectionStatus.Unconnected,
                    connectionLastUpdated: RemoteInstant          = RemoteInstant.Epoch, // server side timestamp of last connection update
                    connectionMessage:     Option[String]         = None, // incoming connection request message
                    conversation:          Option[RConvId]        = None, // remote conversation id with this contact (one-to-one)
                    relation:              Relation               = Relation.Other, //unused - remove in future migration
                    syncTimestamp:         Option[LocalInstant]   = None,
                    displayName:           Name                   = Name.Empty,
                    verified:              Verification           = Verification.UNKNOWN, // user is verified if he has any otr client, and all his clients are verified
                    deleted:               Boolean                = false,
                    availability:          Availability           = Availability.None,
                    handle:                Option[Handle]         = None,

                    user_address: Option[String] = None,
                    locale: Option[String] = None,
                    remark: Option[String] = None,
                    nature: Option[Int] = None,
                    bots: Option[String] = None,
                    rAssetId: Option[String] = None,
                    payValidTime: Option[Int] = None,
                    extid: Option[String] = None,

                    providerId:            Option[ProviderId]     = None,
                    integrationId:         Option[IntegrationId]  = None,
                    expiresAt:             Option[RemoteInstant]  = None,
                    managedBy:             Option[ManagedBy]      = None,
                    fields:                Seq[UserField]         = Seq.empty,
                    permissions:           PermissionsMasks       = (0,0),
                    createdBy:             Option[UserId]         = None) extends Identifiable[UserId] {

  def isConnected = ConnectionStatus.isConnected(connection)
  def hasEmailOrPhone = email.isDefined || phone.isDefined
  def isSelf = connection == ConnectionStatus.Self
  def isAcceptedOrPending = connection == ConnectionStatus.Accepted || connection == ConnectionStatus.PendingFromOther || connection == ConnectionStatus.PendingFromUser
  def isVerified = verified == Verification.VERIFIED
  def isAutoConnect = isConnected && ! isSelf && connectionMessage.isEmpty
  def isReadOnlyProfile = managedBy.exists(_ != ManagedBy.Wire) //if none or "Wire", then it's not read only.
  lazy val isWireBot = integrationId.nonEmpty

  /*def getDisplayName = if (displayName.isEmpty) name else displayName*/
  def getDisplayName: Name = if (displayName.isEmpty) name else displayName

  def getShowName: String = remark.filter(_.nonEmpty).getOrElse(getDisplayName.str)

  def getDisplayNameForJava: String = if (displayName.isEmpty) name.str else displayName.str

  def isRobotUser: Boolean = nature.getOrElse(NatureTypes.Type_Normal) > NatureTypes.Type_Normal

  def updated(user: UserInfo): UserData = updated(user, withSearchKey = true)
  def updated(user: UserInfo, withSearchKey: Boolean): UserData = copy(
    name = user.name.getOrElse(name),
    email = user.email.orElse(email),
    phone = user.phone.orElse(phone),
    accent = user.accentId.getOrElse(accent),
    trackingId = user.trackingId.orElse(trackingId),
    searchKey = SearchKey(if (withSearchKey) user.name.getOrElse(name).str else ""),
    picture = user.mediumPicture.map(_.id).orElse(picture),
    deleted = user.deleted,
    handle = user.handle match {
      case Some(h) if !h.toString.isEmpty => Some(h)
      case _ => handle
    },

    user_address = user.user_address,
    locale = user.locale,
    remark = user.remark,
    nature = user.nature,
    bots = user.bots,
    rAssetId = if (user.picture.nonEmpty && user.picture.head.nonEmpty) {
      Option(user.picture.head.head.remoteId.head.str)
    } else {
      rAssetId
    },
    payValidTime = user.payValidTime,
    extid = user.extid,

    providerId = user.service.map(_.provider).orElse(providerId),
    integrationId = user.service.map(_.id).orElse(integrationId),
    expiresAt = user.expiresAt.orElse(expiresAt),
    teamId = user.teamId.orElse(teamId),
    managedBy = user.managedBy.orElse(managedBy),
    fields = user.fields.getOrElse(fields)
  )

  def updatedExtid(extid: Option[String]): UserData = copy(
    extid = extid
  )

  def updated(user: UserSearchEntry): UserData = copy(
    name = user.name,
    searchKey = SearchKey(user.name),
    accent = user.colorId.getOrElse(accent),
    handle = Some(user.handle)
  )

  def updateConnectionStatus(status: UserData.ConnectionStatus, time: Option[RemoteInstant] = None, message: Option[String] = None): UserData = {
    if (time.exists(_.isBefore(this.connectionLastUpdated))) this
    else if (this.connection == status) time.fold(this) { time => this.copy(connectionLastUpdated = time) }
    else {
      val relation = (this.relation, status) match {
        case (_, ConnectionStatus.Accepted) => Relation.First
        case (Relation.First, _) => Relation.Other
        case (rel, _) => rel
      }

      this.copy(
        connection = status,
        relation = relation,
        connectionLastUpdated = time.getOrElse(this.connectionLastUpdated + 1.millis),
        connectionMessage = message.orElse(this.connectionMessage))
    }
  }

  def isGuest(ourTeamId: TeamId): Boolean = isGuest(Some(ourTeamId))

  def isGuest(ourTeamId: Option[TeamId]): Boolean = ourTeamId.isDefined && teamId != ourTeamId

  def isPartner(ourTeamId: Option[TeamId]): Boolean =
    teamId.isDefined && teamId == ourTeamId && decodeBitmask(permissions._1) == PartnerPermissions

  def isInTeam(otherTeamId: Option[TeamId]): Boolean = teamId.isDefined && teamId == otherTeamId

  def matchesFilter(filter: String): Boolean = {
    val isHandleSearch = Handle.isHandle(filter)
    matchesFilter(filter, isHandleSearch)
  }

  def matchesFilter(filter: String, handleOnly: Boolean): Boolean =
    handle.exists(_.startsWithQuery(filter)) || remark.exists(_.contains(filter)) ||
      (!handleOnly && (SearchKey(filter).isAtTheStartOfAnyWordIn(searchKey) || email.exists(e => filter.trim.equalsIgnoreCase(e.str))))

  def matchesQuery(query: Option[SearchKey] = None, handleOnly: Boolean = false): Boolean = query match {
    case Some(q) =>
      this.handle.map(_.string).contains(q.asciiRepresentation) || (!handleOnly && q.isAtTheStartOfAnyWordIn(this.searchKey))
    case _ => true
  }
}

object UserData {

  lazy val Empty = UserData(UserId("EMPTY"), "")

  type ConnectionStatus = com.waz.api.User.ConnectionStatus
  object ConnectionStatus {
    import com.waz.api.User.ConnectionStatus._

    val Unconnected = UNCONNECTED
    val PendingFromUser = PENDING_FROM_USER
    val PendingFromOther = PENDING_FROM_OTHER
    val Accepted = ACCEPTED
    val Blocked = BLOCKED
    val Ignored = IGNORED
    val Self = SELF
    val Cancelled = CANCELLED

    val codeMap = Seq(Unconnected, PendingFromOther, PendingFromUser, Accepted, Blocked, Ignored, Self, Cancelled).map(v => v.code -> v).toMap

    def apply(code: String) = codeMap.getOrElse(code, Unconnected)

    def isConnected(status: ConnectionStatus) = status == Accepted || status == Blocked || status == Self
  }

  // used for testing only
  def apply(name: String): UserData = UserData(UserId(name), name = Name(name), searchKey = SearchKey.simple(name))

  def apply(id: UserId, name: String): UserData = UserData(id, None, Name(name), None, None, searchKey = SearchKey(name), handle = None)

  def apply(entry: UserSearchEntry): UserData =
    UserData(
      id        = entry.id,
      teamId    = None,
      name      = entry.name,
      accent    = entry.colorId.getOrElse(0),
      searchKey = SearchKey(entry.name),
      handle    = Some(entry.handle)
    ) // TODO: improve connection, relation, search level stuff

  def apply(user: UserInfo): UserData = apply(user, withSearchKey = true)

  def apply(user: UserInfo, withSearchKey: Boolean): UserData = UserData(user.id, user.name.getOrElse(Name.Empty)).updated(user, withSearchKey)

  implicit object UserDataDao extends Dao[UserData, UserId] {
    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.id)
    val TeamId = opt(id[TeamId]('teamId))(_.teamId)
    val Name = text[model.Name]('name, _.str, model.Name(_))(_.name)
    val Email = opt(emailAddress('email))(_.email)
    val Phone = opt(phoneNumber('phone))(_.phone)
    val TrackingId = opt(id[TrackingId]('tracking_id))(_.trackingId)
    val Picture = opt(id[AssetId]('picture))(_.picture)
    val Accent = int('accent)(_.accent)
    val SKey = text[SearchKey]('skey, _.asciiRepresentation, SearchKey.unsafeRestore)(_.searchKey)
    val Conn = text[ConnectionStatus]('connection, _.code, ConnectionStatus(_))(_.connection)
    val ConnTime = date('conn_timestamp)(_.connectionLastUpdated.javaDate) //TODO: Migrate instead?
    val ConnMessage = opt(text('conn_msg))(_.connectionMessage)
    val Conversation = opt(id[RConvId]('conversation))(_.conversation)
    val Rel = text[Relation]('relation, _.name, Relation.valueOf)(_.relation)
    val Timestamp = opt(localTimestamp('timestamp))(_.syncTimestamp)
    val DisplayName = text[model.Name]('display_name, _.str, model.Name(_))(_.displayName)
    val Verified = text[Verification]('verified, _.name, Verification.valueOf)(_.verified)
    val Deleted = bool('deleted)(_.deleted)
    val AvailabilityStatus = int[Availability]('availability, _.id, Availability.apply)(_.availability)
    val Handle = opt(handle('handle))(_.handle)

    val User_address = opt(text('user_address))(_.user_address)
    val Locale = opt(text('locale))(_.locale)
    val Remark = opt(text('remark))(_.remark)
    val Nature = opt(int('nature))(_.nature)
    val Bots = opt(text('bots))(_.bots)
    val RAssetId = opt(text('remote_asset_id))(_.rAssetId)
    val PayValidTime = opt(int('pay_valid_time))(_.payValidTime)
    val Extid = opt(text('extid))(_.extid)

    val ProviderId = opt(id[ProviderId]('provider_id))(_.providerId)
    val IntegrationId = opt(id[IntegrationId]('integration_id))(_.integrationId)
    val ExpiresAt = opt(remoteTimestamp('expires_at))(_.expiresAt)
    val Managed = opt(text[ManagedBy]('managed_by, _.toString, ManagedBy(_)))(_.managedBy)
    val Fields = json[Seq[UserField]]('fields)(UserField.userFieldsDecoder, UserField.userFieldsEncoder)(_.fields)
    val SelfPermissions = long('self_permissions)(_.permissions._1)
    val CopyPermissions = long('copy_permissions)(_.permissions._2)
    val CreatedBy = opt(id[UserId]('created_by))(_.createdBy)

    override val idCol = Id
    override val table = Table(
      "Users", Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, ConnTime, ConnMessage,
      Conversation, Rel, Timestamp, DisplayName, Verified, Deleted, AvailabilityStatus, Handle,
      User_address, Locale, Remark, Nature, Bots, RAssetId,PayValidTime,Extid,
      ProviderId, IntegrationId, ExpiresAt, Managed, SelfPermissions, CopyPermissions, CreatedBy // Fields are now lazy-loaded from BE every time the user opens a profile
    )

    override def apply(implicit cursor: DBCursor): UserData = new UserData(
      Id, TeamId, Name, Email, Phone, TrackingId, Picture, Accent, SKey, Conn, RemoteInstant.ofEpochMilli(ConnTime.getTime), ConnMessage,
      Conversation, Rel, Timestamp, DisplayName, Verified, Deleted, AvailabilityStatus, Handle,
      User_address, Locale, Remark, Nature, Bots, RAssetId,PayValidTime, Extid,
      ProviderId, IntegrationId, ExpiresAt, Managed, Seq.empty, (SelfPermissions, CopyPermissions), CreatedBy
    )

    override def onCreate(db: DB): Unit = {
      super.onCreate(db)
      db.execSQL(s"CREATE INDEX IF NOT EXISTS Conversation_id on Users (${Id.name})")
      db.execSQL(s"CREATE INDEX IF NOT EXISTS UserData_search_key on Users (${SKey.name})")
    }

    def get(id: UserId)(implicit db: DB): Option[UserData] = single(find(Id, id)(db))

    override def getCursor(id: UserId)(implicit db: DB): DBCursor = find(Id, id)(db)

    override def delete(id: UserId)(implicit db: DB): Int = db.delete(table.name, Id.name + "=?", Array(id.toString))

    def findByConnectionStatus(status: Set[ConnectionStatus])(implicit db: DB): Managed[Iterator[UserData]] = iterating(findInSet(Conn, status))

    def findAll(users: Set[UserId])(implicit db: DB) = iterating(findInSet(Id, users))

    def listContacts(implicit db: DB) = list(db.query(table.name, null, s"(${Conn.name} = ? or ${Conn.name} = ?) and ${Deleted.name} = 0", Array(ConnectionStatus.Accepted.code, ConnectionStatus.Blocked.code), null, null, null))

    def topPeople(implicit db: DB): Managed[Iterator[UserData]] =
      search(s"${Conn.name} = ? and ${Deleted.name} = 0", Array(Conn(ConnectionStatus.Accepted)))

    def recommendedPeople(prefix: String)(implicit db: DB): Managed[Iterator[UserData]] = {
      val query = SearchKey(prefix)
      search(s"""(
                |  (
                |    (
                |      ${SKey.name} LIKE ? OR ${SKey.name} LIKE ?
                |    ) AND (${Rel.name} = '${Rel(Relation.First)}' OR ${Rel.name} = '${Rel(Relation.Second)}' OR ${Rel.name} = '${Rel(Relation.Third)}')
                |  ) OR ${Email.name} = ?
                |    OR ${Handle.name} LIKE ?
                |) AND ${Deleted.name} = 0
                |  AND ${Conn.name} != '${Conn(ConnectionStatus.Accepted)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Blocked)}' AND ${Conn.name} != '${Conn(ConnectionStatus.Self)}'
              """.stripMargin,
        Array(s"${query.asciiRepresentation}%", s"% ${query.asciiRepresentation}%", prefix, s"%${query.asciiRepresentation}%"))
    }

    def search(whereClause: String, args: Array[String])(implicit db: DB): Managed[Iterator[UserData]] =
      iterating(db.query(table.name, null, whereClause, args, null, null,
        s"case when ${Conn.name} = '${Conn(ConnectionStatus.Accepted)}' then 0 when ${Rel.name} != '${Relation.Other.name}' then 1 else 2 end ASC, ${Name.name} ASC"))

    def search(prefix: SearchKey, handleOnly: Boolean, teamId: Option[TeamId])(implicit db: DB): Set[UserData] = {
      val select = s"SELECT u.* FROM ${table.name} u WHERE "
      val handleCondition =
        if (handleOnly){
          s"""u.${Handle.name} LIKE '%${prefix.asciiRepresentation}%'""".stripMargin
        } else {
          s"""(
             |     u.${SKey.name} LIKE '${SKey(prefix)}%'
             |     OR u.${SKey.name} LIKE '% ${SKey(prefix)}%'
             |     OR u.${Handle.name} LIKE '%${prefix.asciiRepresentation}%')""".stripMargin
        }
      val teamCondition = teamId.map(tId => s"AND u.${TeamId.name} = '$tId'")

      list(db.rawQuery(select + " " + handleCondition + teamCondition.map(qu => s" $qu").getOrElse(""), null)).toSet
    }

    def findForTeams(teams: Set[TeamId])(implicit db: DB) = iterating(findInSet(TeamId, teams.map(Option(_))))

    def findService(integrationId: IntegrationId)(implicit db: DB) = iterating(find(IntegrationId, Some(integrationId)))

    def findByuserIds(userIds: Set[UserId])(implicit db: DB) = iterating(findInSet(Id, userIds))
  }
}
