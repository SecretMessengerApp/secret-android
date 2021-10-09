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
package com.waz.message;

import com.google.protobuf.nano.*;
import com.wire.messages.nano.Otr;

import java.io.IOException;
import java.util.Arrays;

public final class CustomOtrMessage extends ExtendableMessageNano<CustomOtrMessage> {
    private static volatile CustomOtrMessage[] _emptyArray;
    public Otr.ClientId sender;
    public Otr.UserEntry[] recipients;
    public boolean nativePush;
    public byte[] blob;
    public boolean unblock;
    public boolean video;
    public String call_user_id;
    public String call_user_name;
    public String call_conversation_id;
    public String call_type;


    public static CustomOtrMessage[] emptyArray() {
        if (_emptyArray == null) {
            Object var0 = InternalNano.LAZY_INIT_LOCK;
            synchronized (InternalNano.LAZY_INIT_LOCK) {
                if (_emptyArray == null) {
                    _emptyArray = new CustomOtrMessage[0];
                }
            }
        }

        return _emptyArray;
    }

    public CustomOtrMessage() {
        this.clear();
    }

    public CustomOtrMessage clear() {
        this.sender = null;
        this.recipients = Otr.UserEntry.emptyArray();
        this.nativePush = true;
        this.blob = WireFormatNano.EMPTY_BYTES;
        this.unknownFieldData = null;
        this.cachedSize = -1;
        this.unblock = false;
        this.video = false;
        this.call_user_id = null;
        this.call_user_name = null;
        this.call_conversation_id = null;
        this.call_type = null;
        return this;
    }

    public void writeTo(CodedOutputByteBufferNano var1) throws IOException {
        if (this.sender != null) {
            var1.writeMessage(1, this.sender);
        }

        if (this.recipients != null && this.recipients.length > 0) {
            for (int var2 = 0; var2 < this.recipients.length; ++var2) {
                Otr.UserEntry var3 = this.recipients[var2];
                if (var3 != null) {
                    var1.writeMessage(2, var3);
                }
            }
        }

        if (!this.nativePush) {
            var1.writeBool(3, this.nativePush);
        }

        if (!Arrays.equals(this.blob, WireFormatNano.EMPTY_BYTES)) {
            var1.writeBytes(4, this.blob);
        }

        if (this.unblock) {
            var1.writeBool(100, this.unblock);
        }

        if (this.call_type != null && !"".equals(this.call_type)) {
            var1.writeBool(101, this.video);
            var1.writeString(102, this.call_user_id);
            var1.writeString(103, this.call_user_name);
            var1.writeString(104, this.call_conversation_id);
            var1.writeString(105, this.call_type);
        }

        super.writeTo(var1);
    }

    protected int computeSerializedSize() {
        int var1 = super.computeSerializedSize();
        if (this.sender != null) {
            var1 += CodedOutputByteBufferNano.computeMessageSize(1, this.sender);
        }

        if (this.recipients != null && this.recipients.length > 0) {
            for (int var2 = 0; var2 < this.recipients.length; ++var2) {
                Otr.UserEntry var3 = this.recipients[var2];
                if (var3 != null) {
                    var1 += CodedOutputByteBufferNano.computeMessageSize(2, var3);
                }
            }
        }

        if (!this.nativePush) {
            var1 += CodedOutputByteBufferNano.computeBoolSize(3, this.nativePush);
        }

        if (!Arrays.equals(this.blob, WireFormatNano.EMPTY_BYTES)) {
            var1 += CodedOutputByteBufferNano.computeBytesSize(4, this.blob);
        }

        if (this.unblock) {
            var1 += CodedOutputByteBufferNano.computeBoolSize(100, this.unblock);
        }

        if (this.call_type != null && !"".equals(this.call_type)) {
            var1 += CodedOutputByteBufferNano.computeBoolSize(101, this.video);
            var1 += CodedOutputByteBufferNano.computeStringSize(102, this.call_user_id);
            var1 += CodedOutputByteBufferNano.computeStringSize(103, this.call_user_name);
            var1 += CodedOutputByteBufferNano.computeStringSize(104, this.call_conversation_id);
            var1 += CodedOutputByteBufferNano.computeStringSize(105, this.call_type);
        }

        return var1;
    }

    public CustomOtrMessage mergeFrom(CodedInputByteBufferNano paramCodedInputByteBufferNano) throws IOException {
        while (true) {
            int var2 = paramCodedInputByteBufferNano.readTag();
            switch (paramCodedInputByteBufferNano.readTag()) {
                case 0:
                    return this;
                case 10:
                    if (this.sender == null) {
                        this.sender = new Otr.ClientId();
                    }

                    paramCodedInputByteBufferNano.readMessage(this.sender);
                    continue;
                case 18:
                    int var3 = WireFormatNano.getRepeatedFieldArrayLength(paramCodedInputByteBufferNano, 18);
                    int var4 = this.recipients == null ? 0 : this.recipients.length;
                    Otr.UserEntry[] arrayOfUserEntry = new Otr.UserEntry[var4 + var3];
                    if (var4 != 0) {
                        System.arraycopy(this.recipients, 0, arrayOfUserEntry, 0, var4);
                    }
                    while (var4 < arrayOfUserEntry.length - 1) {
                        arrayOfUserEntry[var4] = new Otr.UserEntry();
                        paramCodedInputByteBufferNano.readMessage(arrayOfUserEntry[var4]);
                        paramCodedInputByteBufferNano.readTag();
                        ++var4;
                    }

                    arrayOfUserEntry[var4] = new Otr.UserEntry();
                    paramCodedInputByteBufferNano.readMessage(arrayOfUserEntry[var4]);
                    this.recipients = arrayOfUserEntry;
                    break;
                case 24:
                    this.nativePush = paramCodedInputByteBufferNano.readBool();
                    continue;
                case 34:
                    this.blob = paramCodedInputByteBufferNano.readBytes();
                    continue;
                case 800:
                    this.unblock = paramCodedInputByteBufferNano.readBool();
                    break;
                case 808:
                    this.video = paramCodedInputByteBufferNano.readBool();
                    break;
                case 818:
                    this.call_user_id = paramCodedInputByteBufferNano.readString();
                    break;
                case 826:
                    this.call_user_name = paramCodedInputByteBufferNano.readString();
                    break;
                case 834:
                    this.call_conversation_id = paramCodedInputByteBufferNano.readString();
                    break;
                case 842:
                    this.call_type = paramCodedInputByteBufferNano.readString();
                    break;
                default:
                    if (this.storeUnknownField(paramCodedInputByteBufferNano, var2)) {
                        continue;
                    }
                    return this;
            }
        }
    }

    public static CustomOtrMessage parseFrom(byte[] var0) throws InvalidProtocolBufferNanoException {
        return MessageNano.mergeFrom(new CustomOtrMessage(), var0);
    }

    public static CustomOtrMessage parseFrom(CodedInputByteBufferNano var0) throws IOException {
        return (new CustomOtrMessage()).mergeFrom(var0);
    }
}


