/**
 * Secret
 * Copyright (C) 2021 Secret
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
package com.jsy.common.acts

import java.util

import android.content.{Context, Intent}
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.{LinearLayoutManager, RecyclerView}
import com.chad.library.adapter.base.{BaseQuickAdapter, BaseViewHolder}
import com.jsy.common.acts.LanguageActivity._
import com.waz.zclient.utils.SpUtils
import com.waz.zclient.{BaseActivity, Constants, R}

import scala.util.Try

class LanguageActivity extends BaseActivity {

  private var oldLanguage: Option[String] = None

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_language)

    oldLanguage = Option(SpUtils.getLanguage(this))

    setToolbarNavigtion(findViewById[Toolbar](R.id.toolbar), this)

    val confirmTextView = findViewById[TextView](R.id.tvConfirm)
    confirmTextView.setEnabled(false)
    confirmTextView.setOnClickListener(new View.OnClickListener {
      override def onClick(v: View): Unit = {
        oldLanguage.foreach(it => SpUtils.setLanguage(LanguageActivity.this, it))
        sendBroadcast(new Intent(Constants.ACTION_CHANGE_LANGUAGE))
        finish()
      }
    })

    val showData = new util.ArrayList[LanguageBean]()
    val entries = getResources.getStringArray(R.array.language_entries)
    val values = getResources.getStringArray(R.array.language_values)

    entries.zip(values).foreach { it =>
      showData.add(LanguageBean(it._1, it._2, oldLanguage.contains(it._2)))
    }

    val languageAdapter = new LanguageAdapter(showData)
    languageAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener {
      override def onItemClick(adapter: BaseQuickAdapter[_, _ <: BaseViewHolder], view: View, position: Int): Unit = {
        Try(adapter.getItem(position)).filter(_.isInstanceOf[LanguageBean]).map(_.asInstanceOf[LanguageBean])
          .foreach { it =>
            confirmTextView.setEnabled(!oldLanguage.contains(it.value))

            oldLanguage = Some(it.value)
            for (index <- 0 until showData.size()) {
              val itemBean = showData.get(index)
              itemBean.checked = oldLanguage.contains(itemBean.value)
            }
            adapter.notifyDataSetChanged()
          }
      }
    })

    val contentRecyclerView = findViewById[RecyclerView](R.id.content_recyclerView)
    contentRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
    contentRecyclerView.setAdapter(languageAdapter)
  }
}

object LanguageActivity {
  def start(context: Context): Unit = {
    Option(context).foreach { it =>
      it.startActivity(new Intent(it, classOf[LanguageActivity]))
    }
  }

  private class LanguageAdapter(data: java.util.List[LanguageBean])
    extends BaseQuickAdapter[LanguageBean, BaseViewHolder](R.layout.item_language, data) {
    override def convert(helper: BaseViewHolder, item: LanguageBean): Unit = {
      helper.setText(R.id.name_textView, item.name)
        .setGone(R.id.checked_imageView, item.checked)
    }
  }

  private case class LanguageBean(name: String, value: String, var checked: Boolean = false)

}
