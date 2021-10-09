/**
 * Secret
 * Copyright (C) 2019 Secret
 */
package com.jsy.common.moduleProxy

import com.waz.utils.events.Signal

import scala.concurrent.duration._

object ProxyConversationListManagerFragmentObject {

  lazy val ConvListUpdateThrottling = 250.milli

  val hideListActionsView = Signal(false)
}
