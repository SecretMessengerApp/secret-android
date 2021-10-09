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

import java.io.File

import android.app.Activity
import android.content.pm.PackageManager
import android.content.{Context, Intent}
import android.os.{Bundle, Environment}
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.app.ShareCompat
import androidx.fragment.app.FragmentTransaction
import com.jsy.common.model.circle.CircleConstant
import com.jsy.common.utils.ToastUtil
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.service.{UiLifeCycle, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.common.views.MenuRowButton
import com.waz.zclient.log.LogUI._
import com.waz.zclient.preferences.dialogs.BackupPasswordDialog
import com.waz.zclient.preferences.pages.BackupExportView._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class BackupExportView(context: Context, attrs: AttributeSet, style: Int)
  extends LinearLayout(context, attrs, style)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.backup_export_layout)

  private val zms                = inject[Signal[ZMessaging]]
  private val spinnerController  = inject[SpinnerController]
  private val lifecycle          = inject[UiLifeCycle]
  private val sharing            = inject[ExternalFileSharing]

  private val descriptionText = findById[TypefaceTextView](R.id.backup_description_text)
  private val backupButton = findById[MenuRowButton](R.id.backup_button)
  descriptionText.setText(getNewDescription(descriptionText.getText.toString))
  backupButton.setOnClickProcess(requestPassword())

  def getNewDescription(description: String): String = {
    description + context.getString(R.string.backup_description_path, Environment.DIRECTORY_DOCUMENTS)
  }

  def requestPassword(): Future[Unit] = {
    val fragment = returning(new BackupPasswordDialog)(
      _.onPasswordEntered(backupData)
    )
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(fragment, BackupPasswordDialog.FragmentTag)
      .addToBackStack(BackupPasswordDialog.FragmentTag)
      .commit
    Future.successful(())
  }

  private def backupTargetDir(): Option[File] = {
    val backupPath: String = CircleConstant.BACKUP_FIRST_PATH
    val targetDir = Option(new File(backupPath))
    targetDir.foreach { dir =>
      if (!dir.exists) dir.mkdirs
    }
    targetDir
  }

  private def backupData(password: Password): Unit = {
    spinnerController.showDimmedSpinner(show = true, ContextUtils.getString(R.string.back_up_progress))
    import Threading.Implicits.Ui

    (for {
      z                <- zms.head
      Some(accManager) <- z.accounts.activeAccountManager.head
      res              <- accManager.exportDatabase(password, backupTargetDir)
      _                <- lifecycle.uiActive.collect{ case true => () }.head
    } yield res).onComplete {
      case Success(file) =>
        if (isShown) {
          spinnerController.hideSpinner(Some(ContextUtils.getString(R.string.back_up_progress_complete)))
          Try {
            val fileUri = sharing.getUriForFile(file)
            val intent = ShareCompat.IntentBuilder.from(context.asInstanceOf[Activity]).setType("application/octet-stream").setStream(fileUri).getIntent
            if (BuildConfig.DEBUG && !context.getPackageManager.queryIntentActivities(new Intent(TestingGalleryPackage), PackageManager.MATCH_ALL).isEmpty) {
              intent.setPackage(TestingGalleryPackage)
            }
            context.startActivity(intent)
          }
        } else
          spinnerController.hideSpinner()
        ToastUtil.toastByString(context, context.getString(R.string.content__file__action__save_completed, file.getAbsolutePath))
      case Failure(err) =>
        error(l"Error while exporting database", err)
        spinnerController.hideSpinner()
        ViewUtils.showAlertDialog(getContext, R.string.export_generic_error_title, R.string.export_generic_error_text, android.R.string.ok, null, true)
    }
  }
}

object BackupExportView {
  val TestingGalleryPackage = "com.wire.testinggallery"
}

case class BackupExportKey(args: Bundle = new Bundle()) extends BackStackKey(args) {
  override def nameId: Int = R.string.pref_backup_screen_title

  override def layoutId = R.layout.preferences_backup_export

  override def onViewAttached(v: View) = {}

  override def onViewDetached() = {}
}
