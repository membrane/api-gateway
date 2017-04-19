package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.util.ByteUtil;
import com.sun.javaws.exceptions.InvalidArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class WebSocketFrame {

    protected static Logger log = LoggerFactory.getLogger(WebSocketFrame.class.getName());

    final static int INITIAL_BUFFER_SIZE = 8192;

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

    public WebSocketFrame(){

    }

    private String calcError() {
        if (payloadLength < 2)
            throw new IllegalStateException("Error code not read.");
        return String.valueOf(ByteBuffer.wrap(payload, 0, 2).getShort());
    }

    public void write(OutputStream out) throws IOException {
        byte[] result = new byte[getSizeInBytes()];

        byte finAndReservedAndOpcode = 0;
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode,0, finalFragment);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode,1,rsv1);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode,2,rsv2);
        finAndReservedAndOpcode = ByteUtil.setBitValueBigEndian(finAndReservedAndOpcode,3,rsv3);
        finAndReservedAndOpcode = ByteUtil.setBitValuesBigEndian(finAndReservedAndOpcode,4,7,opcode);

        byte maskAndPayloadLength = 0;
        maskAndPayloadLength = ByteUtil.setBitValueBigEndian(maskAndPayloadLength,0,this.isMasked);
        int additionalPayloadBytes = getExtendedPayloadSize(computePayloadField());

        maskAndPayloadLength = ByteUtil.setBitValuesBigEndian(maskAndPayloadLength,1,7, computePayloadField());

        result[0] = finAndReservedAndOpcode;
        result[1] = maskAndPayloadLength;

        byte[] additionalPayloadLength = null;

        if(additionalPayloadBytes == 2){
            byte[] extendedPayloadLength = ByteBuffer.allocate(4).putInt((int)payloadLength).array();
            byte[] correctedExtendedPayloadLength = new byte[2];
            for(int i = 2; i < extendedPayloadLength.length;i++)
                correctedExtendedPayloadLength[i-2] = extendedPayloadLength[i];
            additionalPayloadLength = correctedExtendedPayloadLength;
        }
        if(additionalPayloadBytes == 8){
            additionalPayloadLength = ByteBuffer.allocate(8).putLong((int)payloadLength).array();
        }
        if(additionalPayloadLength != null)
            for(int i = 0; i < additionalPayloadBytes; i++){
                result[2+i] = additionalPayloadLength[i];
            }


        int maskKeyLength = isMasked ? maskKey.length : 0;
        for(int i = 0; i < maskKeyLength; i++)
            result[2+additionalPayloadBytes+i] = maskKey[i];

        int payloadOffset = 2 + additionalPayloadBytes + maskKeyLength;
        System.arraycopy(payload, 0, result, payloadOffset, (int)payloadLength);

        if(isMasked){
            int maskIndex = 0;
            for(int i = 0; i < payloadLength; i++) {
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

    private int getExtendedPayloadSize(int size){
        if(size >= 126){
            if(size == 126){
                return 2;
            }else{
                return 8;
            }
        }else
            return 0;
    }

    private int getSizeInBytes() {
        return 2 + getExtendedPayloadSize(computePayloadField()) + (maskKey != null ? maskKey.length : 0) + (payload != null ? (int)payloadLength : 0);
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
    }

    /**
     * @param buffer
     * @param offset
     * @param length
     * @return the number of bytes read. if > 0, this class has been properly initialized with the frame data read.
     */
    public int tryRead(byte[] buffer, int offset, int length) {
        if(length == 0)
            return 0;

        int origOffset = offset;

        byte finAndReservedAndOpCode = buffer[offset++];
        finalFragment = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,0);
        if(!finalFragment)
            return 0;
        rsv1 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,1);
        rsv2 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,2);
        rsv3 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,3);
        opcode = ByteUtil.getValueOfBits(finAndReservedAndOpCode, 4,7);

        byte maskAndPayloadLength = buffer[offset++];
        isMasked = ByteUtil.getBitValueBigEndian(maskAndPayloadLength,0);
        payloadLength = ByteUtil.getValueOfBits(maskAndPayloadLength,1,7);
        if(payloadLength >= 126){
            if(payloadLength == 126){
                byte[] newPayloadLength = new byte[4];
                for(int i = 2; i < newPayloadLength.length;i++)
                    newPayloadLength[i] = buffer[offset++];
                payloadLength = ByteBuffer.wrap(newPayloadLength).getInt();
            }else{
                byte[] newPayloadLength = new byte[8];
                for(int i = 0; i < newPayloadLength.length;i++)
                    newPayloadLength[i] = buffer[offset++];
                payloadLength = ByteBuffer.wrap(newPayloadLength).getLong();
            }
        }

        if(isMasked) {
            for (int i = 0; i < 4; i++)
                maskKey[i] = buffer[offset++];
        }

        if(payloadLength > Integer.MAX_VALUE || payloadLength < 0) {
            log.warn("Payload of ws message is bigger than Integer.MAX_VALUE which is currently not supported. Message will be truncated");
            payloadLength = Integer.MAX_VALUE;
        }
        // ensure that 'payload' buffer is large enough
        if (payload.length < payloadLength)
            payload = new byte[(int)payloadLength];

        int maskIndex = 0;
        for(int i = 0; i < payloadLength; i++) {
            payload[i] = buffer[offset++];
            if(isMasked)
                payload[i] = (byte) (payload[i] ^ maskKey[maskIndex]);
            maskIndex = (maskIndex + 1) % 4;
        }

        // TODO: directly set the fields (or better: use them during parsing)

        if(opcode == 8)
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
                ", payload=" + new String(payload, 0, (int)payloadLength) +
                '}';
    }
}
