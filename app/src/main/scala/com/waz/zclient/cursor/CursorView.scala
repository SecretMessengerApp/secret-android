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
package com.waz.zclient.cursor

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.{Color, Rect}
import android.text.method.TransformationMethod
import android.text.{Editable, Spanned, TextUtils, TextWatcher}
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.animation.{Animation, TranslateAnimation}
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, MotionEvent, View}
import android.widget.TextView.OnEditorActionListener
import android.widget.{EditText, FrameLayout, LinearLayout, TextView}
import com.jsy.res.utils.ViewUtils
import com.waz.api.IConversation
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.ConversationData.ConversationType
import com.waz.model._
import com.waz.service.UserSearchService
import com.waz.threading.Threading
import com.waz.utils.events.{ClockSignal, Signal, SourceSignal}
import com.waz.utils.{returning, _}
import com.waz.zclient.common.controllers.global.AccentColorController
import com.waz.zclient.controllers.globallayout.IGlobalLayoutController
import com.waz.zclient.conversation.{ConversationController, ReplyController}
import com.waz.zclient.cursor.CursorController.{EnteredTextSource, KeyboardState}
import com.waz.zclient.cursor.MentionUtils.{Replacement, getMention}
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.ui.cursor.CursorEditText.OnBackspaceListener
import com.waz.zclient.ui.cursor._
import com.waz.zclient.ui.text.TextTransform
import com.waz.zclient.ui.text.TypefaceEditText.OnSelectionChangedListener
import com.waz.zclient.ui.views.OnDoubleClickListener
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.AvailabilityView
import com.waz.zclient.{ClipboardUtils, R, ViewHelper}
import org.json.JSONArray
import org.threeten.bp.Instant

import scala.concurrent.duration.{FiniteDuration, _}


class CursorView(val context: Context, val attrs: AttributeSet, val defStyleAttr: Int)
  extends LinearLayout(context, attrs, defStyleAttr)
    with ViewHelper
    with DerivedLogTag {

  def this(context: Context, attrs: AttributeSet) {
    this(context, attrs, 0)
  }

  def this(context: Context) {
    this(context, null)
  }

  import CursorView._
  import Threading.Implicits.Ui

  lazy val accentColorController = inject[AccentColorController]
  val convController = inject[ConversationController]
  val clipboard = inject[ClipboardUtils]
  val layoutController = inject[IGlobalLayoutController]
  lazy val accentColor = inject[Signal[AccentColor]]
  val messages = inject[MessagesController]
  private val controller = inject[CursorController]
  private lazy val replyController = inject[ReplyController]

  setOrientation(LinearLayout.VERTICAL)
  inflate(R.layout.cursor_view_content)

  val cursorToolbarFrame = returning(findById[CursorToolbarContainer](R.id.cal__cursor)) { f =>
    val left = getDimenPx(R.dimen.cursor_toolbar_padding_horizontal_edge)
    f.setPadding(left, 0, left, 0)
  }

  val cursorEditText = findById[CursorEditText](R.id.cet__cursor)
  val mainToolbar = findById[CursorToolbar](R.id.c__cursor__main)
  val secondaryToolbar = findById[CursorToolbar](R.id.c__cursor__secondary)
  val hintView = findById[TextView](R.id.ttv__cursor_hint)
  val dividerView = findById[View](R.id.v__cursor__divider)
  //val emojiButton = findById[CursorIconButton](R.id.cib__emoji)

  val switchButtonOn = findById[CursorIconButton](R.id.cib__switch_on)
  val switchButtonOff = findById[CursorIconButton](R.id.cib__switch_off)

  val sendButton = findById[CursorIconButton](R.id.cib__send)

  val forbiddenTipsTextView = findById[TextView](R.id.forbidden_tips_textView)

  val recordButton = returning(findById[CursorIconButton](R.id.cib__record)) { view =>
    view.menuItem ! Some(CursorMenuItem.AudioMessage)
    controller.recordBtnVisible.onUi(view.setVisible)
  }

  val ephemeralButton = returning(findById[EphemeralTimerButton](R.id.cib__ephemeral)) { v =>
    controller.ephemeralBtnVisible.onUi(v.setVisible)

    controller.ephemeralExp.pipeTo(v.ephemeralExpiration)

    v.setOnClickListener(new OnDoubleClickListener() {
      override def onDoubleClick(): Unit =
        controller.toggleEphemeralMode()

      override def onSingleClick(): Unit =
        controller.keyboard ! KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EPHEMERAL)
    })
  }

  controller.keyboard.onUi{
    case KeyboardState.ExtendedCursor(ExtendedCursorContainer.Type.EMOJIS) =>

      if(switchButtonOff != null) {
        switchButtonOn.menuItem ! Some(CursorMenuItem.Emoji)
        switchButtonOn.setTextColor(Color.parseColor("#EC69B9"))
        switchButtonOff.performClick()
      }
    case _ =>
  }

  def showUserViews(): Unit = {
    ViewUtils.getView[FrameLayout](this, R.id.fl__cursor__switch).setVisibility(View.VISIBLE)
    cursorToolbarFrame.setVisibility(View.GONE)
    dividerView.setVisibility(View.GONE)
  }

  def showGlyphItems(conversationData: ConversationData, other: UserData): Unit = {

    if (conversationData != null) {
      if (conversationData.convType == IConversation.Type.GROUP) {
        showCursorItemsGroup()
      } else if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
        showCursorItemsThousandsGroup(conversationData)
      } else if (conversationData.convType == IConversation.Type.ONE_TO_ONE) {
        showCursorItemsNormalOneToOne()
      } else {
        // ... SELF  、 UNKNOW
        showCursorItemsEmptyConversationData()
      }
    } else {
      showCursorItemsEmptyConversationData()
    }
  }

  val defaultHintTextColor = hintView.getTextColors.getDefaultColor

  val dividerColor = controller.isEditingMessage.map {
    case true => getColor(R.color.separator_light)
    case _ => dividerView.getBackground.asInstanceOf[ColorDrawable].getColor
  }

  val lineCount = Signal(1)

  private val cursorSpanWatcher = new MentionSpanWatcher
  private val cursorText: SourceSignal[String] = Signal(cursorEditText.getEditableText.toString)
  private val cursorSelection: SourceSignal[(Int, Int)] = Signal((cursorEditText.getSelectionStart, cursorEditText.getSelectionEnd))

  val mentionQuery = Signal(cursorText, cursorSelection).collect {
    case (text, (_, sEnd)) if sEnd <= text.length => MentionUtils.mentionQuery(text, sEnd)
  }
  val selectionHasMention = Signal(cursorText, cursorSelection).collect {
    case (text, (_, sEnd)) if sEnd <= text.length =>
      MentionUtils.mentionMatch(text, sEnd).exists { m =>
        CursorMentionSpan.hasMentionSpan(cursorEditText.getEditableText, m.start, sEnd)
      }
  }
  val cursorSingleSelection = cursorSelection.map(s => s._1 == s._2)
  val mentionSearchResults = for {
    searchService <- inject[Signal[UserSearchService]]
    convId <- inject[ConversationController].currentConvId
    query <- mentionQuery
    selectionHasMention <- selectionHasMention
    selectionSingle <- cursorSingleSelection
    results <- if (selectionHasMention || !selectionSingle)
      Signal.const(IndexedSeq.empty[UserData])
    else
      searchService.mentionsSearchUsersInConversation(convId, query.getOrElse(""), includeSelf = false)
  } yield results.reverse

  def createMention(userId: UserId, name: String, editText: EditText, selectionIndex: Int, accentColor: Int): Unit = {
    val editable = editText.getEditableText
    getMention(editable.toString, selectionIndex, userId, name).foreach {
      case (mention, Replacement(rStart, rEnd, rText)) =>
        editable.replace(rStart, rEnd, CursorMentionSpan.PlaceholderChar + " ")
        editable.setSpan(
          CursorMentionSpan(userId, rText, accentColor),
          mention.start,
          mention.start + 1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editText.setSelection(mention.start + 2)
        controller.enteredText ! (getText, EnteredTextSource.FromView)
    }
  }

  def createLongMention(userId: UserId, name: String, editText: EditText, selectionIndex: Int, accentColor: Int): Unit = {
    val editable = editText.getEditableText
    getMention(editable.toString + "@", selectionIndex + 1, userId, name).foreach {
      case (mention, Replacement(rStart, rEnd, rText)) =>
        editable.insert(rStart, CursorMentionSpan.PlaceholderChar + " ")
        editable.setSpan(
          CursorMentionSpan(userId, rText, accentColor),
          mention.start,
          mention.start + 1,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editText.setSelection(mention.start + 2)
        controller.enteredText ! (getText, EnteredTextSource.FromView)
    }
  }

  lineCount.onUi(cursorEditText.setLines(_))

  dividerColor.onUi(dividerView.setBackgroundColor)
  //bgColor.onUi(setBackgroundColor)

  //  emojiButton.menuItem ! Some(CursorMenuItem.Emoji)
  //  keyboardButton.menuItem ! Some(CursorMenuItem.Keyboard)

  switchButtonOn.menuItem ! Some(CursorMenuItem.LineUp)
  switchButtonOff.menuItem ! Some(CursorMenuItem.LineDown)

  val mShowAction = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f,
    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF,
    1.0f, Animation.RELATIVE_TO_SELF, 0.0f)
  mShowAction.setDuration(500)

  controller.curSorMainViewVisible.onUi { vis =>
    switchButtonOn.setVisible(!vis)
    switchButtonOff.setVisible(vis)
  }

  switchButtonOn.onClick {
    cursorToolbarFrame.startAnimation(mShowAction)
    cursorToolbarFrame.setVisibility(View.VISIBLE)
    dividerView.setVisibility(View.VISIBLE)
    controller.curSorMainViewVisible ! true
    controller.cursorCallback.foreach(_.expandCursorItems())
    val emojiSelected = controller.extendedCursor.currentValue.head== ExtendedCursorContainer.Type.EMOJIS
    if(emojiSelected){
      controller.keyboard ! KeyboardState.EmojiHidden
    }
  }

  switchButtonOff.onClick {
    cursorToolbarFrame.setVisibility(View.GONE)
    dividerView.setVisibility(View.GONE)
    controller.curSorMainViewVisible ! false
    controller.cursorCallback.foreach(_.collapseCursorItems())
  }

  val cursorHeight = getDimenPx(R.dimen.new_cursor_height)

  def showCursorItemsGroup(): Unit = {
    mainToolbar.cursorItems ! MainCursorItemsGroup
    secondaryToolbar.cursorItems ! SecondaryCursorItemsGroup
  }

  def showCursorItemsThousandsGroup(conversationData: ConversationData): Unit = {
    mainToolbar.cursorItems ! MainCursorItemsThousandsGroup
    secondaryToolbar.cursorItems ! SecondaryCursorItemsThousandsGroup
  }

  def showCursorItemsNormalOneToOne(): Unit = {
    mainToolbar.cursorItems ! MainCursorItemsOneToOne
    secondaryToolbar.cursorItems ! SecondaryCursorItemsOneToOne
  }

  def showCursorItemsEmptyConversationData(): Unit = {
    mainToolbar.cursorItems ! MainCursorItemsEmptyConversationData
    secondaryToolbar.cursorItems ! SecondaryCursorItemsEmptyConversationData
  }

  cursorEditText.setOnSelectionChangedListener(new OnSelectionChangedListener {
    override def onSelectionChanged(selStart: Int, selEnd: Int): Unit =
      cursorSelection ! (selStart, selEnd)
  })

  cursorEditText.addTextChangedListener(new TextWatcher() {

    override def beforeTextChanged(charSequence: CharSequence, start: Int, count: Int, after: Int): Unit = {
      val editable = cursorEditText.getEditableText
      editable.removeSpan(cursorSpanWatcher)
      editable.setSpan(cursorSpanWatcher, 0, editable.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE)
    }

    override def onTextChanged(charSequence: CharSequence, start: Int, before: Int, count: Int): Unit = {
      val text = charSequence.toString
      controller.enteredText ! (getText, EnteredTextSource.FromView)
      if (text.trim.nonEmpty) lineCount ! Math.max(cursorEditText.getLineCount, 1)
      cursorText ! charSequence.toString
    }

    override def afterTextChanged(editable: Editable): Unit = {}
  })
  cursorEditText.setBackspaceListener(new OnBackspaceListener {

    //XXX: This is a bit ugly...
    var hasSelected = false

    override def onBackspace(): Boolean = {
      val sStart = cursorEditText.getSelectionStart
      val sEnd = cursorEditText.getSelectionEnd
      val mentionAtSelection = CursorMentionSpan.getMentionSpans(cursorEditText.getEditableText).find(_._3 == sEnd)
      mentionAtSelection match {
        case Some((_, s, e)) if hasSelected =>
          cursorEditText.getEditableText.replace(s, e, "")
          hasSelected = false
          true
        case Some(_) if sStart == sEnd && !hasSelected =>
          cursorEditText.post(new Runnable {
            override def run(): Unit = {
              mentionAtSelection.foreach(m => cursorEditText.setSelection(m._2, m._3))
              hasSelected = true
            }
          })
          true
        case None =>
          hasSelected = false
          false
      }
    }
  })

  def submitTextJson(json: String, mentions: Seq[Mention] = Nil): Boolean = controller.submitTextJson(json, mentions)

  def submitTextJsonForRecipients(json: String, mentions: Seq[Mention] = Nil, uids: JSONArray, unblock: Boolean = false): Boolean = {
    controller.submitTextJsonForRecipients(json, mentions, uids, unblock)
  }

  cursorEditText.setOnClickListener(new OnClickListener {
    override def onClick(v: View): Unit = controller.notifyKeyboardVisibilityChanged(true)
  })

  cursorEditText.setOnEditorActionListener(new OnEditorActionListener {
    override def onEditorAction(textView: TextView, actionId: Int, event: KeyEvent): Boolean = {
      if (actionId == EditorInfo.IME_ACTION_SEND ||
        (cursorEditText.getImeOptions == EditorInfo.IME_ACTION_SEND &&
          event != null &&
          event.getKeyCode == KeyEvent.KEYCODE_ENTER &&
          event.getAction == KeyEvent.ACTION_DOWN)) {
        val cursorText = getText
        controller.submit(cursorText.text, cursorText.mentions)
      } else
        false
    }
  })

  controller.editHasFocus ! cursorEditText.hasFocus
  cursorEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    override def onFocusChange(view: View, hasFocus: Boolean): Unit = controller.editHasFocus ! hasFocus
  })

  cursorEditText.setTransformationMethod(new TransformationMethod() {
    override def getTransformation(source: CharSequence, view: View): CharSequence = {
      source
    }

    override def onFocusChanged(view: View, sourceText: CharSequence, focused: Boolean, direction: Int, previouslyFocusedRect: Rect): Unit = ()
  })

  cursorEditText.setFocusableInTouchMode(true)

  controller.sendButtonEnabled.onUi { enabled =>
    cursorEditText.setImeOptions(if (enabled) EditorInfo.IME_ACTION_NONE else EditorInfo.IME_ACTION_SEND)
  }

  accentColor.map(_.color).onUi(cursorEditText.setAccentColor)

  private lazy val transformer = TextTransform.get(ContextUtils.getString(R.string.single_image_message__name__font_transform))

  def isGroupMsgOnlyShow(conversationData: ConversationData, isManager: Boolean): Boolean = {
    ((conversationData.convType == ConversationType.Group || conversationData.convType == ConversationType.ThousandsGroup)
      && (!isManager && conversationData.msg_only_to_manager))
  }

  (for {
    eph <- controller.isEphemeral
    av <- controller.convAvailability
    name <- controller.conv.map(_.displayName)
    isManager <- convController.currentIsGroupCreateOrManager
    isGroupMsgOnly <- controller.conv.map(data => isGroupMsgOnlyShow(data, isManager))
    accentColor <- accentColorController.accentColor.map(_.color)
  } yield (eph, av, name, isGroupMsgOnly, accentColor)).onUi {
    case (true, av, _, _, accentColor) =>
      hintView.setText(getString(R.string.cursor__ephemeral_message))
      AvailabilityView.displayLeftOfText(hintView, av, defaultHintTextColor)
      if (av == Availability.None) {
        hintView.setTextColor(accentColor)
      }
    case (false, av, name, _, _) if av != Availability.None =>
      val transformedName = transformer.transform(name.str.split(' ')(0)).toString
      hintView.setText(getString(AvailabilityView.viewData(av).textId, transformedName))
      AvailabilityView.displayLeftOfText(hintView, av, defaultHintTextColor)
    case (_, av, _, true, accentColor) =>
      hintView.setText(getString(R.string.conversation_msg_only_to_manager_show))
      AvailabilityView.displayLeftOfText(hintView, av, defaultHintTextColor)
      hintView.setTextColor(accentColor)
    case _ =>
      hintView.setText(getString(R.string.cursor__type_a_message))
      AvailabilityView.hideAvailabilityIcon(hintView)
      hintView.setTextColor(defaultHintTextColor)
  }

  // allows the controller to "empty" the text field if necessary by resetting the signal.
  // specifying the source guards us from an infinite loop of the view and controller updating each other
  controller.enteredText {
    case (CursorText(text, mentions), EnteredTextSource.FromController) if text != cursorEditText.getText.toString => setText(text, mentions)
    case _ =>
  }

  (controller.isEditingMessage.zip(controller.enteredText) map {
    case (editing, (text, _)) => !editing && text.isEmpty
  }).onUi {
    hintView.setVisible
  }

  controller.convIsActive.onUi(this.setVisible)

  controller.onMessageSent.onUi(_ => setText(CursorText.Empty))

  controller.isEditingMessage.onChanged.onUi {
    case false => setText(CursorText.Empty)
    case true =>
      controller.editingMsg.head foreach {
        case Some(msg) => setText(msg.contentString, msg.mentions)
        case _ => // ignore
      }
  }

  controller.onEditMessageReset.onUi { _ =>
    controller.editingMsg.head.map {
      case Some(msg) => setText(msg.contentString, msg.mentions)
      case _ =>
    }(Threading.Ui)
  }

  private var isForbidden = false

  override def onInterceptTouchEvent(ev: MotionEvent): Boolean = {
    if (isForbidden) {
      true
    } else {
      super.onInterceptTouchEvent(ev)
    }
  }

  controller.onCursorItemClick {
    case CursorMenuItem.Mention =>
      mentionQuery.head.map {
        case None =>
          val sel = cursorEditText.getSelectionStart
          val mentionSymbol = if (cursorEditText.getText.length() == 0 || cursorEditText.getText.charAt(Math.max(sel - 1, 0)).isWhitespace) "@" else " @"

          cursorEditText.getEditableText.insert(sel, mentionSymbol)
        case _ =>
      }(Threading.Ui)
    case _ =>
  }

  def enableMessageWriting(): Unit = cursorEditText.requestFocus

  def setCallback(callback: CursorCallback) = controller.cursorCallback = Option(callback)

  def setText(cursorText: CursorText): Unit = {
    val color = accentColor.map(_.color).currentValue.getOrElse(Color.BLUE)
    var offset = 0
    var text = cursorText.text
    var mentionSpans = Seq.empty[(CursorMentionSpan, Int, Int)]
    cursorText.mentions.sortBy(_.start).foreach { case Mention(uid, mStart, mLength) =>
      val tStart = mStart + offset
      val tEnd = mStart + mLength + offset
      val mentionText = text.substring(tStart, tEnd)

      text = text.substring(0, tStart) + CursorMentionSpan.PlaceholderChar + text.substring(tEnd)
      mentionSpans = mentionSpans :+ (CursorMentionSpan(uid.get, mentionText, color), tStart, tStart + CursorMentionSpan.PlaceholderChar.length)

      offset = offset + CursorMentionSpan.PlaceholderChar.length - mLength
    }

    cursorEditText.setText(text)
    mentionSpans.foreach { case (span, start, end) =>
      cursorEditText.getEditableText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    controller.enteredText ! (getText, EnteredTextSource.FromView)
    cursorEditText.setSelection(cursorEditText.getEditableText.length())
  }

  def setText(text: String, mentions: Seq[Mention]): Unit = setText(CursorText(text, mentions))

  def insertText(text: String): Unit =
    cursorEditText.getEditableText.insert(cursorEditText.getSelectionStart, text)

  def hasText: Boolean = !TextUtils.isEmpty(cursorEditText.getText.toString)

  def getText: CursorText = {
    var offset = 0
    var cursorText = cursorEditText.getEditableText.toString
    var mentions = Seq.empty[Mention]
    CursorMentionSpan.getMentionSpans(cursorEditText.getEditableText).sortBy(_._2).foreach {
      case (span, s, e) =>
        val spanLength = e - s
        val mentionLength = span.text.length

        cursorText = cursorText.substring(0, s + offset) + span.text + cursorText.substring(e + offset)
        mentions = mentions :+ Mention(Some(span.userId), s + offset, mentionLength)

        offset = offset + mentionLength - spanLength
    }
    CursorText(cursorText, mentions)
  }

  def setConversation(): Unit = {
    enableMessageWriting()
    controller.editingMsg ! None
    controller.secondaryToolbarVisible ! false
  }

  def isEditingMessage: Boolean = controller.isEditingMessage.currentValue.contains(true)

  def closeEditMessage(animated: Boolean): Unit = controller.editingMsg ! None

  def onExtendedCursorClosed(): Unit = {
    resetSwitchOnButton()
    controller.keyboard.mutate {
      case KeyboardState.ExtendedCursor(_) => KeyboardState.Hidden
      case state => state
    }
  }

  def resetSwitchOnButton(): Unit ={
    if(switchButtonOn!=null) {
      switchButtonOn.menuItem ! Some(CursorMenuItem.LineUp)
      switchButtonOn.initTextColor()
    }
  }

  private val countDownTime = Signal(0L)

  private var blockEndTime = 0L
  private var blockTimeout: Signal[Option[FiniteDuration]] = countDownTime.flatMap { _ =>
    blockEndTime match {
      case time@_
        if LocalInstant.ofEpochSecond(time) >= LocalInstant.Now =>
        ClockSignal(1.second) map { now =>
          Some(now.until(Instant.ofEpochSecond(time)).asScala).filterNot(_.isNegative)
        }
      case _ => Signal const Option.empty[FiniteDuration]
    }
  }

  private val MINUTE_SECOND = 60
  private val HOUR_SECOND = MINUTE_SECOND * 60
  private val DAY_SECOND = HOUR_SECOND * 24

  (for {
    time <- blockTimeout
  } yield time).onUi { tempTime =>
    if (tempTime.isEmpty) {
      forbiddenTipsTextView.setText(R.string.conversation_detail_settings_forbidden_state)
    } else {
      var forbiddenSecond = blockEndTime - Instant.now().getEpochSecond

      val contentStr = new StringBuilder()

      if (forbiddenSecond / DAY_SECOND > 0) {
        contentStr.append(forbiddenSecond / DAY_SECOND)
        contentStr.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_day))
        forbiddenSecond = forbiddenSecond % DAY_SECOND
      }

      if (forbiddenSecond / HOUR_SECOND > 0) {
        contentStr.append(s"${forbiddenSecond / HOUR_SECOND}")
        contentStr.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_hour))
        forbiddenSecond = forbiddenSecond % HOUR_SECOND
      }

      if (forbiddenSecond > 0 && forbiddenSecond < 60) {
        forbiddenSecond = forbiddenSecond + MINUTE_SECOND
      }

      if (forbiddenSecond / MINUTE_SECOND > 0) {
        contentStr.append(s"${forbiddenSecond / MINUTE_SECOND}")
        contentStr.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_minute))
      } else if (forbiddenSecond > 0) {
        contentStr.append("1")
        contentStr.append(getResources.getString(R.string.conversation_detail_settings_forbidden_option_time_minute))
      }

      forbiddenTipsTextView.setText(getResources.getString(R.string.conversation_detail_settings_forbidden_state2, contentStr))
    }
  }


  def changeForbidden(isForbidden: Boolean, endTime: Long = 0L): Unit = {
    this.isForbidden = isForbidden
    this.blockEndTime = endTime

    if (isForbidden) {
      forbiddenTipsTextView.setVisibility(View.VISIBLE)
      cursorEditText.setText("")
      switchButtonOff.performClick()

      countDownTime ! endTime
    } else {
      forbiddenTipsTextView.setVisibility(View.GONE)
    }
  }

  override def onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int): Unit = {
    super.onLayout(changed, l, t, r, b)
    controller.cursorWidth ! (r - l)
  }
}

object CursorView {

  import CursorMenuItem._

  private val MainCursorItemsEmptyConversationData = Seq(Camera, VideoMessage, Sketch, Emoji, File, Ping, More)
  private val SecondaryCursorItemsEmptyConversationData = Seq(Location, Dummy, Dummy, Dummy, Dummy, Dummy, Less)

  private val MainCursorItemsOneToOne = Seq(Camera, VideoMessage, Sketch, Emoji, File, Ping, More)
  private val SecondaryCursorItemsOneToOne = Seq(Location, Dummy, Dummy, Dummy, Dummy, Dummy, Less)

  private val MainCursorItemsGroup = Seq(Camera, VideoMessage, Sketch, Emoji, File, Ping, More)
  private val SecondaryCursorItemsGroup = Seq(Location, Dummy, Dummy, Dummy, Dummy, Dummy, Less)

  private val MainCursorItemsThousandsGroup = Seq(Camera, VideoMessage, Sketch, Emoji, Location, File, Dummy)
  private val SecondaryCursorItemsThousandsGroup = Seq(Dummy, Dummy, Dummy, Dummy, Dummy, Dummy, Less)
}
