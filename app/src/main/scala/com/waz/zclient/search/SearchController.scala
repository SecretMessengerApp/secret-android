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
package com.waz.zclient.search

import com.waz.api.impl.ErrorResponse
import com.waz.model.{Handle, IntegrationData, UserData}
import com.waz.service.{IntegrationsService, SearchResults, UserSearchService}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.conversation.creation.CreateConversationController
import com.waz.zclient.{Injectable, Injector}

import scala.concurrent.duration._

class SearchController(implicit inj: Injector, eventContext: EventContext) extends Injectable {

  import SearchController._

  private val searchService   = inject[Signal[UserSearchService]]

  val filter = Signal("")

  lazy val addUserOrServices: Signal[AddUserListState] = {
    import AddUserListState._
    for {
      filter  <- filter.throttle(500.millis)
      res <- for {
        search      <- searchService
        results <- search.usersForNewConversation(filter.trim, false/*teamOnly*/)
      } yield
        if (results.isEmpty)
          if (filter.trim.isEmpty) NoUsers else NoUsersFound
        else Users(results)
    } yield res
  }

  lazy val searchUserOrServices: Signal[SearchUserListState] = {
    import SearchUserListState._
    for {
      filter  <- filter.throttle(500.millis)
      res <- for {
        search      <- searchService
        results     <- search.search2(filterToHandle(filter))
      } yield
        //TODO make isEmpty method on SE?
        if (results.convs.isEmpty &&
          results.local.isEmpty &&
          results.top.isEmpty &&
          results.dir.isEmpty)
          if (filter.isEmpty) NoUsers else NoUsersFound
        else Users(results)
    } yield res
  }

  def filterToHandle(filter: String): Handle = Handle(Handle.stripSymbol(filter))

}

object SearchController {

  //TODO merge these two types somehow
  sealed trait AddUserListState
  object AddUserListState {
    case object NoUsers extends AddUserListState
    case object NoUsersFound extends AddUserListState
    case class Users(us: Seq[UserData]) extends AddUserListState

    case object NoServices extends AddUserListState
    case object NoServicesFound extends AddUserListState
    case object LoadingServices extends AddUserListState
    /*case class Services(ss: Seq[IntegrationData]) extends AddUserListState*/
    case class Error(err: ErrorResponse) extends AddUserListState
  }

  sealed trait SearchUserListState
  object SearchUserListState {
    case object NoUsers extends SearchUserListState
    case object NoUsersFound extends SearchUserListState
    case class Users(us: SearchResults) extends SearchUserListState

    case object NoServices extends SearchUserListState
    case object NoServicesFound extends SearchUserListState
    case object LoadingServices extends SearchUserListState
    /*case class Services(ss: Seq[IntegrationData]) extends SearchUserListState*/
    case class Error(err: ErrorResponse) extends SearchUserListState
  }

  sealed trait Tab
/*
  object Tab {
    case object People extends Tab
    case object Services extends Tab
  }
*/
}
