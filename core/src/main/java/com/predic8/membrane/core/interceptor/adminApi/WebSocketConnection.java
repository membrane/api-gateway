package com.predic8.membrane.core.interceptor.adminApi;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.transport.http.TwoWayStreaming;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import org.jetbrains.annotations.NotNull;
import org.jose4j.base64url.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ArrayBlockingQueue;

import static com.predic8.membrane.core.http.Header.CONNECTION;
import static com.predic8.membrane.core.http.Header.UPGRADE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public abstract class WebSocketConnection {
    public static final String WEBSOCKET_CLOSED_POLL_INTERVAL_MILLISECONDS = "websocket.closed-poll-interval-ms";

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnection.class);
    private static final byte[] mask = new byte[4];

    private ArrayBlockingQueue<String> messagesToSend = new ArrayBlockingQueue<>(1000);
    private WebSocketConnectionCollection connections;
    private WebSocketFrameAssembler frameAssembler;
    private OutputStream srcOut;
    private Thread readerThread;
    private TwoWayStreaming twoWayStreaming;
    private int closedPollIntervalMilliSeconds = 60000;

    public abstract void onMessage(WebSocketFrame frame);

    public Outcome handle(Exchange exc, WebSocketConnectionCollection connections) {
        if (exc.getProperty(WEBSOCKET_CLOSED_POLL_INTERVAL_MILLISECONDS) != null)
            closedPollIntervalMilliSeconds = (int) exc.getProperty(WEBSOCKET_CLOSED_POLL_INTERVAL_MILLISECONDS);
        this.connections = connections;
        if (isRelevantForMe(exc)) {
            handleInternal(exc);
            return Outcome.RETURN;
        }
        return Outcome.CONTINUE;
    }

    private void handleInternal(Exchange exc) {
        initialize(exc);

        exc.setResponse(getUpgradeResponse(exc));
        connections.register(this);

        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {

            @Override
            public void setExchangeFinished() {
                readerThread.start();
                sendMessagesFromQueueOrWait();
            }
        });
    }

    private void sendMessagesFromQueueOrWait() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (twoWayStreaming.isClosed())
                    return;

                String msg = messagesToSend.poll(closedPollIntervalMilliSeconds, MILLISECONDS);
                if (msg == null)
                    continue;

                WebSocketFrame wsf = new WebSocketFrame(true, false, false, false, 1, false, mask, msg.getBytes(UTF_8));
                log.trace("sending {}", wsf);

                wsf.write(srcOut);
            }
        } catch (InterruptedException e) {
            // do nothing
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initialize(Exchange exc) {
        twoWayStreaming = (TwoWayStreaming) exc.getHandler();
        frameAssembler = new WebSocketFrameAssembler(twoWayStreaming.getSrcIn(), exc);
        readerThread = new Thread(new FrameReader());
        readerThread.setName(getThreadName(twoWayStreaming));
        srcOut = twoWayStreaming.getSrcOut();

        try {
            twoWayStreaming.removeSocketSoTimeout();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull Response getUpgradeResponse(Exchange exc) {
        Response res = new Response();
        res.setStatusCode(101);
        res.setStatusMessage("Switching Protocols");
        res.getHeader().add(CONNECTION, UPGRADE);
        res.getHeader().add(UPGRADE, "websocket");
        res.getHeader().add("Sec-WebSocket-Accept", computeKeyResponse(exc.getRequest().getHeader().getFirstValue("Sec-WebSocket-Key")));
        res.setBody(new EmptyBody());
        return res;
    }

    private static @NotNull String getThreadName(TwoWayStreaming handler) {
        return "WebSocket Reader " + handler.getRemoteDescription();
    }


    private boolean isRelevantForMe(Exchange exc) {
        return exc.getRequest().getMethod().equals("GET") &&
                "websocket".equalsIgnoreCase(getUpgradeProtocol(exc.getRequest()));
    }

    private String getUpgradeProtocol(Request req) {
        if (req.getHeader().getSingleValues(CONNECTION).noneMatch(v -> v.equalsIgnoreCase(UPGRADE)))
            return null;
        return req.getHeader().getFirstValue(UPGRADE);
    }

    static String computeKeyResponse(String key) {
        String t = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest dig = null;
        try {
            dig = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = dig.digest(t.getBytes());
        return Base64.encode(hash);
    }

    public void enqueueForSending(String msg) {
        try {
            messagesToSend.add(msg);
        } catch (IllegalStateException e) {
            log.error("Closing websocket connection in adminApi: Queue is full.");
            try {
                if (twoWayStreaming.isClosed())
                    return;
                twoWayStreaming.close();
            } catch (IOException ex) {
                log.error("Could not close source socket.", ex);
            }
        }
    }

    private class FrameReader implements Runnable {
        @Override
        public void run() {
            try {
                frameAssembler.readFrames(frame -> {
                    try {
                        onMessage(frame);
                    } catch (Exception e) {
                        log.error("Error handling frame.", e);
                    }
                });

            } catch (SocketException e) {
                if (e.getMessage().contains("Connection reset"))
                    log.debug("AdminApi WebSocket connection closed.");
                else
                    log.error("Error while reading frames.", e);
            } catch (IOException e) {
                log.error("Error while reading frames.", e);
            } finally {
                connections.unregister(WebSocketConnection.this);
                try {
                    if (!twoWayStreaming.isClosed())
                        twoWayStreaming.close();
                } catch (IOException e) {
                    log.error("Error while closing handler", e);
                }

            }
        }
    }
}
