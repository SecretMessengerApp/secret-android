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

import java.util

import android.animation.{AnimatorSet, ObjectAnimator}
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.appcompat.widget.Toolbar
import com.jsy.common.adapter.ViewsVpAdapter
import com.jsy.common.moduleProxy.ProxyAppEntryActivity
import com.jsy.common.utils.SoftInputUtils
import com.jsy.common.views.NoScrollViewPager
import com.jsy.res.utils.ViewUtils
import com.waz.api.EmailCredentials
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData.Password
import com.waz.model.{ConfirmationCode, EmailAddress}
import com.waz.service.tracking.TrackingService
import com.waz.service.AccountsService
import com.waz.threading.Threading
import com.waz.zclient.appentry.DialogErrorMessage.EmailError
import com.waz.zclient.appentry.fragments.{FirstLaunchAfterLoginFragment, SignInFragment}
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.globallayout.{IGlobalLayoutController, KeyboardVisibilityObserver}
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.pages.main.profile.validator.{EmailValidator, NameValidator, PasswordValidator}
import com.waz.zclient.tracking.GlobalTrackingController.responseToErrorPair
import com.waz.zclient.tracking.{EnteredCodeEvent, GlobalTrackingController, RegistrationSuccessfulEvent}
import com.waz.zclient.ui.text.{TypefaceEditText, TypefaceTextView}
import com.waz.zclient.ui.utils.KeyboardUtils
import com.waz.zclient.utils.ContextUtils.showErrorDialog
import com.waz.zclient.utils._
import com.waz.zclient.{FragmentHelper, R}

import scala.concurrent.Future
import com.waz.zclient.log.LogUI._

import scala.collection.mutable.Map

class SignInFragment2 extends BaseFragment[SignInFragment2.Container] with FragmentHelper with DerivedLogTag {

  import SignInFragment2._

  implicit def context: Context = getActivity

  implicit val executionContext = Threading.Ui

  private val INTENT_KEY_startedAnimName = "startedAnimName"
  private val INTENT_KEY_startedAnimEmail = "startedAnimEmail"
  private val INTENT_KEY_startedAnimPassword = "startedAnimPassword"
  private val INTENT_KEY_startedAnimVerifyCode = "startedAnimVerifyCode "

  private val INTENT_KEY_inputNameStr = "inputNameStr"
  private val INTENT_KEY_inputEmailStr = "inputEmailStr"
  private val INTENT_KEY_inputPasswordStr = "inputPasswordStr"
  private val INTENT_KEY_inputVerifyCodeStr = "inputVerifyCodeStr "

  private lazy val nameValidator = new NameValidator()
  private lazy val verifyCodeValidator = new NameValidator()
  private lazy val emailValidator = EmailValidator.newInstance()
  private lazy val passwordValidator = PasswordValidator.instance(context)

  private lazy val accountsService = inject[AccountsService]
  private lazy val globalTrackingController = inject[GlobalTrackingController]
  private lazy val trackingService = inject[TrackingService]
  private lazy val browser = inject[BrowserController]
  private lazy val globalLayoutController = inject[IGlobalLayoutController]

  private val handler = new android.os.Handler() {}

  private lazy val toolBar = ViewUtils.getView[Toolbar](getView, R.id.toolBar)
  private lazy val tvNext = ViewUtils.getView[TypefaceTextView](getView, R.id.tvNext)

  private lazy val noScrollViewPager = ViewUtils.getView[NoScrollViewPager](getView, R.id.noScrollViewPager)
  private val views: java.util.List[View] = new util.ArrayList[View]()
  private var viewsVpAdapter: ViewsVpAdapter = _

  private var inputNameStr = ""
  private var inputEmailStr = ""
  private var inputPasswordStr = ""
  private var inputVerifyCodeStr = ""

  private var method: String = _

  private val LOGIN_IDX_EMAIL = 0
  private val LOGIN_IDX_PASSWORD = 1

  private val REGISTRATION_IDX_NAME = 0
  private val REGISTRATION_IDX_EMAIL = 1
  private val REGISTRATION_IDX_PASSWORD = 2
  private val REGISTRATION_IDX_VERIFY_CODE = 3

  private val LAYOUT_ITEM_ID = R.layout.fragment_signin2_item
  private val ID_tvTips = R.id.tvTips
  private val ID_etInput = R.id.etInput
  private val ID_tvSubTips = R.id.tvSubTips
  private val ID_tvError = R.id.tvError
  private val ID_tvBottomLeftTips = R.id.tvBottomLeftTips
  private val ID_tvBottomRightTips = R.id.tvBottomRightTips

  private val translationDistanceResId = R.dimen.dp20
  private var translationDistance: Int = _

  private val startedAnimMap: Map[Int, Boolean] = Map.empty

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    method = if (savedInstanceState != null) savedInstanceState.getString(INTENT_KEY_method) else getArguments.getString(INTENT_KEY_method, "")

    val startedAnimName = if (savedInstanceState != null) savedInstanceState.getBoolean(INTENT_KEY_startedAnimName) else getArguments.getBoolean(INTENT_KEY_startedAnimName, false)
    val startedAnimEmail = if (savedInstanceState != null) savedInstanceState.getBoolean(INTENT_KEY_startedAnimEmail) else getArguments.getBoolean(INTENT_KEY_startedAnimEmail, false)
    val startedAnimPassword = if (savedInstanceState != null) savedInstanceState.getBoolean(INTENT_KEY_startedAnimPassword) else getArguments.getBoolean(INTENT_KEY_startedAnimPassword, false)
    val startedAnimVerifyCode = if (savedInstanceState != null) savedInstanceState.getBoolean(INTENT_KEY_startedAnimVerifyCode) else getArguments.getBoolean(INTENT_KEY_startedAnimVerifyCode, false)

    inputEmailStr = if (savedInstanceState != null) savedInstanceState.getString(INTENT_KEY_inputEmailStr) else getArguments.getString(INTENT_KEY_inputEmailStr, inputEmailStr)
    inputNameStr = if (savedInstanceState != null) savedInstanceState.getString(INTENT_KEY_inputNameStr) else getArguments.getString(INTENT_KEY_inputNameStr, inputNameStr)
    inputPasswordStr = if (savedInstanceState != null) savedInstanceState.getString(INTENT_KEY_inputPasswordStr) else getArguments.getString(INTENT_KEY_inputPasswordStr, inputPasswordStr)
    inputVerifyCodeStr = if (savedInstanceState != null) savedInstanceState.getString(INTENT_KEY_inputVerifyCodeStr) else getArguments.getString(INTENT_KEY_inputVerifyCodeStr, inputVerifyCodeStr)

    startedAnimMap.clear()
    views.clear()
    if (method == METHOD_LOGIN) {
      startedAnimMap += (LOGIN_IDX_EMAIL -> startedAnimEmail, LOGIN_IDX_PASSWORD -> startedAnimPassword)
      views.add(LOGIN_IDX_EMAIL, initViewEmail())
      views.add(LOGIN_IDX_PASSWORD, initViewPassword())
    } else if (method == METHOD_REGISTER) {
      startedAnimMap += (REGISTRATION_IDX_NAME -> startedAnimName, REGISTRATION_IDX_EMAIL -> startedAnimEmail,
        REGISTRATION_IDX_PASSWORD -> startedAnimPassword, REGISTRATION_IDX_VERIFY_CODE -> startedAnimVerifyCode)

      views.add(REGISTRATION_IDX_NAME, initViewName())
      views.add(REGISTRATION_IDX_EMAIL, initViewEmail())
      views.add(REGISTRATION_IDX_PASSWORD, initViewPassword())
      views.add(REGISTRATION_IDX_VERIFY_CODE, initViewVerifyCode())

    } else {
      //...
    }


  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)

    outState.putString(INTENT_KEY_method, method)

    if (method == METHOD_LOGIN) {
      startedAnimMap.get(REGISTRATION_IDX_EMAIL).foreach(outState.putBoolean(INTENT_KEY_startedAnimEmail, _))
      startedAnimMap.get(REGISTRATION_IDX_PASSWORD).foreach(outState.putBoolean(INTENT_KEY_startedAnimPassword, _))

      outState.putString(INTENT_KEY_inputEmailStr, inputEmailStr)
      outState.putString(INTENT_KEY_inputPasswordStr, inputPasswordStr)
    } else if (method == METHOD_REGISTER) {
      startedAnimMap.get(REGISTRATION_IDX_NAME).foreach(outState.putBoolean(INTENT_KEY_startedAnimName, _))
      startedAnimMap.get(REGISTRATION_IDX_EMAIL).foreach(outState.putBoolean(INTENT_KEY_startedAnimEmail, _))
      startedAnimMap.get(REGISTRATION_IDX_PASSWORD).foreach(outState.putBoolean(INTENT_KEY_startedAnimPassword, _))
      startedAnimMap.get(REGISTRATION_IDX_VERIFY_CODE).foreach(outState.putBoolean(INTENT_KEY_startedAnimVerifyCode, _))

      outState.putString(INTENT_KEY_inputNameStr, inputNameStr)
      outState.putString(INTENT_KEY_inputEmailStr, inputEmailStr)
      outState.putString(INTENT_KEY_inputPasswordStr, inputPasswordStr)
      outState.putString(INTENT_KEY_inputVerifyCodeStr, inputVerifyCodeStr)
    }


  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.fragment_signin2, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    translationDistance = getResources.getDimension(translationDistanceResId).toInt

    verbose(l"onViewCreated translationDistance:$translationDistance");

    tvNext.onClick {

      if (method == METHOD_LOGIN) {
        if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
          setCurrentItem(noScrollViewPager.getCurrentItem + 1)
        } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
          dealSigninRegisterEmail()
        }
      } else if (method == METHOD_REGISTER) {
        if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
          setCurrentItem(noScrollViewPager.getCurrentItem + 1)
        } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
          setCurrentItem(noScrollViewPager.getCurrentItem + 1)
        } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
          requestCode()
        } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
          confirmCode()
        }
      } else {
        //...
      }

    }

    toolBar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        onBackPressed
      }
    })

    viewsVpAdapter = new ViewsVpAdapter(views)
    noScrollViewPager.setAdapter(viewsVpAdapter)
    noScrollViewPager.addPageChangeListener { selectedIdx =>
      verbose(l"addPageChangeListener selectedIdx:${selectedIdx}")
      refreshNextEnable()
      updateTipsHint()
    }
    refreshNextEnable()
    updateTipsHint()
    setBottomLeftRightTipsOnClick()

    handler.postDelayed(new Runnable {
      override def run(): Unit = {
        setCurrentItem(0)
      }
    }, 100)

    //globalLayoutController.addKeyboardVisibilityObserver(keyboardVisibilityObserver)

  }

  private def startAnimAfterTextChange(): Unit = {
    val currentFocus = getActivity.getWindow.getCurrentFocus
    var tvTips: TypefaceTextView = null
    var etInput: TypefaceEditText = null
    val idx: Int = noScrollViewPager.getCurrentItem

    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        tvTips = tvTipsEmail
        etInput = etInputEmail
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
        tvTips = tvTipsPassword
        etInput = etInputPassword
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
        tvTips = tvTipsName
        etInput = etInputName
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        tvTips = tvTipsEmail
        etInput = etInputEmail
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
        tvTips = tvTipsPassword
        etInput = etInputPassword
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
        tvTips = tvTipsVerifyCode
        etInput = etInputVerifyCode
      } else {

      }

    } else {
      //...
    }

    if (etInput != null && tvTips != null) {
      if (TextUtils.isEmpty(etInput.getText.toString)) {
        startsToRestoreTipsAnim(tvTips)
        startedAnimMap.put(idx, false)
      } else {
        startedAnimMap.get(idx).foreach {
          case true =>
          case false =>
            startedAnimMap.put(idx, true)
            startsToBeSmallerTipsAnim(tvTips)

        }

      }
    }
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    //globalLayoutController.removeKeyboardVisibilityObserver(keyboardVisibilityObserver)
  }

  override def onDestroy(): Unit = {
    super.onDestroy()
  }

  override def onBackPressed(): Boolean = {
    activity.fold {
      getFragmentManager.popBackStack()
    } { activity =>
      if (!activity.isShowingProgress()) {
        if (noScrollViewPager.getCurrentItem == 0) {
          getFragmentManager.popBackStack()
        } else {
          setCurrentItem(noScrollViewPager.getCurrentItem - 1)
        }
      }
    }
    true
  }

  private def startsToBeSmallerTipsAnim(animView: View): Unit = {
    animView.clearAnimation()
    animView.setVisibility(View.VISIBLE)
    val animXY = new AnimatorSet()
    val scale = 13 / 22f
    val objectAnimatorSX = ObjectAnimator.ofFloat(animView, "scaleX", 1, scale)
    val objectAnimatorSY = ObjectAnimator.ofFloat(animView, "scaleY", 1, scale)
    val objectAnimatorTY = ObjectAnimator.ofFloat(animView, "translationY", 0, -translationDistance)
    animView.setPivotX(0)
    animView.setPivotY(0)
    animXY.setDuration(250)
    animXY.playTogether(objectAnimatorSX, objectAnimatorSY, objectAnimatorTY)
    animXY.start()
  }

  private def startsToRestoreTipsAnim(animView: View): Unit = {
    animView.clearAnimation()
    animView.setVisibility(View.VISIBLE)
    val animXY = new AnimatorSet()
    val scale = 13 / 22f
    val objectAnimatorSX = ObjectAnimator.ofFloat(animView, "scaleX", scale, 1)
    val objectAnimatorSY = ObjectAnimator.ofFloat(animView, "scaleY", scale, 1)
    val objectAnimatorTY = ObjectAnimator.ofFloat(animView, "translationY", -translationDistance, 0)
    animView.setPivotX(0)
    animView.setPivotY(0)
    animXY.setDuration(250)
    animXY.playTogether(objectAnimatorSX, objectAnimatorSY, objectAnimatorTY)
    animXY.start()
  }

  private def refreshNextEnable(): Unit = {
    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        tvNext.setEnabled(emailValidator.validate(inputEmailStr))
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
        tvNext.setEnabled(passwordValidator.validate(inputPasswordStr))
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
        tvNext.setEnabled(nameValidator.validate(inputNameStr))
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        tvNext.setEnabled(emailValidator.validate(inputEmailStr))
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
        tvNext.setEnabled(passwordValidator.validate(inputPasswordStr))
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
        tvNext.setEnabled(verifyCodeValidator.validate(inputVerifyCodeStr))
      }
    }
  }


  private def updateTipsHint(): Unit = {
    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        tvTipsEmail.setText(R.string.sign_in2_your_email_title)
        setSubTips_error(tvErrorEmail, R.string.empty_string)
        setSubTips_error(tvSubTipsEmail, R.string.empty_string)
        etInputEmail.setHint(R.string.empty_string)
        tvBottomLeftTipsEmail.setText(R.string.sign_in2_forget_your_password)
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
        tvTipsPassword.setText(R.string.sign_in2_password_title)
        setSubTips_error(tvSubTipsPassword, getString(R.string.sign_in2_password_subtitle, inputEmailStr))
        etInputEmail.setHint(R.string.empty_string)
        tvBottomLeftTipsPassword.setText(R.string.sign_in2_forget_your_password)
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
        tvTipsName.setText(R.string.sign_up2_name_title)
        setSubTips_error(tvSubTipsName, R.string.empty_string)
        etInputName.setHint(R.string.empty_string)
        tvBottomLeftTipsName.setText(R.string.sign_up2_name_bottom_tips)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        tvTipsEmail.setText(R.string.sign_up2_your_email_title)
        setSubTips_error(tvErrorEmail, R.string.empty_string)
        setSubTips_error(tvSubTipsEmail, R.string.sign_up2_your_email_subtitle)

        etInputEmail.setHint(R.string.empty_string)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
        tvTipsPassword.setText(R.string.sign_up2_your_password)
        setSubTips_error(tvSubTipsPassword, R.string.empty_string)
        etInputPassword.setHint(R.string.empty_string)
        tvBottomLeftTipsPassword.setText(R.string.sign_up2_password_bottom_tips)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
        tvTipsVerifyCode.setText(R.string.sign_up2_email_verify_code)
        setSubTips_error(tvSubTipsVerifyCode, R.string.empty_string)
        etInputVerifyCode.setHint(R.string.empty_string)
        tvBottomLeftTipsVerifyCode.setText(R.string.sign_up2_resend_code)
        tvBottomRightTipsVerifyCode.setText(R.string.sign_up2_change_email)
      }
    }
  }

  private def updateInputError(): Unit = {
    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        if (TextUtils.isEmpty(inputEmailStr)) {
          setSubTips_error(tvErrorEmail, R.string.empty_string)
        } else {
          val errorEmail = if (emailValidator.validate(inputEmailStr)) R.string.empty_string else R.string.sign_in2_invalid_email
          setSubTips_error(tvErrorEmail, errorEmail)
        }

      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
        tvSubTipsPassword.setText(getString(R.string.sign_in2_password_subtitle, inputEmailStr))
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {

      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        if (TextUtils.isEmpty(inputEmailStr)) {
          setSubTips_error(tvErrorEmail, R.string.empty_string)
        } else {
          val errorEmail = if (emailValidator.validate(inputEmailStr)) R.string.empty_string else R.string.sign_in2_invalid_email
          setSubTips_error(tvErrorEmail, errorEmail)
        }

      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {

      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {

      }
    }
  }

  private def setBottomLeftRightTipsOnClick(): Unit = {

    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
      }
      tvBottomLeftTipsEmail.onClick {
        browser.openForgotPassword()
      }
      tvBottomLeftTipsPassword.onClick {
        browser.openForgotPassword()
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
      }
      tvBottomLeftTipsVerifyCode.onClick {
        requestCode()
      }
      tvBottomRightTipsVerifyCode.onClick {
        setCurrentItem(REGISTRATION_IDX_EMAIL)
      }
    }
  }

  private def setCurrentItem(position: Int): Unit = {

    noScrollViewPager.setCurrentItem(position)

    var focusView: View = null
    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        focusView = etInputEmail
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_PASSWORD) {
        focusView = etInputPassword
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
        focusView = etInputName
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        focusView = etInputEmail
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
        focusView = etInputPassword
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {
        focusView = etInputVerifyCode
      }
    }

    verbose(l"setCurrentItem focusView:${focusView}")

    SoftInputUtils.showWindowSoftInputMethod(getActivity, focusView)

    if (method == METHOD_LOGIN) {
      if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
        startedAnimMap.put(LOGIN_IDX_PASSWORD, false)
        etInputPassword.setText(R.string.empty_string)
      } else if (noScrollViewPager.getCurrentItem == LOGIN_IDX_EMAIL) {
      }
    } else if (method == METHOD_REGISTER) {
      if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_NAME) {
        startedAnimMap.put(REGISTRATION_IDX_EMAIL, false)
        etInputEmail.setText(R.string.empty_string)

        startedAnimMap.put(REGISTRATION_IDX_PASSWORD, false)
        etInputPassword.setText(R.string.empty_string)

        startedAnimMap.put(REGISTRATION_IDX_VERIFY_CODE, false)
        etInputVerifyCode.setText(R.string.empty_string)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_EMAIL) {
        startedAnimMap.put(REGISTRATION_IDX_PASSWORD, false)
        etInputPassword.setText(R.string.empty_string)

        startedAnimMap.put(REGISTRATION_IDX_VERIFY_CODE, false)
        etInputVerifyCode.setText(R.string.empty_string)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_PASSWORD) {
        startedAnimMap.put(REGISTRATION_IDX_VERIFY_CODE, false)
        etInputVerifyCode.setText(R.string.empty_string)
      } else if (noScrollViewPager.getCurrentItem == REGISTRATION_IDX_VERIFY_CODE) {

      }
    }

  }

  private var tvTipsName: TypefaceTextView = _
  private var tvSubTipsName: TypefaceTextView = _
  private var etInputName: TypefaceEditText = _
  private var tvErrorName: TypefaceTextView = _
  private var tvBottomLeftTipsName: TypefaceTextView = _
  private var tvBottomRightTipsName: TypefaceTextView = _

  private def initViewName(): View = {
    val itemView = getLayoutInflater.inflate(LAYOUT_ITEM_ID, null, false)
    tvTipsName = itemView.findViewById(ID_tvTips)
    etInputName = itemView.findViewById(ID_etInput)
    etInputName.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
    tvSubTipsName = itemView.findViewById(ID_tvSubTips)
    tvErrorName = itemView.findViewById(ID_tvError)
    tvBottomLeftTipsName = itemView.findViewById(ID_tvBottomLeftTips)
    tvBottomRightTipsName = itemView.findViewById(ID_tvBottomRightTips)

    etInputName.setText(inputNameStr)
    etInputName.addTextListener { input =>

      inputNameStr = input
      refreshNextEnable()
      updateInputError()
      startAnimAfterTextChange()
    }

    etInputName.setAccentColor(Color.BLACK)

    itemView
  }


  private var tvTipsEmail: TypefaceTextView = _
  private var etInputEmail: TypefaceEditText = _
  private var tvSubTipsEmail: TypefaceTextView = _
  private var tvErrorEmail: TypefaceTextView = _
  private var tvBottomLeftTipsEmail: TypefaceTextView = _
  private var tvBottomRightTipsEmail: TypefaceTextView = _

  private def initViewEmail(): View = {
    val itemView = getLayoutInflater.inflate(LAYOUT_ITEM_ID, null, false)
    tvTipsEmail = itemView.findViewById(ID_tvTips)
    etInputEmail = itemView.findViewById(ID_etInput)
    etInputEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
    tvSubTipsEmail = itemView.findViewById(ID_tvSubTips)
    tvErrorEmail = itemView.findViewById(ID_tvError)
    tvBottomLeftTipsEmail = itemView.findViewById(ID_tvBottomLeftTips)
    tvBottomRightTipsEmail = itemView.findViewById(ID_tvBottomRightTips)

    etInputEmail.setText(inputEmailStr)
    etInputEmail.addTextListener { input =>

      inputEmailStr = input
      refreshNextEnable()
      updateInputError()
      startAnimAfterTextChange()
    }
    etInputEmail.setAccentColor(Color.BLACK)

    itemView
  }


  private var tvTipsPassword: TypefaceTextView = _
  private var etInputPassword: TypefaceEditText = _
  private var tvSubTipsPassword: TypefaceTextView = _
  private var tvErrorPassword: TypefaceTextView = _
  private var tvBottomLeftTipsPassword: TypefaceTextView = _
  private var tvBottomRightTipsPassword: TypefaceTextView = _

  private def initViewPassword(): View = {
    val itemView = getLayoutInflater.inflate(LAYOUT_ITEM_ID, null, false)
    tvTipsPassword = itemView.findViewById(ID_tvTips)
    etInputPassword = itemView.findViewById(ID_etInput)
    etInputPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD | android.text.InputType.TYPE_CLASS_TEXT)
    tvSubTipsPassword = itemView.findViewById(ID_tvSubTips)
    tvErrorPassword = itemView.findViewById(ID_tvError)
    tvBottomLeftTipsPassword = itemView.findViewById(ID_tvBottomLeftTips)
    tvBottomRightTipsPassword = itemView.findViewById(ID_tvBottomRightTips)

    etInputPassword.setText(inputPasswordStr)
    etInputPassword.addTextListener { input =>

      inputPasswordStr = input
      refreshNextEnable()
      updateInputError()
      startAnimAfterTextChange()
    }
    etInputPassword.setAccentColor(Color.BLACK)

    itemView
  }


  private var tvTipsVerifyCode: TypefaceTextView = _
  private var tvSubTipsVerifyCode: TypefaceTextView = _
  private var etInputVerifyCode: TypefaceEditText = _
  private var tvErrorVerifyCode: TypefaceTextView = _
  private var tvBottomLeftTipsVerifyCode: TypefaceTextView = _
  private var tvBottomRightTipsVerifyCode: TypefaceTextView = _

  private def initViewVerifyCode(): View = {
    val itemView = getLayoutInflater.inflate(LAYOUT_ITEM_ID, null, false)
    tvTipsVerifyCode = itemView.findViewById(ID_tvTips)
    tvSubTipsVerifyCode = itemView.findViewById(ID_tvSubTips)
    tvErrorVerifyCode = itemView.findViewById(ID_tvError)
    tvBottomLeftTipsVerifyCode = itemView.findViewById(ID_tvBottomLeftTips)
    tvBottomRightTipsVerifyCode = itemView.findViewById(ID_tvBottomRightTips)

    etInputVerifyCode = itemView.findViewById(ID_etInput)
    etInputVerifyCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER)
    etInputVerifyCode.setText(inputPasswordStr)
    etInputVerifyCode.addTextListener { input =>

      inputVerifyCodeStr = input
      refreshNextEnable()
      updateInputError()
      startAnimAfterTextChange()
    }
    etInputVerifyCode.setAccentColor(Color.BLACK)

    itemView
  }


  private def dealSigninRegisterEmail(): Unit = {
    activity.foreach {
      KeyboardUtils.closeKeyboardIfShown(_)
      _.enableProgress(true)
    }

    def onResponse[A](req: Either[ErrorResponse, A]) = {
      globalTrackingController.onEnteredCredentials(req, SignInFragment.SignInMethod(SignInFragment.Login))
      activity.foreach(_.enableProgress(false))
      req match {
        case Left(error) =>
          showErrorDialog(EmailError(error))
          Left({})
        case Right(res) => Right(res)
      }
    }

    for {
      req <- accountsService.loginEmail(inputEmailStr, inputPasswordStr)
    } yield onResponse(req).right.foreach { id =>
      activity.foreach(_.showFragment(FirstLaunchAfterLoginFragment(id), FirstLaunchAfterLoginFragment.Tag))
    }

  }

  def setSubTips_error(textView: TypefaceTextView, text: String): Unit = {
    textView.setVisibility(if (TextUtils.isEmpty(text)) View.GONE else View.VISIBLE)
    textView.setText(text)
  }

  def setSubTips_error(textView: TypefaceTextView, textId: Int): Unit = {
    setSubTips_error(textView, getString(textId))
  }

  private def requestCode() = {
    activity.foreach(_.enableProgress(true))
    KeyboardUtils.hideKeyboard(getActivity)
    accountsService.requestEmailCode(EmailAddress(inputEmailStr)).map {
      case Right(_) =>
        activity.foreach(_.enableProgress(false))
        setCurrentItem(REGISTRATION_IDX_VERIFY_CODE)
        showToast(R.string.new_reg__email_code_resent)
      case Left(err) =>
        activity.foreach(_.enableProgress(false))
        showErrorDialog(EmailError(err))
      //editTextCode.requestFocus
    }
  }

  private def confirmCode(): Unit = {
    activity.foreach(_.enableProgress(true))
    KeyboardUtils.hideKeyboard(getActivity)
    for {
      resp <- accountsService.register(EmailCredentials(EmailAddress(inputEmailStr), Password(inputPasswordStr), Some(ConfirmationCode(inputVerifyCodeStr))), inputNameStr)
      //askMarketingConsent <- inject[GlobalModule].prefs(GlobalPreferences.ShowMarketingConsentDialog).apply()
      //color <- inject[AccentColorController].accentColor.head
//      _ <- resp match {
//        case Right(Some(am)) =>
//          //          (if (!askMarketingConsent) Future.successful(Some(false)) else
//          //            showConfirmationDialog(
//          //              getString(R.string.receive_news_and_offers_request_title),
//          //              getString(R.string.receive_news_and_offers_request_body),
//          //              R.string.app_entry_dialog_accept,
//          //              R.string.app_entry_dialog_no_thanks,
//          //              Some(R.string.app_entry_dialog_privacy_policy),
//          //              color
//          //            )).map { consent =>
//          //            am.setMarketingConsent(consent)
//          //            if (consent.isEmpty) inject[BrowserController].openPrivacyPolicy()
//          //          }
//          Future.successful({})
//        case _ => Future.successful({})
//      }
    } yield {
      trackingService.track(EnteredCodeEvent(SignInFragment.SignInMethod(SignInFragment.Register), responseToErrorPair(resp)))
      resp match {
        case Left(error) =>
          activity.foreach(_.enableProgress(false))
          showErrorDialog(EmailError(error)).map { _ =>
            activity.foreach(KeyboardUtils.showKeyboard(_))
            //editTextCode.requestFocus
            //phoneConfirmationButton.setState(PhoneConfirmationButton.State.INVALID)
          }
        case _ =>
          trackingService.track(RegistrationSuccessfulEvent(SignInFragment.Email))
          activity.foreach(_.enableProgress(false))
          activity.foreach(_.onEnterApplication(openSettings = false))
      }
    }
  }


  def activity = if (getActivity.isInstanceOf[ProxyAppEntryActivity]) Some(getActivity.asInstanceOf[ProxyAppEntryActivity]) else None
}

object SignInFragment2 {

  val INTENT_KEY_method = "method"

  val METHOD_LOGIN = "login"

  val METHOD_REGISTER = "register"

  val Tag = classOf[SignInFragment2].getSimpleName

  trait Container {

  }

  def newInstance(method: String = METHOD_LOGIN): SignInFragment2 = {
    val signInFragment2 = new SignInFragment2()
    val args = new Bundle()
    args.putString(INTENT_KEY_method, method)
    signInFragment2.setArguments(args)

    signInFragment2
  }

}
