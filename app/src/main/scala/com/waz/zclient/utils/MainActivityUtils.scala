/**
 * Secret
 * Copyright (C) 2019 Secret
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
package com.waz.zclient.utils

import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.os.{VibrationEffect, Vibrator}
import com.jsy.common.utils.RomUtil
import com.waz.api.IConversation
import com.waz.model.ConversationData
import com.waz.zclient.{R, ZApplication}

class MainActivityUtils {

}

object MainActivityUtils {

  val RESULT_OK = Activity.RESULT_OK // -1
  val RESULT_CANCELED = Activity.RESULT_CANCELED // 0
  val RESULT_FIRST_USER = Activity.RESULT_FIRST_USER // 1

  val REQUET_CODE_SwitchAccountCode = 789
  val INTENT_KEY_SwitchAccountExtra = "SWITCH_ACCOUNT_EXTRA"
  val REQUEST_CODE_ManageDevices = 790


  val REQUEST_CODE_SELECT_CHAT_BACKGROUND = 1001
  val REQUEST_CODE_CHANGE_CONVERSATION_ONE_TO_ONE_REMARK = 1002

  val REQUEST_CODE_SCAN_LOGIN = 1003

  val INTENT_KEY_FROM_SCAN_PAYMENT = "FROM_SCAN_PAYMENT"

  /**[[com.waz.zclient.pages.main.conversation.AssetIntentsManager.IntentType]]*/
  val REQUEST_CODE_IntentType_UNKNOWN = 1007
  val REQUEST_CODE_IntentType_GALLERY = 1008
  val REQUEST_CODE_IntentType_SKETCH_FROM_GALLERY = 1009
  val REQUEST_CODE_IntentType_VIDEO = 1010
  val REQUEST_CODE_IntentType_VIDEO_CURSOR_BUTTON = 1007
  val REQUEST_CODE_IntentType_CAMERA = 1011
  val REQUEST_CODE_IntentType_FILE_SHARING = 1012
  val REQUEST_CODE_IntentType_BACKUP_IMPORT = 1013
  val REQUEST_CODE_IntentType_EMOJI_SETTING = 1014

  def isGroupConversation(conversationData: ConversationData): Boolean = conversationData != null && (conversationData.convType == IConversation.Type.GROUP | conversationData.convType == IConversation.Type.THROUSANDS_GROUP)

  def isOnlyThousandsGroupConversation(conversationData: ConversationData): Boolean = conversationData != null && conversationData.convType == IConversation.Type.THROUSANDS_GROUP

  def getMd5(orgString: String): String = {
    import java.security.MessageDigest
    val digest = MessageDigest.getInstance("MD5")
    //    val key: String = "3a8db7cf3e690bc2c51df3bf9cd93e8b"
    //    val sign: String = address + key + "SDK"
    digest.digest(orgString.getBytes("UTF-8")).map("%02x".format(_)).mkString
  }

  var mp: MediaPlayer = _

  def playOutGoingMessageAudio(activity: Activity): Unit = {
    if (mp == null) {
      mp = MediaPlayer.create(ZApplication.getInstance(), R.raw.outgoing)
    }

    if(activity != null){
      vibrator(activity)
    }

    if (!mp.isPlaying) mp.start()
  }

  def vibrator(context: Context, ms: Int = 50): Unit = {
    if (context != null) {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE).asInstanceOf[Vibrator]

      if (RomUtil.isEmui && ms == 10) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          vibrator.vibrate(25)
        }
      } else {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
          vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          vibrator.vibrate(ms)
        }
      }
    }
  }

}


