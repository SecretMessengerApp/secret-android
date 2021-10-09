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
package com.waz.zclient.messages.parts.footer

import android.content.Context
import android.util.AttributeSet
import android.widget.{RelativeLayout, TextView}
import com.waz.model.{ConvId, UserId}
import com.waz.utils.events.{EventStream, Signal}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.ViewHelper
import com.waz.zclient.R
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, OrientationHelper, RecyclerView}
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.ui.text.GlyphTextView

class LikeDetailsView(context: Context, attrs: AttributeSet, style: Int) extends RelativeLayout(context, attrs, style) with ViewHelper {

  import LikeDetailsView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.message_footer_like_details)

  val onClickedLikedUser = EventStream[UserId]()

  private val description: TextView = findById(R.id.like__description)

  /**  width = （16 + 4）dp * [[MAX_COUNT]]  */
  private val recyclerHeaders: RecyclerView = findById(R.id.recyclerHeaders)
  private val layoutMgr = new LinearLayoutManager(getContext, OrientationHelper.HORIZONTAL, false)
  val divider = getResources.getDimension(R.dimen.wire__padding__2).toInt
  recyclerHeaders.setLayoutManager(layoutMgr)

  private var likeAdp: LikeAdp = _

  description.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = {}
  })

  def getDisplayNameString(ids: Seq[UserId], convId: ConvId): Signal[String] = {
    if(ids.size > MAX_COUNT)
      Signal.const(getQuantityString(R.plurals.message_footer__number_of_likes, ids.size, Integer.valueOf(ids.size)))
    else
      Signal.sequence(ids map { itemId =>
        controller.signals.displayNameStringIncludingSelf(itemId, convId)
      }: _*).map { names =>
        if(names.isEmpty)
          getString(R.string.message_footer__tap_to_like)
        else
          names.mkString(", ")
      }
  }

  private var controller: FooterViewController = _

  def init(controller: FooterViewController): Unit = {
    this.controller = controller
    val likedBy = controller.messageAndLikes.map(_.likes)
    val likeAdp: LikeAdp = new LikeAdp(likedBy)
    recyclerHeaders.setAdapter(likeAdp)

    (for {
      resultLikedBy <- likedBy
      resultConvId <- controller.messageAndLikes.map(_.message.convId)
      resultContent <- getDisplayNameString(resultLikedBy, resultConvId)
    } yield resultContent).onUi(description.setText)
  }

  class LikeAdp(userIdsSignal: Signal[IndexedSeq[UserId]]) extends RecyclerView.Adapter[RecyclerView.ViewHolder] {

    private var ids: Option[Seq[UserId]] = Option.empty

    private val VIEW_TYPE_NORMAL = 1;
    private val VIEW_TYPE_MORE = 2;

    override def onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = {
      viewType match {
        case VIEW_TYPE_NORMAL =>
          new LikeVh(LayoutInflater.from(parent.getContext).inflate(R.layout.layout_chathead_like, parent, false))
        case VIEW_TYPE_MORE =>
          new LikeMoreVh(LayoutInflater.from(parent.getContext).inflate(R.layout.layout_chathead_like_more, parent, false))
        case _ =>
          new LikeVh(LayoutInflater.from(parent.getContext).inflate(R.layout.layout_chathead_like, parent, false))
      }

    }

    override def getItemViewType(position: Int): Int = {
      if (ids.fold(0)(_.size) > MAX_COUNT) {
        if (position == MAX_COUNT - 1) {
          VIEW_TYPE_MORE
        } else {
          VIEW_TYPE_NORMAL
        }
      } else {
        VIEW_TYPE_NORMAL
      }

    }

    userIdsSignal.onUi { ids =>
      this.ids = Some(ids)
      notifyDataSetChanged()
    }

    override def onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int): Unit = {
      ids.foreach { ids =>
        getItemViewType(position) match {
          case VIEW_TYPE_NORMAL =>
            if (holder.isInstanceOf[LikeVh]) {
              holder.asInstanceOf[LikeVh].setData(ids.apply(position))
            }
          case VIEW_TYPE_MORE =>
          case _ =>
            if (holder.isInstanceOf[LikeVh]) {
              holder.asInstanceOf[LikeVh].setData(ids.apply(position))
            }
        }
        holder.itemView.setOnClickListener(new OnClickListener {
          override def onClick(v: View): Unit = {
            onClickedLikedUser ! ids.apply(position)
          }
        })
      }
    }

    override def getItemCount: Int = {
      ids.fold(0)(_.take(MAX_COUNT).size)
    }
  }

  class LikeVh(view: View) extends RecyclerView.ViewHolder(view) {

    private val chatheadViewLiked: ChatHeadViewNew = view.findViewById(R.id.chatheadViewLiked)

    def setData(userId: UserId): Unit = {
      chatheadViewLiked.clearUser()
      chatheadViewLiked.loadUser(userId)
    }
  }

  class LikeMoreVh(view: View) extends RecyclerView.ViewHolder(view) {

    private val glyphTextViewLikeMored: GlyphTextView = view.findViewById(R.id.glyphTextViewLikeMored)

  }

}

object LikeDetailsView {
  val MAX_COUNT = 3
}



