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

import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_PING;

public class PingFrame {
    public static final int FLAG_ACK = 0x1;

    private final Frame frame;

    public PingFrame(Frame frame) {
        this.frame = frame;
    }

    public Frame getFrame() {
        return frame;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ping {\n");
        if (isAck())
            sb.append("  Flags: ACK\n");
        sb.append("  data: \n");
        frame.appendHex(sb, frame.content, 0, frame.length, 2);
        sb.append("}");
        return sb.toString();
    }

    public boolean isAck() {
        return (frame.flags & FLAG_ACK) != 0;
    }


    public static Frame pong(PingFrame ping) {
        Frame frame = new Frame();
        frame.fill(TYPE_PING, FLAG_ACK, 0, ping.getFrame().getContent(), 0, ping.getFrame().getLength());
        return frame;
    }
}
