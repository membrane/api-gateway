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

public class PushPromiseFrame implements HeaderBlockFragment {
    public static final int FLAG_END_HEADERS = 0x4;
    public static final int FLAG_PADDED = 0x8;

    private final Frame frame;

    private final int padLength;
    private final int promisedStreamId;
    private final int headerBlockStartIndex;

    public PushPromiseFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.6
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "PUSH_PROMISE frame stream ID must not be 0.");
        }

        int currentOffset = 0;
        int declaredPadLength = 0;

        if (isPadded()) {
            if (frame.length < 1) { // Must have at least 1 byte for Pad Length field
                throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR,
                        "PUSH_PROMISE frame with PADDED flag must have a payload of at least 1 byte for the Pad Length field.");
            }
            declaredPadLength = frame.content[currentOffset++] & 0xFF;
        }

        // Promised Stream ID is 4 bytes
        if (frame.length - currentOffset < 4) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR,
                    "PUSH_PROMISE frame payload is too short for the 4-byte Promised Stream ID (currentOffset=" + currentOffset + ", frame.length=" + frame.length + ").");
        }
        // Correctly parse Promised Stream ID, ensuring MSB is cleared
        int parsedPromisedStreamId = (frame.content[currentOffset] & 0x7F) << 24 |
                                     (frame.content[currentOffset + 1] & 0xFF) << 16 |
                                     (frame.content[currentOffset + 2] & 0xFF) << 8 |
                                     (frame.content[currentOffset + 3] & 0xFF);
        currentOffset += 4;

        // Validate Pad Length value against remaining frame length
        if (isPadded()) {
            // frame.length - currentOffset is the length of (Header Block Fragment + Padding Octets)
            if (declaredPadLength > frame.length - currentOffset) {
                throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                        "Padding in PUSH_PROMISE frame (" + declaredPadLength + ") is larger than or equal to the remaining frame payload length for HBF and padding (" + (frame.length - currentOffset) + ").");
            }
        }

        this.padLength = declaredPadLength; // This is the Pad Length Field Value
        this.promisedStreamId = parsedPromisedStreamId;
        this.headerBlockStartIndex = currentOffset;

        // Final check for header block length integrity
        if (getHeaderBlockLength() < 0) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "Calculated header block length for PUSH_PROMISE frame is negative, indicating inconsistent padding or length.");
        }
    }

    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public int getHeaderBlockStartIndex() {
        return headerBlockStartIndex;
    }

    public int getHeaderBlockLength() {
        return frame.length - padLength - headerBlockStartIndex;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PushPromise {\n");
        sb.append("  streamId = ");
        sb.append(frame.streamId);
        sb.append("\n  flags = ");
        if (isEndHeaders())
            sb.append("END_HEADERS ");
        sb.append("\n");
        sb.append("  promisedStreamId = ");
        sb.append(promisedStreamId);
        sb.append("\n");
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
}
