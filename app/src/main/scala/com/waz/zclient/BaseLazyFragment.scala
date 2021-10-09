/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.waz.zclient

import android.os.Bundle
import android.view.View
import androidx.annotation.Nullable
import com.jsy.secret.sub.swipbackact.utils.LogUtils

class BaseLazyFragment extends FragmentHelper {
  private var isFristVisible = false
  protected var mRootView:View = _

  override def onCreate(@Nullable savedInstanceState: Bundle) = {
    super.onCreate(savedInstanceState)
    LogUtils.d(getClass.getSimpleName,s"onCreate")
    initVariable()
  }

  override def onDestroy() = {
    super.onDestroy()
    LogUtils.d(getClass.getSimpleName,s"onDestroy")
    initVariable()
  }

  private def initVariable() = {
    isFristVisible = true
    mRootView = null
  }

  override def setUserVisibleHint(isVisibleToUser: Boolean): Unit = {
    super.setUserVisibleHint(isVisibleToUser)
    LogUtils.d(getClass.getSimpleName,s"setUserVisibleHint:${isVisibleToUser},rootView:${mRootView}")
    if (mRootView == null) return
    if (isFristVisible && isVisibleToUser) {
      isFristVisible = false
      onFragmentFirstVisible()
      return
    }
    onFragmentVisibleChange(isVisibleToUser)
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)
    LogUtils.d(getClass.getSimpleName,s"onViewCreated")
    if (mRootView == null) {
      mRootView = view
      if (getUserVisibleHint) {
        if (isFristVisible) {
          isFristVisible = false
          onFragmentFirstVisible()
          return
        }
        onFragmentVisibleChange(true)
      }
    }
  }

  protected def onFragmentVisibleChange(isVisibleToUser: Boolean) = {
    LogUtils.d(getClass.getSimpleName,s"onFragmentVisibleChange:${isVisibleToUser}")
  }

  protected def onFragmentFirstVisible() = {
    LogUtils.d(getClass.getSimpleName,s"onFragmentFirstVisible")
  }

  override def onDestroyView(): Unit = {
    super.onDestroyView()
    LogUtils.d(getClass.getSimpleName,s"onDestroyView")
  }

  override def onDetach(): Unit = {
    super.onDetach()
    LogUtils.d(getClass.getSimpleName,s"onDetach")
  }
}
