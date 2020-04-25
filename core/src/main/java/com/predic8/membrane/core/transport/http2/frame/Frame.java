package com.predic8.membrane.core.transport.http2.frame;

import com.predic8.membrane.core.transport.http2.Settings;
import com.predic8.membrane.core.util.ByteUtil;
import org.apache.commons.lang.NotImplementedException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    final int maximumFrameSize;

    int length;
    int type;
    int flags;
    int streamId;
    byte[] content;

    public Frame(Settings receivingSettings) {
        maximumFrameSize = receivingSettings.getMaxFrameSize();
    }

    public Frame() {
        maximumFrameSize = -1;
    }

    public void fill(int type, int flags, int streamId, byte[] buf, int offset, int length) {
        this.length = length;
        this.type = type;
        this.flags = flags;
        this.streamId = streamId;
        if (length == 0) {
            content = null;
        } else {
            content = new byte[length]; // TODO: this allocates memory
            System.arraycopy(buf, offset, content, 0, length); // TODO: this is bad performance
        }
    }

    public void write(OutputStream stream) throws IOException {
        stream.write(length >> 16);
        stream.write(length >> 8);
        stream.write(length);
        stream.write(type);
        stream.write(flags);
        stream.write((streamId >> 24) & 0x7F);
        stream.write(streamId >> 16);
        stream.write(streamId >> 8);
        stream.write(streamId);
        if (length > 0) {
            stream.write(content, 0, length);
        }
    }

    public void read(InputStream stream) throws IOException {
        length = readByte(stream) * 0x10000 + readByte(stream) * 0x100 + readByte(stream);

        if (length > maximumFrameSize)
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR);

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

    public SettingsFrame asSettings() {
        if (type != TYPE_SETTINGS)
            throw new IllegalStateException();
        return new SettingsFrame(this);
    }

    public WindowUpdateFrame asWindowUpdate() {
        if (type != TYPE_WINDOW_UPDATE)
            throw new IllegalStateException();
        return new WindowUpdateFrame(this);
    }

    public HeadersFrame asHeaders() {
        if (type != TYPE_HEADERS)
            throw new IllegalStateException();
        return new HeadersFrame(this);
    }

    public ContinuationFrame asContinuation() {
        if (type != TYPE_CONTINUATION)
            throw new IllegalStateException();
        return new ContinuationFrame(this);
    }

    public DataFrame asData() {
        if (type != TYPE_DATA)
            throw new IllegalStateException();
        return new DataFrame(this);
    }

    public String toString() {
        switch (type) {
            case TYPE_SETTINGS: return asSettings().toString();
            case TYPE_WINDOW_UPDATE: return asWindowUpdate().toString();
            case TYPE_HEADERS: return asHeaders().toString();
            case TYPE_DATA: return asData().toString();
            default: throw new NotImplementedException("type = " + type);
        }
    }

    public void appendHex(StringBuilder sb, byte[] buffer, int offset, int length, int indent) {
        for (int i = 0; i < length; i+=16) {
            for (int j = 0; j < indent; j++)
                sb.append(" ");
            sb.append(String.format("%04X", i));
            sb.append(": ");
            for (int j = 0; j < 16; j++) {
                if (i + j >= length)
                    sb.append("  ");
                else
                    sb.append(String.format("%02X", buffer[offset + i + j]));
                if (j == 7)
                    sb.append("   ");
                else
                    sb.append(" ");
            }
            sb.append("  ");
            for (int j = 0; j < 16 && i + j < length; j++)
                if (buffer[offset + i + j] < 32)
                    sb.append(".");
                else
                    sb.append((char) buffer[offset + i + j]);
            sb.append("\n");
        }
    }

    public int getType() {
        return type;
    }

    public byte[] getContent() {
        return content;
    }

    public int getStreamId() {
        return streamId;
    }

    public int getLength() {
        return length;
    }
}