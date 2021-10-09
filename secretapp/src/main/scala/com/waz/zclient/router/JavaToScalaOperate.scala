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
package com.waz.zclient.router

import android.app.Activity
import android.content._
import android.text.TextUtils
import androidx.annotation._
import com.jsy.common.acts._
import com.waz.log.BasicLogging.LogTag._
import com.waz.log.InternalLog
import com.waz.model._
import com.waz.permissions.PermissionsService
import com.waz.service._
import com.waz.services.websocket.WebSocketService
import com.waz.threading._
import com.waz.utils.events._
import com.waz.zclient._
import com.waz.zclient.common.controllers.SoundController
import com.waz.zclient.controllers.IControllerFactory
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation._
import com.waz.zclient.log.LogUI._
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.utils.SpUtils

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class JavaToScalaOperate extends Injectable with DerivedLogTag {
  private implicit val wContext = WireContext(ZApplication.getInstance())
  private implicit val executionContext = ExecutionContext.Implicits.global
  //  private implicit val executionContext: ExecutionContext = Threading.Background
  //  private implicit val executionContext = Threading.Ui

  private implicit val eventContext = EventContext.Implicits.global
  def getInjector: Injector = wContext.injector

  def getEventContext: EventContext = {
    EventContext.Implicits.global
  }

  def injectJava[T](cls: Class[T]) = inject[T](reflect.Manifest.classType(cls), getInjector)

  def getControllerFactory(activity: Activity): IControllerFactory = ZApplication.from(activity).getControllerFactory

  def onBaseActivityStart(activity: Activity) = {
    getControllerFactory(activity).getGlobalLayoutController.setActivity(activity)
    ZMessaging.currentUi.onStart()
    uiLifeCycle.acquireUi()
    //    permissions.registerProvider(this)
  }

  def onBaseActivityResume(activity: Activity) = {
    CancellableFuture.delay(150.millis).foreach { _ =>
      WebSocketService(activity.getApplicationContext)
    }(Threading.Ui)
  }

  def onBaseActivityStop(activity: Activity) = {
    debug(l"onStop")
    getControllerFactory(activity).getGlobalLayoutController.tearDown()
    ZMessaging.currentUi.onPause()
    uiLifeCycle.releaseUi()
    InternalLog.flush()
  }

  def uiLifeCycle(): UiLifeCycle = {
    injectJava(classOf[UiLifeCycle])
  }

  def permissions(): PermissionsService = {
    injectJava(classOf[PermissionsService])
  }

  def soundController(): SoundController = {
    injectJava(classOf[SoundController])
  }

  def accountsService(): AccountsService = {
    injectJava(classOf[AccountsService])
  }

  def conversationController(): ConversationController = {
    injectJava(classOf[ConversationController])
  }

  def createConversationController(): CreateConversationController = {
    injectJava(classOf[CreateConversationController])
  }

  def participantsController(): ParticipantsController = {
    injectJava(classOf[ParticipantsController])
  }


  def updateCurrentAccountToken(): Unit = {
    for {
      //      zms <- (accountsService.activeZms.collect { case Some(zms) => zms}).head
      rightAccessToken <- accountsService.getCurrentToken()
      (rightTokenType, rightToken) = if (rightAccessToken.isDefined) {
        (rightAccessToken.get.tokenType, rightAccessToken.get.accessToken)
      } else {
        (null, null)
      }
      existUserId = SpUtils.getUserId(ZApplication.getInstance)
      _ = debug(l"updateCurrentAccountToken 1 existUserId:$existUserId,rightTokenType:$rightTokenType,rightToken:$rightToken")
      accountData <- (ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }).head
      currentAccountData <- if (existUserId.equals(accountData.id.str)) {
        Future.successful(accountData)
      } else {
        for {
          _ <- ZMessaging.currentAccounts.setAccount(Some(UserId(existUserId)))
          newAccountData <- (ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }).head
        } yield {
          newAccountData
        }
      }
      userId = currentAccountData.id
      cookie = currentAccountData.cookie
      (tokenType, accessToken) = if (currentAccountData.accessToken.isDefined) {
        (currentAccountData.accessToken.get.tokenType, currentAccountData.accessToken.get.accessToken)
      } else {
        (null, null)
      }
    } yield {
      SpUtils.putString(ZApplication.getInstance, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_USERID, userId.str)
      if (null != cookie && !TextUtils.isEmpty(cookie.str)) {
        SpUtils.putString(ZApplication.getInstance, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_COOKIES, cookie.str)
      }
      if (!TextUtils.isEmpty(rightToken)) {
        SpUtils.putString(ZApplication.getInstance, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, rightToken)
      }
      if (!TextUtils.isEmpty(rightTokenType)) {
        SpUtils.putString(ZApplication.getInstance, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, rightTokenType)
      }
      debug(l"updateCurrentAccountToken 2 existUserId:$existUserId,userId:$userId,tokenType:$tokenType,accessToken:$accessToken,cookie:$cookie,")
    }
  }

  def toViewUserDetails(@Nullable context: Context, userId: String, isRight: Boolean){
    SendConnectRequestActivity.startSelf(userId, context, isRight, null, true, false)
  }
}
