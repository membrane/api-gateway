/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http.HttpServerThreadFactory;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.transport.http2.frame.HeadersFrame;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The FrameSender instance synchronized access to the OutputStream as well as the Encoder.
 */
public class FrameSender implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(FrameSender.class.getName());
    private static final int TYPE_STOP = -1;

    private final OutputStream out;
    private final Encoder encoder;
    private final Settings peerSettings;
    private final Map<Integer, StreamInfo> streams;
    private final String remoteAddr;
    private final LinkedTransferQueue<Frame> queue = new LinkedTransferQueue<>();
    private final AtomicInteger totalBufferedFrames = new AtomicInteger(0);

    public FrameSender(OutputStream out, Encoder encoder, Settings peerSettings, Map<Integer, StreamInfo> streams, String remoteAddr) {
        this.out = out;
        this.encoder = encoder;
        this.peerSettings = peerSettings;
        this.streams = streams;
        this.remoteAddr = remoteAddr;
    }

    public void send(Frame frame) {
        if (frame.getType() == Frame.TYPE_DATA) {
            StreamInfo streamInfo = streams.get(frame.getStreamId());
            try {
                streamInfo.getBufferedDataFrames().acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            streamInfo.getDataFramesToBeSent().add(frame);
        } else {
            if (!queue.offer(frame)) {
                long now = System.nanoTime();
                queue.put(frame);
                long enter = System.nanoTime();
                if (enter - now > 10 * 1000 * 1000)
                    log.info("waited " + (enter - now) / 1000000 + "ms for queue");
            }
        }
        totalBufferedFrames.incrementAndGet();
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

    private Frame getNextFrame() {
        Frame frame = queue.poll();
        if (frame != null) {
            totalBufferedFrames.decrementAndGet();
            return frame;
        }
        // TODO: implement prioritization logic
        for (StreamInfo streamInfo : streams.values()) {
            frame = streamInfo.getDataFramesToBeSent().poll();
            if (frame != null) {
                streamInfo.getBufferedDataFrames().release();
                totalBufferedFrames.decrementAndGet();
                return frame;
            }
        }
        return null;
    }

    private Frame waitForNextFrame() throws InterruptedException {
        // TODO: improve waiting logic
        Thread.sleep(100);
        Frame frame = getNextFrame();
        return frame;
    }

    @Override
    public void run() {
        try {
            updateThreadName(true);
            while (true) {
                Frame frame = getNextFrame();
                if (frame == null) {
                    out.flush();
                    log.debug("found no frame to send, starting wait loop.");
                    while (frame == null)
                        frame = waitForNextFrame();
                    log.debug("found another frame to send.");
                }

                if (frame.getType() == TYPE_STOP)
                    break;

                if (frame.getType() == Frame.TYPE_RST_STREAM)
                    streams.get(frame.getStreamId()).sendRstStream();

                if (frame.getType() == Frame.TYPE_HEADERS)
                    streams.get(frame.getStreamId()).sendHeaders();

                if ((frame.getType() == Frame.TYPE_HEADERS ||
                        frame.getType() == Frame.TYPE_DATA)&&
                        (frame.getFlags() & HeadersFrame.FLAG_END_STREAM) != 0)
                    streams.get(frame.getStreamId()).sendEndStream();

                if (log.isTraceEnabled())
                    log.trace("sending: " + frame);
                else if (log.isDebugEnabled())
                    log.debug("sending: " + frame.getTypeString() + " length=" + frame.getLength());

                frame.write(out);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            updateThreadName(false);
        }
        log.debug("frame sender shutdown");
    }

    public void stop() {
        Frame e = new Frame();
        e.fill(TYPE_STOP, 0, 0, null, 0, 0);
        queue.add(e);
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP2 Frame Sender ");
            sb.append(remoteAddr);
            Thread.currentThread().setName(sb.toString());
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

}
