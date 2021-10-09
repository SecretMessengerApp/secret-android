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
package com.waz.zclient.pages.main

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

import android.content.Context
import android.graphics.Bitmap.CompressFormat
import android.graphics.{Bitmap, BitmapFactory, Matrix}
import android.media.ExifInterface
import android.net.Uri
import android.text.TextUtils
import android.util.AttributeSet
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{CheckBox, FrameLayout, ImageView, TextView}
import com.waz.api.IConversation
import com.waz.bitmap.BitmapUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.{ConversationData, Mime, Name}
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.assets.AssetService.RawAssetInput.{BitmapInput, ByteInput, UriInput}
import com.waz.utils.events.{EventStream, Signal}
import com.waz.utils.returning
import com.waz.utils.wrappers.URI
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.pages.main.profile.views.{ConfirmationMenu, ConfirmationMenuListener}
import com.waz.zclient.ui.theme.OptionsDarkTheme
import com.waz.zclient.utils.RichView
import com.waz.zclient.{R, ViewHelper}


class ImagePreviewLayout(context: Context, attrs: AttributeSet, style: Int)
  extends FrameLayout(context, attrs, style)
    with ViewHelper
    with ConfirmationMenuListener
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)
  private val currentConv: Signal[ConversationData] = inject[ConversationController].currentConv
  private lazy val convName = currentConv.map(_.displayName)
  private lazy val conversationType = currentConv.map(_.convType)

  val sketchShouldBeVisible = Signal(true)
  val titleShouldBeVisible = Signal(true)

  private val onDrawClicked = EventStream[IDrawingController.DrawingMethod]()

  private var imageInput = Option.empty[RawAssetInput]
  private var source = Option.empty[ImagePreviewLayout.Source]

  private lazy val approveImageSelectionMenu = returning(findViewById[ConfirmationMenu](R.id.cm__cursor_preview)) { menu =>
    menu.setWireTheme(new OptionsDarkTheme(getContext))
    menu.setCancel(getResources.getString(R.string.confirmation_menu__cancel))
    menu.setConfirm(getResources.getString(R.string.confirmation_menu__confirm_done))
    menu.setConfirmationMenuListener(this)
    accentColor.onUi(menu.setAccentColor)
  }

  private lazy val imageView = returning(findViewById[ImageView](R.id.iv__conversation__preview)) { view =>
    view.onClick {
      if (sketchShouldBeVisible.currentValue.getOrElse(true)) sketchMenuContainer.fade(!approveImageSelectionMenu.isVisible)
      approveImageSelectionMenu.fade(!approveImageSelectionMenu.isVisible)
      if (!TextUtils.isEmpty(titleTextView.getText)) titleTextViewContainer.fade(!approveImageSelectionMenu.isVisible)
    }
  }

  private lazy val titleTextViewContainer = returning(findViewById[FrameLayout](R.id.ttv__image_preview__title__container)) { container =>
    convName.map(TextUtils.isEmpty(_)).onUi(empty => container.setVisible(!empty))
  }

  private lazy val titleTextView = returning(findViewById[TextView](R.id.ttv__image_preview__title)) { view =>
    (for {
      visible <- titleShouldBeVisible
      name    <- if (visible) convName else Signal.const(Name.Empty)
    } yield name).onUi(view.setText(_))
  }

  private lazy val sketchMenuContainer = returning(findViewById[View](R.id.ll__preview__sketch)) { container =>
    sketchShouldBeVisible.onUi { show => container.setVisible(show) }
  }

  private lazy val sketchDrawButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__sketch)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.DRAW)
  }

  private lazy val sketchEmojiButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__emoji)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.EMOJI)
  }

  private lazy val sketchTextButton = returning(findViewById[View](R.id.gtv__preview__drawing_button__text)) {
    _.onClick(onDrawClicked ! IDrawingController.DrawingMethod.TEXT)
  }

  private lazy val compressCheckBox = returning(findViewById[CheckBox](R.id.compress_checkBox)) { checkBox =>
    currentConv.onUi({ data =>
      val isVisible = data.convType == IConversation.Type.ONE_TO_ONE || data.convType == IConversation.Type.GROUP /*|| data.convType == IConversation.Type.THROUSANDS_GROUP*/
      checkBox.setVisibility(if(isVisible) View.VISIBLE else View.GONE)
    })
  }

  override protected def onFinishInflate(): Unit = {
    super.onFinishInflate()

    // eats the click
    this.onClick({})

    imageView
    approveImageSelectionMenu
    sketchMenuContainer
    sketchDrawButton
    sketchEmojiButton
    sketchTextButton
    titleTextView
    titleTextViewContainer
    compressCheckBox
  }

  onDrawClicked.onUi { method =>
    (imageInput, source, callback) match {
      case (Some(a), Some(s), Some(c)) => c.onSketchOnPreviewPicture(a, s, method)
      case _ =>
    }
  }

  override def confirm(): Unit = (imageInput, source, callback) match {
    case (Some(a), Some(s), Some(c)) => {
      a match {
        case uriInput: UriInput => {
          try {
            val uri = URI.unwrap(uriInput.uri)
            val mime = Mime(BitmapUtils.detectImageType(context.getContentResolver.openInputStream(uri)))
            verbose(l"case uriInput:,mime:$mime")
            if (mime == Mime.Image.Gif) {
              c.onSendPictureFromPreview(a, s)
            } else {
              val inputStream = context.getContentResolver.openInputStream(uri)
              val newBitmap = if (compressCheckBox.isChecked) {
                rotateBitmap(inputStream, uri)
              } else {
                compressBitmap(inputStream, uri)

              }
              c.onSendPictureFromPreview(BitmapInput(newBitmap), s)
            }
          } catch {
            case _: Throwable => c.onSendPictureFromPreview(a, s)
          }
        }
        case byteInput: ByteInput => {
          try {
            val mime = Mime(BitmapUtils.detectImageTypeForByte(byteInput.bytes))
            verbose(l"case byteInput: ByteInput:Mime:$mime")
            if (mime == Mime.Image.Gif) {
              c.onSendPictureFromPreview(a, s)
            } else {
              val byteData = byteInput.bytes
              val newBitmap = if (compressCheckBox.isChecked) {
                BitmapFactory.decodeByteArray(byteData, 0, byteData.length)
              } else {
                compressBitmap(byteData)
              }
              c.onSendPictureFromPreview(BitmapInput(newBitmap), s)
            }
          } catch {
            case _: Throwable => c.onSendPictureFromPreview(a, s)
          }
        }
        case _ => c.onSendPictureFromPreview(a, s)
      }
    }
    case _ =>
  }

  private val compressMaxWidth = 480
  private val compressQuality = 90


  private def rotateBitmap(sourceStream: InputStream,uri : Uri) : Bitmap = {

    var sourceBitmap = BitmapFactory.decodeStream(sourceStream)

    val degree = calculateDegree(context.getContentResolver.openInputStream(uri))

    if (degree != 0) {
      val matrix = new Matrix()
      matrix.postRotate(degree)
      sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth, sourceBitmap.getHeight, matrix, true)
    }

    sourceBitmap
  }

  private def compressBitmap(sourceStream: InputStream,uri : Uri): Bitmap = {
    val sourceOptions = new BitmapFactory.Options()
    var sourceBitmap = BitmapFactory.decodeStream(sourceStream, null, sourceOptions)
    sourceOptions.inSampleSize = calculateInSampleSize(sourceOptions, compressMaxWidth, compressMaxWidth)

    val degree = calculateDegree(context.getContentResolver.openInputStream(uri))
    if (degree != 0) {
      val matrix = new Matrix()
      matrix.postRotate(degree)
      sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth, sourceBitmap.getHeight, matrix, true)
    }

    val newOutputStream = new ByteArrayOutputStream
    sourceBitmap.compress(CompressFormat.JPEG, compressQuality, newOutputStream)
    val bytes = newOutputStream.toByteArray
    if (!sourceBitmap.isRecycled) sourceBitmap.recycle()

    val newBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, sourceOptions)

    newOutputStream.close()

    newBitmap
  }

  private def compressBitmap(sourceByte: Array[Byte]): Bitmap = {
    val sourceOptions = new BitmapFactory.Options()
    sourceOptions.inJustDecodeBounds = true
    BitmapFactory.decodeByteArray(sourceByte, 0, sourceByte.length, sourceOptions)
    sourceOptions.inSampleSize = calculateInSampleSize(sourceOptions, compressMaxWidth, compressMaxWidth)
    sourceOptions.inJustDecodeBounds = false
    var sourceBitmap = BitmapFactory.decodeByteArray(sourceByte, 0, sourceByte.length, sourceOptions)

    val degree = calculateDegree(new ByteArrayInputStream(sourceByte))
    if (degree != 0) {
      val matrix = new Matrix()
      matrix.postRotate(degree)
      sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth, sourceBitmap.getHeight, matrix, true)
    }

    val newOutputStream = new ByteArrayOutputStream
    sourceBitmap.compress(CompressFormat.JPEG, compressQuality, newOutputStream)
    val bytes = newOutputStream.toByteArray
    if (!sourceBitmap.isRecycled) sourceBitmap.recycle()

    val newBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length)

    newOutputStream.close()

    newBitmap
  }

  private def calculateDegree(inputStream: InputStream): Int = {
    var degree = 0

    val exifInterface = new ExifInterface(inputStream)
    val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
      degree = 90
    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
      degree = 180
    } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
      degree = 270
    }

    degree
  }

  private def calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int = {
    val height = options.outHeight
    val width = options.outWidth

    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
      val heightRatio = Math.round(height / reqHeight)
      val widthRatio = Math.round(width / reqWidth)
      inSampleSize = if (heightRatio < widthRatio) {
        heightRatio
      } else {
        widthRatio
      }
    }
    inSampleSize
  }

  override def cancel(): Unit = {
    callback.foreach(_.onCancelPreview())
  }

  def setImage(imageData: Array[Byte], isMirrored: Boolean): Unit = {
    this.source = Option(ImagePreviewLayout.Source.Camera)
    this.imageInput = Some(ByteInput(imageData))
    imageView.setImageDrawable(ImageAssetDrawable(imageData, isMirrored))
  }

  def setImage(uri: URI, source: ImagePreviewLayout.Source): Unit = {
    this.source = Option(source)
    this.imageInput = Some(UriInput(uri))
    imageView.setImageDrawable(ImageAssetDrawable(uri, scaleType = ScaleType.CenterInside))
  }

  // TODO: switch to signals after rewriting CameraFragment
  private var callback = Option.empty[ImagePreviewCallback]

  private def setCallback(callback: ImagePreviewCallback) = { this.callback = Option(callback) }

  def showSketch(show: Boolean): Unit = sketchShouldBeVisible ! show

  def showTitle(show: Boolean): Unit = titleShouldBeVisible ! show
}

trait ImagePreviewCallback {
  def onCancelPreview(): Unit

  def onSketchOnPreviewPicture(image: RawAssetInput, source: ImagePreviewLayout.Source, method: IDrawingController.DrawingMethod): Unit

  def onSendPictureFromPreview(imageAsset: RawAssetInput, source: ImagePreviewLayout.Source): Unit
}

object ImagePreviewLayout {
  sealed trait Source

  object Source {

    case object InAppGallery extends Source

    case object DeviceGallery extends Source

    case object Camera extends Source

  }

  def CAMERA(): ImagePreviewLayout.Source = ImagePreviewLayout.Source.Camera // for java

  def newInstance(context: Context, container: ViewGroup, callback: ImagePreviewCallback): ImagePreviewLayout =
    returning(LayoutInflater.from(context).inflate(R.layout.fragment_cursor_images_preview, container, false).asInstanceOf[ImagePreviewLayout]) {
      _.setCallback(callback)
    }

}
