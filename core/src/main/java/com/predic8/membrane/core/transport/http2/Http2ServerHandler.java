package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.*;
import com.predic8.membrane.core.transport.http2.frame.*;
import com.predic8.membrane.core.util.ByteUtil;
import com.predic8.membrane.core.util.DNSCache;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import com.twitter.hpack.HeaderListener;
import org.apache.commons.lang.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static com.predic8.membrane.core.transport.http2.frame.Frame.*;

public class Http2ServerHandler extends AbstractHttpHandler {
    private static final Logger log = LoggerFactory.getLogger(Http2ServerHandler.class.getName());
    private static final byte[] PREFACE = new byte[]{0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2f, 0x32, 0x2e,
            0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d, 0x0d, 0x0a, 0x0d, 0x0a};

    private final HttpServerHandler httpServerHandler;
    private final Socket sourceSocket;
    private final InputStream srcIn;
    private final OutputStream srcOut;
    private final FrameSender sender;
    private final boolean showSSLExceptions;
    private final Decoder decoder;
    private final String remoteAddr;
    private final FlowControl flowControl;
    private final PeerFlowControl peerFlowControl;

    ExecutorService executor = Executors.newCachedThreadPool();

    private Settings ourSettings = new Settings(); // TODO: changing our settings is not supported (and the ACK ignored)
    private Settings peerSettings = new Settings();
    private Map<Integer, StreamInfo> streams = new HashMap<>();

    public Http2ServerHandler(HttpServerHandler httpServerHandler, Socket sourceSocket, InputStream srcIn, OutputStream srcOut, boolean showSSLExceptions) {
        super(httpServerHandler.getTransport());

        this.httpServerHandler = httpServerHandler;
        this.sourceSocket = sourceSocket;
        this.srcIn = srcIn;
        this.srcOut = srcOut;
        this.showSSLExceptions = showSSLExceptions;

        int maxHeaderSize = 4096; // TODO: update this value, when a SETTINGS frame arrives
        int maxHeaderTableSize = 4096;
        decoder = new Decoder(maxHeaderSize, maxHeaderTableSize);
        Encoder encoder = new Encoder(maxHeaderTableSize); // TODO: update this value
        this.sender = new FrameSender(this, srcOut, encoder, ourSettings);
        flowControl = new FlowControl(0, sender, ourSettings);
        peerFlowControl = new PeerFlowControl(0, sender, peerSettings);

        StringBuilder sb = new StringBuilder();
        InetAddress ia = sourceSocket.getInetAddress();
        if (ia != null)
            sb.append(ia.toString());
        sb.append(":");
        sb.append(sourceSocket.getPort());
        remoteAddr = sb.toString();

    }

    public void handle() throws IOException {
        byte[] preface = ByteUtil.readByteArray(srcIn, 24);

        if (!isCorrectPreface(preface))
            throw new RuntimeException("Incorrect Preface.");

        sender.send(SettingsFrame.empty());

        Frame frame = new Frame(peerSettings);
        frame.read(srcIn);
        handleFrame(frame);

        while (true) {
            frame = new Frame(peerSettings);
            frame.read(srcIn);
            handleFrame(frame);
        }
    }

    private void handleFrame(Frame frame) throws IOException {
        if (log.isTraceEnabled())
            log.trace("received: " + frame);
        else if (log.isInfoEnabled())
            log.info("received: " + frame.getTypeString() + " length=" + frame.getLength());

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
            case TYPE_GOAWAY:
            case TYPE_PUSH_PROMISE:
            case TYPE_RST_STREAM:
            default:
                // TODO
                throw new NotImplementedException("frame type " + frame.getType());
        }

    }

    private void handleFrame(DataFrame dataFrame) throws IOException {
        if (dataFrame.getFrame().getStreamId() == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        StreamInfo streamInfo = streams.get(dataFrame.getFrame().getStreamId());

        if (streamInfo == null)
            throw new FatalConnectionException(ERROR_STREAM_CLOSED); // TODO: change to stream error

        streamInfo.addDataFrame(dataFrame);

        // TODO: If a DATA frame is received
        //   whose stream is not in "open" or "half-closed (local)" state, the
        //   recipient MUST respond with a stream error (Section 5.4.2) of type
        //   STREAM_CLOSED.

        flowControl.received(dataFrame.getDataLength());
        flowControl.processed(dataFrame.getDataLength());
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
        if (priority.getFrame().getStreamId() == 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (priority.getFrame().getLength() != 5)
            throw new FatalConnectionException(ERROR_FRAME_SIZE_ERROR); // TODO: convert to stream error

        // TODO
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
            streamInfo = new StreamInfo(streamId1, this);
            streams.put(streamId1, streamInfo);
        }

        if (streamInfo.state != StreamState.IDLE && streamInfo.state != StreamState.RESERVED_REMOTE && streamInfo.state != StreamState.RESERVED_LOCAL && streamInfo.state != StreamState.OPEN)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (streamInfo.state == StreamState.IDLE)
            streamInfo.state = StreamState.OPEN;


        List<HeaderBlockFragment> headerFrames = new ArrayList<>();
        headerFrames.add(headers);

        HeaderBlockFragment last = headers;

        while (!last.isEndHeaders()) {
            Frame frame = new Frame(peerSettings);
            frame.read(srcIn);

            if (frame.getType() != TYPE_CONTINUATION)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

            last = frame.asContinuation();

            int streamId = frame.getStreamId();
            if (streamId != streamId1)
                throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

            headerFrames.add(last);
        }

        Exchange exchange = new Exchange(this);
        Request request = new Request();
        exchange.setRequest(request);

        StringBuilder sb = log.isInfoEnabled() ? new StringBuilder() : null;

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

                if (":method".equals(key))
                    request.setMethod(val);
                else if (":scheme".equals(key))
                    ; // ignore
                else if (":authority".equals(key))
                    request.getHeader().setHost(val);
                else if (":path".equals(key))
                    request.setUri(val);
                else
                    request.getHeader().add(key, val);
            }
        });
        decoder.endHeaderBlock();

        if (sb != null)
            log.info(sb.toString());

        if (!headers.isEndStream())
            request.setBody(streamInfo.createBody());

        exchange.received();
        DNSCache dnsCache = httpServerHandler.getTransport().getRouter().getDnsCache();
        InetAddress remoteAddr2 = sourceSocket.getInetAddress();
        String ip = dnsCache.getHostAddress(remoteAddr2);
        exchange.setRemoteAddrIp(ip);
        exchange.setRemoteAddr(httpServerHandler.getTransport().isReverseDNS() ? dnsCache.getHostName(remoteAddr2) : ip);

        exchange.setRequest(request);
        exchange.setOriginalRequestUri(request.getUri());

        executor.submit(new Http2ExchangeHandler(streamInfo, this, exchange, showSSLExceptions, remoteAddr));


        handleStreamEnd(streamInfo, headers);
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

    private void handleStreamEnd(StreamInfo streamInfo, StreamEnd streamEnd) {
        if (streamEnd.isEndStream()) {
            streamInfo.state = StreamState.HALF_CLOSED_REMOTE;
        }
    }

    private void handleFrame(SettingsFrame settings) throws IOException {
        if (settings.getFrame().getLength() % 6 != 0)
            throw new FatalConnectionException(ERROR_FRAME_SIZE_ERROR);

        if (settings.getFrame().getStreamId() != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        if (settings.isAck())
            return; // TODO: updating our settings is not implemented

        for (int i = 0; i < settings.getSettingsCount(); i++) {
            long settingsValue = settings.getSettingsValue(i);
            switch (settings.getSettingsId(i)) {
                case SettingsFrame.ID_SETTINGS_MAX_FRAME_SIZE:
                    if (settingsValue < 16384 || settingsValue > 16777215)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setMaxFrameSize((int) settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_ENABLE_PUSH:
                    if (settingsValue != 0 && settingsValue != 1)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    peerSettings.setEnablePush((int) settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_HEADER_TABLE_SIZE:
                    if (settingsValue > Integer.MAX_VALUE) {
                        System.err.println("HEADER_TABLE_SIZE > Integer.MAX_VALUE received: " + settingsValue);
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // this is limited by our implementation
                    }
                    peerSettings.setHeaderTableSize((int) settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_MAX_CONCURRENT_STREAMS:
                    if (settingsValue > Integer.MAX_VALUE)
                        peerSettings.setMaxConcurrentStreams(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        peerSettings.setMaxConcurrentStreams((int) settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_INITIAL_WINDOW_SIZE:
                    if (settingsValue > 1 << 31 - 1)
                        throw new FatalConnectionException(ERROR_FLOW_CONTROL_ERROR);

                    int delta = (int) settingsValue - peerSettings.getInitialWindowSize();
                    for (StreamInfo si : streams.values()) {
                        si.getPeerFlowControl().increment(delta);
                    }

                    // TODO: should peerFlowControl also be incremented?

                    peerSettings.setInitialWindowSize((int) settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_MAX_HEADER_LIST_SIZE:
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

    private boolean isCorrectPreface(byte[] preface) {
        if (preface.length != PREFACE.length)
            return false;
        for (int i = 0; i < PREFACE.length; i++)
            if (preface[i] != PREFACE[i])
                return false;
        return true;
    }

    public HttpServerHandler getHttpServerHandler() {
        return httpServerHandler;
    }

    @Override
    public void shutdownInput() throws IOException {
        throw new NotImplementedException();
    }

    @Override
    public InetAddress getLocalAddress() {
        return httpServerHandler.getLocalAddress();
    }

    @Override
    public int getLocalPort() {
        return httpServerHandler.getLocalPort();
    }

    public FrameSender getSender() {
        return sender;
    }

    public Settings getOurSettings() {
        return ourSettings;
    }

    public Settings getPeerSettings() {
        return peerSettings;
    }

    public PeerFlowControl getPeerFlowControl() {
        return peerFlowControl;
    }

    public StreamInfo getStreamInfo(int streamId) {
        return streams.get(streamId);
    }
}