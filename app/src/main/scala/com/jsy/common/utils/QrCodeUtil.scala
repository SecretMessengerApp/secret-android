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
package com.jsy.common.utils

import java.util
import java.util.Hashtable

import android.graphics.{BitmapFactory, Canvas}
import android.view.{LayoutInflater, View}
import com.google.zxing._
import com.google.zxing.common.{BitMatrix, GlobalHistogramBinarizer, HybridBinarizer}
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

class QrCodeUtil {

}

object QrCodeUtil {

  import java.io.{FileOutputStream, _}

  import android.content.Context
  import android.graphics.Bitmap
  import com.waz.model.Mime
  import com.waz.service.assets.AssetService
  import com.waz.utils.IoUtils
  import com.waz.utils.wrappers._

  def saveImageToGallery(context: Context, view: View, mime: Mime, saveImageCallBack: SaveImageCallBack): Unit = {
    new Thread() {
      override def run(): Unit = {
        super.run()
        val baos = new ByteArrayOutputStream()
        val bitmap = view2Bitmap(view, android.graphics.Color.TRANSPARENT)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val is = new ByteArrayInputStream(baos.toByteArray)
        baos.close()
        val newFile = AssetService.saveImageFile(mime)
        IoUtils.copy(is, new FileOutputStream(newFile))
        is.close()
        val uri = URI.fromFile(newFile)
        context.sendBroadcast(Intent.scanFileIntent(uri))
        saveImageCallBack.onSuccess(AndroidURIUtil.unwrap(uri))
      }
    }.start()
  }

  /**
    *
    * @param v
    * @param colorIntBg
    * @return
    */
  def view2Bitmap(v: View, colorIntBg: Int): Bitmap = {
    val bitmap = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888)
    val canvas: Canvas = new Canvas(bitmap)
    canvas.drawColor(colorIntBg)
    v.draw(canvas)
    bitmap
  }

  /**
    *
    * @param context
    * @param layResId
    * @param colorIntBg
    * @return
    */
  def view2Bitmap(context: Context, layResId: Int, colorIntBg: Int): Bitmap = {
    val v = LayoutInflater.from(context).inflate(layResId, null)
    val w: Int = v.getWidth
    val h: Int = v.getHeight
    val x = v.getX.toInt
    val y = v.getY.toInt
    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas: Canvas = new Canvas(bitmap)
    canvas.drawColor(colorIntBg)
    v.layout(x, y, w, h)
    v.draw(canvas)
    bitmap
  }


  trait SaveImageCallBack {
    def onSuccess(uri: android.net.Uri)

    def onFail()
  }

  val allFormats: util.List[BarcodeFormat] = new util.ArrayList[BarcodeFormat]() {
    add(BarcodeFormat.AZTEC)
    add(BarcodeFormat.CODABAR)
    add(BarcodeFormat.CODE_39)
    add(BarcodeFormat.CODE_93)
    add(BarcodeFormat.CODE_128)
    add(BarcodeFormat.DATA_MATRIX)
    add(BarcodeFormat.EAN_8)
    add(BarcodeFormat.EAN_13)
    add(BarcodeFormat.ITF)
    add(BarcodeFormat.MAXICODE)
    add(BarcodeFormat.PDF_417)
    add(BarcodeFormat.QR_CODE)
    add(BarcodeFormat.RSS_14)
    add(BarcodeFormat.RSS_EXPANDED)
    add(BarcodeFormat.UPC_A)
    add(BarcodeFormat.UPC_E)
    add(BarcodeFormat.UPC_EAN_EXTENSION)
  }

  val HINTS: util.Map[DecodeHintType, AnyRef] = new util.EnumMap[DecodeHintType, AnyRef](classOf[DecodeHintType]) {
    put(DecodeHintType.TRY_HARDER, BarcodeFormat.QR_CODE)
    put(DecodeHintType.POSSIBLE_FORMATS, allFormats)
    put(DecodeHintType.CHARACTER_SET, "utf-8")
  }

  def syncDecodeQRCode(picturePath: String): Result = syncDecodeQRCode(getDecodeAbleBitmap(picturePath), true)

  def syncDecodeQRCode(bitmap: Bitmap, recycler: Boolean): Result = {
    var result: Result = null
    var source: RGBLuminanceSource = null
    try {
      val width: Int = bitmap.getWidth
      val height: Int = bitmap.getHeight
      val pixels: Array[Int] = new Array[Int](width * height)
      bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
      source = new RGBLuminanceSource(width, height, pixels)
      result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS)
      if (recycler) {
        bitmap.recycle()
      }
      result
    } catch {
      case e: Exception =>
        e.printStackTrace()
        if (source != null){
          try {
            result = new MultiFormatReader().decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)), HINTS)
             result
          } catch {
            case e2: Throwable =>
              e2.printStackTrace()
              null
          }
        } else {
          null
        }
    }
  }

  private def getDecodeAbleBitmap(picturePath: String): Bitmap = try {
    val options: BitmapFactory.Options = new BitmapFactory.Options
    options.inJustDecodeBounds = true
    BitmapFactory.decodeFile(picturePath, options)
    var sampleSize: Int = options.outHeight / 400
    if (sampleSize <= 0) sampleSize = 1
    options.inSampleSize = sampleSize
    options.inJustDecodeBounds = false
    BitmapFactory.decodeFile(picturePath, options)
  } catch {
    case e: Exception =>
      null
  }

}
