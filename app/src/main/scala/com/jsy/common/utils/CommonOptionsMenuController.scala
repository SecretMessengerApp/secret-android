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
package com.jsy.common.utils

import java.io.File

import android.content.{Context, Intent}
import android.net.Uri
import android.text.TextUtils
import com.jsy.common.download.GlideImageDownload
import com.jsy.common.model.circle.CircleConstant
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.utils.events.{EventContext, EventStream, Signal, SourceStream}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.participants.OptionsMenuController
import com.waz.zclient.participants.OptionsMenuController.{BaseMenuItem, MenuItem}
import com.waz.zclient.{Injectable, Injector, R}

class CommonOptionsMenuController(titleStr: String, builder: Set[MenuItem])(implicit injector: Injector, context: Context, ec: EventContext)
  extends OptionsMenuController
    with Injectable
    with DerivedLogTag {

  import CommonOptionsMenuController._

  override val title: Signal[Option[String]] =
    if (!TextUtils.isEmpty(titleStr))
      Signal.const(Option(titleStr))
    else
      Signal.const(None)
  override val optionItems: Signal[Seq[MenuItem]] =
    Signal.const(builder.toSeq.sortWith {
      case (a, b) => CommonSeq.indexOf(a).compareTo(CommonSeq.indexOf(b)) < 0
    })

  override val onMenuItemClicked: SourceStream[MenuItem] = EventStream()
  override val selectedItems: Signal[Set[MenuItem]] = Signal.const(Set())

  def savePicture(picPath: String): Unit = {
    verbose(l"savePicture:  arPicPath: ${Option(picPath)}")
    if (!TextUtils.isEmpty(picPath)) {
      val filePath = CircleConstant.SAVE_IMG_PATH
      val fileName = MD5Util.MD5(picPath) + ".jpg"
      GlideImageDownload.pictureDownload(context, picPath, filePath, fileName, new GlideImageDownload.PictureDownloadCallBack() {
        override def onDownLoadSuccess(file: File): Unit = {
          if (file != null) {
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(file.getPath))))
            ToastUtil.toastByString(context, context.getString(R.string.picture_save_success, file.getAbsolutePath))
          } else ToastUtil.toastByString(context, context.getString(R.string.picture_save_error))
        }

        override def onDownloadFail(e: Throwable): Unit = ToastUtil.toastByString(context, context.getString(R.string.picture_save_error))
      })
    }
  }

}

object CommonOptionsMenuController {

  object Save extends BaseMenuItem(R.string.message_bottom_menu_action_save, Some(R.string.glyph__download))

  val CommonSeq = Seq(Save)
}


