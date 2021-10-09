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
package com.waz.zclient.emoji.activity

import java.util
import java.util.List

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter.OnItemChildClickListener
import com.chad.library.adapter.base.{BaseItemDraggableAdapter, BaseQuickAdapter, BaseViewHolder}
import com.google.gson.Gson
import com.jsy.common.httpapi.{EmojiGifService, SimpleHttpListener}
import com.jsy.common.utils.ScreenUtils
import com.waz.zclient.emoji.OnEmojiChangeListener
import com.waz.zclient.emoji.activity.EmojiMarketActivity._
import com.waz.zclient.emoji.bean.{EmojiBean, EmojiResponse}
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog
import com.waz.zclient.emoji.utils.EmojiUtils
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{BaseActivity, R, ZApplication}
import org.telegram.ui.Components.RLottieImageView

import scala.util.Try

class EmojiMarketActivity extends BaseActivity {

  private val showData = new util.ArrayList[EmojiBean]()
  private var emojiMarketAdapter: EmojiMarketAdapter = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_emoji_market)

    setToolbarNavigtion(findById[Toolbar](R.id.toolbar_top), this)

    findViewById[TextView](R.id.title_textView).setText(R.string.emoji_settings_hot)

    emojiMarketAdapter = new EmojiMarketAdapter(showData)
    emojiMarketAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener {
      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        Try(adapter.getItem(position)).filter(_.isInstanceOf[EmojiBean]).map(_.asInstanceOf[EmojiBean])
          .foreach(it => showStickers(it, position))
      }
    })
    emojiMarketAdapter.setOnItemChildClickListener(new OnItemChildClickListener {
      override def onItemChildClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        if (view.getId == R.id.add_imageView) {
          Try(adapter.getItem(position)).filter(_.isInstanceOf[EmojiBean]).map(_.asInstanceOf[EmojiBean])
            .foreach(itemBean => changeItemData(itemBean, position, itemBean.isLocal))
        }
      }
    })
    val contentRecyclerView = findViewById[RecyclerView](R.id.content_recyclerView)
    contentRecyclerView.setAdapter(emojiMarketAdapter)
    val emojiBeanList = EmojiUtils.getDbEmojiByDefault(false)
    if (emojiBeanList != null && emojiBeanList.size() > 0) {
       showData.clear()
       showData.addAll(emojiBeanList)
       emojiMarketAdapter.notifyDataSetChanged()
    }

  }

  private def changeItemData(itemBean: EmojiBean, position: Int, remove: Boolean = false): Unit = {
    if (remove) {
      itemBean.setLocal(false)
      EmojiUtils.RemoveEmojiInDb(itemBean)
    } else {
      itemBean.setLocal(true)
      EmojiUtils.AddEmoji2Db(itemBean)
    }
    if (emojiMarketAdapter != null) emojiMarketAdapter.notifyItemChanged(position)

    setResult(Activity.RESULT_OK)
  }

  private def showStickers(item: EmojiBean, position: Int): Unit = {
    val dialog = new EmojiStickerSetDialog(this)
    dialog.setData(item)
    dialog.setOnEmojiChangeListener(new OnEmojiChangeListener {
      override def onEmojiAdd(emojiBean: EmojiBean): Unit = {
        changeItemData(emojiBean, position)
      }

      override def onEmojiRemove(emojiBean: EmojiBean): Unit = {
        changeItemData(emojiBean, position, remove = true)
      }

      override def onEmojiChanged(): Unit = {}
    })
    dialog.show()
  }
}

object EmojiMarketActivity {

  private class EmojiMarketAdapter(data: java.util.List[EmojiBean])
    extends BaseItemDraggableAdapter[EmojiBean, BaseViewHolder](R.layout.adapter_emoji_market, data) {

    private lazy val width = ScreenUtils.dip2px(mContext, 40F)

    override def convert(helper: BaseViewHolder, item: EmojiBean): Unit = {
      helper.setImageResource(R.id.add_imageView, if (item.isLocal) R.drawable.icon_emoji_add_complete else R.drawable.icon_emoji_add)
      helper.setText(R.id.title_textView, item.getName)
      helper.setText(R.id.subtitle_textView, mContext.getResources.getString(R.string.emoji_gif_count, Integer.valueOf(item.getGifSize)))
      helper.setGone(R.id.bottom_lineView, helper.getAdapterPosition != getData.size() - 1)
      val headImageView = helper.getView[RLottieImageView](R.id.head_imageView)
      EmojiUtils.loadSticker(mContext, item, headImageView, width, width)

      helper.addOnClickListener(R.id.add_imageView)
    }
  }

}
