package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The FrameSender instance synchronized access to the OutputStream as well as the Encoder.
 */
public class FrameSender {
    private static final Logger log = LoggerFactory.getLogger(FrameSender.class.getName());

    private final OutputStream out;
    private final Encoder encoder;
    private final Settings peerSettings;

    public FrameSender(OutputStream out, Encoder encoder, Settings peerSettings) {
        this.out = out;
        this.encoder = encoder;
        this.peerSettings = peerSettings;
    }

    public void send(Frame frame) throws IOException {
        long now = System.nanoTime();
        synchronized (this) {
            long enter = System.nanoTime();
            if (enter - now > 1000000)
                log.warn("Took " + ((enter - now) / 1000) + "ms to acquire lock (streamId=" + frame.getStreamId() + ").");

            if (log.isTraceEnabled())
                log.trace("sending: " + frame);
            else if (log.isDebugEnabled())
                log.debug("sending: " + frame.getTypeString() + " length=" + frame.getLength());

            frame.write(out);
            out.flush();
            // TODO
        }
    }

    public void send(int streamId, FrameProducer frameProducer) throws IOException {
        long now = System.nanoTime();
        synchronized (this) {
            long enter = System.nanoTime();
            if (enter - now > 1000000)
                log.warn("Took " + ((enter - now) / 1000) + "ms to acquire lock (streamId=" + streamId + ").");

            for (Frame frame : frameProducer.call(encoder, peerSettings)) {
                send(frame);
            }
        }
    }
}
