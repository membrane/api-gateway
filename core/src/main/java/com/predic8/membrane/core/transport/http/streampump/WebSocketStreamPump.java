/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http.streampump;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.tunnel.WebSocketInterceptor;
import com.predic8.membrane.core.proxies.Proxy;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketFrameTooLargeException;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class WebSocketStreamPump extends StreamPump {
    protected static final Logger log = LoggerFactory.getLogger(WebSocketStreamPump.class.getName());

    private static final SecureRandom random = new SecureRandom();

    //pumpsToRight == true: from sender to recipient "sender -> recipient"
    public WebSocketStreamPump(InputStream in, OutputStream out, StreamPumpStats stats, String name, Proxy proxy, boolean pumpsToRight, Exchange originalExchange) {
        super(in, out, stats, name, proxy);
        this.pumpsToRight = pumpsToRight;
        final var webSocket = WebSocketInterceptor.findIn(proxy);
        webSocket.ifPresent(ws -> chain = ws.getFlow());
        maxMessageSize = webSocket.map(WebSocketInterceptor::getMaxMessageSize).orElse(WebSocketFrameAssembler.DEFAULT_MAX_PAYLOAD_SIZE);
        frameAssembler = new WebSocketFrameAssembler(in, originalExchange, maxMessageSize);
    }

    public void init(WebSocketStreamPump otherStreamPump) {
        this.otherStreamPump = otherStreamPump;
    }

    List<WebSocketInterceptorInterface> chain = new ArrayList<>();
    WebSocketStreamPump otherStreamPump;
    private final boolean pumpsToRight;
    boolean connectionIsOpen = true;
    final WebSocketFrameAssembler frameAssembler;
    private final int maxMessageSize;

    /**
     * Fragments of the data message read so far, null between messages. Only touched by this pump's thread.
     */
    private PendingMessage pendingMessage;

    @Override
    public void run() {
        if (otherStreamPump == null)
            throw new RuntimeException("Call init with other WebSocketStreamPump (backward direction)");
        if (stats != null)
            stats.registerPump(this);
        try {
            frameAssembler.readFrames(frame -> {
                bytesTransferred.addAndGet(frame.getSizeInBytes());
                handleFrame(frame);
            });
        } catch (WebSocketFrameTooLargeException e) {
            connectionIsOpen = false;
            log.info("Closing WebSocket connection, {} sent a frame that is too large: {}", senderName(), e.getMessage());
            sendClose(CLOSE_MESSAGE_TOO_BIG, false);
        } catch (CloseConnectionException e) {
            connectionIsOpen = false;
            if (e.statusCode == CLOSE_INTERNAL_ERROR)
                log.error("Closing WebSocket connection with status {}: {}", e.statusCode, e.getMessage(), e.getCause());
            else
                log.info("Closing WebSocket connection with status {}, {} {}", e.statusCode, senderName(), e.getMessage());
            sendClose(e.statusCode, e.closeReceiver);
        } catch (Exception e) {
            connectionIsOpen = false;
            String entity = (pumpsToRight ? "client to server" : "server to client");

            log.debug("",e);
            log.info("Connection from {}: {}", entity,e.fillInStackTrace().toString());
        } finally {
            try {
                out.close();
            } catch (Exception e) {
                // ignore
            }
            if (stats != null)
                stats.unregisterPump(this);
        }
    }

    /**
     * Hands whole messages to the frame interceptors: the fragments of a message are joined, control frames
     * (which are never fragmented) pass right away, even between the fragments of a message. Changing the
     * fragmentation is allowed as no extension is negotiated when frame interceptors are configured
     * (RFC 6455 5.4), see {@link WebSocketInterceptor}.
     */
    private void handleFrame(WebSocketFrame frame) {
        if (frame.getOpcode() >= OPCODE_CLOSE) {
            if (frame.getOpcode() == OPCODE_CLOSE)
                pendingMessage = null;
            passOn(frame);
            return;
        }
        if (frame.getOpcode() == OPCODE_CONTINUATION) {
            if (pendingMessage == null)
                throw new CloseConnectionException(CLOSE_PROTOCOL_ERROR, false, "sent a continuation frame without a message to continue.");
            pendingMessage.append(frame);
            if (!frame.isFinalFragment())
                return;
            final var message = pendingMessage.toFrame();
            pendingMessage = null;
            passOn(message);
            return;
        }
        if (pendingMessage != null)
            throw new CloseConnectionException(CLOSE_PROTOCOL_ERROR, false, "started a new message before the previous one was complete.");
        if (frame.isFinalFragment()) {
            passOn(frame);
            return;
        }
        pendingMessage = new PendingMessage(frame);
        pendingMessage.append(frame);
    }

    private void passOn(WebSocketFrame frame) {
        try {
            if (pumpsToRight)
                passFrameToChainElement(0, true, frame);
            else
                passFrameToChainElement(chain.size() - 1, false, frame);
        } catch (RelayWriteException e) {
            throw e;
        } catch (Exception e) {
            throw new CloseConnectionException(CLOSE_INTERNAL_ERROR, true, "a frame interceptor failed.", e);
        }
    }

    private String senderName() {
        return pumpsToRight ? "client" : "server";
    }

    /**
     * Sends a close frame with the status code to the peer this pump reads from and, if requested, to the peer it
     * writes to. Frames towards the server have to be masked.
     */
    private void sendClose(int statusCode, boolean closeReceiver) {
        writeClose(otherStreamPump.out, !pumpsToRight, statusCode);
        if (closeReceiver)
            writeClose(out, pumpsToRight, statusCode);
    }

    private static void writeClose(OutputStream target, boolean toServer, int statusCode) {
        final var maskKey = new byte[4];
        if (toServer)
            random.nextBytes(maskKey);
        final var close = new WebSocketFrame(true, false, false, false, OPCODE_CLOSE, toServer, maskKey,
                new byte[]{(byte) (statusCode >> 8), (byte) statusCode});
        try {
            synchronized (target) {
                close.write(target);
            }
        } catch (IOException e) {
            log.debug("Could not send close frame.", e);
        }
    }

    private void passFrameToChainElement(int i, boolean frameTravelsToRight, WebSocketFrame frame) throws Exception {
        if (chain.isEmpty()) {
            write(frame, out);
            return;
        }
        if (i == -1) {
            write(frame, pumpsToRight ? otherStreamPump.out : out);
        } else if (chain.size() == i) {
            write(frame, pumpsToRight ? out : otherStreamPump.out);
        } else {
            chain.get(i).handleFrame(frame, frameTravelsToRight, frame1 -> passFrameToChainElement(i + (frameTravelsToRight ? 1 : -1), frameTravelsToRight, frame1));
        }
    }

    /**
     * A failing write means the peer's connection is gone, which is not an interceptor failure.
     */
    private static void write(WebSocketFrame frame, OutputStream target) {
        try {
            synchronized (target) {
                frame.write(target);
            }
        } catch (IOException e) {
            throw new RelayWriteException(e);
        }
    }

    private static final int OPCODE_CONTINUATION = 0;
    private static final int OPCODE_CLOSE = 8;

    private static final int CLOSE_PROTOCOL_ERROR = 1002;
    private static final int CLOSE_MESSAGE_TOO_BIG = 1009;
    private static final int CLOSE_INTERNAL_ERROR = 1011;

    /**
     * Ends the connection with a close frame. Thrown while frames are read, handled in {@link #run()}.
     */
    private static final class CloseConnectionException extends RuntimeException {

        final int statusCode;
        final boolean closeReceiver;

        CloseConnectionException(int statusCode, boolean closeReceiver, String message) {
            this(statusCode, closeReceiver, message, null);
        }

        CloseConnectionException(int statusCode, boolean closeReceiver, String message, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.closeReceiver = closeReceiver;
        }
    }

    private static final class RelayWriteException extends UncheckedIOException {
        RelayWriteException(IOException cause) {
            super(cause);
        }
    }

    /**
     * A fragmented data message, collected until its final fragment arrives.
     */
    private final class PendingMessage {

        private final int opcode;
        private final boolean masked;
        private final byte[] maskKey;
        private final ByteArrayOutputStream payload = new ByteArrayOutputStream();

        PendingMessage(WebSocketFrame first) {
            opcode = first.getOpcode();
            masked = first.isMasked();
            maskKey = first.getMaskKey().clone();
        }

        void append(WebSocketFrame fragment) {
            if (payload.size() + fragment.getPayloadLength() > maxMessageSize)
                throw new CloseConnectionException(CLOSE_MESSAGE_TOO_BIG, false,
                        "sent a message larger than the maximum of %d bytes.".formatted(maxMessageSize));
            payload.write(fragment.getPayload(), 0, (int) fragment.getPayloadLength());
        }

        WebSocketFrame toFrame() {
            return new WebSocketFrame(true, false, false, false, opcode, masked, maskKey, payload.toByteArray());
        }
    }
}
