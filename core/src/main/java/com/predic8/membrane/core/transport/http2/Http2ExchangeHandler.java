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

import com.google.common.collect.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.HeaderField;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.transport.*;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http2.frame.*;
import com.predic8.membrane.core.util.*;
import com.twitter.hpack.*;
import org.slf4j.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.util.*;

import static com.predic8.membrane.core.exchange.ExchangeState.COMPLETED;
import static com.predic8.membrane.core.transport.http.AbstractHttpHandler.*;
import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.HeadersFrame.*;
import static java.nio.charset.StandardCharsets.*;

public class Http2ExchangeHandler implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Http2ExchangeHandler.class.getName());
    private static final InterceptorFlowController flowController = new InterceptorFlowController();

    private final StreamInfo streamInfo;
    private final Transport transport;
    private final FrameSender sender;
    private final Settings peerSettings;
    private final PeerFlowControl peerFlowControl;
    private final Exchange exchange;
    private final boolean showSSLExceptions;
    private final String remoteAddr;
    private final int streamId;

    public Http2ExchangeHandler(StreamInfo streamInfo, Transport transport, FrameSender sender, Settings peerSettings, PeerFlowControl peerFlowControl, Exchange exchange, boolean showSSLExceptions, String remoteAddr) {
        this.streamInfo = streamInfo;
        this.transport = transport;
        this.sender = sender;
        this.peerSettings = peerSettings;
        this.peerFlowControl = peerFlowControl;
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
                if (s.getCause() instanceof SSLException) {
                    log.error("Caused by",s.getCause());
                    return;
                }
                if (s.getCause() instanceof SocketException)
                    log.debug("ssl socket closed");
                else
                    log.error("", s);
            }
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
        } finally {

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
            flowController.invokeHandlers(exchange, transport.getInterceptors());
            if (exchange.getResponse() == null)
                throw new AbortException("No response was generated by the interceptor chain.");
        } catch (Exception e) {
            if (exchange.getResponse() == null)
                exchange.setResponse(generateErrorResponse(e, exchange, transport));

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
            String sb = HttpServerThreadFactory.DEFAULT_THREAD_NAME +
                    " " +
                    remoteAddr +
                    " stream " +
                    streamId;
            Thread.currentThread().setName(sb);
        } else {
            Thread.currentThread().setName(HttpServerThreadFactory.DEFAULT_THREAD_NAME);
        }
    }

    protected void writeResponse(Response res) throws Exception {
        sender.send(streamId, (encoder, peerSettings) -> createHeadersFrames(res, res.getHeader(), streamId, encoder, peerSettings, false));

        writeMessageBody(streamId, streamInfo, sender, peerSettings, peerFlowControl, res);

        exchange.setTimeResSent(System.currentTimeMillis());
        exchange.collectStatistics();
    }

    public static void writeMessageBody(final int streamId, final StreamInfo streamInfo, final FrameSender sender, final Settings peerSettings, final PeerFlowControl peerFlowControl, Message res) throws IOException {
        res.getBody().write(new AbstractBodyTransferrer() {
            @Override
            public void write(byte[] content, int i, int length) {
                sendData(content, i, length);
            }

            private void sendData(byte[] content, int offset, int length) {
                int mOffset = offset;
                while (mOffset < offset + length) {
                    int mLength = Math.min(peerSettings.getMaxFrameSize(), length - (mOffset - offset));

                    // as we do not send padding, reserve exactly the length we want to send
                    streamInfo.getPeerFlowControl().reserve(mLength, streamId);
                    peerFlowControl.reserve(mLength, streamId);

                    Frame frame = new Frame();
                    frame.fill(TYPE_DATA,
                            0,
                            streamId,
                            content,
                            mOffset,
                            mLength);

                    sender.send(frame);

                    mOffset += mLength;
                }
            }

            @Override
            public void write(Chunk chunk) {
                sendData(chunk.getContent(), 0, chunk.getLength());
            }

            @Override
            public void finish(Header header) throws IOException {
                if (header != null) {
                    // wait for sender queue to empty
                    // TODO: this could be solved via synchronization
                    while (!streamInfo.getDataFramesToBeSent().isEmpty()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    sender.send(streamId, (encoder, peerSettings) -> createHeadersFrames(null, header, streamId, encoder, peerSettings, true));
                } else {
                    Frame frame = new Frame();
                    frame.fill(TYPE_DATA,
                            FLAG_END_STREAM,
                            streamId,
                            null,
                            0,
                            0);

                    sender.send(frame);
                }
            }
        }, false);

    }

    public static List<Frame> createHeadersFrames(Message res, Header header, int streamId, Encoder encoder, Settings peerSettings, boolean isAtEof) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        encoder.setMaxHeaderTableSize(baos, peerSettings.getHeaderTableSize());

        StringBuilder sb = null;
        if (log.isDebugEnabled()) {
            sb = new StringBuilder();
            sb.append("Headers on stream ");
            sb.append(streamId);
            sb.append(":\n");
        }

        if (res instanceof Request req) {
            encodeHeader(encoder, baos, sb, ":method", req.getMethod());
            encodeHeader(encoder, baos, sb, ":scheme", "https");
            encodeHeader(encoder, baos, sb, ":path", req.getUri());
            encodeHeader(encoder, baos, sb, ":authority", req.getHeader().getHost());
        }
        if (res instanceof Response response) {
            encodeHeader(encoder, baos, sb, ":status", "" + response.getStatusCode());
        }

        for (HeaderField hf : header.getAllHeaderFields()) {
            String key = hf.getHeaderName().toString().toLowerCase();
            if ("keep-alive".equals(key) || "proxy-connection".equals(key) || "transfer-encoding".equals(key) || "upgrade".equals(key) || "connection".equals(key) || "host".equals(key))
                continue;

            encodeHeader(encoder, baos, sb, key, hf.getValue());
        }

        if (sb != null)
            log.debug(sb.toString());

        return constructFrames(streamId, peerSettings, isAtEof, baos.toByteArray());
    }

    private static void encodeHeader(Encoder encoder, ByteArrayOutputStream baos, StringBuilder sb, String key, String val) throws IOException {
        boolean sensitive = "set-cookie".equals(key);
        encoder.encodeHeader(baos, key.getBytes(US_ASCII), val.getBytes(US_ASCII), sensitive);
        logHeader(sb, key, val, sensitive);
    }

    private static void logHeader(StringBuilder sb, String key, String val, boolean sensitive) {
        if (sb != null) {
            sb.append(key);
            sb.append(": ");
            sb.append(val);
            if (sensitive)
                sb.append("    (sensitive)");
            sb.append("\n");
        }
    }

    private static List<Frame> constructFrames(int streamId, Settings peerSettings, boolean isAtEof, byte[] buffer) {
        List<Frame> frames = new ArrayList<>();

        int maxFrameSize = peerSettings.getMaxFrameSize();
        for (int offset = 0; offset < buffer.length; offset += maxFrameSize) {
            frames.add(constructFrame(streamId, isAtEof, buffer, maxFrameSize, offset));
        }
        return frames;
    }

    private static Frame constructFrame(int streamId, boolean isAtEof, byte[] buffer, int maxFrameSize, int offset) {
        Frame frame = new Frame();
        frame.fill(
                offset == 0 ? TYPE_HEADERS : TYPE_CONTINUATION,
                (isLast(buffer, maxFrameSize, offset) ? FLAG_END_HEADERS : 0) + (isAtEof ? FLAG_END_STREAM : 0),
                streamId,
                buffer,
                offset,
                Math.min(maxFrameSize, buffer.length - offset)
        );
        return frame;
    }

    private static boolean isLast(byte[] buffer, int maxFrameSize, int offset) {
        return offset + maxFrameSize >= buffer.length;
    }

    private void removeBodyFromBuffer() {
        // TODO: is there anything to do here?
    }

    private void closeConnections() {
        // TODO: improve condition for RST_STREAM
        if (exchange.getStatus() != COMPLETED) {
            try {
                sender.send(streamId, (encoder, peerSettings) -> ImmutableList.of(RstStreamFrame.construct(streamId,
                        ERROR_INTERNAL_ERROR)));
            } catch (IOException e) {
                // do nothing
            }
        }
    }


}
