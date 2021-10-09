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
package com.waz.zclient.conversation

import android.app.{Activity, PendingIntent}
import android.content.pm.{ShortcutInfo, ShortcutManager}
import android.content.{Context, Intent}
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.text.TextUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.model.conversation.TabListMenuModel
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.utils.{MessageUtils, UtilForJava}
import com.waz.api
import com.waz.api.{AssetForUpload, IConversation, Message, Verification}
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.model.otr.Client
import com.waz.service.assets.AssetService
import com.waz.service.assets.AssetService.RawAssetInput.UriInput
import com.waz.service.conversation.{ConversationsService, ConversationsUiService, SelectedConversationService}
import com.waz.service.messages.MessagesService
import com.waz.service.{AccountManager, ZMessaging}
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.utils.wrappers.URI
import com.waz.utils.{Serialized, returning, _}
import com.waz.zclient._
import com.waz.zclient.broadcast.AppShortCutReceiver
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{Callback, MainActivityUtils, StringUtils}
import org.json.JSONArray
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class ConversationController(implicit injector: Injector, context: Context, ec: EventContext)
  extends Injectable with DerivedLogTag {

  private implicit val dispatcher = new SerialDispatchQueue(name = "ConversationController")

  private lazy val selectedConv = inject[Signal[SelectedConversationService]]
  private lazy val convsUi = inject[Signal[ConversationsUiService]]
  private lazy val conversations = inject[Signal[ConversationsService]]
  private lazy val convsStorage = inject[Signal[ConversationStorage]]
  private lazy val membersStorage = inject[Signal[MembersStorage]]
  private lazy val usersStorage = inject[Signal[UsersStorage]]
  private lazy val otrClientsStorage = inject[Signal[OtrClientsStorage]]
  private lazy val account = inject[Signal[Option[AccountManager]]]
  private lazy val callStart = inject[CallStartController]
  private lazy val convListController = inject[ConversationListController]
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val messagesService = inject[Signal[MessagesService]]
  private lazy val zms = inject[Signal[ZMessaging]]
  private val storage = zms.map(_.storage.db)

  private var lastConvId = Option.empty[ConvId]

  /**
    * (appId, PageData)
    */
  val currentChangeAppTabPram = Signal(("", ""))

  val sendMessageAndType = EventStream[Message.Type]()

  val onUserLongClicked: SourceStream[UserData] = EventStream()

  val currentConvIdOpt: Signal[Option[ConvId]] = selectedConv.flatMap(_.selectedConversationId)

  val currentConvId: Signal[ConvId] = currentConvIdOpt.collect { case Some(convId) => convId }

  val needJumpNewConvId = Signal("")

  val currentConvOpt: Signal[Option[ConversationData]] =
    currentConvIdOpt.flatMap(_.fold(Signal.const(Option.empty[ConversationData]))(conversationData)) // updates on every change of the conversation data, not only on switching

  val currentConv: Signal[ConversationData] =
    currentConvOpt.collect { case Some(conv) => conv }

  val defConvs = Signal[scala.collection.immutable.Seq[TabListMenuModel]]()

  val convChanged: SourceStream[ConversationChange] = EventStream[ConversationChange]()

  def getByRemoteId(remoteId: RConvId): Future[Option[ConversationData]] = {
    conversations.head.flatMap(_.getByRemoteId(remoteId))
  }

  def conversationData(convId: ConvId): Signal[Option[ConversationData]] =
    convsStorage.flatMap(_.optSignal(convId))

  def getConversation(convId: ConvId): Future[Option[ConversationData]] =
    convsStorage.head.flatMap(_.get(convId))

  val currentConvType: Signal[ConversationType] = currentConv.map(_.convType).disableAutowiring()
  /*val currentConvName: Signal[String] = currentConv.map(_.displayName).map {
    case Name.Empty => getString(R.string.default_deleted_username)
    case name => name
  } // the name of the current conversation can be edited (without switching)
  */

  val currentConvName: Signal[String] = for {
    isServerNotification <- currentConv.map(_.isServerNotification)
    displayName <- currentConv.map(_.displayName)
    name <- currentConv.map(_.name)
  } yield {
    if (isServerNotification && !TextUtils.isEmpty(name.getOrElse(displayName).str)) {
      name.getOrElse(displayName).str
    } else {
      displayName.str
    }
  }

  val currentConvIsVerified: Signal[Boolean] = currentConv.map(_.verified == Verification.VERIFIED)
  val currentConvIsGroup: Signal[Boolean] =
    for {
      convs <- conversations
      convId <- currentConvId
      isGroup <- convs.groupConversation(convId)
    } yield isGroup

  val currentConvIsTeamOnly: Signal[Boolean] = currentConv.map(_.isTeamOnly)

  lazy val currentConvMembers = for {
    membersStorage <- membersStorage
    selfUserId <- inject[Signal[UserId]]
    conv <- currentConvId
    members <- membersStorage.activeMembers(conv)
  } yield members.filter { member =>
    member != selfUserId
  }

  currentConvId { convId =>
    conversations(_.forceNameUpdate(convId))
    conversations.head.foreach(_.forceNameUpdate(convId))
    if (!lastConvId.contains(convId)) { // to only catch changes coming from SE (we assume it's an account switch)
      verbose(l"a conversation change bypassed selectConv: last = $lastConvId, current = $convId")
      convChanged ! ConversationChange(from = lastConvId, to = Option(convId), requester = ConversationChangeRequester.ACCOUNT_CHANGE)
      lastConvId = Option(convId)
    }
  }


  def addGroupData(convIds: Array[ConvId]) = (for {
    convs <- if (convIds.isEmpty) Future.successful((Map.empty[ConvId, ConversationData],Vector.empty[UserData])) else for {
      convStor <- convsStorage.head
      cs <- convStor.getByConvIds(convIds.toSet)
      us <- usersStorage.head.flatMap(_.listAll(cs.filter(_._2.convType == IConversation.Type.ONE_TO_ONE).keySet.map { cId => UserId(cId.str) }))
    } yield (cs,us)
  } yield {
    if (convs._1.isEmpty) {
    } else {
      convIds.foreach { cid =>
        convs._1.get(cid).foreach{
          conversationData =>
            val isGroup = MessageUtils.MessageContentUtils.isGroupForConversation(conversationData.convType)
            val convName = UtilForJava.getNameByConversationData(conversationData)
            val rAssetId = if (isGroup) {
              UtilForJava.getSmallRAssetId(conversationData,"")
            } else{
              convs._2.find(_.id.str.equals(cid.str)).fold {
                Option(conversationData.smallRAssetId).map(_.str).getOrElse("")
              } { u =>
                u.rAssetId.getOrElse("")
              }
            }
            val userNoticeData=UserNoticeData(Uid(),ServerIdConst.USER_NOTICE,conversationData.id.str,conversationData.id.str,convName,rAssetId,
              "",RemoteInstant(ZMessaging.clock.instant()),read = true,Constants.USER_NOTICE_TYPE_MANUAL)
            zms.currentValue.foreach(_.userNoticeStorage.insert(userNoticeData))
            verbose(l"addGroupData:${userNoticeData}")
        }
      }
    }
    convs
  }).flatMap(kv =>
    Future.successful(kv)
  )

  def updateGroupData()={
    var sortedTabListMenuModels: scala.collection.immutable.Seq[TabListMenuModel] = scala.collection.immutable.Seq.empty[TabListMenuModel]
    storage.head.flatMap(_.read(implicit db => UserNoticeData.UserNoticeDao.listByType(ServerIdConst.USER_NOTICE)
      .map(userNoticeData =>{
          verbose(l"++++++updateGroupData,userNoticeData:${userNoticeData}")
          val tabListMenuModel:TabListMenuModel=new TabListMenuModel(userNoticeData.name,userNoticeData.conv,userNoticeData.img,userNoticeData.subType,userNoticeData.uuid.str,userNoticeData.joinUrl,userNoticeData.read)
          sortedTabListMenuModels = sortedTabListMenuModels.:+(tabListMenuModel)
      })
    )).onComplete{
      case Success(_)=>
        defConvs ! sortedTabListMenuModels
      case _ =>
        defConvs ! scala.collection.immutable.Seq.empty[TabListMenuModel]
    }
  }

  def localGroupDataNum():Int={
    var num=0
    defConvs.currentValue.foreach(models => {
      num=models.size
    })
    verbose(l"localGroupDataNum:$num")
    num
  }

  def existsGroupData(convId:ConvId,rConvId:RConvId)={
    var flag=false
    if(defConvs.currentValue.nonEmpty){
      defConvs.currentValue.foreach(models => {
        flag = models.exists(model=> model.getConvId==convId.str || model.getConvId==rConvId.str)
      })
    }
    verbose(l"existsGroupData:$flag")
    flag
  }

  def updateGroupConv(conversationData: ConversationData): Unit ={
    verbose(l"updateGroupConv 11")
    zms.currentValue.foreach(storage =>storage.userNoticeStorage.findByConv(conversationData.remoteId.str).map{
      case Some(userNoticeData) => {
        storage.userNoticeStorage.update(userNoticeData.id,userNoticeData=>userNoticeData.copy(conv=conversationData.id.str))
        verbose(l"updateGroupConv 22")
      }
      case None =>
    })
  }

  def updateGroupReadStatus(conv:String): Unit ={
    verbose(l"updateGroupConv 33")
    zms.currentValue.foreach(storage =>storage.userNoticeStorage.findByConv(conv).map{
      case Some(userNoticeData) => {
        storage.userNoticeStorage.update(userNoticeData.id,userNoticeData=>userNoticeData.copy(read = true))
        verbose(l"updateGroupConv 44")
      }
      case None =>
    })
  }

  def removeGroupData(convId: ConvId): Unit ={
    zms.currentValue.foreach(storage => storage.userNoticeStorage.findByConv(convId.str).map{
      case Some(userNoticeData) => {
        storage.userNoticeStorage.remove(userNoticeData.uuid)
        verbose(l"removeGroupData")
      }
      case None =>
    })

  }

  def addToDeskTopLauncher(convId: ConvId): Unit = {
    for{
      Some(conversationData) <- getConversation(convId)
      userData <- if(conversationData.convType == ConversationType.OneToOne) usersStorage.head.flatMap(_.get(UserId(convId.str))) else Future.successful(None)
    }yield {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val defaultRes = MessageContentUtils.getGroupDefaultAvatar(convId)
        val shortcutManager = context.getSystemService(Context.SHORTCUT_SERVICE).asInstanceOf[ShortcutManager]
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
          val shortCutIntent = Intents.enterConversationIntent(convId.str)
          shortCutIntent.setAction(Intent.ACTION_VIEW)
          val shortcutBuilder= new ShortcutInfo.Builder(context, convId.str).setShortLabel(conversationData.displayName).setIntent(shortCutIntent)
          val pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, classOf[AppShortCutReceiver]), PendingIntent.FLAG_UPDATE_CURRENT)

          if(conversationData.convType == ConversationType.OneToOne){

            if(userData.nonEmpty){
              val rAssetId = userData.get.rAssetId match {
                case Some(a) =>
                  Option(a)
                case _ =>
                  userData.get.picture match {
                    case Some(p) =>
                      Option(p.str)
                    case _ =>
                      Option.empty[String]
                  }
              }
              val imageUrl: String = rAssetId.getOrElse("")

              if (StringUtils.isNotBlank(imageUrl)) {
                Glide.`with`(context).asBitmap().load(CircleConstant.appendAvatarUrl(imageUrl, context)).into(new SimpleTarget[Bitmap]() {
                  override def onResourceReady(resource: Bitmap, transition: Transition[_ >: Bitmap]): Unit = {
                    shortcutBuilder.setIcon(Icon.createWithBitmap(resource))
                    shortcutManager.requestPinShortcut(shortcutBuilder.build(), pendingIntent.getIntentSender)
                  }
                })
              }else{
                shortcutBuilder.setIcon(Icon.createWithResource(context, defaultRes))
                shortcutManager.requestPinShortcut(shortcutBuilder.build(), pendingIntent.getIntentSender)
              }

            }else{
              shortcutBuilder.setIcon(Icon.createWithResource(context, defaultRes))
              shortcutManager.requestPinShortcut(shortcutBuilder.build(), pendingIntent.getIntentSender)
            }
          }else{
            if (conversationData.smallRAssetId != null) {
              Glide.`with`(context).asBitmap().load(CircleConstant.appendAvatarUrl(conversationData.smallRAssetId.str, context)).into(new SimpleTarget[Bitmap]() {
                override def onResourceReady(resource: Bitmap, transition: Transition[_ >: Bitmap]): Unit = {
                  shortcutBuilder.setIcon(Icon.createWithBitmap(resource))
                  shortcutManager.requestPinShortcut(shortcutBuilder.build(), pendingIntent.getIntentSender)
                }
              })
            }else{
              shortcutBuilder.setIcon(Icon.createWithResource(context, defaultRes))
              shortcutManager.requestPinShortcut(shortcutBuilder.build(), pendingIntent.getIntentSender)
            }
          }

        }
      }
    }
  }

  def updateConvMsgEdit(id: ConvId, enabled_edit_msg: Boolean): Future[Option[ConversationData]] = {
    convsUi.head.flatMap(_.updateConvMsgEdit(id, enabled_edit_msg))
  }

  def updateConversationCus(id: ConvId)(updater: ConversationData => ConversationData): Future[Option[ConversationData]] = {
    convsUi.head.flatMap{convsui =>
      convsui.updateConversationCus(id)(updater)
    }
  }

  // this should be the only UI entry point to change conv in SE
  def selectConv(convId: Option[ConvId], requester: ConversationChangeRequester): Future[Unit] = convId match {
    case None => Future.successful({})
    case Some(id) =>
      val oldId = lastConvId
      lastConvId = convId
      for {
        selectedConv <- selectedConv.head
        convsUi <- convsUi.head
        conv <- getConversation(id)
        _ <- selectedConv.selectConversation(convId)
      } yield { // catches changes coming from UI
        verbose(l"changing conversation from $oldId to $convId, requester: $requester ")
        convChanged ! ConversationChange(from = oldId, to = convId, requester = requester)
      }
  }

  lazy val currentIsGroupCreateOrManager: Signal[Boolean] = for {
    selfUserId <- inject[Signal[UserId]]
    isManager <- currentUserIsGroupCreateOrManager(selfUserId)
  } yield {
    isManager
  }


  def currentUserIsGroupCreateOrManager(userId: UserId): Signal[Boolean] = {
    for {
      manager <- currentConv.map(_.manager)
      creator <- currentConv.map(_.creator)
    } yield {
      creator.str.equalsIgnoreCase(userId.str) || manager.exists(_.str.equalsIgnoreCase(userId.str))
    }
  }

  lazy val currentGroupIsManager: Signal[Boolean] = for {
    selfUserId <- inject[Signal[UserId]]
    isManager <- currentUserIsGroupManager(selfUserId)
  } yield {
    isManager
  }

  def currentUserIsGroupManager(userId: UserId): Signal[Boolean] = {
    for {
      manager <- currentConv.map(_.manager)
    } yield {
      manager.exists(_.str.equalsIgnoreCase(userId.str))
    }
  }

  def selectConv(id: ConvId, requester: ConversationChangeRequester): Future[Unit] =
    selectConv(Some(id), requester)

  def switchConversation(convId: ConvId, call: Boolean = false, delayMs: FiniteDuration = 750.millis) =
    CancellableFuture.delay(delayMs).map { _ =>
      selectConv(convId, ConversationChangeRequester.INTENT).foreach { _ =>
        if (call)
          for {
            Some(acc) <- account.map(_.map(_.userId)).head
            _ <- callStart.startCall(acc, convId)
          } yield ()
      }
    }(Threading.Ui).future

  def isCallingOngoing(): Future[Boolean] = {
    for {
      accountData <- (ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }).head
      z <- zms.head
      curCall <- z.calling.currentCall.head
      acceptingCall = curCall.exists(c => c.selfParticipant.userId == accountData.id)
      isOngoing <- if (!curCall.isEmpty && acceptingCall) {
        for {
          ongoingCalls <- z.calling.joinableCalls.head
          isJoiningCall = ongoingCalls.contains(curCall.get.convId)
        } yield {
          isJoiningCall
        }
      } else {
        Future.successful(false)
      }
    } yield {
      verbose(l"isCallingOngoing isOngoing:$isOngoing account?: ${accountData.id},acceptingCall:$acceptingCall,curCall:$curCall")
      isOngoing
    }
  }

  def groupConversation(id: ConvId): Signal[Boolean] =
    conversations.flatMap(_.groupConversation(id))

  def participantsIds(conv: ConvId): Future[Seq[UserId]] =
    membersStorage.head.flatMap(_.getActiveUsers(conv))

  def setEphemeralExpiration(expiration: Option[FiniteDuration], isGlobal: Boolean = false): Future[Unit] =
    for {
      id <- currentConvId.head
      _ <- convsUi.head.flatMap { it =>
        if (isGlobal) {
          it.setEphemeralGlobal(id, expiration)
        } else {
          it.setEphemeral(id, expiration)
        }
      }
    } yield ()

  def loadMembers(convId: ConvId): Future[Seq[UserData]] =
    for {
      userIds <- membersStorage.head.flatMap(_.getActiveUsers(convId)) // TODO: maybe switch to ConversationsMembersSignal
      users <- usersStorage.head.flatMap(_.listAll(userIds))
    } yield users

  def loadClients(userId: UserId): Future[Seq[Client]] =
    otrClientsStorage.head.flatMap(_.getClients(userId)) // TODO: move to SE maybe?

  def sendMessage(text: String, mentions: Seq[Mention] = Nil, quote: Option[MessageId] = None, activity: Activity): Future[Option[MessageData]] = {
    MainActivityUtils.playOutGoingMessageAudio(activity)
    val msg = convsUiwithCurrentConv({ (ui, id) =>
      quote.fold2(ui.sendTextMessage(id, text, mentions), ui.sendReplyMessage(_, text, mentions))
    })
    sendMessageAndType ! Message.Type.TEXT
    msg
  }

  /**
    *
    * @param json
    * @param mentions
    * @return
    */
  def sendTextJsonMessage(json: String, mentions: Seq[Mention] = Nil, activity: Activity): Future[Option[MessageData]] = {
    MainActivityUtils.playOutGoingMessageAudio(activity)
    val msg = convsUiwithCurrentConv({ (ui, id) =>
      ui.sendTextJsonMessage(id, json, mentions)
    })
    sendMessageAndType ! Message.Type.TEXTJSON
    msg
  }

  def sendTextJsonMessageForRecipients(json: String, mentions: Seq[Mention] = Nil, activity: Activity, uids: JSONArray, unblock: Boolean = false): Future[Option[MessageData]] = {
    MainActivityUtils.playOutGoingMessageAudio(activity)
    val msg = convsUiwithCurrentConv({ (ui, id) =>
      ui.sendTextJsonMessageForRecipients(id, json, mentions, uids = uids, unblock = unblock)
    })
    sendMessageAndType ! Message.Type.TEXTJSON
    msg
  }

  def sendMessage(input: AssetService.RawAssetInput, activity: Activity): Future[Option[MessageData]] = {
    MainActivityUtils.playOutGoingMessageAudio(activity)
    val msg = convsUiwithCurrentConv((ui, id) => ui.sendAssetMessage(id, input))
    sendMessageAndType ! Message.Type.ASSET
    msg
  }

  def sendMessage(uri: URI, activity: Activity): Future[Option[MessageData]] = {
    val msg = convsUiwithCurrentConv((ui, id) =>
      accentColorController.accentColor.head.flatMap(color =>
        ui.sendAssetMessage(
          id,
          UriInput(uri),
          (s: Long) => showWifiWarningDialog(s, color)(dispatcher, activity)
        )
      )
    )
    sendMessageAndType ! Message.Type.ASSET
    msg
  }

  def sendMessage(audioAsset: AssetForUpload, activity: Activity): Future[Option[MessageData]] = {
    verbose(l"audioasset,sendMessage :$audioAsset")
    audioAsset match {
      case asset: com.waz.api.impl.AudioAssetForUpload =>
        accentColorController.accentColor.head.flatMap { color =>
          val msg = convsUiwithCurrentConv((ui, id) =>
            ui.sendMessage(
              id,
              asset,
              (s: Long) => showWifiWarningDialog(s, color)(dispatcher, activity)
            )
          )
          sendMessageAndType ! Message.Type.ASSET
          msg
        }
      case _ => Future.successful(None)
    }
  }

  def sendMessage(location: api.MessageContent.Location, activity: Activity): Future[Option[MessageData]] = {
    MainActivityUtils.playOutGoingMessageAudio(activity)
    val msg = convsUiwithCurrentConv((ui, id) => ui.sendLocationMessage(id, location))
    sendMessageAndType ! Message.Type.LOCATION
    msg
  }

  def addLocalSentMessage(msg: MessageData, time: Option[RemoteInstant] = None) = {
    messagesService.head.flatMap(_.addLocalSentMessage(msg, time))
  }

  def updateMessageCus(convId: ConvId, msgId: MessageId)(updater: MessageData => MessageData): Future[Option[MessageData]] ={
    convsUi.head.flatMap{convsui =>
      convsui.updateMessageCus(msgId)(updater)
    }
  }

  def updateMessageCloseState(convId: ConvId, msgId: MessageId) = {
    for {
      mcui <- convsUi
    } yield {
      mcui.updateMessageState(convId, msgId, MessageActions.Action_MsgClose)
    }
  }

  def updateMessageCloseStateF(convId: ConvId, msgId: MessageId) ={
    convsUi.head.flatMap(_.updateMessageState(convId, msgId, MessageActions.Action_MsgClose))
  }

  def checkLocalConnectRequestMsg(convs: Set[ConversationData]) = {
    conversations.currentValue.foreach(_.checkCreateFirstMessage(convs))
  }

  private def convsUiwithCurrentConv[A](f: (ConversationsUiService, ConvId) => Future[A]): Future[A] =
    for {
      cUi <- convsUi.head
      convId <- currentConvId.head
      res <- f(cUi, convId)
    } yield res

  def setCurrentConvName(name: String): Future[Unit] =
    for {
      service <- convsUi.head
      id <- currentConvId.head
      currentName <- currentConv.map(_.displayName).head
    } yield {
      val newName = Name(name)
      if (newName != currentName) service.setConversationName(id, newName)
    }

  def setCurrentConvReadReceipts(readReceiptsEnabled: Boolean): Future[Unit] =
    for {
      service <- convsUi.head
      id <- currentConvId.head
      currentReadReceipts <- currentConv.map(_.readReceiptsAllowed).head
    } yield
      if (currentReadReceipts != readReceiptsEnabled)
        service.setReceiptMode(id, if (readReceiptsEnabled) 1 else 0)

  def addMembers(id: ConvId, users: Set[UserId]): Future[Unit] =
    convsUi.head.flatMap(_.addConversationMembers(id, users)).map(_ => {})

  def addMembersForConv(id: ConvId, users: Set[UserId], needConfirm: Boolean, inviteStr: String, selfName: Option[String]) =
    convsUi.head.flatMap(_.addMembersForConversation(id, users, needConfirm, inviteStr, selfName))

  def removeMember(user: UserId): Future[Unit] =
    for {
      id <- currentConvId.head
      _ <- convsUi.head.flatMap(_.removeConversationMember(id, user))
    } yield {}

  def leave(convId: ConvId): CancellableFuture[Unit] =
    returning(Serialized("Conversations", convId)(CancellableFuture.lift(convsUi.head.flatMap(_.leaveConversation(convId))))) { _ =>
      currentConvId.head.map { id => if (id == convId) setCurrentConversationToNext(ConversationChangeRequester.LEAVE_CONVERSATION) }
    }

  def setCurrentConversationToNext(requester: ConversationChangeRequester): Future[Unit] = {
    def nextConversation(convId: ConvId): Future[Option[ConvId]] =
      convListController.regularConversationListData.head.map {
        regular => regular.lift(regular.indexWhere(_.id == convId) + 1).map(_.id)
      }(Threading.Background)

    for {
      currentConvId <- currentConvId.head
      nextConvId <- nextConversation(currentConvId)
      _ <- selectConv(nextConvId, requester)
    } yield ()
  }

  def setMuted(id: ConvId, muted: MuteSet): Future[Unit] =
    convsUi.head.flatMap(_.setConversationMuted(id, muted)).map(_ => {})

  def delete(id: ConvId, alsoLeave: Boolean): CancellableFuture[Option[ConversationData]] = {
    def clear(id: ConvId) = Serialized("Conversations", id)(CancellableFuture.lift(convsUi.head.flatMap(_.clearConversation(id))))

    if (alsoLeave) leave(id).flatMap(_ => clear(id)) else clear(id)
  }

  def createGuestRoom(): Future[ConversationData] = createGroupConversation(Some(context.getString(R.string.guest_room_name)), Set(), false, "")

  def createGroupConversation(name: Option[Name], users: Set[UserId], readReceipts: Boolean, apps: String): Future[ConversationData] =
    convsUi.head.flatMap(_.createGroupConversation(name, users, teamOnly = false, if (readReceipts) 1 else 0, apps)).map(_._1)

  def withCurrentConvName(callback: Callback[String]): Unit = currentConvName.head.foreach(callback.callback)(Threading.Ui)

  def getCurrentConvId: ConvId = currentConvId.currentValue.orNull

  def withConvLoaded(convId: ConvId, callback: Callback[ConversationData]): Unit = getConversation(convId).foreach {
    case Some(data) => callback.callback(data)
    case None =>
  }(Threading.Ui)

  private var convChangedCallbackSet = Set.empty[Callback[ConversationChange]]

  def addConvChangedCallback(callback: Callback[ConversationChange]): Unit = convChangedCallbackSet += callback

  def removeConvChangedCallback(callback: Callback[ConversationChange]): Unit = convChangedCallbackSet -= callback

  convChanged.onUi { ev => convChangedCallbackSet.foreach(callback => callback.callback(ev)) }

  object messages {

    val ActivityTimeout = 3.seconds

    /**
      * Currently focused message.
      * There is only one focused message, switched by tapping.
      */
    val focused = Signal(Option.empty[MessageId])

    /**
      * Tracks last focused message together with last action time.
      * It's not cleared when message is unfocused, and toggleFocus takes timeout into account.
      * This is used to decide if timestamp view should be shown in footer when message has likes.
      */
    val lastActive = Signal((MessageId.Empty, Instant.EPOCH)) // message showing status info

    currentConv.onChanged { _ => clear() }

    def clear() = {
      focused ! None
      lastActive ! (MessageId.Empty, Instant.EPOCH)
    }

    def isFocused(id: MessageId): Boolean = focused.currentValue.flatten.contains(id)

    /**
      * Switches current msg focus state to/from given msg.
      */
    def toggleFocused(id: MessageId) = {
      verbose(l"toggleFocused($id)")
      focused mutate {
        case Some(`id`) => None
        case _ => Some(id)
      }
      lastActive.mutate {
        case (`id`, t) if !ActivityTimeout.elapsedSince(t) => (id, Instant.now - ActivityTimeout)
        case _ => (id, Instant.now)
      }
    }
  }

  val currentConvGroupManagerNoSelf_Creator: Signal[Seq[UserId]] = for {
    creator <- currentConv.map(_.creator)
    selfUserId <- inject[Signal[UserId]]
    isManager <- currentUserIsGroupManager(selfUserId)
    manager <- currentConv.map(_.manager)
  } yield {
    if (isManager) {
      manager.filterNot { id =>
        id == selfUserId || id == creator
      }
    } else {
      manager.filterNot { id =>
        id == creator
      }
    }
  }

  def setPlaceTop(id: ConvId, place_top: Boolean) = {
    convsUi.head.flatMap(_.setConversationPlaceTop(id, place_top)).map(_ => {})
  }

  def changeGroupNickname(nickname: String) =
    for {
      convId <- currentConvId.head
      convsUi <- convsUi.head
      _ <- convsUi.changeGroupNickname(convId, nickname)
    } yield ()
}

object ConversationController extends DerivedLogTag {
  val ARCHIVE_DELAY = 500.millis
  val MaxParticipants: Int = 300

  case class ConversationChange(from: Option[ConvId], to: Option[ConvId], requester: ConversationChangeRequester) {
    def toConvId: ConvId = to.orNull // TODO: remove when not used anymore
    lazy val noChange: Boolean = from == to
  }

  def getOtherParticipantForOneToOneConv(conv: ConversationData): UserId = {
    if (conv != ConversationData.Empty &&
      conv.convType != IConversation.Type.ONE_TO_ONE &&
      conv.convType != IConversation.Type.WAIT_FOR_CONNECTION &&
      conv.convType != IConversation.Type.INCOMING_CONNECTION)
      error(l"unexpected call, most likely UI error", new UnsupportedOperationException(s"Can't get other participant for: ${conv.convType} conversation"))
    UserId(conv.id.str) // one-to-one conversation has the same id as the other user, so we can access it directly
  }

  lazy val PredefinedExpirations =
    Seq(
      None,
      Some(10.seconds),
      Some(5.minutes),
      Some(1.hour),
      Some(1.day),
      Some(7.days),
      Some(28.days)
    )

  import com.waz.model.EphemeralDuration._

  def getEphemeralDisplayString(exp: Option[FiniteDuration])(implicit context: Context): String = {
    exp.map(EphemeralDuration(_)) match {
      case None => getString(R.string.ephemeral_message__timeout__off)
      case Some((l, Second)) => getQuantityString(R.plurals.unit_seconds, l.toInt, l.toString)
      case Some((l, Minute)) => getQuantityString(R.plurals.unit_minutes, l.toInt, l.toString)
      case Some((l, Hour)) => getQuantityString(R.plurals.unit_hours, l.toInt, l.toString)
      case Some((l, Day)) => getQuantityString(R.plurals.unit_days, l.toInt, l.toString)
      case Some((l, Week)) => getQuantityString(R.plurals.unit_weeks, l.toInt, l.toString)
      case Some((l, Year)) => getQuantityString(R.plurals.unit_years, l.toInt, l.toString)
    }
  }

  lazy val MuteSets = Seq(MuteSet.AllAllowed, MuteSet.OnlyMentionsAllowed, MuteSet.AllMuted)

  def muteSetDisplayStringId(muteSet: MuteSet): Int = muteSet match {
    case MuteSet.AllMuted => R.string.conversation__action__notifications_nothing
    case MuteSet.OnlyMentionsAllowed => R.string.conversation__action__notifications_mentions_and_replies
    case _ => R.string.conversation__action__notifications_everything
  }
}
