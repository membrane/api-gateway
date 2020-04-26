package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.transport.http2.frame.WindowUpdateFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FlowControl {
    private static final Logger log = LoggerFactory.getLogger(FlowControl.class);

    private final int streamId;
    private final FrameSender sender;
    public long ourWindowSize;
    public long ourWindowPositionReceived;
    public long ourWindowPositionProcessed;
    public int ourWindowSizeStep;


    public FlowControl(int streamId, FrameSender sender, Settings ourSettings) {
        this.streamId = streamId;
        this.sender = sender;
        ourWindowSize = ourSettings.getInitialWindowSize();
        ourWindowSizeStep = ourSettings.getInitialWindowSize();
    }

    public void received(int length) {
        ourWindowPositionReceived += length;

        log.info("stream=" + streamId + " size=" + ourWindowSize + " pos=" + ourWindowPositionReceived + " diff=" + (ourWindowSize - ourWindowPositionReceived));
    }

    public void processed(int length) throws IOException {
        ourWindowPositionProcessed += length;
        if (ourWindowSize - ourWindowPositionProcessed < (ourWindowSizeStep >> 1))
            increaseWindow();

        log.info("stream=" + streamId + " size=" + ourWindowSize + " pos=" + ourWindowPositionReceived + " diff=" + (ourWindowSize - ourWindowPositionReceived));
    }

    private void increaseWindow() throws IOException {
        sender.send(WindowUpdateFrame.inc(streamId, ourWindowSizeStep));
        ourWindowSize += ourWindowSizeStep;
    }

}
