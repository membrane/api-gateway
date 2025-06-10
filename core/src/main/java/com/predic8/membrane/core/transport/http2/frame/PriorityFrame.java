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

package com.predic8.membrane.core.transport.http2.frame;

import java.io.IOException;

public class PriorityFrame {
    private final Frame frame;

    private final boolean exclusive;
    private final int streamDependency;
    private final int weight;

    public PriorityFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.3
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "PRIORITY frame stream ID must not be 0.");
        }
        if (frame.getLength() != 5) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "PRIORITY frame length must be 5 bytes.");
        }

        exclusive = (frame.content[0] & 0x80) != 0;
        streamDependency = (frame.content[0] & 0x7F) << 24 |
                (frame.content[1] & 0xFF) << 16 |
                (frame.content[2] & 0xFF) << 8 |
                frame.content[3] & 0xFF;
        weight = (frame.content[4] & 0xFF) + 1;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Priority {\n");
        if (exclusive)
            sb.append("  exclusive,\n");
        sb.append("  weight = ");
        sb.append(weight);
        sb.append(",\n  streamDependency = ");
        sb.append(streamDependency);
        sb.append("\n");
        sb.append("}");
        return sb.toString();
    }

    public Frame getFrame() {
        return frame;
    }

    public int getWeight() {
        return weight;
    }

    public int getStreamDependency() {
        return streamDependency;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    // validateSize() is removed as checks are now in constructor.

}
