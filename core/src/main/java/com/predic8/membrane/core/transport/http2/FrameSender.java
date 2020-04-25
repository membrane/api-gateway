package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;

import java.io.IOException;
import java.io.OutputStream;

public class FrameSender {

    private final OutputStream out;

    public FrameSender(OutputStream out) {
        this.out = out;
    }

    public void send(Frame frame) throws IOException {
        frame.write(out);
        // TODO
    }
}
