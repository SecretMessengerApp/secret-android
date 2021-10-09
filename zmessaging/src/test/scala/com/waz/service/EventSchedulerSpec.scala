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

import java.util.concurrent.atomic.AtomicReference

import com.waz.model._
import com.waz.service.EventScheduler.Stage
import com.waz.threading.Threading
import com.waz.utils.compareAndSet
import org.scalatest.{FeatureSpec, Matchers, OptionValues, RobolectricTests}
import org.threeten.bp.Instant
import com.waz.testutils.Implicits._
import com.waz.testutils.Matchers._

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random.nextInt

class EventSchedulerSpec extends FeatureSpec with Matchers with OptionValues with RobolectricTests {

  feature("Creating schedules") {
    scenario("Atomic stage")(withFixture { env => import env._
      val events = E('ab, 'a, 'b, 'ab)

      events scheduledBy _A shouldEqual "A013"
      events scheduledBy _C shouldEqual "-"
    })

    scenario("Sequential stage")(withFixture { env => import env._
      val events = E('ab, 'a, 'b, 'ab)

      events scheduledBy seq(_A, _C, _B) shouldEqual "seq(A013,-,B023)"
      events scheduledBy seq(_C, _D)     shouldEqual "-"
      events scheduledBy seq()           shouldEqual "-"
    })

    scenario("Parallel stage")(withFixture { env => import env._
      val events = E('ab, 'a, 'b, 'ab)

      events scheduledBy par(_A, _C, _B) shouldEqual "par(A013,-,B023)"
      events scheduledBy par(_C, _D)     shouldEqual "-"
      events scheduledBy par()           shouldEqual "-"
    })

    scenario("Interleaved stage")(withFixture { env => import env._
      info(
        """   |- a sequence of events (0 1 2 3 4 5 6 7 8 9) all in the same conversation
          |    - processing stages A, B, C, D
          |    - eligibility:
          |      A: 2
          |      B: 0, 2, 3, 8
          |      C: 1, 2, 3, 4, 5, 6, 7
          |      D: 2, 7, 8, 9
          |
          |    should result in this schedule:
          |
          |      time ->
          |      A: - - 2 - - - - - - - - - - - - -
          |      B: 0 - - 2 - - 3 - - - - - - 8 - -
          |      C: - 1 - - 2 - - 3 4 5 6 7 - - - -
          |      D: - - - - - 2 - - - - - - 7 - 8 9
        """.stripMargin)

      //              0   1    2     3    4   5   6   7    8    9
      val events = E('b, 'c, 'abcd, 'bc, 'c, 'c, 'c, 'cd, 'bd, 'd)

      events scheduledBy intr(_A, _B, _C, _D) shouldEqual "seq(B0,C1,A2,B2,C2,D2,B3,C34567,D7,B8,D89)"
      events scheduledBy intr(_E, _F)         shouldEqual "-"
      events scheduledBy intr()               shouldEqual "-"
    })

    scenario("Sequential, interleaved and parallel combined")(withFixture { env => import env._
      //               0      1     2    3    4     5     6
      val events1 = E('ad, 'abcd, 'bcd, 'bd, 'cd, 'bcd, 'acd)

      events1 scheduledBy seq(intr(_A, par(_B, _C)), _D) shouldEqual "seq(seq(A01,par(B1235,C1245),A6,par(-,C6)),D0123456)"
      events1 scheduledBy seq(intr(_E, par(_B, _C)), _H) shouldEqual "seq(seq(par(B1235,C12456)),-)"
      events1 scheduledBy seq(intr(_E, par(_F, _G)), _D) shouldEqual "seq(-,D0123456)"
      events1 scheduledBy seq(intr(_E, par(_F, _G)), _H) shouldEqual "-"

      //                0     1    2      3     4     5     6     7    8
      val events2 = E('adxy, 'x, 'abcd, 'bcd, 'bdy, 'cdz, 'bcd, 'acd, 'y)

      events2 scheduledBy seq(_X, intr(_A, _Y, par(_B, _C, _Z)), _D) shouldEqual "seq(X01,seq(A0,Y0,A2,par(B23,C23,-),Y4,par(B46,C56,Z5),A7,par(-,C7,-),Y8),D0234567)"
    })

    scenario("Stack safety")(withFixture { env => import env._
      val events = E(1 to 42000 map (_ => randomEvent):_*)
      val scheduler = new EventScheduler(seq(_A, intr(_B, _C, par(_D, intr(_E, _F)), _G), _H))

      val n = numberOfScheduledEvents(scheduler.createSchedule(events))
      info(s"number of scheduled events: $n")
      n should be > 0
    })
  }

  feature("Executing schedules") {
    scenario("Order of execution")(withFixture { env => import env._
      randomDelay = true

      42.times {
        E('ad, 'abcd, 'bcd, 'bd, 'cd, 'bcd, 'acd) scheduledAndExecutedBy seq(intr(_A, par(_B, _C)), _D) should (
          equal("A01,B1235,C1245,A6,C6,D0123456") or
          equal("A01,C1245,B1235,A6,C6,D0123456"))
      }
    })

    scenario("Failing stages")(withFixture { env => import env._
      randomDelay = true

      42.times {
        E('adxy, 'x, 'abcd, 'bcd, 'bdy, 'cdz, 'bcd, 'acd, 'y) scheduledAndExecutedBy seq(_X, intr(_A, _Y, par(_B, _C, _Z)), _D) should (
          equal("A0,A2,B23,C23,B46,C56,A7,C7,D0234567") or
          equal("A0,A2,C23,B23,B46,C56,A7,C7,D0234567") or
          equal("A0,A2,B23,C23,C56,B46,A7,C7,D0234567") or
          equal("A0,A2,C23,B23,C56,B46,A7,C7,D0234567"))
      }
    })

    scenario("Stack safety")(withFixture { env => import env._
      val schedule = new EventScheduler(seq(_A, intr(_B, _C, par(_D, intr(_E, _F)), _G), _H)).createSchedule(E(1 to 42000 map (_ => randomEvent):_*))
      EventScheduler.executeSchedule(conv, schedule).await()

      executed.get.flatMap(_._2) should have size numberOfScheduledEvents(schedule)
    })
  }

  feature("Defining event processing stages") {
    lazy val e1 = RenameConversationEvent(RConvId("R"), RemoteInstant(Instant.now()), UserId("u1"), Name("meep 1"))
    lazy val e2 = UnknownPropertyEvent("e2", "u1")
    lazy val e3 = RenameConversationEvent(RConvId("R"), RemoteInstant(Instant.now()), UserId("u2"), Name("meep 2"))
    lazy val e4 = UnknownPropertyEvent("e4", "u2")

    scenario("Eligibility check")(withFixture { env => import env._
      lazy val stage = Stage[UnknownPropertyEvent](append, _.value == "u1")

      stage.isEligible(e1) shouldBe false
      stage.isEligible(e2) shouldBe true
      stage.isEligible(e3) shouldBe false
      stage.isEligible(e4) shouldBe false
    })

    scenario("Processing only eligible events")(withFixture { env => import env._
      lazy val stage = Stage[UnknownPropertyEvent](append, _.value == "u1")
      stage(conv, Vector(e1,e2,e3,e4)).await()

      processed.get shouldEqual Seq(e2)
    })
  }

  def withFixture(f: Fixture => Unit) = f(new Fixture)

  class Fixture {
    import EventScheduler._
    import Threading.Implicits.Background

    val conv = RConvId("R")

    val Seq(_A, _B, _C, _D, _E, _F, _G, _H) = "ABCDEFGH".sliding(1).map(s => new TestStage(Symbol(s))).toSeq
    val (_X, _Y, _Z) = (new FaultyTestStage('X), new FaultyTestStage('Y), new FaultyTestStage('Z))

    val executed = new AtomicReference(Vector.empty[(TestStage, Vector[Event])])
    val processed = new AtomicReference(Vector.empty[Event])
    @volatile var randomDelay = false

    class TestStage(name: Symbol) extends Stage.Atomic {
      def isEligible(e: Event) = e match {
        case e: UnknownPropertyEvent if e.value.contains(name.name.toLowerCase) => true
        case x => false
      }

      def apply(conv: RConvId, es: Traversable[Event]) = Future[Unit] {
        if (randomDelay) Thread.sleep(0, nextInt(100))
        compareAndSet(executed)(_ :+ (this, es.to[Vector]))
      }
      override def toString = name.name
    }

    val append = (conv: RConvId, es: Traversable[Event]) => Future[Unit](compareAndSet(processed)(_ ++ es))

    class FaultyTestStage(name: Symbol) extends TestStage(name) {
      override def apply(conv: RConvId, ex: Traversable[Event]): Future[Unit] = Future.failed(new IllegalStateException("shenanigans"))
    }

    def E(es: Symbol*): Vector[Event] = es.zipWithIndex.map {
      case (user, uid) => new UnknownPropertyEvent(uid.toString, user.name) {
        override def toString = key
      }
    }(breakOut)

    implicit class RichEvents(events: Vector[Event]) {
      def scheduledBy(stage: Stage) = stringify(new EventScheduler(stage).createSchedule(events))
      def scheduledAndExecutedBy(stage: Stage) = {
        executed.set(Vector.empty)
        executeSchedule(conv, new EventScheduler(stage).createSchedule(events)).await()
        stringify(executed.get)
      }
    }

    def stringify(es: Vector[(Stage, Vector[Event])]): String = es.map { case (stage, events) => s"$stage${events.mkString}" } .mkString(",")

    def stringify(schedule: Schedule): String = schedule match {
      case NOP => "-"
      case Leaf(stage, events) => s"$stage${events.mkString}"
      case Branch(strat, scheds) => s"${strat.toString.take(3).toLowerCase}(${scheds.map(stringify).mkString(",")})"
    }

    def seq(stages: Stage*) = Stage(Sequential)(stages:_*)
    def par(stages: Stage*) = Stage(Parallel)(stages:_*)
    def intr(stages: Stage*) = Stage(Interleaved)(stages:_*)

    def numberOfScheduledEvents(schedule: Schedule): Int = {
      @tailrec def dfs(s: Stream[Schedule], accu: Int = 0): Int = s match {
        case Stream.Empty => accu
        case h #:: t => h match {
          case EventScheduler.Leaf(_, events) => dfs(t, accu + events.size)
          case EventScheduler.Branch(_, schedules) => dfs(schedules #::: t, accu)
        }
      }
      dfs(Stream(schedule))
    }

    def randomEvent = possibleCombinations(nextInt(possibleCombinations.size))
  }

  implicit lazy val patienceConfig = patience(5.seconds, 100.nanos)
  lazy val possibleCombinations = Iterator.range(1, 5).flatMap("abcdefgh".combinations).map(Symbol(_)).to[Vector]
}
