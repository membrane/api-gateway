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
package com.predic8.membrane.core.util.text;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import static com.predic8.membrane.core.util.text.TextUtil.detectEncoding;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class TextUtilTest {

    @Nested
    class DetectEncoding {

        private static final Charset UTF_32BE = Charset.forName("UTF-32BE");
        private static final Charset UTF_32LE = Charset.forName("UTF-32LE");

        @ParameterizedTest
        @ValueSource(strings = {"UTF-8", "UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
        void byteOrderMarkIsDetectedAndSkipped(String charset) throws IOException {
            var stream = stream("﻿<a/>".getBytes(Charset.forName(charset)));
            assertEquals(Charset.forName(charset), detectEncoding(stream));
            assertEquals("<a/>", new String(stream.readAllBytes(), Charset.forName(charset)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"UTF-16BE", "UTF-16LE", "UTF-32BE", "UTF-32LE"})
        void encodingWithoutByteOrderMarkIsDetected(String charset) throws IOException {
            var bytes = " <a/>".getBytes(Charset.forName(charset));
            var stream = stream(bytes);
            assertEquals(Charset.forName(charset), detectEncoding(stream));
            assertArrayEquals(bytes, stream.readAllBytes(), "Nothing to skip");
        }

        @Test
        void asciiIsUtf8() throws IOException {
            var stream = stream("<a/>".getBytes(US_ASCII));
            assertEquals(UTF_8, detectEncoding(stream));
            assertArrayEquals("<a/>".getBytes(US_ASCII), stream.readAllBytes());
        }

        @Test
        void utf8WithoutByteOrderMarkIsUtf8() throws IOException {
            assertEquals(UTF_8, detectEncoding(stream("äöü".getBytes(UTF_8))));
        }

        /**
         * Binary data is no text, so it is reported as UTF-8 and nothing is skipped, even where it starts
         * with a byte a byte order mark or UTF-16 text could start with.
         */
        @Test
        void binaryIsUtf8AndNothingIsSkipped() throws IOException {
            var jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
            var grpc = new byte[]{0x00, 0x00, 0x00, 0x00, 0x05, 0x08};
            for (var bytes : new byte[][]{jpeg, grpc}) {
                var stream = stream(bytes);
                assertEquals(UTF_8, detectEncoding(stream));
                assertArrayEquals(bytes, stream.readAllBytes());
            }
        }

        /**
         * FF FE 00 00 is the UTF-32LE byte order mark, but also a UTF-16LE one followed by U+0000. UTF-32
         * wins, as in XML 1.0, Appendix F.
         */
        @Test
        void utf32LittleEndianByteOrderMarkWinsOverUtf16() throws IOException {
            assertEquals(UTF_32LE, detectEncoding(stream(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x00, '<', 0x00, 0x00, 0x00})));
        }

        @Test
        void byteOrderMarkWithoutText() throws IOException {
            var stream = stream(new byte[]{(byte) 0xFE, (byte) 0xFF});
            assertEquals(UTF_16BE, detectEncoding(stream));
            assertEquals(-1, stream.read());
        }

        @Test
        void shortStreams() throws IOException {
            assertEquals(UTF_8, detectEncoding(stream(new byte[0])));
            assertEquals(UTF_8, detectEncoding(stream(new byte[]{'<'})));
            assertEquals(UTF_8, detectEncoding(stream(new byte[]{0x00})));
            assertEquals(UTF_16BE, detectEncoding(stream(new byte[]{0x00, '<'})));
            assertEquals(UTF_16LE, detectEncoding(stream(new byte[]{'<', 0x00})));
            assertEquals(UTF_16LE, detectEncoding(stream(new byte[]{'<', 0x00, 0x00})));
        }

        @Test
        void utf32WithoutByteOrderMark() throws IOException {
            assertEquals(UTF_32BE, detectEncoding(stream(new byte[]{0x00, 0x00, 0x00, '<'})));
            assertEquals(UTF_32LE, detectEncoding(stream(new byte[]{'<', 0x00, 0x00, 0x00})));
        }

        private static BufferedInputStream stream(byte[] bytes) {
            return new BufferedInputStream(new ByteArrayInputStream(bytes));
        }
    }
}
