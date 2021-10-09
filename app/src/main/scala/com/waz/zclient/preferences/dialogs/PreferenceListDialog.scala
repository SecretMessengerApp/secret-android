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
package com.waz.zclient.preferences.dialogs

import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.waz.zclient.{FragmentHelper, R}

abstract class PreferenceListDialog extends DialogFragment with FragmentHelper {

  protected val title: String
  protected val names: Array[String]
  protected val defaultValue: Int

  protected def updatePref(which: Int): Unit

  override def onCreateDialog(savedInstanceState: Bundle) = {
    val builder: AlertDialog.Builder = new AlertDialog.Builder(getActivity)
    builder.setTitle(title)
      .setSingleChoiceItems(names.map(_.asInstanceOf[CharSequence]), defaultValue, new OnClickListener {
        override def onClick(dialog: DialogInterface, which: Int) = {
          updatePref(which)
          dismiss()
        }
      })
      .setNegativeButton(R.string.secret_cancel, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, id: Int): Unit = {
          dismiss()
        }
      })
      .create
  }
}
