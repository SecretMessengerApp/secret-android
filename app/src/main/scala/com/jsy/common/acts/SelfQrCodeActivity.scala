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

import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import android.widget.{ImageView, Toast}
import androidx.appcompat.widget.Toolbar
import com.jsy.common.ConversationApi
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.{QrCodeContentModel, SearchUserInfo}
import com.jsy.common.utils.QrCodeUtil.SaveImageCallBack
import com.jsy.common.utils.{QrCodeUtil, ToastUtil}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{Mime, UserData}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.BitmapUtils
import com.waz.zclient.utils.{SpUtils, UiStorage, UserSignal}
import com.waz.zclient.{BaseActivity, R}

import java.util

class SelfQrCodeActivity extends BaseActivity with DerivedLogTag {

  lazy val toolBarSelfQrCode: Toolbar = findById(R.id.toolBarSelfQrCode)
  lazy val chatHeadView: ChatHeadViewNew = findById(R.id.chatHeadView)
  lazy val tvUserName: TypefaceTextView = findById(R.id.tvUserName)
  lazy val chatHeadViewCenter: ChatHeadViewNew = findById(R.id.chatHeadViewCenter)
  lazy val ivQrCode: ImageView = findById(R.id.ivQrCode)
  lazy val llSaveQrCode: ViewGroup = findById(R.id.llSaveQrCode)
  lazy val prlQrCodeLayer: ViewGroup = findById(R.id.prlQrCodeLayer)
  lazy val prlQrCodeLayerAll: ViewGroup = findById(R.id.prlQrCodeLayerAll)

  lazy implicit val uiStorage = inject[UiStorage]
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
  } yield self


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings_self_qrcode)

    toolBarSelfQrCode.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })
    self.onUi { self =>
      chatHeadView.setUserData(self)
      tvUserName.setTransformedText(self.getDisplayName)
      chatHeadViewCenter.setUserData(self)

      showQrCode(self)

      llSaveQrCode.setOnClickListener(new OnClickListener {
        override def onClick(v: View): Unit = {
          llSaveQrCode.setEnabled(false)
          QrCodeUtil.saveImageToGallery(SelfQrCodeActivity.this, prlQrCodeLayerAll, Mime.Image.WebP, new SaveImageCallBack {
            override def onFail(): Unit = {
              verbose(l"saveImageToGallery onFail")
              Threading.Ui.execute(new Runnable {
                override def run(): Unit = {
                  llSaveQrCode.setEnabled(true)
                  Toast.makeText(SelfQrCodeActivity.this, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
                }
              })
            }

            override def onSuccess(uri: Uri): Unit = {
              verbose(l"saveImageToGallery onSuccess:$uri.getPath")
              Threading.Ui.execute(new Runnable {
                override def run(): Unit = {
                  llSaveQrCode.setEnabled(true)
                  Toast.makeText(SelfQrCodeActivity.this, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
                }
              })
            }
          })

        }
      })
    }
  }

  override def canUseSwipeBackLayout = true

  def showQrCode(self: UserData) = {
    self.extid match {
      case Some(extid) if (!TextUtils.isEmpty(extid)) =>
        val codeContent = new Uri.Builder()
          .scheme(QrCodeContentModel.QRCODE_URI_SCHEME)
          .authority(QrCodeContentModel.QRCODE_TYPE_USER)
          .appendPath(extid)
          .build().toString
        ivQrCode.setImageBitmap(BitmapUtils.getRQcode(codeContent))
      case _ =>
        reqUserExtId(self)
    }
  }

  def reqUserExtId(self: UserData) = {
    showProgressDialog(R.string.secret_data_loading)
    SpecialServiceAPI.getInstance.get(ImApiConst.REQ_USER_KEY, null, false, new OnHttpListener[SearchUserInfo]() {

      override def onFail(code: Int, err: String): Unit = {
        verbose(l"reqUserExtId onFail code:$code, err:$err")
        val userId: String = self.id.str
        val handleStr = if (self.handle.isEmpty) "" else self.handle.head.string
        val rAssestId = SpUtils.getRemoteAssetId(SelfQrCodeActivity.this, "")
        val codeContent = new QrCodeContentModel(QrCodeContentModel.TYPE_FRIEND, userId, self.getDisplayName, handleStr, rAssestId).createJson().toString()
        ivQrCode.setImageBitmap(BitmapUtils.getRQcode(codeContent))
        dismissProgressDialog()
      }

      override def onSuc(userInfo: SearchUserInfo, orgJson: String): Unit = {
        verbose(l"reqUserExtId object onSuc orgJson:$orgJson")
        val extid: String = if (null != userInfo) userInfo.getExtid else null
        if (!TextUtils.isEmpty(extid)) {
          val codeContent = new Uri.Builder()
            .scheme(QrCodeContentModel.QRCODE_URI_SCHEME)
            .authority(QrCodeContentModel.QRCODE_TYPE_USER)
            .appendPath(extid)
            .build().toString
          ivQrCode.setImageBitmap(BitmapUtils.getRQcode(codeContent))
          ConversationApi.updateUserData(self.updatedExtid(Some(extid)))
        } else ToastUtil.toastByResId(SelfQrCodeActivity.this, R.string.secret_code_get_fail)
        dismissProgressDialog()
      }

      override def onSuc(r: util.List[SearchUserInfo], orgJson: String): Unit = {
        verbose(l"reqUserExtId list onSuc orgJson:$orgJson")
        dismissProgressDialog()
      }
    })
  }

}
