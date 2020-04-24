package com.predic8.membrane.core.transport.http2.frame;

import org.apache.commons.lang.NotImplementedException;

public class SettingsFrame {
    public final int ID_SETTINGS_HEADER_TABLE_SIZE = 0x1;
    public final int ID_SETTINGS_ENABLE_PUSH = 0x2;
    public final int ID_SETTINGS_MAX_CONCURRENT_STREAMS = 0x3;
    public final int ID_SETTINGS_INITIAL_WINDOW_SIZE = 0x04;
    public final int ID_SETTINGS_MAX_FRAME_SIZE  = 0x05;
    public final int ID_SETTINGS_MAX_HEADER_LIST_SIZE  = 0x06;

    private final Frame frame;

    public SettingsFrame(Frame frame) {
        this.frame = frame;
        if (frame.length % 6 != 0)
            throw new RuntimeException("Expected SETTINGS frame to have content length divisible by 6.");
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

}
