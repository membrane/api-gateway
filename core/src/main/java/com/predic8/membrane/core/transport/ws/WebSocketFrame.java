package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.util.ByteUtil;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WebSocketFrame {

    private String error = null;
    boolean finalFragment;
    private boolean rsv1;
    private boolean rsv2;
    private boolean rsv3;
    int opcode;
    boolean isMasked;
    long payloadLength;
    byte[] maskKey;
    byte[] payload;

    public WebSocketFrame(){

    }

    public WebSocketFrame(boolean finalFragment, boolean rsv1, boolean rsv2, boolean rsv3, int opcode, boolean mask, long payloadLength, byte[] maskKey, byte[] payload) {
        this.finalFragment = finalFragment;
        this.rsv1 = rsv1;
        this.rsv2 = rsv2;
        this.rsv3 = rsv3;
        this.opcode = opcode;
        this.isMasked = mask;
        this.payloadLength = payloadLength;
        this.maskKey = maskKey;
        this.payload = payload;


        if(opcode == 8)
            error = calcError();
    }

    private String calcError() {
        return String.valueOf(ByteBuffer.wrap(payload).getShort());
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
        int additionalPayloadBytes = getExtendedPayloadSize();

        maskAndPayloadLength = ByteUtil.setBitValuesBigEndian(maskAndPayloadLength,1,7,(int)this.payloadLength);

        result[0] = finAndReservedAndOpcode;
        result[1] = maskAndPayloadLength;

        byte[] additionalPayloadLength = null;

        if(additionalPayloadBytes == 2){
            byte[] extendedPayloadLength = ByteBuffer.allocate(4).putInt(payload.length).array();
            byte[] correctedExtendedPayloadLength = new byte[2];
            for(int i = 2; i < extendedPayloadLength.length;i++)
                correctedExtendedPayloadLength[i-2] = extendedPayloadLength[i];
            additionalPayloadLength = correctedExtendedPayloadLength;
        }
        if(additionalPayloadBytes == 8){
            additionalPayloadLength = ByteBuffer.allocate(8).putLong(payload.length).array();
        }
        if(additionalPayloadLength != null)
            for(int i = 0; i < additionalPayloadBytes; i++){
                result[2+i] = additionalPayloadLength[i];
            }


        int maskKeyLength = maskKey != null ? maskKey.length : 0;
        for(int i = 0; i < maskKeyLength; i++)
            result[2+additionalPayloadBytes+i] = maskKey[i];
        int payloadLength = payload != null ? payload.length : 0;
        byte[] newPayload = new byte[payload.length]; // copy to not have side-effects from possible masking
        for(int i = 0; i < payload.length;i++)
            newPayload[i] = payload[i];

        if(isMasked){
            int maskIndex = 0;
            for(int i = 0; i < payloadLength; i++) {
                newPayload[i] = (byte) (newPayload[i] ^ maskKey[maskIndex]);
                maskIndex = (maskIndex + 1) % 4;
            }
        }
        for(int i = 0; i < payloadLength; i++)
            result[2+additionalPayloadBytes + maskKeyLength + i] = newPayload[i];

        out.write(result);
        out.flush();

    }

    private int getExtendedPayloadSize(){
        if(this.payloadLength >= 126){
            if(this.payloadLength == 126){
                return 2;
            }else{
                return 8;
            }
        }else
            return 0;
    }

    private int getSizeInBytes() {
        return 2 + getExtendedPayloadSize() + (maskKey != null ? maskKey.length : 0) + (payload != null ? payload.length : 0);
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
        this.maskKey = maskKey;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }
}
