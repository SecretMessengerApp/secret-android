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
package com.waz.sync.queue

import java.io.PrintWriter

import com.waz.api.NetworkMode
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.sync.SyncJob
import com.waz.model.{ConvId, SyncId, UserId}
import com.waz.service.AccountsService.{Active, LoggedOut}
import com.waz.service.tracking.TrackingService
import com.waz.service.{AccountContext, AccountsService, NetworkModeService}
import com.waz.sync.{SyncHandler, SyncRequestServiceImpl, SyncResult}
import com.waz.threading.CancellableFuture.CancelException
import com.waz.threading.{CancellableFuture, SerialDispatchQueue}
import com.waz.utils.events.Signal
import com.waz.utils.returning

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.Try


trait SyncScheduler {

  def await(id: SyncId): Future[SyncResult]
  def await(ids: Set[SyncId]): Future[Set[SyncResult]]

  def withConv[A](job: SyncJob, conv: ConvId)(f: ConvLock => Future[A]): Future[A]
  def awaitPreconditions[A](job: SyncJob)(f: => Future[A]): Future[A]

  def report(pw: PrintWriter): Future[Unit]
  def reportString: Future[String]
}

class SyncSchedulerImpl(accountId:   UserId,
                        val content: SyncContentUpdater,
                        val network: NetworkModeService,
                        service:     SyncRequestServiceImpl,
                        handler:     => SyncHandler,
                        accounts:    AccountsService,
                        tracking:    TrackingService)
                       (implicit accountContext: AccountContext) extends SyncScheduler with DerivedLogTag {

  private implicit val dispatcher = new SerialDispatchQueue(name = "SyncSchedulerQueue")

  private val queue                 = new SyncSerializer
  private[sync] val executor        = new SyncExecutor(accountId, this, content, network, handler, tracking)
  private[sync] val executions      = new mutable.HashMap[SyncId, Future[SyncResult]]()
  private[sync] val executionsCount = Signal(0)

  private val waitEntries  = new mutable.HashMap[SyncId, WaitEntry]
  private val waiting      = Signal(Map.empty[SyncId, Long])
  private val runningCount = Signal(executionsCount, waiting.map(_.size)) map { case (r, w) => r - w }

  content.syncStorage { storage =>
    storage.getJobs.toSeq.sortBy(_.timestamp) foreach execute
    storage.onAdded.on(dispatcher) { execute }
    storage.onUpdated
      .filter { case (prev, job) => prev.priority != job.priority || prev.startTime != job.startTime }
      .on(dispatcher) {
        case (prev, job) =>
          waiting.mutate { jobs => if (jobs.contains(job.id)) jobs.updated(job.id, getStartTime(job)) else jobs }
          waitEntries.get(job.id) foreach (_.onUpdated(job))
      }
  }

  accounts.accountState(accountId).on(dispatcher) {
    case _: Active => waitEntries.foreach(_._2.onRestart())
    case _ =>
  }

  network.networkMode.on(dispatcher) {
    case NetworkMode.OFFLINE => // do nothing
    case _ => waitEntries.foreach(_._2.onOnline())
  }

  override def reportString = Future {
    s"SyncScheduler: executors: ${executions.size}, count: ${executionsCount.currentValue}, running: ${runningCount.currentValue}, waiting: ${waiting.currentValue}"
  }

  override def report(pw: PrintWriter) = reportString.map(pw.println)

  private def execute(job: SyncJob): Unit = {
    verbose(l"execute($job)")
    val future = executor(job)
    executions += job.id -> future
    executionsCount.mutate(_ + 1)
    future onComplete { res =>
      executions -= job.id
      executionsCount.mutate(_ - 1)
      verbose(l"job completed: $job, res: $res")
      res.failed.foreach(t => t.printStackTrace())
    }
  }

  override def await(id: SyncId) = Future { executions.getOrElse(id, Future.successful(SyncResult.Success)) } flatMap identity

  override def await(ids: Set[SyncId]) = Future.sequence(ids.map(await))

  private def countWaiting[A](id: SyncId, startTime: Long)(future: Future[A]) = {
    waiting.mutate(_ + (id -> startTime))
    future.onComplete(_ => waiting.mutate(_ - id))
    future
  }

  override def withConv[A](job: SyncJob, conv: ConvId)(f: ConvLock => Future[A]) = {
    verbose(l"withConv($job, $conv)")
    countWaiting(job.id, getStartTime(job)) { queue.acquire(conv) } flatMap { lock =>
      Try(f(lock)).recover { case t => Future.failed[A](t) }.get.andThen { case _ => lock.release() }
    }
  }

  override def awaitPreconditions[A](job: SyncJob)(f: => Future[A]) = {
    verbose(l"awaitPreconditions($job)")

    val entry = new WaitEntry(job)
    waitEntries.put(job.id, entry)

    val jobReady = for {
      _ <- accounts.accountState(accountId).filter(_ != LoggedOut).head
      _ <- entry.future
    } yield {}

    jobReady.onComplete(_ => waitEntries -= job.id)

    countWaiting(job.id, getStartTime(job))(jobReady) flatMap { _ =>
      returning(f)(_.onComplete(_ => queue.release()))
    }
  }

  private def getStartTime(job: SyncJob): Long =
    if (job.offline && network.isOnlineMode) 0  // start right away if request last failed due to possible network errors
    else job.startTime


  class WaitEntry(private var job: SyncJob) extends DerivedLogTag { self =>
    private val promise = Promise[Unit]()

    private var delayFuture: CancellableFuture[Unit] = setup(job)

    private def setup(job: SyncJob) = {
      val delay = CancellableFuture.delay(math.max(0, getStartTime(job) - System.currentTimeMillis()).millis)
      for {
        _ <- delay.recover { case _: CancelException => () } .future
        _ <- Future.traverse(job.dependsOn)(await)
        _ <- queue.acquire(job.priority)
      } yield {
        if (job == self.job) promise.trySuccess(())
        else queue.release() // this wait entry was already updated, releasing acquired lock
      }
      delay
    }

    def isCompleted = promise.isCompleted
    def onRestart() = delayFuture.cancel()
    def onOnline() = if (job.offline) delayFuture.cancel()
    def onUpdated(updated: SyncJob): Unit = {
      job = updated
      verbose(l"job updated: $job, should update delay and/or priority")
      delayFuture = setup(updated)
    }

    def future = promise.future
  }
}

object SyncScheduler {
  val AlarmRequestCode = 19523
}
