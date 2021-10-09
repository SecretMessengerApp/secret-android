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
package com.waz.sync.handler

import com.waz.api.impl.ErrorResponse._
import com.waz.cache.CacheService
import com.waz.model.AssetStatus.{UploadCancelled, UploadFailed, UploadInProgress, UploadNotStarted}
import com.waz.model._
import com.waz.service.assets.AssetService
import com.waz.sync.client.AssetClient.Retention
import com.waz.sync.otr.OtrSyncHandler
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.sync.client.ErrorOrResponse
import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._

class AssetSyncHandler(teamId:  Option[TeamId],
                       cache:   CacheService,
                       assets:  AssetService,
                       otrSync: OtrSyncHandler) {

  import Threading.Implicits.Background
  protected def tag = "Asset"
  private implicit val logTag: LogTag = LogTag(s"${LogTag[AssetSyncHandler].value}[$tag]")

  def uploadAssetData(assetId: AssetId, public: Boolean = false, retention: Retention, isRetry: Boolean = false): ErrorOrResponse[AssetData] = {
    verbose(l"uploadAssetData assetId:$assetId, public:$public,isRetry:$isRetry,teamId.isDefined:${teamId.isDefined}")
  CancellableFuture.lift(assets.updateAsset(assetId, asset => asset.copy(status = if (asset.status == UploadNotStarted) UploadInProgress else asset.status )).zip(assets.getLocalData(assetId))) flatMap {
      case (Some(asset), Some(data)) if data.length > AssetData.maxAssetSizeInBytes(teamId.isDefined) =>
        debug(l"Local data too big. Data length: ${data.length}, max size: ${AssetData.maxAssetSizeInBytes(teamId.isDefined)}, local data: $data, asset: $asset")
        CancellableFuture successful Left(internalError(AssetSyncHandler.AssetTooLarge))

      case (Some(asset), _) if asset.remoteId.isDefined =>
        warn(l"asset has already been uploaded, skipping isRetry:$isRetry, asset:$asset")
        if(asset.status == AssetStatus.UploadDone && isRetry){
          CancellableFuture successful Right(asset)
        }else {
          CancellableFuture.successful(Left(internalError("asset has already been uploaded, skipping")))
        }
      case (Some(asset), Some(data)) if Set[AssetStatus](UploadInProgress, UploadFailed).contains(asset.status) =>
        debug(l"Upload for asset was cancelled asset:$asset,data:$data")
        otrSync.uploadAssetDataV3(data, if (public) None else Some(AESKey()), asset.mime, retention).flatMap {
          case Right(remoteData) => CancellableFuture.lift(assets.updateAsset(asset.id, _.copyWithRemoteData(remoteData)).map {
            case Some(updated) =>
              debug(l"asset update suc updated:updated,remoteData:$remoteData")
              Right(updated)
            case None          =>
              debug(l"asset update failed None:,remoteData:$remoteData")
              Left(internalError("asset update failed"))
          })
          case Left(err) =>
            debug(l"Upload for asset was cancelled otrSync.uploadAssetDataV3 err:$err")
            CancellableFuture successful Left(err)
        }

      case (Some(asset), Some(_)) if asset.status == UploadCancelled =>
        debug(l"Upload for asset was cancelled asset:$asset")
        CancellableFuture successful Left(internalError("Upload for asset was cancelled"))

      case (asset, local) =>
        debug(l"Unable to handle asset upload with asset: $asset, and local data: $local")
        CancellableFuture successful Left(internalError(s"Unable to handle asset upload with asset: $asset, and local data: $local"))
    }
  }
}

object AssetSyncHandler {
  val AssetTooLarge = "Failed to upload asset: Asset is too large"
}