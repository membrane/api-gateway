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

package com.predic8.membrane.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.zip.Deflater;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class MessageUtilTest {

    @Test
    void validDeflateBodyReturnsOnlyDecompressedBytes() throws IOException {
        // Regression: Chunk.write() added HTTP chunk framing, e.g. "7\r\npayload\r\n".
        // Decoding must return only the original bytes, including for empty and multi-buffer bodies.
        for (String content : new String[]{"", "payload", "x".repeat(4096)}) {
            assertArrayEquals(content.getBytes(UTF_8),
                    MessageUtil.getDecompressedData(rawDeflate(content)));
        }
    }

    /**
     * RFC 9110 8.4.1.2 defines "deflate" as a zlib-wrapped (RFC 1950) stream; that is also what
     * Java's own {@link Deflater#Deflater(int)} produces by default. Only opting into raw DEFLATE
     * (RFC 1951, no zlib header) is the non-conformant case.
     */
    @Test
    void zlibWrappedDeflateBodyReturnsOnlyDecompressedBytes() throws IOException {
        for (String content : new String[]{"", "payload", "x".repeat(4096)}) {
            assertArrayEquals(content.getBytes(UTF_8),
                    MessageUtil.getDecompressedData(zlibDeflate(content)));
        }
    }

    @Test
    void isZlibWrappedDetectsZlibHeaderOnly() {
        assertTrue(MessageUtil.isZlibWrapped(zlibDeflate("payload")));
        assertFalse(MessageUtil.isZlibWrapped(rawDeflate("payload")));
        assertFalse(MessageUtil.isZlibWrapped(new byte[0]));
        assertFalse(MessageUtil.isZlibWrapped(new byte[]{0x78}));
    }

    /**
     * A truncated raw deflate body leaves {@link java.util.zip.Inflater} unfinished and needing
     * input. MessageUtil must reject it instead of repeatedly calling inflate() with no progress.
     */
    @Test
    void truncatedDeflateBodyDoesNotLoopForever() throws IOException {
        byte[] compressed = rawDeflate("payload");
        byte[] truncated = Arrays.copyOf(compressed, compressed.length - 1);

        assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                assertThrows(IOException.class, () -> MessageUtil.getDecompressedData(truncated)));
    }

    private static byte[] rawDeflate(String value) {
        return deflate(value, new Deflater(Deflater.DEFAULT_COMPRESSION, true));
    }

    private static byte[] zlibDeflate(String value) {
        return deflate(value, new Deflater(Deflater.DEFAULT_COMPRESSION, false));
    }

    private static byte[] deflate(String value, Deflater deflater) {
        try {
            deflater.setInput(value.getBytes(UTF_8));
            deflater.finish();
            byte[] compressed = new byte[128];
            int length = deflater.deflate(compressed);
            return Arrays.copyOf(compressed, length);
        } finally {
            deflater.end();
        }
    }
}
