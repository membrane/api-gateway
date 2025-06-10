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

public class DataFrame implements StreamEnd {
    public static final int FLAG_END_STREAM = 0x1;
    public static final int FLAG_PADDED = 0x8;

    private final Frame frame;
    private final int padLengthValue; // Stores the value of the Pad Length field
    private final int payloadDataOffset; // 1 if padded, 0 otherwise

    public DataFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.1
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "DATA frame stream ID must not be 0.");
        }

        int tempPadLength = 0;
        int tempPayloadOffset = 0;

        if (isPadded()) {
            if (frame.length == 0) { // Frame payload must be at least 1 byte for Pad Length field
                throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                        "DATA frame with PADDED flag must have a payload of at least 1 byte for the Pad Length field.");
            }
            // Pad Length field is the first byte of the payload
            tempPadLength = frame.content[0] & 0xFF; // Treat as unsigned byte
            tempPayloadOffset = 1;

            // Total length of data + padding must be at least padLength
            // frame.length is: Pad Length Field (1) + Data Length + Padding Octets (tempPadLength)
            // So, frame.length - tempPayloadOffset is Data Length + Padding Octets
            if (tempPadLength > frame.length - tempPayloadOffset) {
                // This means padding is larger than the remaining payload (Data + Padding Octets)
                throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                        "Padding in DATA frame (" + tempPadLength + ") is larger than or equal to the remaining frame payload length (" + (frame.length - tempPayloadOffset) + ").");
            }
        }
        this.padLengthValue = tempPadLength;
        this.payloadDataOffset = tempPayloadOffset;
    }

    @Override
    public boolean isEndStream() {
        return (frame.flags & FLAG_END_STREAM) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public int getDataStartIndex() {
        return this.payloadDataOffset;
    }

    public int getDataLength() {
        // frame.length is total payload length (PadLenField + Data + Padding)
        // payloadDataOffset is 1 if PadLenField is present, 0 otherwise
        // padLengthValue is the value of the PadLenField (length of Padding)
        return frame.length - this.payloadDataOffset - this.padLengthValue;
    }

    public byte[] getContent() {
        return frame.getContent();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Data {\n");
        sb.append("  streamId = ");
        sb.append(frame.streamId);
        sb.append("\n  flags = ");
        if (isPadded())
            sb.append("PADDED ");
        if (isEndStream())
            sb.append("END_STREAM");
        sb.append("\n");
        sb.append("  data: \n");
        frame.appendHex(sb, frame.content, getDataStartIndex(), getDataLength(), 2);
        sb.append("}");
        return sb.toString();
    }

    public Frame getFrame() {
        return frame;
    }
}
