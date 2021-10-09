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
package com.waz.zclient.messages

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.{Canvas, Color, Paint}
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, RelativeLayout}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.acts.SendConnectRequestActivity
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.waz.api.Message
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.assets.AssetActionButton
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.jsy.res.theme.ThemeUtils
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getDimenPx}
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import org.json.JSONObject

trait MessageViewPart extends View {
  def tpe: MsgPart


  var adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]] = _
  var opts: Option[MsgBindOptions] = Option.empty
  var msgLikes: Option[MessageAndLikes] = Option.empty
  var prev: Option[MessageData] = Option.empty
  var next: Option[MessageData] = Option.empty
  var part: Option[MessageContent] = Option.empty

  protected val messageAndLikes = Signal[MessageAndLikes]()
  protected val message = messageAndLikes.map(_.message)
  message.disableAutowiring() //important to ensure the signal keeps updating itself in the absence of any listeners

  def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    this.msgLikes = Some(msg)
    this.prev = prev
    this.next = next
    this.part = part
    this.opts = opts
    this.adapter = adapter
    messageAndLikes.publish(msg, Threading.Ui)

  }

  final def set(msg: MessageAndLikes, part: Option[MessageContent], opts: Option[MsgBindOptions] = None): Unit = {
    this.adapter = None
    this.opts = opts
    this.msgLikes = Some(msg)
    set(msg, None, None, part, opts, None)
  }

  //By default disable clicks for all view types. There are fewer that need click functionality than those that don't
  this.onClick {}
  this.onLongClick(false)

  def getChatMessageBg(self:Boolean): Int = {
    if (self) {
      if (ThemeUtils.isDarkTheme(getContext)) {
        R.drawable.icon_chat_send_bg_dark
      }else {
        R.drawable.icon_chat_send_bg_light
      }
    }else {
      if (ThemeUtils.isDarkTheme(getContext)) {
        R.drawable.icon_chat_received_bg_dark
      }else {
        R.drawable.icon_chat_received_bg_light
      }
    }
  }

  /**
    *
    * @param tpe
    * @param bgView
    * @param isSelf
    * @param nextIsSameSide
    * @param isRepliedChild
    */
  def setItemBackground(tpe: MsgPart, bgView: View = this, isSelf: Boolean = false, nextIsSameSide: Boolean, isRepliedChild: Boolean = false): Unit = {
    bgView.setBackgroundResource {
      tpe match {
        case MsgPart.Text =>
          if (!isRepliedChild) {
            getChatMessageBg(isSelf)
          } else {
            R.color.transparent
          }
        case MsgPart.FileAsset | MsgPart.AudioAsset | MsgPart.VideoAsset | MsgPart.Image
             | MsgPart.AudioAsset =>
          if (isSelf) {
            val shouldChangeLayoutDirection = tpe match {
              case MsgPart.AudioAsset | MsgPart.FileAsset => false
              case _ => true
            }
            if (shouldChangeLayoutDirection) {
              setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
            } else {
              setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
            }
            if (!isRepliedChild) {
              getChatMessageBg(isSelf)
            } else {
              R.color.transparent
            }
          } else {
            setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
            if (!isRepliedChild) {
              getChatMessageBg(isSelf)
            } else {
              R.color.transparent
            }
          }
        case _ =>
          getChatMessageBg(isSelf)
      }
    }
  }

  def setAudioActionButtonBg(view: View, isSelf: Boolean): Unit = {
    tpe match {
      case MsgPart.AudioAsset => {
        val assetActionButton: AssetActionButton = view.findViewById(R.id.action_button)
        if (isSelf) {
          assetActionButton.normalButtonResource(R.drawable.shape__icon_button__bg__video_message_from_me)
        } else {
          assetActionButton.normalButtonResource(R.drawable.selector__icon_button__background__video_message)
        }
      }
      case _ =>
    }
  }

}

/**
  * Marker for views that should pass up the click event either when clicked/double cicked OR when long clicked
  * This prevents some of the more distant parts of a single message view (like the timestamp or chathead view) from
  * passing up the click event, which can feel a bit confusing.
  *
  * Check the message view as well - it has further filtering on which views
  */
trait ClickableViewPart extends MessageViewPart with ViewHelper with DerivedLogTag {
  import com.waz.threading.Threading.Implicits.Ui

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val likes = inject[LikesController]
  val onClicked = EventStream[Unit]()

  def onSingleClick() = {
    onClicked ! ({})
    Option(getParent.asInstanceOf[View]).foreach(_.performClick())
  }

  def onDoubleClick() = messageAndLikes.head.map { mAndL =>
    if (MessageView.clickableTypes.contains(mAndL.message.msgType)) {
      likes.onViewDoubleClicked ! mAndL
      Option(getParent.asInstanceOf[View]).foreach(_.performClick()) //perform click to change focus
    }
  }

  this.onClick({
    onSingleClick
  }, {
    onDoubleClick
  })

  this.onLongClick(getParent.asInstanceOf[View].performLongClick())
}

trait HighlightViewPart extends MessageViewPart with ViewHelper with DerivedLogTag{
  private lazy val accentColorController = inject[AccentColorController]
  private lazy val collectionController = inject[CollectionController]
  private val animAlpha = Signal(0f)

  private val animator = ValueAnimator.ofFloat(1, 0).setDuration(1500)

  animator.addUpdateListener(new AnimatorUpdateListener {
    override def onAnimationUpdate(animation: ValueAnimator): Unit =
      animAlpha ! Math.min(animation.getAnimatedValue.asInstanceOf[Float], 0.5f)
  })

  private val bgColor = for {
    accent <- accentColorController.accentColor
    alpha <- animAlpha
  } yield
    if (alpha <= 0) Color.TRANSPARENT
    else ColorUtils.injectAlpha(alpha, accent.color)

  private val isHighlighted = for {
    msg           <- message
    Some(focused) <- collectionController.focusedItem
  } yield focused.id == msg.id

  //  bgColor.on(Threading.Ui) {
  //    setBackgroundColor
  //  }

  isHighlighted.on(Threading.Ui) {
    case true => animator.start()
    case false => animator.end()
  }

  def stopHighlight(): Unit = animator.end()

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    if (adapter.nonEmpty && adapter.head.isInstanceOf[MessagesPagedListAdapter]) {
    } else {
      bgColor.currentValue.foreach {
        setBackgroundColor
      }
    }
  }
}

// Marker for view parts that should be laid out as in FrameLayout (instead of LinearLayout)
trait FrameLayoutPart extends MessageViewPart

trait TimeSeparator extends MessageViewPart with ViewHelper {

  lazy val timeText: TypefaceTextView = findById(R.id.separator__time)
  lazy val unreadDot: UnreadDot = findById(R.id.unread_dot)

  val time = Signal[RemoteInstant]()
  val text = time.map(_.instant).map(TimeStamp(_).string)

  text.on(Threading.Ui)(timeText.setTransformedText)

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    this.time ! msg.message.time
    //opts.foreach(unreadDot.show ! _.isFirstUnread)
    unreadDot.setVisibility(View.GONE)
  }
}

class SeparatorView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with TimeSeparator {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.Separator
}

class SeparatorViewLarge(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with TimeSeparator {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.SeparatorLarge

  if (ThemeUtils.isDarkTheme(context)) setBackgroundColor(getColor(R.color.white_8))
  else setBackgroundColor(getColor(R.color.black_4))

}

class UnreadDot(context: Context, attrs: AttributeSet, style: Int)
  extends View(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  val accent = inject[AccentColorController].accentColor
  val show = Signal[Boolean](false)

  val dotRadius = getDimenPx(R.dimen.conversation__unread_dot__radius)
  val dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG)

  accent { color =>
    dotPaint.setColor(color.color)
    postInvalidate()
  }

  show.onChanged.on(Threading.Ui)(_ => invalidate())

  override def onDraw(canvas: Canvas): Unit = if (show.currentValue.getOrElse(false)) canvas.drawCircle(getWidth / 2, getHeight / 2, dotRadius, dotPaint)
}

class UserPartView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with MessageViewPart with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.User

  inflate(R.layout.message_user_content)

  private val chathead: ChatHeadViewNew = findById(R.id.chathead)
  private val tvName: TypefaceTextView = findById(R.id.tvName)
  private val gtvStateGlyph: GlyphTextView = findById(R.id.gtvStateGlyph)

  private val userId = Signal[UserId]()
  private lazy val convController = inject[ConversationController]
  lazy val zms = inject[Signal[ZMessaging]]
  implicit lazy val uiStorage = inject[UiStorage]

 // override val onClicked = EventStream[Unit]()

  var msg : MessageData = _

//  onClicked { _ =>
//    if (isThousandsGroupMsg) {
//      if (msg != null && !account.currentValue.map(_.id).contains(msg.userId)) {
//        val allowUserAddFriend = convController.currentConv.currentValue.map(_.add_friend).getOrElse(false) ||
//          convController.currentConv.currentValue.map(_.creator.str.equals(SpUtils.getUserId(getContext))).getOrElse(false)
//
//        SendConnectRequestActivity.startSelf(msg.userId.str, getContext, allowUserAddFriend,null)
//      }
//    }
//  }

  chathead.setOnClickListener(new View.OnClickListener {
    override def onClick(view: View): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        if (MessageContentUtils.isGroupForConversation(conversationData.convType)
          && msg != null && !account.currentValue.map(_.id).contains(msg.userId)) {
          val allowUserAddFriend = conversationData.add_friend || conversationData.creator.str.equals(SpUtils.getUserId(getContext)) || ParticipantsController.isManager(conversationData,UserId(SpUtils.getUserId(getContext)))
          SendConnectRequestActivity.startSelf(msg.userId.str, getContext, allowUserAddFriend, null, true,true)
        }
      }
    }
  })

  chathead.setOnLongClickListener(new View.OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      convController.currentConv.currentValue.foreach { conversationData =>
        if (MessageContentUtils.isGroupForConversation(conversationData.convType)
          && msg != null && !account.currentValue.map(_.id).contains(msg.userId)) {
          UserSignal(msg.userId).currentValue.foreach{
            userData =>
              convController.onUserLongClicked ! userData
          }
        }
      }
      true
    }
  })

  lazy val account = ZMessaging.currentAccounts.activeAccount.collect { case Some(accountData) => accountData }

  private lazy val user = Signal(zms, userId).flatMap {
//    case (z, id) => z.usersStorage.signal(id)
    case (_, id) => UserSignal(id)
  }
  private lazy val alias = Signal(convController.currentConv, userId).flatMap {
    case (conversationData, userId) => AliasSignal(conversationData.id, userId)
  }

  private lazy val stateGlyph = message map {
    case m if m.msgType == Message.Type.RECALLED => Some(R.string.glyph__trash)
    case m if !m.editTime.isEpoch => Some(R.string.glyph__edit)
    case _ => None
  }

  user.onUi { tempUser =>
    if(!isThousandsGroupMsg) {
      chathead.setUserData(tempUser)
    }
  }

  (for {
    showName <- user.map(_.getShowName)
    aliasName <- alias.map(_.map(_.getAliasName).filter(_.nonEmpty))
  } yield (showName, aliasName))
    .onUi { parts =>
      if(!isThousandsGroupMsg) {
        tvName.setTransformedText(parts._2.getOrElse(parts._1))
      }
    }

  user.map(_.accent).on(Threading.Ui) { accentColor =>
    tvName.setTextColor(getNameColor(accentColor))
  }

  stateGlyph.map(_.isDefined) {
    gtvStateGlyph.setVisible
  }

  stateGlyph.collect { case Some(glyph) => glyph } {
    gtvStateGlyph.setText
  }

  private var isThousandsGroupMsg = false


  def showEditOrDeletedStatus(isSelf: Boolean): Unit = {
    if (isSelf) {
      returning(gtvStateGlyph.getLayoutParams) { lp =>
        getLayoutDirection match {
          case View.LAYOUT_DIRECTION_LTR =>
            lp.asInstanceOf[LinearLayout.LayoutParams].leftMargin = MessageViewLayout.dp_24(getContext)
          case View.LAYOUT_DIRECTION_RTL =>
            lp.asInstanceOf[LinearLayout.LayoutParams].rightMargin = MessageViewLayout.dp_24(getContext)
        }
      }
    }
    setVisibility(View.VISIBLE)
    (0 until getChildCount).foreach { idx =>
      val child = getChildAt(idx)
      if (child == gtvStateGlyph) {
        child.setVisibility(View.VISIBLE)
      } else {
        if(isSelf){
          child.setVisibility(View.GONE)
        }
        else{
          child.setVisibility(View.VISIBLE)
        }

      }
    }

  }

  def showAllChilds(): Unit = {
    (0 until getChildCount).foreach { idx =>
      val child = getChildAt(idx)
      child.setVisibility(View.VISIBLE)
    }
    returning(gtvStateGlyph.getLayoutParams) { lp =>
      lp.asInstanceOf[LinearLayout.LayoutParams].leftMargin = 0
    }
  }


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    val isTextJson: Boolean = msg.message.msgType == Message.Type.TEXTJSON
    this.msg = msg.message

    def setLayoutDirectionNormal(): Unit = {
      opts.foreach { opts =>
        if (opts.isSelf) {
          this.setLayoutDirection(View.LAYOUT_DIRECTION_RTL)
        } else {
          this.setLayoutDirection(View.LAYOUT_DIRECTION_LTR)
        }
      }
    }

    def getAutoreplyUserId(content: JSONObject): UserId = {
      val msgData: JSONObject = content.optJSONObject(MessageUtils.KEY_TEXTJSON_MSGDATA)
      new UserId(msgData.optString("toUserId"))
    }

    def getAssistantUserId(content: JSONObject): UserId = {
      val msgData: JSONObject = content.optJSONObject(MessageUtils.KEY_TEXTJSON_MSGDATA)
      new UserId(msgData.optString("fromUserId"))
    }

    def showChatHead(updateUserId: UserId, picture: Option[String]): Unit = {
      if(!isThousandsGroupMsg) {
        userId ! updateUserId
      } else {
        picture.fold(chathead.setImageResource(R.drawable.circle_noname)) { url =>
          chathead.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(url, context), R.drawable.circle_noname)
        }
      }
    }

    def showAssistantChatHead(updateUserId: UserId): Unit = {
      userId ! updateUserId
    }

    isThousandsGroupMsg = convController.currentConv.currentValue.exists(MainActivityUtils.isOnlyThousandsGroupConversation)

    if (isThousandsGroupMsg) {
      msg.message.userName.foreach(tvName.setTransformedText)
      tvName.setTextColor(ColorUtils.getAttrColor(context,R.attr.SecretPrimaryTextColor))
    }

    if (isTextJson) {
      try {
        val msgType = MessageContentUtils.getTextJsonContentType(msg.message.contentType.getOrElse(""))
        msgType match {
          case MessageContentUtils.EMOJI_GIF =>
            setLayoutDirectionNormal()
            showChatHead(msg.message.userId, msg.message.picture)
          case _ =>
        }
      } catch {
        case ex: Exception =>
          setLayoutDirectionNormal()
          showChatHead(msg.message.userId, msg.message.picture)
      }
    } else {
      setLayoutDirectionNormal()
      showChatHead(msg.message.userId, msg.message.picture)
    }

    opts.foreach { opts =>
      if (MessageView.shouldShowChatheadAllChilds(opts.convType, prev, msg.message, opts.isSelf)) {
        showAllChilds()
      } else {
        if (MessageView.isEditedMsg(msg.message) || MessageView.isDeletedMsg(msg.message)) {
          showEditOrDeletedStatus(opts.isSelf)
        } else {
          // ..
        }
      }
    }
  }

  def getNameColor(accent: Int): Int = {
    val alpha = if (ThemeUtils.isDarkTheme(context)) accent match {
      case 1 => 0.8f
      case 2 => 0.72f
      case 4 => 0.72f
      case 5 => 0.8f
      case 6 => 0.8f
      case 7 => 1f
      case _ => 1f
    } else accent match {
      case 1 => 0.8f
      case 2 => 0.72f
      case 4 => 0.56f
      case 5 => 0.80f
      case 6 => 0.80f
      case 7 => 0.64f
      case _ => 1f
    }
    ColorUtils.injectAlpha(alpha, AccentColor(accent).color)
  }

}

class EmptyPartView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with MessageViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.Empty
}

class EphemeralDotsView(context: Context, attrs: AttributeSet, style: Int) extends View(context, attrs, style) with ViewHelper with FrameLayoutPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe = MsgPart.EphemeralDots

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
  }
}
