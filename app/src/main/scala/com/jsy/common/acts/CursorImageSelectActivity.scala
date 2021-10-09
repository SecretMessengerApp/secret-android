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
package com.jsy.common.acts

import java.util

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import android.widget._
import androidx.recyclerview.widget.{GridLayoutManager, LinearLayoutManager, RecyclerView, SimpleItemAnimator}
import com.jsy.common.acts.CursorImageSelectActivity._
import com.jsy.common.adapter.{ImageGridAdapter, PictureAlbumDirectoryAdapter}
import com.jsy.common.config.{PictureConfig, PictureSelectionConfig}
import com.jsy.common.model.circle.LocalMedia
import com.jsy.common.model.{LocalMediaFolder, LocalMediaLoader}
import com.jsy.common.utils.{AnimaUtil, DoubleUtils}
import com.jsy.common.views.FolderPopWindow
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.pages.extendedcursor.image.CursorImagesItemDecoration
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout.MultipleImageSendCallback
import com.waz.zclient.{BaseActivity, R}

class CursorImageSelectActivity extends BaseActivity with View.OnClickListener

  with PictureAlbumDirectoryAdapter.OnItemClickListener {

  override def canUseSwipeBackLayout = true

  private val IMAGE_ROWS = 3
  private var mRlCursorSelect: RelativeLayout = _
  private var mTvCenterTitle: TextView = _
  private var mTvArrow: TextView = _
  private var mTvCancle: TextView = _
  private var mPictureRecycler: RecyclerView = _
  private var mCompressCheck: CheckBox = _
  private var mBtnSend: Button = _
  private var folderWindow: FolderPopWindow = _
  private var images: util.List[LocalMedia] = new util.ArrayList[LocalMedia]
  private var mediaLoader: LocalMediaLoader = _
  private var adapter: ImageGridAdapter = _


  override def onCreate(savedInstanceState: Bundle): Unit = {

    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_cursor_images_select)

    mediaLoader = new LocalMediaLoader(this, PictureConfig.TYPE_ALL, true)
    mRlCursorSelect = ViewUtils.getView(this, R.id.rl_cursor_image_select)
    mTvCenterTitle = ViewUtils.getView(this, R.id.tvCentreTitle)
    mTvArrow = ViewUtils.getView(this, R.id.tv_arrow)
    mTvCancle = ViewUtils.getView(this, R.id.tvCancle)
    mPictureRecycler = ViewUtils.getView(this, R.id.picture_recycler)
    mCompressCheck = ViewUtils.getView(this, R.id.compress_checkBox)
    mBtnSend = ViewUtils.getView(this, R.id.bt_multiple_send)

    mTvCancle.setOnClickListener(this)
    mBtnSend.setOnClickListener(this)
    mTvCenterTitle.setOnClickListener(this)

    val layout = new GridLayoutManager(this, IMAGE_ROWS,LinearLayoutManager.VERTICAL, false)
    mPictureRecycler.setLayoutManager(layout)
    val dividerSpacing = getResources.getDimensionPixelSize(R.dimen.extended_container__camera__gallery_grid__divider__spacing)
    mPictureRecycler.addItemDecoration(new CursorImagesItemDecoration(dividerSpacing))
    mPictureRecycler.getItemAnimator.asInstanceOf[SimpleItemAnimator].setSupportsChangeAnimations(false)

    adapter = new ImageGridAdapter(this, PictureSelectionConfig.getInstance,LinearLayoutManager.VERTICAL)
    folderWindow = new FolderPopWindow(this, PictureConfig.TYPE_IMAGE)
    folderWindow.setOnItemClickListener(this)
    folderWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
      override def onDismiss(): Unit = {
        if (DoubleUtils.isFastDoubleClick(500)) return
        AnimaUtil.rotateArrow(mTvArrow, true)
      }
    })

    loadImages()

  }


  override def onClick(view: View): Unit = {
    val vId = view.getId

    if (vId == R.id.tvCancle) {
      if (folderWindow.isShowing) folderWindow.dismiss()
      finish()
    } else if (vId == R.id.tvCentreTitle) {
      if (folderWindow.isShowing) folderWindow.dismiss()
      else {
        AnimaUtil.rotateArrow(mTvArrow, false)
        if (haveImages) {
          folderWindow.showAsDropDown(mRlCursorSelect)
          folderWindow.notifyDataCheckedStatus(getSelectImages)
        }
      }
    } else if (vId == R.id.bt_multiple_send) {
      val sendImages = getSelectImages
      if (sendImages != null && sendImages.size() > 0) {
        callBack.sendMultipleImages(sendImages, mCompressCheck.isChecked)
        finish()
      }
    }
  }

  def loadImages(): Unit = {
    mediaLoader.loadAllMedia(new LocalMediaLoader.LocalMediaLoadListener() {
      override def loadComplete(folders: util.List[LocalMediaFolder]): Unit = {
        if (folders.size > 0) {
          val folder = folders.get(0)
          folder.setChecked(true)
          val localImg = folder.getImages

          if (localImg.size >= images.size) {
            images = localImg
            bindFolder(folders)
          }
        }
        if (adapter != null) {
          if (images == null) images = new util.ArrayList[LocalMedia]
          adapter.bindImagesData(images)
          mPictureRecycler.setAdapter(adapter)
        }
      }
    })
  }


  def bindFolder(folders: util.List[LocalMediaFolder]): Unit = {
    folderWindow.bindFolder(folders)
  }

  def haveImages: Boolean = if (images != null && images.size > 0) true else false

  def getSelectImages: util.List[LocalMedia] = {
    if (adapter == null) new util.ArrayList[LocalMedia]
    else adapter.getSelectedImages
  }

  override def onItemClick(folderName: String, images: util.List[LocalMedia]): Unit = {
    folderWindow.dismiss()
    mTvCenterTitle.setText(folderName)
    adapter.bindImagesData(images)
  }
}

object CursorImageSelectActivity {

  var callBack: MultipleImageSendCallback = _

  def setCallBack(callBack: MultipleImageSendCallback) = {
    this.callBack = callBack
  }

  def startSelf(context: Context): Unit = {
    context.startActivity(new Intent(context, classOf[CursorImageSelectActivity]))
  }
}
