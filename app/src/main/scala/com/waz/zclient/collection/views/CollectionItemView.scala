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
package com.waz.zclient.collection.views

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View.OnClickListener
import android.webkit.URLUtil
import android.widget.{FrameLayout, TextView}
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceSignal}
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.collection.controllers.CollectionController
import com.waz.zclient.common.controllers.{AssetsController, BrowserController}
import com.waz.zclient.common.views.ImageAssetDrawable.RequestBuilder
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.common.views.{ImageAssetDrawable, ProgressDotsDrawable, RoundedImageAssetDrawable}
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.assets.{AssetActionButton, AudioAssetPartView, FileAssetPartView}
import com.waz.zclient.messages.parts.{EphemeralPartView, WebLinkPartView}
import com.waz.zclient.messages.{ClickableViewPart, MsgPart}
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.ui.text.GlyphTextView
import com.waz.zclient.utils.Time.TimeStamp
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

trait CollectionItemView extends ViewHelper with EphemeralPartView with DerivedLogTag {
  protected lazy val civZms = inject[Signal[ZMessaging]]
  protected lazy val messageActions = inject[MessageActionsController]
  protected lazy val collectionController = inject[CollectionController]

  val messageData: SourceSignal[MessageData] = Signal()

  val messageAndLikesResolver = for {
    z <- civZms
    mId <- messageData.map(_.id)
    message <- z.messagesStorage.signal(mId)
    msgAndLikes <- Signal.future(z.msgAndLikes.combineWithLikes(message))
  } yield msgAndLikes

  messageAndLikesResolver.disableAutowiring()

  this.onLongClick {
    messageData.currentValue.foreach(collectionController.openContextMenuForMessage ! _)
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    messageAndLikesResolver.currentValue.exists(messageActions.showDialog(_, fromCollection = true))
  }
}

trait CollectionNormalItemView extends CollectionItemView with ClickableViewPart{
  lazy val messageTime: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__time)
  lazy val messageUser: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__user_name)

  var content = Option.empty[MessageContent]

  messageData.flatMap(msg => civZms.map(_.usersStorage).flatMap(_.signal(msg.userId))).on(Threading.Ui) {
    user =>
      messageUser.setText(user.name)
      messageUser.setTextColor(AccentColor(user.accent).color)
  }

  messageData
      .map(_.time.instant)
      .map(TimeStamp(_, showWeekday = false).string)
      .onUi(messageTime.setText)

  messageAndLikesResolver.on(Threading.Ui) { mal => set(mal, content) }

  onClicked { _ =>
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      md <- messageData.head
    } collectionController.clickedMessage ! md
  }

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    this.content = content
    this.messageData ! messageData
  }
}

class CollectionImageView(context: Context) extends AspectRatioImageView(context) with CollectionItemView {
  setId(R.id.collection_image_view)

  override val tpe: MsgPart = MsgPart.Image
  messageAndLikesResolver.onUi(set(_, None))

  val onClicked = EventStream[Unit]()

  private val controller = inject[AssetsController]
  private  val assets = inject[AssetsController]

  object CollectionImageView {
    val CornerRadius = 10
  }
  import CollectionImageView._

  val padding = getResources.getDimensionPixelSize(R.dimen.collections__image_padding)
  setCropToPadding(true)
  setPadding(padding, padding, padding, padding)

  val image: Signal[ImageSource] = messageData.map(md => WireImage(md.assetId))

  private val imageDrawable =
    new RoundedImageAssetDrawable(image, scaleType = ImageAssetDrawable.ScaleType.CenterCrop,
      cornerRadius = CornerRadius, request = RequestBuilder.Single, background = Some(new ProgressDotsDrawable))

  ephemeralDrawable(imageDrawable).onUi { setImageDrawable }

  //this.onClick {
  //  import Threading.Implicits.Ui
  //  for {
  //    false <- expired.head
  //    md <- messageData.head
  //  } {
  //    collectionController.clickedMessage ! md
  //    onClicked ! (())
  //  }
  //}
  this.onClick {
    message.head.map(assets.showSingleImage(_, this))(Threading.Ui)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    setAspectRatio(1)
    this.setWidth(width)
    this.setHeight(width)
    this.messageData ! messageData
  }
}

class CollectionVideoView(context: Context) extends FrameLayout(context) with CollectionItemView {

  private val controller = inject[AssetsController]
  private val asset = controller.assetSignal(message)

  inflate(R.layout.collection_simple_video)

  private val assetActionButton: AssetActionButton = findById(R.id.action_button)
  private val gtvAction: GlyphTextView = findById(R.id.gtvAction)
  private val aspectRatioImageView: AspectRatioImageView = findViewById(R.id.aspectRatioImageView)

  override val tpe: MsgPart = MsgPart.VideoAsset
  messageAndLikesResolver.onUi(set(_, None))

  val onClicked = EventStream[Unit]()

  object CollectionVideoView {
    val CornerRadius = 10
  }

  import CollectionVideoView._

  private val padding = getResources.getDimensionPixelSize(R.dimen.collections__image_padding)
  aspectRatioImageView.setCropToPadding(true)
  setPadding(padding, padding, padding, padding)

  private val image: Signal[ImageSource] = messageData.map(md => WireImage(md.assetId))

  private val imageDrawable =
  new RoundedImageAssetDrawable(image, scaleType = ImageAssetDrawable.ScaleType.CenterCrop,
    cornerRadius = CornerRadius, request = RequestBuilder.Single, background = Some(new ProgressDotsDrawable))

  ephemeralDrawable(imageDrawable).onUi {
    aspectRatioImageView.setImageDrawable
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    aspectRatioImageView.setAspectRatio(1)
    this.setWidth(width)
    this.setHeight(width)
    this.messageData ! messageData
  }

  this.onClick {
    asset.currentValue foreach { case (a, _) =>
      controller.openFile(a)
    }
  }
}

class CollectionAudioAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends AudioAssetPartView(context, attrs, style) with CollectionNormalItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override def layoutList = {
    case _: CollectionAudioAssetPartView=> R.layout.collection_message_audio_asset_content
  }

  this.onClick {
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      ds <- deliveryState.head
    } assetActionButton.onClicked ! ds
  }

  assetActionButton.onClicked(_ => onClicked ! (()))
  setWillNotDraw(true)
}

class CollectionWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends WebLinkPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def inflate() = inflate(R.layout.collection_message_part_weblink_content)
}

class CollectionFileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FileAssetPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def layoutList = {
    case _: CollectionFileAssetPartView => R.layout.collection_message_file_asset_content
  }

  this.onClick{
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      ds <- deliveryState.head
    } assetActionButton.onClicked ! ds
  }

  assetActionButton.onClicked(_ => onClicked ! (()))
  setWillNotDraw(true)
}

class CollectionSimpleWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context: Context, attrs: AttributeSet, style: Int) with CollectionNormalItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val browser = inject[BrowserController]

  override val tpe: MsgPart = MsgPart.WebLink

  inflate(R.layout.collection_message_part_simple_link_content)

  lazy val urlTextView: TextView    = findById(R.id.ttv__row_conversation__link_preview__url)

  val urlText =
    message.map(msg => msg.content.find(c => URLUtil.isValidUrl(c.content)).map(_.content).getOrElse(msg.contentString))

  urlText.on(Threading.Ui){ urlTextView.setText }

  onClicked { _ =>
    import Threading.Implicits.Ui
    for {
      false <- expired.head
      text <- urlText.head
    } browser.openUrl(AndroidURIUtil.parse(text))
  }
  registerEphemeral(urlTextView)
}

case class CollectionItemViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view){

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    view.setMessageData(messageData, content)
  }

  def setMessageData(messageData: MessageData): Unit = {
    setMessageData(messageData, None)
  }
}

case class CollectionImageViewHolder(view: CollectionImageView, listener: OnClickListener)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {
  view.onClicked { _ =>
    listener.onClick(view)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    view.setMessageData(messageData, width, color)
  }
}

case class CollectionVideoViewHolder(view: CollectionVideoView, listener: OnClickListener)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {
  view.onClicked { _ =>
    listener.onClick(view)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    view.setMessageData(messageData, width, color)
  }
}



