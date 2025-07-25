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

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.*;
import static com.predic8.membrane.core.http.Response.*;
import static java.lang.Boolean.*;

/**
 * HttpClient with possibly multiple selectable destinations, with internal logic to auto-retry and to
 * switch destinations on failures.
 * Instances are thread-safe.
 */
public class HttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class.getName());

    public static final String HTTP2 = "h2";

    private final HttpClientConfiguration configuration;

    private StreamPump.StreamPumpStats streamPumpStats;

    private static volatile boolean infoOnHttp2Downgrade = true;

    private final ConnectionFactory connectionFactory;

    private final ProtocolHandlerFactory protocolHandlerFactory;

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
        return call(exc, true, true);
    }

    public Exchange call(Exchange exc, boolean adjustHostHeader, boolean failOverOn5XX) throws Exception {

        ProtocolHandler ph = protocolHandlerFactory.getHandler(exc, exc.getRequest().getHeader().getUpgradeProtocol());
        ph.checkUpgradeRequest(exc);

        Exchange exchange = configuration.getRetryHandler().executeWithRetries(exc,
                failOverOn5XX, (e, target, attempt)
                -> dispatchHttp1or2(e, attempt, initializeRequest(exc, target, adjustHostHeader), ph));

        if (exc.getRequest().isCONNECTRequest())
            return exchange;

        if (!isHTTP2(exc)) {
            Connection tc = exchange.getTargetConnection();
            if (tc != null) {
                applyKeepAliveHeader(exc.getResponse(), tc);
                exc.getResponse().addObserver(tc);
                tc.setExchange(exc);
            }
        }
        return exchange;
    }

    private boolean dispatchHttp1or2(Exchange exc, int counter, HostColonPort target, ProtocolHandler ph) throws Exception {
        OutgoingConnectionType outConType = connectionFactory.getConnection(exc, target, counter);

        if (configuration.getProxy() != null && outConType.sslProvider() == null)
            // if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
            exc.getRequest().getHeader().setProxyAuthorization(configuration.getProxy().getCredentials());

        ProtocolHandler handler;
        if (outConType.usingHttp2()) {
            handler = protocolHandlerFactory.getHandler(exc, "h2");
        } else {
            handler = protocolHandlerFactory.getHandler(exc, null); // HTTP/1.1
        }

        handler.handle(exc, outConType, target);

        if (trackNodeStatus(exc)) {
            exc.setNodeStatusCode(counter, exc.getResponse().getStatusCode());
        }

        String upgradedProtocol = exc.getPropertyOrNull("UPGRADED_PROTOCOL", String.class);
        if (upgradedProtocol != null) {
            StreamPump.setupConnectionForwarding(exc, outConType.con(), upgradedProtocol, streamPumpStats);
            outConType.con().setExchange(exc);
            return true;
        }

        return false;
    }

    /**
     * @param exc
     * @param oct
     * @param counter
     * @param ph
     * @return
     * @throws EndOfStreamException
     * @throws IOException
     */
    boolean checkUpgradeProtocolAndExecuteHttp1Call(Exchange exc, OutgoingConnectionType oct, int counter, ProtocolHandler ph) throws Exception {
        Response response;
        String newProtocol;

        if (exc.getRequest().isCONNECTRequest()) {
            handleConnectRequest(exc, oct.con());
            response = ok().build();
            newProtocol = METHOD_CONNECT;
            //TODO should we report to the httpClientStatusEventBus here somehow?
        } else {

            response = ph.handle(exc, oct,null).getResponse();

            if (trackNodeStatus(exc))
                exc.setNodeStatusCode(counter, response.getStatusCode());
            newProtocol = checkUpgradeProtocol(exc, response);
        }

        if (newProtocol != null) {
            StreamPump.setupConnectionForwarding(exc, oct.con(), newProtocol, streamPumpStats);
            oct.con().setExchange(exc);
            exc.setResponse(response);
            return true;
        }

        exc.setResponse(response);
        return false;
    }

    HostColonPort initializeRequest(Exchange exc, String dest, boolean adjustHostHeader) throws IOException {
        setRequestURI(exc.getRequest(), dest);

        if (configuration.getAuthentication() != null)
            exc.getRequest().getHeader().setAuthorization(configuration.getAuthentication().getUsername(), configuration.getAuthentication().getPassword());

        HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);
        if (adjustHostHeader && (exc.getProxy() == null || exc.getProxy().isTargetAdjustHostHeader())) {
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

    private String checkUpgradeProtocol(Exchange exc, Response response) {
        if (exc.getProperty(ALLOW_WEBSOCKET) == TRUE && isUpgradeToResponse(response, "websocket")) {
            log.debug("Upgrading to WebSocket protocol.");
            return "WebSocket";
            //TODO should we report to the httpClientStatusEventBus here somehow?
        }
        if (exc.getProperty(ALLOW_TCP) == TRUE && isUpgradeToResponse(response, "tcp")) {
            log.debug("Upgrading to TCP protocol.");
            return "TCP";
        }
        return null;
    }

    private void applyKeepAliveHeader(Response response, Connection con) {
        String value = response.getHeader().getFirstValue(KEEP_ALIVE);
        if (value == null)
            return;

        long timeoutSeconds = Header.parseKeepAliveHeader(value, TIMEOUT);
        if (timeoutSeconds != -1)
            con.setTimeout(timeoutSeconds * 1000);

        long max = Header.parseKeepAliveHeader(value, MAX);
        if (max != -1 && max < con.getMaxExchanges())
            con.setMaxExchanges((int) max);
    }

    /**
     * Returns the target destination to use for this attempt.
     *
     * @param counter starting at 0 meaning the first.
     */
    public static String getDestination(Exchange exc, int counter) {
        return exc.getDestinations().get(counter % exc.getDestinations().size());
    }

    private boolean isUpgradeToResponse(Response res, String protocol) {
        return res.getStatusCode() == 101 &&
               "upgrade".equalsIgnoreCase(res.getHeader().getFirstValue(CONNECTION)) &&
               protocol.equalsIgnoreCase(res.getHeader().getFirstValue(UPGRADE));
    }

    private void handleConnectRequest(Exchange exc, Connection con) throws IOException, EndOfStreamException {
        if (configuration.getProxy() != null) {
            exc.getRequest().write(con.out, configuration.getMaxRetries() > 1);
            Response response = fromStream(con.in, false);
            log.debug("Status code response on CONNECT request: {}", response.getStatusCode());
        }
        exc.getRequest().setUri(NOT_APPLICABLE);
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
        if (configuration.getProxy() != null || req.isCONNECTRequest()) {
            req.setUri(dest);
            return;
        }

        if (!dest.startsWith("http")) {
            throw new MalformedURLException("""
                    The exchange's destination URI %s does not start with 'http'. Specify a <target> within the API configuration or make sure the exchanges destinations list contains a valid URI.
                    """.formatted(dest));
        }
        req.setUri(HttpUtil.getPathAndQueryString(dest));
    }

    /**
     *
     * @param connect If true, do not use TLS even when the URL starts with https
     * @param dest URL
     * @return HostColonPort
     * @throws MalformedURLException
     */
    private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException {
        if (connect)
            return new HostColonPort(false, dest);
        return HostColonPort.parse(dest);
    }

    private static boolean isHTTP2(Exchange exc) {
        return !(exc.getProperty(HTTP2) == null || (exc.getProperty(HTTP2) instanceof Boolean h2 && !h2));
    }

    // TODO Rewrite all clients to use try with resources and then remove it
    @Override
    protected void finalize() {
        close();
    }
}