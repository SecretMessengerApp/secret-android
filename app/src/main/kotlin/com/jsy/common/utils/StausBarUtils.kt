package com.jsy.common.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.jsy.res.theme.ThemeUtils

object StausBarUtils {

    fun setNagivationBarColor(activity:Activity, navigationBarColor: Int) {
        setNagivationBarColor(activity.window,navigationBarColor)
    }
    fun setNagivationBarColor(window: Window, navigationBarColor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.navigationBarColor = navigationBarColor
        }
        val decorView = window.decorView
        var uiFlag = decorView.systemUiVisibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ThemeUtils.isDarkTheme(decorView.context)) {
                uiFlag = uiFlag or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
        }
        decorView.systemUiVisibility = uiFlag
    }
}
