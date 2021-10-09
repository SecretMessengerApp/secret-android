/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.conversationlist.views

import android.animation.ObjectAnimator
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.animation.Animation.AnimationListener
import android.view.animation._
import android.view.{View, ViewGroup}
import android.widget.LinearLayout.LayoutParams
import android.widget.{FrameLayout, ImageView, RelativeLayout, TextView}
import com.facebook.rebound.ui.Util
import com.jsy.common.model.GroupNoticeModel
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.res.utils.ViewUtils
import com.waz.api.{IConversation, Message}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.call.CallInfo
import com.waz.service.call.CallInfo.CallState.SelfCalling
import com.waz.threading.Threading
import com.waz.utils._
import com.waz.utils.events.Signal
import com.waz.zclient.calling.CallingActivity
import com.waz.zclient.calling.controllers.CallStartController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.conversationlist.views.ConversationBadge.OngoingCall
import com.waz.zclient.conversationlist.views.ConversationListRow2.{isGroupForConversation, _}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.main.conversationlist.views.ConversationCallback
import com.waz.zclient.pages.main.conversationlist.views.listview.SwipeListView
import com.waz.zclient.pages.main.conversationlist.views.row.MenuIndicatorView
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.utils.TextViewUtils
import com.waz.zclient.ui.views.properties.MoveToAnimateable
import com.waz.zclient.utils.ContextUtils.{getString, _}
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{DateTimeUtils, Instant}

import scala.collection.Set
import scala.concurrent.duration.DurationInt

trait ConversationListRow2 extends FrameLayout

class NormalConversationListRow2(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ConversationListRow2
    with ViewHelper
    with SwipeListView.SwipeListRow
    with MoveToAnimateable
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  implicit lazy val executionContext = Threading.Background
  implicit lazy val uiStorage = inject[UiStorage]

  inflate(R.layout.conv_list_item)

  lazy val controller = inject[ConversationListController]

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val callStartController = inject[CallStartController]

  lazy val selfId = zms.map(_.selfUserId)

  private lazy val conversationId = Signal[Option[ConvId]]()

  lazy val container = ViewUtils.getView(this, R.id.conversation_row_container).asInstanceOf[RelativeLayout]
  lazy val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TextView]
  lazy val subtitle = ViewUtils.getView(this, R.id.conversation_subtitle).asInstanceOf[TextView]
  lazy val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  lazy val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  lazy val separator = ViewUtils.getView(this, R.id.conversation_separator).asInstanceOf[View]
  lazy val menuIndicatorView = ViewUtils.getView(this, R.id.conversation_menu_indicator).asInstanceOf[MenuIndicatorView]
  lazy val subtitleGlyph = ViewUtils.getView(this, R.id.conversation_subtitle_glyph).asInstanceOf[TextView]
  lazy val stickyTopView = ViewUtils.getView(this, R.id.sticky_top_imageView).asInstanceOf[ImageView]
  lazy val msgTime = ViewUtils.getView(this, R.id.conversation_time).asInstanceOf[TextView]
  lazy val rlHeadRing = ViewUtils.getView(this, R.id.rlHeadRing).asInstanceOf[View]
  lazy val ivScaleRing = ViewUtils.getView(this, R.id.ivScaleRing).asInstanceOf[ImageView]

  var isCanSwipeable: Boolean = true

  var conversationData = Option.empty[ConversationData]

  def getDbConversationData(convId: ConvId): Signal[ConversationData] = {
    if (isSameConversation(convId)) {
      ConversationSignal(convId)
    } else {
      Signal.empty[ConversationData]
    }
  }

  lazy val conversation = for {
    Some(convId) <- conversationId
    conv <- getDbConversationData(convId).throttle(500.millis)
  } yield conv

  lazy val members = conversationId.collect { case Some(convId) => convId } flatMap controller.membersLimit

  lazy val conversationName = conversation map { conv =>
    if (conv.displayName.isEmpty) {
      // This hack was in the UiModule Conversation implementation
      // XXX: this is a hack for some random errors, sometimes conv has empty name which is never updated
      zms.head.foreach {
        _.conversations.forceNameUpdate(conv.id)
      }
      Name(getString(R.string.default_deleted_username))
    } else{
      if (conv.isServerNotification && !TextUtils.isEmpty(conv.name.getOrElse(conv.displayName).str)) {
        conv.name.getOrElse(conv.displayName)
      } else {
        conv.displayName
      }
    }
  }

  lazy val userTyping = for {
    z <- zms
    convId <- conversation.map(_.id)
    typing <- Signal.wrap(z.typing.onTypingChanged.filter(_._1 == convId).map(_._2.headOption)).orElse(Signal.const(None))
    typingUser <- userData(typing.map(_.id))
  } yield typingUser

  lazy val badgeInfo = for {
    z <- zms
    conv <- conversation
    typing <- userTyping.map(_.nonEmpty)
    availableCalls <- z.calling.joinableCalls
    call <- z.calling.currentCall
    callDuration <- call.filter(_.convId == conv.id).fold(Signal.const(""))(_.durationFormatted)
  } yield (conv.id, badgeStatusForConversation(conv, conv.unreadCount, typing, availableCalls, callDuration), conv.place_top)

  lazy val subtitleText = for {
    conv <- conversation
    r <- subtitleTextForNormal
  } yield {
    r
  }

  lazy val subtitleTextForNormal = for {
    z <- zms
    resultAliasStorage <- zms.map(_.aliasStorage)
    conv <- conversation
    isEmptyUser = (null == conv.lastMsgUserId || TextUtils.isEmpty(conv.lastMsgUserId.str) || (conv.lastMsgType == Message.Type.TEXT && TextUtils.isEmpty(conv.contentString)))
    _ = verbose(l"subtitleTextForNormal 111 convname: ${conv.displayName} ==, selfId: ${selfId.currentValue.getOrElse("empty")}, conv.lastMsgUserId:${conv.lastMsgUserId},isEmptyUser:$isEmptyUser, conv.lastMsgMembers: ${if (conv.lastMsgMembers.nonEmpty) conv.lastMsgMembers.size else "empty"}")
//    lastMessageUser = userData(Option.apply(conv.lastMsgUserId))
    lastUnreadMessageUser <- if(isEmptyUser || !isGroupForConversation(conv.convType)) {
      Signal.const(Option.empty[UserData])
    } else {
      userData(Option.apply(conv.lastMsgUserId))
    }
    lastUnreadMessageMembers <- if (conv.lastMsgMembers.isEmpty) Signal.const(Vector[UserData]()) else UserSetSignal(conv.lastMsgMembers).map(_.toVector)
    typingUser <- userTyping
    typingAliasData <- typingUser.fold(Signal.const(Option.empty[AliasData])){ tempUser =>
      resultAliasStorage.optSignal(conv.id,tempUser.id)
    }
    ms <- if(isGroupForConversation(conv.convType)) Signal.const(Seq.empty[UserId]) else members
    otherUser <- if(isGroupForConversation(conv.convType)) Signal.const(Option.empty[UserData]) else userData(ms.headOption)
    resultAliasData <- lastUnreadMessageUser.fold(Signal.const(Option.empty[AliasData])){ tempUser =>
      resultAliasStorage.optSignal(conv.id,tempUser.id)
    }
    _ = verbose(l"subtitleTextForNormal 222 conv.id: ${conv.id}, otherUser.isEmpty: ${otherUser.isEmpty}, ms.size: ${if (ms.nonEmpty) ms.size else "empty"}, lastUnreadMessageMembers.size: ${if (lastUnreadMessageMembers.nonEmpty) lastUnreadMessageMembers.size else "empty"}, lastUnreadMessageUser: ${lastUnreadMessageUser.map(_.displayName)}")
  } yield (conv, false, subtitleStringForLastMessages(conv, otherUser, ms.toSet, lastUnreadMessageUser, lastUnreadMessageMembers, typingUser, z.selfUserId, resultAliasData,typingAliasData = typingAliasData /*,  isGroupConv, userName*/))

  private def userData(id: Option[UserId]) = id.fold2(Signal.const(Option.empty[UserData]), uid => UserSignal(uid).map(Option(_)))

  lazy val avatarInfo = for {
    conv <- conversation
    assets <- conversation.map(_.assets)
    userData <- if (!shouldShowChatHeadView(conv)) {
      Signal.const(Option.empty[UserData])
    } else {
      for {
        z <- zms
        memberIds <- members
        memberSeq <- if (memberIds.isEmpty) userData(Option(conv.creator)) else userData(memberIds.find(_ != z.selfUserId))
      } yield {
        memberSeq
      }
    }
  } yield {
    (conv, assets, userData)
  }

  lazy val conversationTitle = for {
    name <- conversationName
    Some(convId) <- conversationId
  } yield {
    (name, convId)
  }

  conversationTitle.onUi {
    case (name, convId) if isSameConversation(convId) =>
      title.setText(name)
    case _ =>
      verbose(l"Outdated conversation convNameInfo")
  }

  subtitleText.onUi {
    case (conv, isTextJson, (isGlyph, time, text)) if isSameConversation(conv.id) =>
      setSubtitle(conv, isTextJson, isGlyph, time, text)
    case _ =>
      verbose(l"Outdated conversation subtitle")
  }

  def setSubtitle(conv: ConversationData, isTextJson: Boolean, isGlyph: Boolean, time: Instant, text: String): Unit = {
    if (text.nonEmpty) {
      if (isGlyph) {
        subtitleGlyph.setText(text)
        TextViewUtils.boldText(subtitleGlyph)
        subtitle.setVisibility(View.GONE)
        subtitleGlyph.setVisibility(View.VISIBLE)
      } else {
        subtitle.setText(text)
        TextViewUtils.boldText(subtitle)
        subtitle.setVisibility(View.VISIBLE)
        subtitleGlyph.setVisibility(View.GONE)
      }

      if (time == Instant.EPOCH || conv.lastEventTime.instant == Instant.EPOCH) {
        msgTime.setText("")
      } else {
        val showTime = ZTimeFormatter.getSingleMessageTime(context, DateTimeUtils.toDate(time))
        if(showTime.contains(DEFAULT_TIME)){
          msgTime.setText("")
        }else{
          msgTime.setText(showTime)
          TextViewUtils.boldText(msgTime)
        }
      }
    } else {
      if (conv.isServerNotification) {
        subtitle.setText("")
      } else if (conv.convType == IConversation.Type.ONE_TO_ONE) {
        subtitle.setText("@" + title.getText)
        TextViewUtils.boldText(subtitle)
        subtitle.setVisibility(View.VISIBLE)
      } else {
        subtitle.setText("")
      }
      msgTime.setText("")
      subtitleGlyph.setVisibility(View.GONE)
    }
  }

  badgeInfo.onUi {
    case (convId, status, stickyTop) if isSameConversation(convId) =>
      badge.setStatus(status)
      Option(stickyTopView.getLayoutParams).filter(_.isInstanceOf[RelativeLayout.LayoutParams]).map(_.asInstanceOf[RelativeLayout.LayoutParams])
        .foreach { layoutParams =>
          if(status == ConversationBadge.Empty) {
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams.setMarginEnd(Util.dpToPx(23, getResources))
          } else {
            layoutParams.removeRule(RelativeLayout.ALIGN_PARENT_END)
            layoutParams.setMarginEnd(Util.dpToPx(15, getResources))
          }
          stickyTopView.setLayoutParams(layoutParams)
          stickyTopView.setVisibility(if(stickyTop) View.VISIBLE else View.GONE)
        }
    case _ =>
      verbose(l"Outdated badge status")
  }

  avatarInfo.onUi {
    case (conv, _, members) if members.nonEmpty && isSameConversation(conv.id) =>
      verbose(l"Hasdated avatar info ${conv.id} members: $members")
      members.foreach(userData => showAvara(conv, userData, true))
    case (conv, _, _) if isSameConversation(conv.id) =>
      verbose(l"Hasdated avatar info ${conv.id} members:null")
      showAvara(conv, null, true)
    case _ =>
      verbose(l"Outdated avatar info")
  }

  def showAvara(conversationData: ConversationData, userData: UserData, right: Boolean): Unit = {
    verbose(l"showAvara avatar info ${conversationData.id} right: $right, userData: ${null == userData}, smallRAssetId: ${null == conversationData.smallRAssetId}")
    //val convType = conversationData.convType
    //avatar.setConversationType(if (convType != ConversationType.WaitForConnection) ConversationType.OneToOne else convType)
    avatar.setConversationType(ConversationType.OneToOne)
    if (shouldShowChatHeadView(conversationData)) {
      if (!right || null == userData) {
        avatar.avatarSingle.setImageResource(R.drawable.circle_noname)
      }
      if (null != userData) {
        avatar.avatarSingle.setUserData(userData, R.drawable.circle_noname)
      }
    } else {
      val defaultRes = MessageContentUtils.getGroupDefaultAvatar(conversationData.id)
      if (conversationData.smallRAssetId != null) {
        avatar.avatarSingle.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(conversationData.smallRAssetId.str, getContext), defaultRes)
      } else {
        avatar.avatarSingle.setImageResource(defaultRes)
      }
    }
  }

  badge.onClickEvent {
    case ConversationBadge.IncomingCall =>
      (zms.map(_.selfUserId).currentValue, conversationData.map(_.id)) match {
        case (Some(acc), Some(cId)) => callStartController.startCall(acc, cId, withVideo = false, forceOption = true)
        case _ => //
      }
    case OngoingCall(_) =>
      CallingActivity.startIfCallIsActive(getContext)
    case _ =>
  }

  private var conversationCallback: ConversationCallback = null
  private var maxAlpha: Float = .0f
  private var openState: Boolean = false
  private val menuOpenOffset: Int = getDimenPx(R.dimen.list__menu_indicator__max_swipe_offset)
  private var moveTo: Float = .0f
  private var maxOffset: Float = .0f
  private var moveToAnimator: ObjectAnimator = null
  private var isScrolling = false
  private var isSyncData = false

  def isSameConversation(convId: ConvId): Boolean = {
    conversationData.nonEmpty && this.conversationData.forall(_.id == convId)
  }

  def setConversation(conversationData: ConversationData, isScrolling: Boolean = false): Unit = {
    if (!isSameConversation(conversationData.id)) {
      this.conversationData = Some(conversationData)
      title.setText(if (conversationData.displayName.str.nonEmpty) conversationData.displayName.str else getString(R.string.default_deleted_username))
      verbose(l"setConversation=conversationData.ConvId: ${conversationData.id},displayName: ${conversationData.displayName}, conversationData.assets: ${conversationData.assets}")
      badge.setStatus(ConversationBadge.Empty)
      subtitle.setText("")
      subtitleGlyph.setText("")
      msgTime.setText("")
      stickyTopView.setVisibility(View.GONE)
      setMenuIndicatorScroll(conversationData.isServerNotification)
      closeImmediate()

      ivScaleRing.clearAnimation()
      rlHeadRing.setVisibility(View.GONE)
      avatar.avatarSingle.clearAnimation()
      avatar.clearImages()
      showAvara(conversationData, null, false)
      conversationId.publish(Some(conversationData.id), Threading.Ui)
    } else {
    }
  }

  def setMenuIndicatorScroll(isServerNotification: Boolean): Unit = {
    isCanSwipeable = !isServerNotification
  }

  menuIndicatorView.setClickable(false)
  menuIndicatorView.setMaxOffset(menuOpenOffset)
  menuIndicatorView.setOnClickListener(new View.OnClickListener() {
    def onClick(v: View): Unit = {
      close()
      conversationCallback.onConversationListRowSwiped(null, NormalConversationListRow2.this)
    }
  })

  def setConversationCallback(conversationCallback: ConversationCallback): Unit = {
    this.conversationCallback = conversationCallback
  }

  override def open(): Unit =
    if (!openState) {
      animateMenu(menuOpenOffset)
      menuIndicatorView.setClickable(true)
      openState = true
    }

  def close(): Unit = {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
    animateMenu(0)
  }

  private def closeImmediate(): Unit = {
    if (openState) openState = false
    menuIndicatorView.setClickable(false)
    setMoveTo(0)
  }

  override def setMaxOffset(maxOffset: Float) = this.maxOffset = maxOffset

  override def setOffset(offset: Float) = {
    val openOffset: Int = if (openState) menuOpenOffset
    else 0
    var moveTo: Float = openOffset + offset

    if (moveTo < 0) moveTo = 0

    if (moveTo > maxOffset) {
      val overshoot: Float = moveTo - maxOffset
      moveTo = maxOffset + overshoot / 2
    }

    setMoveTo(moveTo)
  }

  override def isSwipeable = isCanSwipeable

  override def isOpen = openState

  override def swipeAway() = {
    close()
    conversationCallback.onConversationListRowSwiped(null, this)
  }

  override def dimOnListRowMenuSwiped(alpha: Float) = {
    val cappedAlpha = Math.max(alpha, maxAlpha)
    menuIndicatorView.setAlpha(cappedAlpha)
    setAlpha(cappedAlpha)
  }

  override def setPagerOffset(pagerOffset: Float): Unit = {

    val alpha = Math.max(Math.pow(1 - pagerOffset, 4).toFloat, maxAlpha)
    setAlpha(alpha)
  }

  override def getMoveTo = moveTo

  override def setMoveTo(value: Float) = {
    moveTo = value
    container.setTranslationX(moveTo)
    menuIndicatorView.setClipX(moveTo.toInt)
  }

  private def animateMenu(moveTo: Int): Unit = {
    val moveFrom: Float = getMoveTo
    moveToAnimator = ObjectAnimator.ofFloat(this, MoveToAnimateable.MOVE_TO, moveFrom, moveTo)
    moveToAnimator.setDuration(getResources.getInteger(R.integer.framework_animation_duration_medium))
    moveToAnimator.setInterpolator(new Expo.EaseOut)
    moveToAnimator.start()
  }

  def setMaxAlpha(maxAlpha: Float): Unit = {
    this.maxAlpha = maxAlpha
  }

  def formatSubtitle(content: String, user: String, group: Boolean, isEphemeral: Boolean = false, replyPrefix: Boolean = false, quotePrefix: Boolean = false)(implicit context: Context): String = {
    val groupSubtitle = if (quotePrefix) R.string.conversation_list__group_with_quote else R.string.conversation_list__group_without_quote
    val singleSubtitle = if (quotePrefix) R.string.conversation_list__single_with_quote else R.string.conversation_list__single_without_quote
    if (group && !isEphemeral) {
      getString(groupSubtitle, user, content)
    } else {
      getString(singleSubtitle, content)
    }
  }



  def subtitleStringForLastMessage(conv: ConversationData,
                                   user: Option[UserData],
                                   members: Vector[UserData],
                                   isGroup: Boolean,
                                   selfId: UserId,
                                   isQuote: Boolean,
                                   otherMember: Option[UserData],
                                   msgType: String = MessageContentUtils.INVALID_TYPE,
                                   aliasData: Option[AliasData] = None
                                  )(implicit context: Context): String = {

    lazy val senderName = user.fold{
      getString(R.string.conversation_list__someone)
    }{tempUser =>
      if (tempUser.id.equals(selfId)) {
        getString(R.string.conversation_thousands_group_me)
      }else {
        aliasData.filter(_.getAliasName.nonEmpty).filter(_ => conv.convType != ConversationType.ThousandsGroup).fold(tempUser.getShowName)(_.getAliasName)
      }
    }

    //    lazy val senderName = user.map(_.getDisplayName).getOrElse(Name(getString(R.string.conversation_list__someone)))
    lazy val memberName = members.headOption.map(_.getDisplayName).getOrElse(Name(getString(R.string.conversation_list__someone)))
    lazy val otherMemberName = otherMember.map(_.getDisplayName).getOrElse(Name(getString(R.string.conversation_list__someone)))
    if (conv.isEphemeral) {
      if (conv.hasMentionOf(selfId)) {
        if (isGroup) formatSubtitle(getString(R.string.conversation_list__group_eph_and_mention), senderName, isGroup, isEphemeral = true)
        else formatSubtitle(getString(R.string.conversation_list__single_eph_and_mention), senderName, isGroup, isEphemeral = true)
      } else if (isQuote) {
        if (isGroup) formatSubtitle(getString(R.string.conversation_list__group_eph_and_quote), senderName, isGroup, isEphemeral = true)
        else formatSubtitle(getString(R.string.conversation_list__single_eph_and_quote), senderName, isGroup, isEphemeral = true)
      } else
        formatSubtitle(getString(R.string.conversation_list__ephemeral), senderName, isGroup, isEphemeral = true)
    } else if (conv.isForbid) {
      if (isGroup) "@" + senderName else "@" + otherMemberName
    } else {
      conv.lastMsgType match {
        case Message.Type.TEXT | Message.Type.TEXT_EMOJI_ONLY | Message.Type.RICH_MEDIA =>
          val contentStr = conv.contentString
          if (TextUtils.isEmpty(contentStr)) {
            if (isGroup) {
              s"@${getString(R.string.conversation_thousands_group_me)}"
            } else {
              otherMember.map(it => it.handle.map(_.string).getOrElse(it.getDisplayName))
                .map(subtitle => s"@$subtitle").getOrElse("")
            }
          } else {
            formatSubtitle(conv.contentString, senderName, isGroup, quotePrefix = isQuote)
          }
        case Message.Type.TEXTJSON =>
          val (color, content) = getTextJsonMessageSubTitle(conv.contentString, msgType, selfId)
          formatSubtitle(content, senderName, isGroup)
        case Message.Type.ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__image), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.ANY_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__file), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.VIDEO_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__video), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.AUDIO_ASSET =>
          formatSubtitle(getString(R.string.conversation_list__shared__audio), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.LOCATION =>
          formatSubtitle(getString(R.string.conversation_list__shared__location), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.MISSED_CALL =>
          formatSubtitle(getString(R.string.conversation_list__missed_call), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.KNOCK =>
          formatSubtitle(getString(R.string.conversation_list__pinged), senderName, isGroup, quotePrefix = isQuote)
        case Message.Type.CONNECT_ACCEPTED | Message.Type.MEMBER_JOIN if !isGroup =>
          members.headOption.flatMap(_.handle).map(_.string).fold("")(StringUtils.formatHandle)
        case Message.Type.MEMBER_JOIN if members.exists(_.id == selfId) =>
          getString(R.string.conversation_list__added_you, senderName)
        case Message.Type.MEMBER_JOIN if members.length > 1 =>
          //getString(R.string.conversation_list__added, memberName)
          //if(members.nonEmpty) {
          //  val name = members.headOption.get.getDisplayName
          //  getString(R.string.conversation_list__added, name)
          //} else {
          //  getString(R.string.conversation_list__added, memberName)
          //}
          getMemberJoinStr(conv, selfId, members, memberName).getOrElse(if(isGroup) "@" + senderName else "@" + otherMemberName)
        case Message.Type.MEMBER_JOIN =>
          //getString(R.string.conversation_list__added, memberName)
          //if(members.nonEmpty) {
          //  val name = members.headOption.get.getDisplayName
          //  getString(R.string.conversation_list__added, name)
          //} else {
          //  getString(R.string.conversation_list__added, memberName)
          //}
          getMemberJoinStr(conv, selfId, members, memberName).getOrElse(if(isGroup) "@" + senderName else "@" + otherMemberName)
        case Message.Type.MEMBER_LEAVE if members.exists(_.id == selfId) && user.exists(_.id == selfId) =>
          getString(R.string.conversation_list__left_you, senderName)
        case Message.Type.MEMBER_LEAVE if members.exists(_.id == selfId) =>
          getString(R.string.conversation_list__removed_you, senderName)
        case Message.Type.MISSED_CALL | Message.Type.SUCCESSFUL_CALL =>
          val content = getResources.getString(R.string.secret_conversation_voice_call_message_subtitle)
          formatSubtitle(content, senderName, isGroup)
        case _ =>
          if (isGroup) "@" + senderName else "@" + otherMemberName
      }
    }
  }

  private def getMemberJoinStr(conversationData: ConversationData,selfId:UserId, members: Vector[UserData], memberName: => Name): Option[String] = {
    if(conversationData.view_chg_mem_notify) {
      if(members.nonEmpty) {
        val name = members.headOption.get.getDisplayName
        Some(getString(R.string.conversation_list__added, name))
      } else {
        Some(getString(R.string.conversation_list__added, memberName))
      }
    } else {
      None
    }
  }

  def getTextJsonMessageSubTitle(textJsonMessageContent: String, msgType: String, selfUserId: UserId)(implicit context: Context): (Int, String) = {
    msgType match {
      case MessageContentUtils.EMOJI_GIF =>
        (com.waz.zclient.R.color.black_48, getResources.getString(R.string.emoji_gif_message_subtitle))
      case _ =>
        (com.waz.zclient.R.color.black_48, "")
    }
  }

  def subtitleStringForLastMessages(conv: ConversationData,
                                    otherMember: Option[UserData],
                                    memberIds: Set[UserId],
                                    lastMessageUser: Option[UserData] = Option.empty[UserData],
                                    lastUnreadMessageMembers: Vector[UserData] = Vector[UserData](),
                                    typingUser: Option[UserData],
                                    selfId: UserId,
                                    aliasData: Option[AliasData] = None,
                                    typingAliasData: Option[AliasData] = None,
                                    isForbid: Boolean = false/*,
                                    userName: Option[Name]*/)
                                   (implicit context: Context): (Boolean, Instant, String) = {
    val lastMessageTime = conv.lastMsgTime.instant
    if (conv.convType == ConversationType.WaitForConnection || (conv.lastMsgType == Message.Type.MEMBER_JOIN && conv.convType == ConversationType.OneToOne)) {
      (false, lastMessageTime, otherMember.flatMap(_.handle.map(_.string)).fold("")(StringUtils.formatHandle))
    } else if (conv.unreadCount.total == 0 && !conv.isActive) {
      (false, lastMessageTime, getString(R.string.conversation_list__left_you))
    } else if (
      (conv.muted.isAllMuted ||
        conv.incomingKnockMessage.nonEmpty ||
        conv.missedCallMessage.nonEmpty)
        && typingUser.isEmpty) {

      val normalMessageCount = conv.unreadCount.normal
      val missedCallCount = conv.unreadCount.call
      val pingCount = conv.unreadCount.ping
      val likesCount = 0 //TODO: There is no good way to get this so far
      val unsentCount = conv.failedCount
      //val mentionsCount = conv.unreadCount.mentions
      //val quotesCount = conv.unreadCount.quotes

      val unsentString =
        if (unsentCount > 0)
          if (normalMessageCount + missedCallCount + pingCount + likesCount == 0)
            getString(R.string.conversation_list__unsent_message_long)
          else
            getString(R.string.conversation_list__unsent_message_short)
        else
          ""
      val strings = Seq(
        if (normalMessageCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__new_message_count, normalMessageCount, normalMessageCount.toString) else "",
        if (missedCallCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__missed_calls_count, missedCallCount, missedCallCount.toString) else "",
        if (pingCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__pings_count, pingCount, pingCount.toString) else "",
        if (likesCount > 0)
          context.getResources.getQuantityString(R.plurals.conversation_list__new_likes_count, likesCount, likesCount.toString) else ""
      ).filter(_.nonEmpty)

      val text = Seq(unsentString, strings.mkString(", ")).filter(_.nonEmpty).mkString(" | ")

      if(StringUtils.isNotBlank(text)){
        (false, lastMessageTime, text)
      }else{
        var isGlyph = false
        val text = conv.lastMsgType match {
          case Message.Type.TEXTJSON =>
            val msgType = MessageContentUtils.getTextJsonContentType(conv.lastMsgContentType.getOrElse(""))
            msgType match {
              case MessageContentUtils.CONV_NOTICE_REPORT_BLOCKED =>
                try {
                  val groupNoticeModel: GroupNoticeModel = GroupNoticeModel.parseJson(conv.contentString)
                  groupNoticeModel.msgData.text
                } catch {
                  case e: Exception =>
                    e.printStackTrace()
                    ""
                }
              case _ =>
                subtitleStringForLastMessage(conv, lastMessageUser, lastUnreadMessageMembers, isGroupForConversation(conv.convType)
                  , selfId, conv.unreadCount.quotes > 0, otherMember, msgType, aliasData = aliasData)
            }
          case _ => subtitleStringForLastMessage(conv, lastMessageUser, lastUnreadMessageMembers, isGroupForConversation(conv.convType)
            , selfId, conv.unreadCount.quotes > 0, otherMember, MessageContentUtils.INVALID_TYPE, aliasData = aliasData)
        }
        (isGlyph, lastMessageTime, text)
      }


    } else {
      var isGlyph = false
      val text = typingUser.fold {
        conv.lastMsgType match {
          case Message.Type.TEXTJSON =>
            val msgType = MessageContentUtils.getTextJsonContentType(conv.lastMsgContentType.getOrElse(""))
            msgType match {
              case MessageContentUtils.CONV_NOTICE_REPORT_BLOCKED =>
                try {
                  val groupNoticeModel: GroupNoticeModel = GroupNoticeModel.parseJson(conv.contentString)
                  groupNoticeModel.msgData.text
                } catch {
                  case e: Exception =>
                    e.printStackTrace()
                    ""
                }
              case _ =>
                subtitleStringForLastMessage(conv, lastMessageUser, lastUnreadMessageMembers, isGroupForConversation(conv.convType)
                  , selfId, conv.unreadCount.quotes > 0, otherMember, msgType, aliasData = aliasData)
            }
          case _ => subtitleStringForLastMessage(conv, lastMessageUser, lastUnreadMessageMembers, isGroupForConversation(conv.convType)
            , selfId, conv.unreadCount.quotes > 0, otherMember, MessageContentUtils.INVALID_TYPE, aliasData = aliasData)
        }
      } { usr =>
        val showUserName = typingAliasData.filter(_.getAliasName.nonEmpty).fold(usr.getShowName)(_.getAliasName)
        formatSubtitle(getString(R.string.conversation_list__typing), showUserName, isGroupForConversation(conv.convType))
      }
      (isGlyph, lastMessageTime, text)
    }
  }
}

object ConversationListRow2 {

  val TAG = classOf[ConversationListRow2].getSimpleName

  val AnimDuration1 = 500L
  val RepeatCount = 60
  val DEFAULT_TIME = "1970"

  def headAnimationSet(animationListener: AnimationListener) = new HeadAnimSet2(true, animationListener)

  def ringAnimSet = new AnimationSet(true) {
    val ringScaleAnim = new ScaleAnimation(1.0F, 1.2F, 1.0F, 1.2F, Animation.RELATIVE_TO_SELF, 0.5F, Animation.RELATIVE_TO_SELF, 0.5F)
    ringScaleAnim.setInterpolator(new LinearInterpolator())
    ringScaleAnim.setDuration(AnimDuration1)
    ringScaleAnim.setRepeatCount(RepeatCount)
    addAnimation(ringScaleAnim)

    val ringAlphaAnim = new AlphaAnimation(1.0F, 0.0F)
    ringAlphaAnim.setInterpolator(new LinearInterpolator())
    ringAlphaAnim.setDuration(AnimDuration1)
    ringAlphaAnim.setRepeatCount(RepeatCount)
    addAnimation(ringAlphaAnim)
  }

  def getLastMessageTime(lastMessage: Option[MessageData]): Instant = {
    if (lastMessage.isEmpty) Instant.EPOCH else lastMessage.get.time.instant
  }

  def isGroupForConversation(conversationType: ConversationType): Boolean = {
    (conversationType == ConversationType.Group || conversationType == ConversationType.ThousandsGroup)
  }

  def shouldShowChatHeadView(conversationData: ConversationData): Boolean = {
    (conversationData.convType == IConversation.Type.ONE_TO_ONE || conversationData.convType == IConversation.Type.WAIT_FOR_CONNECTION)
  }

  def badgeStatusForConversation(conversationData: ConversationData,
                                 unreadCount: ConversationData.UnreadCount,
                                 typing: Boolean,
                                 availableCalls: Map[ConvId, CallInfo],
                                 callDuration: String
                                ): ConversationBadge.Status = {
    if (callDuration!=null && callDuration.nonEmpty) {
      ConversationBadge.OngoingCall(Some(callDuration))
    } else if (availableCalls!=null && availableCalls.contains(conversationData.id)) {
      availableCalls(conversationData.id).state match {
        case SelfCalling => OngoingCall(None)
        case _ => ConversationBadge.IncomingCall
      }
    } else if (conversationData.convType == ConversationType.WaitForConnection || conversationData.convType == ConversationType.Incoming) {
      ConversationBadge.WaitingConnection
    } else if (unreadCount.mentions > 0 && !conversationData.muted.isAllMuted) {
      ConversationBadge.Mention
    } else if (unreadCount.quotes > 0 && !conversationData.muted.isAllMuted) {
      ConversationBadge.Quote
    } else if (!conversationData.muted.isAllAllowed) {
      ConversationBadge.Muted
    } else if (typing) {
      ConversationBadge.Typing
    } else if (conversationData.missedCallMessage.nonEmpty) {
      ConversationBadge.MissedCall
    } else if (conversationData.incomingKnockMessage.nonEmpty) {
      ConversationBadge.Ping
    } else if (unreadCount.messages > 0) {
      ConversationBadge.Count(unreadCount.messages)
    } else {
      ConversationBadge.Empty
    }
  }
}

class HeadAnimSet2(shareInterpolator: Boolean, animationListener: AnimationListener) extends AnimationSet(shareInterpolator) {

  import ConversationListRow2._

  val imageScaleAnimation = new ScaleAnimation(1.0F, 0.75F, 1.0F, 0.75F, Animation.RELATIVE_TO_SELF, 0.5F, Animation.RELATIVE_TO_SELF, 0.5F)
  imageScaleAnimation.setInterpolator(new LinearInterpolator())
  imageScaleAnimation.setDuration(AnimDuration1)
  imageScaleAnimation.setRepeatCount(RepeatCount)
  imageScaleAnimation.setRepeatMode(Animation.REVERSE)
  addAnimation(imageScaleAnimation)

  imageScaleAnimation.setAnimationListener(animationListener)

}

class IncomingConversationListRow2(context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style)
  with ConversationListRow2
  with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__row__height)))
  inflate(R.layout.conv_list_item)

  lazy val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TextView]
  lazy val avatar = ViewUtils.getView(this, R.id.conversation_icon).asInstanceOf[ConversationAvatarView]
  lazy val badge = ViewUtils.getView(this, R.id.conversation_badge).asInstanceOf[ConversationBadge]
  lazy val rlHeadRing = ViewUtils.getView(this, R.id.rlHeadRing).asInstanceOf[View]

  var convId = Option.empty[ConvId]

  def setIncomingUsers(convId: Option[ConvId], users: Seq[UserId]): Unit = {
    this.convId = convId

    avatar.setAlpha(getResourceFloat(R.dimen.conversation_avatar_alpha_inactive))
    avatar.loadMembers(users, ConvId(), ConversationType.Group)
    title.setText(getInboxName(users.size))
    badge.setStatus(ConversationBadge.WaitingConnection)
    rlHeadRing.setVisibility(View.GONE)
  }

  private def getInboxName(convSize: Int): String = getResources.getQuantityString(R.plurals.connect_inbox__link__name, convSize, Integer.valueOf(convSize))
}

class TopStickFoldListRow(context: Context, attrs:AttributeSet, style:Int)  extends FrameLayout(context, attrs, style)
  with ConversationListRow2
  with ViewHelper {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.conversation_list__row__height)))
  inflate(R.layout.conv_list_item_fold)

  private lazy val title = ViewUtils.getView(this, R.id.conversation_title).asInstanceOf[TextView]
  private lazy val arrowImageView = ViewUtils.getView(this, R.id.arrow_imageView).asInstanceOf[ImageView]

  def setData(count: Int, isExpand: Boolean): Unit = {
    title.setText(getContext.getString(R.string.conversation_list_stick_fold, count.toString))
    arrowImageView.setRotation(if (isExpand) 180 else 0)
  }
}
