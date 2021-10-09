/**
 * Secret
 * Copyright (C) 2019 Secret
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

import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.{Gravity, View}
import android.widget.{ImageView, TextView, Toast}
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.httpapi.{ConversationApiService, SimpleHttpListener}
import com.jsy.common.model.HttpResponseBaseModel
import com.jsy.common.popup.JoinGroupPopUpWindow
import com.jsy.common.popup.JoinGroupPopUpWindow.JoinGroupAllowCallBack
import com.waz.api.Message.Part
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.GenericContent.LinkPreview
import com.waz.model.GenericMessage.TextMessage
import com.waz.model._
import com.waz.service.messages.MessageAndLikes
import com.waz.sync.client.OpenGraphClient.OpenGraphData
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.URI
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType, State}
import com.waz.zclient.common.views.ImageController.{DataImage, ImageUri}
import com.waz.zclient.common.views.{ImageAssetDrawable, ProgressDotsDrawable}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.JoinGroupTipsDialogFragment.ClickListener
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.parts.WebLinkPartView._
import com.waz.zclient.messages.{ClickableViewPart, JoinGroupTipsDialogFragment, MessageView, MsgPart}
import com.waz.zclient.ui.text.LinkTextView
import com.waz.zclient.ui.text.LinkTextView.{OnClickSpanListener, OnLongClickSpanListener}
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}
import org.json.JSONObject
import org.jsoup.Jsoup

import java.io.IOException
import java.util.regex.Pattern
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.util.Success

class WebLinkPartView(context: Context, attrs: AttributeSet, style: Int)
  extends CardView(context, attrs, style)
    with ClickableViewPart
    with ViewHelper
    with EphemeralPartView
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  implicit val ec: ExecutionContext = Threading.Ui

  override val tpe: MsgPart = MsgPart.WebLink

  lazy val browser = inject[BrowserController]

  lazy val conversationController = inject[ConversationController]

  val activity = inject[Activity]

  lazy val ttvOrgTextParent: LinkTextView = findViewById(R.id.ttvOrgTextParent)

  lazy val vgWebContent: View = findById(R.id.vgWebContent)

  lazy val ttvWebTitle: TextView = findById(R.id.ttvWebTitle)

  lazy val ttvWebDomain: TextView = findById(R.id.ttvWebDomain)

  lazy val ivImg: ImageView = findById(R.id.ivImg)

  lazy val vGoneWhenIsOnlyUrl: View = findById(R.id.vGoneWhenIsOnlyUrl)

  private val content = Signal[MessageContent]()

  private val titles = mutable.HashMap[String,String]()

  def inflate(): Unit = inflate(R.layout.message_part_weblink_content)

  inflate()

  val linkPreview = for {
    msg <- message
    ct <- content
  } yield {
    val index = msg.content.indexOf(ct)
    val linkIndex = msg.content.take(index).count(_.tpe == Part.Type.WEB_LINK)
    msg.protos.lastOption flatMap {
      case TextMessage(_, _, previews, _, _) if index >= 0 && previews.size > linkIndex => Some(previews(linkIndex))
      case _ => None
    }

  }


  val image = for {
    ct <- content
    lp <- linkPreview
  } yield (ct.openGraph, lp) match {
    case (_, Some(LinkPreview.WithAsset(asset))) =>
      Some(DataImage(asset))
    case (Some(OpenGraphData(_, _, Some(uri), _, _)), None) =>
      Some(ImageUri(uri))
    case _ =>
      None
  }

  val dimensions = content.zip(linkPreview) map {
    case (_, Some(LinkPreview.WithAsset(AssetData.WithDimensions(d)))) => d
    case (ct, _) => Dim2(ct.width, ct.height)
  }

  val openGraph = content.zip(linkPreview) map {
    case (_, Some(LinkPreview.WithDescription(t, s))) => OpenGraphData(t, s, None, "", None)
    case (ct, _) => ct.openGraph.getOrElse(OpenGraphData.Empty)
  }

  val title = openGraph.map(_.title)
  val urlText = content.map(c => StringUtils.trimLinkPreviewUrls(c.contentAsUri))
  val hasImage = image.map(_.isDefined)

  private val dotsDrawable = new ProgressDotsDrawable
  private val imageDrawable = new ImageAssetDrawable(
    image.collect {
      case Some(im) =>
        im
    }, scaleType = ScaleType.CenterCrop, request = RequestBuilder.Single)

  registerEphemeral(ttvWebTitle)
  registerEphemeral(ivImg, imageDrawable)

  ivImg.setBackground(dotsDrawable)

  hasImage.on(Threading.Ui) { has =>
    ivImg.setVisible(has)
  }

  imageDrawable.state.map {
    case State.Loading(_) =>
      dotsDrawable
    case _ =>
      null
  }.on(Threading.Ui) {
    ivImg.setBackground
  }

  title.on(Threading.Ui) {
    text =>

      if (!TextUtils.isEmpty(text)) {
        ttvWebTitle.setText(text)
      } else {
        content.map(u => u.content).on(Threading.Background) {
          url =>
            ttvWebTitle.setTag(url)
            val cachedTitles = titles.get(url)
            if (cachedTitles.isDefined) {
              showWebTitle(url, cachedTitles.get)
            } else {
              try {
                Option(Jsoup.connect(url).get)
                  .flatMap(it => Option(it.select("head")))
                  .flatMap(it => Option(it.first()))
                  .flatMap(it => Option(it.select("title")))
                  .flatMap(it => Option(it.first()))
                  .flatMap(it => Option(it.text()))
                  .foreach { it =>
                    titles.put(url, it)
                    showWebTitle(url, it)
                  }
              } catch {
                case e: IOException =>
                  showWebTitle(url, "");
              }
            }
        }
      }
  }

  def showWebTitle(url: String, titleStr: String): Unit = {
    ttvWebTitle.getContext.asInstanceOf[Activity].runOnUiThread(new Runnable {
      override def run(): Unit = {
        if (!TextUtils.isEmpty(url) && url.equals(ttvWebTitle.getTag)) {
          ttvWebTitle.setText(titleStr);
        }
      }
    })
  }

  urlText.on(Threading.Ui) { fullUrl =>

    val pattern = Pattern.compile("[a-zA-Z0-9][-a-zA-Z0-9]{0,62}(\\.[a-zA-Z0-9][-a-zA-Z0-9]{0,62})+\\.?", Pattern.CASE_INSENSITIVE)
    val matcher = pattern.matcher(fullUrl)
    if (matcher.find()) {
      ttvWebDomain.setText(matcher.group())
    } else {
      ttvWebDomain.setText(fullUrl)
    }
  }

  private var popUpWindow: JoinGroupPopUpWindow = _

  onClicked { _ =>
    if (expired.currentValue.forall(_ == false)) {
      content.currentValue foreach {
        c =>
          if (!TextUtils.isEmpty(c.content)) {
            clickSpanUrl(c.content)
          }
      }
    }
  }


  private def clickSpanUrl(urlContent: String): Unit = {
    if (!StringUtils.isBlank(urlContent) && isGroupShareLink(urlContent)) {
      if (popUpWindow == null) popUpWindow = new JoinGroupPopUpWindow(context, -1, -1)
      popUpWindow.showAtLocation(activity.getWindow.getDecorView, Gravity.CENTER, 0, 0)
      popUpWindow.setCallBack(new JoinGroupAllowCallBack {
        override def clickAllow(): Unit = {
          val id = urlContent.substring(urlContent.length - idLength, urlContent.length)
          if (!StringUtils.isBlank(id)) {
            requestCheckGroupStatus(id)
          }
        }

        override def clickRefuse(): Unit = {

        }
      })
    } else if (!TextUtils.isEmpty(urlContent)) {
      browser.openUrl(urlContent)
    }
  }

  private def requestCheckGroupStatus(groupInviteCode: String): Unit = {
    ConversationApiService.getInstance().checkGroupInviteUrl(groupInviteCode, new SimpleHttpListener[HttpResponseBaseModel] {

      override def onFail(code: Int, err: String): Unit = {
        requestJoinGroup(groupInviteCode)
      }

      override def onSuc(data: HttpResponseBaseModel, orgJson: String): Unit = {
        val code = data.getCode
        if (code == 2003 || code == 2004 || code == 2005) {
          showTipsDialog(code, groupInviteCode)
        } else if (code == 2001 ) {
          val resultJsonObject = new JSONObject(orgJson)
          val dataJSONObject = resultJsonObject.optJSONObject("data")
          if (dataJSONObject != null) {
            Toast.makeText(context, getResources.getString(R.string.conversation_join_group_already_in), Toast.LENGTH_SHORT).show()
            val conversationId = dataJSONObject.optString("conv")
            if (StringUtils.isNotBlank(conversationId)) {
              conversationController.getByRemoteId(RConvId(conversationId)) onComplete {
                case Success(Some(conversationData)) =>
                  conversationController.selectConv(conversationData.id, ConversationChangeRequester.START_CONVERSATION)
                case _ =>

              }
            }
          }
        } else {
          Toast.makeText(context, data.getMsg, Toast.LENGTH_SHORT).show()
        }
      }
    })
  }

  private def showTipsDialog(code: Int, groupInviteCode: String): Unit = {
    val level = if (code == 2004) "warring" else if (code == 2005) "error" else ""
    if (TextUtils.isEmpty(level)) {
      requestJoinGroup(groupInviteCode)
    } else {
      val dialogFragment = JoinGroupTipsDialogFragment.apply(level)
      dialogFragment.setClickListener(new ClickListener {
        override def confirm(): Unit = {
          requestJoinGroup(groupInviteCode)
        }
      })

      Option(getContext).filter(_.isInstanceOf[AppCompatActivity])
        .flatMap(it => Option(it.asInstanceOf[AppCompatActivity]))
        .flatMap(it => Option(it.getSupportFragmentManager))
        .foreach(it => dialogFragment.show(it, "JoinGroupTipsDialogFragment"))
    }
  }

  private def requestJoinGroup(groupInviteCode: String): Unit = {
    ConversationApiService.getInstance().joinGroup(groupInviteCode, new SimpleHttpListener[HttpResponseBaseModel] {
      override def onSuc(data: HttpResponseBaseModel, orgJson: String): Unit = {
        val resultJsonObject = new JSONObject(orgJson)
        val code = resultJsonObject.optInt("code")
        if (code == 2002) {
          Toast.makeText(context, getResources.getString(R.string.conversation_join_group_closed), Toast.LENGTH_SHORT).show()
        } else if (code == 2005) {
          showTipsDialog(code, "")
        } else if (code == 2001){
          val dataObj = resultJsonObject.optJSONObject("data")
          if (dataObj != null) {
            val convRId = dataObj.optString("conv")
            if (!StringUtils.isBlank(convRId)) {
              Toast.makeText(context, getResources.getString(R.string.conversation_join_group_already_in), Toast.LENGTH_SHORT).show()
              conversationController.getByRemoteId(RConvId(convRId)) onComplete {
                case Success(Some(conversationData)) =>
                  conversationController.selectConv(conversationData.id, ConversationChangeRequester.START_CONVERSATION)
                case _ =>
              }
            }
          }
        }else {
          val conversationId = resultJsonObject.optString("conversation")
          if (!StringUtils.isBlank(conversationId)){
            conversationController.needJumpNewConvId ! conversationId
          }
        }
      }
    })
  }

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opts: Option[MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opts, adapter)
    part foreach {
      content ! _
    }
    verbose(l"set $msg.message.content")

    opts.foreach { opts =>
      val isSameSide@(preIsSameSide, nextIsSameSide) = MessageView.latestIsSameSide(msg, prev, next, opts)
      setItemBackground(tpe = tpe, bgView = this, isSelf = opts.isSelf, nextIsSameSide = nextIsSameSide, isRepliedChild = false)

      if (opts.isSelf) {
        vgWebContent.setBackgroundResource(R.drawable.shape_web_content_bg_self_side)
        ttvWebDomain.setTextColor(ColorUtils.getAttrColor(getContext, R.attr.conversationWebLinkSelfTextColor))
      } else {
        vgWebContent.setBackgroundResource(R.drawable.shape_web_content_bg_other_side)
        ttvWebDomain.setTextColor(ColorUtils.getAttrColor(getContext, R.attr.conversationWebLinkOtherTextColor))
      }

      var firstUrl = ""
      msg.message.content.find(_.tpe == Part.Type.WEB_LINK).foreach { firstLinkContent =>
        firstUrl = firstLinkContent.content
        content ! firstLinkContent
      }

      ttvOrgTextParent.setText(msg.message.content.map(_.content).mkString(" "))
      ttvOrgTextParent.setTextLink(new OnClickSpanListener {
        override def onClickSpanLink(link: String): Unit = {
          if (!TextUtils.isEmpty(link)) {
            clickSpanUrl(link)
          }
        }
      }, new OnLongClickSpanListener {
        override def onLongClickSpanLink(link: String): Unit = {
          getParent.asInstanceOf[View].performLongClick()
        }
      })
    }

  }

}

object WebLinkPartView {

  import java.util.regex.Pattern

  private val groupHosts = Array("isecret.im", "www.isecret.im", "secret.chat")
  val idLength = 10
  val splitPrefix = "/"

  def isGroupShareLink(content: String): Boolean = {
    val parseUri = URI.parse(content)
    val uriHost = parseUri.getHost

    if (uriHost != null && groupHosts.contains(uriHost) && content.length > idLength) {
      if (content.contains(splitPrefix)) {
        val spiltArray = content.split(splitPrefix)
        //val result = content.substring(content.length - idLength, content.length)
        val result = spiltArray.apply(spiltArray.length - 1)
        val regex = "^[A-Za-z0-9]{10}$"
        Pattern.matches(regex, result)
      } else false
    } else false


  }

}

