/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.ws.*;
import com.predic8.membrane.core.util.ConfigurationException;

import java.util.*;

/**
 * @description Records the WebSocket messages of recent connections in memory, so that the admin console can show
 * them with the call that opened the connection. Messages are passed on unchanged. Place it first in the flow to
 * record the messages as they arrive.
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - webSocket:
 *         flow:
 *           - wsRecord:
 *               maxMessages: 500
 *   target:
 *     host: localhost
 *     port: 8080
 * </code></pre>
 */
@MCElement(name = "wsRecord")
public class WebSocketRecordInterceptor implements WebSocketInterceptorInterface {

    /**
     * A recorded message or control frame.
     *
     * @param seq       position in the connection, starting at 1; older messages may have been dropped
     * @param payload   the payload, cut after {@code maxPayloadBytes}
     * @param length    length of the whole payload in bytes
     */
    public record RecordedMessage(long seq, long time, boolean clientToServer, int opcode, long length, byte[] payload,
                                  boolean truncated) {
    }

    /**
     * The newest messages of one connection. Thread safe.
     */
    public static final class Connection {
        private final int maxMessages;
        private final ArrayDeque<RecordedMessage> messages = new ArrayDeque<>();
        private long count;

        private Connection(int maxMessages) {
            this.maxMessages = maxMessages;
        }

        private synchronized void add(long time, boolean clientToServer, int opcode, byte[] payload, int maxPayloadBytes) {
            final var truncated = payload.length > maxPayloadBytes;
            final var kept = truncated ? Arrays.copyOf(payload, maxPayloadBytes) : payload.clone();
            messages.addLast(new RecordedMessage(++count, time, clientToServer, opcode, payload.length, kept, truncated));
            while (messages.size() > maxMessages)
                messages.removeFirst();
        }

        public synchronized List<RecordedMessage> messages() {
            return List.copyOf(messages);
        }

        /**
         * The kept messages and the number of messages seen, read together.
         */
        public record Snapshot(List<RecordedMessage> messages, long count) {
        }

        public synchronized Snapshot snapshot() {
            return new Snapshot(List.copyOf(messages), count);
        }

        /**
         * @return number of messages seen, including dropped ones
         */
        public synchronized long count() {
            return count;
        }
    }

    private int maxMessages = 1000;
    private int maxPayloadBytes = 65_536;
    private int maxConnections = 100;

    /**
     * Connections by the id of the exchange that opened them, least recently active first: a frame moves its
     * connection to the end, reading it in the admin console does not. Guarded by {@code this}.
     */
    private final LinkedHashMap<Long, Connection> connections = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Connection> eldest) {
            return size() > maxConnections;
        }
    };

    @Override
    public void init(Router router) {
        if (maxMessages < 1 || maxPayloadBytes < 0 || maxConnections < 1)
            throw new ConfigurationException("wsRecord: maxMessages and maxConnections must be at least 1, maxPayloadBytes at least 0.");
    }

    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        final var exchange = frame.getOriginalExchange();
        if (exchange != null) {
            final Connection connection;
            synchronized (this) {
                final var existing = connections.remove(exchange.getId());
                connection = existing != null ? existing : new Connection(maxMessages);
                connections.put(exchange.getId(), connection);
            }
            connection.add(System.currentTimeMillis(), frameTravelsToRight, frame.getOpcode(),
                    frame.getPayload() == null ? new byte[0] : frame.getPayload(), maxPayloadBytes);
        }
        sender.handleFrame(frame);
    }

    /**
     * @return the recorded connection opened by the exchange, if it is still kept
     */
    public synchronized Optional<Connection> connection(long exchangeId) {
        return Optional.ofNullable(connections.get(exchangeId));
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * @description Number of messages kept per connection; older ones are dropped.
     * @default 1000
     */
    @MCAttribute
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxPayloadBytes() {
        return maxPayloadBytes;
    }

    /**
     * @description Number of payload bytes kept per message; the rest is cut off.
     * @default 65536
     */
    @MCAttribute
    public void setMaxPayloadBytes(int maxPayloadBytes) {
        this.maxPayloadBytes = maxPayloadBytes;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * @description Number of connections whose messages are kept; the messages of the least recently active
     * connection are dropped first.
     * @default 100
     */
    @MCAttribute
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
}
