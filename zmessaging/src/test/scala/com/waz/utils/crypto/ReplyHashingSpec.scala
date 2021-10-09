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
package com.waz.utils.crypto

import java.math.BigInteger

import com.waz.api.Message
import com.waz.content.AssetsStorage
import com.waz.model.GenericContent.{Location, Text}
import com.waz.model._
import com.waz.specs.AndroidFreeSpec

import scala.concurrent.Future

/**
  * This test class checks that our hashing implementation matches that defined in
  * https://github.com/wearezeta/documentation/blob/master/topics/replies/use-cases/004-quote-hash.md
  */
class ReplyHashingSpec extends AndroidFreeSpec {

  private val timestamp1 = RemoteInstant.ofEpochSec(1540213769)
  private val timestamp2 = RemoteInstant.ofEpochSec(1540213965)
  private val rAssetId1 = RAssetId.apply("3-2-1-38d4f5b9")
  private val rAssetId2 = RAssetId.apply("3-3-3-82a62735")

  private val location1 = (52.5166667.toFloat, 13.4.toFloat)
  private val location2 = (51.509143.toFloat, -0.117277.toFloat)

  private val rt = RemoteInstant.Epoch

  val assetStorage = mock[AssetsStorage]

  feature("hashing of quoted messages") {

    scenario("asset id 1") {
      getReplyHashing.hashAsset(rAssetId1, timestamp1).hexString shouldEqual "bf20de149847ae999775b3cc88e5ff0c0382e9fa67b9d382b1702920b8afa1de"
    }

    scenario("asset id 2") {
      getReplyHashing.hashAsset(rAssetId2, timestamp2).hexString shouldEqual "2235f5b6c00d9b0917675399d0314c8401f0525457b00aa54a38998ab93b90d6"
    }

    scenario("emojis") {
      val str = "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!"
      val hash = getReplyHashing.hashTextReply(str, timestamp1).hexString
      hash shouldEqual "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
    }

    scenario("link text") {
      val str = "https://www.youtube.com/watch?v=DLzxrzFCyOs"
      val hash = getReplyHashing.hashTextReply(str, timestamp1).hexString
      hash shouldEqual "ef39934807203191c404ebb3acba0d33ec9dce669f9acec49710d520c365b657"
    }

    scenario("markdown") {
      val str = "This has **markdown**"
      val hash = getReplyHashing.hashTextReply(str, timestamp2).hexString
      hash shouldEqual "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
    }

    scenario("arabic") {
      val str = "بغداد"
      val hash = getReplyHashing.hashTextReply(str, timestamp2).hexString
      hash shouldEqual "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
    }

    scenario("check location 1") {
      getReplyHashing.hashLocation(location1._1, location1._2, timestamp1).hexString shouldEqual "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
    }

    scenario("check location 2") {
      getReplyHashing.hashLocation(location2._1, location2._2, timestamp1).hexString shouldEqual "803b2698104f58772dbd715ec6ee5853d835df98a4736742b2a676b2217c9499"
    }

  }

  feature("hashing many quotes at the same time") {
    scenario("hash assets") {
      val msg1Id = MessageId("msg1")
      val msg2Id = MessageId("msg2")
      val msg1 = MessageData(id = msg1Id, msgType = Message.Type.ASSET, time = timestamp1)
      val msg2 = MessageData(id = msg2Id, msgType = Message.Type.ASSET, time = timestamp2)

      val asset1 = AssetData(id = AssetId(msg1Id.str), remoteId = Some(rAssetId1))
      val asset2 = AssetData(id = AssetId(msg2Id.str), remoteId = Some(rAssetId2))

      (assetStorage.getAll _).expects(Seq(AssetId(msg1Id.str), AssetId(msg2Id.str))).once.returning(Future.successful(Seq(Option(asset1), Option(asset2))))

      val shas = result(getReplyHashing.hashMessages(Seq(msg1, msg2)))
      hexString(shas(msg1Id)) shouldEqual "bf20de149847ae999775b3cc88e5ff0c0382e9fa67b9d382b1702920b8afa1de"
      hexString(shas(msg2Id)) shouldEqual "2235f5b6c00d9b0917675399d0314c8401f0525457b00aa54a38998ab93b90d6"
    }

    scenario("hash locations") {
      val msg1Id = MessageId("msg1")
      val msg2Id = MessageId("msg2")
      val msg1 = MessageData(id = msg1Id, msgType = Message.Type.LOCATION, time = timestamp1, protos = Seq(GenericMessage(msg1Id.uid, Location(location1._2, location1._1, "", 0, expectsReadConfirmation = false))))
      val msg2 = MessageData(id = msg2Id, msgType = Message.Type.LOCATION, time = timestamp1, protos = Seq(GenericMessage(msg2Id.uid, Location(location2._2, location2._1, "", 0, expectsReadConfirmation = false))))

      (assetStorage.getAll _).expects(*).once.returning(Future.successful(Nil))

      val shas = result(getReplyHashing.hashMessages(Seq(msg1, msg2)))
      hexString(shas(msg1Id)) shouldEqual "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
      hexString(shas(msg2Id)) shouldEqual "803b2698104f58772dbd715ec6ee5853d835df98a4736742b2a676b2217c9499"
    }

    scenario("hash text messages") {
      val msg1Id = MessageId("msg1")
      val msg2Id = MessageId("msg2")
      val msg1 = MessageData(id = msg1Id, msgType = Message.Type.TEXT, time = timestamp2, protos = Seq(GenericMessage(msg1Id.uid, Text("This has **markdown**"))))
      val msg2 = MessageData(id = msg2Id, msgType = Message.Type.TEXT, time = timestamp2, protos = Seq(GenericMessage(msg2Id.uid, Text("بغداد"))))

      (assetStorage.getAll _).expects(*).once.returning(Future.successful(Nil))

      val shas = result(getReplyHashing.hashMessages(Seq(msg1, msg2)))
      hexString(shas(msg1Id)) shouldEqual "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
      hexString(shas(msg2Id)) shouldEqual "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
    }
    
    scenario("hash mixed messages") {
      val msg1Id = MessageId("msg1")
      val msg2Id = MessageId("msg2")
      val msg3Id = MessageId("msg3")
      val msg4Id = MessageId("msg4")
      val msg5Id = MessageId("msg5")
      val msg6Id = MessageId("msg6")
      
      val msg1 = MessageData(id = msg1Id, msgType = Message.Type.ASSET, time = timestamp1)
      val msg2 = MessageData(id = msg2Id, msgType = Message.Type.ASSET, time = timestamp2)
      val msg3 = MessageData(id = msg3Id, msgType = Message.Type.LOCATION, time = timestamp1, protos = Seq(GenericMessage(msg3Id.uid, Location(location1._2, location1._1, "", 0, expectsReadConfirmation = false))))
      val msg4 = MessageData(id = msg4Id, msgType = Message.Type.LOCATION, time = timestamp1, protos = Seq(GenericMessage(msg4Id.uid, Location(location2._2, location2._1, "", 0, expectsReadConfirmation = false))))
      val msg5 = MessageData(id = msg5Id, msgType = Message.Type.TEXT, time = timestamp2, protos = Seq(GenericMessage(msg5Id.uid, Text("This has **markdown**"))))
      val msg6 = MessageData(id = msg6Id, msgType = Message.Type.TEXT, time = timestamp2, protos = Seq(GenericMessage(msg2Id.uid, Text("بغداد"))))
      
      val asset1 = AssetData(id = AssetId(msg1Id.str), remoteId = Some(rAssetId1))
      val asset2 = AssetData(id = AssetId(msg2Id.str), remoteId = Some(rAssetId2))
      val assets = Map(asset1.id -> asset1, asset2.id -> asset2)

      (assetStorage.getAll _).expects(*).anyNumberOfTimes.onCall { assetIds: Traversable[AssetId] =>
        Future.successful(assetIds.map(assets.get).toSeq)
      }
      
      val shas = result(getReplyHashing.hashMessages(Seq(msg3, msg5, msg1, msg4, msg6, msg2)))

      hexString(shas(msg1Id)) shouldEqual "bf20de149847ae999775b3cc88e5ff0c0382e9fa67b9d382b1702920b8afa1de"
      hexString(shas(msg2Id)) shouldEqual "2235f5b6c00d9b0917675399d0314c8401f0525457b00aa54a38998ab93b90d6"
      hexString(shas(msg3Id)) shouldEqual "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
      hexString(shas(msg4Id)) shouldEqual "803b2698104f58772dbd715ec6ee5853d835df98a4736742b2a676b2217c9499"
      hexString(shas(msg5Id)) shouldEqual "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
      hexString(shas(msg6Id)) shouldEqual "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
    }
  }

  private def getReplyHashing = new ReplyHashingImpl(assetStorage)

  private def hexString(sha: Sha256) = String.format("%02X", new BigInteger(1, AESUtils.base64(sha.str))).toLowerCase
}
