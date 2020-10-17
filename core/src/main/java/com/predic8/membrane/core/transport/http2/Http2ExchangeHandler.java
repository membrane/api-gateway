/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http2;

import com.google.common.collect.Lists;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.AbstractBodyTransferrer;
import com.predic8.membrane.core.http.Chunk;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.InterceptorFlowController;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.predic8.membrane.core.transport.http.AbstractHttpHandler.generateErrorResponse;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.HeadersFrame.FLAG_END_HEADERS;
import static com.predic8.membrane.core.transport.http2.frame.HeadersFrame.FLAG_END_STREAM;

public class Http2ExchangeHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Http2ExchangeHandler.class.getName());
    private static final InterceptorFlowController flowController = new InterceptorFlowController();

    private final StreamInfo streamInfo;
    private final Http2ServerHandler http2ServerHandler;
    private final Exchange exchange;
    private final boolean showSSLExceptions;
    private final String remoteAddr;
    private final int streamId;

    public Http2ExchangeHandler(StreamInfo streamInfo, Http2ServerHandler http2ServerHandler, Exchange exchange, boolean showSSLExceptions, String remoteAddr) {
        this.streamInfo = streamInfo;
        this.http2ServerHandler = http2ServerHandler;
        this.exchange = exchange;
        this.showSSLExceptions = showSSLExceptions;
        this.remoteAddr = remoteAddr;
        streamId = streamInfo.getStreamId();
    }

    @Override
    public void run() {
        // TODO: update endpointListener to indicate whether any streams are currently processed within this HTTP/2 connection
        try {
            updateThreadName(true);

            process();


            exchange.detach();
        } catch (SocketTimeoutException e) {
            log.debug("Socket timed out");
        } catch (SocketException se) {
            log.debug("client socket closed");
        } catch (SSLException s) {
            if(showSSLExceptions) {
                if (s.getCause() instanceof SSLException)
                    s = (SSLException) s.getCause();
                if (s.getCause() instanceof SocketException)
                    log.debug("ssl socket closed");
                else
                    log.error("", s);
            }
        } catch (IOException e) {
            log.error("", e);
        } catch (EndOfStreamException e) {
            log.debug("stream closed");
        } catch (AbortException e) {
            log.debug("exchange aborted.");
        } catch (NoMoreRequestsException e) {
            // happens at the end of a keep-alive connection
        } catch (NoResponseException e) {
            log.debug("No response received. Maybe increase the keep-alive timeout on the server.");
        } catch (EOFWhileReadingFirstLineException e) {
            log.debug("Client connection terminated before line was read. Line so far: ("
                    + e.getLineSoFar() + ")");
        } catch (Exception e) {
            log.error("", e);
        }

        finally {

            closeConnections();

            exchange.detach();

            updateThreadName(false);
        }

    }

    private void process() throws Exception {
        try {
            invokeHandlers();

            exchange.blockResponseIfNeeded();
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

    private void invokeHandlers() throws IOException, EndOfStreamException, AbortException {
        try {
            flowController.invokeHandlers(exchange, http2ServerHandler.getHttpServerHandler().getTransport().getInterceptors());
            if (exchange.getResponse() == null)
                throw new AbortException("No response was generated by the interceptor chain.");
        } catch (Exception e) {
            if (exchange.getResponse() == null)
                exchange.setResponse(generateErrorResponse(e, exchange, http2ServerHandler.getHttpServerHandler().getTransport()));

            if (e instanceof IOException)
                throw (IOException)e;
            if (e instanceof EndOfStreamException)
                throw (EndOfStreamException)e;
            if (e instanceof AbortException)
                throw (AbortException)e; // TODO: migrate catch logic into this method
            if (e instanceof NoMoreRequestsException)
                throw (NoMoreRequestsException)e;
            if (e instanceof NoResponseException)
                throw (NoResponseException)e;
            if (e instanceof EOFWhileReadingFirstLineException)
                throw (EOFWhileReadingFirstLineException)e;
            log.warn("An exception occured while handling a request: ", e);
        }
    }

    private void updateThreadName(boolean fromConnection) {
        if (fromConnection) {
            StringBuilder sb = new StringBuilder();
            sb.append(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
            sb.append(" ");
            sb.append(remoteAddr);
            sb.append(" stream ");
            sb.append(streamId);
            Thread.currentThread().setName(sb.toString());
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

    protected void writeResponse(Response res) throws Exception {
        http2ServerHandler.getSender().send(streamId, (encoder, peerSettings) -> createHeadersFrames(res, encoder, peerSettings, false));

        res.getBody().write(new AbstractBodyTransferrer() {
            @Override
            public void write(byte[] content, int i, int length) throws IOException {
                sendData(content, i, length);
            }

            private void sendData(byte[] content, int offset, int length) throws IOException {
                int mOffset = offset;
                while (mOffset < offset + length) {
                    int mLength = Math.min(http2ServerHandler.getPeerSettings().getMaxFrameSize(), length - (mOffset - offset));

                    // as we do not send padding, reserve exactly the length we want to send
                    streamInfo.getPeerFlowControl().reserve(mLength, streamId);
                    http2ServerHandler.getPeerFlowControl().reserve(mLength, streamId);

                    Frame frame = new Frame();
                    frame.fill(TYPE_DATA,
                            0,
                            streamId,
                            content,
                            mOffset,
                            mLength);

                    http2ServerHandler.getSender().send(frame);

                    mOffset += mLength;
                }
            }

            @Override
            public void write(Chunk chunk) throws IOException {
                sendData(chunk.getContent(), 0, chunk.getLength());
            }

            @Override
            public void finish() throws IOException {

            }
        }, false);

        Frame frame = new Frame();
        frame.fill(TYPE_DATA,
                FLAG_END_STREAM,
                streamId,
                null,
                0,
                0);

        http2ServerHandler.getSender().send(frame);

        exchange.setTimeResSent(System.currentTimeMillis());
        exchange.collectStatistics();
    }

    private List<Frame> createHeadersFrames(Response res, Encoder encoder, Settings peerSettings, boolean isAtEof) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        encoder.setMaxHeaderTableSize(baos, peerSettings.getHeaderTableSize());

        StringBuilder sb = log.isDebugEnabled() ? new StringBuilder() : null;

        String keyStatus = ":status";
        String valStatus = "" + res.getStatusCode();
        encoder.encodeHeader(baos, keyStatus.getBytes(StandardCharsets.US_ASCII), valStatus.getBytes(StandardCharsets.US_ASCII), false);
        if (sb != null) {
            sb.append("Headers on stream ");
            sb.append(streamId);
            sb.append(":\n");

            sb.append(keyStatus);
            sb.append(": ");
            sb.append(valStatus);
            sb.append("\n");
        }

        for (HeaderField hf : res.getHeader().getAllHeaderFields()) {
            String key = hf.getHeaderName().toString().toLowerCase();
            if ("keep-alive".equals(key) || "proxy-connection".equals(key) || "transfer-encoding".equals(key) || "upgrade".equals(key) || "connection".equals(key))
                continue;

            boolean sensitive = "set-cookie".equals(key);

            encoder.encodeHeader(baos, key.getBytes(StandardCharsets.US_ASCII), hf.getValue().getBytes(StandardCharsets.US_ASCII), sensitive);
            if (sb != null) {
                sb.append(key);
                sb.append(": ");
                sb.append(hf.getValue());
                if (sensitive)
                    sb.append("    (sensitive)");
                sb.append("\n");
            }
        }

        if (sb != null)
            log.debug(sb.toString());

        byte[] header = baos.toByteArray();
        List<Frame> frames = new ArrayList<>();

        int maxFrameSize = peerSettings.getMaxFrameSize();
        for (int offset = 0; offset < header.length; offset += maxFrameSize) {
            Frame frame = new Frame();
            boolean isLast = offset + maxFrameSize >= header.length;
            frame.fill(
                    offset == 0 ? TYPE_HEADERS : TYPE_CONTINUATION,
                    (isLast ? FLAG_END_HEADERS : 0) + (isAtEof ? FLAG_END_STREAM : 0),
                    streamId,
                    header,
                    offset,
                    Math.min(maxFrameSize, header.length - offset)
            );
            frames.add(frame);
        }

        return frames;
    }

    private void removeBodyFromBuffer() throws IOException {
        // TODO: is there anything to do here?
    }

    private void closeConnections() {
        // TODO: close stream
    }


}
