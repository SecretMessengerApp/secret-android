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
package com.jsy.common.fragment

import android.Manifest
import android.app.{Activity, AlertDialog}
import android.content.pm.PackageManager
import android.content.{Context, DialogInterface, Intent}
import android.os.Bundle
import android.provider.MediaStore.MediaColumns
import android.text.{Editable, TextUtils, TextWatcher}
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{EditText, ImageView, TextView}
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.{GridLayoutManager, RecyclerView}
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.chad.library.adapter.base.{BaseQuickAdapter, BaseViewHolder}
import com.google.gson.{JsonArray, JsonObject}
import com.jsy.common.fragment.ConvReportContentFragment.SelectPicBean
import com.jsy.common.fragment.ConvReportTypeFragment.ReportTypeBean
import com.jsy.common.httpapi._
import com.jsy.common.model.ConvReportModel
import com.jsy.common.utils.{FileUtil, MD5Util, SelfStartSetting, ToastUtil}
import com.jsy.common.views.camera.CameraARPicView
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.log.LogUI._
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.utils.{IntentUtils, StringUtils}
import com.waz.zclient.{FragmentHelper, R}
import okhttp3.MultipartBody
import top.zibin.luban.{Luban, OnCompressListener, OnRenameListener}

import java.io.File
import java.util

class ConvReportContentFragment extends BaseFragment[ConvReportContentFragment.Container]
  with FragmentHelper
  with View.OnClickListener
  with DerivedLogTag {

  import ConvReportContentFragment._

  private lazy val convController = inject[ConversationController]

  private var rootView: View = null
  private var mToolbar: Toolbar = null
  private var contentEdit: EditText = null
  private var contentNum: TextView = null
  private var imageRecycler: RecyclerView = null
  private var reportSubmitBtn: TextView = null
  private var imageAdapter: ConvReportContentImageAdapter = null
  private var typeBean: ReportTypeBean = null

  private var reportContent: String = ""
  private var reportImages: java.util.List[String] = new util.ArrayList[String]
  private var imageUrls: java.util.List[String] = new util.ArrayList[String]
  private var isUploading: Boolean = false

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    val bundle: Bundle = getArguments
    typeBean = bundle.getSerializable(classOf[ReportTypeBean].getSimpleName).asInstanceOf[ReportTypeBean]
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
      rootView
    } else {
      rootView = inflater.inflate(R.layout.fragment_conv_report_content, container, false)
      rootView
    }
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    mToolbar = view.findViewById(R.id.report_content_toolbar)
    contentEdit = view.findViewById(R.id.report_content_edit)
    contentNum = view.findViewById(R.id.report_content_num)
    imageRecycler = view.findViewById(R.id.report_image_recycler)
    reportSubmitBtn = view.findViewById(R.id.report_submit_btn)
    mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
      override def onClick(view: View): Unit = {
        onBackPressed()
      }
    })
    reportSubmitBtn.setOnClickListener(this)
    imageRecycler.setLayoutManager(new GridLayoutManager(getActivity, MaxImage))
    imageAdapter = new ConvReportContentImageAdapter(getActivity)
    imageRecycler.setAdapter(imageAdapter)
    contentEdit.addTextChangedListener(new TextWatcher() {
      override def beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int): Unit = {

      }

      override def onTextChanged(s: CharSequence, start: Int, before: Int, count: Int): Unit = {
        contentNum.setText(s.length() + "/" + MaxText)
      }

      override def afterTextChanged(s: Editable): Unit = {
      }
    })
    imageAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener {
      override def onItemChildClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        val item: SelectPicBean = adapter.getItem(position).asInstanceOf[SelectPicBean]
        val count: Int = adapter.getItemCount
        val lastItem: SelectPicBean = adapter.getItem(count - 1).asInstanceOf[SelectPicBean]
        if (null != item && lastItem != null) {
          if (!item.isAddBtn && view.getId == R.id.iv_picture_del) {
            imageAdapter.remove(position)
            if (!lastItem.isAddBtn) {
              imageAdapter.addData(initImageSeletBean())
            }
            imageAdapter.notifyDataSetChanged()
          } else if (item.isAddBtn && view.getId == R.id.iv_select_picture) {
            if (count >= MaxImage && !lastItem.isAddBtn) {
              ToastUtil.toastByString(getActivity, getString(R.string.picture_max_num, String.valueOf(MaxImage)))
            } else {
              selectPicture()
            }
          }
        }
      }
    })
  }

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)
    imageAdapter.addData(initImageSeletBean())
    imageAdapter.notifyDataSetChanged()
  }

  private def initImageSeletBean(): SelectPicBean = {
    SelectPicBean("", true)
  }

  private def selectPicture(): Unit = {
    Option(getActivity).foreach{ tempActivity =>
      if (ContextCompat.checkSelfPermission(tempActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ActivityCompat.requestPermissions(tempActivity, Array[String](Manifest.permission.WRITE_EXTERNAL_STORAGE), SELECT_PICTURE_PERMISSON)
      else {
        startActivityForResult(IntentUtils.getPictureIntent, SELECT_PICTURE_REQUESTCODE)
      }
    }
  }

  override def onRequestPermissionsResult(requestCode: Int, permissions: Array[String], grantResults: Array[Int]): Unit = {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == SELECT_PICTURE_PERMISSON) {
      if (grantResults(0) == PackageManager.PERMISSION_GRANTED) {
        startActivityForResult(IntentUtils.getPictureIntent, SELECT_PICTURE_REQUESTCODE)
      } else {
        val alertDialog = new AlertDialog.Builder(getActivity)
        alertDialog.setTitle(getResources.getString(R.string.secret_open_permission)).setMessage(getResources.getString(R.string.secret_open_permission_tip2))
        alertDialog.setCancelable(false)
        alertDialog.setNegativeButton(getResources.getString(R.string.secret_permission_refuse), new DialogInterface.OnClickListener() {
          override def onClick(dialogInterface: DialogInterface, i: Int): Unit = {
            dialogInterface.dismiss()
          }
        }).setPositiveButton(getResources.getString(R.string.secret_permission_allow), new DialogInterface.OnClickListener() {
          override def onClick(dialog: DialogInterface, which: Int): Unit = {
            SelfStartSetting.openAppDetailSetting(getActivity)
          }
        })
        val dialog = alertDialog.create
        dialog.show()
      }
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode == Activity.RESULT_OK && requestCode == SELECT_PICTURE_REQUESTCODE) {
      val selectPicturePath = getSelectPicturePath(data)
      if (StringUtils.isNotBlank(selectPicturePath)) {
        val count: Int = imageAdapter.getItemCount
        val lastItem: SelectPicBean = imageAdapter.getItem(count - 1)
        if (count >= MaxImage && !lastItem.isAddBtn) {
          ToastUtil.toastByString(getActivity, getString(R.string.picture_max_num, String.valueOf(MaxImage)))
        } else {
          if (count >= MaxImage) {
            imageAdapter.remove(count - 1)
          }
          imageAdapter.addData(count - 1, getImageBean(selectPicturePath))
          imageAdapter.notifyDataSetChanged()
        }
      }
    }
  }

  private def getSelectPicturePath(data: Intent): String = {
    if (null == data) return null
    var selectPicturePath: String = null
    val proj = Array(MediaColumns.DATA)
    val cursor = getActivity.getContentResolver.query(data.getData, proj, null, null, null)
    if (cursor.moveToFirst) {
      val column_index = cursor.getColumnIndexOrThrow(MediaColumns.DATA)
      selectPicturePath = cursor.getString(column_index)
      if (selectPicturePath == null) selectPicturePath = FileUtil.getPath(getContext, data.getData)
    }
    cursor.close()
    selectPicturePath
  }

  private def getImageBean(picPath: String) = {
    SelectPicBean(picPath, false)
  }

  override def onClick(v: View): Unit = {
    if (v.getId == R.id.report_submit_btn) {
      val unblockStr = contentEdit.getText.toString
      if (TextUtils.isEmpty(unblockStr)) {
        ToastUtil.toastByResId(getActivity, R.string.cursor__type_a_message)
      } else {
        if (!isUploading) {
          val imageList: java.util.List[SelectPicBean] = imageAdapter.getData
          val count: Int = imageAdapter.getItemCount
          if (count <= 1) {
            ToastUtil.toastByResId(getActivity, R.string.toast_conv_report_min_pic)
          } else {
            isUploading = true
            showProgressDialog()
            reportContent = unblockStr
            reportImages.clear()
            imageUrls.clear()
            val firstItem: SelectPicBean = imageAdapter.getItem(0)
            if (null == firstItem || (firstItem.isAddBtn && count == 1)) {
              uploadReportContent(reportContent)
            } else {
              for (i <- 0 until count) {
                val picBean: SelectPicBean = imageList.get(i)
                if (!picBean.isAddBtn && !TextUtils.isEmpty(picBean.picPath)) {
                  reportImages.add(picBean.picPath)
                }
              }
              startUploadImage()
            }
          }
        }
      }
    }
  }

  def startUploadImage(): Unit = {
    val imageSize = if (null == reportImages) 0 else reportImages.size()
    val urlSize = if (null == imageUrls) 0 else imageUrls.size()
    if (urlSize >= imageSize) {
      uploadReportContent(reportContent, imageUrls)
    } else {
      uploadSingleImage(reportImages.get(urlSize))
    }
  }

  def uploadSingleImage(imagePath: String): Unit = {
    if (!TextUtils.isEmpty(imagePath)) {
      try {
        Luban.`with`(getActivity).load(imagePath)
          .ignoreBy(picIgnoreSize)
          .setTargetDir(CameraARPicView.getARFileDirectory(getActivity).getPath)
          .setRenameListener(new OnRenameListener() {
            override def rename(filePath: String): String = {
              "compress_" + MD5Util.MD5(filePath)
            }
          }).setCompressListener(new OnCompressListener {
          override def onStart(): Unit = {
            verbose(l"uploadSingleImage setCompressListener onStart:${Option(imagePath)}")
          }

          override def onSuccess(file: File): Unit = {
            verbose(l"uploadSingleImage setCompressListener onSuccess:${Option(file.getPath)}")
            uploadImage(file.getPath)
          }

          override def onError(e: Throwable): Unit = {
            verbose(l"uploadSingleImage setCompressListener onError:${e.getMessage}")
            uploadImage(imagePath)
          }
        }).launch()
      } catch {
        case e: Exception =>
          e.printStackTrace()
          verbose(l"uploadSingleImage  Exception:${e.getMessage}")
          uploadImage(imagePath)
      }
    } else {
      imageUrls.add("")
      startUploadImage()
    }
  }

  def getUploadImageMsg(filePath: String) = {
    val file: File = if (TextUtils.isEmpty(filePath)) null else new File(filePath)
    val fileSize = if (null == file) 0L else file.length()
    val fileMd5: String = if (fileSize == 0L) "" else FileUtil.fileToMD5(file) /*AESUtils.base64(IoUtils.md5(new FileInputStream(file)))*/
    (file, fileMd5, fileSize)
  }

  def uploadImage(imagePath: String) = {
    val (file, fileMd5, fileSize) = getUploadImageMsg(imagePath)
    verbose(l"uploadImage() imagePath file:${Option(file.getPath)}, fileMd5:$fileMd5, fileSize:$fileSize")
    if (fileSize > 0L) {
      val builder = new MultipartBody.Builder().setType(MultipartBody.FORM)
      builder.addFormDataPart("size", String.valueOf(fileSize))
      builder.addFormDataPart("md5", fileMd5)
      val requestBody = ServerAPI.getFileBody(file)
      builder.addFormDataPart("file", fileMd5, requestBody)
      SpecialServiceAPI.getInstance().put(ImApiConst.APP_REPORT_UPLOAD_FILE, builder.build(), new OnHttpListener[ConvReportModel] {

        override def onFail(code: Int, err: String): Unit = {
          verbose(l"uploadImage() imagePath onFail code:$code, err:${Option(err)}")
          reqReset()
          val toastMsg: String = code match {
            case 512 =>
              getString(R.string.toast_conv_report_nocreate)
            case 513 =>
              getString(R.string.toast_conv_report_noblocked)
            case 514 =>
              getString(R.string.toast_conv_report_noconv)
            case 515 =>
              getString(R.string.toast_conv_report_appeal_always)
            case 516 =>
              getString(R.string.toast_conv_report_always)
            case _ =>
              getString(R.string.content__file__status__upload_failed__minimized)
          }
          ToastUtil.toastByString(getActivity, toastMsg)
        }

        override def onSuc(r: ConvReportModel, orgJson: String): Unit = {
          verbose(l"uploadImage() imagePath onSuc 1 orgJson:${Option(orgJson)}")
          val isOk = if (null == r) false else r.isOk
          if (isOk) {
            val imageUrl = if (null == r.getResult) "" else r.getResult.getUrl
            imageUrls.add(imageUrl)
            startUploadImage()
          } else {
            onFail(if (null == r) -400 else r.getError_code, if (null == r) "null == r" else r.getDescription)
          }
        }

        override def onSuc(r: util.List[ConvReportModel], orgJson: String): Unit = {
          verbose(l"uploadImage() imagePath onSuc 2 orgJson:${Option(orgJson)}")
          reqReset()
        }
      })
    } else {
      imageUrls.add("")
      startUploadImage()
    }
  }

  def uploadReportContent(contentStr: String, imageUrls: java.util.List[String] = null) = {
    convController.currentConv.currentValue.foreach {
      conv =>
        val urlPath = String.format(ImApiConst.APP_REPORT_UPLOAD_CONTENT, conv.remoteId.str)
        val contentJson = new JsonObject
        val size = if (null == imageUrls) 0 else imageUrls.size()
        if (size > 0) {
          val arrayJson = new JsonArray
          for (i <- 0 until size) {
            val imageUrl = imageUrls.get(i)
            if (!TextUtils.isEmpty(imageUrl)) {
              arrayJson.add(imageUrl)
            }
          }
          contentJson.add("photos", arrayJson)
        }
        contentJson.addProperty("content", contentStr)
        contentJson.addProperty("typ", if (null == typeBean) 0 else typeBean.typeId)
        SpecialServiceAPI.getInstance().put(urlPath, contentJson.toString, new OnHttpListener[ConvReportModel] {

          override def onFail(code: Int, err: String): Unit = {
            verbose(l"uploadReportContent() contentStr onFail code:$code, err:${Option(err)}")
            reqReset()
            val toastMsg: String = code match {
              case 512 =>
                getString(R.string.toast_conv_report_nocreate)
              case 513 =>
                getString(R.string.toast_conv_report_noblocked)
              case 514 =>
                getString(R.string.toast_conv_report_noconv)
              case 515 =>
                getString(R.string.toast_conv_report_appeal_always)
              case 516 =>
                getString(R.string.toast_conv_report_always)
              case _ =>
                getString(R.string.toast_conv_report_fail)
            }
            ToastUtil.toastByString(getActivity, toastMsg)
          }

          override def onSuc(r: ConvReportModel, orgJson: String): Unit = {
            verbose(l"uploadReportContent() contentStr onSuc 1 orgJson:${Option(orgJson)}")
            val isOk = if (null == r) false else r.isOk
            if (isOk) {
              reqReset()
              ToastUtil.toastByResId(getActivity, R.string.toast_conv_report_suc)
              getActivity.finish()
            } else {
              onFail(if (null == r) -401 else r.getError_code, if (null == r) "null == r" else r.getDescription)
            }
          }

          override def onSuc(r: util.List[ConvReportModel], orgJson: String): Unit = {
            verbose(l"uploadReportContent() contentStr onSuc 2 orgJson:${Option(orgJson)}")
            reqReset()
          }
        })
    }
  }

  def reqReset(): Unit = {
    isUploading = false
    reportContent = ""
    reportImages.clear()
    imageUrls.clear()
    dismissProgressDialog()
  }


  override def onBackPressed(): Boolean = {
    getParentFragment.getChildFragmentManager.popBackStackImmediate(ConvReportContentFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    true
  }

  override def onDestroyView(): Unit = {
    if (rootView != null) {
      val parent = rootView.getParent.asInstanceOf[ViewGroup]
      if (parent != null) parent.removeView(rootView)
    }
    super.onDestroyView()
    reqReset()
  }
}

object ConvReportContentFragment {
  val Tag: String = getClass.getSimpleName
  val SELECT_PICTURE_REQUESTCODE: Int = 1003
  val SELECT_PICTURE_PERMISSON: Int = 1004
  val picIgnoreSize: Int = 200 // KB
  val MaxText: Int = 200
  val MaxImage: Int = 3

  def newInstance(typeBean: ReportTypeBean = null): ConvReportContentFragment = {
    val fragment: ConvReportContentFragment = new ConvReportContentFragment
    val bundle = new Bundle()
    bundle.putSerializable(classOf[ReportTypeBean].getSimpleName, typeBean)
    fragment.setArguments(bundle)
    fragment
  }

  trait Container {
  }

  case class SelectPicBean(picPath: String,
                           isAddBtn: Boolean)

}

class ConvReportContentImageAdapter(context: Context) extends BaseQuickAdapter[SelectPicBean, BaseViewHolder](R.layout.adapter_conv_report_content_image) {
  override def convert(helper: BaseViewHolder, item: SelectPicBean): Unit = {
    if (item.isAddBtn) {
      helper.addOnClickListener(R.id.iv_select_picture)
      helper.setVisible(R.id.iv_picture_del, false)
      helper.setImageResource(R.id.iv_select_picture, R.drawable.icon_add_pictrue_report)
    } else {
      helper.addOnClickListener(R.id.iv_picture_del)
      helper.setVisible(R.id.iv_picture_del, true)
      val imageView: ImageView = helper.getView(R.id.iv_select_picture).asInstanceOf[ImageView]
      Glide.`with`(context).load(item.picPath).apply(RequestOptions.diskCacheStrategyOf(DiskCacheStrategy.NONE)).into(imageView)
    }
  }
}
