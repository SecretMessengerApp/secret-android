/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.service

import android.content.Context
import android.telephony.TelephonyManager
import com.github.ghik.silencer.silent
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.{PhoneNumber => GooglePhoneNumber}
import com.waz.log.BasicLogging.LogTag.DerivedLogTag
import com.waz.log.LogSE._
import com.waz.model.PhoneNumber
import com.waz.threading.SerialDispatchQueue

import scala.concurrent.Future
import scala.util.Try

trait PhoneNumberService {
  def defaultRegion: String
  def myPhoneNumber: Future[Option[PhoneNumber]]
  def normalize(phone: PhoneNumber): Future[Option[PhoneNumber]]
  def normalizeNotThreadSafe(phone: PhoneNumber, util: PhoneNumberUtil): Option[PhoneNumber]
}

class PhoneNumberServiceImpl(context: Context) extends PhoneNumberService with DerivedLogTag {
  private implicit val dispatcher = new SerialDispatchQueue(name = "PhoneNumberService")

  private lazy val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE).asInstanceOf[TelephonyManager]
  private lazy val phoneNumberUtil = PhoneNumberUtil.getInstance()

  lazy val defaultRegion = Option(telephonyManager.getSimCountryIso).orElse(getLocale(context).filter(_ != "")).getOrElse("US").toUpperCase

  private val qaShortcutRegex = "^\\+0\\d+$".r

  @silent def getLocale(context: Context): Option[String] =
    Option(context.getResources.getConfiguration.locale.getCountry)

  def myPhoneNumber: Future[Option[PhoneNumber]] =
    Try(telephonyManager.getLine1Number).toOption.flatMap(Option(_)).filter(_.nonEmpty).map(PhoneNumber).map(normalize) match {
      case None => Future(None)
      case Some(f) => f
    }

  def normalize(phone: PhoneNumber): Future[Option[PhoneNumber]] = Future(normalizeNotThreadSafe(phone, phoneNumberUtil))

  def normalizeNotThreadSafe(phone: PhoneNumber, util: PhoneNumberUtil): Option[PhoneNumber] = phone.str match {
    case null => None
    case str if str.length < 5 => None
    case qaShortcutRegex() => Some(phone)
    case str =>
      try {
        val number = new GooglePhoneNumber
        util.parse(str, defaultRegion, number)
        Some(PhoneNumber(util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)))
      } catch {
        case ex: Throwable =>
          debug(l"phone number normalization failed for $phone (${redactedString(defaultRegion)}): ${showString(ex.getMessage)}")
          None
      }
  }

}
