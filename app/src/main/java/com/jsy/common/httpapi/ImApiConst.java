/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jsy.common.httpapi;


public interface ImApiConst {

    String GroupParticipantInviteConfirmToJoin = "/conversations/%1$s/%2$s/member_join_confirm";

    String APP_VERSION_UPDATE_INFO = "/prod/android";

    String APP_SIGNIN_IPPROXY = "/self/ipproxy";

    String APP_REPORT_UPLOAD_FILE = "/judge/file";

    String APP_REPORT_UPLOAD_CONTENT = "/judge/conversations/%1$s/accusation";

    String APP_CONV_APPLY_UNBLOCK = "/judge/conversations/%1$s/appeal";

    String SCAN_LOGIN = "/self/accept2d";

    String USER_REMARK = "/users/setRemark";

    String RECOMMEND_INVITE_URL = "/conversations/%1$s/invite/url";

    String REQ_USER_KEY = "/self/extid";

    String SCAN_REQ_USER = "/users/by/extid/%1$s";

    String CONVERSATION_TRANSLATE = "/thdmod/google/translate/text";

    String CONVERSATION_INVITE_URL_CHECK = "/conversations/%1$s/invite/url/check";

    String CONVERSATION_JOIN_INVITE = "/conversations/%1$s/join_invite";
}
