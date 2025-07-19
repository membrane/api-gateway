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

import com.predic8.membrane.core.Constants;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.model.*;
import com.predic8.membrane.core.transport.http.client.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;

import javax.annotation.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static java.lang.Boolean.*;
import static java.lang.Thread.*;
import static java.nio.charset.StandardCharsets.*;

/**
 * HttpClient with possibly multiple selectable destinations, with internal logic to auto-retry and to
 * switch destinations on failures.
 * Instances are thread-safe.
 */
public class HttpClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class.getName());

    public static final String HTTP2 = "h2";

    /**
     * How long to wait between calls to the same destination, in milliseconds.
     * To prevent hammering one target.
     * Between calls to different targets (think servers) this waiting time is not applied.
     * Note: for reasons of code simplicity, this sleeping time is only applied between direct successive calls
     * to the same target. If there are multiple targets like one, two, one and it all goes very fast, then
     * it's possible that the same server gets hit with less time in between.
     */
    private final int timeBetweenTriesMs = 250;

    private HttpClientConfiguration configuration;

    private StreamPump.StreamPumpStats streamPumpStats;

    private static volatile boolean infoOnHttp2Downgrade = true;

    private final ConnectionFactory connectionFactory;

    public HttpClient() {
        this(null, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration configuration) {
        this(configuration, null);
    }

    public HttpClient(@Nullable HttpClientConfiguration configuration, @Nullable TimerManager timerManager) {
        if (configuration == null)
            configuration = new HttpClientConfiguration();
        this.configuration = configuration;
        this.connectionFactory = new ConnectionFactory(configuration, timerManager);
    }

    public void setStreamPumpStats(StreamPump.StreamPumpStats streamPumpStats) {
        this.streamPumpStats = streamPumpStats;
    }

    // TODO remove it and close it otherwise
    @Override
    protected void finalize() {
        close();
    }

    private void setRequestURI(Request req, String dest) throws MalformedURLException {
        if (configuration.getProxy() != null || req.isCONNECTRequest()) {
            req.setUri(dest);
            return;
        }

        if (!dest.startsWith("http")) {
            throw new MalformedURLException("""
                    The exchange's destination URI %s does not start with 'http'. Specify a <target> within the API configuration or make sure the exchanges destinations list contains a valid URI.
                    """.formatted(dest));
        }
        String originalUri = req.getUri();
        req.setUri(HttpUtil.getPathAndQueryString(dest));

        // Make sure if the request had no path and the destination has also no path
        // to continue with no path. Maybe for STOMP?
        if ("/".equals(originalUri) && req.getUri().isEmpty()) // QUESTION: Will that be ever true?
            req.setUri("/");
    }

    private HostColonPort getTargetHostAndPort(boolean connect, String dest) throws MalformedURLException {
        if (connect)
            return new HostColonPort(false, dest);
        return new HostColonPort(new URL(dest));
    }

    private HostColonPort init(Exchange exc, String dest, boolean adjustHostHeader) throws IOException {
        setRequestURI(exc.getRequest(), dest);
        HostColonPort target = getTargetHostAndPort(exc.getRequest().isCONNECTRequest(), dest);

        if (configuration.getAuthentication() != null)
            exc.getRequest().getHeader().setAuthorization(configuration.getAuthentication().getUsername(), configuration.getAuthentication().getPassword());

        if (adjustHostHeader && (exc.getProxy() == null || exc.getProxy().isTargetAdjustHostHeader())) {
            exc.getRequest().getHeader().setHost(new HostColonPort(new URL(dest)).toString());
        }
        return target;
    }

    public Exchange call(Exchange exc) throws Exception {
        return call(exc, true, true);
    }

    public Exchange call(Exchange exc, boolean adjustHostHeader, boolean failOverOn5XX) throws Exception {
        /*
         TODO: Questions

         - Should we retry POST? Maybe only retry POST when:
           - Network error before the request reaches the server:
             - Connection timeouts during establishment
             - DNS resolution failures
             - Network unreachable errors
             - Connection refused (server not running)
           - Server explicitly indicates retry is safe:
             - 5xx server errors (500, 502, 503, 504) - but be cautious
             - 408 Request Timeout
             - 429 Too Many Requests (with Retry-After header) => ?
         */

        if (exc.getDestinations().isEmpty())
            throw new IllegalStateException("List of destinations is empty. Please specify at least one destination.");

        denyUnsupportedUpgrades(exc);

        int counter = 0;
        Exception exception = null;
        while (counter < configuration.getMaxRetries()) {
            HostColonPort target = init(exc, getDestination(exc, counter), adjustHostHeader);
            log.debug("try # {} to {}", counter, target);
            try {
                if (call(exc, failOverOn5XX, counter, target)) return exc;

                // java.net.SocketException: Software caused connection abort: socket write error
            } catch (MalformedURLException e) {
                throw e; // Rethrow so the caller can handle it
            } catch (ConnectException e) {
                exception = e;
                log.info("Connection to {} refused.", target);
            } catch (SocketException e) {
                exception = e;
                if (e.getMessage().contains("Software caused connection abort")) {
                    log.info("Connection to {} was aborted externally. Maybe by the server or the OS Membrane is running on.", getDestination(exc, counter));
                } else if (e.getMessage().contains("Connection reset")) {
                    log.info("Connection to {} was reset externally. Maybe by the server or the OS Membrane is running on.", getDestination(exc, counter));
                } else {
                    logException(exc, counter, e);
                }
                throwWhenPost(exc, e);
            } catch (SocketTimeoutException e) {
                log.info("Connection to {} timed out.", target);
                throw e;
            } catch (UnknownHostException e) {
                exception = e;
                log.warn("Unknown host: {}", target);
            } catch (EOFWhileReadingFirstLineException e) {
                exception = e;
                log.debug("Server connection to {} terminated before line was read. Line so far: {}", getDestination(exc, counter), e.getLineSoFar());
            } catch (NoResponseException e) {
                exception = e;
                throwWhenPost(exc, e);
            } catch (Exception e) {
                exception = e;
                logException(exc, counter, e);
            } finally {
                if (trackNodeStatus(exc)) {
                    if (exception != null) {
                        exc.setNodeException(counter, exception);
                    }
                }
            }

            HttpClientStatusEventBus.reportException(exc, exception, getDestination(exc, counter));

            if (exception instanceof UnknownHostException) {
                if (exc.getDestinations().size() < 2) {
                    //don't retry this host, it's useless. (it's very unlikely that it will work after timeBetweenTriesMs)
                    break;
                }
            } else if (exception instanceof NoResponseException) {
                //TODO explain why we give up here, don't even retry another host.
                //maybe it means we ourselves lost network connection?
                throw exception;
            }

            counter++;
            waitBetweenCalls(exc);
        }
        throw exception;
    }

    private static void throwWhenPost(Exchange exc, Exception e) throws Exception {
        if (exc.getRequest().isPOSTRequest()) {
            throw e;
        }
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
            doHttp2Call(exc, outConType.con(), target, outConType.h2c(), outConType.sslProvider(), outConType.sniServerName());
        } else {
            if (doHttp1Call(exc, outConType, counter, getDestination(exc, counter))) {
                return true;
            }
        }

        HttpClientStatusEventBus.reportSuccess(exc, getDestination(exc, counter));

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
    }

    private void waitBetweenCalls(Exchange exc) throws InterruptedException {
        //as documented above, the sleep timeout is only applied between successive calls to the SAME destination.
        if (exc.getDestinations().size() != 1)
            return;

        sleep(timeBetweenTriesMs);
    }

    boolean doHttp1Call(Exchange exc, ConnectionFactory.OutgoingConnectionType oct, int counter, String dest) throws EndOfStreamException, IOException {

        Response response;

        String newProtocol;

        if (exc.getRequest().isCONNECTRequest()) {
            handleConnectRequest(exc, oct.con());
            response = Response.ok().build();
            newProtocol = "CONNECT";
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

    private void denyUnsupportedUpgrades(Exchange exc) throws ProtocolUpgradeDeniedException {
        String upgradeProtocol = exc.getRequest().getHeader().getUpgradeProtocol();
        if (upgradeProtocol == null)
            return;
        if (upgradeProtocol.equalsIgnoreCase("websocket") && exc.getProperty(ALLOW_WEBSOCKET) == TRUE)
            return;
        if (upgradeProtocol.equalsIgnoreCase("tcp") && exc.getProperty(ALLOW_TCP) == TRUE)
            return;
        if (upgradeProtocol.equalsIgnoreCase("h2c")) {
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
            return;
        }

        throw new ProtocolUpgradeDeniedException(upgradeProtocol);
    }

    private void doHttp2Call(Exchange exc, Connection con, HostColonPort target, Http2Client h2c, SSLProvider sslProvider, String sniServerName) throws IOException, InterruptedException {
        if (h2c == null) {
            h2c = new Http2Client(con, sslProvider.showSSLExceptions());
            connectionFactory.getHttp2ClientPool().share(target.host(), target.port(), sslProvider, sniServerName, configuration.getProxy(), connectionFactory.getProxySSLContext(), h2c);
        }
        exc.setResponse(h2c.doCall(exc, con));
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
        return exc.getDestinations().get(counter % exc.getDestinations().size());
    }

    private void logException(Exchange exc, int counter, Exception e) throws IOException {
        if (!log.isDebugEnabled())
            return;

        StringBuilder msg = new StringBuilder();
        msg.append("try # ");
        msg.append(counter);
        msg.append(" failed\n");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exc.getRequest().writeStartLine(baos);
        exc.getRequest().getHeader().write(baos);
        msg.append(ISO_8859_1.decode(ByteBuffer.wrap(baos.toByteArray())));

        if (e != null)
            log.debug("{}", msg, e);
        else
            log.debug("{}", msg);
    }

    private Response doCall(Exchange exc, Connection con) throws IOException, EndOfStreamException {
        con.socket.setSoTimeout(configuration.getConnection().getSoTimeout());
        exc.getRequest().write(con.out, configuration.getMaxRetries() > 1);
        exc.setTimeReqSent(System.currentTimeMillis());

        if (exc.getRequest().isHTTP10()) {
            shutDownRequestInputOutput(exc, con);
        }

        Response res = new Response();
        res.read(con.in, !exc.getRequest().isHEADRequest());

        if (res.getStatusCode() == 100) {
            do100ExpectedHandling(exc, res, con);
        }

        exc.setReceived();
        exc.setTimeResReceived(System.currentTimeMillis());
        return res;
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
            Response response = Response.readNew(con.in, false);
            log.debug("Status code response on CONNECT request: {}", response.getStatusCode());
        }
        exc.getRequest().setUri(Constants.N_A);
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
}