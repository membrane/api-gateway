package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http.Connection;
import com.predic8.membrane.core.transport.http.HttpServerThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static com.predic8.membrane.core.transport.http2.Http2Logic.getRemoteAddr;
import static com.predic8.membrane.core.transport.http2.Http2ServerHandler.PREFACE;

public class Http2Client implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(Http2Client.class);

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private final Connection con;
    private final CountDownLatch cdl;
    private final Http2Logic logic;
    private Response response;

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

        executor.submit(this);
    }

    public Response doCall(Exchange exc, Connection con) throws IOException, InterruptedException {
        int streamId = logic.nextClientStreamId.getAndAccumulate(2, Integer::sum);
        // TODO: check number of concurrent streams
        StreamInfo streamInfo = new StreamInfo(streamId, logic.sender, logic.peerSettings, logic.ourSettings);
        logic.streams.put(streamId, streamInfo);

        logic.sender.send(streamId, (encoder, peerSettings) -> createHeadersFrames(exc.getRequest(), streamId, encoder, peerSettings, false));

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
        // TODO
        return true;
    }
}
