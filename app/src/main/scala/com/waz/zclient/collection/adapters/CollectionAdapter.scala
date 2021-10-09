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
package com.waz.zclient.collection.adapters

import android.content.Context
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.{AdapterDataObserver, ViewHolder}
import com.jsy.res.utils.ViewUtils
import com.waz.api.{Message, MessageFilter}
import com.waz.log.BasicLogging.LogTag
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.returning
import com.waz.zclient.collection.adapters.CollectionAdapter._
import com.waz.zclient.collection.controllers.CollectionController._
import com.waz.zclient.collection.controllers._
import com.waz.zclient.collection.views._
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.RecyclerCursor
import com.waz.zclient.messages.RecyclerCursor.RecyclerNotifier
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.ui.utils.ResourceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}
import org.threeten.bp._
import org.threeten.bp.temporal.ChronoUnit

class CollectionAdapter(viewDim: Signal[Dim2])(implicit context: Context, injector: Injector, eventContext: EventContext)
  extends RecyclerView.Adapter[ViewHolder]
    with Injectable
    with DerivedLogTag { adapter =>

  private val zms = inject[Signal[ZMessaging]]
  private val convController = inject[ConversationController]
  private val collectionController = inject[CollectionController]
  private val messageActionsController = inject[MessageActionsController]

  val contentMode = Signal[ContentType](AllContent)

  var header: CollectionHeaderLinearLayout = null

  val adapterState = Signal[AdapterState](AdapterState(contentMode.currentValue.get, 0, loading = true))

  Signal(convController.currentConv, adapterState).on(Threading.Ui){
    case (c, AdapterState(AllContent, 0, false)) => collectionController.openedCollection ! Some(CollectionInfo(c, empty = true))
    case (c, AdapterState(AllContent, count, false)) => collectionController.openedCollection ! Some(CollectionInfo(c, empty = false))
    case _ =>
  }

  val collectionCursors = scala.collection.mutable.Map[ContentType, Option[RecyclerCursor]](
    AllContent -> None,
    Images -> None,
    Files -> None,
    Links -> None,
    Audios -> None,
    Videos -> None)

  def cursorForContentMode(contentType: ContentType): Unit ={
    val notifier = new CollectionRecyclerNotifier(contentType, adapter)
    val cursor = for {
      zs <- zms
      cId <- convController.currentConvId
      rc <- Signal(new RecyclerCursor(cId, zs, notifier, Some(MessageFilter(Some(contentType.typeFilter)))))
      _ <- rc.countSignal
    } yield rc

    debug(l"Started loading for: $contentType")
    cursor.on(Threading.Ui) { c =>
      if (!collectionCursors(contentType).contains(c)) {
        collectionCursors(contentType).foreach(_.close())
        collectionCursors(contentType) = Some(c)
        debug(l"Cursor loaded for: $contentType, current mode is: ${contentMode.currentValue}")
        notifier.notifyDataSetChanged()
      }
    }
  }

  collectionCursors.foreach(t => cursorForContentMode(t._1))

  def messages = contentMode.currentValue.fold(Option.empty[RecyclerCursor])(collectionCursors(_))

  Signal(contentMode, viewDim) .on(Threading.Ui){ _ =>
    notifyDataSetChanged()
  }

  setHasStableIds(true)
  registerAdapterDataObserver(new AdapterDataObserver {
    override def onChanged(): Unit = {
      adapterState ! AdapterState(contentMode.currentValue.get, getItemCount, messages.isEmpty)
    }

    override def onItemRangeInserted(positionStart: Int, itemCount: Int) = onChanged()

    override def onItemRangeChanged(positionStart: Int, itemCount: Int) = onChanged()

    override def onItemRangeRemoved(positionStart: Int, itemCount: Int) = onChanged()

    override def onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = onChanged()
  })

  override def getItemCount: Int = messages.fold(0)(_.count)

  override def getItemViewType(position: Int): Int = {
    getItem(position).fold(CollectionAdapter.VIEW_TYPE_DEFAULT)(_.msgType match {
      case Message.Type.ANY_ASSET                                => CollectionAdapter.VIEW_TYPE_FILE
      case Message.Type.ASSET                                    => CollectionAdapter.VIEW_TYPE_IMAGE
      case Message.Type.RICH_MEDIA if hasOpenGraphData(position) => CollectionAdapter.VIEW_TYPE_LINK_PREVIEW
      case Message.Type.RICH_MEDIA                               => CollectionAdapter.VIEW_TYPE_SIMPLE_LINK
      case Message.Type.VIDEO_ASSET                              => CollectionAdapter.VIEW_TYPE_VIDEO
      case Message.Type.AUDIO_ASSET                              => CollectionAdapter.VIEW_TYPE_AUDIO
      case _                                                     => CollectionAdapter.VIEW_TYPE_DEFAULT
    })
  }

  private def hasOpenGraphData(position: Int): Boolean = {
    getItem(position).exists(_.content.exists(_.openGraph.nonEmpty))
  }

  override def onBindViewHolder(holder: ViewHolder, position: Int): Unit = {
    getItem(position).foreach{ md =>
      holder match {
        case c: CollectionImageViewHolder =>
          c.setMessageData(md, viewDim.currentValue.fold(0)(_.width) / CollectionController.GridColumns, ResourceUtils.getRandomAccentColor(context)); c.view.setTag(position)
        case l: CollectionItemViewHolder if getItemViewType(position) == CollectionAdapter.VIEW_TYPE_LINK_PREVIEW =>
          l.setMessageData(md, md.content.find(_.openGraph.nonEmpty))
        case l: CollectionItemViewHolder =>
          l.setMessageData(md)
        case c: CollectionVideoViewHolder=>
          c.setMessageData(md, viewDim.currentValue.fold(0)(_.width) / CollectionController.GridColumns, ResourceUtils.getRandomAccentColor(context)); c.view.setTag(position)
        case _ =>
      }
    }
  }

  def onBackPressed(): Boolean = contentMode.currentValue.get match {
    case AllContent => false
    case _ =>
      contentMode ! AllContent
      true
  }

  def onHeaderClicked(position: Int): Boolean = {
    if (position < 0) {
      false
    } else {
      contentMode.mutate {
        case AllContent =>
          getHeaderId(position) match {
            case Header.mainLinks if shouldBeClickable(Header.mainLinks) => Links
            case Header.mainImages if shouldBeClickable(Header.mainImages) => Images
            case Header.mainFiles if shouldBeClickable(Header.mainFiles)=> Files
            case Header.mainVideos if shouldBeClickable(Header.mainVideos) => Videos
            case Header.mainAudios if shouldBeClickable(Header.mainAudios) => Audios
            case _ => AllContent
          }
        case currentMode => currentMode
      }
      true
    }
  }

  val imageListener = new OnClickListener {
    override def onClick(v: View): Unit = {
      v match {
        case collectionItemView: CollectionItemView => {
          val currentData = collectionItemView.messageData.currentValue
          collectionController.focusedItem ! currentData
          currentData.foreach(messageActionsController.onMessageAction ! (MessageAction.Reveal, _))
        }
        case _ =>
      }
    }
  }

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
    viewType match {
      case CollectionAdapter.VIEW_TYPE_IMAGE => CollectionImageViewHolder(inflateCollectionImageView(parent), imageListener)
      case CollectionAdapter.VIEW_TYPE_VIDEO => CollectionVideoViewHolder(inflateCollectionVideoView(parent), imageListener)
      case otherType                         => CollectionItemViewHolder(inflateCollectionView(otherType, parent))
    }

  private def inflateCollectionImageView(parent: ViewGroup) = new CollectionImageView(context)

  private def inflateCollectionVideoView(parent: ViewGroup) = new CollectionVideoView(context)

  private def inflateCollectionView(viewType: Int, parent: ViewGroup) = {
    viewType match {
      case CollectionAdapter.VIEW_TYPE_FILE =>
        ViewHelper.inflate[CollectionNormalItemView](R.layout.collection_file_asset, parent, addToParent = false)
      case CollectionAdapter.VIEW_TYPE_LINK_PREVIEW =>
        ViewHelper.inflate[CollectionNormalItemView](R.layout.collection_link_preview, parent, addToParent = false)
      case CollectionAdapter.VIEW_TYPE_SIMPLE_LINK =>
        ViewHelper.inflate[CollectionNormalItemView](R.layout.collection_simple_link, parent, addToParent = false)
      case CollectionAdapter.VIEW_TYPE_AUDIO =>
        ViewHelper.inflate[CollectionNormalItemView](R.layout.collection_audio_asset, parent, addToParent = false)
      case _ =>
        returning(null.asInstanceOf[CollectionNormalItemView])(_ => error(l"Unexpected ViewType: $viewType"))
    }
  }

  def isFullSpan(position: Int): Boolean = {
    contentMode.currentValue.get match {
      case AllContent =>
        getItemViewType(position) match {
          case CollectionAdapter.VIEW_TYPE_FILE => true
          case CollectionAdapter.VIEW_TYPE_IMAGE => false
          case CollectionAdapter.VIEW_TYPE_LINK_PREVIEW => true
          case CollectionAdapter.VIEW_TYPE_SIMPLE_LINK => true
          case CollectionAdapter.VIEW_TYPE_VIDEO => false
          case CollectionAdapter.VIEW_TYPE_AUDIO => true
        }
      case Images =>
        false
      case _ =>
        true
    }
  }

  def getItem(position: Int): Option[MessageData] = {
    messages.fold(Option.empty[MessageData])(cursor =>
      if (cursor.count > position)
        Some(cursor.apply(position).message)
      else
        None)
  }

  def getHeaderId(position: Int): HeaderId = {
    contentMode.currentValue.get match {
      case AllContent => {
        getItem(position).fold(Message.Type.UNKNOWN)(_.msgType) match {
          case Message.Type.ANY_ASSET => Header.mainFiles
          case Message.Type.ASSET => Header.mainImages
          case Message.Type.RICH_MEDIA => Header.mainLinks
          case Message.Type.AUDIO_ASSET => Header.mainAudios
          case Message.Type.VIDEO_ASSET => Header.mainVideos
          case _ => Header.invalid
        }
      }
      case _ =>
        val time = getItem(position).map(_.time).getOrElse(RemoteInstant.Epoch)
        val now = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).toLocalDate
        val messageDate = LocalDateTime.ofInstant(time.instant, ZoneId.systemDefault()).toLocalDate()

        if (now == messageDate)
          Header.subToday
        else if (now.minus(1, ChronoUnit.DAYS) == messageDate)
          Header.subYesterday
        else
          HeaderId(HeaderType.MonthName, messageDate.getMonthValue, messageDate.getYear)
    }
  }

  def getHeaderView(parent: RecyclerView, position: Int): View = {
    if (header == null) {
      header = new CollectionHeaderLinearLayout(parent.getContext)
    }
    if (header.getLayoutParams == null) {
      header.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    val headerId = getHeaderId(position)
    header.iconView.setText(getHeaderIcon(headerId))
    header.nameView.setText(getHeaderText(headerId))
    header.countView.setText(getHeaderCountText(headerId))
    if (contentMode.currentValue.contains(AllContent)) {
      header.iconView.setVisibility(View.VISIBLE)
    } else {
      header.iconView.setVisibility(View.GONE)
    }

    if (shouldBeClickable(headerId)) {
      header.countView.setVisibility(View.VISIBLE)
      header.arrowView.setVisibility(View.VISIBLE)
    } else {
      header.countView.setVisibility(View.GONE)
      header.arrowView.setVisibility(View.GONE)
    }

    val widthSpec: Int = View.MeasureSpec.makeMeasureSpec(parent.getWidth, View.MeasureSpec.EXACTLY)
    val heightSpec: Int = View.MeasureSpec.makeMeasureSpec(parent.getHeight, View.MeasureSpec.EXACTLY)
    val childWidth: Int = ViewGroup.getChildMeasureSpec(widthSpec, parent.getPaddingLeft + parent.getPaddingRight, header.getLayoutParams.width)
    val childHeight: Int = ViewGroup.getChildMeasureSpec(heightSpec, parent.getPaddingTop + parent.getPaddingBottom, header.getLayoutParams.height)
    header.measure(childWidth, childHeight)
    header.layout(0, 0, header.getMeasuredWidth, header.getMeasuredHeight)
    header
  }

  private def getHeaderText(headerId: HeaderId): String = {
    headerId match {
      case HeaderId(HeaderType.Images, _, _) => getString(R.string.collection_header_pictures)
      case HeaderId(HeaderType.Files, _, _) => getString(R.string.collection_header_files)
      case HeaderId(HeaderType.Links, _, _) => getString(R.string.collection_header_links)
      case HeaderId(HeaderType.Today, _, _) => getString(R.string.collection_header_today)
      case HeaderId(HeaderType.Yesterday, _, _) => getString(R.string.collection_header_yesterday)
      case HeaderId(HeaderType.MonthName, m, y) =>
        if (LocalDateTime.now.getYear == y) {
          Month.of(m).toString
        } else {
          Month.of(m).toString + " " + y
        }
      case HeaderId(HeaderType.Videos, _, _) => getString(R.string.incoming__controls__ongoing__video)
      case HeaderId(HeaderType.Audios, _, _) => getString(R.string.mediaplayer__artist__placeholder)
      case _ => ""
    }
  }

  private def getHeaderCountText(headerId: HeaderId): String = {
    val count = getHeaderCount(headerId)
    if (count > 0) getString(R.string.collection_all, count.toString) else ""
  }

  private def getHeaderCount(headerId: HeaderId): Int = {
    headerId match {
      case HeaderId(HeaderType.Images, _, _) => collectionCursors(Images).fold(0)(_.count)
      case HeaderId(HeaderType.Files, _, _) => collectionCursors(Files).fold(0)(_.count)
      case HeaderId(HeaderType.Links, _, _) => collectionCursors(Links).fold(0)(_.count)
      case HeaderId(HeaderType.Videos, _, _) => collectionCursors(Videos).fold(0)(_.count)
      case HeaderId(HeaderType.Audios, _, _) => collectionCursors(Audios).fold(0)(_.count)
      case _ => 0
    }
  }

  private def shouldBeClickable(headerId: HeaderId): Boolean = {
    val minCount = headerId match {
      case HeaderId(HeaderType.Images, _, _) => AllContent.typeFilter.find(_.msgType == Message.Type.ASSET).flatMap(_.limit).getOrElse(0)
      case HeaderId(HeaderType.Files, _, _) => AllContent.typeFilter.find(_.msgType == Message.Type.ANY_ASSET).flatMap(_.limit).getOrElse(0)
      case HeaderId(HeaderType.Links, _, _) => AllContent.typeFilter.find(_.msgType == Message.Type.RICH_MEDIA).flatMap(_.limit).getOrElse(0)
      case HeaderId(HeaderType.Videos, _, _) => AllContent.typeFilter.find(_.msgType == Message.Type.VIDEO_ASSET).flatMap(_.limit).getOrElse(0)
      case HeaderId(HeaderType.Audios, _, _) => AllContent.typeFilter.find(_.msgType == Message.Type.AUDIO_ASSET).flatMap(_.limit).getOrElse(0)
      case _ => 0
    }
    minCount > 0 && getHeaderCount(headerId) > minCount
  }

  private def getHeaderIcon(headerId: HeaderId): Int = {
    headerId match {
      case HeaderId(HeaderType.Images, _, _) => R.string.glyph__picture
      case HeaderId(HeaderType.Files, _, _) => R.string.glyph__file
      case HeaderId(HeaderType.Links, _, _) => R.string.glyph__link
      case HeaderId(HeaderType.Videos, _, _) => R.string.glyph__video
      case HeaderId(HeaderType.Audios, _, _) => R.string.glyph__record
      case _ => R.string.glyph__file
    }
  }

  override def getItemId(position: Int): Long = {
    getItem(position).map(_.id.str.hashCode).getOrElse(0).toLong
  }

  def closeCursors(): Unit ={
    collectionCursors.foreach(_._2.foreach(_.close()))
  }
}

case class HeaderId(headerType:Int, month: Int = 0, year: Int = 0)

object HeaderType {
  val Images: Int = 0
  val Files: Int = 1
  val Links: Int = 2
  val Today: Int = 3
  val Yesterday: Int = 4
  val MonthName: Int = 5
  val Videos: Int = 6
  val Audios: Int = 7
}

object Header {
  val invalid = HeaderId(-1)
  val mainImages = HeaderId(HeaderType.Images)
  val mainFiles = HeaderId(HeaderType.Files)
  val mainLinks = HeaderId(HeaderType.Links)
  val subToday = HeaderId(HeaderType.Today)
  val subYesterday = HeaderId(HeaderType.Yesterday)
  val mainVideos = HeaderId(HeaderType.Videos)
  val mainAudios = HeaderId(HeaderType.Audios)
}

object CollectionAdapter {

  // TODO: Investigate why we can derive the log tag.
  private implicit val logTag: LogTag = LogTag[CollectionAdapter.type]

  val VIEW_TYPE_IMAGE = 0
  val VIEW_TYPE_FILE = 1
  val VIEW_TYPE_LINK_PREVIEW = 2
  val VIEW_TYPE_SIMPLE_LINK = 3
  val VIEW_TYPE_VIDEO = 4
  val VIEW_TYPE_AUDIO = 5
  val VIEW_TYPE_DEFAULT = VIEW_TYPE_FILE

  case class CollectionHeaderLinearLayout(context: Context, attrs: AttributeSet, defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) {

    def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

    def this(context: Context) =  this(context, null)

    lazy val iconView: GlyphTextView = ViewUtils.getView(this, R.id.gtv_collection_icon)
    lazy val nameView: TextView = ViewUtils.getView(this, R.id.ttv__collection_header__name)
    lazy val countView: TextView = ViewUtils.getView(this, R.id.ttv__collection_header__count)
    lazy val arrowView: GlyphTextView = ViewUtils.getView(this, R.id.gtv__arrow)

    LayoutInflater.from(context).inflate(R.layout.row_collection_header, this, true)
  }

  class CollectionRecyclerNotifier(contentType: ContentType, adapter: CollectionAdapter)
    extends RecyclerNotifier
      with DerivedLogTag {

    override def notifyDataSetChanged(): Unit = {
      if (adapter.contentMode.currentValue.contains(contentType)) {
        debug(l"Will notifyDataSetChanged. contentType: $contentType, current mode is: ${adapter.contentMode.currentValue}")
          adapter.notifyDataSetChanged()
      }
    }

    override def notifyItemRangeInserted(index: Int, length: Int): Unit = {
      if (adapter.contentMode.currentValue.contains(contentType)) {
        debug(l"Will notifyItemRangeInserted. contentType: $contentType, current mode is: ${adapter.contentMode.currentValue}")
        adapter.notifyItemRangeInserted(index, length)
      }
    }

    override def notifyItemRangeChanged(index: Int, length: Int): Unit =
      if (adapter.contentMode.currentValue.contains(contentType)) {
        debug(l"Will notifyItemRangeChanged. contentType: $contentType, current mode is: ${adapter.contentMode.currentValue}")
        adapter.notifyItemRangeChanged(index, length)
      }

    override def notifyItemRangeRemoved(pos: Int, count: Int): Unit =
      if (adapter.contentMode.currentValue.contains(contentType)) {
        debug(l"Will notifyItemRangeRemoved. contentType: $contentType, current mode is: ${adapter.contentMode.currentValue}")
        adapter.notifyItemRangeRemoved(pos, count)
      }
  }

  case class AdapterState(contentType: ContentType, itemCount: Int, loading: Boolean)
}
