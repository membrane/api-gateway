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
import com.predic8.membrane.core.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.util.concurrent.*;

import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.writeMessageBody;
import static com.predic8.membrane.core.transport.http2.Http2Logic.getRemoteAddr;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.PREFACE;

public class Http2Client implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(Http2Client.class);

    private static final ExecutorService executor = Util.createNewThreadPool();

    private final Connection con;
    private final CountDownLatch cdl;
    private final Http2Logic logic;
    private final Thread thread;
    private Response response;
    @GuardedBy("this")
    private int reserved;

    public Http2Client(Connection con, boolean showSSLExceptions) {
        this.con = con;
        this.cdl = new CountDownLatch(1);
        this.logic = new Http2Logic(executor, con.socket, con.in, con.out, showSSLExceptions, new Http2MessageHandler() {
            @Override
            public Message createMessage() {
                return new Response();
            }

            @Override
            public void handleExchange(StreamInfo streamInfo, Message message, boolean showSSLExceptions, String remoteAddr) {
                response = (Response)message;
                cdl.countDown();
            }
        });

        thread = new Thread(this);
        thread.start();
    }

    public Response doCall(Exchange exc, Connection con) throws IOException, InterruptedException {
        int streamId;
        synchronized(this) {
            if (reserved > 0)
                reserved--;
            streamId = logic.nextClientStreamId.getAndAccumulate(2, Integer::sum);
        }
        // TODO: check number of concurrent streams
        StreamInfo streamInfo = new StreamInfo(streamId, logic.sender, logic.peerSettings, logic.ourSettings);
        logic.streams.put(streamId, streamInfo);

        logic.sender.send(streamId, (encoder, peerSettings) -> createHeadersFrames(exc.getRequest(), streamId, encoder, peerSettings, false));

        writeMessageBody(streamId, streamInfo, logic.sender, logic.peerSettings, logic.peerFlowControl, exc.getRequest());

        // TODO: handle error/exception
        cdl.await();

        return response;
    }

    @Override
    public void run() {
        try {
            updateThreadName(true);
            LOG.debug("sending PREFACE.");
            con.out.write(PREFACE);

            logic.init();

            logic.handle();
        } catch (Exception e) {
            if (logic.receiving)
                e.printStackTrace();
            // TODO: notify threads waiting on responses
        } finally {
            updateThreadName(false);
        }
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP2 Client ");
            sb.append(getRemoteAddr(con.socket));
            Thread.currentThread().setName(sb.toString());
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

    public Connection getConnection() {
        return con;
    }

    public boolean reserveStream() {
        int max = logic.peerSettings.getMaxConcurrentStreams();
        if (max == -1)
            max = Integer.MAX_VALUE;

        synchronized(this) {
            int current = logic.streams.size() + reserved;
            if (current < 0 || current >= max)
                return false;
            reserved += 1;
            return true;
        }
    }

    public void close() {
        try {
            LOG.debug("stop receiving frames.");
            logic.receiving = false;
            logic.sender.send(GoawayFrame.construct(0, logic.nextClientStreamId.get(), 0));
            LOG.debug("terminating frame sender.");
            logic.sender.stop();
            try {
                logic.senderFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                // do nothing
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
