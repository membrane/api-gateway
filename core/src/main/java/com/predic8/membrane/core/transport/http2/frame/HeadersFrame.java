package com.predic8.membrane.core.transport.http2.frame;

public class HeadersFrame {
    private static final int FLAG_END_STREAM = 0x1;
    private static final int FLAG_END_HEADERS = 0x4;
    private static final int FLAG_PADDED = 0x8;
    private static final int FLAG_PRIORITY = 0x20;

    private final Frame frame;

    private final int padLength;
    private final boolean exclusive;
    private final int streamDependency;
    private final int weight;
    private final int headerBlockStartIndex;
    
    public HeadersFrame(Frame frame) {
        this.frame = frame;

        int p = 0;

        if (isPadded()) {
            padLength = frame.content[p++];
        } else {
            padLength = 0;
        }
        if (isPriority()) {
            exclusive = (frame.content[p] & 0x80) != 0;
            streamDependency = (frame.content[p++] & 0x7F) * 0x1000000 +
                    (frame.content[p++] & 0xFF) * 0x10000 +
                    (frame.content[p++] & 0xFF) * 0x100 +
                    (frame.content[p++] & 0xFF);
            weight = (frame.content[p++] & 0xFF) + 1;
        } else {
            exclusive = false;
            streamDependency = 0;
            weight = 0;
        }

        headerBlockStartIndex = p;
    }

    public boolean isEndStream() {
        return (frame.flags & FLAG_END_STREAM) != 0;
    }

    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public boolean isPriority() {
        return (frame.flags & FLAG_PRIORITY) != 0;
    }

    public int getHeaderBlockStartIndex() {
        return headerBlockStartIndex;
    }

    public int getHeaderBlockLength() {
        return frame.length - padLength - headerBlockStartIndex;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Headers {\n");
        sb.append("  flags = ");
        if (isEndHeaders())
            sb.append("END_HEADERS ");
        if (isEndStream())
            sb.append("END_STREAM");
        sb.append("\n");
        if (isPriority()) {
            sb.append("  priority: ");
            if (exclusive)
                sb.append("exclusive, ");
            sb.append("weight = ");
            sb.append(weight);
            sb.append(", streamDependency = ");
            sb.append(streamDependency);
            sb.append("\n");
        }
        sb.append("  header block data: \n");
        frame.appendHex(sb, frame.content, getHeaderBlockStartIndex(), getHeaderBlockLength(), 2);
        sb.append("}");
        return sb.toString();
    }


}
