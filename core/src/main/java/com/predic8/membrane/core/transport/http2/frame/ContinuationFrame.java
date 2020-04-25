package com.predic8.membrane.core.transport.http2.frame;

public class ContinuationFrame implements HeaderBlockFragment {
    public static final int FLAG_END_HEADERS = 0x4;

    private final Frame frame;

    public ContinuationFrame(Frame frame) {
        this.frame = frame;
    }

    public Frame getFrame() {
        return frame;
    }

    @Override
    public byte[] getContent() {
        return frame.getContent();
    }

    @Override
    public int getHeaderBlockStartIndex() {
        return 0;
    }

    @Override
    public int getHeaderBlockLength() {
        return frame.length;
    }

    @Override
    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }
}
