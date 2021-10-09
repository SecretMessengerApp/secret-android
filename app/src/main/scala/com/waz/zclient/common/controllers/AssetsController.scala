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
package com.waz.zclient.common.controllers

import android.app.DownloadManager
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.text.TextUtils
import android.util.TypedValue
import android.view.{Gravity, View}
import android.widget.{TextView, Toast}
import androidx.appcompat.app.AppCompatDialog
import com.jsy.common.acts.{FileRenderActivity, VideoPlayActivity}
import com.waz.api.Message
import com.waz.content.UserPreferences.DownloadImagesAlways
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AssetData, AssetId, MessageData, Mime}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.RawAssetInput.WireAssetInput
import com.waz.service.assets.GlobalRecordAndPlayService
import com.waz.service.assets.GlobalRecordAndPlayService.{AssetMediaKey, Content, UnauthenticatedContent}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.controllers.drawing.IDrawingController.DrawingMethod
import com.waz.zclient.controllers.singleimage.ISingleImageController
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{AssetSignal, UiStorage}
import com.waz.zclient.{Injectable, Injector, R}
import org.threeten.bp.Duration

import scala.PartialFunction._
import scala.util.{Success, Try}

class AssetsController(implicit context: Context, inj: Injector, ec: EventContext)
  extends Injectable with DerivedLogTag { controller =>

  import AssetsController._
  import Threading.Implicits.Ui
  implicit lazy val uiStorage = inject[UiStorage]
  val zms = inject[Signal[ZMessaging]]
  val assets = zms.map(_.assets)

  val messages = zms.map(_.messages)

  lazy val messageActionsController = inject[MessageActionsController]
  lazy val singleImage              = inject[ISingleImageController]
  lazy val screenController         = inject[ScreenController]

  //TODO make a preference controller for handling UI preferences in conjunction with SE preferences
  val downloadsAlwaysEnabled =
    zms.flatMap(_.userPrefs.preference(DownloadImagesAlways).signal).disableAutowiring()

  val onFileOpened = EventStream[AssetData]()
  val onFileSaved = EventStream[AssetData]()
  val onVideoPlayed = EventStream[AssetData]()
  val onAudioPlayed = EventStream[AssetData]()

  messageActionsController.onMessageAction {
    case (MessageAction.OpenFile, msg) =>
      zms.head.flatMap(_.assetsStorage.get(msg.assetId)) foreach {
        case Some(asset) => openFile(asset)
        case None => // TODO: show error
      }
    case _ => // ignore
  }

//  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => assets.flatMap(_.assetSignal(m.assetId)))
  def assetSignal(mes: Signal[MessageData]) = mes.flatMap(m => AssetSignal(m.assetId))
//  def assetSignal(assetId: AssetId) = assets.flatMap(_.assetSignal(assetId))
  def assetSignal(assetId: AssetId) = AssetSignal(assetId)
  def downloadProgress(id: AssetId) = assets.flatMap(_.downloadProgress(id))

  def uploadProgress(id: AssetId) = assets.flatMap(_.uploadProgress(id))

  def cancelUpload(m: MessageData) = assets.currentValue.foreach(_.cancelUpload(m.assetId, m.id))

  def cancelDownload(m: MessageData) = assets.currentValue.foreach(_.cancelDownload(m.assetId))

  def retry(m: MessageData, isForce: Boolean = false) = {
    verbose(l"message retry, m.state :${m.state}")
    if (isForce || (m.state == Message.Status.FAILED || m.state == Message.Status.FAILED_READ)) {
      messages.currentValue.foreach {
        verbose(l"message retry, retryMessageSending m.convId:${m.convId},m.id:${m.id}")
        _.retryMessageSending(m.convId, m.id)
      }
    }
  }

  def getPlaybackControls(asset: Signal[AssetData]): Signal[PlaybackControls] = asset.flatMap { a =>
    if (cond(a.mime.orDefault) { case Mime.Audio() => true }) Signal.const(new PlaybackControls(a.id, controller))
    else Signal.empty[PlaybackControls]
  }

  // display full screen image for given message
  def showSingleImage(msg: MessageData, container: View) =
    if (!(msg.isEphemeral && msg.expired)) {
      verbose(l"message loaded, opening single image for ${msg.id}")
      singleImage.setViewReferences(container)
      singleImage.showSingleImage(msg.id.str)
    }

  //FIXME: don't use java api
  def openDrawingFragment(msg: MessageData, drawingMethod: DrawingMethod) =
    screenController.showSketch ! Sketch.singleImage(WireAssetInput(msg.assetId), drawingMethod)

  def openFile(asset: AssetData) =
    assets.head.flatMap(_.getContentUri(asset.id)) foreach {
      case Some(uri) =>
        asset match {
         case AssetData.IsVideo() =>
           onVideoPlayed ! asset
           VideoPlayActivity.startSelf(context,uri.toString);
         case _ =>
           showOpenFileDialog(uri, asset)
        }
      case None =>
      // TODO: display error
    }

  def showOpenFileDialog(uri: URI, asset: AssetData) = {
    val intent = getOpenFileIntent(uri, asset.mime.orDefault.str)
    val fileCanBeOpened = fileTypeCanBeOpened(context.getPackageManager, intent)

    //TODO tidy up
    //TODO there is also a weird flash or double-dialog issue when you click outside of the dialog
    val dialog = new AppCompatDialog(context)
    asset.name.foreach(dialog.setTitle)
    dialog.setContentView(R.layout.file_action_sheet_dialog)

    val title = dialog.findViewById(R.id.title).asInstanceOf[TextView]
    title.setEllipsize(TextUtils.TruncateAt.MIDDLE)
    title.setTypeface(TypefaceUtils.getTypeface(getString(R.string.wire__typeface__medium)))
    title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getDimenPx(R.dimen.wire__text_size__regular))
    title.setGravity(Gravity.CENTER)

    val openButton = dialog.findViewById(R.id.ttv__file_action_dialog__open).asInstanceOf[TextView]
    val noAppFoundLabel = dialog.findViewById(R.id.ttv__file_action_dialog__open__no_app_found).asInstanceOf[View]
    val saveButton = dialog.findViewById(R.id.ttv__file_action_dialog__save).asInstanceOf[View]

    if (fileCanBeOpened) {
      noAppFoundLabel.setVisibility(View.GONE)
      openButton.setAlpha(1f)
      openButton.setOnClickListener(new View.OnClickListener() {
        def onClick(v: View) = {
          onFileOpened ! asset
          dialog.dismiss()
          asset.name.filter(it => Seq(".txt", ".svg", ".pdf").exists(it.toLowerCase().endsWith))
            .flatMap(name => Try(AndroidURIUtil.unwrap(uri)).toOption.map(uri => (name, uri)))
            .fold {
              context.startActivity(intent)
            } { tuple =>
              FileRenderActivity.start(context, tuple._1, tuple._2.toString)
            }
        }
      })
    }
    else {
      noAppFoundLabel.setVisibility(View.VISIBLE)
      val disabledAlpha = getResourceFloat(R.dimen.button__disabled_state__alpha)
      openButton.setAlpha(disabledAlpha)
    }

    saveButton.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View) = {
        onFileSaved ! asset
        dialog.dismiss()
        saveToDownloads(asset)
      }
    })

    dialog.show()
  }

  def saveToDownloads(asset: AssetData) =
    assets.head.flatMap(_.saveAssetToDownloads(asset)).onComplete {
      case Success(Some(file)) =>
        val uri = URI.fromFile(file)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE).asInstanceOf[DownloadManager]
        downloadManager.addCompletedDownload(asset.name.get, asset.name.get, false, asset.mime.orDefault.str, uri.getPath, asset.sizeInBytes, true)
        Toast.makeText(context, getString(R.string.content__file__action__save_completed, file.getAbsolutePath), Toast.LENGTH_SHORT).show()
        context.sendBroadcast(returning(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE))(_.setData(URI.unwrap(uri))))
      case _ =>
    }(Threading.Ui)
}

object AssetsController {

  class PlaybackControls(assetId: AssetId, controller: AssetsController) extends DerivedLogTag {
    val rAndP = controller.zms.map(_.global.recordingAndPlayback)

    val isPlaying = rAndP.flatMap(rP => rP.isPlaying(AssetMediaKey(assetId)))
    val playHead = rAndP.flatMap(rP => rP.playhead(AssetMediaKey(assetId)))

    private def rPAction(f: (GlobalRecordAndPlayService, AssetMediaKey, Content, Boolean) => Unit): Unit = {
      verbose(l"audioasset,rPAction assetId:$assetId")
      for {
        as <- controller.assets.currentValue
        rP <- rAndP.currentValue
        isPlaying <- isPlaying.currentValue
      } {
        as.getContentUri(assetId).foreach {
          case Some(uri) =>
            verbose(l"audioasset,rPAction assetId:$assetId,uri:$uri")
            f(rP, AssetMediaKey(assetId), UnauthenticatedContent(uri), isPlaying)
          case None =>
        }(Threading.Background)
      }
    }

    def playOrPause() = rPAction {
      case (rP, key, content, playing) =>
        verbose(l"audioasset,playOrPause rP:$rP,key:$key, content:$content,playing:$playing")
        if (playing) rP.pause(key) else rP.play(key, content)
      case (_, _, _, _) =>
        verbose(l"audioasset,playOrPause assetId:$assetId")
    }

    def setPlayHead(duration: Duration) = rPAction { case (rP, key, content, playing) => rP.setPlayhead(key, content, duration) }
  }

  def getOpenFileIntent(uri: URI, mimeType: String): Intent = {
    returning(new Intent) { i =>
      i.setAction(Intent.ACTION_VIEW)
      i.setDataAndType(AndroidURIUtil.unwrap(uri), mimeType)
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  def fileTypeCanBeOpened(manager: PackageManager, intent: Intent): Boolean =
    manager.queryIntentActivities(intent, 0).size > 0
}
