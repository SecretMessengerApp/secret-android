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
package com.waz.znet

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.{CertificateFactory, X509Certificate}

import com.waz.service.CertificatePin
import javax.net.ssl.{TrustManagerFactory, X509TrustManager}
import com.waz.utils.returning
import okhttp3.TlsVersion

object ServerTrust {
/*
  val WireDomain: String = "*.wire.com"

  val WirePublicKey: Array[Byte] = Array(
    0x30, 0x82, 0x01, 0x22, 0x30, 0x0D, 0x06, 0x09, 0x2A, 0x86, 0x48, 0x86, 0xF7, 0x0D, 0x01, 0x01, 0x01,
    0x05, 0x00, 0x03, 0x82, 0x01, 0x0F, 0x00, 0x30, 0x82, 0x01, 0x0A, 0x02, 0x82, 0x01, 0x01, 0x00, 0xAD,
    0xE6, 0x33, 0x05, 0x6B, 0xAF, 0x9D, 0x52, 0x98, 0x7E, 0x03, 0x4D, 0x5F, 0x77, 0x55, 0x8D, 0x49, 0xEA,
    0x21, 0x5B, 0x65, 0xE1, 0x7A, 0x90, 0x9C, 0x27, 0x18, 0xEA, 0x6F, 0xEC, 0x58, 0xCD, 0x79, 0x4D, 0x32,
    0xB4, 0x6E, 0x8F, 0x45, 0x2A, 0x73, 0x31, 0x34, 0x03, 0xED, 0x0F, 0x30, 0x7A, 0x56, 0x24, 0x6F, 0xA5,
    0x9B, 0x0A, 0x11, 0xE7, 0xD7, 0xF2, 0x98, 0xB5, 0xE4, 0x97, 0x2C, 0xE2, 0xE6, 0x62, 0xB9, 0x9F, 0x35,
    0x21, 0x25, 0x75, 0x6E, 0xAC, 0x6D, 0xA6, 0xC8, 0xC9, 0xE7, 0xA6, 0x23, 0x7A, 0x67, 0xBD, 0xF4, 0x37,
    0x1A, 0xE5, 0xC9, 0x37, 0xE6, 0x94, 0xE1, 0x62, 0xE2, 0xBA, 0x8D, 0x9F, 0x2F, 0x1B, 0xAA, 0x49, 0x89,
    0x8F, 0x66, 0x45, 0x8F, 0x67, 0x19, 0xA7, 0x62, 0x77, 0x8A, 0x96, 0x2F, 0xBB, 0xB0, 0x01, 0xEA, 0x08,
    0x9F, 0x1D, 0xA6, 0x38, 0xCE, 0xC7, 0x7A, 0x90, 0x82, 0x87, 0x63, 0x4E, 0x5D, 0xDF, 0x84, 0x8D, 0x0E,
    0x2C, 0x06, 0xC7, 0xE0, 0x6F, 0xE9, 0x16, 0xAD, 0xC5, 0x50, 0x98, 0x1C, 0xEF, 0x2C, 0x73, 0xD4, 0x0E,
    0x8C, 0xA4, 0x9B, 0xF9, 0xBE, 0x06, 0xF2, 0xF6, 0x78, 0x9D, 0x67, 0x8C, 0xF3, 0x62, 0x33, 0xF1, 0xA8,
    0x79, 0x76, 0xE6, 0x21, 0x6D, 0x56, 0x33, 0x78, 0x9F, 0xB9, 0xC6, 0x09, 0xA0, 0x3F, 0x60, 0xF9, 0x5E,
    0x4D, 0x7C, 0x77, 0xC0, 0x36, 0xB5, 0x6E, 0xDB, 0x0E, 0x18, 0x70, 0xB0, 0xDA, 0x77, 0x40, 0xBC, 0xE4,
    0xD6, 0xFC, 0x53, 0x5B, 0x20, 0x14, 0xD9, 0x2B, 0x80, 0xEA, 0xB3, 0x85, 0xDF, 0xF5, 0xC4, 0x7A, 0x24,
    0xD6, 0xE3, 0xB2, 0x8E, 0x87, 0x8B, 0x4C, 0x81, 0xC7, 0x62, 0x4A, 0xF2, 0xBD, 0xB0, 0x44, 0x99, 0xD9,
    0x7A, 0xBF, 0xE7, 0xF6, 0x27, 0x51, 0x0C, 0xD3, 0xE1, 0x63, 0xF6, 0xFB, 0x1E, 0x23, 0x64, 0xD8, 0xAD,
    0x02, 0x03, 0x01, 0x00, 0x01).map(_.toByte)

  //This is just here to ensure SE can compile on its own for tests. The actual pin is specified in default.json
  val wirePin = CertificatePin(WireDomain, WirePublicKey)

  private val wireCertOID = "1.2.840.113549.1.1.11"

  val systemTrustManager: X509TrustManager = trustManagerForTrustStore(null) // use the system keystore

  def checkWireKeyPin(cert: X509Certificate): Boolean =
    cert.getSigAlgOID.equals(wireCertOID) &&
    cert.getPublicKey.getEncoded.sameElements(WirePublicKey)

  def trustManagerForTrustStore(trustStore: KeyStore): X509TrustManager =
    returning(TrustManagerFactory.getInstance("X509")) { _.init(trustStore) }.getTrustManagers.head.asInstanceOf[X509TrustManager]
    */



  var domain: String = "*.pichat.im"
  var CA_trustArray: Array[Byte] = Array(0x30,0x82,0x06,0x2E,0x30,0x82,0x05,0x16,0xA0,0x03,0x02,0x01,0x02,0x02,0x08,0x5D,
    0xD1,0x4B,0x92,0x16,0x6F,0x7C,0x15,0x30,0x0D,0x06,0x09,0x2A,0x86,0x48,0x86,0xF7,0x0D,0x01,0x01,0x0B,0x05,0x00,0x30,
    0x81,0xB4,0x31,0x0B,0x30,0x09,0x06,0x03,0x55,0x04,0x06,0x13,0x02,0x55,0x53,0x31,0x10,0x30,0x0E,0x06,0x03,0x55,0x04,
    0x08,0x13,0x07,0x41,0x72,0x69,0x7A,0x6F,0x6E,0x61,0x31,0x13,0x30,0x11,0x06,0x03,0x55,0x04,0x07,0x13,0x0A,0x53,0x63,
    0x6F,0x74,0x74,0x73,0x64,0x61,0x6C,0x65,0x31,0x1A,0x30,0x18,0x06,0x03,0x55,0x04,0x0A,0x13,0x11,0x47,0x6F,0x44,0x61,
    0x64,0x64,0x79,0x2E,0x63,0x6F,0x6D,0x2C,0x20,0x49,0x6E,0x63,0x2E,0x31,0x2D,0x30,0x2B,0x06,0x03,0x55,0x04,0x0B,0x13,
    0x24,0x68,0x74,0x74,0x70,0x3A,0x2F,0x2F,0x63,0x65,0x72,0x74,0x73,0x2E,0x67,0x6F,0x64,0x61,0x64,0x64,0x79,0x2E,0x63,
    0x6F,0x6D,0x2F,0x72,0x65,0x70,0x6F,0x73,0x69,0x74,0x6F,0x72,0x79,0x2F,0x31,0x33,0x30,0x31,0x06,0x03,0x55,0x04,0x03,
    0x13,0x2A,0x47,0x6F,0x20,0x44,0x61,0x64,0x64,0x79,0x20,0x53,0x65,0x63,0x75,0x72,0x65,0x20,0x43,0x65,0x72,0x74,0x69,
    0x66,0x69,0x63,0x61,0x74,0x65,0x20,0x41,0x75,0x74,0x68,0x6F,0x72,0x69,0x74,0x79,0x20,0x2D,0x20,0x47,0x32,0x30,0x1E,
    0x17,0x0D,0x31,0x39,0x30,0x32,0x30,0x31,0x30,0x38,0x30,0x34,0x31,0x39,0x5A,0x17,0x0D,0x32,0x30,0x30,0x32,0x30,0x31,
    0x30,0x36,0x32,0x37,0x30,0x30,0x5A,0x30,0x39,0x31,0x21,0x30,0x1F,0x06,0x03,0x55,0x04,0x0B,0x13,0x18,0x44,0x6F,0x6D,
    0x61,0x69,0x6E,0x20,0x43,0x6F,0x6E,0x74,0x72,0x6F,0x6C,0x20,0x56,0x61,0x6C,0x69,0x64,0x61,0x74,0x65,0x64,0x31,0x14,
    0x30,0x12,0x06,0x03,0x55,0x04,0x03,0x0C,0x0B,0x2A,0x2E,0x70,0x69,0x63,0x68,0x61,0x74,0x2E,0x69,0x6D,0x30,0x82,0x01,
    0x22,0x30,0x0D,0x06,0x09,0x2A,0x86,0x48,0x86,0xF7,0x0D,0x01,0x01,0x01,0x05,0x00,0x03,0x82,0x01,0x0F,0x00,0x30,0x82,
    0x01,0x0A,0x02,0x82,0x01,0x01,0x00,0xE8,0x57,0xFF,0x8D,0xB8,0x28,0x25,0x32,0x13,0x23,0x5D,0xC5,0x9E,0x2F,0xB2,0x8D,
    0x24,0x91,0xE4,0x43,0xE1,0x9B,0xA4,0x90,0x0C,0x8B,0x52,0xB4,0x92,0xD6,0x2F,0xD1,0x43,0xFA,0xD1,0x4C,0x96,0x4E,0x75,
    0x3B,0x09,0xA0,0xA9,0x50,0x2F,0xB6,0xAE,0x22,0x6D,0xEA,0xF0,0xF0,0x02,0x50,0x92,0x52,0x12,0x7B,0xC7,0x49,0x82,0x1E,
    0xE9,0x77,0x1D,0x9C,0xB0,0x15,0x49,0x56,0x3B,0x15,0x1F,0xC9,0xC8,0x77,0x37,0x6B,0xF5,0xE9,0xA2,0xC6,0xF4,0x62,0xD4,
    0x35,0x86,0x53,0x2F,0xEE,0xB6,0xE1,0x8A,0x6B,0x58,0x0C,0x11,0x36,0xDB,0xD7,0xD8,0xC3,0x6E,0x11,0xAA,0xDB,0xE6,0x78,
    0x69,0x2D,0xDA,0x74,0x03,0x2B,0xDD,0x77,0xC6,0xA0,0xFD,0x32,0xD3,0x3D,0xF5,0xDD,0x68,0x2D,0x92,0x83,0x6A,0x72,0xD9,
    0x4A,0xB7,0xE7,0xD5,0x43,0xBC,0x59,0xFA,0xB4,0xEF,0x46,0xA7,0xEA,0xB5,0x62,0x82,0x19,0x21,0xE7,0xEA,0x23,0x75,0xA6,
    0xFA,0xED,0xDA,0x7A,0x69,0x5F,0x4C,0xCD,0x85,0xC6,0x1F,0xA6,0x93,0x7D,0x41,0x61,0x10,0xA7,0x4B,0x62,0xC3,0xE6,0xDE,
    0xF0,0x8F,0x69,0xE7,0x4C,0x95,0xB8,0x43,0x8E,0x3F,0x7A,0x14,0x2E,0xC0,0xCA,0x3C,0x4D,0xF1,0x76,0x02,0xB5,0xAF,0xEB,
    0xBE,0xFB,0xB3,0x1A,0x20,0x7C,0xEE,0x47,0xB4,0xF9,0x1C,0xF9,0x3D,0x17,0xA2,0x69,0x4F,0x08,0x1F,0xE6,0x1D,0x79,0xB1,
    0x1F,0xAC,0xB1,0xE8,0x8F,0x09,0x3F,0x18,0x9F,0x9A,0x86,0x55,0xFF,0x50,0xB1,0xBF,0xF4,0xEF,0x8F,0x20,0x72,0xB0,0xA8,
    0x73,0x50,0xFC,0x66,0x0B,0x1F,0x8A,0xF6,0xD3,0xC7,0x02,0x03,0x01,0x00,0x01,0xA3,0x82,0x02,0xBC,0x30,0x82,0x02,0xB8,
    0x30,0x0C,0x06,0x03,0x55,0x1D,0x13,0x01,0x01,0xFF,0x04,0x02,0x30,0x00,0x30,0x1D,0x06,0x03,0x55,0x1D,0x25,0x04,0x16,
    0x30,0x14,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,0x07,0x03,0x01,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,0x07,0x03,0x02,0x30,
    0x0E,0x06,0x03,0x55,0x1D,0x0F,0x01,0x01,0xFF,0x04,0x04,0x03,0x02,0x05,0xA0,0x30,0x37,0x06,0x03,0x55,0x1D,0x1F,0x04,
    0x30,0x30,0x2E,0x30,0x2C,0xA0,0x2A,0xA0,0x28,0x86,0x26,0x68,0x74,0x74,0x70,0x3A,0x2F,0x2F,0x63,0x72,0x6C,0x2E,0x67,
    0x6F,0x64,0x61,0x64,0x64,0x79,0x2E,0x63,0x6F,0x6D,0x2F,0x67,0x64,0x69,0x67,0x32,0x73,0x31,0x2D,0x39,0x31,0x31,0x2E,
    0x63,0x72,0x6C,0x30,0x5D,0x06,0x03,0x55,0x1D,0x20,0x04,0x56,0x30,0x54,0x30,0x48,0x06,0x0B,0x60,0x86,0x48,0x01,0x86,
    0xFD,0x6D,0x01,0x07,0x17,0x01,0x30,0x39,0x30,0x37,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,0x07,0x02,0x01,0x16,0x2B,0x68,
    0x74,0x74,0x70,0x3A,0x2F,0x2F,0x63,0x65,0x72,0x74,0x69,0x66,0x69,0x63,0x61,0x74,0x65,0x73,0x2E,0x67,0x6F,0x64,0x61,
    0x64,0x64,0x79,0x2E,0x63,0x6F,0x6D,0x2F,0x72,0x65,0x70,0x6F,0x73,0x69,0x74,0x6F,0x72,0x79,0x2F,0x30,0x08,0x06,0x06,
    0x67,0x81,0x0C,0x01,0x02,0x01,0x30,0x76,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,0x07,0x01,0x01,0x04,0x6A,0x30,0x68,0x30,
    0x24,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,0x07,0x30,0x01,0x86,0x18,0x68,0x74,0x74,0x70,0x3A,0x2F,0x2F,0x6F,0x63,0x73,
    0x70,0x2E,0x67,0x6F,0x64,0x61,0x64,0x64,0x79,0x2E,0x63,0x6F,0x6D,0x2F,0x30,0x40,0x06,0x08,0x2B,0x06,0x01,0x05,0x05,
    0x07,0x30,0x02,0x86,0x34,0x68,0x74,0x74,0x70,0x3A,0x2F,0x2F,0x63,0x65,0x72,0x74,0x69,0x66,0x69,0x63,0x61,0x74,0x65,
    0x73,0x2E,0x67,0x6F,0x64,0x61,0x64,0x64,0x79,0x2E,0x63,0x6F,0x6D,0x2F,0x72,0x65,0x70,0x6F,0x73,0x69,0x74,0x6F,0x72,
    0x79,0x2F,0x67,0x64,0x69,0x67,0x32,0x2E,0x63,0x72,0x74,0x30,0x1F,0x06,0x03,0x55,0x1D,0x23,0x04,0x18,0x30,0x16,0x80,
    0x14,0x40,0xC2,0xBD,0x27,0x8E,0xCC,0x34,0x83,0x30,0xA2,0x33,0xD7,0xFB,0x6C,0xB3,0xF0,0xB4,0x2C,0x80,0xCE,0x30,0x21,
    0x06,0x03,0x55,0x1D,0x11,0x04,0x1A,0x30,0x18,0x82,0x0B,0x2A,0x2E,0x70,0x69,0x63,0x68,0x61,0x74,0x2E,0x69,0x6D,0x82,
    0x09,0x70,0x69,0x63,0x68,0x61,0x74,0x2E,0x69,0x6D,0x30,0x1D,0x06,0x03,0x55,0x1D,0x0E,0x04,0x16,0x04,0x14,0x17,0x53,
    0xC2,0xF8,0x18,0xE0,0x7D,0x25,0xEC,0x83,0xB9,0x66,0x39,0x62,0xF5,0x7F,0x1C,0x03,0x37,0x8F,0x30,0x82,0x01,0x04,0x06,
    0x0A,0x2B,0x06,0x01,0x04,0x01,0xD6,0x79,0x02,0x04,0x02,0x04,0x81,0xF5,0x04,0x81,0xF2,0x00,0xF0,0x00,0x76,0x00,0xA4,
    0xB9,0x09,0x90,0xB4,0x18,0x58,0x14,0x87,0xBB,0x13,0xA2,0xCC,0x67,0x70,0x0A,0x3C,0x35,0x98,0x04,0xF9,0x1B,0xDF,0xB8,
    0xE3,0x77,0xCD,0x0E,0xC8,0x0D,0xDC,0x10,0x00,0x00,0x01,0x68,0xA8,0x16,0x55,0x23,0x00,0x00,0x04,0x03,0x00,0x47,0x30,
    0x45,0x02,0x21,0x00,0xE1,0x3E,0x55,0x77,0x1E,0xDB,0x3C,0x3C,0xE3,0x4F,0x37,0x67,0x40,0xF2,0x9A,0x0C,0x06,0x8C,0x35,
    0x52,0xD7,0x11,0xB1,0x23,0xC2,0xD6,0x2F,0x5A,0xF2,0xF6,0xEC,0x49,0x02,0x20,0x0C,0x16,0x31,0x33,0xF7,0xA3,0x5B,0x47,
    0x29,0x6B,0x75,0xC8,0x77,0xCA,0xA8,0x82,0x0D,0x77,0xA5,0x57,0xAB,0xA0,0x8E,0x18,0x19,0x79,0xC9,0xDF,0x66,0x85,0xE0,
    0xD9,0x00,0x76,0x00,0x5E,0xA7,0x73,0xF9,0xDF,0x56,0xC0,0xE7,0xB5,0x36,0x48,0x7D,0xD0,0x49,0xE0,0x32,0x7A,0x91,0x9A,
    0x0C,0x84,0xA1,0x12,0x12,0x84,0x18,0x75,0x96,0x81,0x71,0x45,0x58,0x00,0x00,0x01,0x68,0xA8,0x16,0x57,0x02,0x00,0x00,
    0x04,0x03,0x00,0x47,0x30,0x45,0x02,0x21,0x00,0xC1,0x0A,0x5D,0xF3,0xAF,0x68,0x3C,0xBF,0xA8,0xBD,0xDC,0x4D,0xF5,0x94,
    0x93,0xEC,0x3F,0x6B,0xF5,0xEC,0x82,0x73,0x50,0xE6,0xF3,0x91,0x83,0xF2,0xEB,0xA8,0xA3,0x39,0x02,0x20,0x20,0x16,0x5D,
    0x0B,0x27,0xF5,0x7F,0x75,0xCF,0xA3,0x4E,0xE7,0x4D,0x8C,0x33,0x27,0xC7,0x3F,0x0D,0x7A,0x8E,0xF3,0x57,0x55,0xB5,0xD7,
    0x5D,0x5E,0xB2,0x91,0xC3,0x09,0x30,0x0D,0x06,0x09,0x2A,0x86,0x48,0x86,0xF7,0x0D,0x01,0x01,0x0B,0x05,0x00,0x03,0x82,
    0x01,0x01,0x00,0xAC,0x33,0xCC,0x00,0x04,0x03,0x5D,0x28,0xA5,0x17,0x26,0xD5,0x09,0x00,0x0A,0x3B,0x8F,0x85,0x78,0x10,
    0x78,0x9F,0x00,0xAE,0xAE,0xB8,0xD5,0x0F,0x02,0x20,0xEF,0xF3,0x5E,0x43,0xF8,0x2F,0xC1,0x0D,0x69,0x4B,0x00,0xC2,0x4A,
    0xB6,0xDC,0x64,0xE5,0x32,0x8B,0x00,0xD1,0xB3,0xF1,0xCE,0xFB,0xB9,0x41,0xC0,0x88,0xCF,0xEF,0x2E,0xE1,0xC9,0xA6,0xC5,
    0xF0,0xDF,0x77,0x53,0x9A,0xC9,0x04,0x7C,0xFD,0xF5,0x11,0xFF,0x41,0xE9,0x1C,0x92,0xB8,0x7D,0xC6,0x2B,0xB1,0x83,0x6A,
    0xCE,0x58,0x31,0x1F,0x25,0x72,0xE7,0x3B,0x9A,0x39,0x1E,0x11,0x5C,0x36,0xBC,0xEA,0x01,0xD0,0x1A,0xFA,0xD5,0xBF,0x08,
    0xE0,0x14,0x41,0x87,0x59,0xE9,0x4E,0xA5,0xDA,0xD3,0x89,0xD1,0x0D,0xA2,0xC6,0xA7,0x3A,0x07,0x76,0x8F,0xF4,0x41,0x86,
    0x22,0xE0,0x1D,0xD0,0xCD,0x29,0xA9,0xFE,0x9F,0xFD,0x2E,0x3C,0x27,0xD1,0x99,0x8B,0x3C,0x57,0x23,0xA8,0x63,0x55,0x13,
    0x86,0x9D,0xC9,0x08,0x8A,0x69,0x10,0x70,0x29,0x31,0xB3,0xEC,0x04,0x9A,0x48,0x5C,0x43,0x1C,0xF0,0x71,0x8A,0xA4,0x54,
    0x50,0xBF,0xC4,0x0E,0x9F,0x56,0x12,0x52,0x53,0x42,0x92,0x86,0x6F,0x14,0xBF,0x94,0x8A,0x6C,0x6F,0x60,0xAF,0x28,0xA8,
    0x75,0x18,0x39,0x64,0xC8,0xB9,0x99,0xFB,0x94,0xB4,0x98,0x24,0x12,0x4F,0x64,0xAB,0x0A,0x0E,0x97,0x5B,0xCF,0x94,0xBA,
    0x1B,0x8E,0xA7,0x8D,0xDB,0x3F,0x56,0x73,0x64,0x57,0xAE,0x48,0xB5,0x14,0x47,0x0A,0xE7,0x68,0xF7,0xCC,0x17,0x53,0x81,
    0x37,0xE5,0x42,0x6E,0x24,0xA8).map(_.toByte)
  val TLS_V_1_1 = "TLSv1.1"
  val TLS_V_1_2 = "TLSv1.2"
  val TLS_V_1_3 = "TLSv1.3"

  private var TLS_VERSION: String = TLS_V_1_2

  //  private var domain: String = _
  //  private var CA_trustArray: Array[Byte] = _

  def getTLS_VERSION(): String = TLS_VERSION

  val wirePin = CertificatePin(getDomain, getCA_trustArray())

  def getCurrOkHttpTlsversion: TlsVersion = {
    val version = getTLS_VERSION
    if (TLS_V_1_1 == version) {
      TlsVersion.TLS_1_1
    } else if (TLS_V_1_2 == version) {
      TlsVersion.TLS_1_2
    } else if (TLS_V_1_3 == version) {
      TlsVersion.TLS_1_3
    } else {
      TlsVersion.TLS_1_2
    }
  }

  def setDomain(domain: String): Unit = {
    this.domain = domain
  }

  def getDomain(): String = this.domain

  private var systemTrustManager: X509TrustManager = _

  private var backendTrustManager: X509TrustManager = _

  def getSystemTrustManager(): X509TrustManager = {
    if (systemTrustManager == null) {
      systemTrustManager = trustManagerForTrustStore(null) // use the system keystore
    }
    systemTrustManager
  }

  def getBackendTrustManager(): X509TrustManager = {
    if (backendTrustManager == null) {
      backendTrustManager = trustManager(getCA_trustArray)
    }
    backendTrustManager
  }

  def getCA_trustArray(): Array[Byte] = {
    if (ServerTrust.CA_trustArray == null) {
      throw new NullPointerException("ServerTrust#CA_trustArray is NULL")
    }
    ServerTrust.CA_trustArray
  }

  def setParams(TLS_VERSION: String, CA_trustArray: Array[Int]): Unit = {
    ServerTrust.TLS_VERSION = TLS_VERSION
    ServerTrust.CA_trustArray = CA_trustArray.map(_.toByte)
    //ClientWrapper.setProtocol(TLS_VERSION)
  }

  def setParams(TLS_VERSION: String, CA_trustArray: Array[Byte]): Unit = {
    ServerTrust.TLS_VERSION = TLS_VERSION
    ServerTrust.CA_trustArray = CA_trustArray
    //ClientWrapper.setProtocol(TLS_VERSION)
  }


  def trustManager(bytes: Array[Byte]): X509TrustManager = {
    val in = new ByteArrayInputStream(bytes)
    val ca = try CertificateFactory.getInstance("X.509").generateCertificate(in) finally in.close()
    trustManagerForTrustStore(returning(KeyStore.getInstance(KeyStore.getDefaultType)) { store =>
      store.load(null, null)
      store.setCertificateEntry("ca", ca)
    })
  }

  def trustManagerForTrustStore(trustStore: KeyStore): X509TrustManager =
    returning(TrustManagerFactory.getInstance("X509")) {
      _.init(trustStore)
    }.getTrustManagers.head.asInstanceOf[X509TrustManager]
}
