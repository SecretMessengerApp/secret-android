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

package com.waz.zclient.appentry.fragments

import java.io.{File, FileOutputStream}

import android.Manifest.permission._
import android.content.{DialogInterface, Intent}
import android.graphics.Color
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.core.content.ContextCompat
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.jsy.common.moduleProxy.ProxyAppEntryActivity
import com.jsy.res.utils.{ColorUtils, ViewUtils}
import com.waz.api.impl.ErrorResponse
import com.waz.model.AccountData.Password
import com.waz.model.UserId
import com.waz.permissions.PermissionsService
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.service.backup.BackupManager.InvalidMetadata
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.utils.{returning, _}
import com.waz.zclient.appentry.fragments.FirstLaunchAfterLoginFragment._
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.pages.main.conversation.AssetIntentsManager
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.ZetaButton
import com.waz.zclient.utils._
import com.waz.zclient._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog.InputPasswordMode

import scala.async.Async.{async, await}
import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._

object FirstLaunchAfterLoginFragment {
  val Tag: String = classOf[FirstLaunchAfterLoginFragment].getName
  val UserIdArg = "user_id_arg"
  val SSOHadDBArg = "sso_had_db_arg"

  def apply(userId: UserId, ssoHadDB: Boolean = true): Fragment = returning(new FirstLaunchAfterLoginFragment) { f =>
    val bundle = new Bundle()
    bundle.putString(UserIdArg, userId.str)
    bundle.putBoolean(SSOHadDBArg, ssoHadDB)
    f.setArguments(bundle)
  }
}

class FirstLaunchAfterLoginFragment extends FragmentHelper with View.OnClickListener {

  implicit val ec = Threading.Ui

  private lazy val accountsService    = inject[AccountsService]
  private lazy val permissions        = inject[PermissionsService]
  private lazy val spinnerController  = inject[SpinnerController]
  private lazy val accentColor = inject[AccentColorController].accentColor

  private lazy val restoreButton = view[ZetaButton](R.id.restore_button)
  private lazy val confirmButton = view[ZetaButton](R.id.zb__first_launch__confirm)
  private lazy val infoTitle = view[TypefaceTextView](R.id.info_title)
  private lazy val infoText = view[TypefaceTextView](R.id.info_text)

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(`type`: AssetIntentsManager.IntentType, uri: URI): Unit = {
      requestPassword(Some(uri))
    }
    override def onCanceled(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def onFailed(`type`: AssetIntentsManager.IntentType): Unit = {}
    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {
      startActivityForResult(intent, intentType.requestCode)
    }
  }

  private var assetIntentsManager = Option.empty[AssetIntentsManager]

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback))
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) = {
    confirmButton .foreach { registerButton =>
      registerButton.setOnClickListener(this)
      registerButton.setIsFilled(true)
      registerButton.setAccentColor(ColorUtils.getAttrColor(getContext, R.attr.SecretPrimaryTextColor))
    }
    restoreButton.foreach{ restoreButton =>
      restoreButton.setVisibility(View.VISIBLE)
      restoreButton.setOnClickListener(this)
      restoreButton.setIsFilled(false)
      restoreButton.setAccentColor(ColorUtils.getAttrColor(getContext, R.attr.SecretPrimaryTextColor))
    }
    if (databaseExists && getBooleanArg(SSOHadDBArg)) {
      infoTitle.foreach(_.setText(R.string.second_launch__header))
      infoText.foreach(_.setText(R.string.second_launch__sub_header))
    }

    accentColor.map(_.color).onUi { color =>
      confirmButton.foreach{ confirmButton=>
        confirmButton.setAccentColor(color)
        confirmButton.setTextColor(Color.WHITE)
      }
      restoreButton.foreach{ restoreButton=>
        restoreButton.setAccentColor(color)
        //restoreButton.setTextColor(Color.BLACK)
      }
    }
  }

  private def databaseExists = getStringArg(UserIdArg).exists(userId => getContext.getDatabasePath(userId).exists())

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View =
    inflater.inflate(R.layout.fragment_login_first_launch, viewGroup, false)

  def onClick(view: View): Unit = {
    val vId = view.getId
    if (vId == R.id.zb__first_launch__confirm) {
      enter(None, None)
    } else if (vId == R.id.restore_button) {
      importBackup()
    } else {

    }
  }

  private def importBackup(): Unit = {
    def openBackupChooser() = {
      permissions.requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).foreach { granted =>
        if (granted) assetIntentsManager.foreach(_.openBackupImport())
        else {
          //todo show something???
        }
      }
    }
    def showBackupConfirmationDialog = ViewUtils.showAlertDialog(
      getContext,
      R.string.restore_override_alert_title,
      R.string.restore_override_alert_text,
      R.string.restore_override_alert_ok,
      R.string.secret_cancel,
      new DialogInterface.OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int): Unit = openBackupChooser()
      },
      null
    )

    if (databaseExists && getBooleanArg(SSOHadDBArg)) showBackupConfirmationDialog
    else openBackupChooser()
  }

  private def displayError(title: Int, text: Int) =
    ViewUtils.showAlertDialog(getContext, title, text, android.R.string.ok, null, true)

  private def requestPassword(backup: Option[URI]): Future[Unit] = {
    backup match {
      case Some(_) =>
        val fragment = returning(BackupPasswordDialog.newInstance(InputPasswordMode)) {
          _.onPasswordEntered(p => enter(backup, Some(p)))
        }
        getActivity.asInstanceOf[BaseActivity]
          .getSupportFragmentManager
          .beginTransaction
          .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
          .add(fragment, BackupPasswordDialog.FragmentTag)
          .addToBackStack(BackupPasswordDialog.FragmentTag)
          .commit
        Future.successful(())
      case _ =>
        enter(backup, None)
    }
  }

  private def enter(backup: Option[URI], backupPassword: Option[Password]): Future[Unit] = {
    spinnerController.showDimmedSpinner(show = true, if (backup.isDefined) getString(R.string.restore_progress) else "")
    async {
      val userId = getStringArg(UserIdArg).map(UserId(_))
      if (userId.nonEmpty) {

        val backupFile = backup.map { uri =>
          val inputStream = getContext.getContentResolver.openInputStream(AndroidURIUtil.unwrap(uri))
          val file = File.createTempFile("secret", null)
          val outputStream = new FileOutputStream(file)
          IoUtils.copy(inputStream, outputStream)
          file
        }

        val accountManager = await(accountsService.createAccountManager(userId.get, backupFile, isLogin = Some(true), backupPassword = backupPassword))
        backupFile.foreach(_.delete())
        await { accountsService.setAccount(userId) }
        reqCustomBackend()
        val registrationState = await { accountManager.fold2(Future.successful(Left(ErrorResponse.internalError(""))), _.getOrRegisterClient()) }
        if (backup.isDefined) {
          spinnerController.hideSpinner(Some(getString(R.string.back_up_progress_complete)))
          await { CancellableFuture.delay(750.millis).future }
        }
        registrationState match {
          case Right(regState) => activity.foreach(_.onEnterApplication(openSettings = false, Some(regState)))
          case _ => activity.foreach(_.onEnterApplication(openSettings = false))
        }
      }
    }.recover {
      case InvalidMetadata.UserId =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_wrong_account_title, R.string.backup_import_error_wrong_account)
      case _: InvalidMetadata =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unsupported_version_title, R.string.backup_import_error_unsupported_version)
      case _ =>
        spinnerController.showSpinner(false)
        displayError(R.string.backup_import_error_unknown_title, R.string.backup_import_error_unknown)
    }
  }

  def reqCustomBackend(): Unit = {
    verbose(l"reqCustomBackend getCustomBackend() start")
    ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }.currentValue.foreach {
      accountData =>
        verbose(l"reqCustomBackend getCustomBackend() mid")
        SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_COOKIES, accountData.cookie.str)
        SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_USERID, accountData.id.str)
        if (accountData.accessToken.isDefined) {
          accountData.accessToken.foreach { x =>
            SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, x.accessToken)
            SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, x.tokenType)
          }
        } else {
          SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, "")
          SpUtils.putString(getActivity, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, "")
        }
        inject[BackendController].getCustomBackend()
    }
  }

  def activity = if (getActivity.isInstanceOf[ProxyAppEntryActivity]) Some(getActivity.asInstanceOf[ProxyAppEntryActivity]) else None

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    super.onActivityResult(requestCode, resultCode, data)
    assetIntentsManager.foreach(_.onActivityResult(requestCode, resultCode, data))
  }
}
