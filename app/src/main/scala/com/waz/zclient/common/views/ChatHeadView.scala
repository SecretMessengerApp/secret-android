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
package com.waz.zclient.common.views

import android.content.Context
import android.graphics._
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import android.view.View.MeasureSpec.{EXACTLY, makeMeasureSpec}
import com.waz.api.User
import com.waz.api.User.ConnectionStatus
import com.waz.api.User.ConnectionStatus._
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccentColor
import com.waz.model.{AssetData, UserData, UserId, _}
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.{Round, Single}
import com.waz.utils.events.{EventContext, Signal}
import com.waz.utils.{NameParts, returning}
import com.waz.zclient.common.controllers.UserAccountsController
import com.waz.zclient.common.views.ImageAssetDrawable.ScaleType
import com.waz.zclient.ui.utils.TypefaceUtils
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.{Injectable, Injector, R, ViewHelper}

class ChatHeadView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends View(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {

  import ChatHeadView._

  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null)

  private val initialsTypeface  = TypefaceUtils.getTypeface(getString(R.string.chathead__user_initials__font))
  private val initialsFontColor = getColor(R.color.chathead__user_initials)
  private val iconOverlayColor  = getColor(R.color.chathead__glyph__overlay)
  private val grayScaleColor    = getColor(R.color.chathead__non_connected)
  private val overlayColor      = getColor(R.color.text__secondary_light)

  private val a = context.getTheme.obtainStyledAttributes(attrs, R.styleable.ChatHeadView, 0, 0)

  private val ctrl = new ChatHeadController(
    a.getBoolean(R.styleable.ChatHeadView_isSelectable, false),
    a.getBoolean(R.styleable.ChatHeadView_show_border, true),
    Some(Border(
      getDimen(R.dimen.chathead__min_size_large_border).toInt,
      getDimen(R.dimen.chathead__border_width).toInt,
      getDimen(R.dimen.chathead__large_border_width).toInt)),
    ColorVal(overlayColor),
    a.getBoolean(R.styleable.ChatHeadView_is_round, true),
    ColorVal(a.getColor(R.styleable.ChatHeadView_default_background, Color.GRAY)),
    a.getBoolean(R.styleable.ChatHeadView_show_waiting, true),
    a.getBoolean(R.styleable.ChatHeadView_gray_on_unconnected, true)
  )
  private val allowIcon                       = a.getBoolean(R.styleable.ChatHeadView_allow_icon, true)
  private val swapBackgroundAndInitialsColors = a.getBoolean(R.styleable.ChatHeadView_swap_background_and_initial_colors, false)
  private val iconFontSize                    = a.getDimensionPixelSize(R.styleable.ChatHeadView_glyph_size, getResources.getDimensionPixelSize(R.dimen.chathead__picker__glyph__font_size))
  private val initialsFontSize                = a.getDimensionPixelSize(R.styleable.ChatHeadView_initials_font_size, defaultInitialFontSize)
  a.recycle()

  private val initialsTextPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
    p.setTextAlign(Paint.Align.CENTER)
    p.setTypeface(initialsTypeface)
  }

  private val backgroundPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(Color.TRANSPARENT))

  private val iconTextPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG)) { p =>
    p.setTextAlign(Paint.Align.CENTER)
    p.setColor(initialsFontColor)
    p.setTypeface(TypefaceUtils.getGlyphsTypeface)
    p.setTextSize(iconFontSize)
  }

  private val glyphOverlayPaint = returning(new Paint(Paint.ANTI_ALIAS_FLAG))(_.setColor(iconOverlayColor))

  private val grayScaleColorMatrix = new ColorMatrix()

  private lazy val matrix = new Matrix()
  private lazy val bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG)
  private lazy val integrationDrawHelper = IntegrationSquareDrawHelper(ScaleType.CenterInside)

  ctrl.invalidate.on(Threading.Ui)(_ => invalidate())

  ctrl.drawColors.on(Threading.Ui) { case (grayScale, accentColor) =>
    if (grayScale) {
      grayScaleColorMatrix.setSaturation(0)
      initialsTextPaint.setColor(grayScaleColor)
      backgroundPaint.setColor(grayScaleColor)
    } else {
      grayScaleColorMatrix.setSaturation(1)
      if (swapBackgroundAndInitialsColors) {
        initialsTextPaint.setColor(accentColor.value)
        backgroundPaint.setColor(initialsFontColor)
      } else {
        backgroundPaint.setColor(accentColor.value)
        initialsTextPaint.setColor(initialsFontColor)
      }
    }

    val colorMatrix = new ColorMatrixColorFilter(grayScaleColorMatrix)
    backgroundPaint.setColorFilter(colorMatrix)
    invalidate()
  }

  def clearUser(): Unit =
    ctrl.clearUser()

  def setUserId(userId: UserId, zms: Option[ZMessaging]): Unit =
    ctrl.setUserId(userId, zms)

  def setUserId(userId: UserId): Unit =
    setUserId(userId, None): Unit

  def setIntegration(integration: IntegrationData): Unit =
    ctrl.setIntegration(integration)

  override def isSelected = {
    ctrl.selected.currentValue.getOrElse(false)
  }

  def requestSelected(selected: Boolean) = {
    ctrl.requestSelected ! selected
  }

  override def onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int): Unit = {
    var width: Int = MeasureSpec.getSize(widthMeasureSpec)
    var height: Int = MeasureSpec.getSize(heightMeasureSpec)
    if (ctrl.setSelectable || allowIcon) {
      height = ((width / chatheadBottomMarginRatio) + width).toInt
    }
    else {
      val size: Int = Math.min(width, height)
      width = size
      height = size
    }

    setMeasuredDimension(width, height)
    super.onMeasure(makeMeasureSpec(width, EXACTLY), makeMeasureSpec(height, EXACTLY))
  }

  override def onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = {
    super.onLayout(changed, left, top, right, bottom)
    ctrl.viewWidth ! Math.min(right - left, bottom - top)
  }

  override def onDraw(canvas: Canvas): Unit = {
    val size: Float = Math.min(getWidth, getHeight)
    if (size > 1) { // This is just to prevent a really small image. Instead we want to draw just nothing
      val borderWidth = ctrl.borderWidth.currentValue.getOrElse(0)
      val selected = ctrl.selected.currentValue.getOrElse(false)
      val connectionStatus = ctrl.connectionStatus.currentValue.getOrElse(UNCONNECTED)
      val glyph = getGlyphText(selected, connectionStatus, ctrl.showWaitingForConnection)
      val bitmap = ctrl.bitmap.currentValue.getOrElse(Option.empty[Bitmap])

      val radius: Float = size / 2f
      val x = (getWidth - size) / 2
      val y = (getHeight - size) / 2

      bitmap.fold {
        if (backgroundPaint.getColor != Color.TRANSPARENT) {
          drawBackgroundAndBorder(canvas, x, y, radius, borderWidth, new RectF(x, y, x + size, y + size))
        }
        ctrl.initials.currentValue.foreach { initials =>
          var fontSize: Float = initialsFontSize
          if (initialsFontSize == defaultInitialFontSize) {
            fontSize = 3f * radius / 4f
          }
          initialsTextPaint.setTextSize(fontSize)
          canvas.drawText(initials, getWidth / 2, getVerticalTextCenter(initialsTextPaint, getHeight / 2), initialsTextPaint)
        }
      } { bitmap =>

        if (ctrl.chatheadInfo.currentValue.flatten.exists(_.isBot)) {
          val bounds = new Rect(0, 0, getWidth, getHeight)
          ImageAssetDrawable.ScaleType.CenterInside(matrix, bitmap.getWidth, bitmap.getHeight, Dim2(bounds.width(), bounds.height()))
          matrix.postTranslate(bounds.left, bounds.top)
          integrationDrawHelper.draw(canvas, bitmap, bounds, matrix, bitmapPaint)

        } else {
          canvas.drawBitmap(bitmap, null, new RectF(x, y, x + size, y + size), backgroundPaint)
        }
      }

      // Cut out
      if (selected || !TextUtils.isEmpty(glyph)) {
        canvas.drawCircle(radius + x, radius + y, radius - borderWidth, glyphOverlayPaint)
        canvas.drawText(glyph, radius + x, (radius + iconTextPaint.getTextSize / 2) + y, iconTextPaint)
      }
    }
  }

  private def drawBackgroundAndBorder(canvas: Canvas, xOffset: Float, yOffset: Float, radius: Float, borderWidthPx: Int, rect: RectF) = {
    if (ctrl.isBot.currentValue.getOrElse(false)) {
      val radius = integrationDrawHelper.cornerRadius(rect.width())
      canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
    }
    else if (swapBackgroundAndInitialsColors) {
      if (ctrl.isRound) {
        canvas.drawCircle(radius + xOffset, radius + yOffset, radius, initialsTextPaint)
        canvas.drawCircle(radius + xOffset, radius + yOffset, radius - borderWidthPx, backgroundPaint)
      } else {
        canvas.drawPaint(initialsTextPaint)
      }
    }
    else {
      if (ctrl.isRound) {
        canvas.drawCircle(radius + xOffset, radius + yOffset, radius, backgroundPaint)
      } else {
        canvas.drawPaint(backgroundPaint)
      }
    }
  }

  private def getVerticalTextCenter(textPaint: Paint, cy: Float): Float = {
    cy - ((textPaint.descent + textPaint.ascent) / 2f)
  }

  private def getGlyphText(selected: Boolean, connectionStatus: ConnectionStatus, showWaiting: Boolean): String = {
    if (selected) getResources.getString(selectedUserGlyphId)
    else {
      connectionStatus match {
        case PENDING_FROM_OTHER | PENDING_FROM_USER | IGNORED if showWaiting => getResources.getString(pendingUserGlyphId)
        case BLOCKED => getResources.getString(blockedUserGlyphId)
        case _ => ""
      }
    }
  }
}

object ChatHeadView {

  private val selectedUserGlyphId: Int = R.string.glyph__check
  private val pendingUserGlyphId: Int = R.string.glyph__clock
  private val blockedUserGlyphId: Int = R.string.glyph__block
  private val chatheadBottomMarginRatio: Float = 12.75f
  private val defaultInitialFontSize = -1
}

protected class ChatHeadController(val setSelectable:            Boolean        = false,
                                   val showBorder:               Boolean        = true,
                                   val border:                   Option[Border] = None,
                                   val contactBackgroundColor:   ColorVal       = ColorVal(Color.GRAY),
                                   val isRound:                  Boolean        = true,
                                   val defaultBackgroundColor:   ColorVal       = ColorVal(Color.GRAY),
                                   val showWaitingForConnection: Boolean        = true,
                                   val grayscaleOnUnconnected:   Boolean        = true)
                                  (implicit inj: Injector, eventContext: EventContext, context: Context) extends Injectable {

  val zMessaging = inject[Signal[ZMessaging]]

  val teamsAndUserController = inject[UserAccountsController]

  val assignInfo = Signal[Option[AssignDetails]]()

  def clearUser(): Unit = assignInfo ! None

  def setUserId(userId: UserId, zms: Option[ZMessaging] = None): Unit =
    Option(userId).fold(throw new IllegalArgumentException("UserId should not be null"))(u => assignInfo ! Some(AssignDetails(u, zms)))

  def setIntegration(integration: IntegrationData): Unit =
    Option(integration).fold(throw new IllegalArgumentException("IntegrationDetails should not be null"))(i => assignInfo ! Some(AssignDetails(i)))

  val chatheadInfo: Signal[Option[ChatHeadDetails]] = assignInfo.flatMap {
    case Some(AssignDetails(_, Some(integration), _)) =>
      Signal.const(Some(ChatHeadDetails(integration)))
    case Some(AssignDetails(Some(userId), _, zms)) =>
      for {
        z  <- zms.fold(zMessaging)(Signal.const)
        ud <- z.usersStorage.signal(userId)
      } yield Some(ChatHeadDetails(ud, z.teamId.isDefined && z.teamId == ud.teamId, zms = Some(z)))
    case _ =>
      Signal.const(None)
  }

  val accentColor = chatheadInfo.map {
    case Some(details) => details.accentColor
    case _ => defaultBackgroundColor
  }

  val connectionStatus = chatheadInfo.map {
    case Some(details) => details.connectionStatus
    case _ => UNCONNECTED
  }

  val teamMember = chatheadInfo.map {
    case Some(details) => details.teamMember
    case _ => false
  }

  val initials = chatheadInfo.map {
    case Some(details) => details.initials
    case _ => ""
  }

  val knownUser = chatheadInfo.map {
    case Some(details) => details.knownUser
    case _ => false
  }

  val grayScale = chatheadInfo.map {
    case Some(details) => details.grayScale
    case _ => false
  }.map(_ && grayscaleOnUnconnected)

  val assetIdAndZms = chatheadInfo.map {
    case Some(details) => (details.assetId, details.zms)
    case _ => (None, None)
  }

  val selectable = knownUser.zip(teamMember).map {
    case (isKnownUser, isTeamMember) => isKnownUser || isTeamMember
  }

  val requestSelected = Signal(false)

  val selected = selectable.zip(requestSelected).map {
    case (selectable, requestSelected) => selectable && requestSelected
  }

  val viewWidth = Signal(0)

  val borderWidth = viewWidth.zip(knownUser).map {
    case (width, isKnownUser) if showBorder && isKnownUser => border.fold(0)(_.getWidth(width))
    case _ => 0
  }

  val isBot = chatheadInfo.map {
    case Some(info) => info.isBot
    case _          => false
  }

  val isDeleted = chatheadInfo.map {
    case Some(info) => info.isDeleted
    case _ => true
  }

  val bitmapResult = (for {
    (assetId, zmsOpt) <- assetIdAndZms
    zMessaging        <- zmsOpt.fold(zMessaging)(Signal.const)
    viewWidth         <- viewWidth
    borderWidth       <- borderWidth
    accentColor       <- accentColor
    isBot             <- isBot
    isDeleted         <- isDeleted
  } yield (zMessaging, assetId, viewWidth, borderWidth, accentColor, isBot, isDeleted)).flatMap[BitmapResult] {
    case (zms, Some(id), width, bWidth, bColor, bot, false) if width > 0 => zms.assetsStorage.signal(id).flatMap {
      case data@AssetData.IsImage() if isRound && !bot => BitmapSignal(zms, data, Round(width, bWidth, bColor.value))
      case data@AssetData.IsImage() => BitmapSignal(zms, data, Single(width))
      case _ => Signal.empty[BitmapResult]
    }
    case (_, aid, width, _, _, _, _) => Signal.const(BitmapResult.Empty)
  }

  val bitmap = bitmapResult.flatMap[Option[Bitmap]] {
    case BitmapLoaded(bitmap, etag) if bitmap != null => Signal(Some(bitmap))
    case _ => Signal(Option.empty[Bitmap])
  }

  val drawColors = grayScale.zip(accentColor)

  //Everything else that requires a redraw
  val invalidate = Signal(bitmap, selected, borderWidth).zip(Signal(initials, connectionStatus)).onChanged

  case class AssignDetails(userId: Option[UserId], integration: Option[IntegrationData], zms: Option[ZMessaging]){
    assert(userId.nonEmpty || integration.nonEmpty)
  }

  object AssignDetails {
    def apply(userId: UserId, zms: Option[ZMessaging]): AssignDetails = AssignDetails(Some(userId), None, zms)
    def apply(integration: IntegrationData): AssignDetails = AssignDetails(None, Some(integration), None)
  }

  case class ChatHeadDetails(accentColor: ColorVal = contactBackgroundColor,
                             connectionStatus: User.ConnectionStatus = UNCONNECTED,
                             teamMember: Boolean = false,
                             hasBeenInvited: Boolean = false,
                             initials: String,
                             knownUser: Boolean = false,
                             grayScale: Boolean = false,
                             assetId: Option[AssetId] = None,
                             selectable: Boolean = false,
                             isBot: Boolean = false,
                             isDeleted: Boolean = false,
                             zms: Option[ZMessaging] = None
                            )

  object ChatHeadDetails {

    def apply(user: UserData, isTeamMember: Boolean, zms: Option[ZMessaging])(implicit context: Context): ChatHeadDetails = {
      val knownUser = user.isConnected || user.isSelf

      ChatHeadDetails(
        accentColor = ColorVal(AccentColor(user.accent).color),
        connectionStatus = user.connection,
        initials = NameParts.parseFrom(if (user.deleted) getString(R.string.default_deleted_username) else user.name).initials,
        knownUser = knownUser,
        grayScale = !(user.isConnected || user.isSelf || isTeamMember),
        assetId = user.picture,
        selectable = knownUser || isTeamMember,
        isBot = user.isWireBot,
        isDeleted = user.deleted,
        zms = zms
      )
    }

    def apply(integration: IntegrationData): ChatHeadDetails =
      ChatHeadDetails(
        initials = NameParts.parseFrom(integration.name).initials,
        assetId = integration.asset,
        isBot = true
      )
  }
}

case class Border(minSizeForLargeBorderWidth: Int, smallBorderWidth: Int, largeBorderWidth: Int) {
  def getWidth(viewWidth: Int) = {
    if (viewWidth < minSizeForLargeBorderWidth) smallBorderWidth else largeBorderWidth
  }
}

case class ColorVal(value: Int)

