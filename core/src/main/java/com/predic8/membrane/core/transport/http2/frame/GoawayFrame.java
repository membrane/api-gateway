package com.predic8.membrane.core.transport.http2.frame;

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
