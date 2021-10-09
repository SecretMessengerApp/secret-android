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
package com.jsy.res.theme;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;

public interface OptionsTheme {
    Type getType();

    int getTextColorPrimary();

    ColorStateList getTextColorPrimarySelector();

    int getOverlayColor();

    void tearDown();

    int getCheckboxTextColor();

    Drawable getCheckBoxBackgroundSelector();

    ColorStateList getIconButtonTextColor();

    Drawable getIconButtonBackground();

    enum Type {
        DARK,
        LIGHT
    }
}
