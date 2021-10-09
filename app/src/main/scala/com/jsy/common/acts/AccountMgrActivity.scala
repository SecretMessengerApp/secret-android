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
package com.jsy.common.acts

import android.content.{Context, Intent}
import android.graphics.drawable.ColorDrawable
import android.os.{Bundle, Handler}
import android.view.View.OnClickListener
import android.view.{LayoutInflater, View, ViewGroup}
import android.widget.{ImageView, Toast}
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.{LinearLayoutManager, OrientationHelper, RecyclerView}
import com.jsy.common.utils.{DoubleUtils, ModuleUtils}
import com.jsy.common.views.{BtnClickListener, DividerListItemDecoration, LogoutPasswordDialog}
import com.jsy.res.utils.ViewUtils
import com.jsy.secret.sub.swipbackact.ActivityContainner
import com.waz.api.impl.ErrorResponse
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.model.AccountData
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient._
import com.waz.zclient.common.views.ChatHeadViewNew
import com.waz.zclient.log.LogUI._
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.utils._

import java.io.File
import java.util
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success

class AccountMgrActivity extends BaseActivity with BtnClickListener{

  var toolBarAccountMgr: Toolbar = _
  var tvTitle: TypefaceTextView = _
  var recyclerViewAccountMgr: RecyclerView = _
  var accountMgrAdp: AccountMgrAdp = _

  lazy implicit val uiStorage = inject[UiStorage]
  lazy val zms = inject[Signal[ZMessaging]]

  val currentAccount = ZMessaging.currentAccounts.activeAccount.collect { case Some(account) if account.id != null => account }

  var logoutDialog : LogoutPasswordDialog = _
  var clickAccountData : AccountData = _
  var clickPositon : Int = _


  override def canUseSwipeBackLayout = true

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_account_mgr)

    tvTitle = findById[TypefaceTextView](R.id.tvTitle)
    toolBarAccountMgr = findById[Toolbar](R.id.toolBarAccountMgr)
    recyclerViewAccountMgr = findById[RecyclerView](R.id.recyclerViewAccountMgr)
    recyclerViewAccountMgr.setLayoutManager(new LinearLayoutManager(this, OrientationHelper.VERTICAL, false))
    recyclerViewAccountMgr.addItemDecoration(new DividerListItemDecoration(OrientationHelper.VERTICAL, new ColorDrawable(ContextCompat.getColor(AccountMgrActivity.this, R.color.transparent)), getResources.getDisplayMetrics.widthPixels, ViewUtils.toPx(this, 10f), false))

    accountMgrAdp = new AccountMgrAdp(this)
    recyclerViewAccountMgr.setAdapter(accountMgrAdp)
    accountMgrAdp._onItemClick = new OnItemClick {
      override def onItemClick(view: View, accountData: AccountData, position: Int, isClickActiveAccount: Boolean): Unit = {
        if (!DoubleUtils.isFastDoubleClick()) {
          val vType = accountMgrAdp.getItemViewType(position)
          if (vType == AccountMgrAdp.VIEW_TYPE_NORMAL) {
            val vId = view.getId
            if (vId == R.id.rlSwitchAccount) {
              if (isClickActiveAccount) {
                // ...
              } else {
                ZMessaging.currentAccounts.setAccount(Some(accountData.id)).onComplete {
                  case Success(x) =>
                    (for {
                      cur <- currentAccount
                    } yield {
                      verbose(l"onItemClick getCustomBackend() rlSwitchAccount $cur")
                      SpUtils.putString(AccountMgrActivity.this, null, SpUtils.SP_KEY_USERID, cur.id.str)
                      SpUtils.putString(AccountMgrActivity.this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_COOKIES, cur.cookie.str)
                      if (cur.accessToken.isDefined) {
                        cur.accessToken.foreach { x =>
                          SpUtils.putString(AccountMgrActivity.this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, x.accessToken)
                          SpUtils.putString(AccountMgrActivity.this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, x.tokenType)
                        }
                      } else {
                        SpUtils.putString(AccountMgrActivity.this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN, "")
                        SpUtils.putString(AccountMgrActivity.this, SpUtils.SP_NAME_NORMAL, SpUtils.SP_KEY_TOKEN_TYPE, "")
                      }
                      inject[BackendController].getCustomBackend()
                      cur
                    }).onUi{
                      cur =>
                         finish()
                    }
                  case _ =>
                }
              }
            } else if (vId == R.id.tvDeleteAccount) {
              clickAccountData = accountData
              clickPositon = position

              if(logoutDialog == null) logoutDialog = new LogoutPasswordDialog(AccountMgrActivity.this)
              logoutDialog.setBtnClickListener(AccountMgrActivity.this)
              logoutDialog.show()
            } else {
              //...
            }
          } else if (vType == AccountMgrAdp.VIEW_TYPE_ADD_ACCOUNT) {
            val clazz: java.lang.Class[_] = ModuleUtils.classForName(ModuleUtils.CLAZZ_AppEntryActivity)
            if (clazz != null) {
              startActivity(new Intent(AccountMgrActivity.this, clazz))
            }
          } else {
            //...
          }
        }
      }
    }

    toolBarAccountMgr.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        finish()
      }
    })

  }

  override def onDestroy(): Unit = {
    super.onDestroy()
  }

  override def onBtnClick(password: String): Unit = {

    if(StringUtils.isBlank(password)) Toast.makeText(AccountMgrActivity.this,getString(R.string.new_reg_phone_add_password_header),Toast.LENGTH_SHORT).show()
    else{
      val account = accountMgrAdp.accounts.get(clickPositon)

      ZMessaging.currentAccounts.getZms(clickAccountData.id).flatMap {
        case Some(z) =>
          z.otrClientsService.getSelfClient.onComplete{
            case Success(c) =>
              c.foreach{
                cs =>
                  (for {
                    Some(accManager) <- z.accounts.accountManagers.map(_.find(_.userId == account.id)).head
                    res              <- accManager.deleteClient(cs.id,Some(AccountData.Password(password)))
                  } yield res).onComplete{

                    case Success(Right(_)) =>

                      for {
                        Some(accManager) <- z.accounts.accountManagers.map(_.find(_.userId == account.id)).head
                        _              <- accManager.logoutAndResetClient()
                      }yield {}

                      val dbFile = getDatabasePath(clickAccountData.id.str)

                      val exts = Seq("", "-wal", "-shm", "-journal")

                      val toDelete = exts.map(ext => s"${dbFile.getAbsolutePath}$ext").map(new File(_))

                      toDelete.foreach{
                        f => if(f.exists()) f.delete()
                      }
                      if(logoutDialog != null) logoutDialog.dismiss()
                      ServerConfig.clearConfigUrl(clickAccountData.id.str)
                      ActivityContainner.finishAndRemoveAllBeside(ModuleUtils.CLAZZ_MainActivity)
                    case Success(Left(ErrorResponse(code,_,_))) =>
                      if(code == 403)

                        runOnUiThread(new Runnable {
                          override def run(): Unit = Toast.makeText(AccountMgrActivity.this,getString(R.string.otr__remove_device__error),Toast.LENGTH_SHORT ).show()
                        })

                      else{

                        runOnUiThread(new Runnable {
                          override def run(): Unit = {
                            Toast.makeText(AccountMgrActivity.this,getString(R.string.secret_data_delete_failed),Toast.LENGTH_SHORT ).show()
                            if(logoutDialog != null) logoutDialog.dismiss()
                          }
                        })
                      }


                    case _ =>
                      runOnUiThread(new Runnable {
                        override def run(): Unit = {
                          Toast.makeText(AccountMgrActivity.this,getString(R.string.secret_data_delete_failed),Toast.LENGTH_SHORT ).show()
                          if(logoutDialog != null) logoutDialog.dismiss()
                        }
                      })
                  }

              }
          }
          Future.successful({})
        case None =>
          if(logoutDialog != null) logoutDialog.dismiss()
          Future.successful({})
      }
    }
  }

  //
  //  override def onLogout(): Unit = {
  //    finish()
  //    overridePendingTransition(0, 0)
  //  }
  //
  //  override def onForceClientUpdate(): Unit = {
  //
  //  }
  //
  //  override def onInitialized(self: Self): Unit = {
  //
  //  }

}


class AccountMgrAdp(context: Context)(implicit injector: Injector, eventContext: EventContext) extends RecyclerView.Adapter[AccountMgrVh] with Injectable with DerivedLogTag {

  import AccountMgrAdp._

  val zms = inject[Signal[ZMessaging]]

  var accounts = new util.ArrayList[AccountData]()

  var _onItemClick: OnItemClick = _

  ZMessaging.currentAccounts.activeAccount.onUi {
    case Some(active) =>
      activiteAccount = active

      ZMessaging.currentAccounts.activeZms.currentValue.flatten.foreach { zms =>
        zms.accountStorage.list().flatMap { seqAccs =>
          handler.post(new Runnable {
            override def run(): Unit = {
              accounts.clear()
              accounts.addAll(seqAccs.asJava)
              notifyDataSetChanged()
            }
          })
          Future.successful(seqAccs)
        }
      }
    case _ =>
  }

  private val handler = new Handler();

  var activiteAccount: AccountData = _

  override def getItemCount: Int = if (accounts.size < MAX_ACCOUNT) accounts.size + 1 else accounts.size

  override def onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountMgrVh = {
    viewType match {
      case VIEW_TYPE_ADD_ACCOUNT => new AccountMgrVhAdd(LayoutInflater.from(context).inflate(R.layout.lay_item_account_mgr_add_acount, parent, false))
      case VIEW_TYPE_NORMAL => new AccountMgrVhNormal(LayoutInflater.from(context).inflate(R.layout.lay_item_account_mgr_normal, parent, false))
      case _ => null
    }
  }

  override def onBindViewHolder(holder: AccountMgrVh, position: Int): Unit = {

    getItemViewType(position) match {
      case VIEW_TYPE_ADD_ACCOUNT => holder.setData(position, _onItemClick)
      case VIEW_TYPE_NORMAL => holder.setData(accounts.get(position), activiteAccount, position, _onItemClick)
      case _ =>
    }

  }

  override def getItemViewType(position: Int): Int = {
    if (accounts.size < MAX_ACCOUNT) {
      if (position < accounts.size) {
        VIEW_TYPE_NORMAL
      } else {
        VIEW_TYPE_ADD_ACCOUNT
      }
    } else {
      VIEW_TYPE_NORMAL
    }
  }
}

object AccountMgrAdp {

  val VIEW_TYPE_NORMAL = 0
  val VIEW_TYPE_ADD_ACCOUNT = 1
  val MAX_ACCOUNT = 3
}

class AccountMgrVh(itemView: View) extends RecyclerView.ViewHolder(itemView) {
  def setData(accountData: AccountData, activiteAccount: AccountData, position: Int, onItemClick: OnItemClick): Unit = {}

  def setData(position: Int, onItemClick: OnItemClick): Unit = {}
}

class AccountMgrVhAdd(itemView: View) extends AccountMgrVh(itemView) {

  override def setData(position: Int, onItemClick: OnItemClick): Unit = {
    itemView.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (onItemClick != null) {
          onItemClick.onItemClick(v, null, position, false)
        }
      }
    })
  }
}

class AccountMgrVhNormal(itemView: View) extends AccountMgrVh(itemView) {

  val rlSwitchAccount = ViewUtils.getView[ViewGroup](itemView, R.id.rlSwitchAccount)
  val chatHeadView = ViewUtils.getView[ChatHeadViewNew](itemView, R.id.chatHeadView)
  val ivAccountMgrStatus = ViewUtils.getView[ImageView](itemView, R.id.ivAccountMgrStatus)
  val tvAccountMgrName = ViewUtils.getView[TypefaceTextView](itemView, R.id.tvAccountMgrName)
  val tvDeleteAccount = ViewUtils.getView[TypefaceTextView](itemView, R.id.tvDeleteAccount)

  override def setData(accountData: AccountData, activiteAccount: AccountData, position: Int, onItemClick: OnItemClick): Unit = {
    chatHeadView.clearImage()
    chatHeadView.setAccountData(accountData)
    accountData.name.foreach(tvAccountMgrName.setText)
    val isClickActiveAccount = activiteAccount.id == accountData.id
    ivAccountMgrStatus.setVisibility(if (isClickActiveAccount) View.VISIBLE else View.INVISIBLE)
    tvDeleteAccount.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (onItemClick != null) {
          onItemClick.onItemClick(v, accountData, position, isClickActiveAccount)
        }
      }
    })
    rlSwitchAccount.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (onItemClick != null) {
          onItemClick.onItemClick(v, accountData, position, isClickActiveAccount)
        }
      }
    })
  }

}


trait OnItemClick {
  def onItemClick(view: View, accountData: AccountData, position: Int, isClickActiveAccount: Boolean)
}
