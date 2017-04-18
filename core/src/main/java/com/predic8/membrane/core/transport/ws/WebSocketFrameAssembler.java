package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.util.ByteUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class WebSocketFrameAssembler {

    final static int BUFFER_SIZE = 8192;

    InputStream in;
    Queue<Byte> readBytes;

    Queue<WebSocketFrame> readFrames;
    byte[] buffer = new byte[BUFFER_SIZE];

    public WebSocketFrameAssembler(InputStream in) {
        this.in = in;
        this.readBytes = new ArrayBlockingQueue<>(BUFFER_SIZE);

        readFrames = new LinkedBlockingQueue<>();
    }

    public synchronized void getNextFrame(Consumer<WebSocketFrame> consumer) throws IOException {
        int length = 0;
        while ((length = in.read(buffer)) > 0) {
            for (int i = 0; i < length; i++)
                this.readBytes.add(buffer[i]);

            while (constructFrameIfPossible()) ;
            while (!readFrames.isEmpty())
                consumer.accept(readFrames.poll());
        }
        getNextFrame(consumer);
    }

    private boolean constructFrameIfPossible() {
        if(readBytes.isEmpty())
            return false;

        byte finAndReservedAndOpCode = this.readBytes.poll();
        boolean fin = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,0);
        if(!fin)
            return false;
        boolean rsv1 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,1);
        boolean rsv2 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,2);
        boolean rsv3 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,3);
        int opcode = ByteUtil.getValueOfBits(finAndReservedAndOpCode, 4,7);

        byte maskAndPayloadLength = this.readBytes.poll();
        boolean mask = ByteUtil.getBitValueBigEndian(maskAndPayloadLength,0);
        int payloadLength = ByteUtil.getValueOfBits(maskAndPayloadLength,1,7);
        if(payloadLength >= 126) // extended payload is ignored for now
            throw new RuntimeException("NYI length >= 126");
        byte[] maskKey = null;
        if(mask) {
             maskKey = new byte[4];
            for (int i = 0; i < 4; i++)
                maskKey[i] = this.readBytes.poll();
        }

        byte[] payload = new byte[payloadLength];
        int maskIndex = 0;
        for(int i = 0; i < payloadLength; i++) {
            payload[i] = this.readBytes.poll();
            if(mask)
                payload[i] = (byte) (payload[i] ^ maskKey[maskIndex]);
            maskIndex = (maskIndex + 1) % 4;
        }

        WebSocketFrame frame = new WebSocketFrame(fin,rsv1,rsv2,rsv3,opcode,mask,payloadLength,maskKey,payload);
        this.readFrames.add(frame);

        System.out.println("|" + new String(payload) + "|");
        if(new String(payload).contains("SEND"))
            System.out.println("here");
        return true;
    }


}
