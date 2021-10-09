/**
 * Wire
 * Copyright (C) 2019 Wire Swiss GmbH
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
package com.waz.zclient.common.views

import android.content.Context
import android.graphics._
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.{DiskCacheStrategy, GlideException}
import com.bumptech.glide.load.resource.bitmap.{CenterCrop, CircleCrop}
import com.bumptech.glide.load.{DataSource, Transformation}
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.{RequestListener, RequestOptions}
import com.jsy.common.model.circle.CircleConstant
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.UserData.ConnectionStatus
import com.waz.model._
import com.waz.utils.events.Signal
import com.waz.utils.{NameParts, returning}
import com.waz.zclient.calling.controllers.CallController.CallParticipantInfo
import com.waz.zclient.common.views.ChatHeadViewNew._
import com.waz.zclient.glide.WireGlide
import com.waz.zclient.glide.transformations.{GlyphOverlayTransformation, IntegrationBackgroundCrop}
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils.{getColor, getString}
import com.waz.zclient.utils.{UiStorage, UserSignal}
import com.waz.zclient.{R, ViewHelper}

class ChatHeadViewNew(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends ImageView(context, attrs, defStyleAttr) with ViewHelper with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)

  def this(context: Context) = this(context, null)
  private lazy implicit val uiStorage = inject[UiStorage]
//  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val userId = Signal[Option[UserId]]()
  val attributes: Attributes = parseAttributes(attrs)

  private var placeId = 0
  private lazy val options = for {
//    z <- zms
    Some(uId) <- userId
    user <- UserSignal(uId)
  } yield optionsForUser(user, /*z.teamId.exists(user.teamId.contains(_))*/false, placeId, attributes)

  options.onUi(setInfo)

  def parseAttributes(attributeSet: AttributeSet): Attributes = {
    val a = context.getTheme.obtainStyledAttributes(attributeSet, R.styleable.ChatHeadView, 0, 0)

    val isRound = a.getBoolean(R.styleable.ChatHeadView_is_round, true)
    val showWaiting = a.getBoolean(R.styleable.ChatHeadView_show_waiting, false)
    val grayScaleOnConnected = a.getBoolean(R.styleable.ChatHeadView_gray_on_unconnected, false)
    val defaultBackground = a.getColor(R.styleable.ChatHeadView_default_background, Color.TRANSPARENT)
    val allowIcon = a.getBoolean(R.styleable.ChatHeadView_allow_icon, true)

    Attributes(isRound, showWaiting && allowIcon, grayScaleOnConnected && allowIcon, defaultBackground)
  }

  def clearImage(): Unit = {
    WireGlide(context).clear(this)
    setImageDrawable(null)
  }

  def clearUser(): Unit = {
    clearImage()
    this.userId ! None
    this.placeId = 0
  }

  def loadUser(userId: UserId): Unit = {
    loadUser(userId, 0)
  }

  def loadUser(userId: UserId, placeId: Int): Unit = {
    verbose(l"will set loadUser: $userId")
    WireGlide(context).clear(this)
    this.userId ! Some(userId)
    this.placeId = placeId
  }

  def setUserData(userData: UserData): Unit = {
    setUserData(userData, 0, false)
  }

  def setUserData(userData: UserData, belongsToSelfTeam: Boolean): Unit = {
    setUserData(userData, 0, belongsToSelfTeam)
  }

  def setUserData(userData: UserData, placeId: Int, belongsToSelfTeam: Boolean = false): Unit = {
    setInfo(optionsForUser(userData, belongsToSelfTeam, placeId, attributes))
  }

  private def optionsForUser(user: UserData, teamMember: Boolean, placeId: Int, attributes: Attributes): ChatHeadViewOptions = {
    val backgroundColor = AccentColor.apply(user.accent).color
    val greyScale = !(user.isConnected || user.isSelf || user.isWireBot || teamMember) && attributes.greyScaleOnConnected
    val initials = NameParts.parseFrom(user.name).initials
    val icon =
      if (user.connection == ConnectionStatus.Blocked)
        Some(OverlayIcon.Blocked)
      else if (pendingConnectionStatuses.contains(user.connection) && attributes.showWaiting && !user.isWireBot)
        Some(OverlayIcon.Waiting)
      else
        None
    val shape =
      if (user.isWireBot && attributes.isRound)
        Some(CropShape.RoundRect)
      else if (attributes.isRound)
        Some(CropShape.Circle)
      else
        None

    val rAssetId = user.rAssetId match {
      case Some(a) =>
        Option(a)
      case _ =>
        user.picture match {
          case Some(p) =>
            Option(p.str)
          case _ =>
            Option.empty[String]
        }
    }
    val imageUrl: String = rAssetId.getOrElse("")
    ChatHeadViewOptions(Option(CircleConstant.appendAvatarUrl(imageUrl, context)), backgroundColor, greyScale, initials, shape, icon, placeId)
  }

  def setCallParticipant(user: CallParticipantInfo, placeId: Int = 0): Unit = {
    setInfo(optionsCallParticipant(user, placeId, attributes))
  }

  private def optionsCallParticipant(user: CallParticipantInfo, placeId: Int, attributes: Attributes): ChatHeadViewOptions = {
    val backgroundColor = AccentColor.apply(0).color
    val greyScale = attributes.greyScaleOnConnected
    val initials = NameParts.parseFrom(user.displayName).initials
    val icon = None
    val shape =
      if (attributes.isRound)
        Some(CropShape.Circle)
      else
        None

    val rAssetId = if (user.assetId.nonEmpty) {
      user.assetId.get.str
    } else {
      ""
    }

    ChatHeadViewOptions(Option(CircleConstant.appendAvatarUrl(rAssetId, context)), backgroundColor, greyScale, initials, shape, icon, placeId)
  }

  def setAccountData(accountData: AccountData, placeId: Int = 0): Unit = {
    setInfo(optionsForAccount(accountData, placeId, attributes))
  }

  private def optionsForAccount(accountData: AccountData, placeId: Int, attributes: Attributes): ChatHeadViewOptions = {
    val backgroundColor = AccentColor.apply(0).color
    val greyScale = false
    val initials = NameParts.parseFrom(if (accountData.name.nonEmpty) accountData.name.get else "").initials
    val icon =
      if (attributes.showWaiting)
        Some(OverlayIcon.Waiting)
      else
        None
    val shape =
      if (attributes.isRound)
        Some(CropShape.Circle)
      else
        None

    val rAssetId = if (accountData.rAssetId.nonEmpty) {
      accountData.rAssetId.get
    } else {
      ""
    }
    ChatHeadViewOptions(Option(CircleConstant.appendAvatarUrl(rAssetId, context)), backgroundColor, greyScale, initials, shape, icon, placeId)
  }

  def setIntegration(integration: IntegrationData, placeId: Int = 0): Unit ={
    setInfo(optionsForIntegration(integration, placeId, attributes))
  }

  private def optionsForIntegration(integration: IntegrationData, placeId: Int, attributes: Attributes): ChatHeadViewOptions = {
    val imageUrl: String = integration.asset.map(_.str).getOrElse("")
    ChatHeadViewOptions(
      Option(CircleConstant.appendAvatarUrl(imageUrl, context)),
      attributes.defaultBackground,
      grayScale = false,
      NameParts.parseFrom(integration.name).initials,
      cropShape = Some(CropShape.RoundRect),
      None, placeId)
  }

  def loadImageUrlPlaceholder(imageUrl: String, placeId: Int = 0): Unit = {
    if (TextUtils.isEmpty(imageUrl)) {
      WireGlide(context).clear(this)
      setImageResource(placeId)
    } else {
      val options: ChatHeadViewOptions = defaultOptions(imageUrl, placeId, attributes)
      val requestBuilder: RequestBuilder[Drawable] = options.glideRequest(context)
      val requestOptions: RequestOptions = new RequestOptions
      requestOptions.placeholder(ContextCompat.getDrawable(context.getApplicationContext, placeId))
      requestBuilder.apply(requestOptions).into(this)
    }
  }

  private def defaultOptions(imageUrl: String, placeId: Int, attributes: Attributes): ChatHeadViewOptions = {
    ChatHeadViewOptions(
      Option(imageUrl),
      attributes.defaultBackground,
      grayScale = false,
      "",
      cropShape = if (attributes.isRound) Some(CropShape.Circle) else None,
      icon = None, placeId)
  }

  def setInfo(options: ChatHeadViewOptions): Unit = {
    //    verbose(l"will set options: $options")
    if (TextUtils.isEmpty(options.imageUrl.getOrElse(""))) {
      WireGlide(context).clear(this)
      setImageDrawable(options.errorholder)
    } else {
      options.glideRequest
        .addListener(new RequestListener[Drawable] {
          override def onLoadFailed(e: GlideException, model: Any, target: Target[Drawable], isFirstResource: Boolean): Boolean = {
            verbose(l"setInfo onLoadFailed isFirstResource:$isFirstResource, e:${e.getMessage}")
            setImageDrawable(options.errorholder)
            true
          }

          override def onResourceReady(resource: Drawable, model: Any, target: Target[Drawable], dataSource: DataSource, isFirstResource: Boolean): Boolean = {
            verbose(l"setInfo onResourceReady isFirstResource:$isFirstResource, dataSource:$dataSource")
            false
          }
        })
        .into(this)
    }
  }
}

object ChatHeadViewNew {
  private val pendingConnectionStatuses = Set(ConnectionStatus.PendingFromUser, ConnectionStatus.PendingFromOther)

  case class Attributes(isRound: Boolean,
                        showWaiting: Boolean,
                        greyScaleOnConnected: Boolean,
                        defaultBackground: Int)

  object OverlayIcon extends Enumeration {
    val Waiting, Blocked = Value
  }

  type OverlayIcon = OverlayIcon.Value

  object CropShape extends Enumeration {
    val RoundRect, Circle = Value
  }

  type CropShape = CropShape.Value

  case class ChatHeadViewOptions(imageUrl: Option[String],
                                 backgroundColor: Int,
                                 grayScale: Boolean,
                                 initials: String,
                                 cropShape: Option[CropShape],
                                 icon: Option[OverlayIcon],
                                 placeId: Int)(implicit context: Context) extends DerivedLogTag {
    lazy val errorholder = ChatHeadViewPlaceholder(backgroundColor, initials, cropShape = cropShape, reversedColors = cropShape.isEmpty, icon = icon)

    def glideRequest(implicit context: Context): RequestBuilder[Drawable] = {
      val request = imageUrl match {
        case Some(p) if (!TextUtils.isEmpty(p)) =>
          WireGlide(context).load(p)
        case _ =>
          verbose(l"picture is None, loading a placeholder")
          WireGlide(context).load(errorholder)
      }
      val requestOptions = new RequestOptions()
      requestOptions.diskCacheStrategy(DiskCacheStrategy.ALL)
      requestOptions.skipMemoryCache(false)

      if (placeId <= 0) {
        requestOptions.placeholder(errorholder)
      } else {
        requestOptions.placeholder(placeId)
      }

      val transformations = Seq.newBuilder[Transformation[Bitmap]]

      transformations += new CenterCrop()

      icon.map {
        case OverlayIcon.Waiting => R.string.glyph__clock
        case OverlayIcon.Blocked => R.string.glyph__block
      }.foreach { g =>
        requestOptions.diskCacheStrategy(DiskCacheStrategy.DATA)
        transformations += new GlyphOverlayTransformation(getString(g))
      }

      cropShape.foreach { cs =>
        transformations += (cs match {
          case CropShape.Circle => new CircleCrop()
          case CropShape.RoundRect => new IntegrationBackgroundCrop()
        })
      }

      val transformationsResult = transformations.result()
      if (transformationsResult.nonEmpty)
        requestOptions.transform(transformationsResult: _*)

      request.apply(requestOptions)
    }
  }

  case class ChatHeadViewPlaceholder(color: Int,
                                     text: String,
                                     cropShape: Option[CropShape],
                                     reversedColors: Boolean = false,
                                     icon: Option[OverlayIcon] = None)(implicit context: Context) extends Drawable {
    lazy val textPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
      p.setTextAlign(Paint.Align.CENTER)
      //val tf = TypefaceUtils.getTypeface(getString(if (reversedColors) R.string.wire__typeface__medium else R.string.wire__typeface__light))
      //p.setTypeface(tf)
      p.setColor(if (reversedColors) color else Color.WHITE)
    }

    lazy val backgroundPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) {
      _.setColor(if (reversedColors) getColor(R.color.black_16) else color)
    }

    lazy val darkenPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)){ p =>
      p.setColor(Color.BLACK)
      p.setAlpha(65)
    }

    lazy val glyphPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
      p.setTextAlign(Paint.Align.CENTER)
      p.setColor(Color.WHITE)
      p.setTypeface(TypefaceUtils.getGlyphsTypeface)
    }

    override def draw(canvas: Canvas): Unit = {
      val radius = Math.min(getBounds.width(), getBounds.height()) / 2
      cropShape match {
        case Some(CropShape.Circle) =>
          canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), radius, backgroundPaint)
        case Some(CropShape.RoundRect) =>
          canvas.drawRoundRect(new RectF(getBounds), radius * 0.4f, radius * 0.4f, backgroundPaint)
        case _ =>
          canvas.drawPaint(backgroundPaint)
      }

      textPaint.setTextSize(radius / 1.1f)
      val y = getBounds.centerY() - ((textPaint.descent + textPaint.ascent) / 2f)
      val x = getBounds.centerX()
      canvas.drawText(text, x, y, textPaint)

      icon.map {
        case OverlayIcon.Waiting => R.string.glyph__clock
        case OverlayIcon.Blocked => R.string.glyph__block
      }.foreach { glyph =>
        val textSize = radius
        glyphPaint.setTextSize(radius)
        cropShape match {
          case Some(CropShape.Circle) =>
            canvas.drawCircle(getBounds.centerX(), getBounds.centerY(), radius, darkenPaint)
          case Some(CropShape.RoundRect) =>
            canvas.drawRoundRect(new RectF(getBounds), radius * 0.4f, radius * 0.4f, darkenPaint)
          case _ =>
            canvas.drawPaint(darkenPaint)
        }
        canvas.drawText(context.getString(glyph), canvas.getClipBounds.centerX(), canvas.getClipBounds.centerY() + textSize / 2f, glyphPaint)
      }
    }

    override def setAlpha(alpha: Int): Unit = {
      backgroundPaint.setAlpha(alpha)
      invalidateSelf()
    }

    override def setColorFilter(colorFilter: ColorFilter): Unit = {
      backgroundPaint.setColorFilter(colorFilter)
      invalidateSelf()
    }

    override def getOpacity: Int = backgroundPaint.getAlpha
  }

}
