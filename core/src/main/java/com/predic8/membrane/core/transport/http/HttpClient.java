/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. */

package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http.client.protocol.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.net.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.transport.http.client.protocol.Http2ProtocolHandler.*;
import static com.predic8.membrane.core.util.HttpUtil.*;

/**
 * HTTP client supporting:
 * - HTTP1
 * - HTTP/2
 * - proxy connections
 * - auto-retries with failover to other destinations
 * - connection pooling.
 *
 * <p>The HttpClient is designed to be thread-safe and can be used concurrently
 * from multiple threads.</p>
 */
public class HttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

    private final HttpClientConfiguration configuration;
    private final ConnectionFactory connectionFactory;
    private final ProtocolHandlerFactory protocolHandlerFactory;

    private StreamPump.StreamPumpStats streamPumpStats;

    public HttpClient() {
        this(null, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration configuration) {
        this(configuration, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration clientConfiguration, @Nullable TimerManager timerManager) {
        configuration = clientConfiguration != null ? clientConfiguration : new HttpClientConfiguration();
        connectionFactory = new ConnectionFactory(this.configuration, timerManager);
        protocolHandlerFactory = new ProtocolHandlerFactory(configuration, connectionFactory);
    }

    public Exchange call(Exchange exc) throws Exception {
        ProtocolHandler ph = protocolHandlerFactory.getHandler(exc, exc.getRequest().getHeader().getUpgradeProtocol());
        ph.checkUpgradeRequest(exc);
        configuration.getRetryHandler().executeWithRetries(exc, this::dispatchCall);
        ph.cleanup(exc);
        return exc;
    }

    private boolean dispatchCall(Exchange exc, String target, int attempt) throws Exception {
        HostColonPort hcp = initializeRequest(exc, target);

        OutgoingConnectionType outConType = connectionFactory.getConnection(exc, hcp, attempt);

        if (configuration.getProxy() != null && outConType.sslProvider() == null) {
            // if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
            exc.getRequest().getHeader().setProxyAuthorization(configuration.getProxy().getCredentials());
        }

        protocolHandlerFactory.getHandlerForConnection(exc, outConType).handle(exc, outConType, hcp);

        if (trackNodeStatus(exc)) {
            exc.setNodeStatusCode(attempt, exc.getResponse().getStatusCode());
        }

        // Check for protocol upgrades
        String upgradedProtocol = exc.getPropertyOrNull(UPGRADED_PROTOCOL, String.class);
        if (upgradedProtocol == null)
            return false;

        log.debug("Upgrading to {}",upgradedProtocol);

        StreamPump.setupConnectionForwarding(exc, outConType.con(), upgradedProtocol, streamPumpStats);
        outConType.con().setExchange(exc);
        return true;
    }

    HostColonPort initializeRequest(Exchange exc, String dest) throws IOException {
        setRequestURI(exc.getRequest(), dest);
        if (configuration.getAuthentication() != null)
            exc.getRequest().getHeader().setAuthorization(configuration.getAuthentication().getUsername(), configuration.getAuthentication().getPassword());

        HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
        if (configuration.isAdjustHostHeader() && (exc.getProxy() == null || exc.getProxy().isTargetAdjustHostHeader())) {
            exc.getRequest().getHeader().setHost(target.toString());
        }
        return target;
    }


    public void setStreamPumpStats(StreamPump.StreamPumpStats streamPumpStats) {
        this.streamPumpStats = streamPumpStats;
    }

    private static boolean trackNodeStatus(Exchange exc) {
        if (exc.getProperty(TRACK_NODE_STATUS) instanceof Boolean status)
            return status;
        return false;
    }

    @Override
    public void close() {
        connectionFactory.getConnectionManager().shutdownWhenDone();
        if (connectionFactory.getHttp2ClientPool() != null)
            connectionFactory.getHttp2ClientPool().shutdownWhenDone();
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    void setRequestURI(Request req, String dest) throws MalformedURLException {
        // Use complete URL with protocol and host. The proxy needs to know where to forward
        if (configuration.getProxy() != null || req.isCONNECTRequest()) {
            req.setUri(dest);
            return;
        }
        if (!dest.startsWith("http")) {
            throw new MalformedURLException("""
                    The exchange's destination URI %s does not start with 'http'. Specify a <target> within the API configuration or make sure the exchanges destinations list contains a valid URI.
                    """.formatted(dest));
        }
        req.setUri(getPathAndQueryString(dest));
    }

    /**
     * @param connect If true, do not use TLS even when the URL starts with https
     * @param dest    URL
     * @return HostColonPort
     * @throws MalformedURLException
     */
    private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException {
        if (connect)
            return new HostColonPort(false, dest);
        return HostColonPort.parse(dest);
    }

    // TODO Rewrite all clients to use try with resources and then remove it
    @Override
    protected void finalize() {
        close();
    }
}