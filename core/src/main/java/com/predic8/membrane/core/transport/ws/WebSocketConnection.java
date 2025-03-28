/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ws;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.EmptyBody;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.model.AbstractExchangeViewerListener;
import com.predic8.membrane.core.transport.http.TwoWayStreaming;
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

import static com.predic8.membrane.core.http.Header.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * An RFC 6455 implementation.
 */
public abstract class WebSocketConnection {
    public static final String WEBSOCKET_CLOSED_POLL_INTERVAL_MILLISECONDS = "websocket.closed-poll-interval-ms";

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnection.class);
    private static final byte[] mask = new byte[4];
    private static final String WEBSOCKET_PROTOCOL_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ArrayBlockingQueue<String> messagesToSend = new ArrayBlockingQueue<>(1000);
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
        res.getHeader().add(SEC_WEBSOCKET_ACCEPT, computeKeyResponse(exc.getRequest().getHeader().getFirstValue(SEC_WEBSOCKET_KEY)));
        res.setBody(new EmptyBody());
        return res;
    }

    private static @NotNull String getThreadName(TwoWayStreaming handler) {
        return "WebSocket Reader " + handler.getRemoteDescription();
    }

    private boolean isRelevantForMe(Exchange exc) {
        return exc.getRequest().isGETRequest() &&
                "websocket".equalsIgnoreCase(exc.getRequest().getHeader().getUpgradeProtocol());
    }

    static String computeKeyResponse(String key) {
        String t = key + WEBSOCKET_PROTOCOL_UUID;
        MessageDigest dig;
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
