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
    private final int padLength;

    public DataFrame(Frame frame) {
        this.frame = frame;

        int p = 0;

        if (isPadded()) {
            padLength = frame.content[p++];
        } else {
            padLength = 0;
        }

    }

    @Override
    public boolean isEndStream() {
        return (frame.flags & FLAG_END_STREAM) != 0;
    }

    public boolean isPadded() {
        return (frame.flags & FLAG_PADDED) != 0;
    }

    public int getDataStartIndex() {
        return 0;
    }

    public int getDataLength() {
        return frame.length - padLength;
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
