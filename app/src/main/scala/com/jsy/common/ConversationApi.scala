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
package com.jsy.common

import android.content.Context
import com.waz.content.ConversationStorageImpl
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.wrappers.AndroidURIUtil
import com.waz.zclient.common.controllers.SharingController
import com.waz.zclient.common.controllers.SharingController.{FileContent, TextContent, TextJsonContent}
import com.waz.zclient.utils.StringUtils
import com.waz.zclient.{Injectable, WireContext, ZApplication}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}

object ConversationApi extends Injectable with DerivedLogTag{

  private implicit val wContext = WireContext(ZApplication.getInstance())
  private implicit val injector = wContext.injector
  private implicit val executionContext = ExecutionContext.Implicits.global
  implicit val eventContext = EventContext.Implicits.global
  private lazy val multiConversationMsgSendController = inject[SharingController]

  private lazy val zms = inject[Signal[ZMessaging]]


  def updateSystemMessage(convIdNames: Seq[(ConvId, String)]): Unit = {
    for {
      zms <- zms.head
    } yield {
      zms.accountStorage.list().map {
        case accs =>
          accs.foreach { acc =>
            ZMessaging.accountsService.flatMap(_.getZms(acc.id)).map {
              case Some(z) =>
                def updateConv(convId: ConvId, name: String): Unit = {
                  z.convsStorage.update(convId, _.copy(name = Some(Name(name)))).flatMap {
                    case Some((old, updated)) =>
                      z.convsStorage.asInstanceOf[ConversationStorageImpl].onUpdated.!(Seq((old, updated)))
                      Future.successful(Some(old, updated))
                  }
                }

                convIdNames.foreach { convIdName =>
                  z.convsStorage.get(convIdName._1).map {
                    case Some(cData) =>
                      if (cData.name.isDefined) {
                      } else {
                        updateConv(convIdName._1, convIdName._2)
                      }
                    case _ =>
                    // ...
                  }
                }
            }
          }
      }
    }
  }

  def sendMutiMessage(selectUser: Traversable[UserData], content: String, context: Context) = {
    val convIds = selectUser.map(user => ConvId(user.id.str))
    multiConversationMsgSendController.targetConvs ! convIds.toSeq
    multiConversationMsgSendController.sharableContent ! Some(TextContent(content))
    multiConversationMsgSendController.ephemeralExpiration ! None
    multiConversationMsgSendController.sendContent(context)
  }

  def sendMutiMessage(selectUser: Traversable[UserData], file: File, context: Context) = {
    val uri = AndroidURIUtil.fromFile(file)
    val videos = Seq(uri)
    val convIds = selectUser.map(user => ConvId(user.id.str))
    multiConversationMsgSendController.targetConvs ! convIds.toSeq
    multiConversationMsgSendController.sharableContent ! Some(FileContent(videos))
    multiConversationMsgSendController.ephemeralExpiration ! None
    multiConversationMsgSendController.sendContent(context)
  }


  def sendMutiTextJsonMessageOneToOne(selectUser: Traversable[UserData], content: String, context: Context) = {
    val convIds = selectUser.map(user => ConvId(user.id.str))
    multiConversationMsgSendController.targetConvs ! convIds.toSeq
    multiConversationMsgSendController.sharableContent ! Some(TextJsonContent(content))
    multiConversationMsgSendController.ephemeralExpiration ! None
    multiConversationMsgSendController.sendContent(context)
  }


  def sendMutiTextJsonMessage(convId: String, content: String, context: Context) = {

    val convIds = Set(ConvId(convId))
    multiConversationMsgSendController.targetConvs ! convIds.toSeq
    multiConversationMsgSendController.sharableContent ! Some(TextJsonContent(content))
    multiConversationMsgSendController.ephemeralExpiration ! None
    multiConversationMsgSendController.sendContent(context)
  }

  def loadUser(userId: UserId, onLoadUserListener: OnLoadUserListener) = {
    zms.foreach { z =>
      z.users.getOrCreateUser(userId).flatMap { userData =>
        if (userData != null && !StringUtils.isBlank(userData.id.str)) {
          onLoadUserListener.onSuc(userData)
        } else {
          onLoadUserListener.onFail()
        }
        Future.successful({})
      }
    }
  }

  def loadUsers(userIds : Set[UserId],onLoadUsersListener: OnLoadUsersListener): Unit ={
    zms.foreach { z =>
      z.users.findUsersByIds(userIds).flatMap {
        userDatas =>
          if(userDatas.nonEmpty){
            onLoadUsersListener.onSuc(userDatas)
          }else{
            onLoadUsersListener.onFail()
          }
          Future.successful({})
      }
    }
  }


  def getSelf(onLoadUserListener: OnLoadUserListener): Future[UserData] = {
    for {
      zms <- zms.head
      self <- zms.users.selfUser.head
    } yield {
      if (onLoadUserListener != null) {
        onLoadUserListener.onSuc(self)
      }
      self
    }
  }

  def updateGoogleVerifyTime(isOpen : Boolean) = {
    var time = 0
    if(isOpen) time = 600
    else  time = 0
    for{
      zms  <- zms.head
      self <- zms.users.selfUser.head
      _    <- zms.usersStorage.update(self.id,u => u.copy(payValidTime = Some(time)))
    }yield {

    }
  }

  def getUserSync(id: UserId): Option[UserData] = {
    if (zms.currentValue.isEmpty) {
      None
    } else {
      //      None
      zms.currentValue.get.users.getUserSync(id)
    }
  }

  def updateUserData(userId: UserId, updater: UserData => UserData) {
    zms.head.flatMap(_.users.updateUserData(userId, updater))
  }

  def updateUserData(userData: UserData) {
    updateUserData(userData.id, _ => userData)
  }

}


trait OnConversationUpdateListener {

  def onConversationUpdated(conversationData: ConversationData, isSuc: Boolean): Unit

}

trait OnLoadUserListener {
  def onSuc(userData: UserData)

  def onFail()
}

trait OnLoadUsersListener {
  def onSuc(userData: IndexedSeq[UserData])

  def onFail()
}
