package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_DATA;

public class FrameSender {
    private static final Logger log = LoggerFactory.getLogger(FrameSender.class.getName());

    private final Http2ServerHandler http2ServerHandler;
    private final OutputStream out;
    private final Encoder encoder;
    private final Settings sendSettings;

    public FrameSender(Http2ServerHandler http2ServerHandler, OutputStream out, Encoder encoder, Settings sendSettings) {
        this.http2ServerHandler = http2ServerHandler;
        this.out = out;
        this.encoder = encoder;
        this.sendSettings = sendSettings;
    }

    public synchronized void send(Frame frame) throws IOException {
        if (log.isTraceEnabled())
            log.trace("sending: " + frame);
        else
            log.info("sending: " + frame.getTypeString() + " length=" + frame.getLength());

        if (frame.getType() == TYPE_DATA) {
            // TODO: does the frame length (which one?) or data length count?
            http2ServerHandler.getPeerFlowControl().used(frame.getLength());
            http2ServerHandler.getStreamInfo(frame.getStreamId()).getPeerFlowControl().used(frame.getLength());
        }

        frame.write(out);
        out.flush();
        // TODO
    }

    public synchronized void send(FrameProducer frameProducer) throws IOException {
        for (Frame frame : frameProducer.call(encoder, sendSettings)) {
            send(frame);
        }
        // TODO
    }
}
