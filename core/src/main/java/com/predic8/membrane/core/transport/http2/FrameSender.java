package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.Frame;
import com.predic8.membrane.core.util.functionalInterfaces.Function;
import com.twitter.hpack.Encoder;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

public class FrameSender {

    private final OutputStream out;
    private final Encoder encoder;
    private final Settings sendSettings;

    public FrameSender(OutputStream out, Encoder encoder, Settings sendSettings) {
        this.out = out;
        this.encoder = encoder;
        this.sendSettings = sendSettings;
    }

    public synchronized void send(Frame frame) throws IOException {
        System.out.println(frame);
        frame.write(out);
        out.flush();
        // TODO
    }

    public synchronized void send(FrameProducer frameProducer) throws IOException {
        for (Frame frame : frameProducer.call(encoder, sendSettings)) {
            System.out.println(frame);
            frame.write(out);
            out.flush();
        }
        // TODO
    }
}
