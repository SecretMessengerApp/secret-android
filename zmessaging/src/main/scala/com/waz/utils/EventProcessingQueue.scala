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
package com.waz.utils

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import com.waz.log.BasicLogging.LogTag
import com.waz.log.LogSE._
import com.waz.model.Event
import com.waz.threading.{CancellableFuture, SerialDispatchQueue, Threading}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait EventProcessingQueue[A <: Event] {

  protected implicit val evClassTag: ClassTag[A]
  protected val selector: A => Boolean = { _ => true }
  
  def enqueue(event: A): Future[Any]

  def enqueue(events: Seq[A]): Future[Any]

  def enqueueEvent(event: Event): Future[Any] = event match {
    case ev: A if selector(ev) => enqueue(ev)
    case _ => Future.successful(()) // ignore
  }

  def enqueueEvents(events: Seq[Event]): Future[Any] = enqueue(events collect { case ev: A if selector(ev) => ev })
}

object EventProcessingQueue {

  def apply[A <: Event : ClassTag, B](eventProcessor: A => Future[B]) = {
    val classTag = implicitly[ClassTag[A]]

    new EventProcessingQueue[A] {
      import Threading.Implicits.Background
      override protected implicit val evClassTag = classTag
      override def enqueue(event: A): Future[Any] = eventProcessor(event)
      override def enqueue(events: Seq[A]): Future[Any] = Future.traverse(events)(eventProcessor)
    }
  }
}

class SerialEventProcessingQueue[A <: Event](processor: Seq[A] => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A]) extends SerialProcessingQueue[A](processor, name) with EventProcessingQueue[A]

class GroupedEventProcessingQueue[A <: Event, Key](groupBy: A => Key, processor: (Key, Seq[A]) => Future[Any], name: String = "")(implicit val evClassTag: ClassTag[A]) extends EventProcessingQueue[A] {

  private implicit val dispatcher = new SerialDispatchQueue(name = s"GroupedEventProcessingQueue[${evClassTag.runtimeClass.getSimpleName}]")

  private val queues = new mutable.HashMap[Key, SerialProcessingQueue[A]]

  private def queue(key: Key) = queues.getOrElseUpdate(key, new SerialProcessingQueue[A](processor(key, _), s"${name}_$key"))

  override def enqueue(event: A): Future[Any] = Future(queue(groupBy(event))).flatMap(_.enqueue(event))

  override def enqueue(events: Seq[A]): Future[Vector[Any]] =
    Future.traverse(events.groupBy(groupBy).toVector) { case (key, es) => Future(queue(key)).flatMap(_.enqueue(es)) }

  def post[T](k: Key)(task: => Future[T]) = Future {
    queue(k).post(task)
  } flatMap identity
}

class SerialProcessingQueue[A](processor: Seq[A] => Future[Any], name: String = "") {
  private implicit val logTag: LogTag = LogTag(name)

  private val queue = new ConcurrentLinkedQueue[A]()

  def enqueue(event: A): Future[Any] = {
    queue.offer(event)
    processQueue()
  }

  def !(event: A) = enqueue(event)

  def enqueue(events: Seq[A]): Future[Any] = if (events.nonEmpty) {
    events.foreach(queue.offer)
    processQueue()
  } else
    Future.successful(())

  protected def processQueue(): Future[Any] = post(processQueueNow())

  protected def processQueueNow(): Future[Any] = {
    val events = Iterator.continually(queue.poll()).takeWhile(_ != null).toVector
    verbose(l"processQueueNow, events: ???")
    if (events.nonEmpty) processor(events).recoverWithLog()
    else Future.successful(())
  }

  // post some task on this queue, effectively blocking all other processing while this task executes
  def post[T](f: => Future[T]): Future[T] = Serialized.future(this)(f)

  /* just for tests! */
  def clear(): Unit = queue.clear()
}

class ThrottledProcessingQueue[A](delay: FiniteDuration, processor: Seq[A] => Future[Any], name: String = "") extends SerialProcessingQueue[A](processor, name)  {
  private implicit val dispatcher = new SerialDispatchQueue(name = if (name.isEmpty) "ThrottledProcessingQueue_" + hashCode() else name)
  private val waiting = new AtomicBoolean(false)
  @volatile private var waitFuture: CancellableFuture[Any] = CancellableFuture.successful(())
  private var lastDispatched = 0L
  private implicit val logTag: LogTag = LogTag(name)

  override protected def processQueue(): Future[Any] =
    if (waiting.compareAndSet(false, true)) {
      post {
        val d = math.max(0, lastDispatched - System.currentTimeMillis() + delay.toMillis)
        verbose(l"processQueue, delaying: $d millis")
        waitFuture = CancellableFuture.delay(d.millis)
        if (!waiting.get()) waitFuture.cancel()(logTag) // to avoid race conditions with `flush`
        waitFuture.future.flatMap { _ =>
          CancellableFuture.lift(processQueueNow())
        } .recover {
          case e: Throwable => waiting.set(false)
        }
      }
    } else waitFuture.future


  override protected def processQueueNow(): Future[Any] = {
    waiting.set(false)
    lastDispatched = System.currentTimeMillis()
    super.processQueueNow()
  }

  def flush() = {
    waiting.set(false)
    waitFuture.cancel()(logTag)
    post {
      processQueueNow()
    }
  }
}
