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

package com.predic8.membrane.core.interceptor.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.transport.ws.WebSocketConnection;
import com.predic8.membrane.core.transport.ws.WebSocketConnectionCollection;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.util.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

import static com.google.common.collect.ImmutableMap.of;
import static com.predic8.membrane.core.exceptions.ProblemDetails.user;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static com.predic8.membrane.core.util.TimerTaskUtil.createTimerTask;
import static java.time.Instant.now;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @description Acts as a WebSocket backend that pushes demo data, for trying out and testing WebSocket proxies.
 * It accepts the WebSocket upgrade and sends one JSON text message per interval with a running number, a timestamp
 * and a simulated measured value:
 * <pre><code>{"message":0,"timestamp":"2026-10-12T18:33:35.193Z","value":20.5}</code></pre>
 * <p>All connected clients receive the same messages, and the stream only advances while at least one client is
 * connected. Messages from clients are ignored. A request that is not a WebSocket upgrade is answered with status
 * 400. The plugin always ends the flow.</p>
 * <p>See <a href="https://github.com/membrane/api-gateway/blob/master/distribution/tutorials/websockets/10-WebSocket-Proxy.yaml">tutorials/websockets/10-WebSocket-Proxy.yaml</a>.</p>
 * @yaml
 * <pre><code>
 * api:
 *   port: 2000
 *   path:
 *     uri: /stream
 *   flow:
 *     - webSocketDemoStream:
 *         intervalSeconds: 5
 * </code></pre>
 */
@MCElement(name = "webSocketDemoStream")
public class WebSocketDemoStreamInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketDemoStreamInterceptor.class);

    private final WebSocketConnectionCollection connections = new WebSocketConnectionCollection();

    private int intervalSeconds = 1;

    /**
     * The stream's state. Only touched by the router's timer thread.
     */
    private long messageNumber;
    private double value = 20.0;

    @Override
    public void init() {
        super.init();
        if (intervalSeconds < 1)
            throw new ConfigurationException("webSocketDemoStream: intervalSeconds must be at least 1, but is %d.".formatted(intervalSeconds));
        router.getTimerManager().schedulePeriodicTask(createTimerTask(this::broadcast), intervalSeconds * 1000L, "webSocketDemoStream");
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (new DemoStreamConnection().handle(exc, connections) == RETURN)
            return RETURN;
        user(router.getConfiguration().isProduction(), getDisplayName())
                .status(400)
                .title("WebSocket upgrade expected")
                .detail("Connect with a WebSocket client to receive the demo stream.")
                .buildAndSetResponse(exc);
        return RETURN;
    }

    private void broadcast() {
        // The stream only advances while somebody is listening.
        if (connections.getSize() == 0)
            return;
        value = Math.round((value + ThreadLocalRandom.current().nextDouble(-1, 1)) * 10) / 10.0;
        try {
            connections.broadcast(of(
                    "message", messageNumber++,
                    "timestamp", ISO_INSTANT.format(now()),
                    "value", value));
        } catch (JsonProcessingException e) {
            log.error("", e); // should not happen
        }
    }

    private static class DemoStreamConnection extends WebSocketConnection {
        @Override
        public void onMessage(WebSocketFrame frame) {
            log.debug("Ignoring message from client: {}", frame);
        }
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    /**
     * @description Seconds between two messages. Must be at least 1.
     * @default 1
     * @example 5
     */
    @MCAttribute
    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    @Override
    public String getDisplayName() {
        return "WebSocket Demo Source";
    }
}
