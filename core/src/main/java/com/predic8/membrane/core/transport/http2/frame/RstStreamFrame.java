package com.predic8.membrane.core.transport.http2.frame;

public class RstStreamFrame {
    private final Frame frame;
    private final int errorCode;

    public RstStreamFrame(Frame frame) {
        this.frame = frame;
        errorCode = ((frame.content[0] & 0xFF) << 24) |
                ((frame.content[1] & 0xFF) << 16) |
                ((frame.content[2] & 0xFF) << 8 ) |
                ((frame.content[3] & 0xFF));
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("RstStream {\n");
        sb.append("\n  errorCode = ");
        sb.append(errorCode);
        sb.append("}");
        return sb.toString();
    }

}
