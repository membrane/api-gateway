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
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    private final PeerFlowControl connectionFlowControl;
    private final String remoteAddr;
    private final LinkedTransferQueue<Frame> queue = new LinkedTransferQueue<>();
    private final AtomicInteger totalBufferedFrames = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition hasFrame = lock.newCondition();
    private volatile boolean stopped;

    public FrameSender(OutputStream out, Encoder encoder, Settings peerSettings, Map<Integer, StreamInfo> streams, String remoteAddr) {
        this.out = out;
        this.encoder = encoder;
        this.peerSettings = peerSettings;
        this.streams = streams;
        this.remoteAddr = remoteAddr;
        connectionFlowControl = new PeerFlowControl(0, this, peerSettings);
    }

    /** The peer's flow-control window for the whole connection. */
    public PeerFlowControl getConnectionFlowControl() {
        return connectionFlowControl;
    }

    public void send(Frame frame) {
        queue.put(frame);
        totalBufferedFrames.incrementAndGet();
        fire();
    }

    /**
     * Queues a DATA frame, waiting while the stream already has the maximum number of DATA frames buffered.
     *
     * @throws IOException if the sender has been stopped, so the frame would never be sent
     */
    public void sendData(Frame frame) throws IOException {
        StreamInfo streamInfo = streams.get(frame.getStreamId());
        if (streamInfo == null) {
            // Closed streams are removed and must not carry DATA anymore.
            log.debug("Dropping DATA for closed stream {}.", frame.getStreamId());
            dropped(frame);
            return;
        }
        try {
            streamInfo.getBufferedDataFrames().acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to send DATA on HTTP/2 stream " + frame.getStreamId());
        }
        // stop() releases a permit to wake a waiting writer.
        if (stopped)
            throw new IOException("HTTP/2 frame sender stopped");
        streamInfo.getDataFramesToBeSent().add(frame);
        totalBufferedFrames.incrementAndGet();
        // The stream may have been closed meanwhile, e.g. while waiting for buffer space. streamClosed() removes it
        // before draining its buffer, so if it is still mapped here, the drain will see the frame.
        if (streams.get(frame.getStreamId()) != streamInfo) {
            // Unless streamClosed() or the sender polled it already, nothing would send it.
            if (streamInfo.getDataFramesToBeSent().remove(frame)) {
                streamInfo.getBufferedDataFrames().release();
                totalBufferedFrames.decrementAndGet();
                dropped(frame);
            }
            log.debug("Dropping DATA for closed stream {}.", frame.getStreamId());
            return;
        }
        fire();
    }

    public boolean isStopped() {
        return stopped;
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
            if (streamInfo.getState() == StreamState.IDLE)
                continue;
            frame = streamInfo.getDataFramesToBeSent().poll();
            if (frame != null) {
                streamInfo.getBufferedDataFrames().release();
                totalBufferedFrames.decrementAndGet();
                return frame;
            }
        }
        return null;
    }

    /**
     * Forgets a stream that reached the CLOSED state, so closed streams neither accumulate nor count
     * against the peer's concurrent stream limit. DATA still buffered for it is dropped, which also
     * wakes a writer waiting for buffer space.
     */
    void streamClosed(StreamInfo streamInfo) {
        streams.remove(streamInfo.getStreamId(), streamInfo);
        Frame frame;
        while ((frame = streamInfo.getDataFramesToBeSent().poll()) != null) {
            streamInfo.getBufferedDataFrames().release();
            totalBufferedFrames.decrementAndGet();
            dropped(frame);
        }
    }

    /**
     * The writer reserved the connection's flow-control window for this DATA frame. The peer only grants window
     * again for DATA it received, so give it back, or the other streams on the connection run out of window.
     * The stream's own window does not matter anymore, as the stream is closed.
     */
    private void dropped(Frame frame) {
        connectionFlowControl.increment(frame.getLength());
    }

    private Frame waitForNextFrame() throws InterruptedException {
        lock.lock();
        try {
            hasFrame.await(1000, TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
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

                if (frame.getType() == Frame.TYPE_DATA || frame.getType() == Frame.TYPE_HEADERS || frame.getType() == Frame.TYPE_RST_STREAM) {
                    final var streamInfo = streams.get(frame.getStreamId());
                    // A closed stream is removed, possibly while its frames were queued, e.g. when the peer
                    // reset it. RST_STREAM is also sent ahead of DATA still buffered for the stream. A closed
                    // stream must not carry frames anymore (RFC 9113 §5.1), so drop them.
                    if (streamInfo == null || (frame.getType() == Frame.TYPE_DATA && streamInfo.getState() == StreamState.CLOSED)) {
                        // Except HEADERS: encoding them already updated the HPACK dynamic table. Dropping them would
                        // desynchronize the peer's decoder for all later header blocks on the connection. The peer
                        // still decodes header blocks on a closed stream (RFC 9113 §5.1, §4.3).
                        if (frame.getType() != Frame.TYPE_HEADERS) {
                            log.debug("Dropping {} for closed stream {}.", frame.getTypeString(), frame.getStreamId());
                            if (frame.getType() == Frame.TYPE_DATA)
                                dropped(frame);
                            continue;
                        }
                    } else {
                        if (frame.getType() == Frame.TYPE_RST_STREAM)
                            streamInfo.sendRstStream();

                        if (frame.getType() == Frame.TYPE_HEADERS)
                            streamInfo.sendHeaders();

                        if (frame.getType() != Frame.TYPE_RST_STREAM && (frame.getFlags() & HeadersFrame.FLAG_END_STREAM) != 0)
                            streamInfo.sendEndStream();
                    }
                }

                if (log.isTraceEnabled())
                    log.trace("sending: " + frame);
                else if (log.isDebugEnabled())
                    log.debug("sending: " + frame.getTypeString() + " length=" + frame.getLength());

                frame.write(out);
            }
        } catch (IOException e) {
            // Usually the connection was closed, deliberately or by the peer.
            log.debug("HTTP/2 frame sender stopped: {}", e.toString());
        } catch (Throwable e) {
            log.error("", e);
        } finally {
            updateThreadName(false);
        }
        log.debug("frame sender shutdown");
    }

    public void stop() {
        stopped = true;
        Frame e = new Frame();
        e.fill(TYPE_STOP, 0, 0, null, 0, 0);
        queue.add(e);
        fire();
        // Nothing is sent from now on: writers waiting for buffer space or flow-control window would wait forever.
        for (StreamInfo streamInfo : streams.values())
            streamInfo.senderStopped();
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            String sb = "HTTP2 Frame Sender " +
                    remoteAddr;
            Thread.currentThread().setName(sb);
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

    private void fire() {
        lock.lock();
        try {
            hasFrame.signal();
        } finally {
            lock.unlock();
        }
    }

}
