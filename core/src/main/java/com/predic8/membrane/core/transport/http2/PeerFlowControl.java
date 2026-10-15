/* Copyright 2020 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;
import java.io.InterruptedIOException;

public class PeerFlowControl {
    private static final Logger log = LoggerFactory.getLogger(PeerFlowControl.class);

    private final int streamId;
    private final FrameSender sender;

    @GuardedBy("this")
    public long peerWindowSize;
    @GuardedBy("this")
    public long peerWindowPosition;

    public PeerFlowControl(int streamId, FrameSender sender, Settings peerSettings) {
        this.streamId = streamId;
        this.sender = sender;
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

    /**
     * @throws IOException if the frame sender stops while waiting, as the peer will not grant more window then
     */
    public synchronized void reserve(int wantLength, int streamId) throws IOException {
        boolean warned = false;
        while(!canUse(wantLength)) {
            if (sender.isStopped())
                throw new IOException("HTTP/2 frame sender stopped while stream %d waited for flow-control window".formatted(streamId));
            if (!warned) {
                log.warn("stream " + streamId + " blocked because of flow control on stream " + this.streamId + ".");
                warned = true;
            }
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while stream %d waited for flow-control window".formatted(streamId));
            }
        }

        used(wantLength);
    }

    /** Wakes writers waiting in {@link #reserve(int, int)}, so they notice that the frame sender stopped. */
    synchronized void senderStopped() {
        notifyAll();
    }

}
