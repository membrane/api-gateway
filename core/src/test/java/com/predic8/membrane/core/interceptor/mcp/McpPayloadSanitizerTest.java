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
package com.predic8.membrane.core.interceptor.mcp;

import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

class McpPayloadSanitizerTest {

    private final McpPayloadSanitizer sanitizer = new McpPayloadSanitizer();

    @Test
    void shortBodyIsReturnedCompletely() throws Exception {
        final var body = "a".repeat(8 * 1024);
        assertEquals(body, sanitizer.sanitizeBody(Request.post("/").contentType("text/plain").body(body).build()));
    }

    @Test
    void longBodyIsTruncated() throws Exception {
        final var body = "a".repeat(8 * 1024) + "b";
        assertEquals("a".repeat(8 * 1024) + "... <truncated>",
                sanitizer.sanitizeBody(Request.post("/").contentType("text/plain").body(body).build()));
    }

    @Test
    void contentEncodingAndCharsetAreDecoded() throws Exception {
        final var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes)) {
            gzip.write("café".getBytes(ISO_8859_1));
        }
        final var msg = Request.post("/").contentType("text/plain; charset=ISO-8859-1").body(bytes.toByteArray()).build();
        msg.getHeader().setValue("Content-Encoding", "gzip");

        assertEquals("café", sanitizer.sanitizeBody(msg));
    }

    @Test
    void multiByteCharactersAtTheLimitAreNotSplit() throws Exception {
        final var body = "ä".repeat(8 * 1024 + 1);
        final var msg = Request.post("/").contentType("text/plain; charset=UTF-8").body(body.getBytes(UTF_8)).build();
        assertEquals("ä".repeat(8 * 1024) + "... <truncated>", sanitizer.sanitizeBody(msg));
    }
}
