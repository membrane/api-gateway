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

package com.predic8.membrane.core.transport.http2.frame;

import com.predic8.membrane.core.transport.http2.Settings;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;

import java.util.HashMap;
import java.util.Map;

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
                (frame.content[i * 6] & 0xFF) << 8 |
                (frame.content[i * 6 + 1] & 0xFF);
    }

    /**
     * @return a value between 0 and 2^32-1
     */
    public long getSettingsValue(int i) {
        if (i < 0 || i >= getSettingsCount())
            throw new IllegalArgumentException();
        return
                (long)(frame.content[i * 6 + 2] & 0xFF) << 24 |
                        (frame.content[i * 6 + 3] & 0xFF) << 16 |
                        (frame.content[i * 6 + 4] & 0xFF) << 8 |
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
            sb.append("  Flags: ACK\n");
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

    public static Frame diff(Settings oldS, Settings newS) {
        byte[] buf = new byte[6 * 6];
        int p = 0;

        p = put(p, buf, ID_SETTINGS_HEADER_TABLE_SIZE, oldS.getHeaderTableSize(), newS.getHeaderTableSize());
        p = put(p, buf, ID_SETTINGS_ENABLE_PUSH, oldS.getEnablePush(), newS.getEnablePush());
        p = put(p, buf, ID_SETTINGS_MAX_CONCURRENT_STREAMS, oldS.getMaxConcurrentStreams(), newS.getMaxConcurrentStreams());
        p = put(p, buf, ID_SETTINGS_INITIAL_WINDOW_SIZE, oldS.getInitialWindowSize(), newS.getInitialWindowSize());
        p = put(p, buf, ID_SETTINGS_MAX_FRAME_SIZE, oldS.getMaxFrameSize(), newS.getMaxFrameSize());
        p = put(p, buf, ID_SETTINGS_MAX_HEADER_LIST_SIZE, oldS.getMaxHeaderListSize(), newS.getMaxHeaderListSize());

        Frame frame = new Frame();
        frame.fill(Frame.TYPE_SETTINGS, 0, 0, buf, 0, p);
        return frame;
    }

    private static int put(int p, byte[] buf, int key, int oldValue, int newValue) {
        if (oldValue == newValue)
            return p;

        buf[p++] = (byte)(key >> 8);
        buf[p++] = (byte)key;
        buf[p++] = (byte)(newValue >> 24);
        buf[p++] = (byte)(newValue >> 16);
        buf[p++] = (byte)(newValue >> 8);
        buf[p++] = (byte)(newValue);

        return p;
    }

    public Frame getFrame() {
        return frame;
    }

    public boolean isAck() {
        return (frame.flags & FLAG_ACK) != 0;
    }
}
