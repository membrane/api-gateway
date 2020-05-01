package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

/**
 * The FrameSender instance synchronized access to the OutputStream as well as the Encoder.
 */
public class FrameSender implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(FrameSender.class.getName());
    private static final int TYPE_STOP = -1;

    private final OutputStream out;
    private final Encoder encoder;
    private final Settings peerSettings;
    private final ArrayBlockingQueue<Frame> queue = new ArrayBlockingQueue<>(80);

    public FrameSender(OutputStream out, Encoder encoder, Settings peerSettings) {
        this.out = out;
        this.encoder = encoder;
        this.peerSettings = peerSettings;
    }

    public void send(Frame frame) throws IOException {
        try {
            if (!queue.offer(frame)) {
                long now = System.nanoTime();
                queue.put(frame);
                long enter = System.nanoTime();
                if (enter - now > 10 * 1000 * 1000)
                    log.info("waited " + (enter - now ) / 1000000 + "ms for queue");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void send(int streamId, FrameProducer frameProducer) throws IOException {
        long now = System.nanoTime();
        synchronized (this) {
            long enter = System.nanoTime();
            if (enter - now > 10 * 1000 * 1000)
                log.warn("Took " + ((enter - now) / 1000000) + "ms to acquire lock (streamId=" + streamId + ").");

            for (Frame frame : frameProducer.call(encoder, peerSettings)) {
                send(frame);
            }
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                Frame frame = queue.poll();
                if (frame == null) {
                    out.flush();
                    while (frame == null)
                        frame = queue.poll(10, TimeUnit.MINUTES);
                }

                if (frame.getType() == TYPE_STOP)
                    break;

                if (log.isTraceEnabled())
                    log.trace("sending: " + frame);
                else if (log.isDebugEnabled())
                    log.debug("sending: " + frame.getTypeString() + " length=" + frame.getLength());

                frame.write(out);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        log.debug("frame sender shutdown");
    }

    public void stop() {
        Frame e = new Frame();
        e.fill(TYPE_STOP, 0, 0, null, 0, 0);
        queue.add(e);
    }
}
