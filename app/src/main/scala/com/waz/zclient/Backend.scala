/**
 * Wire
 * Copyright (C) 2019 Secret
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
package com.waz.zclient

import com.waz.service.{BackendConfig, CertificatePin, FirebaseOptions}
import com.waz.zclient.utils.ServerConfig
import com.waz.znet.ServerTrust

object Backend {

  lazy val byName: Map[String, BackendConfig] =
    Seq(StagingBackend, ProdBackend).map(b => b.environment -> b).toMap

  val certPin = CertificatePin(ServerTrust.domain, ServerTrust.CA_trustArray)

  //This information can be found in downloadable google-services.json file from the BE console.
  val StagingFirebaseOptions = FirebaseOptions(
    BuildConfig.FIREBASE_PUSH_SENDER_ID,
    BuildConfig.FIREBASE_APP_ID,
    BuildConfig.FIREBASE_API_KEY)

  val ProdFirebaseOptions = FirebaseOptions(
    BuildConfig.FIREBASE_PUSH_SENDER_ID,
    BuildConfig.FIREBASE_APP_ID,
    BuildConfig.FIREBASE_API_KEY)

  val ProdBackend = BackendConfig(
    environment = "prod",
    ServerConfig.getBaseUrl,
    ServerConfig.getWebSocketUrl,
    ServerConfig.getBlackListHost,
    teamsUrl = ServerConfig.getTeamsUrl,
    accountsUrl = ServerConfig.getAccountsUrl,
    websiteUrl = ServerConfig.getWebsiteUrl,
    signInUrl = ServerConfig.getSignInUrl,
    ProdFirebaseOptions,
    certPin)

  //These are only here so that we can compile tests, the UI sets the backendConfig
  val StagingBackend = ProdBackend

}
