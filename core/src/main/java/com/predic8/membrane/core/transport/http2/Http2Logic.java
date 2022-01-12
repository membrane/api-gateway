package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.transport.http2.frame.*;
import com.predic8.membrane.core.util.EndOfStreamException;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;
import static com.predic8.membrane.core.transport.http2.frame.SettingsFrame.*;
import static com.predic8.membrane.core.transport.http2.frame.SettingsFrame.ID_SETTINGS_MAX_HEADER_LIST_SIZE;

public class Http2Logic {
    private static final Logger log = LoggerFactory.getLogger(Http2Logic.class.getName());

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
    volatile boolean receiving = true;
    Future<?> senderFuture;

    public Http2Logic(ExecutorService executor, Socket sourceSocket, InputStream srcIn, OutputStream srcOut, boolean showSSLExceptions, Http2MessageHandler messageHandler) {
        this.executor = executor;
        this.srcIn = srcIn;
        this.showSSLExceptions = showSSLExceptions;
        this.messageHandler = messageHandler;
        this.remoteAddr = getRemoteAddr(sourceSocket);

        log.info("started HTTP2 connection " + remoteAddr);

        int maxHeaderSize = 4096; // TODO: update this value, when a SETTINGS frame arrives
        int maxHeaderTableSize = 4096;
        decoder = new Decoder(maxHeaderSize, maxHeaderTableSize);
        Encoder encoder = new Encoder(maxHeaderTableSize); // TODO: update this value
        this.sender = new FrameSender(srcOut, encoder, peerSettings, streams, remoteAddr);
        flowControl = new FlowControl(0, sender, ourSettings);
        peerFlowControl = new PeerFlowControl(0, sender, peerSettings);
    }

    public void init() throws IOException {
        senderFuture = executor.submit(sender);

        Settings newSettings = new Settings();
        newSettings.copyFrom(ourSettings);
        newSettings.setMaxConcurrentStreams(50);
        updateSettings(newSettings);
    }

    public static String getRemoteAddr(Socket sourceSocket) {
        StringBuilder sb = new StringBuilder();
        InetAddress ia = sourceSocket.getInetAddress();
        if (ia != null)
            sb.append(ia.toString());
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
            sender.stop();
        }
    }

    private void updateSettings(Settings newSettings) throws IOException {
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
            default:
                // TODO
                throw new NotImplementedException("frame type " + frame.getType());
        }

    }

    private void handleFrame(GoawayFrame goawayFrame) throws IOException {
        int streamId = goawayFrame.getFrame().getStreamId();

        if (streamId != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        // TODO: implement this, once sending PUSH_PROMISE is implemented
    }

    private void handleFrame(RstStreamFrame rstStream) throws IOException {
        rstStream.validateSize();

        int streamId = rstStream.getFrame().getStreamId();

        if (streamId == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        StreamInfo streamInfo = streams.get(streamId);
        if (streamInfo == null)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // stream is in idle state

        streamInfo.receivedRstStream();
    }

    private void handleFrame(DataFrame dataFrame) throws IOException {
        if (dataFrame.getFrame().getStreamId() == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        StreamInfo streamInfo = streams.get(dataFrame.getFrame().getStreamId());

        if (streamInfo == null)
            throw new FatalConnectionException(ERROR_STREAM_CLOSED); // TODO: change to stream error

        streamInfo.receivedDataFrame(dataFrame);

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

        if (streamInfo == null) {
            streamInfo = new StreamInfo(streamId1, sender, peerSettings, ourSettings);
            streams.put(streamId1, streamInfo);
        }

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
            if (streamInfo == null)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // stream not open // TODO: change to stream error

            streamInfo.getPeerFlowControl().increment(windowUpdate.getWindowSizeIncrement());
        }
    }

    private void handleFrame(HeadersFrame headers) throws IOException {
        int streamId1 = headers.getFrame().getStreamId();
        if (streamId1 == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
        StreamInfo streamInfo = streams.get(streamId1);

        if (streamInfo == null) {
            streamInfo = new StreamInfo(streamId1, sender, peerSettings, ourSettings);
            streams.put(streamId1, streamInfo);
        }

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

        Message request = messageHandler.createMessage();

        StringBuilder sb = log.isDebugEnabled() ? new StringBuilder() : null;

        if (sb != null) {
            sb.append("Headers on stream ");
            sb.append(streamId1);
            sb.append(":\n");
        }

        decoder.decode(getPackedHeaderStream(headerFrames), new HeaderListener() {
            @Override
            public void addHeader(byte[] name, byte[] value, boolean sensitive) {
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
                    log.info("streamId=" + streamId1 + " uri=" + val);
                } else if (":status".equals(key) && request instanceof Response) {
                    ((Response) request).setStatusCode(Integer.parseInt(val));
                    log.debug("streamId=" + streamId1 + " status=" + val);
                } else
                    request.getHeader().add(key, val);
            }
        });
        decoder.endHeaderBlock();

        if (sb != null)
            log.debug(sb.toString());

        if (!headers.isEndStream())
            request.setBody(streamInfo.createBody());

        messageHandler.handleExchange(streamInfo, request, showSSLExceptions, remoteAddr);

        if (headers.isEndStream())
            streamInfo.receivedEndStream(false);
    }

    public InputStream getPackedHeaderStream(List<HeaderBlockFragment> headerFrames) {
        if (headerFrames.size() == 1) {
            HeaderBlockFragment one = headerFrames.get(0);
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
            ourSettings.copyFrom(wantedSettings.remove(0));
            return;
        }

        for (int i = 0; i < settings.getSettingsCount(); i++) {
            long settingsValue = settings.getSettingsValue(i);
            switch (settings.getSettingsId(i)) {
                case ID_SETTINGS_MAX_FRAME_SIZE:
                    if (settingsValue < 16384 || settingsValue > 16777215)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setMaxFrameSize((int) settingsValue);
                    break;
                case ID_SETTINGS_ENABLE_PUSH:
                    if (settingsValue != 0 && settingsValue != 1)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setEnablePush((int) settingsValue);
                    break;
                case ID_SETTINGS_HEADER_TABLE_SIZE:
                    if (settingsValue > Integer.MAX_VALUE) {
                        System.err.println("HEADER_TABLE_SIZE > Integer.MAX_VALUE received: " + settingsValue);
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // this is limited by our implementation
                    }
                    peerSettings.setHeaderTableSize((int) settingsValue);
                    break;
                case ID_SETTINGS_MAX_CONCURRENT_STREAMS:
                    if (settingsValue > Integer.MAX_VALUE)
                        peerSettings.setMaxConcurrentStreams(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        peerSettings.setMaxConcurrentStreams((int) settingsValue);
                    break;
                case ID_SETTINGS_INITIAL_WINDOW_SIZE:
                    if (settingsValue > 1 << 31 - 1)
                        throw new FatalConnectionException(ERROR_FLOW_CONTROL_ERROR);

                    int delta = (int) settingsValue - peerSettings.getInitialWindowSize();
                    for (StreamInfo si : streams.values()) {
                        si.getPeerFlowControl().increment(delta);
                    }

                    peerSettings.setInitialWindowSize((int) settingsValue);
                    break;
                case ID_SETTINGS_MAX_HEADER_LIST_SIZE:
                    if (settingsValue > Integer.MAX_VALUE)
                        peerSettings.setMaxHeaderListSize(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        peerSettings.setMaxHeaderListSize((int) settingsValue);
                    break;
                default:
                    System.err.println("not implemented: setting " + settings.getSettingsId(i));
            }
        }
        sender.send(SettingsFrame.ack());
    }

}
