package com.predic8.membrane.core.transport.http2.frame;

public class PriorityFrame {
    private final Frame frame;

    private final boolean exclusive;
    private final int streamDependency;
    private final int weight;

    public PriorityFrame(Frame frame) {
        this.frame = frame;

        exclusive = (frame.content[0] & 0x80) != 0;
        streamDependency = (frame.content[0] & 0x7F) << 24 |
                (frame.content[1] & 0xFF) << 16 |
                (frame.content[2] & 0xFF) << 8 |
                frame.content[3] & 0xFF;
        weight = (frame.content[4] & 0xFF) + 1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Priority {\n");
        if (exclusive)
            sb.append("  exclusive,\n");
        sb.append("  weight = ");
        sb.append(weight);
        sb.append(",\n  streamDependency = ");
        sb.append(streamDependency);
        sb.append("\n");
        sb.append("}");
        return sb.toString();
    }

    public Frame getFrame() {
        return frame;
    }
}
