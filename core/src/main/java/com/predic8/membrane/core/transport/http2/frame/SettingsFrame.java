package com.predic8.membrane.core.transport.http2.frame;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;

import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_FRAME_SIZE_ERROR;
import static com.predic8.membrane.core.transport.http2.frame.Error.ERROR_PROTOCOL_ERROR;

public class SettingsFrame {
    public static final int FLAG_ACK = 0x1;

    public static final int ID_SETTINGS_HEADER_TABLE_SIZE = 0x1;
    public static final int ID_SETTINGS_ENABLE_PUSH = 0x2;
    public static final int ID_SETTINGS_MAX_CONCURRENT_STREAMS = 0x3;
    public static final int ID_SETTINGS_INITIAL_WINDOW_SIZE = 0x04;
    public static final int ID_SETTINGS_MAX_FRAME_SIZE  = 0x05;
    public static final int ID_SETTINGS_MAX_HEADER_LIST_SIZE  = 0x06;

    private final Frame frame;

    public SettingsFrame(Frame frame) {
        this.frame = frame;
    }

    public int getSettingsCount() {
        return frame.length / 6;
    }

    public int getSettingsId(int i) {
        if (i < 0 || i >= getSettingsCount())
            throw new IllegalArgumentException();
        return
                (frame.content[i * 6] & 0xFF) * 0x100 +
                (frame.content[i * 6 + 1] & 0xFF);
    }

    /**
     * @return a value between 0 and 2^32-1
     */
    public long getSettingsValue(int i) {
        if (i < 0 || i >= getSettingsCount())
            throw new IllegalArgumentException();
        return
                (frame.content[i * 6 + 2] & 0xFF) * 0x1000000l +
                        (frame.content[i * 6 + 3] & 0xFF) * 0x10000l +
                        (frame.content[i * 6 + 4] & 0xFF) * 0x100l +
                        (frame.content[i * 6 + 5] & 0xFF);
    }
    
    public String getSettingsIdAsString(int i) {
        switch (getSettingsId(i)) {
            case ID_SETTINGS_HEADER_TABLE_SIZE: return "SETTINGS_HEADER_TABLE_SIZE";
            case ID_SETTINGS_ENABLE_PUSH: return "SETTINGS_ENABLE_PUSH";
            case ID_SETTINGS_MAX_CONCURRENT_STREAMS: return "SETTINGS_MAX_CONCURRENT_STREAMS";
            case ID_SETTINGS_INITIAL_WINDOW_SIZE: return "SETTINGS_INITIAL_WINDOW_SIZE";
            case ID_SETTINGS_MAX_FRAME_SIZE : return "SETTINGS_MAX_FRAME_SIZE ";
            case ID_SETTINGS_MAX_HEADER_LIST_SIZE : return "SETTINGS_MAX_HEADER_LIST_SIZE ";
            default: throw new NotImplementedException();
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Settings {\n");
        if (isAck())
            sb.append("  Flags: ACK");
        for (int i = 0; i < getSettingsCount(); i++) {
            sb.append("  ");
            sb.append(getSettingsIdAsString(i));
            sb.append(" = ");
            sb.append(getSettingsValue(i));
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public static Frame ack() {
        Frame frame = new Frame();
        frame.fill(Frame.TYPE_SETTINGS, FLAG_ACK, 0, null, 0, 0);
        return frame;
    }

    public static Frame empty() {
        Frame frame = new Frame();
        frame.fill(Frame.TYPE_SETTINGS, 0, 0, null, 0, 0);
        return frame;
    }

    public Frame getFrame() {
        return frame;
    }

    public boolean isAck() {
        return (frame.type & FLAG_ACK) != 0;
    }
}
