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
package com.waz.znet2

import com.waz.service.MetaDataService
import okhttp3.{Interceptor, Response}

class OkHttpUserAgentInterceptor(metadata: MetaDataService) extends Interceptor {

  private val WireUserAgent = {
    val androidVersion = metadata.androidVersion
    val wireVersion = metadata.versionName
    val okHttpDefaultUserAgent = okhttp3.internal.Version.userAgent()

    s"Android $androidVersion / Secret $wireVersion / HttpLibrary $okHttpDefaultUserAgent"
  }

  override def intercept(chain: Interceptor.Chain): Response = {
    val request = chain.request.newBuilder.header("User-Agent", WireUserAgent).build
    chain.proceed(request)
  }

}
