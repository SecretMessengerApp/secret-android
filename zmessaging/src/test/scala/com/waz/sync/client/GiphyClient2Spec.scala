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
package com.waz.sync.client

import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.{AuthenticationConfig, ZIntegrationSpec}
import com.waz.log.LogSE._
import com.waz.utils.RichSeq
import org.scalatest.Ignore

import scala.concurrent.ExecutionContext.Implicits.global

@Ignore
class GiphyClient2Spec extends ZIntegrationSpec with AuthenticationConfig with DerivedLogTag {

  private lazy val giphyClient = new GiphyClientImpl2()

  private val testLimit = 5
  private val testOffset = testLimit
  private val testSearchKeyword = "green"

  feature("Giphy http requests") {

    scenario("loading trending with limit") {
      for {
        result <- giphyClient.loadTrending(limit = testLimit)
        _ = verbose(l"Loading trending with limit result: $result")
      } yield result.size shouldBe testLimit
    }

    scenario("loading trending with offset") {
      for {
        withZeroOffset <- giphyClient.loadTrending(offset = 0, limit = testLimit)
        _ = verbose(l"Loading trending with offset=0 result: $withZeroOffset")
        withTestOffset <- giphyClient.loadTrending(testOffset, testLimit)
        _ = verbose(l"Loading trending with offset=$testOffset result: $withTestOffset")
      } yield (withZeroOffset ++ withTestOffset).distinctBy(_.original.source).size should be > withZeroOffset.size
    }

    scenario("searching by keyword with limit") {
      for {
        result <- giphyClient.search(testSearchKeyword, limit = testLimit)
        _ = verbose(l"Searching by keyword with limit result: $result")
      } yield result.size shouldBe testLimit
    }

    scenario("searching by keyword with offset") {
      for {
        withZeroOffset <- giphyClient.search(testSearchKeyword, offset = 0, limit = testLimit)
        _ = verbose(l"Searching by keyword with offset=0 result: $withZeroOffset")
        withTestOffset <- giphyClient.search(testSearchKeyword, testOffset, testLimit)
        _ = verbose(l"Searching by keyword with offset=$testOffset result: $withTestOffset")
      } yield (withZeroOffset ++ withTestOffset).distinctBy(_.original.source).size should be > withZeroOffset.size
    }

  }


}
