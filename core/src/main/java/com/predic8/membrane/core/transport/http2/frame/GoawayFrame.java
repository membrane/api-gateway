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

import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_GOAWAY;

public class GoawayFrame {
    private final Frame frame;

    private final int lastStreamId;
    private final int errorCode;

    public GoawayFrame(Frame frame) {
        this.frame = frame;

        lastStreamId = (frame.content[0] & 0x7F) << 24 |
                (frame.content[1] & 0xFF) << 16 |
                (frame.content[2] & 0xFF) << 8 |
                (frame.content[3] & 0xFF);

        errorCode = (frame.content[4] & 0xFF) << 24 |
                (frame.content[5] & 0xFF) << 16 |
                (frame.content[6] & 0xFF) << 8 |
                (frame.content[7] & 0xFF);
    }

    public static Frame construct(int streamId, int lastStreamId, int errorCode) {
        byte[] buf = new byte[8];

        buf[0] = (byte) ((lastStreamId >> 24) & 0x7F);
        buf[1] = (byte) (lastStreamId >> 16);
        buf[2] = (byte) (lastStreamId >> 8);
        buf[3] = (byte) (lastStreamId);

        buf[4] = (byte) ((errorCode >> 24) & 0x7F);
        buf[5] = (byte) (errorCode >> 16);
        buf[6] = (byte) (errorCode >> 8);
        buf[7] = (byte) (errorCode);

        Frame frame = new Frame();
        frame.fill(TYPE_GOAWAY,
                0,
                streamId,
                buf,
                0,
                8);
        return frame;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Goaway {\n");
        sb.append("  lastStreamId = ");
        sb.append(lastStreamId);
        sb.append("\n  errorCode = ");
        sb.append(errorCode);
        sb.append("\n  additional debug data:\n");
        frame.appendHex(sb, frame.content, 8, frame.length - 8, 2);
        sb.append("}");
        return sb.toString();
    }

    public Frame getFrame() {
        return frame;
    }
}
