/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.waz.zclient.emoji.fragment

import java.util

import android.view.{LayoutInflater, View}
import android.widget.{ImageView, TextView}
import com.waz.threading.Threading
import com.waz.zclient.R
import com.waz.zclient.emoji.OnEmojiChangeListener
import com.waz.zclient.emoji.bean.GifSavedItem
import com.waz.zclient.emoji.utils.GifSavedDaoHelper
import com.waz.zclient.utils.SpUtils

import scala.concurrent.Future
import scala.util.{Failure, Success}

class RecentlyFragment(override val onEmojiChangeListener:OnEmojiChangeListener) extends BaseGifFragment(onEmojiChangeListener){
  override def getData: Future[util.List[GifSavedItem]] = Future {
    GifSavedDaoHelper.getSavedGIFs(SpUtils.getUserId(getContext), false, isRecently = true)
  }(Threading.Background)

  override def getEmptyResId: Int = R.string.emoji_gif_empty_recently

  override def onLoadedData(): Unit = {
    gifAdapter.removeAllHeaderView()
    if (!gifAdapter.getData.isEmpty) {
      gifAdapter.addHeaderView(getHeadView)
    }
  }

  private def deleteData(): Future[Int] = Future {
    GifSavedDaoHelper.deleteRecently(SpUtils.getUserId(getContext))
  }(Threading.Background)

  private def getHeadView = {
    val headerView = LayoutInflater.from(getContext).inflate(R.layout.emoji_recently_header_layout, null, false)
    headerView.findViewById(R.id.textView1).asInstanceOf[TextView].setText(R.string.emoji_gif_recently_tips)
    val deleteImageView = headerView.findViewById[ImageView](R.id.delete_imageView)
    deleteImageView.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        deleteData().onComplete {
          case Success(_) =>
            gifAdapter.getData.clear()
            gifAdapter.removeAllHeaderView()
            gifAdapter.notifyDataSetChanged()
          case Failure(_) =>
        }(Threading.Ui)
      }
    })
    headerView
  }

}

