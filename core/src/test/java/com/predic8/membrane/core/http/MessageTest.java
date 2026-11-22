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
package com.predic8.membrane.core.http;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static com.predic8.membrane.core.util.ZstdTestUtil.withZstdDecoding;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void isContentEncodingDecodedOnlyForTheCodingsThatAreDecoded() throws Exception {
        for (final var coding : new String[]{"gzip", "deflate", "br", "GZIP"})
            assertTrue(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        for (final var coding : new String[]{"unknown", "identity", "gzip, br", "zstd"})
            assertFalse(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        assertFalse(Request.post("/").body("plain").build().isContentEncodingDecoded());
    }

    @Test
    void zstdIsDecodedOnlyWhenSwitchedOn() throws Throwable {
        assertFalse(requestWithContentEncoding("zstd").isContentEncodingDecoded());
        withZstdDecoding(() -> {
            for (final var coding : new String[]{"zstd", "ZSTD"})
                assertTrue(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        });
    }

    /**
     * RFC 9110 5.3: "Content-Encoding: gzip" plus "Content-Encoding: br" means "gzip, br", which
     * Membrane does not decode. Empty list elements do not count (RFC 9110 5.6.1).
     */
    @Test
    void codingsAreReadFromAllFieldLines() throws Exception {
        assertFalse(requestWithContentEncoding("gzip", "br").isGzip());
        assertFalse(requestWithContentEncoding("gzip", "br").isContentEncodingDecoded());
        assertFalse(requestWithContentEncoding("br", "gzip").isContentEncodingDecoded());
        assertFalse(requestWithContentEncoding("deflate", "deflate").isDeflate());
        assertTrue(requestWithContentEncoding(", gzip,").isGzip());
        assertTrue(requestWithContentEncoding("", "br").isBrotli());
        assertTrue(requestWithContentEncoding("", "deflate", ",").isDeflate());
        assertTrue(requestWithContentEncoding(", zstd,").isZstd());
        assertFalse(requestWithContentEncoding("gzip, zstd").isZstd());
        assertFalse(requestWithContentEncoding("gzip", "zstd").isZstd());
        assertFalse(requestWithContentEncoding("zstd", "gzip").isContentEncodingDecoded());
    }

    @Test
    void isEncodedOnlyWithACoding() throws Exception {
        assertTrue(requestWithContentEncoding("", "unknown").isEncoded());
        assertFalse(requestWithContentEncoding(",", "").isEncoded());
    }

    private static Message requestWithContentEncoding(String... fieldLines) throws URISyntaxException {
        final var builder = Request.post("/").body(new byte[]{1});
        for (final var fieldLine : fieldLines)
            builder.header("Content-Encoding", fieldLine);
        return builder.build();
    }
}
