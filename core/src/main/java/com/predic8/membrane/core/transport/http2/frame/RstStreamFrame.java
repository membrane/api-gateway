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

import static com.predic8.membrane.core.transport.http2.frame.Frame.TYPE_RST_STREAM;

public class RstStreamFrame {
    private final Frame frame;
    private final int errorCode;

    public RstStreamFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.4
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "RST_STREAM frame stream ID must not be 0.");
        }
        if (frame.getLength() != 4) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "RST_STREAM frame length must be 4 bytes.");
        }

        errorCode = ((frame.content[0] & 0xFF) << 24) |
                ((frame.content[1] & 0xFF) << 16) |
                ((frame.content[2] & 0xFF) << 8 ) |
                ((frame.content[3] & 0xFF));
    }

    public static Frame construct(int streamId, int errorCode) {
        byte[] buf = new byte[4];

        buf[0] = (byte) (errorCode >> 24); // Error code is a 32-bit field
        buf[1] = (byte) (errorCode >> 16);
        buf[2] = (byte) (errorCode >> 8);
        buf[3] = (byte) (errorCode);

        Frame frame = new Frame();
        frame.fill(TYPE_RST_STREAM,
                0,
                streamId,
                buf,
                0,
                4);
        return frame;
    }

    public String toString() {
        String sb = "RstStream {\n" +
                "\n  errorCode = " +
                errorCode +
                "}";
        return sb;
    }

    public Frame getFrame() {
        return frame;
    }

    // validateSize() is removed as checks are now in constructor.
}
