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
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.Logger;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.transport.http.HostColonPort.parse;
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
    public static final String CONNECT = "CONNECT";

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
//        configuration.getRetryHandler().setConfig(clientConfiguration);
        connectionFactory = new ConnectionFactory(this.configuration, timerManager);
    }

    public Exchange call(Exchange exc) throws Exception {
        return call(exc, true, true);
    }

    public Exchange call(Exchange exc, boolean adjustHostHeader, boolean failOverOn5XX) throws Exception {
        denyUnsupportedUpgrades(exc);
        return configuration.getRetryHandler().executeWithRetries(exc, failOverOn5XX, (e, target, attempt) -> {
            HostColonPort hcp = initializeRequest(exc, target, adjustHostHeader);
            return call(e, failOverOn5XX, attempt, hcp);
        });

    }

    /**
     * @param exc
     * @param failOverOn5XX
     * @param counter
     * @param target
     * @return if true the exchange should be returned
     * @throws IOException
     * @throws InterruptedException
     * @throws EndOfStreamException
     */
    private boolean call(Exchange exc, boolean failOverOn5XX, int counter, HostColonPort target) throws IOException, InterruptedException, EndOfStreamException {
        ConnectionFactory.OutgoingConnectionType outConType = connectionFactory.getConnection(exc, target, counter);

        if (configuration.getProxy() != null && outConType.sslProvider() == null)
            // if we use a proxy for a plain HTTP (=non-HTTPS) request, attach the proxy credentials.
            exc.getRequest().getHeader().setProxyAuthorization(configuration.getProxy().getCredentials());

        if (outConType.usingHttp2()) {
            executeHttp2Call(exc, outConType, target);
        } else {
            if (executeHttp1Call(exc, outConType, counter, getDestination(exc, counter))) {
                return true;
            }
        }

        // TODO Closer look!
        if (!failOverOn5XX || !is5xx(exc.getResponse().getStatusCode()) || counter == configuration.getMaxRetries() - 1) {
            applyKeepAliveHeader(exc.getResponse(), outConType.con());
            exc.setDestinations(List.of(getDestination(exc, counter)));
            outConType.con().setExchange(exc);
            if (!outConType.usingHttp2())
                exc.getResponse().addObserver(outConType.con());
            //exc.setResponse(response);
            //TODO should we report to the httpClientStatusEventBus here somehow?
            return true;
        }
        return false;

//        return true;


    }

    boolean executeHttp1Call(Exchange exc, ConnectionFactory.OutgoingConnectionType oct, int counter, String dest) throws EndOfStreamException, IOException {

        Response response;
        String newProtocol;

        if (exc.getRequest().isCONNECTRequest()) {
            handleConnectRequest(exc, oct.con());
            response = ok().build();
            newProtocol = CONNECT;
            //TODO should we report to the httpClientStatusEventBus here somehow?
        } else {
            response = doCall(exc, oct.con());
            if (trackNodeStatus(exc))
                exc.setNodeStatusCode(counter, response.getStatusCode());
            newProtocol = upgradeProtocol(exc, response);
        }

        if (newProtocol != null) {
            setupConnectionForwarding(exc, oct.con(), newProtocol, streamPumpStats);
            exc.getDestinations().clear();
            exc.getDestinations().add(dest);
            oct.con().setExchange(exc);
            exc.setResponse(response);
            return true;
        }

        exc.setResponse(response);
        return false;
    }

    HostColonPort initializeRequest(Exchange exc, String dest, boolean adjustHostHeader) throws IOException, URISyntaxException {
        setRequestURI(exc.getRequest(), dest);
        HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);

        if (configuration.getAuthentication() != null)
            exc.getRequest().getHeader().setAuthorization(configuration.getAuthentication().getUsername(), configuration.getAuthentication().getPassword());

        if (adjustHostHeader && (exc.getProxy() == null || exc.getProxy().isTargetAdjustHostHeader())) {
            exc.getRequest().getHeader().setHost(parse(dest).toString());
        }
        return target;
    }

    private void denyUnsupportedUpgrades(Exchange exc) throws ProtocolUpgradeDeniedException {
        String upgradeProtocol = exc.getRequest().getHeader().getUpgradeProtocol();
        if (upgradeProtocol == null ||
            isWebsocketProtocolUpgradeAllowed(exc, upgradeProtocol) ||
            isTcpProtocolUpgradeAllowed(exc, upgradeProtocol)
        )
            return;
        if (upgradeProtocol.equalsIgnoreCase("h2c")) {
            handleH2CUpgradeRequest(exc);
            return;
        }

        throw new ProtocolUpgradeDeniedException(upgradeProtocol);
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

    private void executeHttp2Call(Exchange exc, ConnectionFactory.OutgoingConnectionType outConType, HostColonPort target) throws IOException, InterruptedException {
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
        exc.setResponse(h2c.doCall(exc, outConType.con()));
        exc.setProperty(HTTP2, true);
        // TODO: handle CONNECT / AllowWebSocket / etc
        // TODO: connection should only be closed by the Http2Client
    }

    private static boolean trackNodeStatus(Exchange exc) {
        if (exc.getProperty(TRACK_NODE_STATUS) instanceof Boolean status)
            return status;
        return false;
    }

    private String upgradeProtocol(Exchange exc, Response response) {
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

    // TODO Inline method

    private boolean is5xx(Integer responseStatusCode) {
        return 500 <= responseStatusCode && responseStatusCode < 600;
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

        /*
        Maybe place here:

          if (exc.getDestinations().isEmpty())
            throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");

         */
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

    public static void runClient(Logger log, StreamPump b, String protocol, StreamPump a, Connection con) {
        String threadName = Thread.currentThread().getName();
        new Thread(b, threadName + " " + protocol + " Backward Thread").start();
        try {
            Thread.currentThread().setName(threadName + " " + protocol + " Onward Thread");
            a.run();
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

    // TODO Rewrite all clients to use try with resources and then remove it
    @Override
    protected void finalize() {
        close();
    }
}