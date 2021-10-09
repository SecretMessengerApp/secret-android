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
package com.waz.service

import com.waz.api.User.ConnectionStatus
import com.waz.content._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.SearchQuery.Recommended
import com.waz.model._
import com.waz.service.conversation.{ConversationsService, ConversationsUiService}
import com.waz.service.teams.TeamsService
import com.waz.specs.AndroidFreeSpec
import com.waz.sync.SyncServiceHandle
import com.waz.testutils.TestUserPreferences
import com.waz.utils.Managed
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.utils.wrappers.DB
import org.threeten.bp.Instant

import scala.collection.breakOut
import scala.collection.generic.CanBuild
import scala.concurrent.Future

class UserSearchServiceSpec extends AndroidFreeSpec with DerivedLogTag {

  val emptyTeamId       = Option.empty[TeamId]
  val teamId            = Option(TeamId("59bbc94c-2618-491a-8dba-cf6f94c65873"))
  val partnerPermissions: Long = 1025
  val memberPermissions: Long = 1587
  val adminPermissions: Long = 5951

  val queryCacheStorage = mock[SearchQueryCacheStorage]
  val userService       = mock[UserService]
  val usersStorage      = mock[UsersStorage]
  val membersStorage    = mock[MembersStorage]
  val teamsService      = mock[TeamsService]
  val sync              = mock[SyncServiceHandle]
  val messagesStorage   = mock[MessagesStorage]
  val convsUi           = mock[ConversationsUiService]
  val convsStorage      = mock[ConversationStorage]
  val convs             = mock[ConversationsService]
  val timeouts          = new Timeouts
  val userPrefs         = new TestUserPreferences

  lazy val users = Map(
    id('me) -> UserData(id('me), "A user"),
    id('a) -> UserData(id('a), "other user 1"),
    id('b) -> UserData(id('b), "other user 2"),
    id('c) -> UserData(id('c), "some name"),
    id('d) -> UserData(id('d), "related user 1").copy(relation = Relation.Second), // TODO: relation does not exists anymore, can be removed!
    id('e) -> UserData(id('e), "related user 2").copy(relation = Relation.Second),
    id('f) -> UserData(id('f), "other related").copy(relation = Relation.Third),
    id('g) -> UserData(id('g), "friend user 1").copy(connection = ConnectionStatus.ACCEPTED),
    id('h) -> UserData(id('h), "friend user 2").copy(connection = ConnectionStatus.ACCEPTED),
    id('i) -> UserData(id('i), "some other friend").copy(connection = ConnectionStatus.ACCEPTED),
    id('j) -> UserData(id('j), "meep moop").copy(email = Some(EmailAddress("moop@meep.me"))),
    id('k) -> UserData(id('k), "unconnected user").copy(connection = ConnectionStatus.UNCONNECTED),
    id('l) -> UserData(id('l), "Björn-Rodrigo Smith"),
    id('m) -> UserData(id('m), "John Smith"),
    id('n) -> UserData(id('n), "Jason-John Mercier"),
    id('o) -> UserData(id('o), "Captain Crunch").copy(handle = Some(Handle("john"))),
    id('p) -> UserData(id('p), "Peter Pan").copy(handle = Some(Handle("john"))),
    id('q) -> UserData(id('q), "James gjohnjones"),
    id('r) -> UserData(id('r), "Liv Boeree").copy(handle = Some(Handle("testjohntest"))),
    id('s) -> UserData(id('s), "blah").copy(handle = Some(Handle("mores"))),
    id('t) -> UserData(id('t), "test handle").copy(handle = Some(Handle("smoresare"))),
    id('u) -> UserData(id('u), "Wireless").copy(expiresAt = Some(RemoteInstant.ofEpochMilli(12345L))),
    id('v) -> UserData(id('v), "Wireful"),
    id('pp1) -> UserData(id('pp1), "Partner 1").copy(
      permissions = (partnerPermissions, partnerPermissions),
      teamId = teamId,
      handle = Some(Handle("pp1")),
      createdBy = Some(id('aa1))
    ),
    id('pp2) -> UserData(id('pp2), "Partner 2").copy(
      permissions = (partnerPermissions, partnerPermissions),
      teamId = teamId,
      handle = Some(Handle("pp2")),
      createdBy = Some(id('aa2))
    ),
    id('pp3) -> UserData(id('pp3), "Partner 3").copy(
      permissions = (partnerPermissions, partnerPermissions),
      teamId = teamId,
      handle = Some(Handle("pp3"))
    ),
    id('mm1) -> UserData(id('mm1), "Member 1").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle("mm1")),
      createdBy = Some(id('aa1))
    ),
    id('mm2) -> UserData(id('mm2), "Member 2").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle("mm2")),
      createdBy = Some(id('aa1))
    ),
    id('mm3) -> UserData(id('mm3), "Member 3").copy(
      permissions = (memberPermissions, memberPermissions),
      teamId = teamId,
      handle = Some(Handle("mm3")),
      createdBy = Some(id('aa1))
    ),
    id('aa1) -> UserData(id('aa1), "Admin 1").copy(
      permissions = (adminPermissions, adminPermissions),
      teamId = teamId,
      handle = Some(Handle("aa1"))
    ),
    id('aa2) -> UserData(id('aa2), "Admin 2").copy(
      permissions = (adminPermissions, adminPermissions),
      teamId = teamId,
      handle = Some(Handle("aa2"))
    )
  )

  // Mock search in team
  (teamsService.searchTeamMembers _).expects(*, *).anyNumberOfTimes().onCall { (query, handleOnly) =>
    Signal.const(
      users
        .filter(u => u._2.teamId == teamId)
        .filter(_._2.matchesQuery(query, handleOnly))
        .map(_._2).toSet
    )
  }

  scenario("search conversation with token starting with query") {

    val convMembers = Set(id('l), id('b))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"rod")
    result(res.filter(_.size == 1).head)
  }

  scenario("search conversation with name starting with query") {

    val convMembers = Set(id('l), id('b))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"bjo")
    result(res.filter(_.size == 1).head)
  }

  scenario("search conversation with name containing query") {

    val convMembers = Set(id('l), id('m))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"rn")
    result(res.filter{u => println(u.map(_.displayName));u.size == 1}.head)
  }

  scenario("search conversation with handle containing query") {

    val convMembers = Set(id('s), id('t))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"mores")
    result(res.filter(_.size == 2).head)
  }

  scenario("search conversation handle beginning with query") {

    val convMembers = Set(id('s), id('t))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"smores")
    result(res.filter(_.size == 1).head)
  }

  scenario("search conversation people ordering") {

    val convMembers = Set(id('q), id('r),id('p), id('n), id('m), id('o))
    val correctOrder = IndexedSeq(ud('m), ud('n), ud('o), ud('p), ud('q), ud('r))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
    (membersStorage.activeMembers _).expects(*).anyNumberOfTimes().returning(Signal.const(convMembers))
    (usersStorage.listSignal _).expects(*).once().returning(Signal.const(convMembers.map(users).toVector))

    val res = getService(false, id('me)).mentionsSearchUsersInConversation(ConvId("123"),"john")

    result(res.filter(_.equals(correctOrder)).head)
  }

  def id(s: Symbol) = UserId(s.toString)
  def ids(s: Symbol*) = s.map(id)(breakOut).toSet
  def ud(s: Symbol) = users(id(s))

  def verifySearch(prefix: String, matches: Set[UserId]) = {
    val query = Recommended(prefix)
    val expected = users.filterKeys(matches.contains).values.toVector
    val querySignal = Signal[Option[SearchQueryCache]]()
    val firstQueryCache = SearchQueryCache(query, Instant.now, None)
    val secondQueryCache = SearchQueryCache(query, Instant.now, Some(matches.toVector))

    (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful({}))

    (queryCacheStorage.optSignal _).expects(query).once().returning(querySignal)
    (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
      .expects(*, *, *, *).once().returning(Future.successful(expected))

    (queryCacheStorage.updateOrCreate _).expects(query, *, *).once().returning {
      Future.successful(secondQueryCache)
    }

    (sync.syncSearchQuery _).expects(query).once().onCall { _: SearchQuery =>
      Future.successful {
        querySignal ! Some(secondQueryCache)
        result(querySignal.filter(_.contains(secondQueryCache)).head)
        SyncId()
      }
    }

    if (matches.nonEmpty)
      (usersStorage.listSignal _).expects(*).once().returning(Signal.const(expected))
    else
      (usersStorage.listSignal _).expects(*).never()

    querySignal ! Some(firstQueryCache)
    result(querySignal.filter(_.contains(firstQueryCache)).head)
    
    val resSignal = getService(false, id('me)).searchUserData(Recommended(prefix)).map(_.map(_.id)).disableAutowiring()

    result(querySignal.filter(_.contains(secondQueryCache)).head)

    result(resSignal.map(_.toSet).filter(_ == matches).head)
  }

  feature("Recommended people search") {
    scenario("Return search results for name") {
      verifySearch("rel", ids('d, 'e))
    }

    scenario("Return no search results for name") {
      verifySearch("relt", Set.empty[UserId])
    }

    scenario("Return search results for handle") {
      verifySearch("@rel", ids('d, 'e))
    }

    scenario("Return no search results for handle") {
      verifySearch("@relt", Set.empty[UserId])
    }

  }

  feature("Search by searchState") {
    scenario("search for top people"){
      val expected = ids('g, 'h, 'i)

      (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .expects(*, *, *, *).once().returning(Future.successful(expected.map(users).toVector))

      (userService.acceptedOrBlockedUsers _).expects().returns(Signal.const(Map.empty[UserId, UserData]))
      (messagesStorage.countLaterThan _).expects(*, *).repeated(3).returning(Future.successful(1L))

      val res = getService(false, id('me)).search("").map(_.top.map(_.id).toSet)

      result(res.filter(_ == expected).head)
    }

    scenario("search for local results"){
      val expected = ids('g, 'h)
      val query = Recommended("fr")

      val querySignal = new SourceSignal[Option[SearchQueryCache]]()
      val queryCache = SearchQueryCache(query, Instant.now, Some(Vector.empty[UserId]))

      (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
      (queryCacheStorage.optSignal _).expects(query).once().returning(querySignal)

      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .expects(*, *, *, *).once().returning(Future.successful(Vector.empty[UserData]))
      (userService.acceptedOrBlockedUsers _).expects().once().returning(Signal.const(expected.map(key => (key -> users(key))).toMap))

      (convsStorage.findGroupConversations _).expects(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))
      (queryCacheStorage.updateOrCreate _).expects(*, *, *).once().returning(Future.successful(queryCache))

      (sync.syncSearchQuery _).expects(query).once().onCall { _: SearchQuery =>
        Future.successful[SyncId] {
          querySignal ! Some(queryCache)
          result(querySignal.filter(_.contains(queryCache)).head)
          SyncId()
        }
      }

      (usersStorage.listSignal _).expects(*).never()

      val res = getService(false, id('me)).search("fr").map(_.local.map(_.id).toSet)

      result(res.filter(_ == expected).head)
    }

    scenario("search for remote results") {
      val expected = ids('a, 'b)
      val query = Recommended("ot")

      val querySignal = new SourceSignal[Option[SearchQueryCache]]()
      val queryCache = SearchQueryCache(query, Instant.now, Some(expected.toVector))

      (queryCacheStorage.deleteBefore _).expects(*).anyNumberOfTimes().returning(Future.successful[Unit]({}))
      (queryCacheStorage.optSignal _).expects(query).once().returning(querySignal)

      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .expects(*, *, *, *).once().returning(Future.successful(Vector.empty[UserData]))
      (userService.acceptedOrBlockedUsers _).expects().once().returning(Signal.const(Map.empty[UserId, UserData]))

      (convsStorage.findGroupConversations _).expects(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))
      (queryCacheStorage.updateOrCreate _).expects(*, *, *).once().returning(Future.successful(queryCache))

      (sync.syncSearchQuery _).expects(query).once().onCall { _: SearchQuery =>
        Future.successful[SyncId] {
          querySignal ! Some(queryCache)
          result(querySignal.filter(_.contains(queryCache)).head)
          SyncId()
        }
      }

     (usersStorage.listSignal _).expects(expected.toVector).once().returning(Signal.const(expected.map(users).toVector))

      val res = getService(false, id('me)).search("ot").map(_.dir.map(_.id).toSet)

      result(res.filter(_.nonEmpty).head)
    }
  }

  feature("search inside the team") {

    /**
    * Helper class to keep track of mocked query
      */
    case class PreparedSearch(inTeam: Boolean, selfId: UserId, query: String) {

      def perform() = {
        val service = getService(this.inTeam, this.selfId)
        service.search(query).map(_.local.map(_.id).toSet).head
      }
    }

    /**
    * Will mock all services, instantiate a UserSearchService to test, and store the query to expect
      */
    def prepareTestSearch(query: String,
                            selfId: UserId,
                            conversationMembers: Set[UserId] = Set(),
                            connectedUsers: Set[UserId] = Set()
                           ): PreparedSearch = {
      val convId = ConvId("e7969e91-366d-4ec5-9d85-4e8a4f9d53e6")
      val searchQuery = Recommended(query)

      val querySignal = new SourceSignal[Option[SearchQueryCache]]()
      val queryCache = SearchQueryCache(searchQuery, Instant.now, Some(Vector.empty[UserId]))

      // Stubs
      (queryCacheStorage.deleteBefore _).stubs(*).returning(Future.successful[Unit]({}))
      (queryCacheStorage.optSignal _).stubs(searchQuery).returning(querySignal)

      (usersStorage.get _).stubs(*).onCall { id: UserId =>
        Future.successful(users.get(id))
      }
      (usersStorage.find(_: UserData => Boolean, _: DB => Managed[TraversableOnce[UserData]], _: UserData => UserData)(_: CanBuild[UserData, Vector[UserData]]))
        .stubs(*, *, *, *).returning(Future.successful(Vector.empty[UserData]))
      (userService.acceptedOrBlockedUsers _).stubs().returning(Signal.const(users.filterKeys(connectedUsers.contains)))
      (userService.getSelfUser _).stubs().onCall(_ => Future.successful(users.get(selfId)))

      (convsStorage.findGroupConversations _).stubs(*, *, *, *).returns(Future.successful(IndexedSeq.empty[ConversationData]))
      (queryCacheStorage.updateOrCreate _).stubs(*, *, *).returning(Future.successful(queryCache))

      (membersStorage.getByUsers _).stubs(*).onCall { ids: Set[UserId] =>
        Future.successful(ids.intersect(conversationMembers).map(ConversationMemberData(_, convId)).toIndexedSeq)
      }

      (sync.syncSearchQuery _).stubs(*).onCall { _: SearchQuery =>
        Future.successful[SyncId] {
          querySignal ! Some(queryCache)
          result(querySignal.filter(_.contains(queryCache)).head)
          SyncId()
        }
      }

      // Set up user permissions according to role
      val user = users(selfId)
      userPrefs.setValue(UserPreferences.SelfPermissions, user.permissions._1)

      PreparedSearch(true, selfId, query)
    }

    scenario("as a member, search partners that are not in a conversation with me") {
      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Partner",
        selfId = id('mm1),
        conversationMembers = ids('a, 'mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a member, search partners that are in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Partner",
        selfId = id('mm1),
        conversationMembers = ids('pp1, 'k, 'mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)
    }

    scenario("as a member, search partners that are not in a conversation with me by exact handle") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "pp1",
        selfId = id('mm1),
        conversationMembers = ids('a, 'k, 'mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)
    }

    scenario("as a member, search team members whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('mm3),
        conversationMembers = ids('mm2, 'pp1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('mm1, 'mm2)
    }

    scenario("as a member, search connected guests whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('mm1),
        conversationMembers = ids('mm2, 'pp1, 'e),
        connectedUsers = ids('d, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('d, 'e)
    }

    scenario("as a member, search not connected guests") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('mm1),
        conversationMembers = ids('mm2, 'pp1, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a partner, search team members that are not in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('pp1),
        conversationMembers = ids('pp1, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a partner, show no team members") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('pp3),
        conversationMembers = ids('pp3, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a partner, search team members that are in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Member",
        selfId = id('pp1),
        conversationMembers = ids('mm1, 'k)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('mm1)
    }

    scenario("as a partner, search partners that are in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Partner",
        selfId = id('pp2),
        conversationMembers = ids('pp1, 'pp2)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)
    }

    scenario("as a partner, search partners that are not in a conversation with me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Partner",
        selfId = id('pp1),
        conversationMembers = ids('mm1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as a partner, search connected guests whether they are in a conversation with me or not") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('pp1),
        conversationMembers = ids('mm2, 'pp1, 'e),
        connectedUsers = ids('d, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('e)
    }

    scenario("as a partner, search not connected guests") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "related",
        selfId = id('pp1),
        conversationMembers = ids('mm2, 'pp1, 'e)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids()
    }

    scenario("as an admin, search the partners that I invited") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Partner",
        selfId = id('aa1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('pp1)

    }

    scenario("as an admin, see the partners that I invited") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('aa1)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids(
        'aa2, 'mm1, 'mm2, 'mm3, // all non-partner team members
        'pp1 // partner that I invited
      )

    }

    scenario("as a partner, see the admin that invited me") {

      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "",
        selfId = id('pp2)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('aa2)

    }

    scenario("do not return wireless guests as results") {
      // GIVEN
      val preparedSearch = prepareTestSearch(
        query = "Wire",
        selfId = id('aa1),
        connectedUsers = ids('u, 'v)
      )

      // WHEN
      val res = result(preparedSearch.perform())

      // THEN
      res shouldBe ids('v) // the user 'u also has the username starting with Wire, but is wireless
    }
  }

  def getService(inTeam: Boolean, selfId: UserId) = {
    new UserSearchService(
      selfId,
      queryCacheStorage,
      if (inTeam) teamId else emptyTeamId,
      userService,
      usersStorage,
      teamsService,
      membersStorage,
      timeouts,
      sync,
      messagesStorage,
      convsStorage,
      convsUi,
      convs,
      userPrefs
    )
  }

}
