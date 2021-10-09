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
package com.waz.zclient.conversation.creation

import android.os.Bundle
import android.view.View.OnClickListener
import android.view.animation.Animation
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.{Fragment, FragmentManager}
import com.jsy.common.moduleProxy.{ICreateGroupConversation, ProxyConversationListManagerFragmentObject}
import com.jsy.res.theme.ThemeUtils
import com.jsy.res.utils.ViewUtils
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.zclient.common.controllers.global.{AccentColorController, KeyboardController}
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.conversation.creation.CreateConversationManagerFragment._
import com.waz.zclient.core.stores.conversation.ConversationChangeRequester
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.utils.ColorUtils
import com.waz.zclient.utils.ContextUtils.{getDimenPx, getInt}
import com.waz.zclient.utils._
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, R}
import org.json.JSONArray

class CreateConversationManagerFragment extends FragmentHelper with ICreateGroupConversation with DerivedLogTag {

  implicit private def ctx = getContext
  private lazy val conversationController = inject[ConversationController]
  private lazy val ctrl = inject[CreateConversationController]
  private lazy val keyboard = inject[KeyboardController]


  private lazy val accentColor = inject[AccentColorController].accentColor.map(_.color)

  private lazy val currentPage = Signal[Int]()

  private lazy val confButtonText = for {
    currentPage <- currentPage
    users <- ctrl.users
    integrations <- ctrl.integrations
  } yield currentPage match {
    case SettingsPage => R.string.next_button
    case PickerPage if users.nonEmpty || integrations.nonEmpty => R.string.next_button
    case PickerPage => R.string.skip_button
  }

  private lazy val confButtonEnabled = for {
    currentPage <- currentPage
    name <- ctrl.name
    memberCount <- ctrl.users.map(_.size)
    integrationsCount <- ctrl.integrations.map(_.size)
  } yield currentPage match {
    case SettingsPage if name.trim.isEmpty => false
    case _ if memberCount + integrationsCount >= ConversationController.MaxParticipants => false
    case _ => true
  }

  private lazy val confButtonColor = confButtonEnabled.flatMap {
    case false => Signal.const(ColorUtils.getAttrColor(getContext,R.attr.SecretSecondTextColor))
    case _ => accentColor
  }

  private lazy val headerText = for {
    currentPage <- currentPage
    userCount <- ctrl.users.map(_.size)
    integrationsCount <- ctrl.integrations.map(_.size)
  } yield currentPage match {
    case SettingsPage => getString(R.string.new_group_header)
    case PickerPage if userCount == 0 && integrationsCount == 0 => getString(R.string.add_participants_empty_header)
    case PickerPage => getString(R.string.add_participants_count_header, (userCount + integrationsCount).toString)
  }

  private var toolbar: Option[Toolbar] = None

  private var confButton: Option[TypefaceTextView] = None

  private var header: Option[TypefaceTextView] = None

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (enter)
      new DefaultPageTransitionAnimation(0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_long),
        getInt(R.integer.framework_animation_duration_medium),
        1f)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        1f)
  }


  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    ProxyConversationListManagerFragmentObject.hideListActionsView ! true

    ctrl.users.zip(ctrl.integrations).map { case (users, integrations) => users.size + integrations.size >= ConversationController.MaxParticipants }.onUi {
      case true =>
        ViewUtils.showAlertDialog(getContext,
          R.string.max_participants_alert_title,
          R.string.max_participants_create_alert_message,
          android.R.string.ok, null, true)
      case _ =>
    }
  }


  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    inflater.inflate(R.layout.create_conv_fragment, container, false)
  }

  override def onViewCreated(rootView: View, savedInstanceState: Bundle): Unit = {
    toolbar = Option(ViewUtils.getView(rootView, R.id.toolbar))
    Signal(currentPage, Signal(ThemeUtils.isDarkTheme(getContext))).map {
      case (PickerPage, true) => R.drawable.action_back_light
      case (PickerPage, false) => R.drawable.action_back_dark
      case (SettingsPage, false) => R.drawable.ic_action_close_dark
      case (SettingsPage, true) => R.drawable.ic_action_close_light
      case _ => R.drawable.ic_action_close_dark
    }.onUi(dr => toolbar.foreach(_.setNavigationIcon(dr)))

    confButton = Option(ViewUtils.getView(rootView, R.id.confirmation_button))
    confButtonEnabled.onUi(e => confButton.foreach(_.setEnabled(e)))
    confButtonText.onUi(id => confButton.foreach(_.setText(id)))
    confButtonColor.onUi(c => confButton.foreach(_.setTextColor(c)))

    header = Option(ViewUtils.getView(rootView, R.id.header))
    headerText.onUi(txt => header.foreach(_.setText(txt)))

    openFragment(new CreateConversationSettingsFragment, CreateConversationSettingsFragment.Tag)
    currentPage ! SettingsPage

    confButton.foreach(_.onClick {
      currentPage.currentValue.foreach {
        case SettingsPage =>
          keyboard.hideKeyboardIfVisible()
          currentPage ! PickerPage
          openFragment(AddParticipantsFragment.newInstance(false), AddParticipantsFragment.Tag)
        case PickerPage =>
          keyboard.hideKeyboardIfVisible()
          val apps = new JSONArray()
          apps.put("0x00000000000001")
          ctrl.createConversation(apps = apps.toString()).flatMap { convId =>
            close()
            conversationController.selectConv(Some(convId), ConversationChangeRequester.START_CONVERSATION)
          }(Threading.Ui)
        case _ =>
      }

    })

    toolbar.foreach(_.setNavigationOnClickListener(new OnClickListener() {
      override def onClick(v: View): Unit =
        onBackPressed()
    }))

  }


  override def onDestroyView() = {
    ProxyConversationListManagerFragmentObject.hideListActionsView ! false
    toolbar.foreach(_.setNavigationOnClickListener(null))
    super.onDestroyView()
  }

  private def openFragment(fragment: Fragment, tag: String): Unit = {
    getChildFragmentManager.beginTransaction()
      .setCustomAnimations(
        R.anim.fragment_animation_second_page_slide_in_from_right,
        R.anim.fragment_animation_second_page_slide_out_to_left,
        R.anim.fragment_animation_second_page_slide_in_from_left,
        R.anim.fragment_animation_second_page_slide_out_to_right)
      .replace(R.id.container, fragment, tag)
      .addToBackStack(tag)
      .commitAllowingStateLoss()
  }

  private def close() = {
    keyboard.hideKeyboardIfVisible()
    //    ctrl.onShowCreateConversation ! false
    iCreateGroupConversation.foreach(_.removeCreateGroupConversationFragment())

  }

  override def onBackPressed(): Boolean = {
    val frags = getChildFragmentManager.getFragments
    def currentChildFrag() = if (frags != null && frags.size() > 0) Option(frags.get(0)) else None

    if (keyboard.hideKeyboardIfVisible()) {
      keyboard.hideKeyboardIfVisible()
      true
    } else {
      currentChildFrag().fold {
        false
      } { frag =>
        frag.getTag match {
          case AddParticipantsFragment.Tag =>
            getChildFragmentManager.popBackStackImmediate(AddParticipantsFragment.Tag, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            currentPage ! SettingsPage
          case CreateConversationSettingsFragment.Tag =>
            iCreateGroupConversation.foreach(_.removeCreateGroupConversationFragment())
        }
        true
      }
    }
  }


  override def showCreateGroupConversationFragment(): Unit = {
    // ...
  }

  override def removeCreateGroupConversationFragment(): Unit = {
    iCreateGroupConversation.foreach(_.removeCreateGroupConversationFragment())
  }

  def iCreateGroupConversation: Option[ICreateGroupConversation] = {
    if (getParentFragment != null && getParentFragment.isInstanceOf[ICreateGroupConversation]){
      Some(getParentFragment.asInstanceOf[ICreateGroupConversation])
    } else if (getActivity != null && getActivity.isInstanceOf[ICreateGroupConversation]) {
      Some(getActivity.asInstanceOf[ICreateGroupConversation])
    } else {
      None
    }
  }


}

object CreateConversationManagerFragment {

  def newInstance: CreateConversationManagerFragment = new CreateConversationManagerFragment

  val TAG: String = getClass.getSimpleName

  val SettingsPage = 0
  val PickerPage = 1
}
