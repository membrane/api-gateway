/* Copyright 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import static com.predic8.membrane.core.http.ChunksBuilder.chunks;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class LimitInterceptorTest {

    static final String SMALL = "small";
    static final String LARGE = "large message body";
    static final String CRLF = "\r\n";
    static final String NEXT_REQUEST = "GET /next HTTP/1.1" + CRLF + "Host: example.com" + CRLF + CRLF;

    LimitInterceptor limit;

    @BeforeEach
    void setUp() {
        limit = limit(10);
    }

    @Nested
    class Requests {

        @Test
        void small() throws Exception {
            final var exc = Request.post("/foo").body(SMALL).buildExchange();
            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
        }

        @Test
        void largeIsAnsweredWith413() throws Exception {
            final var exc = Request.post("/foo").body(LARGE).buildExchange();
            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        @Test
        void bodyOfUnknownLengthBelowTheLimitKeepsItsContent() throws Exception {
            final var exc = exchangeOf(requestWithBodyOfUnknownLength(SMALL));
            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
        }

        @Test
        void bodyOfUnknownLengthAboveTheLimitIsReadOnlyUpToTheLimit() throws Exception {
            final var in = new ByteArrayInputStream(new byte[1_000_000]);
            final var request = Request.post("/foo").build();
            request.setBody(new Body(in));
            final var exc = exchangeOf(request);

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
            assertEquals(1_000_000 - 11, in.available());
        }

        @Test
        void chunkedBodyBelowTheLimitIsForwardedWithItsContent() throws Exception {
            final var exc = exchangeOf(parseRequest("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Transfer-Encoding: chunked\r
                    \r
                    """, chunks().add("sma").add("ll").build()));

            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
            assertTrue(exc.getRequest().getHeader().isChunked());
        }

        @Test
        void chunkedBodyAboveTheLimitIsRejected() throws Exception {
            final var exc = exchangeOf(parseRequest("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Transfer-Encoding: chunked\r
                    \r
                    """, chunks().add("large ").add("message body").build()));

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        /**
         * The chunk is also larger than the decoder accepts for a single chunk; it is over the limit all the same.
         */
        @Test
        void chunkLargerThanTheDecoderAcceptsIsAnsweredWith413() throws Exception {
            final var exc = exchangeOf(parseRequest("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Transfer-Encoding: chunked\r
                    \r
                    """, "FFFFFFFF\r\n".getBytes(US_ASCII)));

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        @Test
        void chunkedGzipBodyBelowTheLimitIsForwardedEncoded() throws Exception {
            final var limit = limit(1000);
            final var gzip = gzip(SMALL);
            final var exc = exchangeOf(parseRequest("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Transfer-Encoding: chunked\r
                    Content-Encoding: gzip\r
                    \r
                    """, chunked(gzip)));

            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals("gzip", exc.getRequest().getHeader().getContentEncoding());
            assertTrue(exc.getRequest().getHeader().isChunked());
            assertArrayEquals(gzip, exc.getRequest().getBody().getContent());
        }

        @Test
        void bodyOfKnownLengthAboveTheLimitIsNotReadAndTheConnectionIsNotReused() throws Exception {
            final var in = new ByteArrayInputStream(("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Content-Length: %d\r
                    \r
                    %s""".formatted(LARGE.length(), LARGE) + NEXT_REQUEST).getBytes(US_ASCII));
            final var request = new Request();
            request.read(in, true);
            final var exc = exchangeOf(request);
            final var unread = in.available();

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
            assertEquals(unread, in.available());
            assertFalse(exc.canKeepConnectionAlive());
        }

        @Test
        void uncompressedBodyOfKnownLengthBelowTheLimitIsForwardedUnread() throws Exception {
            final var in = new ByteArrayInputStream(("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Content-Length: 5\r
                    \r
                    small""" + NEXT_REQUEST).getBytes(US_ASCII));
            final var request = new Request();
            request.read(in, true);
            final var unread = in.available();

            assertEquals(CONTINUE, limit.handleRequest(exchangeOf(request)));
            assertFalse(request.getBody().isRead());
            assertEquals(unread, in.available());
            assertEquals("5", request.getHeader().getFirstValue("Content-Length"));
        }

        @Test
        void compressedBodyIsForwardedEncoded() throws Exception {
            final var limit = limit(1000);
            final var gzip = gzip(SMALL);
            final var exc = Request.post("/foo").body(gzip).header("Content-Encoding", "gzip").buildExchange();

            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals("gzip", exc.getRequest().getHeader().getContentEncoding());
            assertArrayEquals(gzip, exc.getRequest().getBody().getContent());
        }

        @Test
        void compressedBodyIsLimitedByItsDecodedLength() throws Exception {
            final var limit = limit(100);
            final var bomb = gzip("a".repeat(1000));
            assertTrue(bomb.length < 100);
            final var exc = exchangeOf(compressedRequestOfUnknownLength(bomb));

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        @Test
        void compressedBodyIsLimitedByItsEncodedLengthToo() throws Exception {
            final var limit = limit(20);
            final var random = new byte[10];
            new Random(0).nextBytes(random);
            final var gzip = gzip(random);
            assertTrue(gzip.length > 20);
            final var exc = exchangeOf(compressedRequestOfUnknownLength(gzip));

            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        @Test
        void decoderIsClosedBelowTheLimit() throws Exception {
            final var request = new CloseTrackingRequest("text/plain", SMALL);
            final var limit = limit(1000);
            assertEquals(CONTINUE, limit.handleRequest(exchangeOf(request)));
            assertTrue(request.allStreamsClosed());
        }

        @Test
        void decoderIsClosedAboveTheLimit() throws Exception {
            final var request = new CloseTrackingRequest("text/plain", "a".repeat(1000));
            final var limit = limit(100);
            assertEquals(ABORT, limit.handleRequest(exchangeOf(request)));
            assertTrue(request.allStreamsClosed());
        }

        @Test
        void truncatedGzipIsABodyFailure() throws Exception {
            final var limit = limit(1000);
            final var request = new CloseTrackingRequest("text/plain", "a".repeat(100));
            final var gzip = request.getBody().getContent();
            request.setBody(new Body(Arrays.copyOf(gzip, gzip.length - 8)));

            assertThrows(ReadingBodyException.class, () -> limit.handleRequest(exchangeOf(request)));
            assertTrue(request.allStreamsClosed());
        }

        @Test
        void bodyWithUnsupportedContentEncodingIsForwardedEncoded() throws Exception {
            final var encoded = new byte[]{1, 2, 3, 4, 5};
            final var exc = Request.post("/foo").body(encoded).header("Content-Encoding", "unknown").buildExchange();
            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals("unknown", exc.getRequest().getHeader().getContentEncoding());
            assertArrayEquals(encoded, exc.getRequest().getBody().getContent());
        }

        @Test
        void bodyWithUnsupportedContentEncodingIsLimitedByItsEncodedLength() throws Exception {
            final var request = Request.post("/foo").header("Content-Encoding", "unknown").build();
            request.setBody(new Body(new ByteArrayInputStream(LARGE.getBytes(UTF_8))));
            final var exc = exchangeOf(request);
            assertEquals(ABORT, limit.handleRequest(exc));
            assertEquals(413, exc.getResponse().getStatusCode());
        }

        @Test
        void xopMessageIsForwardedUnchanged() throws Exception {
            final var limit = limit(10_000);
            final var contentType = "multipart/related; type=\"application/xop+xml\"; boundary=\"uuid:b\"; start=\"<root@test>\"; start-info=\"text/xml\"";
            final var body = ("""
                    --uuid:b\r
                    Content-Type: application/xop+xml; charset=UTF-8; type="text/xml"\r
                    Content-ID: <root@test>\r
                    \r
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><data>\
                    <xop:Include xmlns:xop="http://www.w3.org/2004/08/xop/include" href="cid:bin@test"/>\
                    </data></s:Body></s:Envelope>\r
                    --uuid:b\r
                    Content-Type: application/octet-stream\r
                    Content-ID: <bin@test>\r
                    \r
                    0123456789\r
                    --uuid:b--\r
                    """).getBytes(UTF_8);
            final var request = Request.post("/foo").contentType(contentType).build();
            request.setBody(new Body(new ByteArrayInputStream(body)));
            final var exc = exchangeOf(request);

            assertEquals(CONTINUE, limit.handleRequest(exc));
            assertEquals(contentType, exc.getRequest().getHeader().getContentType());
            assertArrayEquals(body, exc.getRequest().getBody().getContent());
        }

        @Test
        void chunkedBodyAlreadyPartlyReadIsReadToTheEnd() throws Exception {
            final var request = parseRequest("""
                    POST /foo HTTP/1.1\r
                    Host: example.com\r
                    Transfer-Encoding: chunked\r
                    \r
                    """, chunks().add("sma").add("ll").build());
            assertEquals('s', request.getBodyAsStream().read());

            assertEquals(CONTINUE, limit.handleRequest(exchangeOf(request)));
            assertEquals(SMALL, request.getBodyAsStringDecoded());
        }
    }

    @Nested
    class Responses {

        @Test
        void largeIsAnsweredWith502() throws Exception {
            final var exc = Response.ok().body(LARGE).buildExchange();
            assertEquals(ABORT, limit.handleResponse(exc));
            assertEquals(502, exc.getResponse().getStatusCode());
        }

        @Test
        void compressedBodyKeepsItsFraming() throws Exception {
            final var limit = limit(1000);
            final var gzip = gzip(SMALL);
            final var response = Response.fromStream(new ByteArrayInputStream(concat(("""
                    HTTP/1.1 200 OK\r
                    Content-Encoding: gzip\r
                    Content-Length: %d\r
                    \r
                    """.formatted(gzip.length)).getBytes(US_ASCII), gzip)), true);
            final var exc = new Exchange(null);
            exc.setResponse(response);

            assertEquals(CONTINUE, limit.handleResponse(exc));
            assertEquals(String.valueOf(gzip.length), response.getHeader().getFirstValue("Content-Length"));
            assertEquals("gzip", response.getHeader().getContentEncoding());
            assertArrayEquals(gzip, response.getBody().getContent());
        }

        @Test
        void responseWithoutContentIsNotCheckedAgainstItsContentLength() throws Exception {
            final var response = Response.fromStream(new ByteArrayInputStream("""
                    HTTP/1.1 304 Not Modified\r
                    Content-Length: 1000\r
                    \r
                    """.getBytes(US_ASCII)), true);
            final var exc = new Exchange(null);
            exc.setResponse(response);

            assertEquals(CONTINUE, limit.handleResponse(exc));
            assertEquals("1000", response.getHeader().getFirstValue("Content-Length"));
        }

        @Test
        void responseWithoutContentLabelledGzipPasses() throws Exception {
            final var response = Response.fromStream(new ByteArrayInputStream("""
                    HTTP/1.1 304 Not Modified\r
                    Content-Encoding: gzip\r
                    \r
                    """.getBytes(US_ASCII)), true);
            final var exc = new Exchange(null);
            exc.setResponse(response);

            assertEquals(CONTINUE, limit.handleResponse(exc));
        }

        @Test
        void responseToHeadIsNotCheckedAgainstItsContentLength() throws Exception {
            final var exc = Request.get("/foo").method("HEAD").buildExchange();
            final var response = Response.ok().build();
            response.getHeader().setContentLength(1000);
            exc.setResponse(response);

            assertEquals(CONTINUE, limit.handleResponse(exc));
            assertEquals(200, exc.getResponse().getStatusCode());
        }
    }

    @Test
    void disabledLimitPassesEverything() throws Exception {
        final var limit = limit(-1);
        final var exc = Request.post("/foo").body(LARGE).buildExchange();
        assertEquals(CONTINUE, limit.handleRequest(exc));
        assertEquals(LARGE, exc.getRequest().getBodyAsStringDecoded());
    }

    private static LimitInterceptor limit(long maxBodyLength) {
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(maxBodyLength);
        limit.init(new DummyTestRouter());
        return limit;
    }

    private static Request parseRequest(String head, byte[] body) throws Exception {
        final var request = new Request();
        request.read(new ByteArrayInputStream(concat(head.getBytes(US_ASCII), body)), true);
        return request;
    }

    private static Request requestWithBodyOfUnknownLength(String body) throws Exception {
        final var request = Request.post("/foo").build();
        request.setBody(new Body(new ByteArrayInputStream(body.getBytes(UTF_8))));
        return request;
    }

    private static Request compressedRequestOfUnknownLength(byte[] gzip) throws Exception {
        final var request = Request.post("/foo").header("Content-Encoding", "gzip").build();
        request.setBody(new Body(new ByteArrayInputStream(gzip)));
        return request;
    }

    private static byte[] chunked(byte[] content) {
        return concat((Integer.toHexString(content.length) + CRLF).getBytes(US_ASCII), content, (CRLF + "0" + CRLF + CRLF).getBytes(US_ASCII));
    }

    private static byte[] concat(byte[]... parts) {
        final var out = new ByteArrayOutputStream();
        for (final var part : parts)
            out.writeBytes(part);
        return out.toByteArray();
    }

    private static byte[] gzip(String content) throws IOException {
        return gzip(content.getBytes(UTF_8));
    }

    private static byte[] gzip(byte[] content) throws IOException {
        final var out = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(out)) {
            gzip.write(content);
        }
        return out.toByteArray();
    }

    private static Exchange exchangeOf(Request request) {
        final var exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }
}
