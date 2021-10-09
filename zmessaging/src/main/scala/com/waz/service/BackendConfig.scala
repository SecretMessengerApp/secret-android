/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import com.waz.sync.client.CustomBackendClient.BackendConfigResponse
import com.waz.utils.wrappers.URI
import com.waz.znet.ServerTrust

class BackendConfig(private var _environment: String,
                    private var _baseUrl: URI,
                    private var _websocketUrl: URI,
                    private var _blacklistHost: URI,
                    private var _teamsUrl: URI,
                    private var _accountsUrl: URI,
                    private var _websiteUrl: URI,
                    private var _signInUrl: URI,
                    val firebaseOptions: FirebaseOptions,
                    val pin: CertificatePin = ServerTrust.wirePin) {

  val pushSenderId: String = firebaseOptions.pushSenderId

  def environment: String = _environment
  def baseUrl: URI = _baseUrl
  def websocketUrl: URI = _websocketUrl
  def blacklistHost: URI = _blacklistHost
  def teamsUrl: URI = _teamsUrl
  def accountsUrl: URI = _accountsUrl
  def websiteUrl: URI = _websiteUrl
  def signInUrl: URI = _signInUrl

  def update(configResponse: BackendConfigResponse): Unit = {
    _environment = configResponse.title
    _baseUrl = URI.parse(configResponse.endpoints.backendURL.toString)
    _websocketUrl = URI.parse(configResponse.endpoints.backendWSURL.toString)
    _blacklistHost = URI.parse(configResponse.endpoints.blackListURL.toString)
    _teamsUrl = URI.parse(configResponse.endpoints.teamsURL.toString)
    _accountsUrl = URI.parse(configResponse.endpoints.accountsURL.toString)
    _websiteUrl = URI.parse(configResponse.endpoints.websiteURL.toString)
    _signInUrl = URI.parse(configResponse.endpoints.signInUrl.toString)
  }
}

object BackendConfig {
  def apply(environment: String,
            baseUrl: String,
            websocketUrl: String,
            blacklistHost: String,
            teamsUrl: String,
            accountsUrl: String,
            websiteUrl: String,
            signInUrl: String,
            firebaseOptions: FirebaseOptions,
            pin: CertificatePin = ServerTrust.wirePin): BackendConfig =
    new BackendConfig(
      environment,
      URI.parse(baseUrl),
      URI.parse(websocketUrl),
      URI.parse(blacklistHost),
      URI.parse(teamsUrl),
      URI.parse(accountsUrl),
      URI.parse(websiteUrl),
      URI.parse(signInUrl),
      firebaseOptions,
      pin)
}

//cert is expected to be base64-encoded
case class CertificatePin(domain: String, cert: Array[Byte])

case class FirebaseOptions(pushSenderId: String, appId: String, apiKey: String)
