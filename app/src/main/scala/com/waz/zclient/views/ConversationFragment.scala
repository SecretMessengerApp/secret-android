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
package com.waz.zclient.views

import android.Manifest.permission.{CAMERA, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE}
import android.animation.{Animator, AnimatorListenerAdapter, ObjectAnimator, ValueAnimator}
import android.content.{Context, Intent}
import android.os.{Bundle, Handler}
import android.provider.MediaStore
import android.text.TextUtils
import android.util.TypedValue
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.{View, _}
import android.widget.{ActionMenuView => _, Toolbar => _, _}
import androidx.annotation.Nullable
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.jsy.common.acts.{CursorImageSelectActivity, GroupNoticeActivity, SelectChatBackgroundActivity}
import com.jsy.common.model._
import com.jsy.common.model.circle.LocalMedia
import com.jsy.common.popup.GroupNoticePopupWindow
import com.jsy.common.utils.image.Bmp
import com.jsy.common.utils.rxbus2.{RxBus, Subscribe, ThreadMode}
import com.jsy.common.utils.{ScreenShotListenManager, _}
import com.jsy.common.view.ConversationBackgroundLayout
import com.jsy.res.utils.ViewUtils
import com.waz.api._
import com.waz.bitmap.BitmapUtils
import com.waz.content.GlobalPreferences
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE.verbose
import com.waz.model.ConversationData.ConversationType
import com.waz.model.{AccentColor, MessageContent => _, _}
import com.waz.permissions.PermissionsService
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.RawAssetInput
import com.waz.service.assets.AssetService.RawAssetInput.{BitmapInput, UriInput}
import com.waz.threading.{CancellableFuture, Threading}
import com.waz.utils.events.{EventStreamWithAuxSignal, Signal}
import com.waz.utils.wrappers.URI
import com.waz.utils.{returning, returningF}
import com.waz.zclient.Intents.ShowDevicesIntent
import com.waz.zclient.calling.controllers.{CallController, CallStartController}
import com.waz.zclient.camera.controllers.GlobalCameraController
import com.waz.zclient.common.controllers.global.KeyboardController
import com.waz.zclient.common.controllers.{ScreenController, ThemeController, UserAccountsController}
import com.waz.zclient.controllers.camera.ICameraController
import com.waz.zclient.controllers.confirmation.{ConfirmationCallback, ConfirmationRequest, IConfirmationController}
import com.waz.zclient.controllers.drawing.IDrawingController
import com.waz.zclient.controllers.globallayout.{IGlobalLayoutController, KeyboardVisibilityObserver}
import com.waz.zclient.controllers.navigation.{INavigationController, NavigationControllerObserver, Page}
import com.waz.zclient.controllers.singleimage.{ISingleImageController, SingleImageObserver}
import com.waz.zclient.controllers.userpreferences.IUserPreferencesController
import com.waz.zclient.conversation.ConversationController.ConversationChange
import com.waz.zclient.conversation._
import com.waz.zclient.conversation.toolbar.AudioMessageRecordingView
import com.waz.zclient.conversationlist.ConversationListController
import com.waz.zclient.cursor.CursorController.KeyboardState
import com.waz.zclient.cursor._
import com.waz.zclient.drawing.DrawingFragment.Sketch
import com.waz.zclient.log.LogUI._
import com.waz.zclient.messages._
import com.waz.zclient.messages.parts.InviteMembersConfirmTypePartView
import com.waz.zclient.pages.extendedcursor.ExtendedCursorContainer
import com.waz.zclient.pages.extendedcursor.image.CursorImagesLayout
import com.waz.zclient.pages.extendedcursor.voicefilter.{VoiceFilterController, VoiceFilterLayout, VoiceFilterRecordingLayout}
import com.waz.zclient.pages.main.conversation.{AssetIntentsManager, MessageStreamAnimation}
import com.waz.zclient.pages.main.conversationlist.ConversationListAnimation
import com.waz.zclient.pages.main.conversationpager.controller.{ISlidingPaneController, SlidingPaneObserver}
import com.waz.zclient.pages.main.profile.camera.CameraContext
import com.waz.zclient.pages.main.{ImagePreviewCallback, ImagePreviewLayout}
import com.waz.zclient.participants.ParticipantsController
import com.waz.zclient.participants.fragments.SingleParticipantFragment
import com.waz.zclient.ui.animation.HeightEvaluator
import com.waz.zclient.ui.animation.interpolators.penner.Expo
import com.waz.zclient.ui.cursor.CursorMenuItem
import com.waz.zclient.ui.utils.{ColorUtils, KeyboardUtils}
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils._
import com.waz.zclient.views.e2ee.ShieldView
import com.waz.zclient.{R, _}
import org.json.JSONArray
import org.threeten.bp.Instant

import java.util
import scala.collection.JavaConverters
import scala.collection.immutable.ListSet
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class ConversationFragment extends FragmentHelper
  with OnConversationMemsunUpdateListener
  with CursorImagesLayout.MultipleImageSendCallback
  with DerivedLogTag {

  import ConversationFragment._
  import Threading.Implicits.Ui

  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val messagesController = inject[MessagesController]
  private lazy val screenController = inject[ScreenController]
  private lazy val permissions = inject[PermissionsService]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val keyboardController = inject[KeyboardController]
  private lazy val errorsController = inject[ErrorsController]
  private lazy val callController = inject[CallController]
  private lazy val callStartController = inject[CallStartController]
  private lazy val accountsController = inject[UserAccountsController]
  private lazy val globalPrefs = inject[GlobalPreferences]
  private lazy val replyController = inject[ReplyController]

  //TODO remove use of old java controllers
  private lazy val globalLayoutController = inject[IGlobalLayoutController]
  private lazy val navigationController = inject[INavigationController]
  private lazy val singleImageController = inject[ISingleImageController]
  private lazy val slidingPaneController = inject[ISlidingPaneController]
  private lazy val userPreferencesController = inject[IUserPreferencesController]
  private lazy val cameraController = inject[ICameraController]
  private lazy val confirmationController = inject[IConfirmationController]
  private lazy val convListController = inject[ConversationListController]
  private lazy val cursorController = inject[CursorController]

  private var subs = Set.empty[com.waz.utils.events.Subscription]

  private val previewShown = Signal(false)
  private lazy val convChange = convController.convChanged.filter {
    _.to.isDefined
  }
  private lazy val advisoryAndReadStatus = convController.currentConv.map { conv =>
    (conv.advisory, conv.advisory_is_read)
  }

  lazy val lastReportNoticeMessage = for {
    convId <- convController.currentConvId
    lastMessage <- convListController.lastReportNoticeMessage(convId)
  } yield {
    lastMessage
  }

  private lazy val cancelPreviewOnChange = new EventStreamWithAuxSignal(convChange, previewShown)

  private lazy val draftMap = inject[DraftMap]

  private var assetIntentsManager: Option[AssetIntentsManager] = None

  private val handler = new Handler() {}

  private var loadingIndicatorView: LoadingIndicatorView = _



  private var gtvLinedown112: View = _

  private var cvJumpToLatestMessage: View = _

  private var llBottomCursorParent: View = _
  private var typingIndicatorView: TypingIndicatorView = _

  private var containerPreview: ViewGroup = _
  private var cursorView: CursorView = _

  private lazy val mentionCandidatesAdapter = new MentionCandidatesAdapter()

  private var bgView:ConversationBackgroundLayout=_
  private var rootView: View = _
  private var audioMessageRecordingView: AudioMessageRecordingView = _
  private var extendedCursorContainer: ExtendedCursorContainer = _
  private var toolbarTitle: TextView = _
  //  private var llControlls: View = _
  //  private var more: View = _
  //  private var atvMore: View = _
  private var listView: MessagesListView = _

  private var toolbar: Toolbar = _

  private var messagesOpacity: View = _
  private var mentionsList: RecyclerView = _
  private var replyView: ReplyView = _
  private var groupNoticeLayout: Option[View] = None
  private var reportNoticeLayout: Option[View] = None
  private var groupNoticeRoot: Option[View] = None

  private var screenShotListenManager : ScreenShotListenManager = _

  private var showReplyAnimator:ValueAnimator =_
  private var hideReplyAnimator:ValueAnimator=_
  private val replyViewHeight=ScreenUtils.dip2px(ZApplication.getInstance(),52f)

  private val blockTimeRefreshSignal = Signal(0L)
  private lazy val blockTimeRunnable = new Runnable {
    override def run(): Unit = {
      if (getActivity != null && getActivity.isFinishing) {
        handler.removeCallbacks(this)
      }
      blockTimeRefreshSignal ! System.currentTimeMillis()
    }
  }

  private val MSG_COUNT_SHOW_MORE = 20

  var container: Container = _

  private val currentUser = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) => account.id }

  private def showMentionsList(visible: Boolean): Unit = {

    convController.currentConv.currentValue.foreach {
      conversationdata =>
        mentionsList.setVisible(visible && conversationdata.add_friend)
        messagesOpacity.setVisible(visible && conversationdata.add_friend)
    }
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation =
    if (nextAnim == 0 || getParentFragment == null)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_in ||
      nextAnim == R.anim.fragment_animation_swap_profile_conversation_tablet_out) new MessageStreamAnimation(
      enter,
      getInt(R.integer.wire__animation__duration__medium),
      0,
      getOrientationDependentDisplayWidth - getDimenPx(R.dimen.framework__sidebar_width)
    )
    else if (enter) new ConversationListAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_long),
      getInt(R.integer.framework_animation_duration_medium),
      false,
      1f
    )
    else new ConversationListAnimation(
      0,
      getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
      enter,
      getInt(R.integer.framework_animation_duration_medium),
      0,
      false,
      1f
    )


  private def updateNavigationMenus(conversationData: ConversationData): Unit = {
    if (conversationData != null) {
      if (rootView != null) {

        val audioId = R.id.action_audio_call
        val videoId = R.id.action_video_call
        val closeId = R.id.action_close
        val audioMenu = toolbar.getMenu.findItem(audioId)
        val videoMenu = toolbar.getMenu.findItem(videoId)
        val closeMenu = toolbar.getMenu.findItem(closeId)

        audioMenu.setVisible(false)
        videoMenu.setVisible(false)
        closeMenu.setVisible(false)

        if (conversationData.isServerNotification) {

        }else if (conversationData.convType == IConversation.Type.GROUP) {
          audioMenu.setVisible(true)
        } else if (conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
          closeMenu.setVisible(false)
        } else {
          if (conversationData.convType == IConversation.Type.ONE_TO_ONE) {
            participantsController.otherParticipant.map(Some(_)).onUi {
              case Some(userData) =>
                audioMenu.setVisible(true)
                videoMenu.setVisible(true)
              case _ =>
            }
          } else {
          }
        }
      } else {

      }
    } else {

    }
  }


  private def onCurrentConversationHasChanged(conversationData: ConversationData, isFromCreateView: Boolean): Unit = {

    showGlyphItems(conversationData)
    updateNavigationMenus(conversationData)
    if (conversationData != null) {
      updateTranscriptModel()
      updateChatBackground(conversationData)
      setInnerMarginPadding(conversationData, false, true)
      showAndHideToolBarDelay()
      showNormalConversation()
    }
  }

  override def onCreate(@Nullable savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    assetIntentsManager = Option(new AssetIntentsManager(getActivity, assetIntentsManagerCallback))
    screenShotListenManager = ScreenShotListenManager.newInstance(getContext)
    zms.flatMap(_.errors.getErrors).onUi {
      _.foreach(handleSyncError)
    }

    convController.currentConvName.onUi { displayNameOrName =>
      updateTitle(displayNameOrName)
    }

    convController.convChanged.onUi { conversationCHange =>
      val fromConvId = conversationCHange.from
      val toConvId = conversationCHange.to


      convController.currentConv.currentValue.foreach { conversationData =>
        if (rootView != null) {
          onCurrentConversationHasChanged(conversationData, false)
        }
      }
    }

    cancelPreviewOnChange.onUi {
      case (change, Some(true)) if !change.noChange => imagePreviewCallback.onCancelPreview()
      case _ =>
    }

    convChange.onUi {
      case ConversationChange(from, Some(to), _) =>
        CancellableFuture.delay(getInt(R.integer.framework_animation_duration_short).millis).map { _ =>
          convController.getConversation(to).map {
            case Some(toConv) =>
              if (rootView != null) {
                from.foreach { id => draftMap.set(id, cursorView.getText) }
                if (toConv.convType != ConversationType.WaitForConnection) {
                  keyboardController.hideKeyboardIfVisible()
                  loadingIndicatorView.hide()
                  cursorView.enableMessageWriting()

                  from.filter(_ != toConv.id).foreach { id =>

                    cursorView.setVisible(toConv.isActive)
                    draftMap.get(toConv.id).map { draftText =>
                      cursorView.setText(draftText)
                      cursorView.setConversation()
                    }
                    audioMessageRecordingView.hide()
                  }
                  // TODO: ConversationScreenController should listen to this signal and do it itself
                  extendedCursorContainer.close(true)
                }
              }
            case None =>
          }
        }

      case _ =>
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewGroup: ViewGroup, savedInstanceState: Bundle): View = {
    verbose(l"==onCreateView===")
    rootView = inflater.inflate(R.layout.fragment_conversation, viewGroup, false)

    if (savedInstanceState != null) previewShown ! savedInstanceState.getBoolean(SAVED_STATE_PREVIEW, false)

    bgView=rootView.findViewById(R.id.rl_bg)
    containerPreview = rootView.findViewById(R.id.fl__conversation_overlay)
    cvJumpToLatestMessage = rootView.findViewById[View](R.id.cvJumpToLatestMessage)
    // Recording audio messages
    audioMessageRecordingView = rootView.findViewById[AudioMessageRecordingView](R.id.amrv_audio_message_recording)

    gtvLinedown112 = rootView.findViewById[View](R.id.gtvLinedown112)
    extendedCursorContainer = rootView.findViewById[ExtendedCursorContainer](R.id.ecc__conversation)
    // invisible footer to scroll over inputfield
    returningF(new FrameLayout(getActivity)) { footer: FrameLayout =>
      footer.setLayoutParams(
        new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getDimenPx(R.dimen.cursor__list_view_footer__height))
      )
    }

    returningF(rootView.findViewById(R.id.sv__conversation_toolbar__verified_shield)) { view: ShieldView =>
      view.setVisible(false)
    }

    messagesOpacity = rootView.findViewById[View](R.id.mentions_opacity)
    mentionsList = rootView.findViewById[RecyclerView](R.id.mentions_list)
    messagesOpacity.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        showMentionsList(false)
      }
    })


    loadingIndicatorView = rootView.findViewById[LoadingIndicatorView](R.id.lbv__conversation__loading_indicator)

    toolbar = rootView.findViewById(R.id.t_conversation_toolbar)
    toolbar.inflateMenu(R.menu.conversation_header_menu_all)

    toolbarTitle = rootView.findViewById[TextView](R.id.tv__conversation_toolbar__title)
    toolbarTitle.setMaxWidth(getResources.getDisplayMetrics.widthPixels / 4)

    replyView = rootView.findViewById[ReplyView](R.id.reply_view)
    replyView.setOnClose(replyController.clearMessageInCurrentConversation())

    listView = rootView.findViewById[MessagesListView](R.id.messages_list_view)
    llBottomCursorParent = rootView.findViewById[View](R.id.llBottomCursorParent)
    typingIndicatorView = rootView.findViewById[TypingIndicatorView](R.id.tiv_typing_indicator_view)

    cvJumpToLatestMessage.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        if (listView.getAdapter.getItemCount > 0) {
          listView.scrollToBottom()
        }
      }
    })

    listView.setOnTouchListener(new View.OnTouchListener() {
      override def onTouch(view: View, motionEvent: MotionEvent): Boolean = {
        motionEvent.getAction match {
          case MotionEvent.ACTION_DOWN =>

          case MotionEvent.ACTION_MOVE =>
            if (!isListMoving) {
              handler.removeCallbacks(showToolBarRunnable)
              isListMoving = true
              hideToolbar()
              hideNotice()
            }
          case MotionEvent.ACTION_UP
               | MotionEvent.ACTION_OUTSIDE
               | MotionEvent.ACTION_CANCEL =>
            isListMoving = false
            handler.removeCallbacks(showToolBarRunnable)
            handler.postDelayed(showToolBarRunnable, HIDE_SHOW_TOOLBAR_INVERAL)
          case _ =>
          // scala.MatchError: 261 (of class java.lang.Integer)
        }
        false
      }
    })


    listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
      override def onScrollStateChanged(recyclerView: RecyclerView, newState: Int): Unit = {
        super.onScrollStateChanged(recyclerView, newState)
        if (extendedCursorContainer.isExpanded) {
          extendedCursorContainer.close(true)
        }
      }


      override def onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int): Unit = {
        super.onScrolled(recyclerView, dx, dy)
        val first = listView.layoutManager.findFirstVisibleItemPosition()
        if (first > MSG_COUNT_SHOW_MORE) {
          cvJumpToLatestMessage.setVisibility(View.VISIBLE)
        } else {
          cvJumpToLatestMessage.setVisibility(View.GONE)
        }
      }
    })

    cursorView = rootView.findViewById[CursorView](R.id.cv__cursor)

    cursorView.cursorEditText.setOnTouchListener(new View.OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = {
        if (event.getAction == MotionEvent.ACTION_UP && extendedCursorContainer.isExpanded) {
          lockContentViewHeight()
          extendedCursorContainer.close(true)
          KeyboardUtils.showSoftInput(cursorView.cursorEditText)
          unlockContentViewHeight()
        }
        false
      }
    })

    mentionCandidatesAdapter.onUserClicked.onUi { info =>
      cursorView.accentColor.head.foreach { ac =>
        cursorView.createMention(info.id, info.name, cursorView.cursorEditText, cursorView.cursorEditText.getSelectionStart, ac.color)
      }
    }

    convController.onUserLongClicked.onUi {
      info =>
        cursorView.accentColor.head.foreach { ac =>
          cursorView.createLongMention(info.id, info.name, cursorView.cursorEditText, cursorView.cursorEditText.getSelectionStart, ac.color)
        }
    }

    cursorController.keyboard.on(Threading.Ui) {
      case KeyboardState.Shown               =>
        cursorCallback.hideExtendedCursor(false)
      case KeyboardState.Hidden              =>
        cursorCallback.hideExtendedCursor(false)
      case KeyboardState.EmojiHidden         =>
        lockContentViewHeight()
        cursorCallback.hideExtendedCursor(true)
        KeyboardUtils.showSoftInput(cursorView.cursorEditText)
        unlockContentViewHeight()
      case KeyboardState.ExtendedCursor(tpe) =>
        permissions.requestAllPermissions(CursorController.keyboardPermissions(tpe)).map {
          case true => {
            if (KeyboardUtils.isKeyboardVisible(getActivity)) {
              lockContentViewHeight()
              cursorCallback.openExtendedCursor(tpe)
              unlockContentViewHeight()
            } else {
              cursorCallback.openExtendedCursor(tpe)
            }
          }
          case _    =>
            showToast(R.string.conversation_detail_check_permissions)
            cursorController.keyboard ! KeyboardState.Hidden
        }(Threading.Ui)
    }

    handler.postDelayed(new Runnable {
      override def run(): Unit = {
        if (toolbar != null && toolbar.getMenu == null || toolbar.getMenu.size() == 0) {
          convController.currentConv.currentValue.foreach { conversationData =>
            updateNavigationMenus(conversationData)
          }
        }
      }
    }, 80)
    RxBus.getDefault.register(this)
    rootView
  }

  private var groupNoticeShowAnim: ObjectAnimator = _
  private var groupNoticeHideAnim: ObjectAnimator = _

  def noticeIsVisible(): Boolean = {
    (groupNoticeLayout.exists(_.getVisibility == View.VISIBLE)
      || reportNoticeLayout.exists(_.getVisibility == View.VISIBLE))
  }

  private def lockContentViewHeight(): Unit = {
    val contentLayoutParams = listView.getLayoutParams
    contentLayoutParams.height = listView.getHeight
  }

  private def unlockContentViewHeight(): Unit = {
    handler.postDelayed(new Runnable {
      override def run(): Unit = {
        val layoutParams = listView.getLayoutParams
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
      }
    }, 200L)
  }

  private def showNotice(): Unit =
    groupNoticeRoot.foreach { viewRoot =>
      val value = Option(viewRoot.getTag(R.id.conversation_root_notice_flag)).map(_.toString).fold(false)(_.toBoolean)
      if (value && noticeIsVisible) {
        if (viewRoot.getVisibility != View.VISIBLE) {
          if (groupNoticeHideAnim != null && groupNoticeHideAnim.isRunning) {
            groupNoticeHideAnim.cancel()
          }
          if (groupNoticeShowAnim != null && groupNoticeShowAnim.isRunning) {

          } else {
            if (groupNoticeShowAnim == null) {
              groupNoticeShowAnim = ObjectAnimator.ofFloat(viewRoot, "alpha", 0, 1)
              groupNoticeShowAnim.setDuration(ANIM_DURATION)
            }
            viewRoot.setVisibility(View.VISIBLE)
            groupNoticeShowAnim.start()
          }
        }
      }
    }

  private def hideNotice(): Unit =
    groupNoticeRoot.foreach { viewRoot =>
      val value = Option(viewRoot.getTag(R.id.conversation_root_notice_flag)).map(_.toString).fold(false)(_.toBoolean)
      if (value && noticeIsVisible) {
        if (viewRoot.getVisibility == View.VISIBLE) {
          if (groupNoticeShowAnim != null && groupNoticeShowAnim.isRunning) {
            groupNoticeShowAnim.cancel()
          }

          if (groupNoticeHideAnim != null && groupNoticeHideAnim.isRunning) {
          } else {
            if (groupNoticeHideAnim == null) {
              groupNoticeHideAnim = ObjectAnimator.ofFloat(viewRoot, "alpha", 1, 0).setDuration(ANIM_DURATION)
              groupNoticeHideAnim.addListener(new AnimatorListenerAdapter() {
                override def onAnimationCancel(animation: Animator): Unit = {
                  viewRoot.setVisibility(View.GONE)
                }

                override def onAnimationEnd(animation: Animator): Unit = {
                  viewRoot.setVisibility(View.GONE)
                }
              })
            }
            groupNoticeHideAnim.start()
          }
        }
      }
    }

  override def onViewCreated(view: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    inject[Signal[AccentColor]].map(_.color) { c =>
      loadingIndicatorView.setColor(c)
      extendedCursorContainer.setAccentColor(c)
    }

    if (convController.currentConv.currentValue.isEmpty) {
      onCurrentConversationHasChanged(null, true)
    } else {
      convController.currentConv.currentValue.foreach { conversationData =>
        onCurrentConversationHasChanged(conversationData, true)
      }
    }

    val subscription = for {
      currentTime <- blockTimeRefreshSignal
      createUserId <- convController.currentConv.map(_.creator)
      groupBlockTime <- convController.currentConv.map(_.block_time)
      singleBlockTime <- convController.currentConv.map(_.single_block_time)
      orator <- convController.currentConv.map(_.orator)
    } yield (createUserId, groupBlockTime, singleBlockTime, currentTime, orator)

    subscription.onUi({
      case (userId, groupBlockTime, singleBlockTime, _, orator) if !SpUtils.getUserId(getContext).equalsIgnoreCase(userId.str) =>
        val groupTime = Try {
          groupBlockTime.get.toLong
        }.getOrElse(0L)

        val singleTime = Try {
          singleBlockTime.get.toLong
        }.getOrElse(0L)

        if (groupTime == 0L) {
          if (singleTime == 0L) {
            Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(true))
            cursorView.changeForbidden(false)
          } else if (singleTime == -1L || singleTime - Instant.now().getEpochSecond > 0) {
            Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(false))
            cursorView.changeForbidden(true, singleTime)

            cursorView.closeEditMessage(false)
            keyboardController.hideKeyboardIfVisible()

            if (extendedCursorContainer.isExpanded) {
              extendedCursorContainer.close(true)
            }

            val spaceTime = singleTime - Instant.now().getEpochSecond
            if (spaceTime > 0) {
              handler.removeCallbacks(blockTimeRunnable)
              handler.postDelayed(blockTimeRunnable, spaceTime * 1000)
            }
          } else {
            Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(true))
            cursorView.changeForbidden(false)
          }
        } else if (groupTime == -1L) {
          if (orator.nonEmpty && orator.exists(_.str.equalsIgnoreCase(SpUtils.getUserId(getContext)))) {
            Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(true))
            cursorView.changeForbidden(false)
          } else {
            Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(false))
            cursorView.changeForbidden(true)

            cursorView.closeEditMessage(false)
            keyboardController.hideKeyboardIfVisible()

            if (extendedCursorContainer.isExpanded) {
              extendedCursorContainer.close(true)
            }
          }

        } else {
          Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(true))
          cursorView.changeForbidden(false)
        }
      case _ =>
        Option(toolbar.getMenu.findItem(R.id.action_audio_call)).foreach(_.setEnabled(true))
        cursorView.changeForbidden(false)
    })

    groupNoticeRoot = Option(ViewUtils.getView[ViewGroup](view, R.id.group_notice_root))
    groupNoticeRoot.foreach { viewRoot =>
      convController.currentConv.currentValue.foreach { conv =>
        if (MessageUtils.MessageContentUtils.isGroupForConversation(conv.convType)) {
          reportNoticeView(view)
          viewRoot.setTag(R.id.conversation_root_notice_flag, true)
          viewRoot.setVisibility(View.VISIBLE)
        } else {
          viewRoot.setTag(R.id.conversation_root_notice_flag, false)
          viewRoot.setVisibility(View.GONE)
        }
      }
    }

    returning(bgView.getLayoutParams){lp=>
      lp.height=ScreenUtils.getScreenHeight(getContext)
    }

    showGroupNoticeDialog()
  }

  def showGroupNoticeDialog():Unit={
    convController.currentConv.currentValue.foreach(conv =>{
      if(MainActivityUtils.isGroupConversation(conv)){
         if(conv.advisory_show_dialog){
           val advisory=conv.advisory.getOrElse("")
           if(!StringUtils.isBlank(advisory)){
             var width=ScreenUtils.getScreenWidth(getContext)-2*ScreenUtils.dip2px(getContext,25f)
             val popUpWindow = new GroupNoticePopupWindow(getActivity)
             popUpWindow.setData(conv.id.str,advisory)
             popUpWindow.setContentWidth(width)
             popUpWindow.setOnDismissListener(new PopupWindow.OnDismissListener {
               override def onDismiss(): Unit = {
                 if(container!=null){
                    container.updateAdvisoryDialogStatus(conv.id,false)
                 }
               }
             });
             popUpWindow.showAtLocation(getActivity.getWindow.getDecorView, Gravity.CENTER, 0, 0)
           }
           else{
             if(container!=null) {
               container.updateAdvisoryDialogStatus(conv.id, false)
             }
           }
         }
      }
    })
  }

  def groupNoticeView(view: View, isCanShow: Boolean = true): Unit = {
    groupNoticeLayout = Option(ViewUtils.getView[ViewGroup](view, R.id.clAdvisoryParent))
    groupNoticeLayout.foreach { viewGroup =>
      viewGroup.setVisibility(View.GONE)
      viewGroup.setTag(R.id.conversation_group_notice_flag, false)
    }

    if (isCanShow) {
      val ivAdvisoryClose = ViewUtils.getView[ImageView](view, R.id.ivAdvisoryClose)
      ivAdvisoryClose.onClick {
        convController.currentConv.currentValue.foreach { conv =>
          container.updateAdvisoryReadStatus(conv.id, true)
        }
      }

      val tvAdvisory = ViewUtils.getView[TextView](view, R.id.tvAdvisory)
      tvAdvisory.setOnClickListener(new OnClickListener {
        override def onClick(v: View): Unit = {
          convController.currentConv.currentValue.foreach { conv =>
            GroupNoticeActivity.startGroupNoticeActivitySelf(getActivity, conv.id.str)
            if (container != null) {
              container.updateAdvisoryReadStatus(conv.id, true)
            }
          }
        }
      })

      advisoryAndReadStatus.onUi { advisoryAndReadStatus =>
        val advisoryOpt = advisoryAndReadStatus._1
        val isRead = advisoryAndReadStatus._2
        advisoryOpt.fold {
          groupNoticeLayout.foreach { viewGroup =>
            viewGroup.setVisibility(View.GONE)
            viewGroup.setTag(R.id.conversation_group_notice_flag, false)
          }
        } { advisoryStr =>
          tvAdvisory.setEllipsize(TextUtils.TruncateAt.MARQUEE);
          tvAdvisory.setSelected(true);
          tvAdvisory.setText(advisoryStr)

          groupNoticeLayout.foreach { viewGroup =>
            val show = if (TextUtils.isEmpty(advisoryStr)) false else !isRead
            viewGroup.setVisible(show)
            viewGroup.setTag(R.id.conversation_group_notice_flag, show)
          }
        }
      }
    }
  }

  def reportNoticeView(view: View): Unit = {
    reportNoticeLayout = Option(ViewUtils.getView[ViewGroup](view, R.id.report_notice_layout))
    reportNoticeLayout.foreach { viewGroup =>
      viewGroup.setTag(R.id.conversation_report_notice_flag, false)
      viewGroup.setVisibility(View.GONE)
    }

    lastReportNoticeMessage.onUi { lastMessage =>
      lastMessage.fold {
        groupNoticeView(view, true)
        reportNoticeLayout.foreach { viewGroup =>
          viewGroup.setVisibility(View.GONE)
          viewGroup.setTag(R.id.conversation_report_notice_flag, false)
        }
      } { message =>
        reportNoticeLayout.foreach { viewGroup =>
          groupNoticeView(view, false)
          try {
            val groupNoticeModel: GroupNoticeModel = GroupNoticeModel.parseJson(message.contentString)
            val textStr = if (null == groupNoticeModel) "" else groupNoticeModel.msgData.text
            verbose(l"lastReportNoticeMessage.onUi { lastMessage => textStr:$textStr")
            if (TextUtils.isEmpty(textStr)) {
              view.setVisibility(View.GONE)
              view.setTag(R.id.conversation_report_notice_flag, false)

            } else {
              Option(ViewUtils.getView[TextView](view, R.id.report_content_textView)).foreach {
                textView =>
                  textView.setText(textStr)
                  textView.setSelected(true)
              }
              Option(ViewUtils.getView[ImageView](view, R.id.report_close_imageView)).foreach { closeView =>
                closeView.onClick {
                  if (container != null) {
                    convController.currentConv.currentValue.foreach { conv =>
                      container.deleteReportNoticeMessage(conv.id, message.id)
                      viewGroup.setVisibility(View.GONE)
                      viewGroup.setTag(R.id.conversation_report_notice_flag, false)
                      groupNoticeView(view, true)
                    }
                  }
                }
                viewGroup.setTag(R.id.conversation_report_notice_flag, true)
                viewGroup.setVisibility(View.VISIBLE)
              }
            }
          } catch {
            case e: Exception =>
              e.printStackTrace()
              reportNoticeLayout.foreach { viewGroup =>
                viewGroup.setVisibility(View.GONE)
                viewGroup.setTag(R.id.conversation_report_notice_flag, false)
              }
          }
        }
      }
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN) def submitTextJsonMessage(sendTextJsonMessageEntity: SendTextJsonMessageEntity): Unit = {
    if (!StringUtils.isBlank(sendTextJsonMessageEntity.msgType)) {
      sendTextJsonMessageEntity.msgType match {
        case MessageUtils.MessageContentUtils.SCREEN_SHOT =>
          if (sendTextJsonMessageEntity.waitingForConfirmation) {
            cursorView.submitTextJsonForRecipients(sendTextJsonMessageEntity.msgJson,
              uids = new JSONArray().put(SpUtils.getUserId(getContext)))
          } else {
            cursorView.submitTextJson(sendTextJsonMessageEntity.msgJson)
          }
        case _ =>
      }
    } else if (sendTextJsonMessageEntity.actionType != 0) {
      sendTextJsonMessageEntity.actionType match {
        case MessageUtils.MessageActionType.INVITE_MEMBER_REFRESH =>
          val position = listView.adapter.positionForMessage(MessageId(sendTextJsonMessageEntity.messageId))
          position match {
            case Some(pos) =>
              val childView = listView.getLayoutManager.findViewByPosition(pos)
              childView match {
                case messageView: MessageView =>
                  val listParts: Seq[MessageViewPart] = messageView.listParts
                  if (listParts.size == 1 && sendTextJsonMessageEntity.messageId.equals(messageView.msg.id.str)) {
                    listParts.headOption.get match {
                      case firstPart: InviteMembersConfirmTypePartView =>
                        firstPart.refreshInviteStatus()
                      case _ =>
                    }
                  }
              }
            case None =>
          }
        case _ =>
      }
    }
  }

  override def onDestroyView(): Unit = {
    subs.foreach(_.destroy())
    subs = Set.empty
    handler.removeCallbacks(showToolBarRunnable)
    handler.removeCallbacks(blockTimeRunnable)
    RxBus.getDefault.unregister(this)

    super.onDestroyView()
  }

  private val HIDE_SHOW_TOOLBAR_INVERAL = 1000
  private val ANIM_DURATION = 250

  private var isListMoving = false

  private var hideToolBarAnimator: ObjectAnimator = _

  private var showToolBarAnimator: ObjectAnimator = _

  def showAndHideToolBarDelay(): Unit = {
    toolbar.setVisibility(View.VISIBLE)
    handler.removeCallbacks(showToolBarRunnable)
  }



  private val showToolBarRunnable = new Runnable() {

    override def run(): Unit = {
      showToolbar()
      showNotice()
    }
  }



  private def showToolbar(): Unit = {
    if (hideToolBarAnimator != null && hideToolBarAnimator.isRunning) {
      hideToolBarAnimator.cancel()
    }
    if (toolbar.getVisibility == View.VISIBLE || (showToolBarAnimator != null && showToolBarAnimator.isRunning)) {

    } else {
      if (showToolBarAnimator == null) {
        showToolBarAnimator = ObjectAnimator.ofFloat(toolbar, "alpha", 0, 1)
        showToolBarAnimator.setDuration(ANIM_DURATION)
      }
      toolbar.setVisibility(View.VISIBLE)
      showToolBarAnimator.start()
    }
  }

  private def hideToolbar(): Unit = {
    if (showToolBarAnimator != null && showToolBarAnimator.isRunning) {
      showToolBarAnimator.cancel()
    }
    if (toolbar.getVisibility != View.VISIBLE || (hideToolBarAnimator != null && hideToolBarAnimator.isRunning)) {

    } else {
      if (hideToolBarAnimator == null) {
        hideToolBarAnimator = ObjectAnimator.ofFloat(toolbar, "alpha", 1, 0)
        hideToolBarAnimator.setDuration(ANIM_DURATION)
        hideToolBarAnimator.addListener(new AnimatorListenerAdapter() {
          override def onAnimationCancel(animation: Animator): Unit = {
            super.onAnimationCancel(animation)
            if (toolbar != null) toolbar.setVisibility(View.GONE)
          }

          override def onAnimationEnd(animation: Animator): Unit = {
            super.onAnimationEnd(animation)
            if (toolbar != null) toolbar.setVisibility(View.GONE)
          }
        })
      }
      hideToolBarAnimator.start()
    }
  }


  def showParticipant(): Unit = {
    convController.currentConv.currentValue.foreach { conversationData =>
      if (conversationData.convType == IConversation.Type.GROUP || conversationData.convType == IConversation.Type.THROUSANDS_GROUP) {
        KeyboardUtils.closeKeyboardIfShown(getActivity)
        participantsController.onShowParticipants ! None
      } else if (conversationData.convType == IConversation.Type.ONE_TO_ONE) {

        if (conversationData.isServerNotification) {
          KeyboardUtils.closeKeyboardIfShown(getActivity)
          ServerNotificationSettingsActivity.startSelf(getActivity)
        } else {
          participantsController.otherParticipant.currentValue.foreach { other =>
            KeyboardUtils.closeKeyboardIfShown(getActivity)
            participantsController.onShowParticipants ! None
          }
        }

      }
    }
  }

  override def onAttach(context: Context): Unit = {
    super.onAttach(context)
    context match {
      case tempContainer: Container =>
        container = tempContainer
      case _                        =>
    }
  }

  override def onDetach(): Unit = {
    super.onDetach()
    container = null
  }


  override def onStart(): Unit = {
    super.onStart()

    toolbarTitle.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        showParticipant()
      }
    })
    gtvLinedown112.setOnClickListener(new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        showParticipant()
      }
    })
    toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
      override def onMenuItemClick(item: MenuItem): Boolean = {
        val vid = item.getItemId
        if (vid == R.id.action_audio_call || vid == R.id.action_video_call) {
          callStartController.startCallInCurrentConv(withVideo = item.getItemId == R.id.action_video_call, forceOption = true)
          cursorView.closeEditMessage(false)
          true
        } else if (vid == R.id.action_close) {
          cursorView.closeEditMessage(false)
          getActivity.onBackPressed()
          keyboardController.hideKeyboardIfVisible()
          true
        } else {
          false
        }
      }
    })

    val navigationOnClickListener = new View.OnClickListener() {
      override def onClick(v: View): Unit = {
        cursorView.closeEditMessage(false)
        getActivity.onBackPressed()
        keyboardController.hideKeyboardIfVisible()
      }
    }

    toolbar.setNavigationOnClickListener(navigationOnClickListener)

    cursorView.setCallback(cursorCallback)
    globalLayoutController.addKeyboardHeightObserver(extendedCursorContainer)
    globalLayoutController.addKeyboardVisibilityObserver(extendedCursorContainer)
    extendedCursorContainer.setCallback(extendedCursorContainerCallback)
    navigationController.addNavigationControllerObserver(navigationControllerObserver)
    singleImageController.addSingleImageObserver(singleImageObserver)
    globalLayoutController.addKeyboardVisibilityObserver(keyboardVisibilityObserver)
    slidingPaneController.addObserver(slidingPaneObserver)

    draftMap.withCurrentDraft { draftText => if (!TextUtils.isEmpty(draftText.text)) cursorView.setText(draftText) }

    mentionsList.setAdapter(mentionCandidatesAdapter)
    mentionsList.setLayoutManager(returning(new LinearLayoutManager(getContext)) {
      _.setStackFromEnd(true)
    })


    subs += Signal(cursorView.mentionSearchResults, inject[ThemeController].currentTheme).onUi {
      case (data, theme) =>
        mentionCandidatesAdapter.setData(data, None, theme)
        mentionsList.scrollToPosition(data.size - 1)
    }

    val mentionsListShouldShow = Signal(cursorView.mentionQuery.map(_.nonEmpty), cursorView.mentionSearchResults.map(_.nonEmpty), cursorView.selectionHasMention).map {
      case (true, true, false) => true
      case _ => false
    }

    subs += mentionsListShouldShow.onUi(showMentionsList)

    subs += mentionsListShouldShow.zip(replyController.currentReplyContent).onUi {
      case (false, Some(ReplyContent(messageData, asset, senderName))) =>
        replyView.setMessage(messageData, asset, senderName)
        verbose(l"showReplyView")
        showReplyView()
      case _ =>
          verbose(l"hideReplyView")
          hideReplyView()
    }
  }

  private def showReplyView(): Unit ={
      if(!replyView.isVisible ||(hideReplyAnimator!=null && hideReplyAnimator.isRunning)) {
        if(hideReplyAnimator!=null){
          hideReplyAnimator.cancel()
        }
        if(showReplyAnimator!=null){
          showReplyAnimator.cancel()
        }
        replyView.setVisible(true)
        showReplyAnimator = ValueAnimator.ofInt(0, replyViewHeight).setDuration(200)
        showReplyAnimator.setEvaluator(new HeightEvaluator(replyView))
        showReplyAnimator.addListener(new AnimatorListenerAdapter() {
          override def onAnimationEnd(animation: Animator): Unit = {
            MainHandler.getInstance().post(new Runnable {
              override def run(): Unit = {
                KeyboardUtils.showSoftInput(cursorView.cursorEditText)
              }
            })
          }
        })
        showReplyAnimator.start()
      }
  }

  private def hideReplyView(): Unit ={
    if(replyView.isVisible) {
      if(hideReplyAnimator!=null){
        hideReplyAnimator.cancel()
      }
      hideReplyAnimator= ValueAnimator.ofInt(replyViewHeight, 0).setDuration(200)
      hideReplyAnimator.setEvaluator(new HeightEvaluator(replyView))
      hideReplyAnimator.addListener(new AnimatorListenerAdapter(){
        override def onAnimationEnd(animation: Animator): Unit = {
           replyView.setVisible(false)
        }
      })
      hideReplyAnimator.start()
    }
  }

  private def updateTitle(displayName: Name): Unit = {
    if (rootView != null && displayName != null) {
      toolbarTitle.setText(displayName.str)
    }
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    previewShown.head.foreach { isShown => outState.putBoolean(SAVED_STATE_PREVIEW, isShown) }
  }

  private var isHasScreenShotListener : Boolean = _

  private def startScreenShotListen(): Unit = {

      if (!isHasScreenShotListener && screenShotListenManager != null) {
        screenShotListenManager.setListener(new ScreenShotListenManager.OnScreenShotListener {
          override def onShot(imagePath: String): Unit = {
            convController.currentConv.currentValue.foreach {
              conversationData =>
                if (conversationData.convType == ConversationData.ConversationType.OneToOne || conversationData.convType == ConversationData.ConversationType.Group) {
                  val screenShotJson = MessageUtils.createScreenShotJson(SpUtils.getUserId(getContext))
                  val jsonMessageEntity = new SendTextJsonMessageEntity(ConversationFragment.this.getClass.getSimpleName, MessageUtils.MessageContentUtils.SCREEN_SHOT, screenShotJson.toString)
                  RxBus.getDefault.post(jsonMessageEntity)
                }
            }
          }
        })

        screenShotListenManager.startListen()
        isHasScreenShotListener = true
      }
  }

  private def stopScreenShotListen(): Unit ={
    if (isHasScreenShotListener && screenShotListenManager != null) {
      screenShotListenManager.stopListen()
      isHasScreenShotListener = false
    }
  }

  override def onPause(): Unit = {
    super.onPause()
    keyboardController.hideKeyboardIfVisible()
    audioMessageRecordingView.hide()
    if(bgView!=null){
      bgView.onPause()
    }
    stopScreenShotListen()
  }

  override def onResume(): Unit = {
    super.onResume()
    if(bgView!=null){
      bgView.onResume()
    }
    startScreenShotListen()
  }

  override def onHiddenChanged(hidden: Boolean): Unit = {
    super.onHiddenChanged(hidden)
    if(bgView != null){
      bgView.onHiddenChanged(hidden)
    }
  }

  override def setUserVisibleHint(isVisibleToUser: Boolean): Unit = {
    super.setUserVisibleHint(isVisibleToUser)
    if(null != rootView && bgView != null){
      bgView.setUserVisibleHint(isVisibleToUser)
    }
  }

  override def onStop(): Unit = {
    extendedCursorContainer.close(true)
    extendedCursorContainer.setCallback(null)
    cursorView.setCallback(null)
    KeyboardUtils.closeKeyboardIfShown(getActivity)
    toolbar.setOnClickListener(null)
    toolbar.setOnMenuItemClickListener(null)
    toolbar.setNavigationOnClickListener(null)
    globalLayoutController.removeKeyboardHeightObserver(extendedCursorContainer)
    globalLayoutController.removeKeyboardVisibilityObserver(extendedCursorContainer)
    singleImageController.removeSingleImageObserver(singleImageObserver)
    globalLayoutController.removeKeyboardVisibilityObserver(keyboardVisibilityObserver)
    slidingPaneController.removeObserver(slidingPaneObserver)
    navigationController.removeNavigationControllerObserver(navigationControllerObserver)

    if (!cursorView.isEditingMessage) draftMap.setCurrent(cursorView.getText)
    super.onStop()
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = {
    if (requestCode == MainActivityUtils.REQUEST_CODE_SELECT_CHAT_BACKGROUND) {
      convController.currentConv.currentValue.foreach { conversationData =>
        updateChatBackground(conversationData)
      }
    }
    if(requestCode==MainActivityUtils.REQUEST_CODE_IntentType_EMOJI_SETTING){
       extendedCursorContainer.updateEmojis()
    }
    assetIntentsManager.foreach {
      _.onActivityResult(requestCode, resultCode, data)
    }
  }

  private lazy val imagePreviewCallback = new ImagePreviewCallback {
    override def onCancelPreview(): Unit = {
      previewShown ! false
//      navigationController.setPagerEnabled(true)
      containerPreview
        .animate
        .translationY(getView.getMeasuredHeight)
        .setDuration(getInt(R.integer.animation_duration_medium))
        .setInterpolator(new Expo.EaseIn)
        .withEndAction(new Runnable() {
          override def run(): Unit = if (containerPreview != null) containerPreview.removeAllViews()
        })
    }

    override def onSketchOnPreviewPicture(input: RawAssetInput, source: ImagePreviewLayout.Source, method: IDrawingController.DrawingMethod): Unit = {
      screenController.showSketch ! Sketch.cameraPreview(input, method)
      extendedCursorContainer.close(true)
      onCancelPreview()
    }

    override def onSendPictureFromPreview(input: RawAssetInput, source: ImagePreviewLayout.Source): Unit = {
      convController.sendMessage(input, getActivity)
      extendedCursorContainer.close(true)
      onCancelPreview()
    }
  }

  private val assetIntentsManagerCallback = new AssetIntentsManager.Callback {
    override def onDataReceived(intentType: AssetIntentsManager.IntentType, uri: URI): Unit = intentType match {
      case AssetIntentsManager.IntentType.FILE_SHARING =>
        permissions.requestAllPermissions(ListSet(READ_EXTERNAL_STORAGE)).map {
          case true =>
            convController.sendMessage(uri, getActivity)
          case _ =>
            ViewUtils.showAlertDialog(
              getActivity,
              R.string.asset_upload_error__not_found__title,
              R.string.asset_upload_error__not_found__message,
              R.string.asset_upload_error__not_found__button,
              null,
              true
            )
        }
      case AssetIntentsManager.IntentType.GALLERY =>
        showImagePreview {
          _.setImage(uri, ImagePreviewLayout.Source.DeviceGallery)
        }
      case _ => //content://com.jsy.secret.dev.fileprovider/Secret/Android/data/com.jsy.secret.dev/cache/VID_20190321_145039.mp4
        convController.sendMessage(uri, getActivity)
        navigationController.setVisiblePage(Page.MESSAGE_STREAM, TAG)
        extendedCursorContainer.close(true)
    }

    override def openIntent(intent: Intent, intentType: AssetIntentsManager.IntentType): Unit = {

      if (MediaStore.ACTION_VIDEO_CAPTURE.equals(intent.getAction) &&
        extendedCursorContainer.getType == ExtendedCursorContainer.Type.IMAGES &&
        extendedCursorContainer.isExpanded) {
        // Close keyboard camera before requesting external camera for recording video
        extendedCursorContainer.close(true)
      }


      startActivityForResult(intent, intentType.requestCode)
      getActivity.overridePendingTransition(R.anim.camera_in, R.anim.camera_out)
    }

    override def onFailed(tpe: AssetIntentsManager.IntentType): Unit = {}

    override def onCanceled(tpe: AssetIntentsManager.IntentType): Unit = {}
  }

  private val extendedCursorContainerCallback = new ExtendedCursorContainer.Callback {
    override def onExtendedCursorClosed(lastType: ExtendedCursorContainer.Type): Unit = {
      cursorView.onExtendedCursorClosed()

      if (lastType == ExtendedCursorContainer.Type.EPHEMERAL)
        convController.currentConv.head.map {
          _.ephemeralExpiration.map { exp =>
            val eph = exp.duration.toMillis match {
              case 0 => None
              case e => Some(e.millis)
            }
            globalPrefs.preference(GlobalPreferences.LastEphemeralValue) := eph
          }
        }

      globalLayoutController.resetScreenAwakeState()
    }
  }

  private lazy val mEmojiObserve = new Observer[String] {
    override def onChanged(emoji: String): Unit = {
      cursorView.insertText(emoji)
      userPreferencesController.addRecentEmoji(emoji)
    }
  }

  private def openExtendedCursor(cursorType: ExtendedCursorContainer.Type): Unit = cursorType match {
    case ExtendedCursorContainer.Type.NONE =>
    case ExtendedCursorContainer.Type.EMOJIS =>
      extendedCursorContainer.openEmojis(getChildFragmentManager)
    case ExtendedCursorContainer.Type.EPHEMERAL =>
      cursorView.resetSwitchOnButton()
      convController.currentConv.map(_.ephemeralExpiration).head.foreach {
        case Some(globalTime@ConvExpiry(_)) =>
        case exp =>
          extendedCursorContainer.openEphemeral(new EphemeralLayout.Callback {
            override def onEphemeralExpirationSelected(expiration: Option[FiniteDuration], close: Boolean) = {
              if(close) extendedCursorContainer.close(false)
              convController.currentConv.currentValue.foreach { conversationData =>
                //val checkGroupManager = conversationData.convType == ConversationType.Group && currentUser.currentValue.head == conversationData.creator
                convController.setEphemeralExpiration(expiration, isGlobal = false)
              }
            }
          }, exp.map(_.duration))
      }
    case ExtendedCursorContainer.Type.VOICE_FILTER_RECORDING =>
      cursorView.resetSwitchOnButton()
      extendedCursorContainer.openVoiceFilter(new VoiceFilterLayout.Callback {
        override def onAudioMessageRecordingStarted(): Unit = {
          globalLayoutController.keepScreenAwake()
        }

        override def onCancel(): Unit = extendedCursorContainer.close(false)


        override def sendRecording(audioAssetForUpload: AudioAssetForUpload, appliedAudioEffect: AudioEffect, voiceFilterController: VoiceFilterController): Unit = {
          val duration = audioAssetForUpload.getDuration.toMillis
          if (duration < 1000) {
            showToast(R.string.record_time_too_short)
          } else {
            convController.sendMessage(audioAssetForUpload, getActivity)
            extendedCursorContainer.close(true)
          }
        }
      }, VoiceFilterRecordingLayout.FINITE_DURATION)
    case ExtendedCursorContainer.Type.IMAGES =>
      extendedCursorContainer.setMultipleImageSendCallback(getActivity, this)
      extendedCursorContainer.openCursorImages(new CursorImagesLayout.Callback {
        override def openCamera(): Unit = cameraController.openCamera(CameraContext.MESSAGE)

        override def openVideo(): Unit = captureVideoAskPermissions(AssetIntentsManager.IntentType.VIDEO)

        override def onGalleryPictureSelected(uri: URI): Unit = {
          previewShown ! true
          showImagePreview {
            _.setImage(uri, ImagePreviewLayout.Source.InAppGallery)
          }
        }

        override def openGallery(): Unit = {
          CursorImageSelectActivity.startSelf(getContext)
          CursorImageSelectActivity.setCallBack(ConversationFragment.this)
        }

        override def onPictureTaken(imageData: Array[Byte], isMirrored: Boolean): Unit =
          showImagePreview {
            _.setImage(imageData, isMirrored)
          }

        override def sendGalleryVideoSelected(uri: URI): Unit = {
          convController.sendMessage(uri, getActivity)
        }
      })
    case _ =>
  }


  private def captureVideoAskPermissions(`type`: AssetIntentsManager.IntentType) = for {
    _ <- inject[GlobalCameraController].releaseCamera() //release camera so the camera app can use it
    _ <- permissions.requestAllPermissions(ListSet(CAMERA, WRITE_EXTERNAL_STORAGE)).map {
      case true => assetIntentsManager.foreach(_.captureVideo())
      case false => //
    }(Threading.Ui)
  } yield {}

  private val cursorCallback = new CursorCallback {
    override def onMotionEventFromCursorButton(cursorMenuItem: CursorMenuItem, motionEvent: MotionEvent): Unit ={
      if (cursorMenuItem == CursorMenuItem.AUDIO_MESSAGE && audioMessageRecordingView.isVisible)
        audioMessageRecordingView.onMotionEventFromAudioMessageButton(motionEvent)
    }

    override def captureVideo(): Unit = {
      captureVideoAskPermissions(AssetIntentsManager.IntentType.VIDEO_CURSOR_BUTTON)
    }

    override def hideExtendedCursor(immediate: Boolean): Unit = {
      if (extendedCursorContainer.isExpanded) {
        extendedCursorContainer.close(immediate)
      }
    }

    override def onMessageSent(msg: MessageData): Unit = {
    }

    override def openExtendedCursor(tpe: ExtendedCursorContainer.Type): Unit = {
      ConversationFragment.this.openExtendedCursor(tpe)
    }

    override def onCursorClicked(): Unit = {
    }

    override def openFileSharing(): Unit = assetIntentsManager.foreach {
      _.openFileSharing()
    }

    override def onCursorButtonLongPressed(cursorMenuItem: CursorMenuItem): Unit ={
      cursorMenuItem match {
        case CursorMenuItem.AUDIO_MESSAGE =>
          callController.isCallActive.head.foreach {
            case true => showErrorDialog(R.string.calling_ongoing_call_title, R.string.calling_ongoing_call_audio_message)
            case false =>
              extendedCursorContainer.close(true)
              audioMessageRecordingView.show()
          }
        case _ => //
      }
    }

    override def expandCursorItems(): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        setInnerMarginPadding(conversationData, true, false)
      }
    }

    override def collapseCursorItems(): Unit = {
      convController.currentConv.currentValue.foreach { conversationData =>
        setInnerMarginPadding(conversationData, false, false)
      }
    }
  }

  private val navigationControllerObserver = new NavigationControllerObserver {
    override def onPageVisible(page: Page): Unit = if (page == Page.MESSAGE_STREAM) {
      cursorView.enableMessageWriting()
    }
  }

  private val singleImageObserver = new SingleImageObserver {
    override def onShowSingleImage(messageId: String): Unit = {}

    override def onHideSingleImage(): Unit = navigationController.setVisiblePage(Page.MESSAGE_STREAM, TAG)
  }

  private val keyboardVisibilityObserver = new KeyboardVisibilityObserver {
    override def onKeyboardVisibilityChanged(keyboardIsVisible: Boolean, keyboardHeight: Int, currentFocus: View): Unit =
      inject[CursorController].notifyKeyboardVisibilityChanged(keyboardIsVisible)
  }

  private def handleSyncError(err: ErrorData): Unit = err.errType match {
    case ErrorType.CANNOT_SEND_ASSET_FILE_NOT_FOUND =>
      ViewUtils.showAlertDialog(
        getActivity,
        R.string.asset_upload_error__not_found__title,
        R.string.asset_upload_error__not_found__message,
        R.string.asset_upload_error__not_found__button,
        null,
        true
      )
      errorsController.dismissSyncError(err.id)
    case ErrorType.CANNOT_SEND_ASSET_TOO_LARGE =>
      accountsController.isTeam.head.foreach { isTeam =>
        val dialog = ViewUtils.showAlertDialog(
          getActivity,
          R.string.asset_upload_error__file_too_large__title,
          R.string.asset_upload_error__file_too_large__message_default,
          R.string.asset_upload_error__file_too_large__button,
          null,
          true
        )
        dialog.setMessage(getString(R.string.asset_upload_error__file_too_large__message, s"${AssetData.maxAssetSizeInBytes(isTeam) / (1024 * 1024)}MB"))
        errorsController.dismissSyncError(err.id)
      }
    case ErrorType.RECORDING_FAILURE =>
      ViewUtils.showAlertDialog(
        getActivity,
        R.string.audio_message__recording__failure__title,
        R.string.audio_message__recording__failure__message,
        R.string.alert_dialog__confirmation,
        null,
        true
      )
      errorsController.dismissSyncError(err.id)
    case ErrorType.CANNOT_SEND_MESSAGE_TO_UNVERIFIED_CONVERSATION =>
      err.convId.foreach(onErrorCanNotSentMessageToUnverifiedConversation(err, _))
    case ErrorType.CANNOT_ADD_EXIST_USER_TO_CONVERSATION =>
      val userIds = err.users.toSet
      zms.foreach { z =>
        z.users.findUsersByIds(userIds).foreach { userDatas =>
          val names = userDatas.map(_.getShowName).mkString(getString(R.string.content__system__item_separator) + " ")
          verbose(l"handleSyncError cannot_add_exist_user_to_conversation names:${Option(names)}")
          ToastUtil.toastByString(getActivity, getString(R.string.conversation_add_exist_user, names))
          errorsController.dismissSyncError(err.id)
        }
      }
    case ErrorType.ADD_USER_TO_CONVERSATION_SUC =>
      verbose(l"handleSyncError ADD_USER_TO_CONVERSATION_SUC")
      errorsController.dismissSyncError(err.id)
      ToastUtil.toastByString(getActivity, getString(R.string.conversation_detail_add_member))
    case errType =>
  }

  private def onErrorCanNotSentMessageToUnverifiedConversation(err: ErrorData, convId: ConvId) =
    if (navigationController.getCurrentPage == Page.MESSAGE_STREAM) {
      keyboardController.hideKeyboardIfVisible()

      (for {
        self <- inject[UserAccountsController].currentUser.head
        members <- convController.loadMembers(convId)
        unverifiedUsers = (members ++ self.map(Seq(_)).getOrElse(Nil)).filter {
          !_.isVerified
        }
        unverifiedDevices <-
        if (unverifiedUsers.size == 1) Future.sequence(unverifiedUsers.map(u => convController.loadClients(u.id).map(_.filter(!_.isVerified)))).map(_.flatten.size)
        else Future.successful(0) // in other cases we don't need this number
      } yield (self, unverifiedUsers, unverifiedDevices)).map { case (self, unverifiedUsers, unverifiedDevices) =>

        val unverifiedNames = unverifiedUsers.map { u =>
          if (self.map(_.id).contains(u.id)) getString(R.string.conversation_degraded_confirmation__header__you)
          else u.displayName.str
        }

        val header =
          if (unverifiedUsers.isEmpty) getString(R.string.conversation__degraded_confirmation__header__someone)
          else if (unverifiedUsers.size == 1)
            getQuantityString(R.plurals.conversation__degraded_confirmation__header__single_user, unverifiedDevices, unverifiedNames.head)
          else getString(R.string.conversation__degraded_confirmation__header__multiple_user, unverifiedNames.tail.mkString(","), unverifiedNames.head)

        val onlySelfChanged = unverifiedUsers.size == 1 && self.map(_.id).contains(unverifiedUsers.head.id)

        val callback = new ConfirmationCallback {
          override def positiveButtonClicked(checkboxIsSelected: Boolean): Unit = {
            messagesController.retryMessageSending(err.messages)
            errorsController.dismissSyncError(err.id)
          }

          override def onHideAnimationEnd(confirmed: Boolean, cancelled: Boolean, checkboxIsSelected: Boolean): Unit =
            if (!confirmed && !cancelled) {
              if (onlySelfChanged) getContext.startActivity(ShowDevicesIntent(getActivity))
              else participantsController.onShowParticipants ! Some(SingleParticipantFragment.TagDevices)
            }

          override def negativeButtonClicked(): Unit = {}

          override def canceled(): Unit = {}
        }

        val positiveButton = getString(R.string.conversation__degraded_confirmation__positive_action)
        val negativeButton =
          if (onlySelfChanged) getString(R.string.conversation__degraded_confirmation__negative_action_self)
          else getQuantityString(R.plurals.conversation__degraded_confirmation__negative_action, unverifiedUsers.size)

        val messageCount = Math.max(1, err.messages.size)
        val message = getQuantityString(R.plurals.conversation__degraded_confirmation__message, messageCount)

        val request =
          new ConfirmationRequest.Builder()
            .withHeader(header)
            .withMessage(message)
            .withPositiveButton(positiveButton)
            .withNegativeButton(negativeButton)
            .withConfirmationCallback(callback)
            .withCancelButton()
            .withBackgroundImage(R.drawable.degradation_overlay)
            .withWireTheme(inject[ThemeController].getThemeDependentOptionsTheme)
            .build

        confirmationController.requestConfirmation(request, IConfirmationController.CONVERSATION)
      }
    }

  private val slidingPaneObserver = new SlidingPaneObserver {
    override def onPanelSlide(panel: View, slideOffset: Float): Unit = {}

    override def onPanelOpened(panel: View): Unit = keyboardController.hideKeyboardIfVisible()

    override def onPanelClosed(panel: View): Unit = {}
  }
  var imagePreviewLayout: ImagePreviewLayout = _

  private def showImagePreview(setImage: ImagePreviewLayout => Any): Unit = {
    imagePreviewLayout = ImagePreviewLayout.newInstance(getContext, containerPreview, imagePreviewCallback)
    setImage(imagePreviewLayout)
    containerPreview.addView(imagePreviewLayout)
    previewShown ! true
//    navigationController.setPagerEnabled(false)
    containerPreview.setTranslationY(getView.getMeasuredHeight)
    containerPreview.animate.translationY(0).setDuration(getInt(R.integer.animation_duration_medium)).setInterpolator(new Expo.EaseOut)
  }

  def hideImagePreview(): Unit = {
    if (imagePreviewLayout != null) {
      containerPreview.removeView(imagePreviewLayout)
    }
  }

  override def onBackPressed(): Boolean = {
    if (mentionsList != null && mentionsList.getVisibility == View.VISIBLE) {
      showMentionsList(false)
      true
    } else if (extendedCursorContainer != null && extendedCursorContainer.isExpanded) {
      extendedCursorContainer.close(false)
      true
    } else {
      false
    }
  }

  def showNormalConversation(): Unit = {
    if (getActivity != null && rootView != null) {
      containerPreview.setVisibility(View.VISIBLE)
      loadingIndicatorView.setVisibility(View.VISIBLE)
      llBottomCursorParent.setVisibility(View.VISIBLE)
      gtvLinedown112.setVisibility(View.VISIBLE)
    }
  }

  def updateTranscriptModel() {
    if (getActivity != null && rootView != null) {
      listView.setLayoutManagerStartModel(false);
    }
  }

  def updateChatBackground(conversationData: ConversationData) {
    if (getActivity != null && bgView != null) {
      Option(conversationData).fold {
        bgView.setBackgroundResource(SelectChatBackgroundActivity.getDefaultBackground(getContext));
      } { it =>
        val spKey = SpUtils.getConversationBackgroundSpKey(getContext, it.remoteId.str)
        val backgroundIdxTag = SpUtils.getString(getContext, SpUtils.SP_NAME_NORMAL, spKey, null)
        bgView.setBackgroundResource(SelectChatBackgroundActivity.getBackground(getContext, backgroundIdxTag))
      }
    }
  }

  def setInnerMarginPadding(conversationData: ConversationData, expand: Boolean = false, refreshCursorView: Boolean = false): Unit = {
    if (getActivity != null && rootView != null) {
      if (conversationData != null) {
        val paddingTopBottomNormal = getResources().getDimension(R.dimen.wire__padding__regular).toInt
        var toolBarHeight = 0
        val tv = new TypedValue()
        if (getActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
          toolBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics())
        }

        if (refreshCursorView) {
          cursorView.showUserViews()
        }
        val cursorViewHeight = if (expand) {
          (2 * (getResources.getDimension(R.dimen.new_cursor_height) + getResources.getDimension(R.dimen.wire__divider__height__thin))).toInt
        } else {
          (getResources.getDimension(R.dimen.new_cursor_height) + getResources.getDimension(R.dimen.wire__divider__height__thin)).toInt
        }
        verbose(l"setInnerMarginPadding visible:$expand  cursorViewHeight:$cursorViewHeight")

        if (conversationData.isServerNotification) {
          val paddingBottom = getResources.getDimension(R.dimen.system_notification_textjson_ml_mr).toInt
          listView.setPadding(listView.getPaddingLeft(), toolBarHeight + paddingTopBottomNormal, listView.getPaddingRight(), paddingBottom);
        } else {
          listView.setPadding(listView.getPaddingLeft, toolBarHeight + paddingTopBottomNormal, listView.getPaddingRight, cursorViewHeight + paddingTopBottomNormal)
        }
        cvJumpToLatestMessage.setLayoutParams(returning(cvJumpToLatestMessage.getLayoutParams) { lp =>
          if (lp.isInstanceOf[ViewGroup.MarginLayoutParams]) {
            lp.asInstanceOf[ViewGroup.MarginLayoutParams].setMargins(0, 0, 0, cursorViewHeight + DensityUtils.dp2px(getActivity(), 25));
          } else {
            // ...
          }
        })

      }
    }
  }


  def showGlyphItems(conversationData: ConversationData): Unit = {
    if (getActivity != null && rootView != null) {
      if (conversationData != null && conversationData.convType == IConversation.Type.ONE_TO_ONE
        && participantsController.otherParticipant.currentValue.nonEmpty) {
        cursorView.showGlyphItems(conversationData, participantsController.otherParticipant.currentValue.head)
      } else {
        cursorView.showGlyphItems(conversationData, null)
      }
    }
  }

  override def onMemsunUpdate(old: ConversationData, updated: ConversationData): Unit = {

  }

  override def sendMultipleImages(selectImages: util.List[LocalMedia], isCompress: Boolean): Unit = {
    JavaConverters.asScalaIteratorConverter(selectImages.iterator).asScala.toSeq.foreach {
      image =>
        val uri = FileUtil.getImageStreamFromExternal(image.getPath)
        if (VIDEO_TYPE.equals(image.getPictureType)) {
          sendMultipleImagesComfirm(UriInput(URI.parse(uri.toString)))
        } else {
          val mime = Mime(BitmapUtils.detectImageType(ZApplication.getInstance().getContentResolver.openInputStream(uri)))
          verbose(l"case uriInput:,mime:$mime")
          if (mime == Mime.Image.Gif) {
            sendMultipleImagesComfirm(UriInput(URI.parse(uri.toString)))
          } else {
            val inputStream = ZApplication.getInstance().getContentResolver.openInputStream(uri)
            val newBitmap = if (isCompress) {
              Bmp.rotateBitmap(inputStream, uri, getContext)
            } else {
              Bmp.compressBitmap(inputStream, uri, getContext)
            }
            sendMultipleImagesComfirm(BitmapInput(newBitmap))
          }
        }
    }
  }


  def sendMultipleImagesComfirm(input: RawAssetInput): Unit = {
    convController.sendMessage(input, getActivity)
    extendedCursorContainer.close(true)
  }
}

object ConversationFragment {
  val TAG = ConversationFragment.getClass.getSimpleName
  val SAVED_STATE_PREVIEW = "SAVED_STATE_PREVIEW"
  val REQUEST_VIDEO_CAPTURE = 911
  val VIDEO_TYPE = "video/mp4"

  def newInstance() = new ConversationFragment

  trait Container {

    def updateAdvisoryReadStatus(convId: ConvId, isRead: Boolean)

    def deleteReportNoticeMessage(convId: ConvId, msgId: MessageId)

    def updateAdvisoryDialogStatus(convId:ConvId,isShow:Boolean)
  }

}
