/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.jsy.common.model.conversation;

import java.io.Serializable;


public class NormalConvListMenuModel implements Serializable {

    private int resText;
    private int resIcon;
    private int resItemBg;

    public NormalConvListMenuModel(int resText, int resIcon, int resItemBg) {
        this.resText = resText;
        this.resIcon = resIcon;
        this.resItemBg = resItemBg;
    }

    public int getResText() {
        return resText;
    }

    public void setResText(int resText) {
        this.resText = resText;
    }

    public int getResIcon() {
        return resIcon;
    }

    public void setResIcon(int resIcon) {
        this.resIcon = resIcon;
    }

    public int getResItemBg() {
        return resItemBg;
    }

    public void setResItemBg(int resItemBg) {
        this.resItemBg = resItemBg;
    }
}
