/* Copyright 2009, 2011 predic8 GmbH, www.predic8.com

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

import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.EOFWhileReadingLineException;
import com.predic8.membrane.core.transport.http.LineTooLongException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.predic8.membrane.annot.Constants.CRLF;
import static com.predic8.membrane.core.http.Header.X_FORWARDED_FOR;
import static com.predic8.membrane.core.util.HttpTestUtil.convertMessage;
import static com.predic8.membrane.core.util.HttpUtil.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.*;

public class HttpUtilTest {

    private static final String s1 = "foo" + CRLF + "bar" + CRLF + CRLF;
    private static InputStream is1;

    private static final String POST_REQUEST = """
		POST /operation/call HTTP/1.1
		Host: service-repository.com:80
		Connection: keep-alive
		Content-Length: 168
		Content-Type: application/x-www-form-urlencoded
		
		endpoint=http%3A%2F%2Fwww.thomas-bayer.com%3A80%2Faxis2%2Fservices%2FBLZService&xpath%3A%2FgetBank%2Fblz=38070024&id=65657&operation=getBank&portType=BLZServicePortType
		""";

    @BeforeEach
    void setUp() {
        is1 = new ByteArrayInputStream(s1.getBytes());
    }

    @Test
    void testReadLine() throws IOException {
        assertEquals("foo", readLine(is1));
        assertEquals("bar", readLine(is1));
        assertEquals("", readLine(is1));
    }

    @Test
    void readLineMessage() throws Exception {
        assertEquals("POST /operation/call HTTP/1.1", readLine( convertMessage(POST_REQUEST)));
    }

    /**
     * readLine reads a BufferedInputStream or ByteArrayInputStream in bulk and any other stream
     * byte by byte. Every stream kind must return the same lines, throw the same exceptions and
     * leave the same bytes in the stream.
     */
    @Nested
    class ReadLine {

        @Test
        void crlf() throws IOException {
            assertSameOnAllStreams("foo\r\nbar\r\n\r\n");
        }

        @Test
        void lf() throws IOException {
            assertSameOnAllStreams("foo\nbar\n\n");
        }

        @Test
        void lfCrIsOneTerminator() throws IOException {
            assertEquals(List.of("line:foo", "line:bar", "eof:", "rest:"), readAll(byteByByte("foo\n\rbar\r\n".getBytes(ISO_8859_1))));
            assertSameOnAllStreams("foo\n\rbar\r\n");
        }

        @Test
        void crSwallowsTheNextByte() throws IOException {
            assertEquals(List.of("line:foo", "line:ar", "eof:", "rest:"), readAll(byteByByte("foo\rbar\r\n".getBytes(ISO_8859_1))));
            assertSameOnAllStreams("foo\rbar\r\n");
        }

        @Test
        void terminatorAtEndOfStream() throws IOException {
            assertSameOnAllStreams("foo\r");
            assertSameOnAllStreams("foo\n");
        }

        @Test
        void eofInsideLine() throws IOException {
            assertEquals(List.of("line:foo", "eof:ba", "rest:"), readAll(byteByByte("foo\r\nba".getBytes(ISO_8859_1))));
            assertSameOnAllStreams("foo\r\nba");
        }

        @Test
        void emptyStream() throws IOException {
            assertSameOnAllStreams("");
        }

        @Test
        void bytesAreLatin1() throws IOException {
            byte[] bytes = {'/', (byte) 0xC3, (byte) 0xA4, (byte) 0xFF, 13, 10};
            assertEquals("/Ã¤ÿ", readLine(new ByteArrayInputStream(bytes)));
            assertSameOnAllStreams(bytes);
        }

        @Test
        void leavesFollowingBytesInStream() throws IOException {
            InputStream in = new BufferedInputStream(new ByteArrayInputStream("GET / HTTP/1.1\r\nHost: a\r\n\r\nbody".getBytes(ISO_8859_1)));
            assertEquals("GET / HTTP/1.1", readLine(in));
            assertEquals("Host: a\r\n\r\nbody", new String(in.readAllBytes(), ISO_8859_1));
        }

        @Test
        void lineLongerThanBulkReadLimit() throws IOException {
            assertSameOnAllStreams("a".repeat(5_000) + "\r\nb\r\n");
        }

        @Test
        void lineTooLong() throws IOException {
            assertSameOnAllStreams("a".repeat(9_000) + "\r\n");
        }

        @Test
        void randomInput() throws IOException {
            byte[] alphabet = {'a', 'b', ' ', 13, 13, 10, 10, (byte) 0xE4};
            for (int seed = 0; seed < 300; seed++) {
                Random random = new Random(seed);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int length = random.nextInt(3_000);
                while (out.size() < length) {
                    if (random.nextInt(50) == 0)
                        out.writeBytes("x".repeat(random.nextInt(9_000)).getBytes(ISO_8859_1));
                    else
                        out.write(alphabet[random.nextInt(alphabet.length)]);
                }
                assertSameOnAllStreams(out.toByteArray());
            }
        }

        private static void assertSameOnAllStreams(String content) throws IOException {
            assertSameOnAllStreams(content.getBytes(ISO_8859_1));
        }

        private static void assertSameOnAllStreams(byte[] content) throws IOException {
            List<String> expected = readAll(byteByByte(content));
            assertEquals(expected, readAll(new ByteArrayInputStream(content)), "ByteArrayInputStream");
            for (int size : new int[]{1, 2, 3, 16, 2048, 8192}) {
                assertEquals(expected, readAll(new BufferedInputStream(new ByteArrayInputStream(content), size)), "BufferedInputStream(" + size + ")");
            }
        }

        /**
         * Reads lines until the stream fails, then the bytes left in it.
         */
        private static List<String> readAll(InputStream in) throws IOException {
            List<String> events = new ArrayList<>();
            try {
                while (true)
                    events.add("line:" + readLine(in));
            } catch (EOFWhileReadingLineException e) {
                events.add("eof:" + e.getLineSoFar());
            } catch (LineTooLongException e) {
                events.add("tooLong:" + e.getMessage());
            }
            events.add("rest:" + new String(in.readAllBytes(), ISO_8859_1));
            return events;
        }

        /**
         * Not one of the classes read in bulk, so readLine takes the byte-by-byte path.
         */
        private static InputStream byteByByte(byte[] content) {
            return new FilterInputStream(new ByteArrayInputStream(content)) {};
        }
    }

    @Test
    void unescapedHtmlMessageTest() {
        assertEquals("<html><head><title>caption</title></head><body><h1>caption</h1><p>body</p></body></html>", unescapedHtmlMessage("caption", "body"));
    }

    @Test
    void getForwardedForSimpleTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "192.168.15.15").buildExchange();
        assertEquals(List.of("192.168.15.15"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForRemoveMembraneTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "192.168.15.15, 8.7.6.5, 4.4.5.5, ::1, 8.7.6.5").buildExchange();
        exc.setRemoteAddrIp("8.7.6.5");
        assertEquals(List.of("192.168.15.15", "8.7.6.5", "4.4.5.5", "::1"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForCompoundTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "10.10.10.10, 7.7.8.8, 192.168.15.15").buildExchange();
        assertEquals(List.of("10.10.10.10", "7.7.8.8", "192.168.15.15"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForWhitespaceTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "  192.168.15.15,     10.10.10.10       ").buildExchange();
        assertEquals(List.of("192.168.15.15", "10.10.10.10"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForV6Test() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "628c:1f76:efbf:bd62:9528:869d:2333:e115").buildExchange();
        assertEquals(List.of("628c:1f76:efbf:bd62:9528:869d:2333:e115"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForHostnameTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "abc.xyz").buildExchange();
        assertEquals(List.of("abc.xyz"), getForwardedForList(exc));
    }

    @Test
    void getForwardedForCompoundMultiHeaderTest() {
        var exc = new Request.Builder().header(X_FORWARDED_FOR, "::1,628c::e115")
                .header(X_FORWARDED_FOR, "10.10.10.10, 8.8.8.8").buildExchange();
        assertEquals(List.of("::1", "628c::e115", "10.10.10.10", "8.8.8.8"), getForwardedForList(exc));
    }

    @ParameterizedTest
    @CsvSource({
            "'http://localhost', '/'",
            "'http://localhost/', '/'",
            "'http://localhost?', '/?'",
            "'http://localhost?foo=bar', '/?foo=bar'",
            "'http://localhost:2000', '/'",
            "'http://localhost:2000/', '/'",
            "'http://localhost:2000?', '/?'",
            "'http://localhost:2000?foor=bar', '/?foor=bar'",
            "'http://localhost:2000/foor?bar', '/foor?bar'"
    })
    public void testGetPathAndQueryString(String url, String expected) throws MalformedURLException {
        assertEquals(expected, HttpUtil.getPathAndQueryString(url));
    }

    @ParameterizedTest
    @CsvSource({
            "GET,true",
            "PUT,true",
            "DELETE,true",
            "OPTIONS,true",
            "HEAD,true",
            "TRACE,true",
            "POST,false",
            "PATCH,false",
            "CONNECT,false"
    })
    void idempotent(String method, boolean expected) {
        assertEquals(expected, isIdempotent(method));
    }

    @ParameterizedTest
    @CsvSource({
            "204, No Content",
            "301, Moved Permanently",
            "406, Not Acceptable",
            "407, Proxy Authentication Required",
            "408, Request Timeout",
            "412, Precondition Failed",
            "418, I'm a Teapot",
            "423, Locked",
            "428, Precondition Required",
            "429, Too Many Requests",
            "508, Loop Detected"
    })
    void statusMessageForListedCode(int code, String message) {
        assertEquals(message, getMessageForStatusCode(code));
    }

    @ParameterizedTest
    @CsvSource({
            "199, Information",
            "299, Success",
            "399, Redirection",
            "499, Client Error",
            "599, Server Error"
    })
    void unlistedCodeFallsBackToItsStatusClass(int code, String message) {
        assertEquals(message, getMessageForStatusCode(code));
    }

    @ParameterizedTest
    @ValueSource(chars = {'a', 'z', 'A', 'Z', '0', '9', '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~'})
    void tcharIsAccepted(char c) {
        assertTrue(isTchar(c));
    }

    @ParameterizedTest
    @ValueSource(chars = {' ', '\t', ':', '"', '(', ')', ',', '/', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}', '\u0000', '\u000B', '\u007F', 'ö'})
    void nonTcharIsRejected(char c) {
        assertFalse(isTchar(c));
    }

    @ParameterizedTest
    @ValueSource(chars = {' ', '\t'})
    void spaceAndTabAreOptionalWhitespace(char c) {
        assertTrue(isOptionalWhitespace(c));
    }

    @ParameterizedTest
    @ValueSource(chars = {'\u000B', '\f', '\r', '\n', ' ', 'a'})
    void otherCharactersAreNotOptionalWhitespace(char c) {
        assertFalse(isOptionalWhitespace(c));
    }

    @Test
    void everyStatusCodeHasANonEmptyMessage() {
        for (int code = 100; code < 600; code++)
            assertFalse(getMessageForStatusCode(code).isBlank(), "empty reason phrase for " + code);
    }
}
