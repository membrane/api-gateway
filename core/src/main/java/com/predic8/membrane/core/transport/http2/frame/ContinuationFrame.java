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

public class ContinuationFrame implements HeaderBlockFragment {
    public static final int FLAG_END_HEADERS = 0x4;

    private final Frame frame;

    public ContinuationFrame(Frame frame) {
        this.frame = frame;

        // RFC 7540, Section 6.10
        if (frame.getStreamId() == 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR, "CONTINUATION frame stream ID must not be 0.");
        }

        // Check for undefined flags
        if ((frame.getFlags() & ~FLAG_END_HEADERS) != 0) {
            throw new FatalConnectionException(Error.ERROR_PROTOCOL_ERROR,
                    "CONTINUATION frame received with invalid flags (other than END_HEADERS). Flags: " + frame.getFlags());
        }
    }

    public Frame getFrame() {
        return frame;
    }

    @Override
    public byte[] getContent() {
        return frame.getContent();
    }

    @Override
    public int getHeaderBlockStartIndex() {
        return 0;
    }

    @Override
    public int getHeaderBlockLength() {
        return frame.length;
    }

    @Override
    public boolean isEndHeaders() {
        return (frame.flags & FLAG_END_HEADERS) != 0;
    }
}
