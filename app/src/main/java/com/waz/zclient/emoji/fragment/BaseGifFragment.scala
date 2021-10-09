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

import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.view.View.OnTouchListener
import android.view._
import android.widget.{FrameLayout, TextView}
import androidx.recyclerview.widget.{GridLayoutManager, RecyclerView}
import com.j256.ormlite.dao.Dao
import com.jsy.common.model.EmojiGifModel
import com.jsy.common.utils.{MessageUtils, ScreenUtils}
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.assets.AssetService.RawAssetInput.ByteInput
import com.waz.threading.Threading
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.emoji.{Constants, OnEmojiChangeListener}
import com.waz.zclient.emoji.adapter.EmojiNewAdapter
import com.waz.zclient.emoji.bean.{EmojiBean, EmotionItemBean, GifSavedItem}
import com.waz.zclient.emoji.dialog.EmojiStickerSetDialog
import com.waz.zclient.emoji.utils.{EmojiUtils, GifSavedDaoHelper}
import com.waz.zclient.emoji.view.MyStickerEmojiCell
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{FragmentHelper, R}
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.ContentPreviewViewer

import scala.concurrent.Future
import scala.util.{Failure, Success}

abstract class BaseGifFragment(val onEmojiChangeListener:OnEmojiChangeListener) extends FragmentHelper
  with DerivedLogTag {

  protected var gifAdapter: EmojiNewAdapter = _
  private val showData = new util.ArrayList[EmotionItemBean]()
  private var recyclerView:RecyclerListView=_

  def getData:Future[util.List[GifSavedItem]]
  def getEmptyResId:Int=R.string.emoji_gif_empty_saved
  def onLoadedData():Unit={}

  private val contentPreviewViewerDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
    override def onSend(itemBean: EmotionItemBean): Unit ={
       sendMessage(itemBean.asInstanceOf[GifSavedItem])
    }

    override def onStickerSet(itemBean: EmotionItemBean): Unit ={
      if(!TextUtils.isEmpty(itemBean.getFolderName)){
        val bean=EmojiUtils.getDbEmojiById(itemBean.getFolderId)
        if(bean!=null){
          val dialog=new EmojiStickerSetDialog(getContext);
          dialog.setOnEmojiChangeListener(onEmojiChangeListener)
          dialog.setData(bean)
          dialog.show()
        }

      }
    }

    override def onFavorite(itemBean: EmotionItemBean): Unit = {
      val userId = SpUtils.getUserId(getContext)
      val gifSavedItem=itemBean.asInstanceOf[GifSavedItem]
      if(EmojiUtils.isGifFavorite(gifSavedItem)){
        GifSavedDaoHelper.deleteSavedGif(userId, isEmojiGif = false, gifSavedItem.getMD5)
        return
      }
      if (GifSavedDaoHelper.existsSavedGif(userId, isEmojiGif = true, itemBean.getUrl)) {
        GifSavedDaoHelper.deleteSavedGif(userId, isEmojiGif = true, itemBean.getUrl)
      }
      else{
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
    inflater.inflate(R.layout.emoji_gif_content_layout, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    val fl_content:FrameLayout=view.findViewById(R.id.fl_content)
    recyclerView=new RecyclerListView(getContext){
      override def onInterceptTouchEvent(event: MotionEvent): Boolean = {
        val result = ContentPreviewViewer.getInstance.onInterceptTouchEvent(event, recyclerView, 0, contentPreviewViewerDelegate)
        super.onInterceptTouchEvent(event) || result
      }
    }
    val stickersOnItemClickListener = new RecyclerListView.OnItemClickListener() {
      override def onItemClick(view: View, position: Int): Unit = {
        LogUtils.d("JACK819", "click positon:" + position)
        if (!view.isInstanceOf[MyStickerEmojiCell]) return
        ContentPreviewViewer.getInstance.reset()
        val item=gifAdapter.getItem(position-gifAdapter.getHeaderLayoutCount)
        sendMessage(item.asInstanceOf[GifSavedItem])
      }
    }
    recyclerView.setOnTouchListener(new OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean ={
        ContentPreviewViewer.getInstance.onTouch(event, recyclerView, 0, stickersOnItemClickListener, contentPreviewViewerDelegate)
      }
    })
    recyclerView.setClipToPadding(false)
    recyclerView.setLayoutManager(new GridLayoutManager(getContext, Constants.SPAN_COUNT))
    recyclerView.addItemDecoration(new RecyclerView.ItemDecoration {
      override def getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State): Unit = {
        super.getItemOffsets(outRect, view, parent, state)
        val position = parent.getChildAdapterPosition(view)
        if (position >= gifAdapter.getHeaderLayoutCount) {
          outRect.top = ScreenUtils.dip2px(getContext, 10f)
          outRect.bottom = ScreenUtils.dip2px(getContext, 10f)
        }
      }
    })
    recyclerView.setOnItemClickListener(stickersOnItemClickListener)

    gifAdapter = new EmojiNewAdapter(showData)
    val emptyView = LayoutInflater.from(getContext).inflate(R.layout.emoji_empty_content_layout, null, false)
    emptyView.findViewById(R.id.content_textView).asInstanceOf[TextView].setText(getEmptyResId)
    gifAdapter.setEmptyView(emptyView)

    recyclerView.setAdapter(gifAdapter)

    fl_content.removeAllViews()
    fl_content.addView(recyclerView)


  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    loadData()
    GifSavedDaoHelper.registerObserver(databaseObserver)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
    GifSavedDaoHelper.unregisterObserver(databaseObserver)
  }

  private val databaseObserver = new Dao.DaoObserver() {
    override def onChange(): Unit = {
      loadData()
    }
  }


  private def sendMessage(it: GifSavedItem) = {
    if (it.getFolderName != null) {
      val model = new EmojiGifModel()
      model.msgType = MessageUtils.MessageContentUtils.EMOJI_GIF
      model.msgData = new EmojiGifModel.MsgData(it.getFolderId, it.getUrl, it.getFolderName, it.getFolderIcon,it.getName)

      inject[ConversationController].sendTextJsonMessage(MessageUtils.createEmojiGifModel(model).toString, activity = getActivity)
    } else {
      inject[ConversationController].sendMessage(ByteInput(it.getImage), getActivity)
    }
  }

  def loadData(): Unit = getData.onComplete {
    case Success(value) =>
      showData.clear()
      if (value != null && !value.isEmpty) {
        showData.addAll(value)
      }
      onLoadedData()
      gifAdapter.notifyDataSetChanged()
    case Failure(_)     =>
  }(Threading.Ui)


}

