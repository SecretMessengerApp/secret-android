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

import android.content.Context
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Bundle
import android.transition._
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.jsy.common.moduleProxy.ProxyAppEntryActivity
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.EmailAddress
import com.waz.service.{AccountsService, ZMessaging}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient._
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.fragments.SignInFragment._
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.newreg.fragments.country.Country
import com.waz.zclient.newreg.views.PhoneConfirmationButton
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, NameValidator, PasswordValidator}
import com.waz.zclient.pages.main.profile.views.GuidedEditText
import com.waz.zclient.tracking.{GlobalTrackingController, SignUpScreenEvent}
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.ui.utils.{KeyboardUtils, TextViewUtils}
import com.waz.zclient.ui.views.tab.TabIndicatorLayout
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._


class SignInFragment extends FragmentHelper with DerivedLogTag
  with View.OnClickListener {

  implicit def context: Context = getActivity

  private lazy val accountsService = inject[AccountsService]
  private lazy val browserController = inject[BrowserController]
  private lazy val tracking = inject[GlobalTrackingController]

  private lazy val isAddingAccount = accountsService.zmsInstances.map(_.nonEmpty)

  private lazy val uiSignInState = {
    val sign = getStringArg(SignTypeArg) match {
      case Some(Login.str) => Login
      case Some(Register.str) => Register
      case Some(ForgetPassword.str) => ForgetPassword
      case _ => Register
    }
    Signal(SignInMethod(sign))
  }

  private val email = Signal("")
  private val password = Signal("")
  private val name = Signal("")
  private lazy val phoneCountry = Signal[Country]()

  private lazy val nameValidator = new NameValidator()
  private lazy val emailValidator = EmailValidator.newInstance()
  private lazy val passwordValidator = PasswordValidator.instance(context)
  private lazy val legacyPasswordValidator = PasswordValidator.instanceLegacy(context)

  lazy val isValid: Signal[Boolean] = uiSignInState.flatMap {
    case SignInMethod(Login) =>
      for {
        email <- email
        password <- password
      } yield emailValidator.validate(email) && legacyPasswordValidator.validate(password)
    case SignInMethod(Register) =>
      for {
        name <- name
        email <- email
        password <- password
      } yield nameValidator.validate(name) && emailValidator.validate(email) && passwordValidator.validate(password)
    case _ => Signal.empty[Boolean]
  }

  private lazy val container = view[FrameLayout](R.id.sign_in_container)
  private lazy val scenes = Array(
    R.layout.sign_in_email_scene,

    R.layout.sign_up_email_scene

  )

  private lazy val emailButton = view[TypefaceTextView](R.id.ttv__new_reg__sign_in__go_to__email)
  private lazy val tabSelector = view[TabIndicatorLayout](R.id.til__app_entry)
  private lazy val closeButton = view[GlyphTextView](R.id.close_button)

  def nameField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__name))

  def emailField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__email))

  def passwordField = Option(findById[GuidedEditText](getView, R.id.get__sign_in__password))


  def confirmationButton = Option(findById[PhoneConfirmationButton](R.id.pcb__signin__logon))

  def backButton = Option(findById[PhoneConfirmationButton](R.id.pcb__signin__back))

  def termsOfService = Option(findById[TypefaceTextView](R.id.terms_of_service_text))

  def forgotPasswordButton = Option(findById[View](getView, R.id.tv_forget_password))


  def setupViews(): Unit = {

    emailField.foreach { field =>
      field.setValidator(emailValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__email)
      field.setText(email.currentValue.getOrElse(""))
      field.getEditText.addTextListener(email ! _)
      field.setTextColors(R.color.black, R.color.color_666)
      field.setAccentColor(Color.BLACK)
    }

    passwordField.foreach { field =>
      field.setValidator(passwordValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__password)
      field.setText(password.currentValue.getOrElse(""))
      field.getEditText.addTextListener(password ! _)
      field.setTextColors(R.color.black, R.color.color_666)
      field.setAccentColor(Color.BLACK)
    }

    nameField.foreach { field =>
      field.setValidator(nameValidator)
      field.setResource(R.layout.guided_edit_text_sign_in__name)
      field.setText(name.currentValue.getOrElse(""))
      field.getEditText.addTextListener(name ! _)
      field.setTextColors(R.color.black, R.color.color_666)
      field.setAccentColor(Color.BLACK)
    }

    termsOfService.foreach { text =>
      TextViewUtils.linkifyText(text, getColor(R.color.white), true, new Runnable {
        override def run(): Unit = browserController.openUrl(getString(R.string.url_terms_of_service_personal))
      })
    }

    confirmationButton.foreach(_.setOnClickListener(this))
    backButton.foreach(_.setOnClickListener(this))
    backButton.foreach(_.setState(PhoneConfirmationButton.State.CONFIRM_BACK))
    confirmationButton.foreach(_.setAccentColor(Color.WHITE))
    setConfirmationButtonActive(isValid.currentValue.getOrElse(false))
    forgotPasswordButton.foreach(_.setOnClickListener(this))

  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View =
    returning(inflater.inflate(R.layout.fragment_signin_signup, container, false)) { view =>
      findById[TabIndicatorLayout](view, R.id.til__app_entry).setLabels(Array[Int](R.string.email_sign_in))
    }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    emailButton.foreach(_.setOnClickListener(this))
    closeButton.foreach(_.setOnClickListener(this))
    tabSelector.foreach { tabSelector =>
      tabSelector.setTextColor(ContextCompat.getColorStateList(getContext, R.color.wire__text_color_black_dark_selector))
      tabSelector.setPrimaryColor(Color.BLACK)
    }

  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    val transition = if (SDK_INT >= KITKAT) Option(new AutoTransition2()) else None

    def switchScene(sceneIndex: Int): Unit = transition.fold[Unit]({
      container.foreach(_.removeAllViews())
      container.foreach(c => LayoutInflater.from(getContext).inflate(scenes(sceneIndex), c))
    })(tr => container.foreach(c => TransitionManager.go(Scene.getSceneForLayout(c, scenes(sceneIndex), getContext), tr)))

    uiSignInState.onUi {
      case SignInMethod(Login)    =>
        switchScene(0)
        setupViews()
        emailField.foreach(_.getEditText.requestFocus())
      case SignInMethod(Register) =>
        switchScene(1)
        setupViews()
        nameField.foreach(_.getEditText.requestFocus())
      case _                      => None
    }

    uiSignInState.map(s => SignInMethod(s.signType)).onUi { method =>
      ZMessaging.globalModule.map(_.trackingService.track(SignUpScreenEvent(method)))(Threading.Ui)
    }

    isValid.onUi(setConfirmationButtonActive)
    // phoneCountry.onUi(onCountryHasChanged)
    isAddingAccount.onUi(isAdding => closeButton.foreach(_.setVisible(isAdding)))
  }

  private def setConfirmationButtonActive(active: Boolean): Unit = {
    import PhoneConfirmationButton.State._
    confirmationButton.foreach(_.setState(if (active) CONFIRM else NONE))
  }


  override def onResume() = {
    super.onResume()
  }


  override def onPause() = {
    super.onPause()
  }

  def dealSigninEmail(): Unit = {
    implicit val ec = Threading.Ui
    activity.foreach{
      KeyboardUtils.closeKeyboardIfShown(_)
      _.enableProgress(true)
    }


    def onResponse[A](req: Either[ErrorResponse, A], method: SignInMethod) = {
      tracking.onEnteredCredentials(req, method)
      activity.foreach(_.enableProgress(false))
      req match {
        case Left(error) =>
          showErrorDialog(EmailError(error))
          Left({})
        case Right(res) => Right(res)
      }
    }

    uiSignInState.head.flatMap {
      case m@SignInMethod(Login) =>
        for {
          email <- email.head
          password <- password.head
          req <- accountsService.loginEmail(email, password)
        } yield onResponse(req, m).right.foreach { id =>
          activity.foreach(_.showFragment(FirstLaunchAfterLoginFragment(id), FirstLaunchAfterLoginFragment.Tag))
        }
      case m@SignInMethod(Register) =>
        for {
          email <- email.head
          password <- password.head
          name <- name.head
          req <- accountsService.requestEmailCode(EmailAddress(email))
        } yield onResponse(req, m).right.foreach { _ =>
          activity.foreach(_.showFragment(VerifyEmailWithCodeFragment(email, name, password), VerifyEmailWithCodeFragment.Tag))
        }
      case _ => throw new NotImplementedError("Only login with email works right now") //TODO
    }
  }

  override def onClick(v: View) = {
    val vid = v.getId
    if (vid == R.id.pcb__signin__logon) {
      dealSigninEmail()
    } else if (vid == R.id.tv_forget_password) {
      browserController.openForgotPasswordPage()
    } else if (vid == R.id.close_button) {
      activity.foreach(_.abortAddAccount())
    } else if (vid == R.id.pcb__signin__back){
      onBackPressed()
    }
  }


  def clearCredentials(): Unit =
    Set(email, password, name).foreach(_ ! "")

  override def onBackPressed(): Boolean =
    if (getFragmentManager.getBackStackEntryCount > 1) {
      getFragmentManager.popBackStack()
      true
    } else {
      false
    }

  def activity = if (getActivity.isInstanceOf[ProxyAppEntryActivity]) Some(getActivity.asInstanceOf[ProxyAppEntryActivity]) else None

}

object SignInFragment {
  val SignTypeArg = "SIGN_IN_TYPE"
  // val InputTypeArg = "INPUT_TYPE"

  def apply() = new SignInFragment

  def apply(signInMethod: SignInMethod): SignInFragment = {
    returning(new SignInFragment()) {
      _.setArguments(returning(new Bundle) { b =>
        b.putString(SignTypeArg, signInMethod.signType.str)
        //  b.putString(InputTypeArg, signInMethod.inputType.str)
      })
    }
  }

  val Tag = getClass.getSimpleName

  sealed trait SignType {
    val str: String
  }

  object Login extends SignType {
    override val str = "Login"
  }

  object Register extends SignType {
    override val str = "Register"
  }

  object ForgetPassword extends SignType {
    override val str: String = "ForgetPassword"
  }

  sealed trait InputType {
    val str: String
  }

  object Email extends InputType {
    override val str = "Email"
  }

  object Phone extends InputType {
    override val str = "Phone"
  }

  //case class SignInMethod(signType: SignType, inputType: InputType)
  case class SignInMethod(signType: SignType)
}

class AutoTransition2 extends TransitionSet {
  setOrdering(TransitionSet.ORDERING_TOGETHER)
  addTransition(new Fade(Fade.OUT)).addTransition(new ChangeBounds).addTransition(new Fade(Fade.IN))
}
