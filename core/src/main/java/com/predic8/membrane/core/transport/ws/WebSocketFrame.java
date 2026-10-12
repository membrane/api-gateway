/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.util.ByteUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class WebSocketFrame {

    final static int INITIAL_BUFFER_SIZE = 8192;

    Exchange originalExchange;
    private String error = null;
    boolean finalFragment;
    private boolean rsv1;
    private boolean rsv2;
    private boolean rsv3;
    int opcode;
    boolean isMasked;
    long payloadLength;
    final byte[] maskKey = new byte[4];
    byte[] payload = new byte[INITIAL_BUFFER_SIZE];

    public WebSocketFrame() {

    }

    public WebSocketFrame(boolean fin, boolean rsv1, boolean rsv2, boolean rsv3, int opcode, boolean isMasked, byte[] maskingKey, byte[] payload) {
        this.finalFragment = fin;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.opcode = opcode;
        this.isMasked = isMasked;
        payloadLength = payload.length;
        System.arraycopy(maskingKey,0,this.maskKey,0,maskingKey.length);
        System.arraycopy(payload,0,this.payload,0,payload.length);
    }

    /**
     * @return the status code of a close frame, or null if it has none. The body of a close frame is optional
     * (RFC 6455 5.5.1). An invalid one-byte body is passed on as well: the receiving endpoint rejects it.
     */
    private String calcError() {
        if (!hasCloseStatusCode())
            return null;
        return String.valueOf(ByteBuffer.wrap(payload, 0, 2).getShort());
    }

    private boolean hasCloseStatusCode() {
        return opcode == 8 && payloadLength >= 2;
    }

    /**
     * Offset of the payload shown by {@link #toString()}, skipping a close frame's status code.
     */
    private int displayedPayloadOffset() {
        return hasCloseStatusCode() ? 2 : 0;
    }

    public void write(OutputStream out) throws IOException {
        byte[] result = new byte[getSizeInBytes()];

        byte finAndReservedAndOpcode = 0;
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode, 0, finalFragment);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode, 1, rsv1);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode, 2, rsv2);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode, 3, rsv3);
        finAndReservedAndOpcode = ByteUtil.setBitValuesBigEndian(finAndReservedAndOpcode, 4, 7, opcode);

        byte maskAndPayloadLength = 0;
        maskAndPayloadLength = ByteUtil.setBitValueBigEndian(maskAndPayloadLength, 0, this.isMasked);
        int additionalPayloadBytes = getExtendedPayloadSize(computePayloadField());

        maskAndPayloadLength = ByteUtil.setBitValuesBigEndian(maskAndPayloadLength, 1, 7, computePayloadField());

        result[0] = finAndReservedAndOpcode;
        result[1] = maskAndPayloadLength;

        byte[] additionalPayloadLength = null;

        if (additionalPayloadBytes == 2) {
            byte[] extendedPayloadLength = ByteBuffer.allocate(4).putInt((int) payloadLength).array();
            byte[] correctedExtendedPayloadLength = new byte[2];
            if (extendedPayloadLength.length - 2 >= 0)
                System.arraycopy(extendedPayloadLength, 2, correctedExtendedPayloadLength, 0, extendedPayloadLength.length - 2);
            additionalPayloadLength = correctedExtendedPayloadLength;
        }
        if (additionalPayloadBytes == 8) {
            additionalPayloadLength = ByteBuffer.allocate(8).putLong((int) payloadLength).array();
        }
        if (additionalPayloadLength != null)
            System.arraycopy(additionalPayloadLength, 0, result, 2, additionalPayloadBytes);


        int maskKeyLength = isMasked ? maskKey.length : 0;
        for (int i = 0; i < maskKeyLength; i++)
            result[2 + additionalPayloadBytes + i] = maskKey[i];

        int payloadOffset = 2 + additionalPayloadBytes + maskKeyLength;
        System.arraycopy(payload, 0, result, payloadOffset, (int) payloadLength);

        if (isMasked) {
            int maskIndex = 0;
            for (int i = 0; i < payloadLength; i++) {
                result[payloadOffset + i] = (byte) (result[payloadOffset + i] ^ maskKey[maskIndex]);
                maskIndex = (maskIndex + 1) % 4;
            }
        }

        out.write(result);
        out.flush();

    }

    private int computePayloadField() {
        if (payloadLength <= 125)
            return (int) payloadLength;
        if (payloadLength < (1 << 16))
            return 126;
        return 127;
    }

    private int getExtendedPayloadSize(int size) {
        if (size >= 126) {
            if (size == 126) {
                return 2;
            } else {
                return 8;
            }
        } else
            return 0;
    }

    /**
     * @return the size of the frame on the wire, header included
     */
    public int getSizeInBytes() {
        return 2 + getExtendedPayloadSize(computePayloadField()) + (isMasked ? maskKey.length : 0) + (payload != null ? (int) payloadLength : 0);
    }


    public int getOpcode() {
        return opcode;
    }

    public void setOpcode(int opcode) {
        this.opcode = opcode;
    }

    public boolean isMasked() {
        return isMasked;
    }

    public void setMasked(boolean masked) {
        isMasked = masked;
    }

    public long getPayloadLength() {
        return payloadLength;
    }

    public void setPayloadLength(long payloadLength) {
        this.payloadLength = payloadLength;
    }

    public byte[] getMaskKey() {
        return maskKey;
    }

    public void setMaskKey(byte[] maskKey) {
        if (maskKey.length != 4)
            throw new IllegalArgumentException("maskKey must have length 4.");
        System.arraycopy(maskKey, 0, this.maskKey, 0, 4);
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
        this.payloadLength = payload.length;
    }

    /**
     * @param buffer
     * @param offset
     * @param length
     * @param maxPayloadSize frames announcing a larger payload are rejected as soon as their header is complete
     * @return the number of bytes read. if > 0, this class has been properly initialized with the frame data read.
     * @throws WebSocketFrameTooLargeException if the frame announces a payload larger than {@code maxPayloadSize}
     */
    public int tryRead(byte[] buffer, int offset, int length, int maxPayloadSize) throws WebSocketFrameTooLargeException {
        if (length < 2)
            return 0;

        int origOffset = offset;

        byte finAndReservedAndOpCode = buffer[offset++];
        finalFragment = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode, 0);
        rsv1 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode, 1);
        rsv2 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode, 2);
        rsv3 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode, 3);
        opcode = ByteUtil.getValueOfBits(finAndReservedAndOpCode, 4, 7);

        byte maskAndPayloadLength = buffer[offset++];
        isMasked = ByteUtil.getBitValueBigEndian(maskAndPayloadLength, 0);
        payloadLength = ByteUtil.getValueOfBits(maskAndPayloadLength, 1, 7);

        int headerLength = 2 + getExtendedPayloadSize((int) payloadLength) + (isMasked ? 4 : 0);
        // the bytes behind 'length' are leftovers of earlier frames: never parse them as header fields
        if (length < headerLength)
            return 0;

        if (payloadLength >= 126) {
            if (payloadLength == 126) {
                byte[] newPayloadLength = new byte[4];
                for (int i = 2; i < newPayloadLength.length; i++)
                    newPayloadLength[i] = buffer[offset++];
                payloadLength = ByteBuffer.wrap(newPayloadLength).getInt();
            } else {
                byte[] newPayloadLength = new byte[8];
                for (int i = 0; i < newPayloadLength.length; i++)
                    newPayloadLength[i] = buffer[offset++];
                payloadLength = ByteBuffer.wrap(newPayloadLength).getLong();
            }
        }

        if (isMasked) {
            for (int i = 0; i < 4; i++)
                maskKey[i] = buffer[offset++];
        }

        // negative: the most significant bit is set, i.e. more than 2^63 bytes
        if (payloadLength < 0 || payloadLength > maxPayloadSize)
            throw new WebSocketFrameTooLargeException(payloadLength, maxPayloadSize);

        // if payloadLength is bigger than what can currently be in the buffer then we haven't read the whole frame
        if(payloadLength > length-headerLength)
            return 0;
        // ensure that 'payload' buffer is large enough
        if (payload.length < payloadLength)
            payload = new byte[(int) payloadLength];

        int maskIndex = 0;
        for (int i = 0; i < payloadLength; i++) {
            payload[i] = buffer[offset++];
            if (isMasked)
                payload[i] = (byte) (payload[i] ^ maskKey[maskIndex]);
            maskIndex = (maskIndex + 1) % 4;
        }

        // TODO: directly set the fields (or better: use them during parsing)

        if (opcode == 8)
            error = calcError();


        return offset - origOffset;
    }


    @Override
    public String toString() {
        return "WebSocketFrame{" +
                "error='" + error + '\'' +
                ", finalFragment=" + finalFragment +
                ", rsv1=" + rsv1 +
                ", rsv2=" + rsv2 +
                ", rsv3=" + rsv3 +
                ", opcode=" + opcode +
                ", isMasked=" + isMasked +
                ", payloadLength=" + payloadLength +
                (isMasked ? (", maskKey=" + Arrays.toString(maskKey)) : "") +
                ", payload=" + new String(payload, displayedPayloadOffset(), (int) payloadLength - displayedPayloadOffset()) +
                '}';
    }

    private String toHex(byte[] buffer, int offset, int length) {
        if (length == 0)
            return "";

        if (offset + length > buffer.length)
            throw new IllegalArgumentException();

        int i = -1;
        StringBuffer total = new StringBuffer();
        StringBuffer hex = new StringBuffer();
        StringBuffer ascii = new StringBuffer();

        while(true) {
            i++;

            byte b = buffer[offset + i];
            if (b < 32 || b >= 128)
                ascii.append('.');
            else
                ascii.append((char)b);

            for (int d = 0; d < 2; d++) {
                int e = b;
                if (e < 0)
                    e += 256;
                e = (d == 0 ? e / 16 : e) % 16;
                hex.append((char)(e < 10 ? e + '0' : e - 10 + 'A'));
            }

            if (i % 2 == 1)
                hex.append(' ');

            if (i == length-1) {
                while (hex.length() < 40)
                    hex.append(' ');
            }

            if (i % 16 == 15 || i == length-1) {
                total.append(hex.toString());
                total.append(' ');
                total.append(ascii.toString());
                total.append('\n');

                hex.delete(0, hex.length());
                ascii.delete(0, ascii.length());
            }

            if (i == length-1)
                break;
        }

        return total.toString();
    }

    public String toStringHex() {
        return "WebSocketFrame{" +
                "error='" + error + '\'' +
                ", finalFragment=" + finalFragment +
                ", rsv1=" + rsv1 +
                ", rsv2=" + rsv2 +
                ", rsv3=" + rsv3 +
                ", opcode=" + opcode +
                ", isMasked=" + isMasked +
                ", payloadLength=" + payloadLength +
                (isMasked ? (", maskKey=" + Arrays.toString(maskKey)) : "") +
                ", payload=\n" + toHex(payload, displayedPayloadOffset(), (int) payloadLength - displayedPayloadOffset()) +
                '}';
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        write(baos);
        return baos.toByteArray();
    }

    public Exchange getOriginalExchange() {
        return originalExchange;
    }

    public void setOriginalExchange(Exchange originalExchange) {
        this.originalExchange = originalExchange;
    }
}
