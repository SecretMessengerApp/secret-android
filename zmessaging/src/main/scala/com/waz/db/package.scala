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
package com.waz

import com.waz.utils._
import com.waz.log.LogSE._
import com.waz.utils.wrappers._
import com.waz.utils.wrappers.DB

import scala.language.implicitConversions
import scala.util.Try

package object db {
  implicit def iterate[A](c: DBCursor)(implicit read: Reader[A]): Iterator[A] = new CursorIterator[A](c)

  def iteratingWithReader[A](reader: Reader[A])(c: => DBCursor): Managed[Iterator[A]] = Managed(c).map(new CursorIterator(_)(reader))

  class CursorIterator[A](c: DBCursor)(implicit read: Reader[A]) extends Iterator[A] {
    c.moveToFirst()
    override def next(): A = returning(read(c)){ _ => c.moveToNext() }
    override def hasNext: Boolean = !c.isClosed && !c.isAfterLast
  }

  object CursorIterator {
    def list[A](c: DBCursor, close: Boolean = true, filter: A => Boolean = { (_: A) => true })(implicit reader: Reader[A]) =
      try { new CursorIterator(c)(reader).filter(filter).toVector } finally { if (close) c.close() }
  }

  class ReverseCursorIterator[A](c: DBCursor)(implicit read: Reader[A]) extends Iterator[A] {
    c.moveToLast()
    override def next(): A = returning(read(c)){ _ => c.moveToPrevious() }
    override def hasNext: Boolean = !c.isClosed && !c.isBeforeFirst
  }

  def bind[A: DbTranslator](value: A, index: Int, stmt: DBProgram) = implicitly[DbTranslator[A]].bind(value, index, stmt)

  def load[A: DbTranslator](c: DBCursor, index: Int) = implicitly[DbTranslator[A]].load(c, index)

  def forEachRow(cursor: DBCursor)(f: DBCursor => Unit): Unit = try {
    cursor.moveToFirst()
    while(! cursor.isAfterLast) { f(cursor); cursor.moveToNext() }
  } finally cursor.close()

  class Transaction(db: DB) {
    def flush() = {
      db.setTransactionSuccessful()
      db.endTransaction()
      db.beginTransactionNonExclusive()
    }
  }

  def inTransaction[A](body: => A)(implicit db: DB): A =
    inTransaction(_ => body)

  def inTransaction[A](body: Transaction => A)(implicit db: DB): A = {
    val tr = new Transaction(db)
    if (db.inTransaction) body(tr)
    else {
      db.beginTransactionNonExclusive()
      try returning(body(tr)) { _ => db.setTransactionSuccessful() }
      finally db.endTransaction()
    }
  }

  private lazy val readTransactions = ReadTransactionSupport.chooseImplementation()

  def inReadTransaction[A](body: => A)(implicit db: DB): A =
    if (db.inTransaction) body
    else {
      readTransactions.beginReadTransaction(db)
      try returning(body) { _ => db.setTransactionSuccessful() }
      finally db.endTransaction()
    }


  def withStatement[A](sql: String)(body: DBStatement => A)(implicit db: DB): A = {
    val stmt = db.compileStatement(sql)
    try {
      body(stmt)
    } finally
      stmt.close()
  }
}

package db {
  import com.waz.log.BasicLogging.LogTag.DerivedLogTag

/** See https://www.sqlite.org/isolation.html - "Isolation And Concurrency", par. 4 and following.
    * TL;DR: the Android SQLite classes fail to support WAL mode correctly, we are forced to hack our way into them
    */
  trait ReadTransactionSupport {
    def beginReadTransaction(db: DB): Unit
  }

  object ReadTransactionSupport {
    def chooseImplementation(): ReadTransactionSupport = Try(DeferredModeReadTransactionSupport.create).getOrElse(FallbackReadTransactionSupport.create)
  }

  object DeferredModeReadTransactionSupport extends DerivedLogTag {
    def create: ReadTransactionSupport = new ReadTransactionSupport {
      verbose(l"using deferred mode read transactions")

      override def beginReadTransaction(db: DB): Unit = try reflectiveBegin(db) catch { case _: Exception => db.beginTransactionNonExclusive() }

      private def reflectiveBegin(db: DB): Unit = {
        db.acquireReference()
        try {
          db.getThreadSession.beginTransaction()
        }
        finally db.releaseReference()
      }
    }
  }

  object FallbackReadTransactionSupport extends DerivedLogTag {
    def create: ReadTransactionSupport = new ReadTransactionSupport {
      verbose(l"using fallback support for read transactions")
      override def beginReadTransaction(db: DB): Unit = db.beginTransactionNonExclusive()
    }
  }
}
