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
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import static com.predic8.membrane.core.http.Header.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An RFC 6455 implementation.
 */
public abstract class WebSocketConnection {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnection.class);
    private static final byte[] mask = new byte[4];
    private static final String WEBSOCKET_PROTOCOL_UUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final int OPCODE_TEXT = 1;
    private static final int OPCODE_BINARY = 2;
    private static final int OPCODE_CLOSE = 8;
    private static final int OPCODE_PING = 9;
    private static final int OPCODE_PONG = 10;

    /**
     * Queued when the client is gone, to release the sender. Compared by identity.
     */
    private static final WebSocketFrame STOP = new WebSocketFrame();

    /**
     * Frames for the client. The sender thread is the only one writing to the client, so frames never interleave.
     */
    private final ArrayBlockingQueue<WebSocketFrame> framesToSend = new ArrayBlockingQueue<>(1000);
    private volatile boolean stopped;
    private WebSocketConnectionCollection connections;
    private WebSocketFrameAssembler frameAssembler;
    private OutputStream srcOut;
    private Thread readerThread;
    private TwoWayStreaming twoWayStreaming;

    /**
     * Called for every text or binary message from the client. Control frames are handled by the connection.
     */
    public abstract void onMessage(WebSocketFrame frame);

    public Outcome handle(Exchange exc, WebSocketConnectionCollection connections) {
        this.connections = connections;
        if (!isRelevantForMe(exc))
            return Outcome.CONTINUE;
        final var key = exc.getRequest().getHeader().getFirstValue(SEC_WEBSOCKET_KEY);
        if (key == null || key.isBlank()) {
            exc.setResponse(Response.badRequest("WebSocket upgrade without Sec-WebSocket-Key header.").build());
            return Outcome.RETURN;
        }
        handleInternal(exc);
        return Outcome.RETURN;
    }

    private void handleInternal(Exchange exc) {
        initialize(exc);

        exc.setResponse(getUpgradeResponse(exc));
        connections.register(this);

        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {

            @Override
            public void setExchangeFinished() {
                readerThread.start();
                sendFramesFromQueue();
            }
        });
    }

    /**
     * Runs on the connection's handler thread until the client is gone or the closing handshake is done, then closes
     * the connection.
     */
    private void sendFramesFromQueue() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                final var frame = framesToSend.take();
                if (frame == STOP)
                    return;

                log.trace("sending {}", frame);
                frame.write(srcOut);

                // RFC 6455 7.1.1: after its close frame, the server closes the TCP connection
                if (frame.getOpcode() == OPCODE_CLOSE)
                    return;
            }
        } catch (InterruptedException e) {
            // do nothing
        } catch (IOException e) {
            log.debug("Could not send to WebSocket client.", e);
        } finally {
            stopped = true;
            connections.unregister(this);
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (!twoWayStreaming.isClosed())
                twoWayStreaming.close();
        } catch (IOException e) {
            log.error("Could not close source socket.", e);
        }
    }

    /**
     * Releases the sender. If the queue is full, its frames are dropped: the client is gone anyway.
     */
    private void stop() {
        stopped = true;
        if (!framesToSend.offer(STOP)) {
            framesToSend.clear();
            framesToSend.offer(STOP);
        }
    }

    private static WebSocketFrame frameToClient(int opcode, byte[] payload) {
        return new WebSocketFrame(true, false, false, false, opcode, false, mask, payload);
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
        if (stopped)
            return;
        if (!framesToSend.offer(frameToClient(OPCODE_TEXT, msg.getBytes(UTF_8)))) {
            log.error("Closing WebSocket connection: send queue is full.");
            closeConnection(); // ends the reader, which releases the sender
        }
    }

    private class FrameReader implements Runnable {
        @Override
        public void run() {
            try {
                frameAssembler.readFrames(this::handleFrame);
            } catch (SocketException e) {
                log.debug("WebSocket connection closed: {}", e.getMessage());
            } catch (IOException e) {
                if (twoWayStreaming.isClosed())
                    log.debug("WebSocket connection closed: {}", e.getMessage());
                else
                    log.error("Error while reading frames.", e);
            } finally {
                stop();
            }
        }

        /**
         * The assembler reuses the frame for the next read, so payloads handed to the sender are copied.
         */
        private void handleFrame(WebSocketFrame frame) {
            switch (frame.getOpcode()) {
                case OPCODE_TEXT, OPCODE_BINARY -> {
                    try {
                        onMessage(frame);
                    } catch (Exception e) {
                        log.error("Error handling frame.", e);
                    }
                }
                case OPCODE_PING -> framesToSend.offer(frameToClient(OPCODE_PONG, payloadOf(frame, (int) frame.getPayloadLength())));
                // echo the status code, if any (RFC 6455 5.5.1)
                case OPCODE_CLOSE -> framesToSend.offer(frameToClient(OPCODE_CLOSE, payloadOf(frame, frame.getPayloadLength() >= 2 ? 2 : 0)));
                default -> {
                    // pongs and continuations are not expected from the admin console
                }
            }
        }

        private static byte[] payloadOf(WebSocketFrame frame, int length) {
            return Arrays.copyOf(frame.getPayload(), length);
        }
    }
}
