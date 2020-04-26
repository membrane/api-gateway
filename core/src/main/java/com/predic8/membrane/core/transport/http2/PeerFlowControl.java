package com.predic8.membrane.core.transport.http2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerFlowControl {
    private static final Logger log = LoggerFactory.getLogger(PeerFlowControl.class);

    private final int streamId;
    public long peerWindowSize;
    public long peerWindowPosition;

    public PeerFlowControl(int streamId, FrameSender sender, Settings peerSettings) {
        this.streamId = streamId;
        peerWindowSize = peerSettings.getInitialWindowSize();
    }

    public void increment(int delta) {
        peerWindowSize += delta;
        log.info("stream=" + streamId + " size=" + peerWindowSize + " pos=" + peerWindowPosition + " diff=" + (peerWindowSize - peerWindowPosition));
    }

    public void used(int length) {
        peerWindowPosition += length;
        log.info("stream=" + streamId + " size=" + peerWindowSize + " pos=" + peerWindowPosition + " diff=" + (peerWindowSize - peerWindowPosition));
    }
}
