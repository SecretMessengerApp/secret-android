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
package com.waz.zclient.camera;

import android.graphics.Rect;

import java.util.Set;

public interface CameraPreviewObserver {

    void onCameraLoaded(Set<FlashMode> flashModes);

    void onCameraLoadingFailed();

    void onPictureTaken(byte[] imageData, boolean mirrored);

    void onFocusBegin(Rect focusArea);

    void onFocusComplete();

    void onCameraReleased();
}
