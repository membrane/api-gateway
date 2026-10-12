/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.websocket;

import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketFrameAssembler;
import com.predic8.membrane.core.transport.ws.WebSocketFrameTooLargeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketFrameTest {
    static final int numberOfFrames = 100000;

    /**
     * Only the header is sent: the frame must be rejected before any payload is buffered.
     */
    @Test
    void frameAboveDefaultMaxPayloadSizeIsRejected() {
        final var header = new byte[]{(byte) 0x82, 127, 0, 0, 0, 0, 0x40, 0, 0, 0}; // binary, 1 GiB, unmasked
        final var assembler = new WebSocketFrameAssembler(new ByteArrayInputStream(header), null);
        assertThrows(WebSocketFrameTooLargeException.class, () -> assembler.readFrames(frame -> fail()));
    }

    @Test
    void frameWithPayloadAboveInitialBufferSizeCanBeCreated() throws Exception {
        final var payload = filled(10_000, (byte) 'x');
        final var bytes = new WebSocketFrame(true, false, false, false, 1, false, new byte[4], payload).toBytes();

        final var parsed = new ArrayList<byte[]>();
        new WebSocketFrameAssembler(new ByteArrayInputStream(bytes), null)
                .readFrames(frame -> parsed.add(Arrays.copyOf(frame.getPayload(), (int) frame.getPayloadLength())));
        assertArrayEquals(payload, parsed.getFirst());
    }

    @Test
    void frameAboveConfiguredMaxPayloadSizeIsRejected() throws Exception {
        final var frame = new WebSocketFrame(true, false, false, false, 2, false, new byte[4], new byte[513]).toBytes();
        final var assembler = new WebSocketFrameAssembler(new ByteArrayInputStream(frame), null, 512);
        assertThrows(WebSocketFrameTooLargeException.class, () -> assembler.readFrames(f -> fail()));
    }

    /**
     * Headers arriving in pieces must not be judged by the leftover bytes of the previous frame
     * (here 0xFF, which would read as a 65535 byte payload).
     */
    @Test
    void headersSplitAcrossReadsAreNotRejected() throws Exception {
        // 2-byte header, so its 0xFF payload sits right where the next header's extended length is read
        final var first = new WebSocketFrame(true, false, false, false, 2, false, new byte[4], filled(125, (byte) 0xFF)).toBytes();
        final var second = new WebSocketFrame(true, false, false, false, 2, false, new byte[4], filled(300, (byte) 7)).toBytes();
        final var stream = new byte[first.length + second.length];
        System.arraycopy(first, 0, stream, 0, first.length);
        System.arraycopy(second, 0, stream, first.length, second.length);

        final var payloadLengths = new ArrayList<Long>();
        new WebSocketFrameAssembler(new OneByteAtATimeInputStream(stream), null, 512)
                .readFrames(frame -> payloadLengths.add(frame.getPayloadLength()));

        assertEquals(List.of(125L, 300L), payloadLengths);
    }

    private static byte[] filled(int length, byte value) {
        final var bytes = new byte[length];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static class OneByteAtATimeInputStream extends ByteArrayInputStream {

        OneByteAtATimeInputStream(byte[] content) {
            super(content);
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) {
            return super.read(b, off, Math.min(len, 1));
        }
    }

    @Test
    void testDynamicBuffer() throws Exception {

        InputStream is = new InputStream() {
            int sentFrames = 0;
            boolean first = true;

            final byte[] frame = new WebSocketFrame(false, false, false, false, 2, true, new byte[4], new byte[8192 - 8 + 1]).toBytes();
            int counter = 0;

            @Override
            public int read() {
                int my = counter++ % frame.length;
                if(counter == frame.length)
                    counter = 0;
                if (!first && sentFrames % numberOfFrames == 0)
                    return -1;
                if (counter == 0) {
                    first = false;
                    sentFrames++;
                }
                return frame[my];

            }
        };

        final int[] counter = {0};
        WebSocketFrameAssembler wsfa = new WebSocketFrameAssembler(is, null);
        wsfa.readFrames(webSocketFrame -> {
            try {
                counter[0]++;
                assertEquals(8193, webSocketFrame.toBytes().length);
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
        });
        assertEquals(numberOfFrames, counter[0]);

    }
}
