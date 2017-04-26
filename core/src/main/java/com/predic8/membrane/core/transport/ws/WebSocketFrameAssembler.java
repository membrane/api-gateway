package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public class WebSocketFrameAssembler {

    protected static Logger log = LoggerFactory.getLogger(WebSocketFrameAssembler.class.getName());

    final static int BUFFER_SIZE = 8192;
    private final Exchange originalExchange;

    InputStream in;

    byte[] buffer = new byte[BUFFER_SIZE];

    public WebSocketFrameAssembler(InputStream in, Exchange originalExchange) {
        this.in = in;
        this.originalExchange = originalExchange;
    }

    public synchronized void readFrames(Consumer<WebSocketFrame> consumer) throws IOException {
        int read;
        WebSocketFrame frame = new WebSocketFrame();
        if(originalExchange != null)
            frame.setOriginalExchange(originalExchange);
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
