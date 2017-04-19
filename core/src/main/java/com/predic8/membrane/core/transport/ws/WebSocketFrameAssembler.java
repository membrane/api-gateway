package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class WebSocketFrameAssembler {

    protected static Logger log = LoggerFactory.getLogger(WebSocketFrameAssembler.class.getName());

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

        Queue<Byte> readBytesCopy = new LinkedBlockingQueue<>(readBytes);

        byte finAndReservedAndOpCode = readBytesCopy.poll();
        boolean fin = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,0);
        if(!fin)
            return false;
        boolean rsv1 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,1);
        boolean rsv2 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,2);
        boolean rsv3 = ByteUtil.getBitValueBigEndian(finAndReservedAndOpCode,3);
        int opcode = ByteUtil.getValueOfBits(finAndReservedAndOpCode, 4,7);

        byte maskAndPayloadLength = readBytesCopy.poll();
        boolean mask = ByteUtil.getBitValueBigEndian(maskAndPayloadLength,0);
        long payloadLength = ByteUtil.getValueOfBits(maskAndPayloadLength,1,7);
        long givenPayLoadLength = payloadLength;
        if(payloadLength >= 126){
            if(payloadLength == 126){
                byte[] newPayloadLength = new byte[4];
                for(int i = 2; i < newPayloadLength.length;i++)
                    newPayloadLength[i] = readBytesCopy.poll();
                payloadLength = ByteBuffer.wrap(newPayloadLength).getInt();
            }else{
                byte[] newPayloadLength = new byte[8];
                for(int i = 0; i < newPayloadLength.length;i++)
                    newPayloadLength[i] = readBytesCopy.poll();
                payloadLength = ByteBuffer.wrap(newPayloadLength).getLong();
            }
        }
        byte[] maskKey = null;
        if(mask) {
             maskKey = new byte[4];
            for (int i = 0; i < 4; i++)
                maskKey[i] = readBytesCopy.poll();
        }

        if(payloadLength > Integer.MAX_VALUE || payloadLength < 0) {
            log.warn("Payload of ws message is bigger than Integer.MAX_VALUE which is currently not supported. Message will be truncated");
            payloadLength = Integer.MAX_VALUE;
        }
        byte[] payload = new byte[(int)payloadLength];
        int maskIndex = 0;
        for(int i = 0; i < payloadLength; i++) {
            payload[i] = readBytesCopy.poll();
            if(mask)
                payload[i] = (byte) (payload[i] ^ maskKey[maskIndex]);
            maskIndex = (maskIndex + 1) % 4;
        }

        WebSocketFrame frame = new WebSocketFrame(fin,rsv1,rsv2,rsv3,opcode,mask,givenPayLoadLength,maskKey,payload);
        this.readFrames.add(frame);
        this.readBytes = readBytesCopy;

        return true;
    }


}
