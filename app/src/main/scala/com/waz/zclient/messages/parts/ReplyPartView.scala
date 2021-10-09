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
package com.waz.zclient.messages.parts

import android.content.Context
import android.graphics.{Color, Typeface}
import android.util.{AttributeSet, TypedValue}
import android.view.View.OnLongClickListener
import android.view.{Gravity, View, ViewGroup}
import android.widget.{ImageView, LinearLayout, TextView}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.ColorUtils
import com.waz.api.{IConversation, Message}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetData, MessageContent, MessageData}
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events._
import com.waz.zclient.common.controllers.AssetsController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.ReplyView.{MessageReplyBackgroundDrawable, ReplyBackgroundDrawable}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessageViewLayout.PartDesc
import com.waz.zclient.messages.MsgPart._
import com.waz.zclient.messages._
import com.waz.zclient.paintcode.WireStyleKit
import com.waz.zclient.ui.text.{LinkTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils.{getString, getStyledColor}
import com.waz.zclient.utils.Time.DateTimeStamp
import com.waz.zclient.utils.{AliasSignal, RichTextView, RichView, StringUtils, UiStorage}
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.Instant

abstract class ReplyPartView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with EphemeralPartView
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  private lazy val assetsController = inject[AssetsController]
  private lazy val convController = inject[ConversationController]

  setOrientation(LinearLayout.VERTICAL)

  inflate(tpe match {
    case Reply(Image) | Reply(VideoAsset) => R.layout.message_reply_content_video
    case _                                => R.layout.message_reply_content_outer
  })

  protected val name: Option[TextView] = Option(findById[TextView](R.id.name))
  protected val timestamp: Option[TextView] = Option(findById[TextView](R.id.timestamp))
  private val content: Option[ViewGroup] = Option(findById[ViewGroup](R.id.content))
  private val container: Option[ViewGroup] = Option(findById[ViewGroup](R.id.quote_container))
  private val contentLayout: Option[ViewGroup] = Option(findById[ViewGroup](R.id.content_layout))
  private lazy val messageViewFactory = new MessageViewFactory()

  val onQuoteClick: SourceStream[Unit] = EventStream[Unit]

  val quoteView = tpe match {
    case Reply(Text)       => Some(inflate(R.layout.message_reply_content_text,     addToParent = false))
    case Reply(Image)      => Some(inflate(R.layout.message_reply_content_image,    addToParent = false))
    case Reply(Location)   => Some(inflate(R.layout.message_reply_content_generic,  addToParent = false))
    case Reply(AudioAsset) => Some(inflate(R.layout.message_reply_content_generic,  addToParent = false))
    case Reply(VideoAsset) => Some(inflate(R.layout.message_reply_content_image,    addToParent = false))
    case Reply(FileAsset)  => Some(inflate(R.layout.message_reply_content_generic,  addToParent = false))
    case Reply(Unknown)    => Some(inflate(R.layout.message_reply_content_unknown,  addToParent = false))
    case _ => None
  }
  content.foreach(viewGroup => quoteView.foreach(viewGroup.addView))

  container.foreach(_.setLayerType(View.LAYER_TYPE_SOFTWARE, null))
  container.foreach(_.setBackground(new ReplyBackgroundDrawable(getStyledColor(R.attr.replyBorderColor), getStyledColor(R.attr.wireBackgroundCollection))))

  protected val quotedMessage: SourceSignal[MessageData] with NoAutowiring = Signal[MessageData]()
  protected val quotedAsset: Signal[Option[AssetData]] =
    quotedMessage.map(_.assetId).flatMap(assetsController.assetSignal).collect {
      case (asset, _) => Option(asset)
    }.orElse(Signal.const(Option.empty[AssetData]))

  def setQuote(quotedMessage: MessageData): Unit = {
    verbose(l"setQuote: $quotedMessage")
    this.quotedMessage ! quotedMessage
  }

  private val quoteComposer =
    quotedMessage
      .map(_.userId)
      .flatMap(inject[UsersController].user)

  (for {
    currentUser <- quoteComposer
    conv <- convController.currentConv
    aliasUser <- AliasSignal(conv.id, currentUser.id)(inject[UiStorage])
  } yield currentUser -> aliasUser)
    .onUi { parts =>

      val userData = parts._1
      val aliasData = parts._2

      val showContent = if(userData.isWireBot) {
        userData.name.str
      } else {
        aliasData.map(_.getAliasName).filter(_.nonEmpty).getOrElse(userData.getShowName)
      }
      name.foreach(_.setText(showContent))
    }

  quotedMessage
    .map(_.time.instant)
    .map(getTimeStamp)
    .onUi(timeStr => timestamp.foreach(_.setText(timeStr)))

  quotedMessage.map(!_.editTime.isEpoch).onUi { edited =>
    name.foreach(_.setEndCompoundDrawable(if (edited) Some(WireStyleKit.drawEdit) else None, getStyledColor(R.attr.wirePrimaryTextColor)))
  }

  container.foreach(_.onClick(onQuoteClick ! {()}))
  setOnLongClickListener(new OnLongClickListener {
    override def onLongClick(v: View): Boolean = {
      contentLayout.exists(_.performLongClick())
    }
  })

  private def getTimeStamp(instant: Instant) = {
    val timestamp = DateTimeStamp(instant)
    getString(
      if (timestamp.isSameDay) R.string.quote_timestamp_message_time
      else R.string.quote_timestamp_message_date,
      timestamp.string
    )
  }

  private def getSideBarColor(self: Boolean): Int = {
    if (self) {
      ColorUtils.getAttrColor(getContext,R.attr.conversationReplySentSideColor)
    }else {
      ColorUtils.getAttrColor(getContext,R.attr.conversationReplyReceivedSideColor)
    }
  }

  private def getBackgroundColor(self: Boolean): Int = {
    if (self) {
      ColorUtils.getAttrColor(getContext,R.attr.conversationReplySentColor)
    }else {
      ColorUtils.getAttrColor(getContext,R.attr.conversationReplyReceivedColor)
    }
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    quotedMessage.map(_.time.instant).head.foreach(getTimeStamp)(Threading.Ui)

    opts.map(_.isSelf).foreach { self =>
      container.foreach(_.setBackground(new MessageReplyBackgroundDrawable(Color.TRANSPARENT
        , getBackgroundColor(self), getSideBarColor(self))))
    }

    val msgData = msg.message

    val isOneToOne = opts.fold(false)(_.convType == IConversation.Type.ONE_TO_ONE)

    opts.foreach { opts =>
      val (_, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, part, opts)
      setItemBackground(tpe = tpe, bgView = this, isSelf = opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = false)
    }

    contentLayout.foreach{viewGroup =>
      viewGroup.removeAllViews()
      (if(msgData.msgType == Message.Type.RICH_MEDIA) {
        //val contentWithOG = msgData.content.filter(_.openGraph.isDefined)
        //if(contentWithOG.size == 1 && msgData.content.size == 1)
        //  msgData.content.map(content => PartDesc(MsgPart(content.tpe), Some(content)))
        //else
        //  Seq(PartDesc(MsgPart(Message.Type.TEXT, isOneToOne))) ++ contentWithOG.map(content => PartDesc(MsgPart(content.tpe), Some(content))).filter(_.tpe == WebLink)
        if(msgData.content.size > 1) {
          msgData.content.map(content => PartDesc(MsgPart(content.tpe), Some(content))).find(_.tpe == WebLink)
            .fold(Seq(PartDesc(MsgPart(Message.Type.TEXT, MsgPart.Text, isOneToOne))))(Seq(_))
        } else {
          msgData.content.map(content => PartDesc(MsgPart(content.tpe), Some(content)))
        }
      } else Seq(PartDesc(MsgPart(msgData.msgType, isOneToOne))))
        .zipWithIndex foreach { case (PartDesc(tpe, _), index) if tpe != null =>
        val view = messageViewFactory.get(tpe, this)
        view.setFocusable(false)
        view.set(msg = msg, prev = prev, next = next, part = part, opts = opts, adapter = adapter)
        view.setBackgroundColor(Color.TRANSPARENT)
        view.setMargin(0, 0, 0, 0)
        Option(viewGroup.getLayoutParams).filter(_.isInstanceOf[LinearLayout.LayoutParams])
          .map(_.asInstanceOf[LinearLayout.LayoutParams])
          .map { params =>
            params.gravity = if(opts.fold(false)(_.isSelf)) Gravity.END else Gravity.START
            params
          }.foreach(viewGroup.setLayoutParams)
        viewGroup.addView(view, index, Option(view.getLayoutParams).getOrElse(generateDefaultLayoutParams()))
        viewGroup.setOnLongClickListener(new View.OnLongClickListener {
          override def onLongClick(v: View): Boolean = {
            Option(getParent).map(_.asInstanceOf[View]).exists(_.performLongClick())
          }
        })
        viewGroup.setMarginTop(0)
      }
    }
  }
}

class  TextReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) with MentionsViewPart {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Text)

  private lazy val textView = findById[LinkTextView](R.id.text)

  val textSizeRegular = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__regular_small)
  val textSizeEmoji = context.getResources.getDimensionPixelSize(R.dimen.wire__text_size__huge)

  //TODO: Merge duplicated stuff from TextPartView
  quotedMessage.onUi { message =>
    val textSize = if (message.msgType == Message.Type.TEXT_EMOJI_ONLY) textSizeEmoji else textSizeRegular

    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)

    val text = message.contentString
    val offset = 0
    val mentions = message.content.flatMap(_.mentions)

    if (mentions.isEmpty) {
      textView.setTransformedText(text)
      textView.markdownQuotes()
    } else {
      val (replaced, mentionHolders) = TextPartView.replaceMentions(text, mentions, offset)

      textView.setTransformedText(replaced)
      textView.markdownQuotes()

      val updatedMentions = TextPartView.updateMentions(textView.getText.toString, mentionHolders, offset)

      val spannable = TextPartView.restoreMentionHandles(textView.getText, mentionHolders)
      addMentionSpans(
        spannable,
        updatedMentions,
        None,
        getStyledColor(R.attr.wirePrimaryTextColor)
      )
      textView.setText(spannable)
    }
  }

}


class ImageReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Image)

  private val textView: Option[TextView] = Option(findById[TextView](R.id.text))
  private val imageContainer:Option[ImageView] = Option(findById[ImageView](R.id.content_imageView))

  private val imageSignal: Signal[ImageSource] = quotedMessage.map(m => WireImage(m.assetId))

  textView.foreach { view =>
    val leftDrawable = getResources.getDrawable(R.drawable.ic_message_reply_image, context.getTheme)
    leftDrawable.setBounds(0, 0, leftDrawable.getIntrinsicWidth, leftDrawable.getIntrinsicHeight)
    view.setCompoundDrawablesRelative(leftDrawable, null, null, null)
    view.setText(R.string.conversation_reply_content_img)
    if(ThemeUtils.isDarkTheme(context)){
      view.setTextColor(Color.parseColor("#A1A1A1"))
    }
    else{
      view.setTextColor(Color.parseColor("#666666"))
    }
  }

  imageContainer.foreach(_.setImageDrawable(new ImageAssetDrawable(imageSignal, ScaleType.CenterCrop, RequestBuilder.Regular)))
}

class LocationReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Location)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedMessage.map(_.location.map(_.getName).getOrElse("")).onUi(textView.setText)
  textView.setStartCompoundDrawable(Some(WireStyleKit.drawLocation), getStyledColor(R.attr.wirePrimaryTextColor))
}

class FileReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(FileAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedAsset.map(_.flatMap(_.name).getOrElse("")).onUi(textView.setText)
  textView.setStartCompoundDrawable(Some(WireStyleKit.drawFile), getStyledColor(R.attr.wirePrimaryTextColor))
}


class VideoReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(VideoAsset)

  private val textView: Option[TextView] = Option(findById[TextView](R.id.text))
  private val imageContainer:Option[ImageView] = Option(findById[ImageView](R.id.content_imageView))

  private val imageSignal: Signal[ImageSource] = quotedAsset.map(_.flatMap(_.previewId)).collect {
    case Some(aId) => WireImage(aId)
  }

  textView.foreach { view =>
    val leftDrawable = getResources.getDrawable(R.drawable.ic_message_reply_video, context.getTheme)
    leftDrawable.setBounds(0, 0, leftDrawable.getIntrinsicWidth, leftDrawable.getIntrinsicHeight)
    view.setCompoundDrawablesRelative(leftDrawable, null, null, null)
    view.setText(R.string.conversation_reply_content_video)
  }

  imageContainer.foreach(_.setImageDrawable(new ImageAssetDrawable(imageSignal, ScaleType.CenterCrop, RequestBuilder.Regular)))
}

class AudioReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(AudioAsset)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  quotedAsset.map {
    case Some(AssetData.WithDuration(d)) => Some(d)
    case _                               => None
  }.map(_.fold(getString(R.string.reply_message_type_audio))(d => StringUtils.formatTimeSeconds(d.getSeconds)))
    .onUi(textView.setText)


  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)

    opts.foreach { tempOpts =>
      val leftDrawable = getResources.getDrawable(R.drawable.ic_message_reply_audio, context.getTheme)
      leftDrawable.setBounds(0, 0, leftDrawable.getIntrinsicWidth, leftDrawable.getIntrinsicHeight)
      textView.setCompoundDrawablesRelative(leftDrawable, null, null, null)
    }
  }
}

class UnknownReplyPartView(context: Context, attrs: AttributeSet, style: Int) extends ReplyPartView(context: Context, attrs: AttributeSet, style: Int) {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  override def tpe: MsgPart = Reply(Unknown)

  private lazy val textView = findById[TypefaceTextView](R.id.text)

  name.foreach(_.setVisibility(View.GONE))
  timestamp.foreach(_.setVisibility(View.GONE))
  textView.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__regular)), Typeface.ITALIC)
}
