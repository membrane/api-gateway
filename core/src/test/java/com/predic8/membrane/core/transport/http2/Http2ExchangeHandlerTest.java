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

package com.predic8.membrane.core.transport.http2;

import com.predic8.membrane.core.http.Request;
import com.twitter.hpack.Decoder;
import com.twitter.hpack.Encoder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.transport.http2.Http2ExchangeHandler.createHeadersFrames;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Http2ExchangeHandlerTest {

    @Test
    void requestWithHostHeaderSendsAuthority() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").header("Host", "example.com").build());
        assertEquals("example.com", headers.get(":authority"));
        assertFalse(headers.containsKey("host"));
    }

    @Test
    void requestWithoutHostHeaderOmitsAuthority() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").build());
        assertEquals("/foo", headers.get(":path"));
        assertFalse(headers.containsKey(":authority"));
    }

    /**
     * Values are written byte for byte, as by the HTTP/1.1 writer, instead of replacing non-ASCII characters.
     */
    @Test
    void nonAsciiValueIsEncodedByteForByte() throws Exception {
        final var headers = encodeRequestHeaders(get("/foo").header("X-Foo", "café").build());
        assertEquals("café", headers.get("x-foo"));
    }

    @Test
    void headerNamesAreLowerCasedIndependentOfDefaultLocale() throws Exception {
        final var defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            final var headers = encodeRequestHeaders(get("/foo").header("TITLE", "x").build());
            assertEquals("x", headers.get("title"));
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    private static Map<String, String> encodeRequestHeaders(Request request) throws IOException {
        final var frames = createHeadersFrames(request, request.getHeader(), 1, new Encoder(4096), new Settings(), false);
        assertEquals(1, frames.size());
        final var headersFrame = frames.getFirst().asHeaders();

        final var headers = new LinkedHashMap<String, String>();
        final var decoder = new Decoder(8192, 4096);
        decoder.decode(new ByteArrayInputStream(headersFrame.getContent(), headersFrame.getHeaderBlockStartIndex(),
                        headersFrame.getHeaderBlockLength()),
                (name, value, sensitive) -> headers.put(new String(name, ISO_8859_1), new String(value, ISO_8859_1)));
        decoder.endHeaderBlock();
        return headers;
    }
}
