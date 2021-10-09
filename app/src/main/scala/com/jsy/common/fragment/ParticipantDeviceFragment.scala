/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.fragment

import android.content.Context
import android.os.Bundle
import android.view.{LayoutInflater, View, ViewGroup}
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.waz.service.ZMessaging
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.BrowserController
import com.waz.zclient.conversation.ConversationController
import com.waz.zclient.participants.fragments.ParticipantFragment
import com.waz.zclient.participants.{ParticipantOtrDeviceAdapter, ParticipantsController}
import com.waz.zclient.utils.RichView
import com.waz.zclient.{FragmentHelper, R}

class ParticipantDeviceFragment(context:Context) extends FragmentHelper{

  import Threading.Implicits.Ui

  private lazy val zms = inject[Signal[ZMessaging]]
  private lazy val convController = inject[ConversationController]
  private lazy val browserController = inject[BrowserController]
  private lazy val participantsController = inject[ParticipantsController]
  private lazy val participantOtrDeviceAdapter = new ParticipantOtrDeviceAdapter
  //private var context : Context = null
  //private var participantOtrDeviceAdapter: ParticipantOtrDeviceAdapter = null

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {

    participantOtrDeviceAdapter.onClientClick.onUi { client =>
      participantsController.otherParticipantId.head.foreach {
        case Some(userId) =>
          Option(getParentFragment).foreach {
            case f: ParticipantFragment => f.showOtrClient(userId, client.id)
            case _ =>
          }
        case _ =>
      }
    }

    participantOtrDeviceAdapter.onHeaderClick {
      _ => browserController.openUrl(getString(R.string.url_otr_learn_why))
    }

    returning( new RecyclerView(context) ) { rv =>
      rv.setLayoutManager(new LinearLayoutManager(context))
      rv.setHasFixedSize(true)
      rv.setAdapter(participantOtrDeviceAdapter)
      rv.setClipToPadding(false)
    }
  }
}


object ParticipantDeviceFragment {
  val Tag = classOf[ParticipantDeviceFragment].getSimpleName
}
