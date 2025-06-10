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

public class WindowUpdateFrame {
    private final Frame frame;

    public WindowUpdateFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.9
        if (frame.getLength() != 4) {
            throw new FatalConnectionException(Error.ERROR_FRAME_SIZE_ERROR, "WINDOW_UPDATE frame length must be 4.");
        }

        // getWindowSizeIncrement() is called here to trigger validation
        int increment = getWindowSizeIncrement();
        if (increment == 0) {
            // Distinguish between connection and stream error based on stream ID
            if (frame.getStreamId() == 0) {
                throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                        "WINDOW_UPDATE connection-level increment must not be 0.");
            } else {
                // Assuming FatalStreamException is available and appropriate.
                // If not, a more generic exception or specific handling might be needed.
                throw new FatalStreamException(Error.ERROR_PROTOCOL_ERROR,
                        "WINDOW_UPDATE stream-level increment must not be 0 for stream " + frame.getStreamId() + ".");
            }
        }
    }

    public int getWindowSizeIncrement() {
        return
                (frame.content[0] & 0x7F) << 24 |
                (frame.content[1] & 0xFF) << 16 |
                (frame.content[2] & 0xFF) << 8 |
                (frame.content[3] & 0xFF);
    }

    public String toString() {
        String sb = "WindowUpdate {\n" +
                "  streamId = " + frame.streamId +
                "\n  windowUpdate = " +
                getWindowSizeIncrement() +
                "\n" +
                "}";
        return sb;
    }

    public Frame getFrame() {
        return frame;
    }

    public static Frame inc(int streamId, int value) {
        Frame frame = new Frame();
        byte[] buf = new byte[4];
        buf[0] = (byte)(value >> 24 & 0x7F);
        buf[1] = (byte)(value >> 16 & 0xFF);
        buf[2] = (byte)(value >> 8 & 0xFF);
        buf[3] = (byte)(value & 0xFF);
        frame.fill(Frame.TYPE_WINDOW_UPDATE, 0, streamId, buf, 0, 4);
        return frame;
    }

}
