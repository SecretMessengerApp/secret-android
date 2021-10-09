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
package com.waz.zclient.utils

import java.net.URL
import java.util

import android.content.{Context, SharedPreferences}
import android.preference.PreferenceManager
import android.text.TextUtils
import com.jsy.common.httpapi.{ImApiConst, OnHttpListener, SpecialServiceAPI}
import com.jsy.common.model.IpProxyModel
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.service.{BackendConfig, GlobalModule, ZMessaging}
import com.waz.sync.client.CustomBackendClient.{BackendConfigResponse, EndPoints}
import com.waz.utils.events.Signal
import com.waz.zclient.log.LogUI._
import com.waz.zclient.{Backend, BuildConfig, WireApplication}


class BackendController(implicit context: Context) extends DerivedLogTag {

  import BackendController._

  private def prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  /// A custom backend is one that is loaded by a config url via deep link.
  def hasCustomBackend: Boolean = customBackendConfigUrl.isDefined

  /// The url string where the custom backend config was downloaded from.
  def customBackendConfigUrl: Option[String] = getStringPreference(CONFIG_URL_PREF)

  /// Retrieves the backend config stored in shared preferences, if present.
  def getStoredBackendConfig: Option[BackendConfig] = {
    val environment = getStringPreference(ENVIRONMENT_PREF)
    val baseUrl = getStringPreference(BASE_URL_PREF)
    val websocketUrl = getStringPreference(WEBSOCKET_URL_PREF)
    val blackListHost = getStringPreference(BLACKLIST_HOST_PREF)
    val teamsUrl = getStringPreference(TEAMS_URL_PREF)
    val accountsUrl = getStringPreference(ACCOUNTS_URL_PREF)
    val websiteUrl = getStringPreference(WEBSITE_URL_PREF)
    val signInUrl = getStringPreference(SIGNIN_URL_PREF)

    (environment, baseUrl, websocketUrl, blackListHost, teamsUrl, accountsUrl, websiteUrl, signInUrl) match {
      case (Some(env), Some(base), Some(web), Some(black), Some(teams), Some(accounts), Some(website), Some(signIn)) =>
        info(l"Retrieved stored backend config for environment: ${redactedString(env)},baseUrl: $baseUrl")

        // Staging requires its own firebase options, but all other BEs (prod or custom)
        // will use the same firebase options.
        val firebaseOptions = if (env.equals(Backend.StagingBackend.environment))
          Backend.StagingFirebaseOptions
        else
          Backend.ProdFirebaseOptions

        val config = BackendConfig(env, base, web, black, teams, accounts, website, signIn, firebaseOptions, Backend.certPin)
        Some(config)

      case _ =>
        info(l"Couldn't load backend config due to missing data.")
        removeBackendConfig()
        ServerConfig.removeBackendConfig()
        None
    }
  }

  /// Saves the given backend config to shared preferences.
  def setStoredBackendConfig(config: BackendConfig): Unit = {
    prefs.edit()
      .putString(ENVIRONMENT_PREF, config.environment)
      .putString(BASE_URL_PREF, config.baseUrl.toString)
      .putString(WEBSOCKET_URL_PREF, config.websocketUrl.toString)
      .putString(BLACKLIST_HOST_PREF, config.blacklistHost.toString)
      .putString(TEAMS_URL_PREF, config.teamsUrl.toString)
      .putString(ACCOUNTS_URL_PREF, config.accountsUrl.toString)
      .putString(WEBSITE_URL_PREF, config.websiteUrl.toString)
      .putString(SIGNIN_URL_PREF, config.signInUrl.toString)
      .commit()
  }

  /// remove the given backend config to shared preferences.
  def removeBackendConfig(): Unit = {
    prefs.edit()
      .remove(ENVIRONMENT_PREF)
      .remove(BASE_URL_PREF)
      .remove(WEBSOCKET_URL_PREF)
      .remove(BLACKLIST_HOST_PREF)
      .remove(TEAMS_URL_PREF)
      .remove(ACCOUNTS_URL_PREF)
      .remove(WEBSITE_URL_PREF)
      .remove(SIGNIN_URL_PREF)
      .remove(CONFIG_URL_PREF)
      .commit()
  }

  /// Switches the backend in the global module and saves the config to shared preferences.
  /// Warning: use with caution. It is assumed that there are no logged in accounts and the
  /// the global module is ready.
  def switchBackend(globalModule: GlobalModule, configResponse: BackendConfigResponse, configUrl: URL): Unit = {
    globalModule.backend.update(configResponse)
    /*globalModule.blacklistClient.loadVersionBlacklist()*/
    setStoredBackendConfig(globalModule.backend)
    ServerConfig.updateBackendConfig(configResponse)
    prefs.edit().putString(CONFIG_URL_PREF, configUrl.toString).commit()
  }

  def shouldShowBackendSelector: Boolean =
    BuildConfig.DEBUG && !backendPreferenceExists

  // This is a helper method to dismiss the backend selector dialog when QA automation
  // selects the backend via an intent.
  def onPreferenceSet(callback: BackendConfig => Unit): Unit = {
    val listener = new SharedPreferences.OnSharedPreferenceChangeListener {
      override def onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String): Unit = {
        if (key.equals(ENVIRONMENT_PREF)) {
          callback(getStoredBackendConfig.getOrElse(Backend.ProdBackend))
          prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
      }
    }

    prefs.registerOnSharedPreferenceChangeListener(listener)
  }

  private def backendPreferenceExists: Boolean =
    prefs.contains(ENVIRONMENT_PREF)

  private def getStringPreference(key: String): Option[String] =
    Option(prefs.getString(key, null))

  def updateBackendConfig(): Unit = {
    val configUrl: String = ServerConfig.getForceConfigUrl()
    verbose(l"getCustomBackend() updateBackendConfig configUrl:${Option(configUrl)}")
    saveRefreshBackendConfig(configUrl)
  }

  def saveRefreshBackendConfig(configUrl: String, userId: String = null): Unit = {
    if (Option(ZMessaging.currentGlobal).isDefined) {
      Option(context.getApplicationContext.asInstanceOf[WireApplication].module).foreach { injector =>
        injector.binding[GlobalModule] match {
          case Some(globalModule) =>
            val isSame = ServerConfig.isSameConfigUrl(configUrl)
            verbose(l"getCustomBackend() injector.binding[GlobalModule] suc saveBackendConfig isSame:$isSame, configUrl:${Option(configUrl)}")
            if (!TextUtils.isEmpty(configUrl) && !isSame) {
              val isSuc = ServerConfig.saveConfigUrl(configUrl, userId)
              if (isSuc) {
                val configResponse: BackendConfigResponse = BackendConfigResponse(
                  endpoints = EndPoints(
                    backendURL = new URL(configUrl),
                    backendWSURL = new URL(configUrl + BuildConfig.WEBSOCKET_URL),
                    blackListURL = new URL(configUrl + BuildConfig.BLACKLIST_HOST),
                    teamsURL = new URL(configUrl),
                    accountsURL = new URL(ServerConfig.getAccountsUrl),
                    websiteURL = new URL(configUrl),
                    signInUrl = new URL(ServerConfig.getSignInUrl)
                  ),
                  title = ServerConfig.getEnvironment
                )
                switchBackend(globalModule(), configResponse, new URL(ServerConfig.getSignInUrl + ImApiConst.APP_SIGNIN_IPPROXY))
                WireApplication.APP_INSTANCE.ensureInitialized(true)

                injector.binding[Signal[ZMessaging]] match {
                  case Some(z) =>
                    verbose(l"getCustomBackend() injector.binding[Signal[ZMessaging]] suc saveBackendConfig")
                    for {
                      z <- z.apply()
                    } yield {
                      verbose(l"getCustomBackend() injector.binding[Signal[ZMessaging]] restartWebSocket saveBackendConfig")
                      z.wsPushService.restartWebSocket()
                    }
                  case _ =>
                    verbose(l"getCustomBackend() injector.binding[Signal[ZMessaging]] isEmpty saveBackendConfig")
                }
              }
            }
          case _ =>
            verbose(l"getCustomBackend() injector.binding[GlobalModule] isEmpty saveBackendConfig configUrl:${Option(configUrl)}")
        }
      }
    } else {
      verbose(l"getCustomBackend() Option(ZMessaging.currentGlobal) isEmpty saveBackendConfig configUrl:${Option(configUrl)}")
    }
  }

  def getCustomBackend(): Unit = {
    verbose(l"getCustomBackend() req signInGet configUrl")
    SpecialServiceAPI.getInstance().signInGet(ImApiConst.APP_SIGNIN_IPPROXY, false, new OnHttpListener[IpProxyModel] {

      override def onFail(code: Int, err: String): Unit = {
        verbose(l"getCustomBackend() configUrl onFail code:$code, err:${Option(err)}")
      }

      override def onSuc(ipProxy: IpProxyModel, orgJson: String): Unit = {
        val configUrl: String = if (null != ipProxy) ipProxy.getIp else ""
        verbose(l"getCustomBackend() configUrl suc 1 orgJson:${Option(orgJson)}")
        saveRefreshBackendConfig(configUrl, ipProxy.getUid)
      }

      override def onSuc(r: util.List[IpProxyModel], orgJson: String): Unit = {
        verbose(l"getCustomBackend() configUrl 2 onSuc orgJson:${Option(orgJson)}")
      }
    })
  }

}

object BackendController {
  // Preference Keys
  val ENVIRONMENT_PREF: String = "CUSTOM_BACKEND_ENVIRONMENT"
  val BASE_URL_PREF: String = "CUSTOM_BACKEND_BASE_URL"
  val WEBSOCKET_URL_PREF: String = "CUSTOM_BACKEND_WEBSOCKET_URL"
  val BLACKLIST_HOST_PREF: String = "CUSTOM_BACKEND_BLACKLIST_HOST"
  val TEAMS_URL_PREF: String = "CUSTOM_BACKEND_TEAMS_URL"
  val ACCOUNTS_URL_PREF: String = "CUSTOM_BACKEND_ACCOUNTS_URL"
  val WEBSITE_URL_PREF: String = "CUSTOM_BACKEND_WEBSITE_URL"
  val SIGNIN_URL_PREF: String = "CUSTOM_BACKEND_SIGNIN_URL"
  val CONFIG_URL_PREF: String = "CUSTOM_BACKEND_CONFIG_URL"
}
