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
package com.waz.zclient.conversation

import android.content.{Context, Intent}
import android.os.{Bundle, PersistableBundle}
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.widget.Toolbar
import com.waz.service.ZMessaging
import com.waz.utils.events.Signal
import com.waz.zclient.BaseActivity
import com.waz.zclient.R
import com.waz.model.{MuteSet, RemoteInstant}
import com.waz.threading.Threading
import com.waz.zclient.preferences.views.SwitchPreference

import scala.concurrent.{ExecutionContext, Future}

class ServerNotificationSettingsActivity extends BaseActivity {
  private implicit val executionContext = ExecutionContext.Implicits.global

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val mutedLocal = convController.currentConv.map(_.muted)

  private lazy val toolbar: Toolbar = findById(R.id.toolbar)
  private lazy val spOpenStatus: SwitchPreference = findById(R.id.spOpenStatus)

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_server_notification_settings)
    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })
    mutedLocal.onUi { muted =>
      if (muted.isAllMuted) {
        spOpenStatus.setChecked(false, true)
      } else {
        spOpenStatus.setChecked(true, true)
      }
    }

    spOpenStatus.onCheckedChange.onUi { checked =>
      for {
        zms <- zms.head
      } yield {
        convController.currentConvId.currentValue.foreach { convId =>
          zms.convsStorage.update(convId, _.copy(muted = if (checked) MuteSet.AllAllowed else MuteSet.AllMuted , muteTime = RemoteInstant.ofEpochMilli(System.currentTimeMillis()))).flatMap {
            case Some((old, updated)) =>
              Future successful Some((old, updated))
            case _ =>
              Future successful None
          }(Threading.Background)
        }

      }
    }

  }

  override def canUseSwipeBackLayout(): Boolean = true


}

object ServerNotificationSettingsActivity {
  def startSelf(context: Context): Unit = {
    context.startActivity(new Intent(context, classOf[ServerNotificationSettingsActivity]))
  }
}
