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
package com.jsy.common.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ConversationUpdateSettingModel implements Serializable {

    @SerializedName("opt_id")
    public String opt_id;

    @SerializedName("opt_name")
    public String opt_name;

    @SerializedName("url_invite")
    public boolean url_invite;

    @SerializedName("confirm")
    public boolean confirm;

    @SerializedName("addright")
    public boolean addright;






}
