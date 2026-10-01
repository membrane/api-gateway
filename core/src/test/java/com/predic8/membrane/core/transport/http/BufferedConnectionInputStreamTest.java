/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BufferedConnectionInputStreamTest {

    @Test
    void nothingBufferedBeforeFirstRead() {
        assertEquals(0, stream("abcdef", 4).buffered());
    }

    @Test
    void bytesLeftInBufferAfterRead() throws IOException {
        var in = stream("abcdef", 4);
        in.read();
        assertEquals(3, in.buffered());
        in.read(new byte[2]);
        assertEquals(1, in.buffered());
    }

    @Test
    void emptyBufferAfterReadingAllBytesOfAFill() throws IOException {
        var in = stream("abcdef", 4);
        in.readNBytes(4);
        assertEquals(0, in.buffered());
    }

    @Test
    void resetMakesMarkedBytesBufferedAgain() throws IOException {
        var in = stream("abcdef", 4);
        in.mark(4);
        in.readNBytes(3);
        in.reset();
        assertEquals(4, in.buffered());
    }

    /**
     * available() adds the estimate of the underlying stream, buffered() does not.
     */
    @Test
    void ignoresAvailableOfUnderlyingStream() throws IOException {
        var in = new BufferedConnectionInputStream(new ByteArrayInputStream("abcdef".getBytes()) {
            @Override
            public synchronized int available() {
                return 1_000;
            }
        }, 4);
        in.read();
        assertEquals(3, in.buffered());
        assertEquals(1_003, in.available());
    }

    private static BufferedConnectionInputStream stream(String content, int size) {
        InputStream in = new ByteArrayInputStream(content.getBytes());
        return new BufferedConnectionInputStream(in, size);
    }
}
