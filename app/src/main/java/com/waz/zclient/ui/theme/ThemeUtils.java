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
package com.waz.zclient.ui.theme;

import android.content.Context;

import com.jsy.res.utils.ResourceUtils;
import com.waz.zclient.R;

public class ThemeUtils {
    public static float getEphemeralBackgroundAlpha(Context context) {
        return com.jsy.res.theme.ThemeUtils.isDarkTheme(context) ?
            ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__primary_alpha__dark_theme) :
            ResourceUtils.getResourceFloat(context.getResources(), R.dimen.ephemeral__accent__primary_alpha);
    }
}
