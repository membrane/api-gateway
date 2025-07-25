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
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.transport.http.ConnectionFactory.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.Logger;
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
import static java.lang.System.*;

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

    public HttpClient() {
        this(null, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration configuration) {
        this(configuration, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration clientConfiguration, @Nullable TimerManager timerManager) {
        configuration = clientConfiguration != null ? clientConfiguration : new HttpClientConfiguration();
        connectionFactory = new ConnectionFactory(this.configuration, timerManager);
    }

    public Exchange call(Exchange exc) throws Exception {
        return call(exc, true, true);
    }

    public Exchange call(Exchange exc, boolean adjustHostHeader, boolean failOverOn5XX) throws Exception {
        checkUpgradeToHttp2(exc);
        Exchange exchange = configuration.getRetryHandler().executeWithRetries(exc,
                failOverOn5XX, (e, target, attempt)
                -> dispatchHttp1or2(e, attempt, initializeRequest(exc, target, adjustHostHeader)));

        if (exc.getRequest().isCONNECTRequest())
            return exchange;

        if (isHTTP2(exc)) {
            Connection tc = exchange.getTargetConnection();
            if (tc != null) {
                applyKeepAliveHeader(exc.getResponse(), tc);
                exc.getResponse().addObserver(tc);
                tc.setExchange(exc);
            }
        }
        return exchange;
    }

    private boolean dispatchHttp1or2(Exchange exc, int counter, HostColonPort target) throws IOException, InterruptedException, EndOfStreamException {
        OutgoingConnectionType outConType = connectionFactory.getConnection(exc, target, counter);

        if (configuration.getProxy() != null && outConType.sslProvider() == null)
            // if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
            exc.getRequest().getHeader().setProxyAuthorization(configuration.getProxy().getCredentials());

        if (outConType.usingHttp2()) {
            executeHttp2Call(exc, outConType, target);
            return false;
        }
        return checkUpgradeProtocolAndExecuteHttp1Call(exc, outConType, counter);
    }

    /**
     *
     * @param exc
     * @param oct
     * @param counter
     * @return
     * @throws EndOfStreamException
     * @throws IOException
     */
    boolean checkUpgradeProtocolAndExecuteHttp1Call(Exchange exc, OutgoingConnectionType oct, int counter) throws EndOfStreamException, IOException {
        Response response;
        String newProtocol;

        if (exc.getRequest().isCONNECTRequest()) {
            handleConnectRequest(exc, oct.con());
            response = ok().build();
            newProtocol = METHOD_CONNECT;
            //TODO should we report to the httpClientStatusEventBus here somehow?
        } else {
            response = doCall(exc, oct.con());
            if (trackNodeStatus(exc))
                exc.setNodeStatusCode(counter, response.getStatusCode());
            newProtocol = checkUpgradeProtocol(exc, response);
        }

        if (newProtocol != null) {
            setupConnectionForwarding(exc, oct.con(), newProtocol, streamPumpStats);
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



    private static boolean isWebsocketProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        return upgradeProtocol.equalsIgnoreCase("websocket") && exc.getProperty(ALLOW_WEBSOCKET) == TRUE;
    }

    public void setStreamPumpStats(StreamPump.StreamPumpStats streamPumpStats) {
        this.streamPumpStats = streamPumpStats;
    }

    private static boolean isTcpProtocolUpgradeAllowed(Exchange exc, String upgradeProtocol) {
        return upgradeProtocol.equalsIgnoreCase("tcp") && exc.getProperty(ALLOW_TCP) == TRUE;
    }

    private static void handleH2CUpgradeRequest(Exchange exc) {
        if (exc.getProperty(ALLOW_H2) == TRUE) {
            // note that this has been deprecated by RFC9113 superseeding RFC7540, and therefore should not happen.
            return;
        }
        // RFC750 section 3.2 specifies that servers not supporting this can respond "as though the Upgrade header
        // field were absent". Therefore, we remove it.
        if (infoOnHttp2Downgrade) {
            infoOnHttp2Downgrade = false;
            log.info("Your client sent a 'Connection: Upgrade' with 'Upgrade: h2c'. Please note that RFC7540 has " +
                     "been superseeded by RFC9113, which removes this option. The header was and will be removed.");
        }
        exc.getRequest().getHeader().removeFields(UPGRADE);
        exc.getRequest().getHeader().removeFields(HTTP2_SETTINGS);
        exc.getRequest().getHeader().keepOnly(CONNECTION, value -> !value.equalsIgnoreCase(UPGRADE) && !value.equalsIgnoreCase(HTTP2_SETTINGS));
    }

    private boolean executeHttp2Call(Exchange exc, OutgoingConnectionType outConType, HostColonPort target) throws IOException, InterruptedException {
        Http2Client h2c = outConType.h2c();
        if (h2c == null) {
            h2c = new Http2Client(outConType.con(), outConType.sslProvider().showSSLExceptions());
            connectionFactory.getHttp2ClientPool().share(target.host(),
                    target.port(),
                    outConType.sslProvider(),
                    outConType.sniServerName(),
                    configuration.getProxy(),
                    connectionFactory.getProxySSLContext(),
                    h2c);
        }
        exc.setResponse(h2c.doCall(exc));
        exc.setProperty(HTTP2, true);
        // TODO: handle CONNECT / AllowWebSocket / etc
        // TODO: connection should only be closed by the Http2Client
        return true;
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

    private static void checkUpgradeToHttp2(Exchange exc) throws ProtocolUpgradeDeniedException {
        String protocol = exc.getRequest().getHeader().getUpgradeProtocol();
        if (protocol == null ||
            isWebsocketProtocolUpgradeAllowed(exc, protocol) ||
            isTcpProtocolUpgradeAllowed(exc, protocol)
        )
            return;
        if (protocol.equalsIgnoreCase("h2c")) {
            handleH2CUpgradeRequest(exc);
            return;
        }

        throw new ProtocolUpgradeDeniedException(protocol);
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

    private Response doCall(Exchange exc, Connection con) throws IOException, EndOfStreamException {
        con.socket.setSoTimeout(configuration.getConnection().getSoTimeout());
        exc.getRequest().write(con.out, configuration.getMaxRetries() > 1);
        exc.setTimeReqSent(currentTimeMillis());

        if (exc.getRequest().isHTTP10()) {
            shutDownRequestInputOutput(exc, con);
        }

        var response = fromStream(con.in, !exc.getRequest().isHEADRequest());

        if (response.getStatusCode() == 100) {
            do100ExpectedHandling(exc, response, con);
        }

        exc.setReceived();
        exc.setTimeResReceived(currentTimeMillis());
        return response;
    }

    /**
     * For proxy
     * @param exc
     * @param con
     * @param protocol
     * @param streamPumpStats
     * @throws SocketException
     */
    public static void setupConnectionForwarding(Exchange exc, final Connection con, final String protocol, StreamPump.StreamPumpStats streamPumpStats) throws SocketException {
        final TwoWayStreaming tws = (TwoWayStreaming) exc.getHandler();
        String source = tws.getRemoteDescription();
        String dest = con.toString();
        final StreamPump a;
        final StreamPump b;
        if ("WebSocket".equals(protocol)) {
            WebSocketStreamPump aTemp = new WebSocketStreamPump(tws.getSrcIn(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, exc.getProxy(), true, exc);
            WebSocketStreamPump bTemp = new WebSocketStreamPump(con.in, tws.getSrcOut(), streamPumpStats, protocol + " " + source + " <- " + dest, exc.getProxy(), false, null);
            aTemp.init(bTemp);
            bTemp.init(aTemp);
            a = aTemp;
            b = bTemp;
        } else {
            a = new StreamPump(tws.getSrcIn(), con.out, streamPumpStats, protocol + " " + source + " -> " + dest, exc.getProxy());
            b = new StreamPump(con.in, tws.getSrcOut(), streamPumpStats, protocol + " " + source + " <- " + dest, exc.getProxy());
        }

        tws.removeSocketSoTimeout();

        exc.addExchangeViewerListener(new AbstractExchangeViewerListener() {

            @Override
            public void setExchangeFinished() {
                runClient(log, b, protocol, a, con);
            }
        });
    }

    public static void runClient(Logger log, StreamPump b, String protocol, StreamPump pump, Connection con) {
        String threadName = Thread.currentThread().getName();

        new Thread(b,  "%s %s Backward Thread".formatted( threadName, protocol)).start();
        try {
            Thread.currentThread().setName("%s %s Onward Thread".formatted( threadName, protocol));
            pump.run();
        } finally {
            try {
                con.close();
            } catch (IOException e) {
                log.debug("", e);
            }
        }
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

    private void do100ExpectedHandling(Exchange exc, Response response, Connection con) throws IOException, EndOfStreamException {
        exc.getRequest().getBody().write(exc.getRequest().getHeader().isChunked() ? new ChunkedBodyTransferrer(con.out) : new PlainBodyTransferrer(con.out), configuration.getMaxRetries() > 1);
        con.out.flush();
        response.read(con.in, !exc.getRequest().isHEADRequest());
    }

    private void shutDownRequestInputOutput(Exchange exc, Connection con) throws IOException {
        exc.getHandler().shutdownInput();
        Util.shutdownOutput(con.socket);
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
        return exc.getProperty(HTTP2) == null || (exc.getProperty(HTTP2) instanceof Boolean h2 && !h2);
    }

    // TODO Rewrite all clients to use try with resources and then remove it
    @Override
    protected void finalize() {
        close();
    }
}