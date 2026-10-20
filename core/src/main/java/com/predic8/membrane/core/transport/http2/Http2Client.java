/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.HttpServerThreadFactory;
import com.predic8.membrane.core.transport.http2.frame.GoawayFrame;
import com.predic8.membrane.core.transport.http2.frame.RstStreamFrame;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.predic8.membrane.core.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.writeMessageBody;
import static com.predic8.membrane.core.transport.http2.Http2Logic.getRemoteAddr;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.PREFACE;
import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_CANCEL;

public class Http2Client implements Runnable, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Http2Client.class);

    private static final ExecutorService executor = Util.createNewThreadPool();

    private final ConcurrentHashMap<Integer, ResponseInfo> responses = new ConcurrentHashMap<>();
    private final Connection con;
    private final Http2Logic logic;
    @GuardedBy("this")
    private int reserved;
    private volatile boolean closed;

    static class ResponseInfo {
        private final CountDownLatch cdl = new CountDownLatch(1);
        // First completion wins: a disconnect must not overwrite a response that already arrived.
        private final AtomicBoolean completed = new AtomicBoolean();
        private Response response;
        private IOException failure;

        void complete(Response response) {
            if (!completed.compareAndSet(false, true))
                return;
            this.response = response;
            cdl.countDown();
        }

        /** @return whether this failure completed the call */
        boolean fail(IOException failure) {
            if (!completed.compareAndSet(false, true))
                return false;
            this.failure = failure;
            cdl.countDown();
            return true;
        }

        Response await() throws IOException, InterruptedException {
            cdl.await();
            if (failure != null)
                throw new IOException(failure.getMessage(), failure);
            return response;
        }
    }

    public Http2Client(Connection con, boolean showSSLExceptions) {
        this.con = con;
        this.logic = new Http2Logic(executor, con.socket, con.in, con.out, showSSLExceptions, new Http2MessageHandler() {
            @Override
            public Message createMessage() {
                Response response = new Response();
                response.getHeader().setValue("Transfer-Encoding", "chunked");
                return response;
            }

            @Override
            public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
                ResponseInfo ri = responses.get(streamInfo.getStreamId());
                if (ri != null)
                    ri.complete((Response) message);
            }

            @Override
            public void handleStreamReset(int streamId, int errorCode) {
                ResponseInfo ri = responses.get(streamId);
                if (ri != null)
                    ri.fail(new IOException("HTTP/2 stream %d was reset by the peer with error code %d.".formatted(streamId, errorCode)));
            }

            @Override
            public void handleOversizedHeaders(StreamInfo streamInfo, boolean endStream) {
                final var streamId = streamInfo.getStreamId();
                final var ri = responses.get(streamId);
                if (ri != null)
                    ri.fail(new IOException("HTTP/2 response headers on stream %d exceed the header size limit.".formatted(streamId)));
                if (!endStream)
                    cancel(streamId);
            }
        });

        Thread thread = new Thread(this);
        thread.start();
    }

    /**
     * @param retainBody whether the request body has to be retained, so that it can be sent again if
     *                   the call is retried
     */
    public Response doCall(Exchange exc, boolean retainBody) throws IOException, InterruptedException {
        int streamId;
        synchronized(this) {
            if (reserved > 0)
                reserved--;
            streamId = logic.nextClientStreamId.getAndAccumulate(2, Integer::sum);
        }
        // TODO: check number of concurrent streams
        ResponseInfo ri = new ResponseInfo();
        responses.put(streamId, ri);
        var streamOpened = false;
        try {
            // Every close path sets closed before draining responses, so a call
            // registered concurrently is either drained there or rejected here.
            if (closed)
                throw new IOException("HTTP/2 connection closed");

            StreamInfo streamInfo = new StreamInfo(streamId, logic.sender, logic.peerSettings, logic.ourSettings);
            logic.streams.put(streamId, streamInfo);
            streamOpened = true;

            logic.sender.send(streamId, (encoder, peerSettings) -> createHeadersFrames(exc.getRequest(), exc.getRequest().getHeader(), streamId, encoder, peerSettings, false));

            writeMessageBody(streamId, streamInfo, logic.sender, logic.peerSettings, logic.peerFlowControl, exc.getRequest(), retainBody);

            return ri.await();
        } catch (Exception e) {
            // E.g. the request body could not be read or the caller was interrupted: cancel the stream, otherwise it
            // stays open and the connection never gets idle. Even if the response already arrived, our side may still
            // be open. For a stream that is already closed, the frame sender drops the RST_STREAM.
            if (streamOpened) {
                ri.fail(new IOException("HTTP/2 call aborted", e));
                cancel(streamId);
            }
            throw e;
        } finally {
            responses.remove(streamId);
        }
    }

    @Override
    public void run() {
        try {
            updateThreadName(true);
            LOG.debug("sending PREFACE.");
            con.out.write(PREFACE);

            logic.init();

            logic.handle();
        } catch (IOException | EndOfStreamException e) {
            // Usually the peer closed the connection.
            if (logic.receiving)
                LOG.debug("HTTP/2 client stopped receiving: {}", e.toString());
        } catch (Exception e) {
            if (logic.receiving)
                LOG.warn("HTTP/2 client stopped receiving.", e);
        } finally {
            // No more responses can arrive once the reader stops, e.g. when the peer disconnects.
            closed = true;
            failPendingResponses();
            updateThreadName(false);
        }
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            Thread.currentThread().setName("HTTP2 Client " +
                    getRemoteAddr(con.socket));
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

    public Connection getConnection() {
        return con;
    }

    public boolean reserveStream() {
        int max = getMax();

        synchronized(this) {
            int current = logic.streams.size() + reserved;
            if (current < 0 || current >= max)
                return false;
            reserved += 1;
            return true;
        }
    }

    private int getMax() {
        int max = logic.peerSettings.getMaxConcurrentStreams();
        if (max == -1)
            return Integer.MAX_VALUE;
        return max;
    }

    /**
     * Aborts transport I/O without waiting for the frame sender. Router teardown
     * must not depend on the peer reading queued frames (including GOAWAY).
     */
    public void closeImmediately() {
        closed = true;
        logic.receiving = false;
        failPendingResponses();
        try {
            con.abort();
        } catch (IOException e) {
            LOG.debug("Could not abort HTTP/2 connection", e);
        } finally {
            // Also wake a sender waiting for work. This is safe before init()
            // has published senderFuture and does not require joining it.
            logic.stopSending();
        }
    }

    /** The frame sender closes the stream when it writes the RST_STREAM frame. */
    private void cancel(int streamId) {
        try {
            logic.sender.send(streamId, (encoder, peerSettings) -> List.of(RstStreamFrame.construct(streamId, ERROR_CANCEL)));
        } catch (IOException e) {
            LOG.debug("Could not cancel HTTP/2 stream {}: {}", streamId, e.toString());
        }
    }

    private void failPendingResponses() {
        for (var ri : responses.values())
            ri.fail(new IOException("HTTP/2 connection closed before a response was received"));
    }

    public void close() {
        try {
            LOG.debug("stop receiving frames.");
            closed = true;
            logic.receiving = false;
            failPendingResponses();
            logic.sender.send(GoawayFrame.construct(0, logic.nextClientStreamId.get(), 0));
            LOG.debug("terminating frame sender.");
            logic.stopSending();
            final var senderFuture = logic.senderFuture;
            if (senderFuture != null) {
                try {
                    senderFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    // do nothing
                }
            }

            con.close();
        } catch (IOException e) {
            // do nothing
        }
    }

    public boolean isIdle() {
        for (StreamInfo value : logic.streams.values()) {
            if (value.getState() != StreamState.CLOSED)
                return false;
        }
        return true;
    }
}
