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
package com.predic8.membrane.core.lang;

import com.predic8.membrane.core.http.MimeType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.CONTENT_ENCODING;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.MimeType.APPLICATION_OCTET_STREAM;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.lang.BodyKind.FOREIGN;
import static com.predic8.membrane.core.lang.BodyKind.MATCHING;
import static com.predic8.membrane.core.lang.BodyKind.MISLABELLED;
import static com.predic8.membrane.core.lang.BodyKind.startsLikeDocument;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class BodyKindTest {

    /**
     * Starts with 0xFF, like the byte order mark of UTF-16LE.
     */
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};

    /**
     * A gRPC frame starts with 0x00, like UTF-16BE or UTF-32BE text.
     */
    static final byte[] GRPC = {0x00, 0x00, 0x00, 0x00, 0x05, 0x08, 0x01, 0x10, 0x02, 0x00};

    @Test
    void jpegIsForeign() throws URISyntaxException {
        assertEquals(FOREIGN, kind("image/jpeg", JPEG));
    }

    @Test
    void grpcIsForeign() throws URISyntaxException {
        assertEquals(FOREIGN, kind("application/grpc", GRPC));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
    void xmlWithoutByteOrderMarkIsMislabelled(String charset) throws URISyntaxException {
        assertEquals(MISLABELLED, kind(APPLICATION_OCTET_STREAM, " <a/>".getBytes(Charset.forName(charset))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
    void xmlWithByteOrderMarkIsMislabelled(String charset) throws URISyntaxException {
        assertEquals(MISLABELLED, kind(APPLICATION_OCTET_STREAM, "﻿ <a/>".getBytes(Charset.forName(charset))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
    void textWithByteOrderMarkIsForeign(String charset) throws URISyntaxException {
        assertEquals(FOREIGN, kind(APPLICATION_OCTET_STREAM, "﻿hello".getBytes(Charset.forName(charset))));
    }

    @Test
    void textIsForeign() throws URISyntaxException {
        assertEquals(FOREIGN, kind(APPLICATION_OCTET_STREAM, "hello <a/>".getBytes(UTF_8)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/xml", "application/soap+xml", "multipart/related; type=\"application/xop+xml\""})
    void ofXmlMatchesXmlAndXop(String contentType) throws URISyntaxException {
        assertEquals(MATCHING, BodyKind.ofXml(post("/foo").contentType(contentType).body("<a/>").build()));
    }

    @Test
    void ofXmlSniffsForAnAngleBracket() throws URISyntaxException {
        assertEquals(FOREIGN, BodyKind.ofXml(post("/foo").contentType(APPLICATION_JSON).body("{}").build()));
        assertEquals(MISLABELLED, BodyKind.ofXml(post("/foo").contentType(APPLICATION_JSON).body("<a/>").build()));
    }

    private static BodyKind kind(String contentType, byte[] body) throws URISyntaxException {
        return BodyKind.of(post("/foo").contentType(contentType).body(body).build(), MimeType::isXML, "<");
    }

    @Nested
    class StartsLikeDocument {

        @ParameterizedTest
        @ValueSource(strings = {"<a/>", " \r\n\t<a/>", "<", "<?xml version=\"1.0\"?><a/>"})
        void xmlStart(String body) throws URISyntaxException {
            assertTrue(startsLike(body.getBytes(UTF_8), "<"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"{\"a\":1}", "[1]", " \n{", "["})
        void jsonStart(String body) throws URISyntaxException {
            assertTrue(startsLike(body.getBytes(UTF_8), "{["));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", " \r\n\t", "a=1&b=2", "hello <a/>", "42", "\"admin\"", " <a/>", "\f<a/>"})
        void noXmlStart(String body) throws URISyntaxException {
            assertFalse(startsLike(body.getBytes(UTF_8), "<"));
        }

        @Test
        void onlyTheLanguagesOwnStartCounts() throws URISyntaxException {
            assertFalse(startsLike("<a/>".getBytes(UTF_8), "{["));
            assertFalse(startsLike("{\"a\":1}".getBytes(UTF_8), "<"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
        void byteOrderMarkIsSkipped(String charset) throws URISyntaxException {
            assertTrue(startsLike("﻿<a/>".getBytes(Charset.forName(charset)), "<"));
            assertTrue(startsLike("﻿ \n{\"a\":1}".getBytes(Charset.forName(charset)), "{["));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
        void encodingWithoutByteOrderMarkIsDetected(String charset) throws URISyntaxException {
            assertTrue(startsLike("<a/>".getBytes(Charset.forName(charset)), "<"));
            assertTrue(startsLike("  <a/>".getBytes(Charset.forName(charset)), "<"));
            assertTrue(startsLike("[1]".getBytes(Charset.forName(charset)), "{["));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
        void byteOrderMarkAloneOrBeforeTextIsNoStart(String charset) throws URISyntaxException {
            assertFalse(startsLike("﻿".getBytes(Charset.forName(charset)), "<"));
            assertFalse(startsLike("﻿hello".getBytes(Charset.forName(charset)), "<"));
        }

        @Test
        void binaryIsNoStart() throws URISyntaxException {
            assertFalse(startsLike(JPEG, "<"));
            assertFalse(startsLike(JPEG, "{["));
            assertFalse(startsLike(GRPC, "<"));
            assertFalse(startsLike(GRPC, "{["));
        }

        /**
         * Bodies shorter than the four bytes the encoding is told from.
         */
        @Test
        void shortBodies() throws URISyntaxException {
            assertTrue(startsLike(new byte[]{0x00, '<'}, "<"));
            assertTrue(startsLike(new byte[]{'<', 0x00}, "<"));
            assertFalse(startsLike(new byte[]{0x00}, "<"));
            assertFalse(startsLike(new byte[]{(byte) 0xFF}, "<"));
            assertFalse(startsLike(new byte[]{(byte) 0xFE, (byte) 0xFF}, "<"));
        }

        @Test
        void invalidUtf8IsNoStart() throws URISyntaxException {
            assertFalse(startsLike(new byte[]{(byte) 0xC3, 0x28, '<'}, "<"));
        }

        /**
         * The body is looked at as the content encoding unpacks it, not as it arrived.
         */
        @Test
        void contentEncodingIsDecoded() throws Exception {
            var msg = post("/foo").contentType(APPLICATION_OCTET_STREAM).body(gzip("  <a/>".getBytes(UTF_8))).build();
            msg.getHeader().setValue(CONTENT_ENCODING, "gzip");
            assertTrue(startsLikeDocument(msg, "<"));
        }

        /**
         * The sniff only reads the body, so a parser or the backend still gets all of it.
         */
        @Test
        void bodyIsLeftIntact() throws URISyntaxException {
            var body = "﻿  <a/>".getBytes(UTF_8);
            var msg = post("/foo").contentType(APPLICATION_OCTET_STREAM).body(body).build();
            assertTrue(startsLikeDocument(msg, "<"));
            assertArrayEquals(body, msg.getBody().getContent());
        }

        private static boolean startsLike(byte[] body, String documentStart) throws URISyntaxException {
            return startsLikeDocument(post("/foo").contentType(APPLICATION_OCTET_STREAM).body(body).build(), documentStart);
        }

        private static byte[] gzip(byte[] content) throws IOException {
            var out = new ByteArrayOutputStream();
            try (var gz = new GZIPOutputStream(out)) {
                gz.write(content);
            }
            return out.toByteArray();
        }
    }
}
