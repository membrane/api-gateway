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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Request;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class McpPayloadSanitizerTest {

    private static final int PREVIEW_LENGTH = 8 * 1024;

    private final McpPayloadSanitizer sanitizer = new McpPayloadSanitizer();

    @Test
    void shortBodyIsReturnedCompletely() throws Exception {
        assertEquals("hello", sanitizer.sanitizeBody(textRequest("hello".getBytes(UTF_8))));
    }

    @Test
    void longBodyIsTruncated() throws Exception {
        final var body = "x".repeat(PREVIEW_LENGTH + 1);

        assertEquals("x".repeat(PREVIEW_LENGTH) + "... <truncated>",
                sanitizer.sanitizeBody(textRequest(body.getBytes(UTF_8))));
    }

    /**
     * MEM-38 F-049: the preview used to decode the whole body before truncating it. The gzip body is
     * cut off far behind the preview length; decoding only the preview never reaches the cut.
     */
    @Test
    void compressedBodyIsDecodedOnlyUpToPreviewLength() throws Exception {
        final var body = randomText(4 * PREVIEW_LENGTH);
        final var compressed = gzip(body);
        final var message = textRequest(Arrays.copyOf(compressed, compressed.length / 2));
        message.getHeader().add("Content-Encoding", "gzip");

        assertEquals(body.substring(0, PREVIEW_LENGTH) + "... <truncated>", sanitizer.sanitizeBody(message));
    }

    private static Message textRequest(byte[] body) throws URISyntaxException {
        return Request.post("/").body(body).header("Content-Type", "text/plain; charset=UTF-8").build();
    }

    private static byte[] gzip(String value) throws IOException {
        final var compressed = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(value.getBytes(UTF_8));
        }
        return compressed.toByteArray();
    }

    /** Text the compressor cannot shrink much, so half of the compressed body is still well past the preview. */
    private static String randomText(int length) {
        final var random = new Random(42);
        final var sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            sb.append((char) ('a' + random.nextInt(26)));
        return sb.toString();
    }
}
