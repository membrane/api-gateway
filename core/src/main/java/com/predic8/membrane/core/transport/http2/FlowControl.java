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

import com.predic8.membrane.core.transport.http2.frame.WindowUpdateFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.GuardedBy;
import java.io.IOException;

public class FlowControl {
    private static final Logger log = LoggerFactory.getLogger(FlowControl.class);

    private final int streamId;
    private final FrameSender sender;
    @GuardedBy("this")
    public long ourWindowSize;
    @GuardedBy("this")
    public long ourWindowPositionReceived;
    @GuardedBy("this")
    public long ourWindowPositionProcessed;
    @GuardedBy("this")
    public int ourWindowSizeStep;


    public FlowControl(int streamId, FrameSender sender, Settings ourSettings) {
        this.streamId = streamId;
        this.sender = sender;
        ourWindowSize = ourSettings.getInitialWindowSize();
        ourWindowSizeStep = ourSettings.getInitialWindowSize();
    }

    /**
     * called on the receiver thread.
     */
    public synchronized void received(int length) {
        ourWindowPositionReceived += length;

        if (log.isDebugEnabled())
            log.debug("stream=" + streamId + " size=" + ourWindowSize + " pos=" + ourWindowPositionReceived + " diff=" + (ourWindowSize - ourWindowPositionReceived));
    }

    /**
     * called on the receiver thread or on the processing thread.
     */
    public void processed(int length) throws IOException {
        int windowIncrease = 0;
        synchronized (this) {
            ourWindowPositionProcessed += length;
            if (ourWindowSize - ourWindowPositionProcessed < (ourWindowSizeStep >> 1))
                windowIncrease = increaseWindow();

            if (log.isDebugEnabled())
                log.debug("stream=" + streamId + " size=" + ourWindowSize + " pos=" + ourWindowPositionReceived + " diff=" + (ourWindowSize - ourWindowPositionReceived));
        }

        if (windowIncrease != 0)
            sender.send(WindowUpdateFrame.inc(streamId, windowIncrease));
    }

    private int increaseWindow() throws IOException {
        ourWindowSize += ourWindowSizeStep;
        return ourWindowSizeStep;
    }

}
