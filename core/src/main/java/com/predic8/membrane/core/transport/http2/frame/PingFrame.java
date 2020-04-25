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
