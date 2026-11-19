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

import com.predic8.membrane.core.http.BodyLengthCheck.TooLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.BodyLengthCheck.WITHIN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void isContentEncodingDecodedOnlyForTheCodingsThatAreDecoded() throws Exception {
        for (final var coding : new String[]{"gzip", "deflate", "br", "GZIP"})
            assertTrue(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        for (final var coding : new String[]{"unknown", "identity", "gzip, br"})
            assertFalse(requestWithContentEncoding(coding).isContentEncodingDecoded(), coding);
        assertFalse(Request.post("/").body("plain").build().isContentEncodingDecoded());
    }

    @Nested
    class CheckBodyLength {

        @Test
        void bodyWithinItsDeclaredLengthIsNotRead() throws Exception {
            final var request = Request.post("/").build();
            request.setBody(new Body(new ByteArrayInputStream("small".getBytes(UTF_8)), 5));

            assertEquals(WITHIN, request.checkBodyLength(5));
            assertFalse(request.getBody().isRead());
        }

        @Test
        void bodyOnTheWireIsAbandoned() throws Exception {
            final var request = Request.post("/").build();
            request.setBody(new Body(new ByteArrayInputStream("too long".getBytes(UTF_8))));

            assertEquals(new TooLong(true), request.checkBodyLength(5));
            assertTrue(request.getBody().hasFailed());
        }

        @Test
        void decodedBodyTooLongStaysReadable() throws Exception {
            final var gzip = gzip("a".repeat(1000));
            final var request = Request.post("/").header("Content-Encoding", "gzip").build();
            request.setBody(new Body(new ByteArrayInputStream(gzip)));

            assertEquals(new TooLong(false), request.checkBodyLength(100));
            assertArrayEquals(gzip, request.getBody().getContent());
        }

        @Test
        void compressedBodyIsKeptEncoded() throws Exception {
            final var gzip = gzip("small");
            final var request = Request.post("/").header("Content-Encoding", "gzip").build();
            request.setBody(new Body(new ByteArrayInputStream(gzip)));

            assertEquals(WITHIN, request.checkBodyLength(100));
            assertEquals("gzip", request.getHeader().getContentEncoding());
            assertArrayEquals(gzip, request.getBody().getContent());
        }

        @Test
        void emptyBodyLabelledGzipIsWithin() throws Exception {
            final var request = Request.post("/").header("Content-Encoding", "gzip").build();

            assertEquals(WITHIN, request.checkBodyLength(0));
        }
    }

    private static Message requestWithContentEncoding(String coding) throws URISyntaxException {
        return Request.post("/").body(new byte[]{1}).header("Content-Encoding", coding).build();
    }

    private static byte[] gzip(String content) throws IOException {
        final var out = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(out)) {
            gzip.write(content.getBytes(UTF_8));
        }
        return out.toByteArray();
    }
}
