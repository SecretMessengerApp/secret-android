/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.jsy.common.views

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.jsy.common.views.pickuer.UserRowView
import com.waz.log.BasicLogging.LogTag
import com.waz.model.{UserData, UserId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.views.ChatHeadView
import com.waz.zclient.ui.text.{TextTransform, TypefaceTextView}
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.ViewUtils
import com.waz.zclient.utils._
import com.waz.zclient.{R, ViewHelper}

class ChatheadWithTextFooter(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int) extends LinearLayout(context, attrs, defStyleAttr) with ViewHelper with UserRowView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  implicit val logTag: LogTag = LogTag(getClass.getSimpleName)
  lazy implicit val uiStorage = inject[UiStorage]

  inflate(R.layout.chathead_with_text_footer, this, addToParent = true)

  private val chathead = findById[ChatHeadView](R.id.cv__chathead)
  private val footer = findById[TypefaceTextView](R.id.ttv__text_view)
  private val guestIndicator = findById[TypefaceTextView](R.id.guest_indicator)
  private val transformer = TextTransform.get(context.getResources.getString(R.string.participants__chathead__name_label__text_transform))
  private val userId = Signal[UserId]()
  private val userInfo = for{
//    z <- inject[Signal[ZMessaging]]
    uId <- userId
    data <- UserSignal(uId)
    //isGuest <- z.teams.isGuest(uId)
  } yield (data) // true means guest status

  setOrientation(LinearLayout.VERTICAL)
  initAttributes(attrs)
  userInfo.on(Threading.Ui) { data =>
    updateView(data,false)
  }
  if (ThemeUtils.isDarkTheme(context)) {
    applyDarkTheme()
  } else {
    applyLightTheme()
  }


  override def isSelected: Boolean = chathead.isSelected

  override def setSelected(selected: Boolean): Unit = {
    chathead.requestSelected(selected)
  }

  def setUser(user: UserData): Unit = {
    updateView((user, false))
    userId ! user.id
  }

  def setUserId(userId: UserId): Unit = {
    this.userId ! userId
  }

  /*
  private def updateView(userInfo: (UserData, Boolean)): Unit = {
    chathead.setUserId(userInfo._1.id)
    val userId : UserId = userInfo._1.id
    val remark = SpUtils.getString(context,SpUtils.SP_NAME_NORMAL,userId.toString(),"")
    if(!StringUtils.isBlank(remark)){
      val currentAccId = SpUtils.getString(getContext, SpUtils.SP_NAME_NORMAL, SpUtils.currentAccId, "")
      val remarkAccId = SpUtils.getString(getContext, SpUtils.SP_NAME_NORMAL, remark, "")
      if(currentAccId.equals(remarkAccId)){
        footer.setText(transformer.transform(remark))
      }else{
        val convId : String = SpUtils.getString(context,SpUtils.SP_NAME_NORMAL,userId.toString() + "0","");
        if(icon != null){
          if(convId.equals(icon.initData.remoteId)){
            val nickName = SpUtils.getString(context,SpUtils.SP_NAME_NORMAL,userId.toString() + "1","");
            if(!StringUtils.isBlank(nickName)){
              footer.setText(nickName)
            }else{
              footer.setText(userInfo._1.getDisplayName)
            }
          }else{
            footer.setText(userInfo._1.getDisplayName)
          }
        }else{
          footer.setText(userInfo._1.getDisplayName)
        }

      }
    }else{
      val convId : String = SpUtils.getString(context,SpUtils.SP_NAME_NORMAL,userId.toString() + "0","");
      if(icon != null){
        if(convId.equals(icon.initData.remoteId)){
          val nickName = SpUtils.getString(context,SpUtils.SP_NAME_NORMAL,userId.toString() + "1","");
          if(!StringUtils.isBlank(nickName)){
            footer.setText(nickName)
          }else{
            footer.setText(userInfo._1.getDisplayName)
          }
        }else{
          footer.setText(userInfo._1.getDisplayName)
        }
      }else{
        footer.setText(userInfo._1.getDisplayName)
      }

    }


    //footer.setText(transformer.transform(userInfo._1.getDisplayName))
    guestIndicator.setVisibility(if (userInfo._2) View.VISIBLE else View.GONE)
    guestIndicator.setTextColor(Color.BLACK)
  }
  */

  private def updateView(userInfo: (UserData, Boolean)): Unit = {
    chathead.setUserId(userInfo._1.id)
    footer.setText(transformer.transform(userInfo._1.getDisplayName))
    guestIndicator.setVisibility(if (userInfo._2) View.VISIBLE else View.GONE)
    guestIndicator.setTextColor(Color.BLACK)
  }

  def getUser: Option[UserId] = userInfo.currentValue.map(_.id)

  def onClicked(): Unit = {
    chathead.setSelected(!chathead.isSelected)
  }

  def setChatheadFooterTextColor(color: Int): Unit = {
    //footer.setTextColor(color)
    footer.setTextColor(Color.BLACK)
  }

  def setChatheadFooterFont(fontName: String): Unit = {
    footer.setTypeface(fontName)
  }

  override def setOnClickListener(l: View.OnClickListener): Unit = {
    super.setOnClickListener(new View.OnClickListener() {
      def onClick(v: View): Unit = {
        l.onClick(chathead)
      }
    })
  }

  def setChatheadDimension(size: Int): Unit = {
    ViewUtils.setWidth(chathead, size)
    ViewUtils.setHeight(chathead, size)
  }

  def setFooterWidth(width: Int): Unit = {
    ViewUtils.setWidth(footer, width)
  }

  def applyLightTheme(): Unit = {
    footer.setTextColor(ContextCompat.getColor(getContext, R.color.text__primary_light))
  }

  def applyDarkTheme(): Unit = {
    footer.setTextColor(ContextCompat.getColor(getContext, R.color.black))
  }

  private def initAttributes(attrs: AttributeSet): Unit = {
    var chatheadSize: Int = 0
    var a: TypedArray = null
    try {
      a = getContext.obtainStyledAttributes(attrs, R.styleable.ChatheadWithTextFooter)
      chatheadSize = a.getDimensionPixelSize(R.styleable.ChatheadWithTextFooter_footer_chathead_size, 0)
    } finally {
      if (a != null) {
        a.recycle()
      }
    }
    if (chatheadSize > 0) {
      setChatheadDimension(chatheadSize)
    }
  }
}
