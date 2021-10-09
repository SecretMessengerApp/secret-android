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
package com.jsy.common.fragment

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.view._
import androidx.fragment.app.Fragment
import com.jsy.res.utils.ViewUtils
import com.waz.api._
import com.waz.content.UserPreferences
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient._
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.log.LogUI.{verbose, _}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.preferences.pages.{OptionsView, ProfileBackStackKey}
import com.waz.zclient.utils.{BackStackNavigator, RingtoneUtils}
import com.waz.zclient.views._
import timber.log.Timber

class PreferencesUserFragment extends BaseFragment[PreferencesUserFragment.Container]
  with FragmentHelper {

  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account }

  private lazy val backStackNavigator = inject[BackStackNavigator]
  private lazy val zms = inject[Signal[ZMessaging]]

  lazy val accentColor = inject[AccentColorController].accentColor

  private val handler: android.os.Handler = new android.os.Handler() {}

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    Timber.i("")
    verbose(l"[PreferencesUserFragment:onCreateView] 0")
    val rootView: View = inflater.inflate(R.layout.fragment_preferences_user, viewContainer, false)

    backStackNavigator.setup(ViewUtils.getView(rootView, R.id.content).asInstanceOf[ViewGroup])
    backStackNavigator.goTo(ProfileBackStackKey())
    verbose(l"[PreferencesUserFragment:onCreateView] 1")
    rootView
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) = {
    super.onActivityResult(requestCode, resultCode, data)
    val fragment: Fragment = getActivity.getSupportFragmentManager.findFragmentById(R.id.fl__root__camera)
    if (fragment != null) fragment.onActivityResult(requestCode, resultCode, data)

    if (resultCode == Activity.RESULT_OK && Seq(OptionsView.RingToneResultId, OptionsView.TextToneResultId, OptionsView.PingToneResultId).contains(requestCode)) {

      val pickedUri = Option(data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI).asInstanceOf[Uri])
      val key = requestCode match {
        case OptionsView.RingToneResultId => UserPreferences.RingTone
        case OptionsView.TextToneResultId => UserPreferences.TextTone
        case OptionsView.PingToneResultId => UserPreferences.PingTone
      }
      zms.head.flatMap(_.userPrefs.preference(key).update(pickedUri.fold(RingtoneUtils.getSilentValue)(_.toString)))(Threading.Ui)
    } else {
      //...
    }
  }
}

object PreferencesUserFragment {

  val TAG: String = classOf[PreferencesUserFragment].getName

  def newInstance(): PreferencesUserFragment = {
    val fragment: PreferencesUserFragment = new PreferencesUserFragment
    fragment
  }

  trait Container {
    def showIncomingPendingConnectRequest(conversation: IConversation): Unit

    def onSelectedUsers(users: java.util.List[User], requester: ConversationChangeRequester): Unit

    def getLoadingViewIndicator: LoadingIndicatorView
  }

}
