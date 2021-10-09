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
package com.waz.zclient.preferences.pages

import android.app.Activity
import android.content.{Context, DialogInterface, Intent}
import android.graphics.drawable.Drawable
import android.graphics.{Canvas, ColorFilter, Paint, PixelFormat}
import android.os.{Bundle, Parcel, Parcelable}
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.{Fragment, FragmentTransaction}
import com.jsy.common.acts.SelfQrCodeActivity
import com.jsy.common.utils.{DoubleUtils, ModuleUtils}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.{AccentColor, EmailAddress, PhoneNumber}
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, EventStream, Signal}
import com.waz.utils.returning
import com.waz.zclient.appentry.DialogErrorMessage
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.PasswordController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.preferences.dialogs._
import com.waz.zclient.preferences.views.{EditNameDialog, QrCodeButton, SectionHeadText, TextButton}
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.ui.utils.TextViewUtils._
import com.waz.zclient.utils.ContextUtils._
import com.jsy.res.utils.ViewUtils._
import com.waz.zclient.utils.{BackStackKey, BackStackNavigator, RichView, StringUtils, UiStorage}
import com.waz.zclient.{BuildConfig, _}

trait AccountView {
  val onNameClick: EventStream[Unit]
  val onHandleClick: EventStream[Unit]
  val onEmailClick: EventStream[Unit]
  val onPhoneClick: EventStream[Unit]
  val onPictureClick: EventStream[Unit]
  val onAccentClick: EventStream[Unit]
  val onPasswordResetClick: EventStream[Unit]
  val onLogoutClick: EventStream[Unit]
  val onBackupClick: EventStream[Unit]

  def setName(name: String): Unit

  def setHandle(handle: String): Unit

  def setEmail(email: Option[EmailAddress]): Unit

  def setPhone(phone: Option[PhoneNumber]): Unit

  def setPictureDrawable(drawable: Drawable): Unit

  def setAccentDrawable(drawable: Drawable): Unit

  def setEmailEnabled(enabled: Boolean): Unit

  def setPhoneNumberEnabled(enabled: Boolean): Unit

  def setResetPasswordEnabled(enabled: Boolean): Unit
}

class AccountViewImpl(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with AccountView with ViewHelper with DerivedLogTag {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.preferences_account_layout)
  ColorUtils.setBackgroundColor(this)
  val myQrCode = findById[QrCodeButton](R.id.myQrCode)

  val nameButton = findById[TextButton](R.id.preferences_account_name)
  val handleButton = findById[TextButton](R.id.preferences_account_handle)
  val emailButton = findById[TextButton](R.id.preferences_account_email)
  val phoneButton = findById[TextButton](R.id.preferences_account_phone)
  val pictureButton = findById[TextButton](R.id.preferences_account_picture)
  val colorButton = findById[TextButton](R.id.preferences_account_accent)
  val resetPasswordButton = findById[TextButton](R.id.preferences_account_reset_pw)
  val logoutButton = findById[TextButton](R.id.preferences_account_logout)
  val historyTitle = findById[SectionHeadText](R.id.preferences_history_title)
  val backupButton = findById[TextButton](R.id.preferences_backup)
  historyTitle.setVisible(true)
  backupButton.setVisible(true)

  myQrCode.onClickEvent.onUi{_=>
    if(!DoubleUtils.isFastDoubleClick){
      context.startActivity(new Intent(context, classOf[SelfQrCodeActivity]))
    }
  }

  override val onNameClick = nameButton.onClickEvent.map(_ => ())
  override val onHandleClick = handleButton.onClickEvent.map(_ => ())
  override val onEmailClick = emailButton.onClickEvent.map(_ => ())
  override val onPhoneClick = phoneButton.onClickEvent.map(_ => ())
  override val onPictureClick = pictureButton.onClickEvent.map(_ => ())
  override val onAccentClick = colorButton.onClickEvent.map(_ => ())
  override val onPasswordResetClick = resetPasswordButton.onClickEvent.map(_ => ())
  override val onLogoutClick = logoutButton.onClickEvent.map(_ => ())
  override val onBackupClick = backupButton.onClickEvent.map(_ => ())

  override def setName(name: String) = nameButton.setSubtitle(name)

  override def setHandle(handle: String) = handleButton.setSubtitle(handle)

  override def setEmail(email: Option[EmailAddress]) = emailButton.setSubtitle(email.map(_.str).getOrElse(getString(R.string.pref_account_add_email_title)))

  override def setPhone(phone: Option[PhoneNumber]) = phoneButton.setSubtitle(phone.map(_.str).getOrElse(getString(R.string.pref_account_add_phone_title)))

  override def setPictureDrawable(drawable: Drawable) = pictureButton.setEndGlyphImageDrawable(None, Some(drawable), true, TextButton.ORITATION_NONE)

  override def setAccentDrawable(drawable: Drawable) = colorButton.setEndGlyphImageDrawable(None, Some(drawable), true, TextButton.ORITATION_NONE)

  override def setEmailEnabled(enabled: Boolean) = emailButton.setVisible(enabled)

  override def setPhoneNumberEnabled(enabled: Boolean) = phoneButton.setVisible(enabled)

  override def setResetPasswordEnabled(enabled: Boolean) = resetPasswordButton.setVisible(enabled)
}

case class AccountBackStackKey(args: Bundle = new Bundle()) extends BackStackKey(args) {

  override def nameId: Int = R.string.pref_account_screen_title

  override def layoutId = R.layout.preferences_account

  private var controller = Option.empty[AccountViewController]

  override def onViewAttached(v: View) =
    controller = Option(v.asInstanceOf[AccountViewImpl]).map(view => new AccountViewController(view)(view.wContext.injector, view, view.getContext))

  override def onViewDetached() =
    controller = None
}

object AccountBackStackKey {
  val CREATOR: Parcelable.Creator[AccountBackStackKey] = new Parcelable.Creator[AccountBackStackKey] {
    override def createFromParcel(source: Parcel) = AccountBackStackKey()

    override def newArray(size: Int) = Array.ofDim(size)
  }
}

class AccountViewController(view: AccountView)(implicit inj: Injector, ec: EventContext, context: Context)
  extends Injectable with DerivedLogTag {

  val zms = inject[Signal[ZMessaging]]
  val self = zms.flatMap(_.users.selfUser)
  val accounts = inject[AccountsService]
  implicit val uiStorage = inject[UiStorage]
  val navigator = inject[BackStackNavigator]
  val password = inject[PasswordController].password

  val isTeam = zms.map(_.teamId.isDefined)

  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  val phone = self.map(_.phone)
  val email = self.map(_.email)

  val isPhoneNumberEnabled = for {
    p <- phone
    isTeam <- isTeam
    sso <- accounts.isActiveAccountSSO
  } yield sso && (p.isDefined || !isTeam)

  val selfPicture: Signal[ImageSource] = self.map(_.picture).collect { case Some(pic) => WireImage(pic) }

  view.setPictureDrawable(new ImageAssetDrawable(selfPicture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Round))

  self.onUi { self =>
    self.handle.foreach(handle => view.setHandle(StringUtils.formatHandle(handle.string)))
    view.setName(self.name)
    view.setAccentDrawable(new Drawable {

      val paint = new Paint()

      override def draw(canvas: Canvas) = {
        paint.setColor(AccentColor(self.accent).color)
        canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), getBounds.width() / 2, paint)
      }

      override def setColorFilter(colorFilter: ColorFilter) = {}

      override def setAlpha(alpha: Int) = {}

      override def getOpacity = PixelFormat.OPAQUE
    })
  }

  phone.onUi(view.setPhone)
  email.onUi(view.setEmail)

  accounts.isActiveAccountSSO.onUi { sso =>
    view.setEmailEnabled(!sso)
    view.setResetPasswordEnabled(!sso)
  }
  isPhoneNumberEnabled.onUi(view.setPhoneNumberEnabled)

  view.onNameClick.onUi { _ =>
    if(!DoubleUtils.isFastDoubleClick){
      self.head.map { self =>
        showPrefDialog(EditNameDialog.newInstance(self.name), EditNameDialog.Tag)
      }(Threading.Ui)
    }
  }

  view.onHandleClick.onUi { _ =>
    if(!DoubleUtils.isFastDoubleClick){
      self.head.map { self =>
        import com.waz.zclient.preferences.dialogs.ChangeHandleFragment._
        showPrefDialog(newInstance(self.handle.fold("")(_.string), cancellable = true), Tag)
      }(Threading.Ui)
    }
  }

  view.onEmailClick.filter(_ => BuildConfig.ALLOW_CHANGE_OF_EMAIL).onUi { _ =>
    import Threading.Implicits.Ui
    if (currentAccount.currentValue.nonEmpty && currentAccount.currentValue.head.email.nonEmpty) {
    } else {
      accounts.activeAccountManager.head.map(_.foreach(_.hasPassword().foreach {
        case Left(ex) =>
          val (h, b) = DialogErrorMessage.genericError(ex.code)
          showErrorDialog(h, b)
        case Right(hasPass) =>
          showPrefDialog(
            returning(ChangeEmailDialog(hasPassword = hasPass)) {
              _.onEmailChanged { e =>
                val f = VerifyEmailPreferencesFragment(e)
                //hide the verification screen when complete
                self.map(_.email).onChanged.filter(_.contains(e)).onUi { _ =>
                  f.dismiss()
                }
                showPrefDialog(f, VerifyEmailPreferencesFragment.Tag)
              }
            },
            ChangeEmailDialog.FragmentTag)
      }))
    }
  }

  //TODO move most of this information to the dialogs themselves -- it's too tricky here to sort out what thread things are running on...
  //currently blocks a little...
  view.onPhoneClick.onUi { _ =>
    import Threading.Implicits.Ui
    for {
      email <- self.head.map(_.email)
      ph <- self.head.map(_.phone)
    } {
      showPrefDialog(
        returning(ChangePhoneDialog(ph.map(_.str), email.isDefined)) {
          _.onPhoneChanged {
            case Some(p) =>
              val f = VerifyPhoneFragment.newInstance(p.str)
              //hide the verification screen when complete
              self.map(_.phone).onChanged.filter(_.contains(p)).onUi { _ =>
                f.dismiss()
              }
              showPrefDialog(f, VerifyPhoneFragment.TAG)
            case _ =>
          }
        },
        ChangePhoneDialog.FragmentTag)
    }
  }

  view.onPictureClick.onUi(_ => navigator.goTo(ProfilePictureBackStackKey(From.initArgs(From.FromUploadSelfHead))))

  view.onAccentClick.onUi { _ =>
    if(!DoubleUtils.isFastDoubleClick){
      self.head.map { _ =>
        showPrefDialog(new AccentColorPickerFragment(), AccentColorPickerFragment.fragmentTag)
      }(Threading.Ui)
    }
  }

  view.onPasswordResetClick.onUi { _ =>
    if(!DoubleUtils.isFastDoubleClick){
      inject[BrowserController].openForgotPassword()
    }
  }

  view.onLogoutClick.onUi { _ =>
    showAlertDialog(context, null,
      getString(R.string.pref_account_sign_out_warning_message),
      getString(R.string.pref_account_sign_out_warning_verify),
      getString(R.string.secret_cancel),
      new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int) = {
          import Threading.Implicits.Ui
          zms.map(_.selfUserId).head.flatMap(accounts.logout)
            .flatMap(_ => accounts.accountsWithManagers.head.map(_.isEmpty)).map {
            case true =>
              val startSuc = ModuleUtils.startActivity(context, ModuleUtils.CLAZZ_AppEntryActivity)
              if (startSuc) {
                Option(context.asInstanceOf[Activity]).foreach(_.finish())
              }
            case false =>
              navigator.back()
              navigator.back()
          }
        }
      }, null)
  }

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

  private def showPrefDialog(f: Fragment, tag: String) = {
    context.asInstanceOf[BaseActivity]
      .getSupportFragmentManager
      .beginTransaction
      .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
      .add(f, tag)
      .addToBackStack(tag)
      .commit
  }
}
