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

    public synchronized void increment(int delta) {
        peerWindowSize += delta;
        if (log.isDebugEnabled())
            log.debug("stream=" + streamId + " size=" + peerWindowSize + " pos=" + peerWindowPosition + " diff=" + (peerWindowSize - peerWindowPosition));
        notifyAll();
    }

    private void used(int length) {
        peerWindowPosition += length;
        if (log.isDebugEnabled())
            log.debug("stream=" + streamId + " size=" + peerWindowSize + " pos=" + peerWindowPosition + " diff=" + (peerWindowSize - peerWindowPosition));
    }

    private boolean canUse(int length) {
        return peerWindowSize - peerWindowPosition >= length;
    }

    public synchronized void reserve(int wantLength, int streamId) {
        boolean warned = false;
        while(!canUse(wantLength)) {
            if (!warned) {
                log.warn("stream " + streamId + " blocked because of flow control on stream " + this.streamId + ".");
                warned = true;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        used(wantLength);
    }

}
