/* Copyright 2022 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.http.Header;
import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http2.frame.*;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.SettingsFrame.*;

public class Http2Logic {
    private static final Logger log = LoggerFactory.getLogger(Http2Logic.class.getName());

    /** Like a header line in HTTP/1, a header field ("name: value") must be shorter than this. */
    private final static int MAX_LINE_LENGTH;

    /** Maximum size of a decoded header block, announced to the peer as SETTINGS_MAX_HEADER_LIST_SIZE. */
    static final int MAX_HEADER_LIST_SIZE = 64 * 1024;

    static {
        String maxLineLength = System.getProperty("membrane.core.http.body.maxlinelength");
        MAX_LINE_LENGTH = maxLineLength == null ? 8092 : Integer.parseInt(maxLineLength);
    }

    final InputStream srcIn;
    final FrameSender sender;
    private final boolean showSSLExceptions;
    private final Decoder decoder;
    private final String remoteAddr;
    private final FlowControl flowControl;
    final PeerFlowControl peerFlowControl;
    final Settings ourSettings = new Settings();
    final Settings peerSettings = new Settings();
    private final List<Settings> wantedSettings = new ArrayList<>();
    final Map<Integer, StreamInfo> streams = new ConcurrentHashMap<>();
    private final PriorityTree priorityTree = new PriorityTree();
    private final ExecutorService executor;
    private final Http2MessageHandler messageHandler;
    final AtomicInteger nextClientStreamId = new AtomicInteger(1);
    /** Highest id of a stream the peer opened. Only accessed on the receiver thread. */
    private int highestPeerStreamId;
    volatile boolean receiving = true;
    volatile Future<?> senderFuture;
    @GuardedBy("this")
    private boolean sendingStopped;

    public Http2Logic(ExecutorService executor, Socket sourceSocket, InputStream srcIn, OutputStream srcOut, boolean showSSLExceptions, Http2MessageHandler messageHandler) {
        this.executor = executor;
        this.srcIn = srcIn;
        this.showSSLExceptions = showSSLExceptions;
        this.messageHandler = messageHandler;
        this.remoteAddr = getRemoteAddr(sourceSocket);

        log.debug("started HTTP2 connection " + remoteAddr);

        int maxHeaderTableSize = 4096; // TODO: update with SETTINGS_HEADER_TABLE_SIZE https://datatracker.ietf.org/doc/html/rfc9113#section-4.3.1
        decoder = new Decoder(MAX_HEADER_LIST_SIZE, maxHeaderTableSize);
        // TODO: update this value
        this.sender = new FrameSender(srcOut, new Encoder(maxHeaderTableSize), peerSettings, streams, remoteAddr);
        flowControl = new FlowControl(0, sender, ourSettings);
        peerFlowControl = sender.getConnectionFlowControl();

        Settings newSettings = new Settings();
        newSettings.copyFrom(ourSettings);
        newSettings.setMaxConcurrentStreams(50);
        newSettings.setMaxHeaderListSize(MAX_HEADER_LIST_SIZE);
        newSettings.setInitialWindowSize(65535 * 2);
        updateSettings(newSettings);
    }

    public void init() throws IOException {
        // Published before the sender runs, so whoever sees frames it sent can wait for it.
        final var task = new FutureTask<>(sender, null);
        // A shutdown that ran first saw no sender to wait for, so none may start afterwards.
        synchronized (this) {
            if (sendingStopped)
                return;
            senderFuture = task;
            executor.execute(task);
        }
    }

    public static String getRemoteAddr(Socket sourceSocket) {
        StringBuilder sb = new StringBuilder();
        InetAddress ia = sourceSocket.getInetAddress();
        if (ia != null)
            sb.append(ia);
        sb.append(":");
        sb.append(sourceSocket.getPort());
        return sb.toString();
    }

    public void handle() throws IOException, EndOfStreamException {
        try {
            while (receiving) {
                Frame frame = new Frame(ourSettings);
                frame.read(srcIn);
                handleFrame(frame);
            }
        } catch(EOFException eof) {
            throw new EndOfStreamException("");
        } finally {
            stopSending();
            // No more frames arrive: body readers still waiting for DATA would wait forever.
            for (StreamInfo streamInfo : streams.values())
                streamInfo.abortReceiving(new IOException("HTTP/2 connection closed before the stream ended"));
        }
    }

    /**
     * Closed streams are removed from {@link #streams}, so an unknown id belongs either to a closed
     * stream or to an idle one. Ids up to the highest one opened belong to closed streams (RFC 9113 §5.1.1).
     * Clients open odd ids and servers even ones, so only the opener's own ids are compared.
     */
    private boolean isClosed(int streamId) {
        return streamId % 2 == highestPeerStreamId % 2 && streamId <= highestPeerStreamId
                || streamId % 2 == 1 && streamId < nextClientStreamId.get();
    }

    /** Stops the frame sender and wakes writers waiting for the connection's flow-control window. */
    void stopSending() {
        synchronized (this) {
            sendingStopped = true;
        }
        sender.stop();
        peerFlowControl.senderStopped();
    }

    private void updateSettings(Settings newSettings) {
        wantedSettings.add(newSettings);
        sender.send(SettingsFrame.diff(ourSettings, newSettings));
    }

    private void handleFrame(Frame frame) throws IOException {
        if (log.isTraceEnabled())
            log.trace("received: " + frame);
        else if (log.isDebugEnabled())
            log.debug("received: " + frame.getTypeString() + " length=" + frame.getLength());

        switch (frame.getType()) {
            case TYPE_SETTINGS:
                handleFrame(frame.asSettings());
                break;
            case TYPE_HEADERS:
                handleFrame(frame.asHeaders());
                break;
            case TYPE_WINDOW_UPDATE:
                handleFrame(frame.asWindowUpdate());
                break;
            case TYPE_CONTINUATION:
                handleFrame(frame.asContinuation());
                break;
            case TYPE_PRIORITY:
                handleFrame(frame.asPriority());
                break;
            case TYPE_PING:
                handleFrame(frame.asPing());
                break;
            case TYPE_DATA:
                handleFrame(frame.asData());
                break;
            case TYPE_RST_STREAM:
                handleFrame(frame.asRstStream());
                break;
            case TYPE_GOAWAY:
                handleFrame(frame.asGoaway());
                break;
            case TYPE_PUSH_PROMISE:
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
            default:
                log.debug("ignoring frame of unknown type {}", frame.getType());
                break;
        }

    }

    private void handleFrame(GoawayFrame goawayFrame) throws IOException {
        if (goawayFrame.getFrame().getStreamId() != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        // TODO: implement this, once sending PUSH_PROMISE is implemented
    }

    private void handleFrame(RstStreamFrame rstStream) throws IOException {
        rstStream.validateSize();

        int streamId = rstStream.getFrame().getStreamId();

        if (streamId == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        StreamInfo streamInfo = streams.get(streamId);
        if (streamInfo == null) {
            if (isClosed(streamId))
                return;
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // stream is in idle state
        }

        streamInfo.receivedRstStream(rstStream.getErrorCode());
        messageHandler.handleStreamReset(streamId, rstStream.getErrorCode());
    }

    private void handleFrame(DataFrame dataFrame) throws IOException {
        if (dataFrame.getFrame().getStreamId() == 0 || dataFrame.getDataLength() < 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        StreamInfo streamInfo = streams.get(dataFrame.getFrame().getStreamId());

        if (streamInfo == null) {
            if (!isClosed(dataFrame.getFrame().getStreamId()))
                throw new FatalConnectionException(ERROR_STREAM_CLOSED); // TODO: change to stream error
            // Still in flight when the stream was closed, e.g. by our RST_STREAM. It counts against
            // the connection's window all the same (RFC 9113 §6.9).
            log.debug("Dropping DATA for closed stream {}.", dataFrame.getFrame().getStreamId());
        } else {
            streamInfo.receivedDataFrame(dataFrame);
        }

        // TODO: If a DATA frame is received
        //   whose stream is not in "open" or "half-closed (local)" state, the
        //   recipient MUST respond with a stream error (Section 5.4.2) of type
        //   STREAM_CLOSED.

        flowControl.received(dataFrame.getFrame().getLength());
        flowControl.processed(dataFrame.getFrame().getLength());
    }

    private void handleFrame(PingFrame ping) throws IOException {
        if (ping.isAck())
            return;

        if (ping.getFrame().getStreamId() != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (ping.getFrame().getLength() != 8)
            throw new FatalConnectionException(ERROR_FRAME_SIZE_ERROR);

        sender.send(PingFrame.pong(ping));
    }

    private void handleFrame(PriorityFrame priority) throws IOException {
        priority.validateSize();

        if (priority.getFrame().getStreamId() == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        int streamId1 = priority.getFrame().getStreamId();

        StreamInfo streamInfo = streams.get(streamId1);

        // For a closed or an idle stream: a stream created for an idle one would never close, counting
        // against the concurrent stream limit and keeping the connection busy. Priority signaling is
        // deprecated anyway (RFC 9113 §5.3.2).
        if (streamInfo == null)
            return;

        streamInfo.receivedPriority();

        priorityTree.reprioritize(streamInfo, priority.getWeight(), streams.get(priority.getStreamDependency()), priority.isExclusive());
    }

    private void handleFrame(WindowUpdateFrame windowUpdate) throws IOException {
        if (windowUpdate.getFrame().getStreamId() == 0) {
            if (windowUpdate.getWindowSizeIncrement() == 0)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

            peerFlowControl.increment(windowUpdate.getWindowSizeIncrement());
        } else {
            if (windowUpdate.getWindowSizeIncrement() == 0)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // TODO: change to stream error

            StreamInfo streamInfo = streams.get(windowUpdate.getFrame().getStreamId());
            if (streamInfo == null) {
                // The peer may still send WINDOW_UPDATE after the stream was closed (RFC 9113 §6.9).
                if (isClosed(windowUpdate.getFrame().getStreamId()))
                    return;
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // stream not open // TODO: change to stream error
            }

            streamInfo.getPeerFlowControl().increment(windowUpdate.getWindowSizeIncrement());
        }
    }

    private void handleFrame(HeadersFrame headers) throws IOException {
        int streamId1 = headers.getFrame().getStreamId();
        if (streamId1 == 0 || headers.getHeaderBlockLength() < 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
        StreamInfo streamInfo = streams.get(streamId1);

        if (streamInfo == null) {
            // Like HEADERS on a stream in the CLOSED state, which receivedHeaders() rejects.
            if (isClosed(streamId1))
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
            streamInfo = new StreamInfo(streamId1, sender, peerSettings, ourSettings);
            streams.put(streamId1, streamInfo);
        }
        highestPeerStreamId = Math.max(highestPeerStreamId, streamId1);

        boolean isTrailer = streamInfo.isTrailer();

        streamInfo.receivedHeaders();

        if (headers.isPriority())
            priorityTree.reprioritize(streamInfo, headers.getWeight(), streams.get(headers.getStreamDependency()), headers.isExclusive());
        else
            priorityTree.reprioritize(streamInfo, 16, null, false);

        List<HeaderBlockFragment> headerFrames = new ArrayList<>();
        headerFrames.add(headers);

        HeaderBlockFragment last = headers;

        while (!last.isEndHeaders()) {
            Frame frame = new Frame(ourSettings);
            frame.read(srcIn);

            if (frame.getType() != TYPE_CONTINUATION)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

            last = frame.asContinuation();

            int streamId = frame.getStreamId();
            if (streamId != streamId1)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

            headerFrames.add(last);
        }

        log.debug("header isTrailer={}", isTrailer);
        Message request;
        Header header;
        if (isTrailer) {
            request = streamInfo.getMessage();
            header = new Header();
        } else {
            request = messageHandler.createMessage();
            streamInfo.setMessage(request);
            header = request.getHeader();
        }

        StringBuilder sb = log.isDebugEnabled() ? new StringBuilder() : null;

        if (sb != null) {
            sb.append("Headers on stream ");
            sb.append(streamId1);
            sb.append(":\n");
        }

        final var oversizedField = new boolean[1];
        // The whole block is decoded even if it gets rejected, so the HPACK dynamic table stays in sync.
        decoder.decode(getPackedHeaderStream(headerFrames), (name, value, sensitive) -> {
            if (name.length + 2 + value.length >= MAX_LINE_LENGTH) {
                oversizedField[0] = true;
                return;
            }
            String key = new String(name);
            String val = new String(value);

            if (sb != null) {
                sb.append(key);
                sb.append(": ");
                sb.append(val);
                sb.append("\n");
            }

            if (":method".equals(key) && request instanceof Request)
                ((Request)request).setMethod(val);
            else if (":scheme".equals(key))
                ; // ignore
            else if (":authority".equals(key))
                request.getHeader().setHost(val);
            else if (":path".equals(key) && request instanceof Request) {
                ((Request) request).setUri(val);
                log.debug("streamId=" + streamId1 + " uri=" + val);
            } else if (":status".equals(key) && request instanceof Response) {
                ((Response) request).setStatusCode(Integer.parseInt(val));
                log.debug("streamId=" + streamId1 + " status=" + val);
            } else
                header.add(key, val);
        });
        final var truncated = decoder.endHeaderBlock();

        if (sb != null)
            log.debug(sb.toString());

        if (oversizedField[0] || truncated) {
            log.info("Rejecting header block on stream {}: a header field exceeds {} bytes or the block exceeds {} bytes.",
                    streamId1, MAX_LINE_LENGTH, MAX_HEADER_LIST_SIZE);
            if (isTrailer) {
                // The message has already been handed over: fail its body and drop the stream.
                // Aborting before the end of stream is queued, as the body reader stops at the first END_STREAM.
                streamInfo.abortReceiving(new IOException("HTTP/2 trailer on stream %d exceeds the header size limit.".formatted(streamId1)));
                if (headers.isEndStream())
                    streamInfo.receivedEndStream(false);
                sender.send(streamId1, (encoder, peerSettings) -> List.of(RstStreamFrame.construct(streamId1, ERROR_CANCEL)));
            } else {
                if (headers.isEndStream())
                    streamInfo.receivedEndStream(false);
                messageHandler.handleOversizedHeaders(streamInfo, headers.isEndStream());
            }
            return;
        }

        if (!isTrailer && request instanceof Response response && response.getStatusCode() >= 100 && response.getStatusCode() < 200) {
            // An interim response (RFC 9113 §8.1): the final response follows in another HEADERS frame.
            if (headers.isEndStream())
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
            log.debug("streamId={} skipping interim response {}", streamId1, response.getStatusCode());
            streamInfo.receivedInterimResponse();
            return;
        }

        if (isTrailer) {
            request.getBody().setTrailer(header);
        } else {
            if (!headers.isEndStream())
                request.setBody(streamInfo.createBody());

            messageHandler.handleExchange(streamInfo, request, showSSLExceptions, remoteAddr);
        }

        if (headers.isEndStream())
            streamInfo.receivedEndStream(false);
    }

    public InputStream getPackedHeaderStream(List<HeaderBlockFragment> headerFrames) {
        if (headerFrames.size() == 1) {
            HeaderBlockFragment one = headerFrames.getFirst();
            return new ByteArrayInputStream(one.getContent(), one.getHeaderBlockStartIndex(), one.getHeaderBlockLength());
        }

        // TODO: improve memory + performance
        int sum = 0;
        for (HeaderBlockFragment hbf : headerFrames)
            sum += hbf.getHeaderBlockLength();
        byte[] buf = new byte[sum];

        int offset = 0;
        for (HeaderBlockFragment hbf : headerFrames) {
            System.arraycopy(hbf.getContent(), hbf.getHeaderBlockStartIndex(), buf, offset, hbf.getHeaderBlockLength());
            offset += hbf.getHeaderBlockLength();
        }

        return new ByteArrayInputStream(buf);
    }


    private void handleFrame(ContinuationFrame continuation) throws IOException {
        // CONTIUATION frames are handled within handleFrame(HeadersFrame) and handleFrame(PushPromiseFrame)
        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
    }

    private void handleFrame(SettingsFrame settings) throws IOException {
        if (settings.getFrame().getLength() % 6 != 0)
            throw new FatalConnectionException(ERROR_FRAME_SIZE_ERROR);

        if (settings.getFrame().getStreamId() != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (settings.isAck()) {
            if (wantedSettings.isEmpty())
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // nothing to acknowledge
            ourSettings.copyFrom(wantedSettings.removeFirst());
            return;
        }

        for (int i = 0; i < settings.getSettingsCount(); i++) {
            long settingsValue = settings.getSettingsValue(i);
            switch (settings.getSettingsId(i)) {
                case ID_SETTINGS_MAX_FRAME_SIZE -> {
                    if (settingsValue < 16384 || settingsValue > 16777215)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setMaxFrameSize((int) settingsValue);
                }
                case ID_SETTINGS_ENABLE_PUSH -> {
                    if (settingsValue != 0 && settingsValue != 1)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setEnablePush((int) settingsValue);
                }
                case ID_SETTINGS_HEADER_TABLE_SIZE -> {
                    if (settingsValue > Integer.MAX_VALUE) {
                        System.err.println("HEADER_TABLE_SIZE > Integer.MAX_VALUE received: " + settingsValue);
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // this is limited by our implementation
                    }
                    peerSettings.setHeaderTableSize((int) settingsValue);
                }
                case ID_SETTINGS_MAX_CONCURRENT_STREAMS -> {
                    if (settingsValue > Integer.MAX_VALUE)
                        peerSettings.setMaxConcurrentStreams(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        peerSettings.setMaxConcurrentStreams((int) settingsValue);
                }
                case ID_SETTINGS_INITIAL_WINDOW_SIZE -> {
                    if (settingsValue > Integer.MAX_VALUE)
                        throw new FatalConnectionException(ERROR_FLOW_CONTROL_ERROR);
                    int delta = (int) settingsValue - peerSettings.getInitialWindowSize();
                    for (StreamInfo si : streams.values()) {
                        si.getPeerFlowControl().increment(delta);
                    }
                    peerSettings.setInitialWindowSize((int) settingsValue);
                }
                case ID_SETTINGS_MAX_HEADER_LIST_SIZE -> {
                    if (settingsValue > Integer.MAX_VALUE)
                        peerSettings.setMaxHeaderListSize(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        peerSettings.setMaxHeaderListSize((int) settingsValue);
                }
                default -> System.err.println("not implemented: setting " + settings.getSettingsId(i));
            }
        }
        sender.send(SettingsFrame.ack());
    }

}
