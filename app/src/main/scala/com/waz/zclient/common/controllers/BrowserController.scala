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
package com.waz.zclient.common.controllers

import android.content.Context
import android.net.Uri
import com.jsy.common.acts.OpenUrlActivity
import com.waz.api.MessageContent.Location
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.MessageId
import com.waz.service.BackendConfig
import com.waz.utils.events.{EventStream, SourceStream}
import com.waz.utils.wrappers.{AndroidURIUtil, URI}
import com.waz.zclient.utils.ContextUtils.getString
import com.waz.zclient.utils.{IntentUtils, ServerConfig}
import com.waz.zclient.{Injectable, Injector, R}

import scala.util.Try

class BrowserController(implicit context: Context, injector: Injector) extends Injectable with DerivedLogTag {

  import BrowserController._

  private lazy val config = inject[BackendConfig]

  val onYoutubeLinkOpened: SourceStream[MessageId] = EventStream[MessageId]()

  private def normalizeHttp(uri: Uri) =
    if (uri.getScheme == null) uri.buildUpon().scheme("http").build()
    else uri.normalizeScheme()

  def openUrl(uri: String): Try[Unit] = openUrl(AndroidURIUtil.parse(uri))

  def openUrl(uri: URI): Try[Unit] = Try {
    OpenUrlActivity.startSelf(context, AndroidURIUtil.unwrap(uri).toString)
  }

  def openLocation(location: Location): Unit =
    Option(IntentUtils.getGoogleMapsIntent(
      context,
      location.getLatitude,
      location.getLongitude,
      location.getZoom,
      location.getName)) foreach {
      context.startActivity
    }

  def openForgotPassword(): Try[Unit] = {
    openUrl(ServerConfig.getAccountsUrl + "/forgot")
  }

  def openForgotPasswordPage(): Try[Unit] =
    openForgotPassword()

  def openAboutWebsite(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openUserNamesLearnMore(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openPrivacyPolicy(): Try[Unit] =
    openUrl(ServerConfig.getSecretServiceUrl + "info/private")

  def openPersonalTermsOfService(): Try[Unit] =
    openUrl(getString(R.string.url_terms_of_service_personal).replaceFirst(Website, config.websiteUrl.toString))

  def openTeamsTermsOfService(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openThirdPartyLicenses(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openOtrLearnHow(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl + "privacy/how")
  }

  def openHelp(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openSupportPage(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openContactSupport(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }

  def openInvalidEmailHelp(): Try[Unit] = {
    openUrl(ServerConfig.getSecretBaseUrl)
  }
}

object BrowserController {
  val Accounts = "\\|ACCOUNTS\\|"
  val Teams = "\\|TEAMS\\|"
  val Website = "\\|WEBSITE\\|"

}
