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
package com.waz.api;

public enum ErrorType {
    CANNOT_CREATE_GROUP_CONVERSATION_WITH_UNCONNECTED_USER,
    CANNOT_ADD_UNCONNECTED_USER_TO_CONVERSATION,
    CANNOT_ADD_USER_TO_FULL_CONVERSATION,
    CANNOT_CALL_CONVERSATION_WITH_TOO_MANY_MEMBERS,
    CANNOT_ADD_USER_TO_FULL_CALL,
    CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION,
    CANNOT_SEND_ASSET_TOO_LARGE,
    CANNOT_SEND_ASSET_FILE_NOT_FOUND,
    CANNOT_SEND_VIDEO,
    PLAYBACK_FAILURE,
    RECORDING_FAILURE,
    BOT_REFUSES_TO_JOIN_CONVERSATION,
    CANNOT_ADD_EXIST_USER_TO_CONVERSATION,
    ADD_USER_TO_CONVERSATION_SUC
}
