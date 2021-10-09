/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.jsy.common.utils;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.jsy.common.model.EmojiGifModel;
import com.jsy.common.model.GroupParticipantInviteConfirmModel;
import com.jsy.secret.sub.swipbackact.utils.LogUtils;
import com.waz.api.IConversation;
import com.waz.model.ConvId;
import com.waz.utils.ServerIdConst;
import com.waz.zclient.BuildConfig;
import com.waz.zclient.R;
import com.waz.zclient.utils.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import timber.log.Timber;

public class MessageUtils {

    private static final boolean OPEN_TEXTJSON_DISPLAY = true;

    public static boolean isOpenTextjsonDisplay() {
        return OPEN_TEXTJSON_DISPLAY && BuildConfig.DEBUG;
    }

    public static final String KEY_TEXTJSON_MSGTYPE = ServerIdConst.KEY_TEXTJSON_MSGTYPE;
    public static final String KEY_TEXTJSON_MSGDATA = ServerIdConst.KEY_TEXTJSON_MSGDATA;

    public interface MessageActionType {
        int INVITE_MEMBER_REFRESH = 2;
    }

    public static String iosUidToAndroidUid(String iosUid) {
        return TextUtils.isEmpty(iosUid) ? iosUid : iosUid.toLowerCase();
    }

    public static class MessageContentUtils {

        public static final String INVALID_TYPE           = ServerIdConst.INVALID_TYPE;
        public static final String INVALID_TYPE_EXCEPTION = ServerIdConst.INVALID_TYPE_EXCEPTION;

        public static final String GROUP_PARTICIPANT_INVITE = ServerIdConst.GROUP_PARTICIPANT_INVITE;

        public static final String EMOJI_GIF                   = ServerIdConst.EMOJI_GIF;
        public static final String SCREEN_SHOT                 = ServerIdConst.SCREEN_SHOT;

        public static final String CONV_NOTICE_REPORT_BLOCKED = ServerIdConst.CONV_NOTICE_REPORT_BLOCKED;

        public static final String CONV_SINGLE_EDIT_VERIFY_OPEN         = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_OPEN;
        public static final String CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE   = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE;
        public static final String CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT  = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT;
        public static final String CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE  = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE;
        public static final String CONV_SINGLE_EDIT_VERIFY_CLOSE        = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_CLOSE;
        public static final String CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE  = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE;
        public static final String CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT;
        public static final String CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE = ServerIdConst.CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE;

        public static boolean isEmojiGifJson(String childMsgType) {
            return EMOJI_GIF.equalsIgnoreCase(childMsgType);
        }

        public static final boolean maybeShowChatHead(String childMsgType) {
            if (childMsgType == null) {
                return false;
            }
            switch (childMsgType) {
                case GROUP_PARTICIPANT_INVITE:
                case EMOJI_GIF:
                    return true;
                case SCREEN_SHOT:
                    return false;
                default:
                    return isOpenTextjsonDisplay();
            }
        }

        public static final boolean isGroupForConversation(IConversation.Type conversationType) {
            return conversationType == IConversation.Type.GROUP || conversationType == IConversation.Type.THROUSANDS_GROUP;
        }

        public static final int[] defaultDefaultRes = new int[]{
            R.drawable.icon_group_avatar_blue,
            R.drawable.icon_group_avatar_red,
            R.drawable.icon_group_avatar_green,
            R.drawable.icon_group_avatar_magenta,
            R.drawable.icon_group_avatar_lightblue,
            R.drawable.icon_group_avatar_yellow
        };

        public static final int getGroupDefaultAvatar(ConvId convId) {
            String convStr = null == convId ? "" : convId.str();
            return getGroupDefaultAvatar(convStr);
        }

        public static final int getGroupDefaultAvatar(String convStr) {
            int convHash = TextUtils.isEmpty(convStr) ? 0 : convStr.hashCode();
            int random   = Math.abs(convHash % defaultDefaultRes.length);
            LogUtils.i("MessageContentUtils", "getGroupDefaultAvatar convHash:" + convHash + "==random:" + random);
            return defaultDefaultRes[random];
        }

        public static int getSystemTextJsonNotificationConversationSubTitleRes(String childMsgType) {
            switch (childMsgType) {
                case EMOJI_GIF:
                case CONV_NOTICE_REPORT_BLOCKED:
                case CONV_SINGLE_EDIT_VERIFY_OPEN:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE:
                default:
                    return R.string.empty_string;
            }
        }

        public static String getTextJsonMessageType(String textJsonMessageContent) {
            try {
                JSONObject jsonObj = new JSONObject(textJsonMessageContent);
                return getTextJsonContentType(jsonObj.optString(KEY_TEXTJSON_MSGTYPE));
            } catch (Exception e) {
                e.printStackTrace();
                Timber.d("getTextJsonMessageType textJsonMessageContent: " + textJsonMessageContent + "  e: " + e.getMessage());
                return INVALID_TYPE_EXCEPTION;
            }
        }

        public static String getTextJsonContentType(String childMsgType) {
            String contentType;
            if (StringUtils.isBlank(childMsgType)) {
                contentType = INVALID_TYPE;
            } else {
                contentType = childMsgType;
            }
            switch (childMsgType) {
                case GROUP_PARTICIPANT_INVITE:
                case CONV_NOTICE_REPORT_BLOCKED:
                case EMOJI_GIF:
                case CONV_SINGLE_EDIT_VERIFY_OPEN:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_AGREE:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_REJECT:
                case CONV_SINGLE_EDIT_VERIFY_OPEN_CANCLE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_AGREE:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_REJECT:
                case CONV_SINGLE_EDIT_VERIFY_CLOSE_CANCLE:
                case SCREEN_SHOT:
                    break;
                default:
                    contentType = INVALID_TYPE;
                    break;
            }
            return contentType;
        }

        public static boolean isShouldShowFooterTextJson(String childMsgType) {
            boolean isShouldShowFooter = false;
            switch (childMsgType) {
                case GROUP_PARTICIPANT_INVITE:
                case CONV_NOTICE_REPORT_BLOCKED:
                case EMOJI_GIF:
                    isShouldShowFooter = true;
                    break;
                default:
                    isShouldShowFooter = false;
            }
            return isShouldShowFooter;
        }

        public static String getScreenShotUserId(String content) {

            String userId = "";
            if (!TextUtils.isEmpty(content) && content.startsWith("{") && content.endsWith("}")) {
                try {
                    JSONObject json    = new JSONObject(content);
                    String     msgType = json.getString("msgType");
                    if (SCREEN_SHOT.equals(msgType)) {
                        JSONObject msgData = json.getJSONObject("msgData");
                        userId = msgData.getString("fromUserId");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return userId;
        }
    }

    public static final JSONObject createGroupParticipantInviteMsgJson(GroupParticipantInviteConfirmModel participantInviteConfirmModel) {
        try {
            return new JSONObject(new Gson().toJson(participantInviteConfirmModel));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new JSONObject();
    }

    public static JSONObject createEmojiGifModel(EmojiGifModel emojiGifModel) {
        try {
            return new JSONObject(new Gson().toJson(emojiGifModel));
        }catch(JSONException ignored) {
        }
        return new JSONObject();
    }

    public static final JSONObject createScreenShotJson(String fromUserId) {
        JSONObject msgData = new JSONObject();
        JSONObject js = new JSONObject();
        try {
            msgData.put("fromUserId", fromUserId);
            js.put("msgType", MessageContentUtils.SCREEN_SHOT);
            js.put("msgData", msgData);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return js;
    }

    public static boolean isZH(Context context){
        String country = context.getResources().getConfiguration().locale.getCountry();
        return "CN".equals(country) || "TW".equals(country);
    }

    public static String timeStringToString(String utcTime) {
        if (TextUtils.isEmpty(utcTime)) {
            return "";
        }
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date stringToDate = simpleDateFormat.parse(utcTime);
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(stringToDate);
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }
}
