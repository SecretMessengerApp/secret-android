/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.listener

import android.content.Context
import android.view.{GestureDetector, MotionEvent, View}
import androidx.recyclerview.widget.RecyclerView
import com.jsy.common.views.pickuer.UserRowView
import com.waz.model.{ConversationData, UserId}
import com.waz.zclient.usersearch.views.ConversationRowView

object SearchResultTransferGroupOnItemTouchListener {

  trait Callback {
    def onUserClicked(userId: UserId, position: Int, anchorView: View): Unit

    def onConversationClicked(conversation: ConversationData, position: Int): Unit

    def onUserDoubleClicked(userId: UserId, position: Int, anchorView: View): Unit
  }

}

class SearchResultTransferGroupOnItemTouchListener(val context: Context, var callback: SearchResultTransferGroupOnItemTouchListener.Callback) extends RecyclerView.OnItemTouchListener {

  private var gestureDetector: GestureDetector = _
  private var position: Int = -1
  private var rowView: View = _

  gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
    override def onDoubleTap(e: MotionEvent): Boolean = {
      rowView match {
        case view: UserRowView =>
          view.getUser.foreach(uid => callback.onUserDoubleClicked(uid, position, rowView))
        case _ =>
      }
      true
    }

    override def onSingleTapConfirmed(e: MotionEvent): Boolean = {
      rowView match {
        case view: UserRowView =>
          //          view.onClicked()
          view.getUser.foreach(uid => callback.onUserClicked(uid, position, rowView))
        case view: ConversationRowView =>
          callback.onConversationClicked(view.getConversation, position)
        case _ =>
      }
      true
    }
  })

  def onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean = {
    rowView = rv.findChildViewUnder(e.getX, e.getY)
    position = rv.getChildAdapterPosition(rowView)
    if (rowView.isInstanceOf[RecyclerView]) {
      return false
    }
    position = rv.getChildAdapterPosition(rowView)
    if (rowView != null && callback != null) {
      gestureDetector.onTouchEvent(e)
    }
    false
  }

  def onTouchEvent(rv: RecyclerView, e: MotionEvent): Unit = {
  }

  def onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean): Unit = {
  }
}
