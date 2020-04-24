package com.predic8.membrane.core.transport.http2.frame;

import com.predic8.membrane.core.transport.http2.frame.SettingsFrame;
import com.predic8.membrane.core.util.ByteUtil;
import org.apache.commons.lang.NotImplementedException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

public class Frame {
    public static final int TYPE_DATA = 0x0;
    public static final int TYPE_HEADERS = 0x01;
    public static final int TYPE_PRIORITY = 0x02;
    public static final int TYPE_RST_STREAM = 0x03;
    public static final int TYPE_SETTINGS = 0x04;
    public static final int TYPE_PUSH_PROMISE = 0x05;
    public static final int TYPE_PING = 0x06;
    public static final int TYPE_GOAWAY = 0x07;
    public static final int TYPE_WINDOW_UPDATE = 0x08;
    public static final int TYPE_CONTINUATION = 0x09;

    byte[] buf = new byte[9];
    int length;
    int type;
    int flags;
    int streamId;
    byte[] content;

    public void read(InputStream stream) throws IOException {
        length = readByte(stream) * 0x10000 + readByte(stream) * 0x100 + readByte(stream);
        type = readByte(stream);
        flags = readByte(stream);
        streamId = (readByte(stream) & 0x7F) * 0x1000000 + readByte(stream) * 0x10000 + readByte(stream) * 0x100 + readByte(stream);
        content = ByteUtil.readByteArray(stream, length); // TODO: allocates a new byte[]
    }

    /**
     * @return 0..255
     * @throws EOFException when the EOF is reached
     */
    private int readByte(InputStream stream) throws IOException {
        int read = stream.read();
        if (read == -1)
            throw new EOFException();
        return read;
    }

    private SettingsFrame asSettings() {
        if (type != TYPE_SETTINGS)
            throw new IllegalStateException();
        return new SettingsFrame(this);
    }

    public String toString() {
        switch (type) {
            case TYPE_SETTINGS: return asSettings().toString();
            default: throw new NotImplementedException();
        }
    }
}