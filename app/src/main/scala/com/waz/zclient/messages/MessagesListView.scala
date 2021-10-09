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
package com.waz.zclient.messages

import android.app.Activity
import android.content.Context
import android.graphics.{Canvas, Color, PorterDuff, PorterDuffColorFilter}
import android.util.AttributeSet
import android.view.{HapticFeedbackConstants, MotionEvent, ViewConfiguration, WindowManager}
import androidx.core.content.ContextCompat
import androidx.paging.PagedList
import androidx.recyclerview.widget.RecyclerView.{OnScrollListener, ViewHolder}
import androidx.recyclerview.widget.{DefaultItemAnimator, LinearLayoutManager, RecyclerView}
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.{AssetStatus, Message}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.cursor.CursorController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.main.conversationlist.views.listview.VelocityRecyclerView
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import org.telegram.messenger.AndroidUtilities

class MessagesListView(context: Context, attrs: AttributeSet, style: Int)
  extends VelocityRecyclerView(context, attrs, style) with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  private val messagesController = inject[MessagesController]
  private val messageActionsController = inject[MessageActionsController]
  private val messagePagedListController = inject[MessagePagedListController]
  private val collectionsController = inject[CollectionController]
  private val convController = inject[ConversationController]
  private var isGroupConvType=false

  messageActionsController.messageToReveal ! None

  val viewDim = Signal[Dim2]()
  val realViewHeight = Signal[Int]()
  val layoutManager = new MessagesListLayoutManager(context, LinearLayoutManager.VERTICAL, true)
  val adapter = new MessagesPagedListAdapter()
  val scrollController = new ScrollController(adapter, this, layoutManager)

  def setLayoutManagerStartModel(stackFromEnd: Boolean): Unit = {
    layoutManager.setStackFromEnd(stackFromEnd)
  }

  private val plCallback: PagedList.Callback = new PagedList.Callback {

    private def notifyChanged(): Unit = {
      scrollController.onPagedListChanged()
      adapter.notifyDataSetChanged()
    }

    override def onChanged(position: Int, count: Int): Unit = notifyChanged()

    override def onInserted(position: Int, count: Int): Unit = notifyChanged()

    override def onRemoved(position: Int, count: Int): Unit = notifyChanged()
  }
  setClipChildren(false)
  setClipToPadding(false)
  setWillNotDraw(false)
  setHasFixedSize(true)
  setLayoutManager(layoutManager)
  setAdapter(adapter)

  private var prevConv = Option.empty[ConvId]
  messagePagedListController.pagedListData.onUi { case (data, PagedListWrapper(pl), messageToReveal) =>
    val itemCount = adapter.getItemCount
    val isButtom = if (itemCount <= 0) true else !this.canScrollVertically(1)
    pl.addWeakCallback(null, plCallback)
    adapter.convInfo = data
    adapter.submitList(pl)

    val dataSource = pl.getDataSource.asInstanceOf[MessageDataSource]
    val unread = dataSource.positionForMessage(data.lastRead).filter(_ >= 0)
    val toReveal = messageToReveal.flatMap(mtr => dataSource.positionForMessage(mtr).filter(_ >= 0))

    if (!prevConv.contains(data.convId)) {
      scrollController.reset(toReveal.orElse(unread).getOrElse(0))
      prevConv = Some(data.convId)
    } else {
      scrollController.onPagedListReplaced(pl)
      toReveal.foreach(scrollController.scrollToPositionRequested ! _)
    }

    if (isButtom) {
      scrollToBottom()
    }
    verbose(l"messagePagedListController.pagedListData.onUi isButtom:$isButtom,itemCount:$itemCount,adapter.getItemCount:${adapter.getItemCount}")
  }

  convController.sendMessageAndType.onUi { _ =>
    verbose(l"convController.sendMessageAndType.onUi")
    scrollToBottom()
  }

  convController.currentConvType.onUi{ data =>{
      isGroupConvType=ConversationType.isGroupConv(data)
      verbose(l"isGroupConv:${isGroupConvType}")
    }
  }

  viewDim.onUi { dim =>
    verbose(l"viewDim($dim)")
    adapter.listDim = dim
    adapter.notifyDataSetChanged()
  }

  realViewHeight.onChanged {
    scrollController.onListHeightChanged ! _
  }

  adapter.onScrollRequested.onUi { case (message, _) =>
    collectionsController.focusedItem ! None // needed in case we requested a scroll to the same message again
    collectionsController.focusedItem ! Some(message)
  }

  setItemAnimator(new DefaultItemAnimator {
    // always reuse view holder, we will handle animations ourselves
    override def canReuseUpdatedViewHolder(viewHolder: ViewHolder, payloads: java.util.List[AnyRef]): Boolean = true
  })

  addOnScrollListener(new OnScrollListener {
    override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = newState match {
      case RecyclerView.SCROLL_STATE_IDLE =>
        val page = inject[INavigationController].getCurrentPage
        if (page == Page.MESSAGE_STREAM) {
          messagesController.scrolledToBottom ! (layoutManager.findLastCompletelyVisibleItemPosition() == 0)
        }

      case RecyclerView.SCROLL_STATE_DRAGGING => {
        messagesController.scrolledToBottom ! false
        Option(getContext).map(_.asInstanceOf[Activity]).foreach(a => KeyboardUtils.hideKeyboard(a))
      }
      case _ =>
    }
  })

  adapter.hasEphemeral.onUi { hasEphemeral =>
    Option(getContext).foreach {
      case a: Activity =>
        if (hasEphemeral)
          a.getWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else
          a.getWindow.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
      case _ => // not attahced, ignore
    }
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    //We don't want the original height of the view to change if the keyboard comes up, or else images will be resized to
    //fit in the small space left. So only let the height change if for some reason the new height is bigger (shouldn't happen)
    //i.e., height in viewDim should always represent the height of the screen without the keyboard shown.
    viewDim.mutateOrDefault({ case Dim2(_, h) => Dim2(r - l, math.max(h, b - t)) }, Dim2(r - l, b - t))
    realViewHeight ! b - t
    super.onLayout(changed, l, t, r, b)
  }

  def scrollToBottom(): Unit = scrollController.onScrollToBottomRequested ! true


  private var slidingView:MessageView=null
  private var replyButtonProgress = 0.0f
  private var lastReplyButtonAnimationTime = 0L
  private var startedTrackingX = 0
  private var startedTrackingY = 0
  private var startedTrackingPointerId = 0
  private var maybeStartTrackingSlidingView = false
  private var startedTrackingSlidingView = false
  private var wasTrackingVibrate = false
  private var endTrackingX = 0.0f
  private var lastTrackingAnimationTime = 0L
  private var trackAnimationProgress = 0.0f
  private val minTouchSlop=ViewConfiguration.get(context).getScaledTouchSlop

  override def onDraw(canvas: Canvas): Unit = {
    super.onDraw(canvas)
    if(slidingView!=null){
      var translationX = slidingView.getTranslationX
      if (!maybeStartTrackingSlidingView && !startedTrackingSlidingView && endTrackingX != 0 && translationX != 0) {
        val newTime = System.currentTimeMillis
        val dt = newTime - lastTrackingAnimationTime
        trackAnimationProgress += dt / 180.0f
        if (trackAnimationProgress > 1.0f) trackAnimationProgress = 1.0f
        lastTrackingAnimationTime = newTime
        translationX = (endTrackingX * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(trackAnimationProgress)))
        if (translationX == 0) endTrackingX = 0
        slidingView.setTranslationX(translationX)
        if (trackAnimationProgress == 1f || trackAnimationProgress == 0f) slidingView = null
        invalidate()
      }
      drawReplyButton(canvas)
    }
  }

  override def onInterceptTouchEvent(ev: MotionEvent): Boolean = {
    val result = super.onInterceptTouchEvent(ev)
    processTouchEvent(ev)
    startedTrackingSlidingView || result
  }

  private def processTouchEvent(e: MotionEvent): Unit = {
    if(isGroupConvType){
      slidingView = null
      return
    }
    if (e != null && (e.getAction==MotionEvent.ACTION_DOWN) && !startedTrackingSlidingView && !maybeStartTrackingSlidingView && slidingView == null) {
      val view = findChildViewUnder(e.getX, e.getY)
      if(view!=null && view.isInstanceOf[MessageView]){
        val messageView = view.asInstanceOf[MessageView]
        try {
            if (messageView.canReply.currentValue.getOrElse(false)) {
              slidingView = view.asInstanceOf[MessageView]
              startedTrackingPointerId = e.getPointerId(0)
              maybeStartTrackingSlidingView = true
              startedTrackingX = e.getX.asInstanceOf[Int]
              startedTrackingY = e.getY.asInstanceOf[Int]
            } else {
              slidingView = null
            }
        } catch {
          case e:Exception =>
            LogUtils.e("JACK8", "processTouchEvent,error:" +e.getMessage)
        }
      }
    }
    else if (slidingView != null && e != null && (e.getAction ==MotionEvent.ACTION_MOVE) && (e.getPointerId(0)==startedTrackingPointerId)) {
      val dx = Math.max(AndroidUtilities.dp(-80), Math.min(0, (e.getX - startedTrackingX).asInstanceOf[Int]))
      val dy = Math.abs((e.getY- startedTrackingY).asInstanceOf[Int])
      if (getScrollState == RecyclerView.SCROLL_STATE_IDLE && maybeStartTrackingSlidingView && !startedTrackingSlidingView && Math.abs(dx)>=minTouchSlop && Math.abs(dx) / 2 > dy) {
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0)
        slidingView.onTouchEvent(event)
        super.onInterceptTouchEvent(event)
        event.recycle()
        layoutManager.setCanScrollVertically(false)
        maybeStartTrackingSlidingView = false
        startedTrackingSlidingView = true
        startedTrackingX = e.getX.asInstanceOf[Int]
        if (getParent != null) {
          getParent.requestDisallowInterceptTouchEvent(true)
        }
      }
      else if (startedTrackingSlidingView) {
        if (Math.abs(dx) >= AndroidUtilities.dp(50)) {
          if (!wasTrackingVibrate) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            wasTrackingVibrate = true
          }
        }
        else {
          wasTrackingVibrate = false
        }
        slidingView.setTranslationX(dx)
        invalidate()
      }
    }
    else if (slidingView != null && (e == null || (e.getPointerId(0)== startedTrackingPointerId) && ((e.getAction == MotionEvent.ACTION_CANCEL) || (e.getAction==MotionEvent.ACTION_UP) || (e.getAction== MotionEvent.ACTION_POINTER_UP)))) {
      if (e != null && (e.getAction != MotionEvent.ACTION_CANCEL) && Math.abs(slidingView.getTranslationX) >= AndroidUtilities.dp(50)) {
        if(slidingView.msg!=null){
          LogUtils.d("JACK8","showReplyMessage")
          messageActionsController.replyMessage(slidingView.msg)
        }
      }
      endTrackingX = slidingView.getTranslationX
      if (endTrackingX == 0) slidingView = null
      lastTrackingAnimationTime = System.currentTimeMillis
      trackAnimationProgress = 0.0f
      invalidate()
      maybeStartTrackingSlidingView = false
      startedTrackingSlidingView = false
      layoutManager.setCanScrollVertically(true)
    }
  }

  override def onTouchEvent(e: MotionEvent): Boolean = {
    val result = super.onTouchEvent(e)
    processTouchEvent(e)
    startedTrackingSlidingView || result
  }


  override def requestDisallowInterceptTouchEvent(disallowIntercept: Boolean): Unit = {
    super.requestDisallowInterceptTouchEvent(disallowIntercept)
    if (slidingView != null) {
      processTouchEvent(null)
    }
  }

  private def drawReplyButton(canvas: Canvas): Unit ={
    if (slidingView == null) {
      return
    }
    val translationX = slidingView.getTranslationX
    val newTime = System.currentTimeMillis
    val dt = Math.min(17, newTime - lastReplyButtonAnimationTime)
    lastReplyButtonAnimationTime = newTime
    var showing = translationX <= -(AndroidUtilities.dp(50))
    if (showing) {
      if (replyButtonProgress < 1.0f) {
        replyButtonProgress += dt / 180.0f
        if (replyButtonProgress > 1.0f) {
          replyButtonProgress = 1.0f
        }
        else {
          invalidate()
        }
      }
    }
    else if (replyButtonProgress > 0.0f) {
      replyButtonProgress -= dt / 180.0f
      if (replyButtonProgress < 0.0f){
        replyButtonProgress = 0
      }
      else {
        invalidate()
      }
    }
    var alpha = 0
    var scale = 0.0
    if (showing) {
      if (replyButtonProgress <= 0.8f) {
        scale = 1.2f * (replyButtonProgress / 0.8f)
      }
      else {
        scale = 1.2f - 0.2f * ((replyButtonProgress - 0.8f) / 0.2f)
      }
      alpha = Math.min(255, 255 * (replyButtonProgress / 0.8f)).asInstanceOf[Int]
    }
    else {
      scale = replyButtonProgress
      alpha = Math.min(255, 255 * replyButtonProgress).asInstanceOf[Int]
    }
    val chat_shareDrawable = ContextCompat.getDrawable(context, R.drawable.share_round)
    val chat_replyIconDrawable = ContextCompat.getDrawable(context, R.drawable.fast_reply)
    chat_shareDrawable.setAlpha(alpha)
    chat_replyIconDrawable.setAlpha(alpha)
    val x = getMeasuredWidth + slidingView.getTranslationX / 2
    val y = slidingView.getTop + slidingView.getMeasuredHeight / 2
    chat_shareDrawable.setColorFilter(new PorterDuffColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY))
    chat_shareDrawable.setBounds((x - AndroidUtilities.dp(14) * scale).asInstanceOf[Int], (y - AndroidUtilities.dp(14) * scale).asInstanceOf[Int], (x + AndroidUtilities.dp(14) * scale).asInstanceOf[Int], (y + AndroidUtilities.dp(14) * scale).asInstanceOf[Int])
    chat_shareDrawable.draw(canvas)
    chat_replyIconDrawable.setBounds((x - AndroidUtilities.dp(7) * scale).asInstanceOf[Int], (y - AndroidUtilities.dp(6) * scale).asInstanceOf[Int], (x + AndroidUtilities.dp(7) * scale).asInstanceOf[Int], (y + AndroidUtilities.dp(5) * scale).asInstanceOf[Int])
    chat_replyIconDrawable.draw(canvas)
    chat_shareDrawable.setAlpha(255)
    chat_replyIconDrawable.setAlpha(255)
  }

}

object MessagesListView {

  val MaxSmoothScroll = 50

  case class UnreadIndex(index: Int) extends AnyVal
}

case class MessageViewHolder(view: MessageView, adapter: MessagesPagedListAdapter)(implicit ec: EventContext, inj: Injector)
  extends RecyclerView.ViewHolder(view)
    with Injectable
    with DerivedLogTag {

  private val selection = inject[ConversationController].messages
  private val msgsController = inject[MessagesController]
  private lazy val assets = inject[AssetsController]

  private lazy val zms = inject[Signal[ZMessaging]]
  private val convController = inject[ConversationController]
  private val cursorController = inject[CursorController]
  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  val message = Signal[MessageData]

  def id = message.currentValue.map(_.id)

  private var opts = Option.empty[MsgBindOptions]
  private var _isFocused = false

  selection.focused.onChanged.on(Threading.Ui) { mId =>
    if (_isFocused != (id == mId)) adapter.notifyItemChanged(getAdapterPosition)
  }

  msgsController.lastSelfMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLastSelf != id.contains(m.id)) {
        adapter.notifyItemChanged(getAdapterPosition)
      }
    }
  }

  msgsController.lastMessage.onChanged.on(Threading.Ui) { m =>
    opts foreach { o =>
      if (o.isLast != id.contains(m.id)) adapter.notifyItemChanged(getAdapterPosition)
    }
  }

  // mark message as read if message is bound while list is visible
  msgsController.fullyVisibleMessagesList.flatMap {
    case Some(convId) =>
      message.filter(_.convId == convId) flatMap {
        case msg if msg.isAssetMessage && msg.state == Message.Status.SENT =>
          // received asset message is considered read when its asset is available,
          // this is especially needed for ephemeral messages, only start the counter when message is downloaded
          assets.assetSignal(msg.assetId) flatMap {
            case (_, AssetStatus.DOWNLOAD_DONE) if msg.msgType == Message.Type.ASSET =>
              // image assets are considered read only once fully downloaded
              Signal const msg
            case (_, AssetStatus.UPLOAD_DONE | AssetStatus.UPLOAD_CANCELLED | AssetStatus.UPLOAD_FAILED) if msg.msgType != Message.Type.ASSET =>
              // for other assets it's enough when upload is done, download is user triggered here
              Signal const msg
            case _ => Signal.empty[MessageData]
          }
        case msg => Signal const msg
      }
    case None => Signal.empty[MessageData]
  }(msgsController.onMessageRead)

  def bind(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], opts: MsgBindOptions): Unit = {
    view.set(msg,prev, next, opts, adapter)
    message ! msg.message
    this.opts = Some(opts)
    _isFocused = selection.isFocused(msg.message.id)
  }
}
