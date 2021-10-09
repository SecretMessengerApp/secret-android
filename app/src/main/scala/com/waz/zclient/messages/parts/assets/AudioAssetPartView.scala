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
package com.waz.zclient.messages.parts.assets

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.AttributeSet
import android.view.View
import android.widget.{FrameLayout, LinearLayout}
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.views.AudioWaveProgressView
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.api.AssetStatus
import com.waz.api.impl.AudioOverview
import com.waz.content.WireContentProvider.CacheUri
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AssetMetaData.Audio
import com.waz.model.{MessageContent, MessageData}
import com.waz.service.assets.{AudioLevels, MetaDataRetriever}
import com.waz.service.conversation.ConversationsUiService
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.R
import com.waz.zclient.log.LogUI.{verbose, _}
import com.waz.zclient.messages.{HighlightViewPart, MessageView, MsgPart, UsersController}
import com.waz.zclient.ui.views.CircleView
import com.waz.zclient.utils.StringUtils
import org.threeten.bp.Duration

import scala.util.{Failure, Success, Try}

class AudioAssetPartView(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style) with PlayableAsset with FileLayoutAssetPart with HighlightViewPart with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  override val tpe: MsgPart = MsgPart.AudioAsset

  //private val progressBar: SeekBar = findById(R.id.progress)
  private val progressBar: AudioWaveProgressView = findById(R.id.voice_progress)
  val audioContent: LinearLayout = findById(R.id.content)
  private val unReadDot: CircleView = findById(R.id.unread_dot)

  val users = inject[UsersController]
  private lazy val messagesConvUi = inject[Signal[ConversationsUiService]]
  //message.map(_.userId).flatMap(users.accentColor).on(Threading.Ui) { c =>
  //  //progressBar.setColor(c.color)
  //}

  (for {
    resultColor <- message.map(_.userId).flatMap(users.accentColor)
    resultAsset <- asset.map(assets => assets._1)
  } yield resultColor -> resultAsset).onUi { params =>
    progressBar.setAccentColor(params._1.color)
    params._2.metaData match {
      case Some(x) =>
        verbose(l"33 metaData has Data")
        x match {
          case Audio(duration, loudness) =>
            loudness match {
              case Some(x) =>
                progressBar.onPlaybackStarted(AudioOverview(Option(x.levels)).getLevels(AudioWaveProgressView.MAX_NUM_OF_LEVELS))
              case _ =>
                progressBar.onPlaybackStarted(new Array[Float](AudioWaveProgressView.MAX_NUM_OF_LEVELS))
            }
            progressBar.onPlaybackStopped(duration.toMillis)
          case _ =>
            verbose(l"44no audio")
            progressBar.onPlaybackStarted(new Array[Float](AudioWaveProgressView.MAX_NUM_OF_LEVELS))
            progressBar.onPlaybackStopped(0)
        }
      case _ =>
        import scala.concurrent.ExecutionContext.Implicits.global
        val uri = params._2.source.getOrElse(CacheUri(params._2.cacheKey, context))
        verbose(l"33 metaData isEmpty  uri:$uri, AndroidURIUtil.unwrap(uri):${AndroidURIUtil.unwrap(uri)}")
        lazy val loudness = AudioLevels(context).createAudioOverview(uri, params._2.mime)
          .recover { case _ => warn(l"Failed to generate loudness levels for audio asset: ${params._2.id}"); None }.future
        lazy val duration = MetaDataRetriever(context, AndroidURIUtil.unwrap(uri)) { r =>
          val str = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          Try(Duration.ofMillis(str.toLong)).toOption
        }.recover { case _ => warn(l"Failed to extract duration for audio asset: ${params._2.id}"); None }

        (for {
          d <- duration
          l <- loudness
        } yield {
          (d, l)
        }).onComplete {
          case Failure(exception) =>
            verbose(l"55 case Failure(exception)")
          case Success(value) =>
            verbose(l"55 case Success(value)")
            val handler = getHandler
            handler.post(new Runnable {
              override def run(): Unit = {
                value._2 match {
                  case Some(x) =>
                    progressBar.onPlaybackStarted(AudioOverview(Option(x.levels)).getLevels(AudioWaveProgressView.MAX_NUM_OF_LEVELS))
                  case _ =>
                    verbose(l"66 case Success(value) value is empty")
                }
                progressBar.onPlaybackStopped(value._1.map(_.toMillis).getOrElse(0))
              }
            })
        }
    }
  }

  private val playControls = controller.getPlaybackControls(asset.map(_._1))
  private val isPlaying = playControls.flatMap(_.isPlaying)

  private val progressInMillis = playControls.flatMap(_.playHead).map(_.toMillis.toInt)

  //  duration.map(_.getOrElse(Duration.ZERO).toMillis.toInt).on(Threading.Ui)(progressBar.setMax)
  //  playControls.flatMap(_.playHead).map(_.toMillis.toInt).on(Threading.Ui)(progressBar.setProgress)
  //  duration.map(_.getOrElse(Duration.ZERO).toMillis).on(Threading.Ui)(progressBar.onPlaybackStopped)
  //  playControls.flatMap(_.playHead).map(_.toMillis).on(Threading.Ui)(progressBar.onPlaybackProceeded)

  progressInMillis.onUi { duration =>
    progressBar.onPlaybackProceeded(duration)
  }

  (for {
    playing <- isPlaying
    progress <- progressInMillis
    duration <- duration.map(_.getOrElse(Duration.ZERO))
    displayedTime = if (playing || progress > 0) progress else duration.toMillis
    formatted = StringUtils.formatTimeMilliSeconds(displayedTime)
  } yield formatted).onUi(durationView.setText)

  isPlaying {
    assetActionButton.isPlaying ! _
  }

  (for {
    pl <- isPlaying
    a <- asset
  } yield (pl, a)).onChanged {
    case (pl, (a, _)) =>
      if (pl) controller.onAudioPlayed ! a
  }

  assetActionButton.onClicked.filter { state =>
    LogUtils.i("AudioAssetPartView", "assetActionButton.onClicked state:" + state)
    state == DeliveryState.Complete || state == DeliveryState.DownloadFailed
  } { aa =>
    LogUtils.i("AudioAssetPartView", "assetActionButton.onClicked aa:" + aa)
    playControls.currentValue.foreach {
      bb =>
        LogUtils.i("AudioAssetPartView", "assetActionButton.onClicked bb:" + bb)
        bb.playOrPause()
    }

    (for {
      convId <- message.map(_.convId)
      msgId <- message.map(_.id)
      isRead <- message.map(_.isMsgRead)
      isSelf = opts.fold(false)(_.isSelf)
      mcui <- messagesConvUi
    } yield {
      if (!isRead && !isSelf) {
        mcui.updateMessageReadState(convId, msgId)
      }
      (msgId, isRead, isSelf)
    }).on(Threading.Ui) {
      parm =>
        verbose(l"assetActionButton setdeliveryState 0 end ,parm:$parm")
    }
  }

  //completed.on(Threading.Ui) {
  //  progressBar.setEnabled
  //}

  //progressBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener {
  //  override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit =
  //    if (fromUser) playControls.currentValue.foreach(_.setPlayHead(Duration.ofMillis(progress)))
  //
  //  override def onStopTrackingTouch(seekBar: SeekBar): Unit = ()
  //
  //  override def onStartTrackingTouch(seekBar: SeekBar): Unit = ()
  //})

  override def set(msg: MessageAndLikes, prev: Option[MessageData], next: Option[MessageData], part: Option[MessageContent], opt: Option[MessageView.MsgBindOptions], adapter: Option[RecyclerView.Adapter[_ <: RecyclerView.ViewHolder]]): Unit = {
    super.set(msg, prev, next, part, opt, adapter)
    unReadDot.setVisibility(View.INVISIBLE)
    opt.foreach { opt =>
      setAudioActionButtonBg(getRootView, opt.isSelf)
      (for {
        assetId <- message.map(_.assetId)
        isRead <- message.map(_.isMsgRead)
        //          st <- deliveryState
        //          _ = verbose(l"setdeliveryState ProgressIndicator 1 isRead:$isRead, deliveryState:$st")
        //          assetData <- controller.assetSignal(assetId).map(a => Option(a._1)).orElse(Signal.const(Option.empty[AssetData]))
        //          _ = verbose(l"setdeliveryState ProgressIndicator 2 AssetData:$assetData")
        assetStatus <- controller.assetSignal(assetId).map(a => Option(a._2)).orElse(Signal.const(Option.empty[AssetStatus]))
        isSelf = opts.fold(false)(_.isSelf)
      } yield (isRead, assetStatus, isSelf)).on(Threading.Ui) {
        parm =>
          //          val lp: FrameLayout.LayoutParams = audioContent.getLayoutParams.asInstanceOf[FrameLayout.LayoutParams]
          if (!parm._3) {
            unReadDot.setAccentColor(ContextCompat.getColor(context, R.color.SecretRed))
            parm match {
              case (true, Some(p), _) =>
                verbose(l"setdeliveryState ProgressIndicator 3 AssetStatus:$p")
//                p match {
//                  case AssetStatus.UPLOAD_NOT_STARTED
//                       | AssetStatus.UPLOAD_CANCELLED
//                       | AssetStatus.UPLOAD_FAILED
//                       | AssetStatus.UPLOAD_IN_PROGRESS
//                       | AssetStatus.UPLOAD_DONE
//                       | AssetStatus.DOWNLOAD_DONE =>
//                    unReadDot.setVisibility(View.INVISIBLE)
//                  case AssetStatus.DOWNLOAD_FAILED
//                       | AssetStatus.DOWNLOAD_IN_PROGRESS =>
//                    unReadDot.setVisibility(View.VISIBLE)
//                }
                unReadDot.setVisibility(View.INVISIBLE)
              case (false, Some(p), _) =>
                verbose(l"setdeliveryState ProgressIndicator 4 AssetStatus:$p")
                p match {
                  case AssetStatus.UPLOAD_NOT_STARTED
                       | AssetStatus.UPLOAD_CANCELLED
                       | AssetStatus.UPLOAD_FAILED
                       | AssetStatus.UPLOAD_IN_PROGRESS =>
                    unReadDot.setVisibility(View.INVISIBLE)
                  case AssetStatus.DOWNLOAD_FAILED
                       | AssetStatus.DOWNLOAD_IN_PROGRESS
                       | AssetStatus.DOWNLOAD_DONE
                       | AssetStatus.UPLOAD_DONE =>
                    unReadDot.setVisibility(View.VISIBLE)
                }
              case _ =>
                verbose(l"setdeliveryState ProgressIndicator 5 View.GONE")
                unReadDot.setVisibility(View.INVISIBLE)
            }
            //            lp.rightMargin = context.getResources.getDimensionPixelSize(R.dimen.wire__padding__13)
          } else {
            unReadDot.setVisibility(View.INVISIBLE)
            //            lp.rightMargin = 0
          }
        //          audioContent.setLayoutParams(lp)
      }
    }
  }
}
