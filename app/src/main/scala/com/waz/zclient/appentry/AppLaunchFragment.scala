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
package com.waz.zclient.appentry

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.{AccelerateInterpolator, BounceInterpolator}
import android.view.{LayoutInflater, View, ViewGroup}
import com.jsy.common.fragment.SignInFragment2
import com.jsy.common.moduleProxy.ProxyAppEntryActivity
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.zclient.{FragmentHelper, R}
import com.waz.zclient.utils.RichView

object AppLaunchFragment {

  val Tag: String = getClass.getSimpleName

  def apply(): AppLaunchFragment = {
    val frag = new AppLaunchFragment()
    val args = new Bundle()
    frag.setArguments(args)
    frag
  }
}

class AppLaunchFragment extends FragmentHelper with DerivedLogTag {

  private def activity = if (getActivity.isInstanceOf[ProxyAppEntryActivity]) Some(getActivity.asInstanceOf[ProxyAppEntryActivity]) else None

  /*private lazy val forgetPassword = view[View](R.id.tv_forget_password)*/
  private lazy val registerButton = view[View](R.id.pcb__signin__register)
  private lazy val loginButton = view[View](R.id.pcb__signin__logon)
  /*private lazy val browserController = inject[BrowserController]*/

  private lazy val vAnimGhost = view[View](R.id.vAnimGhost)
  private lazy val vShadow = view[View](R.id.vShadow)

  private var startedAnim = false
  private val INTENT_KEY_startedAnim = "startedAnim"

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    // wire --> R.layout.app_entry_scene
    inflater.inflate(R.layout.fragment_entry_signin_signup, container, false)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    loginButton.foreach(_.onClick(activity.foreach(_.showFragment(SignInFragment2.newInstance(SignInFragment2.METHOD_LOGIN), SignInFragment2.Tag))))
    registerButton.foreach(_.onClick(activity.foreach(_.showFragment(SignInFragment2.newInstance(SignInFragment2.METHOD_REGISTER), SignInFragment2.Tag))))

    if (!startedAnim) {
      vAnimGhost.foreach { vAnimGhost =>
        startedAnim = !startedAnim
        val ty = getResources.getDimension(R.dimen.dp80)
        val objectAnimator = ObjectAnimator.ofFloat(vAnimGhost, "translationY", -ty, 0)
        objectAnimator.setDuration(1500)
        objectAnimator.setInterpolator(new BounceInterpolator())
        objectAnimator.start()
      }
      vShadow.foreach { vShadow =>
        val objectAnimator2 = ObjectAnimator.ofFloat(vShadow, "alpha", 0f, 1.0f)
        objectAnimator2.setInterpolator(new AccelerateInterpolator())
        objectAnimator2.setDuration(2500)
        objectAnimator2.start()
      }
    }
  }


  override def onDestroyView(): Unit = {
    super.onDestroyView()
    vAnimGhost.foreach(_.clearAnimation())
    vShadow.foreach(_.clearAnimation())
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    startedAnim = if (savedInstanceState != null) savedInstanceState.getBoolean(INTENT_KEY_startedAnim) else if (getArguments != null) getArguments.getBoolean(INTENT_KEY_startedAnim) else startedAnim

  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putBoolean(INTENT_KEY_startedAnim, startedAnim)
  }

}
