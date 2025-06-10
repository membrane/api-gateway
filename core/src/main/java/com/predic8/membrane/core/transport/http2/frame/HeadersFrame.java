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

public class HeadersFrame implements HeaderBlockFragment, StreamEnd {
    public static final int FLAG_END_STREAM = 0x1;
    public static final int FLAG_END_HEADERS = 0x4;
    public static final int FLAG_PADDED = 0x8;
    public static final int FLAG_PRIORITY = 0x20;

    private final Frame frame;

    private final int padLength;
    private final boolean exclusive;
    private final int streamDependency;
    private final int weight;
    private final int headerBlockStartIndex;
    
    public HeadersFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.2
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "HEADERS frame stream ID must not be 0.");
        }

        int currentOffset = 0;
        int declaredPadLength = 0;

        if (isPadded()) {
            if (frame.length < 1) {
                throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "HEADERS frame with PADDED flag must have payload of at least 1 byte for Pad Length field.");
            }
            declaredPadLength = frame.content[currentOffset++] & 0xFF;
        }

        boolean prioExclusive = false;
        int prioStreamDependency = 0;
        int prioWeight = 16; // Default weight if not present, but PRIORITY flag means it should be.

        if (isPriority()) {
            if (frame.length - currentOffset < 5) {
                throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "HEADERS frame with PRIORITY flag needs at least 5 bytes for priority fields after Pad Length.");
            }
            prioExclusive = (frame.content[currentOffset] & 0x80) != 0;
            prioStreamDependency = (frame.content[currentOffset] & 0x7F) << 24 |
                                   (frame.content[currentOffset + 1] & 0xFF) << 16 |
                                   (frame.content[currentOffset + 2] & 0xFF) << 8 |
                                   (frame.content[currentOffset + 3] & 0xFF);
            currentOffset += 4; // For Exclusive flag byte and 3 more for stream dependency
            prioWeight = (frame.content[currentOffset++] & 0xFF) + 1;

            if (prioStreamDependency == frame.getStreamId()) {
                throw new FatalStreamException(Error.ERROR_PROTOCOL_ERROR, "HEADERS frame PRIORITY stream dependency cannot be self (stream " + frame.getStreamId() + ").");
            }
        }

        // Validate Pad Length value against remaining frame length
        if (isPadded()) {
            // frame.length - currentOffset is the length of (Header Block Fragment + Padding Octets)
            if (declaredPadLength > frame.length - currentOffset) {
                throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                        "Padding in HEADERS frame (" + declaredPadLength + ") is larger than or equal to the remaining frame payload length for HBF and padding (" + (frame.length - currentOffset) + ").");
            }
        }

        this.padLength = declaredPadLength; // This is the Pad Length Field Value
        this.exclusive = prioExclusive;
        this.streamDependency = prioStreamDependency;
        this.weight = isPriority() ? prioWeight : 0; // Weight is only meaningful if PRIORITY flag is set
        this.headerBlockStartIndex = currentOffset;

        // Final check for header block length integrity
        if (getHeaderBlockLength() < 0) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "Calculated header block length for HEADERS frame is negative, indicating inconsistent padding or length.");
        }
    }

    public boolean isEndStream() {
        return (frame.flags & FLAG_END_STREAM) != 0;
    }

    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public boolean isPriority() {
        return (frame.flags & FLAG_PRIORITY) != 0;
    }

    public int getHeaderBlockStartIndex() {
        return headerBlockStartIndex;
    }

    public int getHeaderBlockLength() {
        return frame.length - padLength - headerBlockStartIndex;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Headers {\n");
        sb.append("  streamId = ");
        sb.append(frame.streamId);
        sb.append("\n  flags = ");
        if (isEndHeaders())
            sb.append("END_HEADERS ");
        if (isEndStream())
            sb.append("END_STREAM");
        sb.append("\n");
        if (isPriority()) {
            sb.append("  priority: ");
            if (exclusive)
                sb.append("exclusive, ");
            sb.append("weight = ");
            sb.append(weight);
            sb.append(", streamDependency = ");
            sb.append(streamDependency);
            sb.append("\n");
        }
        sb.append("  header block data: \n");
        frame.appendHex(sb, frame.content, getHeaderBlockStartIndex(), getHeaderBlockLength(), 2);
        sb.append("}");
        return sb.toString();
    }


    public byte[] getContent() {
        return frame.getContent();
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
}
