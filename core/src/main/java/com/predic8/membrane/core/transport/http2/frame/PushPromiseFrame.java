package com.predic8.membrane.core.transport.http2.frame;

public class PushPromiseFrame implements HeaderBlockFragment {
    public static final int FLAG_END_HEADERS = 0x4;
    public static final int FLAG_PADDED = 0x8;

    private final Frame frame;

    private final int padLength;
    private final int promisedStreamId;
    private final int headerBlockStartIndex;

    public PushPromiseFrame(Frame frame) {
        this.frame = frame;

        int p = 0;

        if (isPadded()) {
            padLength = frame.content[p++];
        } else {
            padLength = 0;
        }

        promisedStreamId = (frame.content[p++] & 0x7F) << 24 |
                (frame.content[p++] & 0xFF) << 16 |
                (frame.content[p++] & 0xFF) << 8 |
                (frame.content[p++] & 0xFF);

        headerBlockStartIndex = p;
    }

    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public int getHeaderBlockStartIndex() {
        return headerBlockStartIndex;
    }

    public int getHeaderBlockLength() {
        return frame.length - padLength - headerBlockStartIndex;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PushPromise {\n");
        sb.append("  streamId = ");
        sb.append(frame.streamId);
        sb.append("\n  flags = ");
        if (isEndHeaders())
            sb.append("END_HEADERS ");
        sb.append("\n");
        sb.append("  promisedStreamId = ");
        sb.append(promisedStreamId);
        sb.append("\n");
        sb.append("  header block data: \n");
        frame.appendHex(sb, frame.content, getHeaderBlockStartIndex(), getHeaderBlockLength(), 2);
        sb.append("}");
        return sb.toString();
    }


    public byte[] getContent() {
        return frame.getContent();
    }

    public Frame getFrame() {
        return frame;
    }
}
