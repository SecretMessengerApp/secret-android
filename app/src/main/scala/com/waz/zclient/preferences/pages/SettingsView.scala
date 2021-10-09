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

import android.content.{Context, DialogInterface}
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.jsy.common.acts.LanguageActivity
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.ViewUtils.showAlertDialog
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.otr.Client
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.zclient._
import com.waz.zclient.preferences.views.TextButton
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, RichView, SpUtils, UiStorage, UserSignal}

trait SettingsView {

  def setDevSettingsEnabled(enabled: Boolean): Unit

  def setTitle(number: Int): Unit

  val onBackupClick: EventStream[Unit]
}

class SettingsViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with SettingsView with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_settings_layout)
  val navigator = inject[BackStackNavigator]

  val darkModeButton=findById[TextButton](R.id.settings_dark_mode)
  val accountButton = findById[TextButton](R.id.settings_account)
  val devicesButton = findById[TextButton](R.id.settings_devices)
  val optionsButton = findById[TextButton](R.id.settings_options)
  val advancedButton = findById[TextButton](R.id.settings_advanced)
  val supportButton = findById[TextButton](R.id.settings_support)
  val aboutButton = findById[TextButton](R.id.settings_about)
  val devButton = findById[TextButton](R.id.settings_dev)
  val avsButton = findById[TextButton](R.id.settings_avs)
  val backupButton = findById[TextButton](R.id.settings_backup)
  val languageButton = findById[TextButton](R.id.settings_language)

  darkModeButton.onClickEvent.on(Threading.Ui) {_ => navigator.goTo(DarkModeBackStackKey())}
  accountButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AccountBackStackKey()) }
  devicesButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(DevicesBackStackKey())}
  optionsButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(OptionsBackStackKey()) }
  advancedButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AdvancedBackStackKey()) }
  supportButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(SupportBackStackKey()) }
  aboutButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AboutBackStackKey()) }
  devButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(DevSettingsBackStackKey()) }
  avsButton.onClickEvent.on(Threading.Ui) { _ => navigator.goTo(AvsBackStackKey()) }
  languageButton.onClickEvent.onUi(_ => LanguageActivity.start(getContext))

  darkModeButton.setSubtitle(ThemeUtils.getThemeDesc(context))
  languageButton.setSubtitle {
    val entries = getResources.getStringArray(R.array.language_entries)
    val values = getResources.getStringArray(R.array.language_values)
    val indexOf = values.indexOf(SpUtils.getLanguage(context))
    entries(Math.max(indexOf, 0))
  }

  override val onBackupClick = backupButton.onClickEvent.map(_ => ())

  override def setDevSettingsEnabled(enabled: Boolean) = {
    advancedButton.setVisible(enabled)
    supportButton.setVisible(enabled)
    devButton.setVisible(enabled)
    avsButton.setVisible(enabled)
  }

  override def setTitle(number: Int) = {
    devicesButton.setSubtitle(String.valueOf(number + 1))
  }
}

case class SettingsBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_category_title

  override def layoutId = R.layout.preferences_settings

  var controller = Option.empty[SettingsViewController]

  override def onViewAttached(v: View) = {
    controller = Option(v.asInstanceOf[SettingsViewImpl]).map(sv => new SettingsViewController(sv)(sv.injector, sv,sv.getContext))
  }

  override def onViewDetached() = {
    controller = None
  }
}

class SettingsViewController(view: SettingsView)(implicit inj: Injector, ec: EventContext, context: Context)
  extends Injectable with DerivedLogTag {

  val zms = inject[Signal[ZMessaging]]
  implicit val uiStorage = inject[UiStorage]
  val navigator = inject[BackStackNavigator]
  val self = zms.flatMap(_.users.selfUser)
  val accounts = inject[AccountsService]
  val email = self.map(_.email)

  val otherClients = for {
    Some(am)      <- accounts.activeAccountManager
    selfClientId  <- am.clientId
    clients       <- Signal.future(am.storage.otrClientsStorage.get(am.userId))
  } yield clients.fold(Seq[Client]())(_.clients.values.filter(client => !selfClientId.contains(client.id)).toSeq.sortBy(_.regTime).reverse)

  val selfInfo = for {
    z <- zms
    self <- UserSignal(z.selfUserId)
  } yield (self.getDisplayName, self.handle.fold("")(_.string))

  view.onBackupClick.onUi { _ =>
    Signal(accounts.isActiveAccountSSO, email).head.map {
      case (true, _)        => navigator.goTo(BackupExportKey())
      case (false, Some(_)) => navigator.goTo(BackupExportKey())
      case _ =>
        showAlertDialog(context,
          R.string.pref_account_backup_warning_title,
          R.string.pref_account_backup_warning_message,
          R.string.pref_account_backup_warning_ok,
          new DialogInterface.OnClickListener() {
            def onClick(dialog: DialogInterface, which: Int) = dialog.dismiss()
          }, true)
    }(Threading.Ui)
  }

  otherClients.onUi(x => view.setTitle(x.size))

  view.setDevSettingsEnabled(BuildConfig.DEBUG)
}

