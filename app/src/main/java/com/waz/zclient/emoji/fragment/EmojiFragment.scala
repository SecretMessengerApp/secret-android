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

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View.OnTouchListener
import android.view.{LayoutInflater, MotionEvent, View, ViewGroup}
import android.widget.{FrameLayout, TextView}
import androidx.recyclerview.widget.{GridLayoutManager, RecyclerView}
import com.jsy.common.model.EmojiGifModel
import com.jsy.common.utils.{MessageUtils, ScreenUtils}
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.emoji.adapter.EmojiNewAdapter
import com.waz.zclient.emoji.bean.{EmojiBean, EmotionItemBean, GifSavedItem}
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog
import com.waz.zclient.emoji.utils.{EmojiUtils, GifSavedDaoHelper}
import com.waz.zclient.emoji.view.MyStickerEmojiCell
import com.waz.zclient.emoji.{Constants, OnEmojiChangeListener}
import com.waz.zclient.utils._
import com.waz.zclient.{BaseLazyFragment, R}
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.ContentPreviewViewer

class EmojiFragment(val emotionRes: EmojiBean,val onEmojiChangeListener:OnEmojiChangeListener) extends BaseLazyFragment
  with DerivedLogTag {

  private var fl_content: FrameLayout = _
  private var adapter:EmojiNewAdapter=_
  private var tv_name: TextView = _
  private var recyclerView:RecyclerListView=_
  private var stickersOnItemClickListener:RecyclerListView.OnItemClickListener = _

  private val contentPreviewViewerDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
    override def onSend(itemBean: EmotionItemBean): Unit ={
      EmojiFragment.this.sendGifEmoji(itemBean)
    }

    override def onStickerSet(itemBean: EmotionItemBean): Unit ={
        showStickerSet()
    }

    override def onFavorite(itemBean: EmotionItemBean): Unit = {
      val userId = SpUtils.getUserId(getContext)
      if (GifSavedDaoHelper.existsSavedGif(userId, isEmojiGif = true, itemBean.getUrl)) {
        GifSavedDaoHelper.deleteSavedGif(userId, isEmojiGif = true, itemBean.getUrl)
      } else {
        val data = new GifSavedItem()
        data.setUserId(SpUtils.getUserId(getContext))
        data.setFile(itemBean.getFile)
        data.setName(itemBean.getName)
        data.setUrl(itemBean.getUrl)
        data.setFolderId(itemBean.getFolderId)
        data.setFolderName(itemBean.getFolderName)
        data.setFolderIcon(itemBean.getFolderIcon)
        data.setRecently(false)
        GifSavedDaoHelper.saveGif(data)
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.emoji_custom_content_layout, container, false)
  }

  override protected def onFragmentFirstVisible(): Unit = {
    super.onFragmentFirstVisible()
    tv_name = ViewUtils.getView(getView, R.id.tv_name)
    fl_content = ViewUtils.getView(getView, R.id.fl_content)

    if (emotionRes != null) {
      tv_name.setText(emotionRes.getName)
      recyclerView=new RecyclerListView(getContext){
        override def onInterceptTouchEvent(event: MotionEvent): Boolean = {
          val result = ContentPreviewViewer.getInstance.onInterceptTouchEvent(event, recyclerView, 400, contentPreviewViewerDelegate)
          super.onInterceptTouchEvent(event) || result
        }
      }

      stickersOnItemClickListener = new RecyclerListView.OnItemClickListener() {
        override def onItemClick(view: View, position: Int): Unit = {
          Log.d("JACK819", "click positon:" + position)
          if (!view.isInstanceOf[MyStickerEmojiCell]) return
          ContentPreviewViewer.getInstance.reset()
          val emotionItemBean=adapter.getItem(position)
          EmojiFragment.this.sendGifEmoji(emotionItemBean)

        }
      }
      recyclerView.setOnTouchListener(new OnTouchListener {
        override def onTouch(v: View, event: MotionEvent): Boolean ={
          ContentPreviewViewer.getInstance.onTouch(event, recyclerView, 400, stickersOnItemClickListener, contentPreviewViewerDelegate)
        }
      })
      recyclerView.setClipToPadding(false)
      recyclerView.setLayoutManager(new GridLayoutManager(getContext, Constants.SPAN_COUNT))
      recyclerView.addItemDecoration(new RecyclerView.ItemDecoration {
        override def getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State): Unit = {
          super.getItemOffsets(outRect, view, parent, state)
          val position = parent.getChildAdapterPosition(view)
          if (position >= Constants.SPAN_COUNT) {
            outRect.top = ScreenUtils.dip2px(getContext, 10f)
            outRect.bottom = ScreenUtils.dip2px(getContext, 10f)
          }
        }
      })
      recyclerView.setOnItemClickListener(stickersOnItemClickListener)
      adapter = new EmojiNewAdapter(EmojiUtils.convert2EmojiList(emotionRes,-1))
      recyclerView.setAdapter(adapter)

      fl_content.removeAllViews()
      fl_content.addView(recyclerView)
    }
  }

  private def sendGifEmoji(itemBean: EmotionItemBean): Unit = {
    LogUtils.d("JACK8","emoji:"+itemBean)
    val model = new EmojiGifModel()
    model.msgType = MessageUtils.MessageContentUtils.EMOJI_GIF
    model.msgData = new EmojiGifModel.MsgData(itemBean.getFolderId, itemBean.getUrl, itemBean.getFolderName
      , itemBean.getFolderIcon, itemBean.getName)

    inject[ConversationController].sendTextJsonMessage(MessageUtils.createEmojiGifModel(model).toString, activity = getActivity).onSuccess {
      case Some(_) => GifSavedDaoHelper.addRecently(SpUtils.getUserId(getContext), itemBean)
    }(Threading.Background)
  }

  def showStickerSet(): Unit ={
     if(emotionRes==null)return
     val dialog=new EmojiStickerSetDialog(getContext);
     dialog.setOnEmojiChangeListener(onEmojiChangeListener)
     dialog.setData(emotionRes)
     dialog.show()
  }
}


object EmojiFragment {
  def apply(emojiBean: EmojiBean,onEmojiChangeListener:OnEmojiChangeListener): EmojiFragment = new EmojiFragment(emojiBean,onEmojiChangeListener)
}
