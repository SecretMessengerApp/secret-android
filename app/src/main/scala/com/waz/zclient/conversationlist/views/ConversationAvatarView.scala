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
package com.waz.zclient.conversationlist.views

import java.math.BigInteger
import java.nio.{ByteBuffer, ByteOrder}
import java.util.UUID

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import com.jsy.res.utils.ViewUtils
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{ConvId, TeamId, UserData, UserId}
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{R, ViewHelper}

import scala.collection.mutable.ArrayBuffer

class ConversationAvatarView (context: Context, attrs: AttributeSet, style: Int) extends FrameLayout(context, attrs, style) with ViewHelper {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.view_conversation_avatar)
  setLayoutParams(new LayoutParams(getDimenPx(R.dimen.conversation_list__row__avatar_size), getDimenPx(R.dimen.conversation_list__row__avatar_size)))

  private val groupBackgroundDrawable = getDrawable(R.drawable.conversation_group_avatar_background)

  private val avatarStartTop = ViewUtils.getView(this, R.id.conversation_avatar_start_top).asInstanceOf[ChatHeadViewNew]
  private val avatarEndTop = ViewUtils.getView(this, R.id.conversation_avatar_end_top).asInstanceOf[ChatHeadViewNew]
  private val avatarStartBottom = ViewUtils.getView(this, R.id.conversation_avatar_start_bottom).asInstanceOf[ChatHeadViewNew]
  private val avatarEndBottom = ViewUtils.getView(this, R.id.conversation_avatar_end_bottom).asInstanceOf[ChatHeadViewNew]

  val avatarSingle = ViewUtils.getView(this, R.id.avatar_single).asInstanceOf[ChatHeadViewNew]
  private val avatarGroup = ViewUtils.getView(this, R.id.avatar_group).asInstanceOf[View]
  private val avatarGroupSingle = ViewUtils.getView(this, R.id.conversation_avatar_single_group).asInstanceOf[ChatHeadViewNew]

//  private val imageSources = Seq.fill(4)(Signal[ImageSource]())

  private val chatheads = Seq(avatarStartTop, avatarEndTop, avatarStartBottom, avatarEndBottom)

  def loadMembers(members: Seq[UserId], convId: ConvId, conversationType: ConversationType): Unit = {
    conversationType match {
      case ConversationType.Group | ConversationType.ThousandsGroup if members.size == 1 =>
        chatheads.foreach(_.clearUser())
        avatarGroupSingle.loadUser(members.head)
        showGroupSingle()
      case ConversationType.Group | ConversationType.ThousandsGroup =>
        val shuffledIds = ConversationAvatarView.shuffle(members.sortBy(_.str), convId)
        avatarGroupSingle.clearUser()
        chatheads.map(Some(_)).zipAll(shuffledIds.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(uid)) =>
            view.loadUser(uid)
          case (Some(view), None) =>
            view.clearUser()
          case _ =>
        }
        showGrid()
      case ConversationType.OneToOne | ConversationType.WaitForConnection if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearUser())(avatarSingle.loadUser)
        showSingle()
      case _ =>
        clearImages()
    }
  }

  def setMembers(members: Seq[UserData], convId: ConvId, conversationType: ConversationType, selfTeam: Option[TeamId] = None): Unit = {
    conversationType match {
      case ConversationType.Group | ConversationType.ThousandsGroup if members.size == 1 =>
        chatheads.foreach(_.clearImage())
        avatarGroupSingle.setUserData(members.head, belongsToSelfTeam = members.head.teamId.exists(selfTeam.contains))
        showGroupSingle()
      case ConversationType.Group | ConversationType.ThousandsGroup =>
        val shuffledIds = ConversationAvatarView.shuffle(members.sortBy(_.id.str), convId)
        avatarGroupSingle.clearImage()
        chatheads.map(Some(_)).zipAll(shuffledIds.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(ud)) =>
            view.setUserData(ud, belongsToSelfTeam = ud.teamId.exists(selfTeam.contains))
          case (Some(view), None) =>
            view.clearImage()
          case _ =>
        }
        showGrid()
      case ConversationType.OneToOne | ConversationType.WaitForConnection if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearImage())(ud => avatarSingle.setUserData(ud, belongsToSelfTeam = ud.teamId.exists(selfTeam.contains)))
        showSingle()
      case _ =>
        clearImages()
    }
  }

  private def hideAll(): Unit = {
    avatarGroup.setVisibility(View.GONE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(null)
  }

  private def showGrid(): Unit = {
    avatarGroup.setVisibility(View.VISIBLE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(groupBackgroundDrawable)
  }

  private def showSingle(): Unit = {
    avatarGroup.setVisibility(View.GONE)
    avatarSingle.setVisibility(View.VISIBLE)
    avatarGroupSingle.setVisibility(View.GONE)
    setBackground(null)
  }

  private def showGroupSingle(): Unit = {
    avatarGroup.setVisibility(View.VISIBLE)
    avatarSingle.setVisibility(View.GONE)
    avatarGroupSingle.setVisibility(View.VISIBLE)
    setBackground(groupBackgroundDrawable)
  }

  //def setConversationType(conversationType: ConversationType): Unit ={
  //  conversationType match {
  //    case ConversationType.Group | ConversationType.ThousandsGroup => showGrid()
  //    case ConversationType.OneToOne | ConversationType.WaitForConnection => showSingle()
  //    case _ => hideAll()
  //  }
  //}
  //
  //def clearImages(): Unit = {
  //  chatheads.foreach(_.clearImage())
  //  avatarSingle.clearImage()
  //  avatarGroupSingle.clearImage()
  //}

  def setMembers(members: Seq[UserId], convId: ConvId, conversationType: ConversationType): Unit = {
    conversationType match {
      case ConversationType.Group if members.size == 1 =>
        chatheads.foreach(_.clearUser())
        avatarGroupSingle.loadUser(members.head)
      case ConversationType.ThousandsGroup if members.size == 1 =>
        chatheads.foreach(_.clearUser())
        avatarGroupSingle.loadUser(members.head)
      case ConversationType.Group =>
        val shuffledIds = ConversationAvatarView.shuffle(members.sortBy(_.str), convId)
        avatarGroupSingle.clearUser()
        chatheads.map(Some(_)).zipAll(shuffledIds.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(uid)) =>
            view.loadUser(uid)
          case (Some(view), None) =>
            view.clearUser()
          case _ =>
        }
      case ConversationType.ThousandsGroup =>
        val shuffledIds = ConversationAvatarView.shuffle(members.sortBy(_.str), convId)
        avatarGroupSingle.clearUser()
        chatheads.map(Some(_)).zipAll(shuffledIds.take(4).map(Some(_)), None, None).foreach{
          case (Some(view), Some(uid)) =>
            view.loadUser(uid)
          case (Some(view), None) =>
            view.clearUser()
          case _ =>
        }
      case ConversationType.OneToOne | ConversationType.WaitForConnection if members.nonEmpty =>
        members.headOption.fold(avatarSingle.clearUser())(avatarSingle.loadUser)
      case _ =>
        clearImages()
//        imageSources.foreach(_ ! NoImage())
    }
  }

  def setConversationType(conversationType: ConversationType): Unit ={
    conversationType match {
      case ConversationType.Group =>
        avatarGroup.setVisibility(View.VISIBLE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(groupBackgroundDrawable)
      case ConversationType.ThousandsGroup =>
        avatarGroup.setVisibility(View.VISIBLE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(groupBackgroundDrawable)
      case ConversationType.OneToOne | ConversationType.WaitForConnection =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.VISIBLE)
        setBackground(null)
      case _ =>
        avatarGroup.setVisibility(View.GONE)
        avatarSingle.setVisibility(View.GONE)
        setBackground(null)
    }
  }

  def clearImages(): Unit ={
    chatheads.foreach(_.clearUser())
    avatarSingle.clearUser()
  }
}

object ConversationAvatarView {

  def longToUnsignedLongLittleEndian(l: Long): BigInt = {
    val value = BigInt(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(l).array())
    if (value.signum < 0) {
      value + BigInteger.ONE.shiftLeft(java.lang.Long.SIZE)
    } else {
      value
    }
  }

  def uuidToBytes(uuid: UUID): Array[Byte] = {
    val byteBuffer = ByteBuffer.wrap(new Array[Byte](16))
    byteBuffer.putLong(uuid.getMostSignificantBits)
    byteBuffer.putLong(uuid.getLeastSignificantBits)
    byteBuffer.array()
  }

  case class RandomGeneratorFromConvId(convId: ConvId) {

    private val uuid = UUID.fromString(convId.str)

    private val leastBits = longToUnsignedLongLittleEndian(uuid.getLeastSignificantBits)
    private val mostBits = longToUnsignedLongLittleEndian(uuid.getMostSignificantBits)

    private var step = 0

    def rand(max: Long): Long = {
      val maxBig = BigInt(max)
      (rand() mod maxBig).longValue()
    }

    def rand(): BigInt = {
      val value =
        if (step % 2 == 0) {
          mostBits
        } else {
          leastBits
        }
      step += 1
      value
    }
  }

  def shuffle[T](seq: Seq[T], convId: ConvId): Seq[T] = {
    val generator = RandomGeneratorFromConvId(convId)
    val input = new ArrayBuffer[T] ++= seq
    val output = new ArrayBuffer[T]

    seq.indices.foreach { _ =>
      val idx = generator.rand(input.size).toInt
      output += input(idx)
      input.remove(idx)
    }

    output
  }
}
