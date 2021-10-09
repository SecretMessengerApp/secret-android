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
package com.jsy.common.acts

import android.content.{Context, Intent}
import android.net.Uri
import android.os.Bundle
import android.view.View.OnClickListener
import android.view.{View, ViewGroup}
import android.widget.{ImageView, Toast}
import androidx.appcompat.widget.Toolbar
import com.jsy.common.model.QrCodeContentModel
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.MessageUtils.MessageContentUtils
import com.jsy.common.utils.QrCodeUtil
import com.jsy.common.utils.QrCodeUtil.SaveImageCallBack
import com.waz.api.IConversation
import com.waz.model.Mime
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.conversationlist.views.ConversationAvatarView
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.BitmapUtils
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{BaseActivity, R}


object GroupQRCodeActivity {

  private val SHARE_URL = "SHARE_URL"

  def start(context: Option[Context] = None, url: String): Unit = {
    context.foreach { it =>
      val intent = new Intent(it, classOf[GroupQRCodeActivity])
      intent.putExtra(SHARE_URL, url)
      it.startActivity(intent)
    }
  }
}

class GroupQRCodeActivity extends BaseActivity {

  lazy val toolBarSelfQrCode: Toolbar = findById(R.id.toolBarSelfQrCode)
  lazy val chatHeadView: ConversationAvatarView = findById(R.id.chatHeadView)
  lazy val tvUserName: TypefaceTextView = findById(R.id.tvUserName)
  lazy val chatHeadViewCenter: ImageView = findById(R.id.chatHeadViewCenter)
  lazy val ivQrCode: ImageView = findById(R.id.ivQrCode)
  lazy val llSaveQrCode: ViewGroup = findById(R.id.llSaveQrCode)
  lazy val prlQrCodeLayer: ViewGroup = findById(R.id.prlQrCodeLayer)
  lazy val prlQrCodeLayerAll: ViewGroup = findById(R.id.prlQrCodeLayerAll)
  private var shareUrl: String = null
  //  private val conversationId = Signal[Option[ConvId]]()


  lazy implicit val uiStorage = inject[UiStorage]
  lazy val zms = inject[Signal[ZMessaging]]
  lazy val controller = inject[ConversationListController]

  private lazy val convController = inject[ConversationController]
  //  private var conversationData : ConversationData = null

  lazy val self = for {
    zms <- zms
    self <- UserSignal(zms.selfUserId)
  } yield self

  override def canUseSwipeBackLayout() = true


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_group_qrcode)

    shareUrl = getIntent.getStringExtra(GroupQRCodeActivity.SHARE_URL)

    convController.currentConv.currentValue.foreach { conversationData =>
      tvUserName.setTransformedText(conversationData.displayName)
      chatHeadView.setConversationType(IConversation.Type.ONE_TO_ONE)
      val defaultRes = MessageContentUtils.getGroupDefaultAvatar(conversationData.id)
      convController.currentConv.map(_.assets).onUi { assets =>
        assets.fold {
          chatHeadView.avatarSingle.setImageResource(defaultRes)
        } { assets =>
          chatHeadView.avatarSingle.loadImageUrlPlaceholder(CircleConstant.appendAvatarUrl(conversationData.smallRAssetId.str, this), defaultRes)
        }
      }
    }

    //    conversationId.publish(Some(conversationData.id), Threading.Background)

    toolBarSelfQrCode.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })
    //    tvUserName.setTransformedText(conversationData.displayName)
    //    val members = conversationId.collect { case Some(convId) => convId } flatMap controller.members
    //
    //    val membersSeq = for{
    //      z <- zms
    //      memberIds <- members
    //      memberSeq <- Signal.sequence(memberIds.map(uid => UserSignal(uid)): _*)
    //    }yield {
    //      memberSeq.filter(_.id != z.selfUserId)
    //    }
    //    membersSeq.on(Threading.Background) {
    //      case members =>
    //        chatHeadView.setMembers(members.map(_.id), conversationData.id, conversationData.convType)
    //        chatHeadView.setConversationType(conversationData.convType)
    //    }


    //    val codeContent = new QrCodeContentModel(QrCodeContentModel.GROUP_URL, shareUrl).createGroupUrlJson().toString()
    val codeContent = new Uri.Builder()
      .scheme(QrCodeContentModel.QRCODE_URI_SCHEME)
      .authority(QrCodeContentModel.QRCODE_TYPE_GROUP)
      .appendQueryParameter(QrCodeContentModel.QRCODE_LINK_GROUP_QUERYKEY, shareUrl)
      .build().toString /*+ "?" +QrCodeContentModel.QRCODE_LINK_GROUP_QUERYKEY + "=" + shareUrl*/

    ivQrCode.setImageBitmap(BitmapUtils.getRQcode(codeContent))
    llSaveQrCode.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        llSaveQrCode.setEnabled(false)
        QrCodeUtil.saveImageToGallery(GroupQRCodeActivity.this, prlQrCodeLayerAll, Mime.Image.WebP, new SaveImageCallBack {
          override def onFail(): Unit = {
            Threading.Ui.execute(new Runnable {
              override def run(): Unit = {
                llSaveQrCode.setEnabled(true)
                Toast.makeText(GroupQRCodeActivity.this, R.string.content__file__action__save_error, Toast.LENGTH_SHORT).show()
              }
            })
          }

          override def onSuccess(uri: Uri): Unit = {
            Threading.Ui.execute(new Runnable {
              override def run(): Unit = {
                llSaveQrCode.setEnabled(true)
                Toast.makeText(GroupQRCodeActivity.this, R.string.message_bottom_menu_action_save_ok, Toast.LENGTH_SHORT).show()
              }
            })
          }
        })

      }
    })
  }

}
