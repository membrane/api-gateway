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

package com.predic8.membrane.core.transport.http.client.protocol;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.transport.http.*;

/**
 * <p>
 * Contract for pluggable, application-layer protocols that can be negotiated by
 * {@code HttpClient}. Implementations include HTTP/1.1, HTTP/2(h2c), WebSocket,
 * and raw TCP tunnels.
 * </p>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *     <li>{@link #canHandle(Exchange, String)} ? Cheap capability check; <strong>no</strong> I/O.</li>
 *     <li>{@link #checkUpgradeRequest(Exchange)} ? Validate request headers <em>before</em> sending.</li>
 *     <li>{@link #handle(Exchange, ConnectionFactory.OutgoingConnectionType, HostColonPort)} ? Full I/O conversation.</li>
 *     <li>{@link #checkUpgradeResponse(Exchange)} ? Inspect upstream response (e.g. {@code 101 Switching Protocols}).</li>
 *     <li>{@link #cleanup(Exchange)} ? Release protocol-specific resources; always invoked exactly once.</li>
 * </ol>
 *
 * <h2>Thread-Safety & State</h2>
 * <p>
 * Implementations should be stateless and therefore thread-safe. Per-connection or per-exchange state
 * (e.g. HTTP/2 multiplexers) must be stored on the {@link Exchange} via
 * {@link Exchange#setProperty(String, Object)} using well-defined keys.
 * </p>
 *
 * @since   6.3
 */
public interface ProtocolHandler {

    /** Property key set on the {@link Exchange} once a successful upgrade has completed. */
    String UPGRADED_PROTOCOL = "UPGRADED_PROTOCOL";

    /**
     * Determines whether this handler can serve the requested {@code protocol} for the given {@link Exchange}.
     * <p>
     * Must be side-effect free; implementations typically inspect headers such as {@code Upgrade},
     * {@code :method}, or ALPN tokens.
     * </p>
     *
     * @param exchange current request/response context
     * @param protocol lowercase protocol token (e.g. {@code "http/1.1"}, {@code "h2c"}, {@code "websocket"})
     * @return {@code true} if this handler is capable
     * @throws ProtocolUpgradeDeniedException to actively veto the upgrade (e.g. security policy)
     */
    boolean canHandle(Exchange exchange, String protocol) throws ProtocolUpgradeDeniedException;

    /**
     * Executes the complete request/response exchange using the supplied socket type.
     * <p>
     * This method blocks until either the response has been fully read or an unrecoverable error occurs.
     * Implementations should honour cancellation via {@link Thread#interrupt()} where practical.
     * </p>
     *
     * @param exchange        current exchange
     * @param connectionType  connection semantics (plain, TLS, proxy tunnel ?)
     * @param target          resolved origin host (including port)
     * @throws Exception any transport, parsing, or protocol error
     */
    void handle(Exchange exchange, ConnectionFactory.OutgoingConnectionType connectionType, HostColonPort target) throws Exception;

    /**
     * Validates that the outbound request meets protocol requirements (header set, method allowed, etc.).
     * Invoked <em>before</em> bytes hit the wire, allowing an early, in-JVM failure.
     *
     * @param exchange current exchange
     * @throws ProtocolUpgradeDeniedException if validation fails
     */
    void checkUpgradeRequest(Exchange exchange) throws ProtocolUpgradeDeniedException;

    /**
     * Called after the upstream response headers are available. Implementations may inspect status code,
     * {@code Upgrade} / {@code Sec-WebSocket-Accept} headers, or ALPN outcomes and store decisions on the exchange.
     *
     * @param exchange current exchange
     */
    void checkUpgradeResponse(Exchange exchange);

    /**
     * Clean up protocol-specific artefacts (e.g. cancel ping schedulers) that are not covered by
     * {@code try-with-resources}. Always invoked exactly once regardless of success or failure.
     *
     * @param exchange current exchange
     */
    void cleanup(Exchange exchange);
}