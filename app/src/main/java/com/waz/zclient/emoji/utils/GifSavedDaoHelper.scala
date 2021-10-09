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
package com.waz.zclient.emoji.utils

import java.util

import com.j256.ormlite.dao.Dao
import com.jsy.secret.sub.swipbackact.utils.LogUtils
import com.waz.zclient.ZApplication
import com.waz.zclient.emoji.bean.{EmotionItemBean, GifSavedItem}
import com.waz.zclient.log.LogUI

import scala.util.Try

object GifSavedDaoHelper {

  private lazy val gifSavedItemDao = ZApplication.getInstance().getOrmliteDbHelper.getGifSavedItemDao

  def getCount(userId:String) ={
    val builder=gifSavedItemDao.queryBuilder()
    val gifFavorites=builder.where().eq("user_id", userId).and().isNull("folder_name").and().eq("is_recently", false).countOf()
    val stickerFavorites=builder.where().eq("user_id", userId).and().isNotNull("folder_name").and().eq("is_recently", false).countOf()
    val recently:Long=builder.where().eq("user_id", userId).and().eq("is_recently", true).countOf()
    LogUtils.d("JACK8",s"getCount:${gifFavorites},${stickerFavorites},${recently}")
    util.Arrays.asList(gifFavorites,stickerFavorites,recently)
  }

  def addRecently(userId: String, itemBean: EmotionItemBean): Unit = {
    val savedItem = gifSavedItemDao.queryBuilder()
      .where().eq("user_id", userId)
      .and().eq("gif_url", itemBean.getUrl)
      .and().eq("is_recently", true)
      .queryForFirst()

    Try {
      if (savedItem == null && itemBean.getFolderName != null) {
        val data = new GifSavedItem()
        data.setUserId(userId)
        data.setFile(itemBean.getFile)
        data.setName(itemBean.getName)
        data.setUrl(itemBean.getUrl)
        data.setFolderId(itemBean.getFolderId)
        data.setFolderName(itemBean.getFolderName)
        data.setFolderIcon(itemBean.getFolderIcon)
        data.setRecently(true)

        gifSavedItemDao.createOrUpdate(data)
      }
    }
  }

  def getSavedGIFs(userId: String, folderNameIsNull:Boolean,isRecently: Boolean = false): util.List[GifSavedItem] = {
    Try {
      if(folderNameIsNull){
        gifSavedItemDao.queryBuilder()
          .orderBy("id", false)
          .limit(20L)
          .where().eq("user_id", userId)
          .and()
          .isNull("folder_name")
          .and().eq("is_recently", isRecently)
          .query()
      }
      else {
        gifSavedItemDao.queryBuilder()
          .orderBy("id", false)
          .limit(20L)
          .where().eq("user_id", userId)
          .and()
          .isNotNull("folder_name")
          .and().eq("is_recently", isRecently)
          .query()
      }
    }.getOrElse(new util.ArrayList[GifSavedItem]())
  }

  def deleteRecently(userId: String, dataId: Long = -1): Int = {
    if (dataId > 0) {
      gifSavedItemDao.deleteById(dataId)
    } else {
      val builder = gifSavedItemDao.deleteBuilder()
      builder.where().eq("user_id", userId)
        .and().eq("is_recently", true)
      builder.delete()
    }
  }

  def existsSavedGif(userId: String, isEmojiGif: Boolean, content: String): Boolean = {
    gifSavedItemDao.queryBuilder()
      .where().eq("user_id", userId)
      .and().eq("is_recently", false)
      .and().eq(if (isEmojiGif) "gif_url" else "data_md5", content)
      .countOf() > 0
  }

  def deleteSavedGif(userId: String, isEmojiGif: Boolean, content: String): Unit = {
    val deleteBuilder = gifSavedItemDao.deleteBuilder()
    deleteBuilder.where().eq("user_id", userId)
      .and().eq("is_recently", false)
      .and().eq(if (isEmojiGif) "gif_url" else "data_md5", content)
    deleteBuilder.delete()
  }

  def saveGif(gifSavedItem: GifSavedItem): Unit = {
    gifSavedItemDao.createOrUpdate(gifSavedItem)
  }

  def registerObserver(observer: Dao.DaoObserver): Unit = {
    gifSavedItemDao.registerObserver(observer)
  }

  def unregisterObserver(observer: Dao.DaoObserver): Unit = {
    gifSavedItemDao.unregisterObserver(observer)
  }
}
