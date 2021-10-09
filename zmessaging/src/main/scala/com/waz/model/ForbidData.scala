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

import android.database.DatabaseUtils.{sqlEscapeString => escape}
import com.waz.db.Col.{id, int, opt, remoteTimestamp, text}
import com.waz.db.{Dao2, Reader, iteratingWithReader}
import com.waz.log.LogShow.SafeToLog
import com.waz.utils.JsonEncoder.encodeInstant
import com.waz.utils.wrappers.{DB, DBCursor}
import com.waz.utils.{Identifiable, JsonDecoder, JsonEncoder, RichWireInstant}
import org.json.JSONObject


case class ForbidData(message: MessageId,
                      userId: UserId,
                      userName: Option[String],
                      timestamp: RemoteInstant,
                      types: ForbidData.Types,
                      action: ForbidData.Action)
    extends Identifiable[ForbidData.Id] {
  override val id: ForbidData.Id = (message, types)

  def max(other: ForbidData) =
    if (other.message == message && other.types == types && other.timestamp.isAfter(timestamp)) other else this
}

object ForbidData {
  type Id = (MessageId, Types)

  def forbidAction: Action   = Action.Forbid
  def unForbidAction: Action = Action.UnForbid

  @SerialVersionUID(1L) sealed abstract class Action(val serial: Int) extends Serializable with SafeToLog


  object Action {
    case object UnKnown extends Action(MessageActions.Action_UnKnown)
    case object Forbid   extends Action(MessageActions.Action_Forbid)
    case object UnForbid extends Action(MessageActions.Action_UnForbid)

    val decode: Int => Action = {
      case Forbid.serial   => Forbid
      case UnForbid.serial => UnForbid
      case _               => UnKnown
    }

    val values = Set(Forbid, UnForbid)
  }

  @SerialVersionUID(1L) sealed abstract class Types(val serial: Int) extends Serializable with SafeToLog


  object Types {
    case object Forbid  extends Types(MessageTypes.Type_Forbid)
    case object UnKnown extends Types(MessageTypes.Type_UnKnown)

    val decode: Int => Types = {
      case Forbid.serial => Forbid
      case _             => UnKnown
    }

    val values = Set(Forbid, UnKnown)
  }

  type Aggregate = (MessageId, Map[ForbidData.Types, (UserId, Option[String], Action, RemoteInstant)])

  implicit object ForbidDao extends Dao2[ForbidData, MessageId, Types] {
    val Message   = id[MessageId]('message_id).apply(_.message)
    val UserId    = id[UserId]('user_id).apply(_.userId)
    val UserName  = opt(text('user_name))(_.userName)
    val Timestamp = remoteTimestamp('timestamp)(_.timestamp)
    val TypeCol   = int[Types]('types, _.serial, ForbidData.Types.decode)(_.types)
    val ActionCol = int[Action]('action, _.serial, ForbidData.Action.decode)(_.action)

    override val idCol = (Message, TypeCol)

    override val table = Table("Forbids", Message, UserId, UserName, Timestamp, TypeCol, ActionCol)

    override def apply(implicit cursor: DBCursor): ForbidData = ForbidData(Message, UserId, UserName, Timestamp, TypeCol, ActionCol)

    def findForMessage(id: MessageId)(implicit db: DB) = iterating(find(Message, id))

    def findForMessageOfType(id: MessageId, types: ForbidData.Types)(implicit db: DB) =
      iterating {
        db.query(table.name,
          null,
          s"${Message.name} = ? AND ${TypeCol.name} = ?",
          Array(Message(id), TypeCol(types)),
          null,
          null,
          null)
      }

    def findForMessages(ids: Set[MessageId])(implicit db: DB) = iterating {
      db.query(table.name,
               null,
               s"${Message.name} in (${ids.map(id => escape(id.str)).mkString(", ")})",
               null,
               null,
               null,
               null)
    }

    def findMaxTime(implicit db: DB) =
      iteratingWithReader(InstantReader)(db.rawQuery(s"SELECT MAX(${Timestamp.name}) FROM ${table.name}", null))
        .acquire(t => if (t.hasNext) t.next else RemoteInstant.Epoch)

    object InstantReader extends Reader[RemoteInstant] {
      override def apply(implicit c: DBCursor): RemoteInstant = Timestamp.load(c, 0)
    }
  }

  implicit lazy val ForbidEncoder: JsonEncoder[ForbidData] = new JsonEncoder[ForbidData] {
    override def apply(forbid: ForbidData): JSONObject = JsonEncoder { o =>
      o.put("message", forbid.message.str)
      o.put("userId", forbid.userId.str)
      o.put("userName", forbid.userName.getOrElse(""))
      o.put("timestamp", encodeInstant(forbid.timestamp.instant))
      o.put("types", forbid.types.serial)
      o.put("action", forbid.action.serial)
    }
  }

  import com.waz.utils.JsonDecoder._

  implicit lazy val ContactDataDecoder: JsonDecoder[ForbidData] = new JsonDecoder[ForbidData] {
    override def apply(implicit js: JSONObject): ForbidData =
      ForbidData(
        decodeId[MessageId]('message),
        decodeId[UserId]('userId),
        decodeOptString('userName),
        decodeRemoteInstant('timestamp),
        Types.decode(decodeInt('types)),
        Action.decode(decodeInt('action))
      )
  }
}
