/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.jsy.common.utils;

import com.bumptech.glide.request.RequestOptions;
import com.waz.zclient.R;

public class GlideHelper {

    private static RequestOptions CIRCLE_CROP_OPTIONS  = null;

    public static RequestOptions getCircleCropOptions() {
        if (CIRCLE_CROP_OPTIONS == null) {
            CIRCLE_CROP_OPTIONS = new RequestOptions().placeholder(R.drawable.circle_noname).error(R.drawable.circle_noname);
        }
        return CIRCLE_CROP_OPTIONS;
    }
}
