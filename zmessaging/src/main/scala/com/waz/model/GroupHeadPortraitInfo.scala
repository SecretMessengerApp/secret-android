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
package com.waz.model

import com.waz.model.AssetMetaData.Image
import com.waz.model.AssetMetaData.Image.Tag
import com.waz.model.AssetMetaData.Image.Tag.{Medium, Preview}
import com.waz.model.AssetStatus.UploadDone
import com.waz.model.UserInfo.Decoder.imageData
import com.waz.utils.{JsonDecoder, JsonEncoder}
import org.json
import org.json.{JSONArray, JSONObject}

import scala.util.Try

/*
https://accounttest.isecret.im/conversations/c25f2ce3ceeeb3bbb975f74ef55c8c52/assets
{
  "assets": [
    {
      "type": "image",
      "key": "3-1-b2d0fadb-704b-4404-a1a8-794cd5446935",
      "size": "complete"
    },
    {
      "type": "image",
      "key": "3-1-41d90f8c-d82d-4f3f-9cee-d5092d565d3a",
      "size": "preview"
    }
  ]
}
  */
case class GroupHeadPortraitInfo(picture: Option[Seq[AssetData]] = None //the empty sequence is used to delete pictures
                                ) {
  //  def mediumPicture = picture.flatMap(_.collectFirst { case a@AssetData.IsImageWithTag(Medium) => a })
}

object GroupHeadPortraitInfo {

  import JsonDecoder._

  def getAssets(implicit js: JSONObject): Option[AssetData] = fromArray(js, "assets") flatMap { assets =>
    Seq.tabulate(assets.length())(assets.getJSONObject).map { js =>
      AssetData(
        remoteId = decodeOptRAssetId('key)(js),
        metaData = Some(AssetMetaData.Image(Dim2(0, 0), Image.Tag(decodeString('size)(js))))
      )
    }.collectFirst { case a@AssetData.IsImageWithTag(Tag.Medium) => a } //discard preview
  }

  def getPicture(userId: UserId)(implicit js: JSONObject): Option[AssetData] = fromArray(js, "picture") flatMap { pic =>
    val id = decodeOptString('correlation_id)(pic.getJSONObject(0).getJSONObject("info")).fold(AssetId())(AssetId(_))

    Seq.tabulate(pic.length())(i => imageData(userId, pic.getJSONObject(i))).collectFirst {
      case a@AssetData.IsImageWithTag(Medium) => a //discard preview
    }.map(_.copy(id = id))
  }


  private def fromArray(js: JSONObject, name: String) = Try(js.getJSONArray(name)).toOption.filter(_.length() > 0)


  implicit object Decoder extends JsonDecoder[GroupHeadPortraitInfo] {

    override def apply(implicit js: JSONObject): GroupHeadPortraitInfo = GroupHeadPortraitInfo(Some(getAssets.toSeq))
  }

  implicit val Encoder: JsonEncoder[GroupHeadPortraitInfo] = JsonEncoder.build(p => js => {
    p.picture.foreach(js.put("assets", _))
  })

}

