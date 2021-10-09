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
package com.waz.zclient.preferences.pages

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.{LinearLayout, Toast}
import com.waz.content.GlobalPreferences.WsForegroundKey
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogsService
import com.waz.service.{FCMNotificationStatsService, ZMessaging}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.GoogleApi
import com.waz.zclient.preferences.views.{AdvanceTextButton, SwitchPreference}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.{BackStackKey, DebugUtils}
import com.waz.zclient.{R, ViewHelper}

import scala.concurrent.duration._

trait AdvancedView

class AdvancedViewImpl(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with AdvancedView
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_advanced_layout)

  private val logsService = inject[LogsService]
  private val initialLogsEnabled = logsService.logsEnabledGlobally.currentValue.getOrElse(false)
  private val statsService = inject[Signal[FCMNotificationStatsService]]

  val submitLogs = returning(findById[AdvanceTextButton](R.id.preferences_debug_report)) { v =>
    setButtonEnabled(v, initialLogsEnabled)
    v.onClickEvent { _ =>
      DebugUtils.sendDebugReport(context.asInstanceOf[Activity])
    }
  }

//  val loggingSwitch = returning(findById[SwitchPreference](R.id.preferences_enable_logs)) { v =>
//    v.setChecked(initialLogsEnabled)
//
//    v.onCheckedChange { enabled =>
//      logsService.setLogsEnabled(enabled)
//      setButtonEnabled(submitLogs, enabled)
//    }
//  }

  val resetPush = returning(findById[AdvanceTextButton](R.id.preferences_reset_push)) { v =>
    inject[GoogleApi].isGooglePlayServicesAvailable.map(if (_) View.VISIBLE else View.GONE).onUi(v.setVisibility)
    v.onClickEvent { _ =>
      ZMessaging.currentGlobal.tokenService.resetGlobalToken()
      Toast.makeText(getContext, getString(R.string.pref_advanced_reset_push_completed)(getContext), Toast.LENGTH_LONG).show()
      setButtonEnabled(v, enabled = false)
      CancellableFuture.delay(5.seconds).map(_ => setButtonEnabled(v, enabled = true))(Threading.Ui)
    }
  }

//  val statsDisplay = returning(findById[TextView](R.id.preferences_notifications_stats)) { v =>
//    (for {
//      service <- statsService.head
//      stats <- service.getFormattedStats
//    } yield stats).foreach(v.setText)(Threading.Ui)
//  }
//
//  import com.waz.zclient.utils._
//  statsDisplay.setVisible(BuildConfig.DEBUG)

  val webSocketForegroundServiceSwitch = returning(findById[SwitchPreference](R.id.preferences_websocket_service)) { v =>
    inject[GoogleApi].isGooglePlayServicesAvailable.map(if (_) View.GONE else View.VISIBLE).onUi(v.setVisibility)
    v.setPreference(WsForegroundKey, global = true)
  }

  def setButtonEnabled(button: View, enabled: Boolean): Unit = {
    button.setEnabled(enabled)
    button.setAlpha(if (enabled) 1f else .5f)
  }
}

case class AdvancedBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_advanced_screen_title

  override def layoutId = R.layout.preferences_advanced

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
