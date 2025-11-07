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

import com.predic8.membrane.core.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.transport.http2.*;
import com.predic8.membrane.core.transport.ssl.*;
import com.predic8.membrane.core.util.*;
import org.jetbrains.annotations.*;
import org.slf4j.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.CONNECTION;
import static com.predic8.membrane.core.http.Header.PROXY_CONNECTION;
import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionInputStream;
import static com.predic8.membrane.core.transport.http.ByteStreamLogging.wrapConnectionOutputStream;
import static com.predic8.membrane.core.transport.http.HttpServerHandler.RequestProcessingResult.*;
import static com.predic8.membrane.core.transport.http.HttpServerThreadFactory.DEFAULT_THREAD_NAME;
import static com.predic8.membrane.core.util.StringUtil.maskNonPrintableCharacters;
import static com.predic8.membrane.core.util.StringUtil.truncateAfter;
import static java.lang.Thread.currentThread;

public class HttpServerHandler extends AbstractHttpHandler implements Runnable, TwoWayStreaming {

    private static final Logger log = LoggerFactory.getLogger(HttpServerHandler.class);

    private static final int BUFFER_SIZE = 2048;

    private static final AtomicInteger counter = new AtomicInteger();

    private final HttpEndpointListener endpointListener;
    private final Socket rawSourceSocket;
    private Socket sourceSocket;
    private InputStream srcIn;
    private OutputStream srcOut;

    private boolean showSSLExceptions = false;
    private Http2ServerHandler http2ServerHandler;


    public HttpServerHandler(Socket socket, HttpEndpointListener endpointListener) {
        super(endpointListener.getTransport());
        this.endpointListener = endpointListener;
        this.sourceSocket = socket;
        this.rawSourceSocket = socket;
    }

    @Override
    public HttpTransport getTransport() {
        return (HttpTransport) super.getTransport();
    }

    private void setup() throws IOException, EndOfStreamException {
        this.exchange = new Exchange(this);
        SSLProvider sslProvider = endpointListener.getSslProvider();
        if (sslProvider != null) {
            showSSLExceptions = sslProvider.showSSLExceptions();
            sourceSocket = sslProvider.wrapAcceptedSocket(sourceSocket);
        }
        log.debug("New ServerThread created. {}", counter.incrementAndGet());
        setupInAndOut();
        sourceSocket.setSoTimeout(endpointListener.getTransport().getSocketTimeout());
        sourceSocket.setTcpNoDelay(endpointListener.getTransport().isTcpNoDelay());
    }

    private void setupInAndOut() throws IOException {
        if (ByteStreamLogging.isLoggingEnabled()) {
            String c = "s-" + new Random().nextInt();
            srcIn = new BufferedInputStream(wrapConnectionInputStream(sourceSocket.getInputStream(), c + " in"), BUFFER_SIZE);
            srcOut = new BufferedOutputStream(wrapConnectionOutputStream(sourceSocket.getOutputStream(), c + " out"), BUFFER_SIZE);
            return;
        }
        srcIn = new BufferedInputStream(sourceSocket.getInputStream(), BUFFER_SIZE);
        srcOut = new BufferedOutputStream(sourceSocket.getOutputStream(), BUFFER_SIZE);
    }

    public void run() {
        Connection boundConnection = null; // see Request.isBindTargetConnectionToIncoming()
        try {
            updateThreadName(true);
            setup();
            while (true) {
                RequestProcessingResult result = processSingleRequest(boundConnection);

                if (result.shouldTerminate()) {
                    break;
                }

                boundConnection = result.boundConnection;
            }
        } catch (SocketTimeoutException e) {
            log.debug("Socket of thread {} timed out", counter);
        } catch (SocketException se) {
            log.debug("client socket closed");
        } catch (TLSUnrecognizedNameException e) {
            if (showSSLExceptions)
                log.info("{}", e.getMessage());
        } catch (SSLHandshakeException e) {
            log.info("SSLHandshakeException: {}", e.getMessage());
            if (showSSLExceptions)
                log.info("", e);
        } catch (SSLException s) {
            if (showSSLExceptions) {
                if (s.getCause() instanceof SocketException) {
                    log.debug("SSL socket closed");
                } else {
                    log.error("", s);
                }
            }
        } catch (EndOfStreamException e) {
            log.debug("stream closed");
        } catch (NoMoreRequestsException e) {
            // happens at the end of a keep-alive connection
        } catch (NoResponseException e) {
            log.debug("No response received. Maybe increase the keep-alive timeout on the server.");
        } catch (EOFWhileReadingFirstLineException e) {
            log.debug("Client connection terminated before first line was read. Line so far: {}", getLineMaskedAndTruncated(e));
        } catch (EOFWhileReadingLineException e) {
            log.debug("Client connection terminated while reading header line: {}", getLineMaskedAndTruncated(e));
        } catch (Exception e) {
            log.error("", e);
        } finally {
            endpointListener.setOpenStatus(rawSourceSocket);

            if (boundConnection != null)
                try {
                    boundConnection.close();
                } catch (IOException e) {
                    log.debug("Closing bound connection.", e);
                }

            closeConnections();

            exchange.detach();

            updateThreadName(false);
        }
    }

    private static @NotNull String getLineMaskedAndTruncated(EOFWhileReadingLineException e) {
        return maskNonPrintableCharacters(truncateAfter(e.getLineSoFar(), 80));
    }

    record RequestProcessingResult(boolean shouldTerminate, Connection boundConnection) {
        static RequestProcessingResult terminate() {
            return new RequestProcessingResult(true, null);
        }

        static RequestProcessingResult terminateWithConnection(Connection conn) {
            return new RequestProcessingResult(true, conn);
        }

        static RequestProcessingResult continueWithConnection(Connection conn) {
            return new RequestProcessingResult(false, conn);
        }
    }

    private RequestProcessingResult processSingleRequest(Connection con) throws IOException, EndOfStreamException, TerminateException {

        // Needed to mark connection as IDLE for reloads
        if (isSrcInEndOfFileAndSetIdleStatus()) {
            return terminate();
        }

        if (Http2TlsSupport.isHttp2(sourceSocket)) {
            http2ServerHandler = new Http2ServerHandler(this, sourceSocket, srcIn, srcOut, showSSLExceptions);
            http2ServerHandler.handle();
            http2ServerHandler = null;
            return terminateWithConnection(con);
        }

        return processHttp1Request(con);
    }

    private boolean isSrcInEndOfFileAndSetIdleStatus() throws IOException {
		endpointListener.setIdleStatus(sourceSocket, true);
        try {
			srcIn.mark(2);
            if (srcIn.read() != -1) {
				srcIn.reset();
                return false;
            }
            return true;
        } finally {
			endpointListener.setIdleStatus(sourceSocket, false);
        }
    }

    private @NotNull RequestProcessingResult processHttp1Request(Connection con) throws EndOfStreamException, IOException, TerminateException {

        // Prepare
        srcReq = new Request();
        if (con != null) {
            exchange.setTargetConnection(con);
            con = null;
        }

        readAndParseRequest();
        process();
        return determineConnectionContinuation(con);
    }

    private void readAndParseRequest() throws IOException, EndOfStreamException {
        srcReq.read(srcIn, true);
        exchange.received();

        log.debug("Requested URI: {}", srcReq.getUri());

        if (srcReq.getHeader().getProxyConnection() != null) {
            srcReq.getHeader().add(CONNECTION,
                    srcReq.getHeader().getProxyConnection());
            srcReq.getHeader().removeFields(PROXY_CONNECTION);
        }
    }

    private @NotNull RequestProcessingResult determineConnectionContinuation(Connection con) {
        if (srcReq.isCONNECTRequest()) {
            log.debug("stopping HTTP Server Thread after establishing an HTTP connect");
            return RequestProcessingResult.terminateWithConnection(con);
        }
        con = exchange.getTargetConnection();
        exchange.setTargetConnection(null);
        if (!exchange.canKeepConnectionAlive())
            return terminateWithConnection(con);
        if (exchange.getResponse().isRedirect()) {
            return terminateWithConnection(con);
        }
        exchange.detach();
        exchange = new Exchange(this);

        return continueWithConnection(con);
    }

    private void closeConnections() {
        try {
            if (!sourceSocket.isClosed()) {
                if (!(sourceSocket instanceof SSLSocket))
                    sourceSocket.shutdownOutput();
                sourceSocket.close();
            }
        } catch (Exception e2) {
            if (e2.getMessage().contains("Socket closed"))
                return;
            log.error("problems closing socket on remote port: {} on remote host: {}", sourceSocket.getPort(), sourceSocket.getInetAddress(), e2);
        }
    }

    private void process() throws TerminateException, IOException {
        try {

            DNSCache dnsCache = getTransport().getRouter().getDnsCache();
            InetAddress remoteAddr = sourceSocket.getInetAddress();
            String ip = dnsCache.getHostAddress(remoteAddr);
            exchange.setRemoteAddrIp(ip);
            exchange.setRemoteAddr(getRemoteAddr(dnsCache, remoteAddr, ip));

            exchange.setRequest(srcReq);
            exchange.setOriginalRequestUri(srcReq.getUri());

            if (exchange.getRequest().getHeader().is100ContinueExpected()) {
                final Request request = exchange.getRequest();
                request.addObserver(new Expect100ContinueObserver(request));
            }
            invokeHandlers();
        } catch (AbortException e) {
            log.debug("Aborted");
            exchange.finishExchange(true, e.getMessage());

            removeBodyFromBuffer();
            writeResponse(exchange.getResponse());

            log.debug("exchange set aborted");
            return;
        }

        try {
            removeBodyFromBuffer();
            writeResponse(exchange.getResponse());
            exchange.setCompleted();
            log.debug("exchange set completed");
        } catch (Exception e) {
            exchange.finishExchange(true, e.getMessage());
            throw e;
        }
    }

    private String getRemoteAddr(DNSCache dnsCache, InetAddress remoteAddr, String ip) {
        return getTransport().isReverseDNS() ? dnsCache.getHostName(remoteAddr) : ip;
    }

    /**
     * Read the body from the client, if not already read.
     * <p>
     * If the body has not already been read, the header includes
     * "Expect: 100-continue" and the body has not already been sent by the
     * client, nothing will be done. (Allowing the HTTP connection state to skip
     * over the body transmission.)
     */
    private void removeBodyFromBuffer() throws IOException {
        if (!exchange.getRequest().getHeader().is100ContinueExpected() || srcIn.available() > 0) {
            exchange.getRequest().discardBody();
        }
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            StringBuilder sb = new StringBuilder();
            sb.append(DEFAULT_THREAD_NAME);
            sb.append(" ");
            InetAddress ia = sourceSocket.getInetAddress();
            if (ia != null)
                sb.append(ia);
            sb.append(":");
            sb.append(sourceSocket.getPort());
            currentThread().setName(sb.toString());
            return;
        }
        currentThread().setName(DEFAULT_THREAD_NAME);
    }

    protected void writeResponse(Response res) throws IOException {
        if (res.isRedirect())
            res.getHeader().setConnection(Header.CLOSE);
        res.write(srcOut, false);
        srcOut.flush();
        exchange.setTimeResSent(System.currentTimeMillis());
        exchange.collectStatistics();
    }

    @Override
    public void shutdownInput() throws IOException {
        Util.shutdownInput(sourceSocket);
    }

    @Override
    public InetAddress getLocalAddress() {
        return sourceSocket.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return sourceSocket.getLocalPort();
    }

    public InputStream getSrcIn() {
        return srcIn;
    }

    public OutputStream getSrcOut() {
        return srcOut;
    }

    @Override
    public String getRemoteDescription() {
        return sourceSocket.getRemoteSocketAddress().toString();
    }

    @Override
    public void removeSocketSoTimeout() throws SocketException {
        sourceSocket.setSoTimeout(0);
    }

    @Override
    public boolean isClosed() {
        return sourceSocket.isClosed();
    }

    @Override
    public void close() throws IOException {
        sourceSocket.close();
    }

    public Socket getSourceSocket() {
        return sourceSocket;
    }

    public Http2ServerHandler getHttp2ServerHandler() {
        return http2ServerHandler;
    }

    private class Expect100ContinueObserver extends AbstractMessageObserver implements NonRelevantBodyObserver {
        private final Request request;

        public Expect100ContinueObserver(Request request) {
            this.request = request;
        }

        @Override
        public void bodyRequested(AbstractBody body) {
            try {
                if (request.getHeader().is100ContinueExpected()) {
                    log.warn("requesting body");
                    // request body from client so that interceptors can handle it
                    Response.continue100().build().write(srcOut, false);
                    // remove "Expect: 100-continue" since we already sent "100 Continue"
                    request.getHeader().removeFields(Header.EXPECT);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}