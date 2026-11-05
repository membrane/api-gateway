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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static org.junit.jupiter.api.Assertions.*;

class MessageTextTest {

    @ParameterizedTest
    @CsvSource({
            "application/json, json",
            "application/problem+json; charset=utf-8, json",
            "text/xml, xml",
            "application/soap+xml, xml",
            "application/yaml, yaml",
            "application/x-www-form-urlencoded, form",
            "text/plain, text",
            "image/png, binary",
            "application/octet-stream, binary",
    })
    void detectsFormat(String contentType, String format) {
        assertEquals(format, MessageText.format(contentType));
    }

    @Test
    void missingContentTypeIsText() {
        assertEquals("text", MessageText.format(null));
    }

    @Test
    void prettyPrintsXml() throws Exception {
        final var msg = Request.post("/").contentType("text/xml").body("<a><b>1</b></a>").build();
        final var body = MessageText.formatted(msg, "text/xml", 1024);
        assertEquals("xml", body.format());
        assertTrue(body.text().contains("\n"));
    }

    @Test
    void truncatesLargeBodiesWithoutFormatting() throws Exception {
        final var msg = Request.post("/").contentType("application/json").body("{\"a\":\"0123456789\"}").build();
        final var body = MessageText.formatted(msg, "application/json", 5);
        assertTrue(body.truncated());
        assertEquals("{\"a\":", body.text());
        assertEquals(18, body.originalLength());
    }

    @Test
    void doesNotRenderBinaryBodies() throws Exception {
        final var msg = Request.post("/").contentType("image/png").body(new byte[]{1, 2, 3}).build();
        final var body = MessageText.formatted(msg, "image/png", 1024);
        assertEquals("binary", body.format());
        assertEquals("", body.text());
    }

    @Test
    void formBodyAsLines() throws Exception {
        final var msg = Request.post("/").contentType("application/x-www-form-urlencoded").body("a=1&b=x+y").build();
        assertTrue(MessageText.formatted(msg, "application/x-www-form-urlencoded", 1024).text().contains("b = x y"));
    }

    @Test
    void curlQuotesShellMetacharacters() throws Exception {
        final var exc = Request.post("/q?a=1").header("Host", "gw.example.com").header("X-Evil", "'; rm -rf / #")
                .contentType("text/plain").body("it's").buildExchange();

        final var curl = MessageText.curl(exc, false);

        assertTrue(curl.startsWith("curl -X 'POST' 'http://gw.example.com/q?a=1'"));
        assertTrue(curl.contains("-H 'X-Evil: '\\''; rm -rf / #'"));
        assertTrue(curl.contains("--data-binary 'it'\\''s'"));
        assertFalse(curl.contains("Host:"));
    }
}
