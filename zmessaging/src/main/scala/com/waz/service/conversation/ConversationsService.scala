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
package com.waz.service.conversation

import com.softwaremill.macwire._
import com.waz.api.IConversation.Access
import com.waz.api.impl.ErrorResponse
import com.waz.api.{ErrorType, Message}
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.ConversationData.ConversationType.isOneToOne
import com.waz.model.ConversationData.{ConversationType, Link, getAccessAndRoleForGroupConv}
import com.waz.model._
import com.waz.service._
import com.waz.service.assets.AssetService
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.messages.{MessagesContentUpdater, MessagesService}
import com.waz.service.push.PushService
import com.waz.service.tracking.{GuestsAllowedToggled, TrackingService}
import com.waz.sync.client.ConversationsClient.ConversationResponse
import com.waz.sync.client.ConversationsClient.ConversationResponse.{getAssets, isVerifiedArr}
import com.waz.sync.client.{ConversationsClient, ErrorOr}
import com.waz.sync.{SyncRequestService, SyncServiceHandle}
import com.waz.threading.Threading
import com.waz.utils.JsonDecoder._
import com.waz.utils._
import com.waz.utils.events.{EventContext, Signal}
import org.json.JSONObject

import scala.collection.{breakOut, mutable}
import scala.concurrent.Future
import scala.concurrent.Future.successful
import scala.util.Try
import scala.util.control.{NoStackTrace, NonFatal}

trait ConversationsService {
  def content: ConversationsContentUpdater
  def convStateEventProcessingStage: EventScheduler.Stage
  def processConversationEvent(ev: ConversationStateEvent, selfUserId: UserId, retryCount: Int = 0): Future[Any]
  def getSelfConversation: Future[Option[ConversationData]]
  def updateConversationsWithDeviceStartMessage(conversations: Seq[ConversationResponse],needSyncUsers : Boolean = true): Future[Unit]

  def setConversationPlaceTop(id: ConvId, place_top: Boolean): Future[Option[ConversationData]]

  def setConversationArchived(id: ConvId, archived: Boolean): Future[Option[ConversationData]]
  def setReceiptMode(id: ConvId, receiptMode: Int): Future[Option[ConversationData]]
  def forceNameUpdate(id: ConvId): Future[Option[(ConversationData, ConversationData)]]
  def onMemberAddFailed(conv: ConvId, users: Set[UserId], error: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false): Future[Unit]
  def onMemberAddSucExist(conv: ConvId, existUsers: Set[UserId], errTpe: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false): Future[Unit]
  def onMemberAddSuc(conv: ConvId, userIds: Set[UserId], errTpe: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false): Future[Unit]
  def groupConversation(convId: ConvId): Signal[Boolean]
  def isGroupConversation(convId: ConvId): Future[Boolean]
  def isWithService(convId: ConvId): Future[Boolean]

  def setToTeamOnly(convId: ConvId, teamOnly: Boolean): ErrorOr[Unit]
  def createLink(convId: ConvId): ErrorOr[Link]
  def removeLink(convId: ConvId): ErrorOr[Unit]

  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]]

  /**
    * This method is used to update conversation state whenever we detect a user on sending or receiving a message
    * who we didn't expect to be there - we need to expose these users to the self user
    */
  def addUnexpectedMembersToConv(convId: ConvId, us: Set[UserId], isAddJoinMessage: Boolean = true): Future[Unit]

  def updateGroupRAssetId(previewAsset: AssetData, completedAsset: AssetData, rConvId: RConvId): Future[Future[Option[ConversationData]]]

  def updateGroupPicture(image: RawAssetInput, rConvId: RConvId): Future[Product]

  def checkCreateFirstMessage(convs: Set[ConversationData]): Future[Any]
}

class ConversationsServiceImpl(teamId: Option[TeamId],
                               selfUserId: UserId,
                               push: PushService,
                               users: UserService,
                               usersStorage: UsersStorage,
                               membersStorage: MembersStorage,
                               convsStorage: ConversationStorage,
                               val content: ConversationsContentUpdater,
                               sync: SyncServiceHandle,
                               errors: ErrorsService,
                               messages: MessagesService,
                               msgContent: MessagesContentUpdater,
                               userPrefs: UserPreferences,
                               requests: SyncRequestService,
                               eventScheduler: => EventScheduler,
                               tracking: TrackingService,
                               client: ConversationsClient,
                               selectedConv: SelectedConversationService,
                               syncReqService: SyncRequestService,
                               assetService: AssetService,
                               conversationsUi: ConversationsUiController,
                               aliasStorage: AliasStorage) extends ConversationsService with DerivedLogTag {

  private implicit val ev = EventContext.Global
  import Threading.Implicits.Background

  private val nameUpdater = wire[NameUpdater]
  nameUpdater.registerForUpdates()

  //On conversation changed, update the state of the access roles as part of migration, then check for a link if necessary
  selectedConv.selectedConversationId {
    case Some(convId) => convsStorage.get(convId).flatMap {
      case Some(conv) if conv.accessRole.isEmpty =>
        for {
          syncId        <- sync.syncConversations(Set(conv.id),needSyncUsers = false)
          _             <- syncReqService.await(syncId)
          Some(updated) <- content.convById(conv.id)
        } yield if (updated.access.contains(Access.CODE)) sync.syncConvLink(conv.id)

      case _ => Future.successful({})
    }
    case None => //
  }

  val convStateEventProcessingStage = EventScheduler.Stage[ConversationStateEvent] { (_, events) =>
    RichFuture.traverseSequential(events)(processConversationEvent(_, selfUserId))
  }

  push.onHistoryLost { req =>
    verbose(l"onSlowSyncNeeded($req)")
    // TODO: this is just very basic implementation creating empty message
    // This should be updated to include information about possibly missed changes
    // this message will be shown rarely (when notifications stream skips data)
    convsStorage.list.flatMap(messages.addHistoryLostMessages(_, selfUserId))
  }

  errors.onErrorDismissed {
    case ErrorData(_, ErrorType.CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER, _, _, Some(convId), _, _, _, _) =>
      deleteConversation(convId)
    case ErrorData(_, ErrorType.CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION, userIds, _, Some(convId), _, _, _, _) => Future.successful(())
    case ErrorData(_, ErrorType.CANNOT_ADD_USER_TO_FULL_CONVERSATION, userIds, _, Some(convId), _, _, _, _) => Future.successful(())
    case ErrorData(_, ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION, _, _, Some(conv), _, _, _, _) =>
      convsStorage.setUnknownVerification(conv)
  }

  def processConversationEvent(ev: ConversationStateEvent, selfUserId: UserId, retryCount: Int = 0) = ev match {
    case CreateConversationEvent(_, time, from, data) =>
      updateConversations(Seq(data)).flatMap { case (_, created) => Future.traverse(created) { created =>
        messages.addConversationStartMessage(created.id, from, (data.members + selfUserId).filter(_ != from), created.name, readReceiptsAllowed = created.readReceiptsAllowed, time = Some(time))
      }}

    case ConversationEvent(rConvId, _, _) =>
      verbose(l"conversation data processConversationEvent 00 rConvId:${rConvId},${ev},selfUserId:${selfUserId}")
      content.convByRemoteId(rConvId) flatMap {
        case Some(conv) => {
          verbose(l"conversation data processConversationEvent 11 conv.convType:${conv.convType}")
          processUpdateEvent(conv, ev)
        }
        case None if retryCount > 3 =>
          tracking.exception(new Exception("No conversation data found for event") with NoStackTrace, "No conversation data found for event")
          successful(())
        case None =>
          ev match {
            case MemberJoinEvent(_, time, from, members, _, _, eId, memsum) if from != selfUserId =>
              // this happens when we are added to group conversation
              verbose(l"conversation data processConversationEvent 22")
              for {
                conv <- convsStorage.insert(ConversationData(ConvId(), rConvId, None, from, ConversationType.Group, lastEventTime = time,advisory_show_dialog=members.contains(selfUserId)))
                ms   <- membersStorage.add(conv.id, from +: members)
                sId  <- sync.syncConversations(Set(conv.id))
                _    <- syncReqService.await(sId)
                Some(conv) <- convsStorage.get(conv.id)
                _    <- if (conv.receiptMode.exists(_ > 0)) messages.addReceiptModeIsOnMessage(conv.id) else Future.successful(None)
                _    <- messages.addMemberJoinMessage(conv.id, from, members.toSet)
              } yield {
                conversationsUi.onSelfJoinConversation(selfUserId, conv)
              }
            case _ =>
              warn(l"No conversation data found for event: $ev on try: $retryCount")
              content.processConvWithRemoteId(rConvId, retryAsync = true) { processUpdateEvent(_, ev) }
          }
      }
  }

  val URL_INVITE = "url_invite"
  val CONFIRM = "confirm"
  val ADDRIGHT = "addright"
  val NEW_CREATOR = "new_creator"
  val ASSET_UPDATE = "assets"
  val MEMSUM = "memsum"
  val APPS = "apps"
  val Viewmem = "viewmem"
  val Memberjoin_Confirm = "memberjoin_confirm"
  val Block_time = "block_time"
  val View_chg_mem_notify = "view_chg_mem_notify"
  val Add_friend = "add_friend"
  val Orator = "orator"
  val OratorSYMBOL = 'orator
  val Manager = "manager"
  val ManagerSYMBOL = 'manager
  val Block_duration = "block_duration"
  val Advisory = "advisory"
  val Msg_only_to_manager = "msg_only_to_manager"
  val Show_invitor_list = "show_invitor_list"
  val Blocked = "blocked"
  val Show_memsum = "show_memsum"
  val Enabled_edit_msg = "enabled_edit_msg"
  val Request_edit_msg = "request_edit_msg"

  private def processUpdateEvent(conv: ConversationData, ev: ConversationEvent) = ev match {
    case RenameConversationEvent(_, _, _, name, eId) => content.updateConversationName(conv.id, name)

    case MemberJoinEvent(_, _, _, userIds, _, _, eId, memsum) =>
      verbose(l"conversation data processConversationEvent 33 conv.convType:${conv.convType}")
      val selfAdded = userIds.contains(selfUserId) //we were re-added to a group and in the meantime might have missed events
      for {
        convSync <- if (selfAdded)
          sync.syncConversations(Set(conv.id)).map(Option(_))
        else
          Future.successful(None)
        _ <- convsStorage.update(conv.id,_.copy(memsum = memsum,advisory_show_dialog=selfAdded))
        syncId <- if(conv.convType == ConversationType.ThousandsGroup) Future.successful(None) else users.syncIfNeeded(userIds.toSet)
        _ <- syncId.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
        _ <- membersStorage.add(conv.id, userIds)
        _ <- if (userIds.contains(selfUserId)) content.setConvActive(conv.id, active = true) else successful(None)
        _ <- convSync.fold(Future.successful(()))(sId => syncReqService.await(sId).map(_ => ()))
        Some(conv) <- convsStorage.get(conv.id)
        _ <- if (selfAdded && conv.receiptMode.exists(_ > 0)) messages.addReceiptModeIsOnMessage(conv.id) else Future.successful(None)
        _ = verbose(l"processUpdateEvent conv.convType:${conv.convType}")
        _ <- if(conv.convType == ConversationType.OneToOne || conv.convType == ConversationType.WaitForConnection) checkCreateFirstMessage(Set(conv)) else Future.successful(None)
      } yield {
        if(selfAdded){
          conversationsUi.onSelfJoinConversation(selfUserId, conv)
        }
      }

    case MemberLeaveEvent(_, time, from, userIds,eId, memsum) =>

        convsStorage.update(conv.id, _.copy(memsum = memsum)).flatMap { res =>
          Future.successful(res)
        }

      membersStorage.remove(conv.id, userIds) flatMap { _ =>
        if (userIds.contains(selfUserId)) {
          content.setConvActive(conv.id, active = false).map { _ =>
            // if the user removed themselves from another device, archived on this device
            if (from.equals(selfUserId) && userIds.contains(selfUserId)) {
              content.updateConversationState(conv.id, ConversationState(Some(true), Some(time)))
            }
          }
        }
        else successful(())
      }

    case MemberUpdateEvent(_, _, _, state, eId) => content.updateConversationState(conv.id, state)

    case ConnectRequestEvent(_, _, from, _, recipient, _, _, eId) =>
      debug(l"ConnectRequestEvent(from = $from, recipient = $recipient")
      membersStorage.add(conv.id, Set(from, recipient)).flatMap { added =>
        users.syncIfNeeded(added.map(_.userId))
      }

    case ConversationAccessEvent(_, _, _, access, accessRole, eId) =>
      content.updateAccessMode(conv.id, access, Some(accessRole))

    case ConversationCodeUpdateEvent(_, _, _, l, eId) =>
      convsStorage.update(conv.id, _.copy(link = Some(l)))

    case ConversationCodeDeleteEvent(_, _, _, eId) =>
      convsStorage.update(conv.id, _.copy(link = None))

    case ConversationReceiptModeEvent(_, _, _, receiptMode, eId) =>
      content.updateReceiptMode(conv.id, receiptMode = receiptMode)

    case MessageTimerEvent(_, time, from, duration, eId) =>
      convsStorage.update(conv.id, _.copy(globalEphemeral = duration))

    case ConvChangeTypeEvent(_, _, _, _, newType, eId) =>

      convsStorage.update(conv.id, { conv =>
        if (newType == 0) conv.copy(convType = ConversationType.Group)
        else conv.copy(convType = ConversationType.ThousandsGroup)
      })

    case ConvUpdateSettingEvent(_, _, _, _, contentString, eId) =>
      var convCopy = conv.copy()
      val optModel = new JSONObject(contentString)
      if (optModel.has(URL_INVITE)) {
        val url_invite = optModel.optBoolean(URL_INVITE)
        convCopy = convCopy.copy(url_invite = url_invite)
      }
      if (optModel.has(CONFIRM)) {
        val confirm = optModel.optBoolean(CONFIRM)
        convCopy = convCopy.copy(confirm = confirm)
      }
      if (optModel.has(ADDRIGHT)) {
        val addright = optModel.optBoolean(ADDRIGHT)
        convCopy = convCopy.copy(addright = addright)
      }
      if (optModel.has(NEW_CREATOR)) {
        val newCreator = optModel.optString(NEW_CREATOR)
        convCopy = convCopy.copy(creator = UserId(newCreator))
      }
      if (optModel.has(ASSET_UPDATE)) {
        convCopy = convCopy.copy(assets = getAssets(optModel))
      }
      if (optModel.has(APPS)) {
        val optApps = if (isVerifiedArr(APPS)(optModel)) Option(decodeWebAppIdSeq('apps)(optModel)) else None
        convCopy = convCopy.copy(apps = optApps.getOrElse(Seq.empty))
      }

      if (optModel.has(MEMSUM)) {
        val memsum = optModel.optInt(MEMSUM)
        convCopy = convCopy.copy(memsum = Option(memsum))
      }
      if (optModel.has(Viewmem)) {
        val viewmem = optModel.optBoolean(Viewmem)
        convCopy = convCopy.copy(viewmem = viewmem)
        //        convsStorage.update(conv.id, { conv => conv.copy(viewmem = viewmem) })
      }
      if (optModel.has(Memberjoin_Confirm)) {
        val memberjoin_confirm = optModel.optBoolean(Memberjoin_Confirm)
        convCopy = convCopy.copy(memberjoin_confirm = memberjoin_confirm)
      }
      if (optModel.has(Block_time)) {
        convCopy = convCopy.copy(block_time = Option(optModel.getString(Block_time)))
      }
      if (optModel.has(Block_duration)) {
        convCopy = convCopy.copy(block_duration = Option(optModel.getInt(Block_duration)))
      }
      if (optModel.has(View_chg_mem_notify)) {
        convCopy = convCopy.copy(view_chg_mem_notify = optModel.getBoolean(View_chg_mem_notify))
      }
      if (optModel.has(Add_friend)) {
        convCopy = convCopy.copy(add_friend = optModel.getBoolean(Add_friend))
      }

      if(optModel.has(Orator)){
        val orator = decodeUserIdSeq(OratorSYMBOL)(optModel)
        convCopy = convCopy.copy(orator = orator)
        if(orator.nonEmpty){
          usersStorage.get(orator.head) onComplete{
            case scala.util.Success(user) =>
            case _ =>
              users.syncUsers(orator.toSet)
          }
        }
      }

      if(optModel.has(Manager)){
        val manager = decodeUserIdSeq(ManagerSYMBOL)(optModel)
        convCopy = convCopy.copy(manager = manager)
        if(manager.nonEmpty){
          usersStorage.get(manager.head) onComplete{
            case scala.util.Success(user) =>
            case _ =>
              users.syncUsers(manager.toSet)
          }
        }
      }

      if(optModel.has(Advisory)) {
        convCopy = convCopy.copy(advisory = Option(optModel.getString(Advisory)), advisory_is_read = false)
      }

      if (optModel.has(Msg_only_to_manager)) {
        val msg_only_to_manager = optModel.optBoolean(Msg_only_to_manager)
        convCopy = convCopy.copy(msg_only_to_manager = msg_only_to_manager)
      }

      if (optModel.has(Show_invitor_list)) {
        val show_invitor_list = optModel.optBoolean(Show_invitor_list)
        convCopy = convCopy.copy(show_invitor_list = show_invitor_list)
      }

      if (optModel.has(Blocked)) {
        val blocked = optModel.optBoolean(Blocked, false)
        convCopy = convCopy.copy(blocked = blocked)
      }

      if (optModel.has(Show_memsum)) {
        val show_memsum = optModel.optBoolean(Show_memsum, true)
        convCopy = convCopy.copy(show_memsum = show_memsum)
      }

      if (optModel.has(Enabled_edit_msg)) {
        val enabled_edit_msg = optModel.optBoolean(Enabled_edit_msg, true)
        convCopy = convCopy.copy(enabled_edit_msg = enabled_edit_msg)
      }

      convsStorage.update(conv.id, { conv => convCopy })
    case ConvUpdateSettingSingleEvent(_, _, _, fromUserId, contentString, eId) =>
      var convCopy = conv.copy()
      val optModel = new JSONObject(contentString)

      val blockUser = optModel.optString("block_user")
      if (blockUser.equalsIgnoreCase(selfUserId.str)) {
        if (optModel.has(Block_time)) {
          convCopy = convCopy.copy(single_block_time = Option(optModel.getString(Block_time)))
        }
        if (optModel.has(Block_duration)) {
          convCopy = convCopy.copy(single_block_duration = Option(optModel.getInt(Block_duration)))
        }
        convsStorage.update(conv.id, { conv => convCopy })
      } else {
        val forbiddenUser = UserId(blockUser)
        val blockEndTime = optModel.getString(Block_time)
        val blockDuration = optModel.optInt(Block_duration)

        val newForbiddenUsers = convCopy.forbiddenUsers.filterNot(_.user == forbiddenUser)
          .+:(ForbiddenUserData(forbiddenUser, blockDuration, blockEndTime))

        convsStorage.update(conv.id, { conv => convCopy.copy(forbiddenUsers = newForbiddenUsers) })
      }
    case AliasEvent(_, _, from, contentString) =>
      val jsonOption = Try(new JSONObject(contentString)).toOption
      val newIsEnable = jsonOption.filter(_.has("alias_name")).exists(_.getBoolean("alias_name"))
      val newAliasName = jsonOption.filter(_.has("alias_name_ref")).map(_.getString("alias_name_ref")).flatMap(str => Some(Name(str)))
      aliasStorage.put((conv.id, from), new AliasData(conv.id, from, newAliasName, newIsEnable))
    case _ => successful(())
  }

  def getSelfConversation = {
    val selfConvId = ConvId(selfUserId.str)
    content.convById(selfConvId).flatMap {
      case Some(c) => successful(Some(c))
      case _ =>
        for {
          user  <- usersStorage.get(selfUserId)
          conv  =  ConversationData(ConvId(selfUserId.str), RConvId(selfUserId.str), None, selfUserId, ConversationType.Self, generatedName = user.map(_.name).getOrElse(Name.Empty))
          saved <- convsStorage.getOrCreate(selfConvId, conv).map(Some(_))
        } yield saved
    }
  }

  def updateConversationsWithDeviceStartMessage(conversations: Seq[ConversationResponse],needSyncUsers : Boolean = true) =
    for {
      (_, created) <- updateConversations(conversations,needSyncUsers)
      _            <- messages.addDeviceStartMessages(created, selfUserId)
    } yield {}

  private def updateConversations(responses: Seq[ConversationResponse],needSyncUsers : Boolean = true): Future[(Seq[ConversationData], Seq[ConversationData])] = {
    verbose(l"updateConversations responses.size:${responses.size}")
    def updateConversationData(): Future[(Set[ConversationData], Seq[ConversationData])] = {
      def findExistingId: Future[Seq[(ConvId, ConversationResponse)]] = convsStorage { convsById =>
        def byRemoteId(id: RConvId) = convsById.values.find(_.remoteId == id)

        responses.map { resp =>
          val newId = if (isOneToOne(resp.convType)) resp.members.find(_ != selfUserId).fold(ConvId())(m => ConvId(m.str)) else ConvId(resp.id.str)

          val matching = byRemoteId(resp.id).orElse {
            convsById.get(newId).orElse {
              if (isOneToOne(resp.convType)) None
              else byRemoteId(ConversationsService.generateTempConversationId(resp.members + selfUserId))
            }
          }

          val r = (matching.fold(newId)(_.id), resp)
          verbose(l"Returning conv id pair $r, isOneToOne: ${isOneToOne(resp.convType)}")
          r
        }
      }

      var created = new mutable.HashSet[ConversationData]

      def updateOrCreate(newLocalId: ConvId, resp: ConversationResponse): (Option[ConversationData] => ConversationData) = { prev =>
        returning(prev.getOrElse(ConversationData(id = newLocalId, hidden = isOneToOne(resp.convType) && resp.members.size <= 1))
          .copy(
            remoteId        = resp.id,
            name            = resp.name.filterNot(_.isEmpty),
            creator         = resp.creator,
            convType        = prev.map(_.convType).filter(oldType => isOneToOne(oldType) && resp.convType != ConversationType.OneToOne).getOrElse(resp.convType),
            team            = resp.team,
            muted           = if (resp.muted == MuteSet.OnlyMentionsAllowed && teamId.isEmpty) MuteSet.AllMuted else resp.muted,
            muteTime        = resp.mutedTime,
            archived        = resp.archived,
            archiveTime     = resp.archivedTime,
            access          = resp.access,
            accessRole      = resp.accessRole,
            link            = resp.link,
            globalEphemeral = resp.messageTimer,
            receiptMode     = resp.receiptMode,

            memsum = if(resp.memsum.isEmpty || resp.memsum.get == 0) Some(resp.members.size + 1) else resp.memsum,
            assets = resp.assets,
            apps = resp.apps.getOrElse(Seq.empty[WebAppId]),
            url_invite = resp.url_invite.getOrElse(false),
            confirm = resp.confirm.getOrElse(false),
            addright = resp.addright.getOrElse(false),
            viewmem = resp.viewmem.getOrElse(false),
            memberjoin_confirm = resp.memberjoin_confirm.getOrElse(false),
            block_time = resp.block_time,
            view_chg_mem_notify = resp.view_chg_mem_notify,
            add_friend = resp.add_friend,
            orator = resp.orator.getOrElse(Seq.empty),
            place_top = resp.place_top,
            auto_reply = resp.auto_reply,
            auto_reply_ref = resp.auto_reply_ref,
            manager = resp.manager.getOrElse(Seq.empty),
            advisory = resp.advisory,
            msg_only_to_manager = resp.msg_only_to_manager.getOrElse(false),
            show_invitor_list = resp.show_invitor_list.getOrElse(false),
            blocked = resp.blocked,
            show_memsum = resp.show_memsum,
            enabled_edit_msg = resp.enabled_edit_msg,
            request_edit_msg = resp.request_edit_msg
          ))(c => if (prev.isEmpty) created += c)
      }

      for {
        withId <- findExistingId
        convs  <- convsStorage.updateOrCreateAll(withId.map { case (localId, resp) => localId -> updateOrCreate(localId, resp) } (breakOut))
      } yield (convs, created.toSeq)
    }

    def updateMembers() =
      content.convsByRemoteId(responses.map(_.id).toSet).flatMap { convs =>
        val toUpdate = responses.map(c => (c.id, c.members)).flatMap {
          case (remoteId, members) => convs.get(remoteId).map(_.id -> (members + selfUserId))
        }.toMap
        membersStorage.setAll(toUpdate)
      }

    def updateAlias() = aliasStorage.insertAll(responses.flatMap(_.aliasUsers).flatten)

    def syncUsers() = users.syncIfNeeded(responses.flatMap(_.members).toSet)

    for {
      (convs, created) <- updateConversationData()
      _                <- updateMembers()
      _                <- updateAlias()
      _                <- if(needSyncUsers) syncUsers() else Future.successful({})
    } yield {
      updateConversationNmae(convs)
      checkCreateFirstMessage(convs)
      verbose(l"updateConversations convs.size:${convs.size} created.size：${created.size}")
      (convs.toSeq, created)
    }

  }

  def getCreateMessageFilter(convType: ConversationType): Boolean = {
    convType == ConversationType.OneToOne || convType == ConversationType.Group || convType == ConversationType.ThousandsGroup
  }

  override def checkCreateFirstMessage(convs: Set[ConversationData]) = {
    verbose(l"createFirstMessage convs.size:${convs.size}")
    Future.traverse(convs.filter { conversationData =>
      getCreateMessageFilter(conversationData.convType) && !conversationData.isServerNotification
    }) { conversationData =>
      Serialized.future("add-local-connect-request-msg", conversationData.id, Message.Type.CONNECT_REQUEST) {
        messages.addLocalConnectRequestMsg(conversationData.id, conversationData.creator, conversationData.convType, conversationData.displayName, conversationData.smallRAssetId)
      }
    } map {
      _.nonEmpty
    }
  }

  def updateConversationNmae(convs : Set[ConversationData]): Unit = {
    convs.filter(_.convType == ConversationType.OneToOne).foreach {
      conv =>
        usersStorage.get(UserId(conv.id.str)) flatMap {
          case Some(user) if !user.deleted => convsStorage.update(conv.id, _.copy(generatedName = user.remark.fold(user.name)(Name)))
          case None => Future successful None
        }
    }
  }

  def setConversationArchived(id: ConvId, archived: Boolean) = content.updateConversationArchived(id, archived) flatMap {
    case Some((_, conv)) =>
      sync.postConversationState(id, ConversationState(archived = Some(conv.archived), archiveTime = Some(conv.archiveTime))) map { _ => Some(conv) }
    case None =>
      Future successful None
  }

  def setConversationPlaceTop(id: ConvId, place_top: Boolean): Future[Option[ConversationData]] = content.updateConversationPlaceTop(id, place_top) flatMap {
    case Some((_, conv)) =>
      sync.postConversationState(id, ConversationState(place_top = Some(place_top))) map { _ => Some(conv) }
    case None =>
      Future successful None
  }

  def setReceiptMode(id: ConvId, receiptMode: Int) = content.updateReceiptMode(id, receiptMode).flatMap {
    case Some((_, conv)) =>
      sync.postReceiptMode(id, receiptMode).map(_ => Some(conv))
    case None =>
      Future successful None
  }

  def updateGroupRAssetId(previewAsset: AssetData, completedAsset: AssetData, rConvId: RConvId): Future[Future[Option[ConversationData]]] =
    convsStorage.getByRemoteId(rConvId) map {
      case Some(convData: ConversationData) =>
        updateAndSync(convData.id, (_: ConversationData).copy(assets = Option(Seq(previewAsset, completedAsset))), (_: ConversationData) =>
          Future.successful({})
        )
      case None => Future successful None
    }


  def updateAndSync(convId: ConvId, updater: ConversationData => ConversationData, sync: ConversationData => Future[_]) =
    updateConversationData(convId, updater) flatMap {
      case Some((p, u))
        if p != u => sync(u) map (_ => Some(u))
      case _ => Future successful None
    }

  def updateConversationData(convId: ConvId, updater: ConversationData => ConversationData) = convsStorage.update(convId, updater)


  def updateGroupPicture(image: RawAssetInput, rConvId: RConvId): Future[Product] =
    assetService.addAsset(image, isProfilePic = false) flatMap {
      assetDataOpt =>
        if (assetDataOpt.isDefined) {
          convsStorage.getByRemoteId(rConvId).flatMap {
            case Some(convData: ConversationData) =>
              sync.postGroupPicture(assetDataOpt.map(_.id), rConvId)
            case None => Future successful None
          }
        } else {
          Future successful None
        }

    }

  private def deleteConversation(convId: ConvId) = for {
    _ <- convsStorage.remove(convId)
    _ <- membersStorage.delete(convId)
    _ <- msgContent.deleteMessagesForConversation(convId: ConvId)
  } yield ()

  def forceNameUpdate(id: ConvId) = {
    warn(l"forceNameUpdate($id)")
    nameUpdater.forceNameUpdate(id)
  }

  def onMemberAddFailed(conv: ConvId, users: Set[UserId], error: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false) = for {
    _ <- error.fold(Future.successful({}))(e => errors.addErrorWhenActive(ErrorData(e, resp, conv, users)).map(_ => {}))
    _ <- if (needConfirm) Future.successful({}) else membersStorage.remove(conv, users)
    _ <- if (needConfirm) Future.successful({}) else messages.removeLocalMemberJoinMessage(conv, users)
  } yield ()

  def onMemberAddSucExist(conv: ConvId, existUsers: Set[UserId], error: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false): Future[Unit] = for {
    _ <- error.fold(Future.successful({}))(e => errors.addErrorWhenActive(ErrorData(e, resp, conv, existUsers)).map(_ => {}))
    _ <- if (needConfirm) Future.successful({}) else messages.removeLocalMemberJoinMessage(conv, existUsers)
  } yield {
    verbose(l"onMemberAddSucExist conv:$conv,existUsers:${Option(existUsers)}")
  }

  def onMemberAddSuc(conv: ConvId, userIds: Set[UserId], error: Option[ErrorType], resp: ErrorResponse, needConfirm: Boolean = false): Future[Unit] = for {
    _ <- error.fold(Future.successful({}))(e => errors.addErrorWhenActive(ErrorData(e, resp, conv, userIds)).map(_ => {}))
  } yield {
    verbose(l"onMemberAddSuc conv:$conv,userIds:${Option(userIds)}")
  }

  def groupConversation(convId: ConvId) =
    convsStorage.signal(convId).map(c => (c.convType, c.name, c.team)).flatMap {
      case (convType, _, _) if convType != ConversationType.Group && convType != ConversationType.ThousandsGroup => Signal.const(false)
      case (_, Some(_), _) | (_, _, None) => Signal.const(true)
      case _ => membersStorage.activeMembers(convId).map(ms => !(ms.contains(selfUserId) && ms.size <= 2))
    }

  def isGroupConversation(convId: ConvId) = groupConversation(convId).head

  def isWithService(convId: ConvId) =
    membersStorage.getActiveUsers(convId)
      .flatMap(usersStorage.getAll)
      .map(_.flatten.exists(_.isWireBot))

  def setToTeamOnly(convId: ConvId, teamOnly: Boolean) =
    teamId match {
      case None => Future.successful(Left(ErrorResponse.internalError("Private accounts can't be set to team-only or guest room access modes")))
      case Some(_) =>
        (for {
          true <- isGroupConversation(convId)
          _ = tracking.track(GuestsAllowedToggled(!teamOnly))
          (ac, ar) = getAccessAndRoleForGroupConv(teamOnly, teamId)
          Some((old, upd)) <- content.updateAccessMode(convId, ac, Some(ar))
          resp <-
            if (old.access != upd.access || old.accessRole != upd.accessRole) {
              client.postAccessUpdate(upd.remoteId, ac, ar)
            }.future.flatMap {
              case Right(_) => Future.successful(Right {})
              case Left(err) =>
                //set mode back on request failed
                content.updateAccessMode(convId, old.access, old.accessRole, old.link).map(_ => Left(err))
            }
            else Future.successful(Right {})
        } yield resp).recover {
          case NonFatal(e) =>
            warn(l"Unable to set team only mode on conversation", e)
            Left(ErrorResponse.internalError("Unable to set team only mode on conversation"))
        }
    }

  override def createLink(convId: ConvId) =
    (for {
      Some(conv) <- content.convById(convId) if conv.isGuestRoom || conv.isWirelessLegacy
      modeResp   <- if (conv.isWirelessLegacy) setToTeamOnly(convId, teamOnly = false) else Future.successful(Right({})) //upgrade legacy convs
      linkResp   <- modeResp match {
        case Right(_) => client.createLink(conv.remoteId).future
        case Left(err) => Future.successful(Left(err))
      }
      _ <- linkResp match {
        case Right(l) => convsStorage.update(convId, _.copy(link = Some(l)))
        case _ => Future.successful({})
      }
    } yield linkResp)
      .recover {
        case NonFatal(e) =>
          error(l"Failed to create link", e)
          Left(ErrorResponse.internalError("Unable to create link for conversation"))
      }

  override def removeLink(convId: ConvId) =
    (for {
      Some(conv) <- content.convById(convId)
      resp       <- client.removeLink(conv.remoteId).future
      _ <- resp match {
        case Right(_) => convsStorage.update(convId, _.copy(link = None))
        case _ => Future.successful({})
      }
    } yield resp)
      .recover {
        case NonFatal(e) =>
          error(l"Failed to remove link", e)
          Left(ErrorResponse.internalError("Unable to remove link for conversation"))
      }

  override def addUnexpectedMembersToConv(convId: ConvId, us: Set[UserId], isAddJoinMessage: Boolean = true) = {
    membersStorage.getByConv(convId).map(_.map(_.userId).toSet).map(us -- _).flatMap {
      case unexpected if unexpected.nonEmpty =>
        for {
          _ <- users.syncIfNeeded(unexpected)
          _ <- membersStorage.add(convId, unexpected)
          _ <- if (!isAddJoinMessage) Future.successful({}) else Future.traverse(unexpected)(u => messages.addMemberJoinMessage(convId, u, Set(u), forceCreate = true)) //add a member join message for each user discovered
        } yield {}
      case _ => Future.successful({})
    }
  }

  override def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]] = {
    convsStorage.getByRemoteId(remoteId)
  }
}

object ConversationsService {

  import scala.concurrent.duration._

  val RetryBackoff = new ExponentialBackoff(500.millis, 3.seconds)

  /**
   * Generate temp ConversationID to identify conversations which don't have a RConvId yet
   */
  def generateTempConversationId(users: Set[UserId]) =
    RConvId(users.toSeq.map(_.toString).sorted.foldLeft("")(_ + _))
}
