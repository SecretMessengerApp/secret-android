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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{ItemTouchHelper, RecyclerView}
import com.chad.library.adapter.base.BaseQuickAdapter.{OnItemChildClickListener, OnItemClickListener}
import com.chad.library.adapter.base.callback.ItemDragAndSwipeCallback
import com.chad.library.adapter.base.listener.OnItemDragListener
import com.chad.library.adapter.base.{BaseItemDraggableAdapter, BaseQuickAdapter, BaseViewHolder}
import com.jsy.common.utils.ScreenUtils
import com.waz.zclient.emoji.OnEmojiChangeListener
import com.waz.zclient.emoji.activity.EmojiManagerActivity._
import com.waz.zclient.emoji.bean.EmojiBean
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog
import com.waz.zclient.emoji.utils.EmojiUtils
import com.waz.zclient.{BaseActivity, R}
import org.telegram.ui.Components.RLottieImageView

import scala.util.Try

class EmojiManagerActivity extends BaseActivity {

  private var customManagerAdapter: EmojiManagerAdapter = _

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_emoji_manager)

    setToolbarNavigtion(findById[Toolbar](R.id.toolbar_top), this)

    val marketTextView = findViewById[TextView](R.id.market_textView)
    marketTextView.setText(R.string.emoji_settings_hot)
    marketTextView.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        startActivityForResult(new Intent(EmojiManagerActivity.this, classOf[EmojiMarketActivity]), REQUEST_CODE_MARKET)
      }
    })

    findViewById[TextView](R.id.title_textView).setText(R.string.emoji_settings)
    findViewById[TextView](R.id.textView1).setText(R.string.emoji_settings_my_tips)

    val systemEmojiAdapter = new EmojiManagerAdapter(EmojiUtils.getDefaultEmoji, true)
    systemEmojiAdapter.setOnItemClickListener(new OnItemClickListener {
      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        Try(adapter.getItem(position)).filter(_.isInstanceOf[EmojiBean]).map(_.asInstanceOf[EmojiBean])
          .foreach(it => showStickers(it, position))
      }
    })
    val systemRecyclerView = findViewById[RecyclerView](R.id.emoji_system_recyclerView)
    systemRecyclerView.setAdapter(systemEmojiAdapter)

    val customRecyclerView = findViewById[RecyclerView](R.id.emoji_custom_recyclerView)

    customManagerAdapter = new EmojiManagerAdapter(EmojiUtils.getDbEmoji)
    customManagerAdapter.setOnItemClickListener(new OnItemClickListener {
      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        Try(adapter.getItem(position)).filter(_.isInstanceOf[EmojiBean]).map(_.asInstanceOf[EmojiBean])
          .foreach(it => showStickers(it, position))
      }
    })
    customManagerAdapter.setOnItemChildClickListener(new OnItemChildClickListener {
      override def onItemChildClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        if (view.getId == R.id.delete_imageView) {
          Try(adapter.getItem(position)).filter(_.isInstanceOf[EmojiBean]).map(_.asInstanceOf[EmojiBean])
            .foreach(EmojiUtils.RemoveEmojiInDb)
          adapter.getData.remove(position)
          adapter.notifyItemRemoved(position)
        }
      }
    })
    customManagerAdapter.setOnItemDragListener(new OnItemDragListener {

      private var startPosition = -1

      override def onItemDragStart(viewHolder: RecyclerView.ViewHolder, pos: Int): Unit = {
        startPosition = pos
      }

      override def onItemDragMoving(source: RecyclerView.ViewHolder, from: Int, target: RecyclerView.ViewHolder, to: Int): Unit = {
      }

      override def onItemDragEnd(viewHolder: RecyclerView.ViewHolder, pos: Int): Unit = {
        if (startPosition != -1 && pos != startPosition) {
          startPosition = -1
          val itemCount = customManagerAdapter.getData.size()
          for (index <- 0 until itemCount) {
            Try(customManagerAdapter.getData.get(index)).foreach { it => it.setSort(itemCount - index); EmojiUtils.updateEmojiDb(it) }
          }
        }
      }
    })
    val dragAndSwipeCallback = new ItemDragAndSwipeCallback(customManagerAdapter)
    val itemTouchHelper = new ItemTouchHelper(dragAndSwipeCallback)
    itemTouchHelper.attachToRecyclerView(customRecyclerView)
    customManagerAdapter.enableDragItem(itemTouchHelper, R.id.menu_imageView, true)

    customRecyclerView.setAdapter(customManagerAdapter)
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == REQUEST_CODE_MARKET && resultCode == Activity.RESULT_OK) {
      if (customManagerAdapter != null) {
        customManagerAdapter.setNewData(EmojiUtils.getDbEmoji)
      }
    }
  }

  private def showStickers(item: EmojiBean, position: Int): Unit = {
    val dialog = new EmojiStickerSetDialog(this)
    dialog.setData(item)
    dialog.setOnEmojiChangeListener(new OnEmojiChangeListener {
      override def onEmojiAdd(emojiBean: EmojiBean): Unit = {}

      override def onEmojiRemove(emojiBean: EmojiBean): Unit = {
        customManagerAdapter.getData.remove(position)
        customManagerAdapter.notifyItemRemoved(position)
        EmojiUtils.RemoveEmojiInDb(emojiBean)
      }

      override def onEmojiChanged(): Unit = {}
    })
    dialog.show()
  }

  override def canUseSwipeBackLayout: Boolean = true
}

object EmojiManagerActivity {

  private val REQUEST_CODE_MARKET = 0x101

  private class EmojiManagerAdapter(data: java.util.List[EmojiBean], system: Boolean = false)
    extends BaseItemDraggableAdapter[EmojiBean, BaseViewHolder](R.layout.adapter_emoji_manager, data) {

    private lazy val width = ScreenUtils.dip2px(mContext, 40F)

    override def convert(helper: BaseViewHolder, item: EmojiBean): Unit = {
      val lastItem = helper.getAdapterPosition == getData.size - 1
      helper.setGone(R.id.bottom_lineView, !lastItem)
      helper.setGone(R.id.delete_imageView, !system)
      helper.setGone(R.id.menu_imageView, !system)
      helper.setText(R.id.title_textView, item.getName)
      helper.setText(R.id.subtitle_textView, mContext.getResources.getString(R.string.emoji_gif_count, Integer.valueOf(item.getGifSize)))
      val headImageView = helper.getView[RLottieImageView](R.id.head_imageView)
      EmojiUtils.loadSticker(mContext, item, headImageView, width, width)

      helper.addOnClickListener(R.id.delete_imageView)
    }
  }

}
