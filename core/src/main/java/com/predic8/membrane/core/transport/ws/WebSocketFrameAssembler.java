package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class WebSocketFrameAssembler {

    protected static Logger log = LoggerFactory.getLogger(WebSocketFrameAssembler.class.getName());

    final static int BUFFER_SIZE = 8192;

    InputStream in;

    byte[] buffer = new byte[BUFFER_SIZE];

    public WebSocketFrameAssembler(InputStream in) {
        this.in = in;
    }

    public synchronized void readFrames(Consumer<WebSocketFrame> consumer) throws IOException {
        int read;
        WebSocketFrame frame = new WebSocketFrame();
        int offset = 0;
        int handled;
        while ((read = in.read(buffer, offset, buffer.length - offset)) > 0) {

            offset = offset + read;

            while ((handled = frame.tryRead(buffer, 0, offset)) > 0) {
                consumer.accept(frame);

                System.arraycopy(buffer, handled, buffer, 0, offset - handled);
                offset -= handled;
            }

        }
    }



}
