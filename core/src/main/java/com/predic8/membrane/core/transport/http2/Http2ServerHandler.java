package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.FatalConnectionException;
import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.transport.http2.frame.HeadersFrame;
import com.predic8.membrane.core.transport.http2.frame.SettingsFrame;
import com.predic8.membrane.core.util.ByteUtil;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.HeaderListener;
import org.apache.commons.lang.NotImplementedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import static com.predic8.membrane.core.transport.http2.frame.Error.*;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_HEADERS;
import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_SETTINGS;

public class Http2ServerHandler {
    private static final byte[] PREFACE = new byte[] { 0x50, 0x52, 0x49, 0x20, 0x2a, 0x20, 0x48, 0x54, 0x54, 0x50, 0x2f, 0x32, 0x2e,
            0x30, 0x0d, 0x0a, 0x0d, 0x0a, 0x53, 0x4d, 0x0d, 0x0a, 0x0d, 0x0a };

    private final Socket sourceSocket;
    private final InputStream srcIn;
    private final OutputStream srcOut;
    private final FrameSender sender;

    private Settings sendSettins = new Settings(); // TODO: changing the sender settings is not supported.
    private Settings recSettings = new Settings();

    public Http2ServerHandler(Socket sourceSocket, InputStream srcIn, OutputStream srcOut) {
        this.sourceSocket = sourceSocket;
        this.srcIn = srcIn;
        this.srcOut = srcOut;
        this.sender = new FrameSender(srcOut);
    }

    public void handle() throws IOException {
        byte[] preface = ByteUtil.readByteArray(srcIn, 24);

        if (!isCorrectPreface(preface))
            throw new RuntimeException("Incorrect Preface.");

        Frame frame = new Frame(recSettings);
        frame.read(srcIn);
        handleFrame(frame);

        sender.send(SettingsFrame.empty());

        while(true) {
            frame = new Frame(recSettings);
            frame.read(srcIn);
            handleFrame(frame);
        }
    }

    private void handleFrame(Frame frame) throws IOException {
        System.out.println(frame);

        switch (frame.getType()) {
            case TYPE_SETTINGS:
                handleFrame(frame.asSettings());
                break;
            case TYPE_HEADERS:
                handleFrame(frame.asHeaders());
                break;
            default:
                // TODO
                throw new NotImplementedException("frame type " + frame.getType());
        }

    }

    private void handleFrame(HeadersFrame headers) throws IOException {
        // decode header list from header block
        int maxHeaderSize = 4096;
        int maxHeaderTableSize = 4096;
        Decoder decoder = new Decoder(maxHeaderSize, maxHeaderTableSize);
        decoder.decode(new ByteArrayInputStream(headers.getContent(), headers.getHeaderBlockStartIndex(), headers.getHeaderBlockLength()), new HeaderListener() {
            @Override
            public void addHeader(byte[] name, byte[] value, boolean sensitive) {
                System.err.println(new String(name) + ": " + new String(value));
                // TODO: handle header
                // handle header field
            }
        });
        decoder.endHeaderBlock();
    }

    private void handleFrame(SettingsFrame settings) throws IOException {
        if (settings.getFrame().getLength() % 6 != 0)
            throw new FatalConnectionException(ERROR_FRAME_SIZE_ERROR);

        if (settings.getFrame().getStreamId() != 0)
            throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);

        for (int i = 0; i < settings.getSettingsCount(); i++) {
            long settingsValue = settings.getSettingsValue(i);
            switch (settings.getSettingsId(i)) {
                case SettingsFrame.ID_SETTINGS_MAX_FRAME_SIZE:
                    if (settingsValue < 16384 || settingsValue > 16777215)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    recSettings.setMaxFrameSize((int)settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_ENABLE_PUSH:
                    if (settingsValue == 0 || settingsValue == 1)
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR);
                    recSettings.setEnablePush((int)settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_HEADER_TABLE_SIZE:
                    if (settingsValue > Integer.MAX_VALUE) {
                        System.err.println("HEADER_TABLE_SIZE > Integer.MAX_VALUE received: " + settingsValue);
                        throw new FatalConnectionException(ERROR_PROTOCOL_ERROR); // this is limited by our implementation
                    }
                    recSettings.setHeaderTableSize((int)settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_MAX_CONCURRENT_STREAMS:
                    if (settingsValue > Integer.MAX_VALUE)
                        recSettings.setMaxConcurrentStreams(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        recSettings.setMaxConcurrentStreams((int)settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_INITIAL_WINDOW_SIZE:
                    if (settingsValue > 1 << 31 - 1)
                        throw new FatalConnectionException(ERROR_FLOW_CONTROL_ERROR);
                    recSettings.setInitialWindowSize((int)settingsValue);
                    break;
                case SettingsFrame.ID_SETTINGS_MAX_HEADER_LIST_SIZE:
                    if (settingsValue > Integer.MAX_VALUE)
                        recSettings.setMaxHeaderListSize(Integer.MAX_VALUE); // this is the limit in our implementation
                    else
                        recSettings.setMaxHeaderListSize((int)settingsValue);
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
}
