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
package com.predic8.membrane.interceptor;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.LimitInterceptor;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class LimitInterceptorTest {

    public static final String SMALL = "small";
    public static final String LARGE = "large message body";
    private static final String NEXT_REQUEST = "GET /next HTTP/1.1\r\n\r\n";
    private static final String NEXT_RESPONSE = "HTTP/1.1 200 OK\r\n\r\n";
    static LimitInterceptor li;

    @BeforeAll
    public static void before() {
        li = new LimitInterceptor();
        li.setMaxBodyLength(10);
        li.setAppliedFlow(REQUEST_FLOW);
        li.init(new DummyTestRouter());
    }

    @Test
    void small() {
        assertDoesNotThrow(() -> li.handleRequest(Request.post("/foo").body(SMALL).buildExchange()));
    }

    @Test
    void large() throws Exception {
        assertRejected(Request.post("/foo").body(LARGE).buildExchange());
    }

    /**
     * A body above the cap could not be read into memory, so it is rejected even though it is below
     * the configured limit. Checked through Content-Length, so no such body has to be built.
     */
    @Test
    void limitAboveLargestArrayIsCapped() throws Exception {
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(5_000_000_000L);
        limit.init(new DummyTestRouter());
        final var exc = Request.post("/foo").header(CONTENT_LENGTH, "3000000000").buildExchange();
        assertEquals(ABORT, limit.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Message is too large."));
    }

    @Test
    void messageWithoutBodyIsLeftAlone() throws Exception {
        final var exc = Request.get("/foo").buildExchange();
        assertEquals(CONTINUE, li.handleRequest(exc));
        assertFalse(exc.getRequest().getHeader().hasContentLength());
    }

    @Test
    void streamSmall() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(SMALL);
        li.handleRequest(exc);
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void streamLarge() throws Exception {
        assertRejected(getExchangeWithLargeStreamRequest(LARGE));
    }

    @Test
    void chunkedSmall() throws Exception {
        assertPassesChunked(SMALL.getBytes(UTF_8), null);
    }

    @Test
    void chunkedGzipSmall() throws Exception {
        assertPassesChunked(gzip(SMALL), "gzip");
    }

    @Test
    void chunkedDeflateSmall() throws Exception {
        assertPassesChunked(deflate(SMALL), "deflate");
    }

    @Test
    void chunkedLarge() throws Exception {
        assertRejected(chunkedExchange(new ByteArrayInputStream(chunked(LARGE.getBytes(UTF_8))), null));
    }

    @Test
    void chunkedDeflateLarge() throws Exception {
        assertRejected(chunkedExchange(new ByteArrayInputStream(chunked(deflate(LARGE))), "deflate"));
    }

    /**
     * The backend connection has to be left at the next response, so that it can go back to the pool.
     */
    @Test
    void rejectedChunkedResponseIsDrained() throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(LARGE.getBytes(UTF_8)), NEXT_RESPONSE.getBytes(US_ASCII)));
        final var response = Response.ok().header(TRANSFER_ENCODING, "chunked").build();
        response.setBody(new ChunkedBody(connection));
        final var exc = new Exchange(null);
        exc.setResponse(response);
        assertEquals(ABORT, li.handleResponse(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Message is too large."));
        assertEquals(NEXT_RESPONSE, new String(connection.readAllBytes(), US_ASCII));
    }

    @Test
    void truncatedChunkedBodyIsAReadFailureNotARejection() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream("5\r\nsm".getBytes(US_ASCII)), null);
        assertThrows(ReadingBodyException.class, () -> li.handleRequest(exc));
    }

    @Test
    void compressedXopIsLimitedBeforeMultipartParsing() throws Exception {
        final var body = "--boundary\r\nContent-Type: application/xop+xml\r\nContent-ID: <root@test>\r\n\r\n"
                + "<root>" + "x".repeat(100_000) + "</root>\r\n--boundary--\r\n";
        final var compressed = deflate(body);
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(1024);
        limit.init(new DummyTestRouter());
        final var exc = Request.post("/soap")
                .body(java.util.Arrays.copyOf(compressed, compressed.length - 1))
                .header(CONTENT_ENCODING, "deflate")
                .header(CONTENT_TYPE, "multipart/related; type=\"application/xop+xml\"; boundary=boundary; start=\"<root@test>\"")
                .buildExchange();
        // The truncated checksum must never be reached: reject once decoded MIME exceeds the limit.
        assertEquals(ABORT, limit.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Message is too large."));
    }

    @Test
    void acceptedXopPreservesMultipartRepresentation() throws Exception {
        final var body = "--boundary\r\nContent-Type: application/xop+xml; type=\"text/xml\"\r\n"
                + "Content-ID: <root@test>\r\n\r\n<root/>\r\n--boundary--\r\n";
        final var type = "multipart/related; type=\"application/xop+xml\"; boundary=boundary; start=\"<root@test>\"";
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(1024);
        limit.init(new DummyTestRouter());
        final var exc = Request.post("/soap").body(deflate(body))
                .header(CONTENT_ENCODING, "deflate").header(CONTENT_TYPE, type).buildExchange();
        assertEquals(CONTINUE, limit.handleRequest(exc));
        assertEquals(body, exc.getRequest().getBodyAsStringDecoded());
        assertEquals(type, exc.getRequest().getHeader().getContentType());
        assertNull(exc.getRequest().getHeader().getContentEncoding());
        assertEquals(body.getBytes(UTF_8).length, exc.getRequest().getHeader().getContentLength());
    }

    private static void assertRejected(Exchange exc) {
        assertEquals(ABORT, li.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Message is too large."));
    }

    /**
     * Also checks that the body is consumed up to its last chunk and no further: the bytes after it
     * belong to the next request on the connection.
     */
    private static void assertPassesChunked(byte[] body, String contentEncoding) throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(body), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc = chunkedExchange(connection, contentEncoding);
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var header = exc.getRequest().getHeader();
        assertNull(header.getFirstValue(TRANSFER_ENCODING));
        assertNull(header.getContentEncoding());
        assertEquals(SMALL.length(), header.getContentLength());
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
        assertEquals(NEXT_REQUEST, new String(connection.readAllBytes(), US_ASCII));
    }

    private static Exchange chunkedExchange(InputStream connection, String contentEncoding) throws URISyntaxException {
        final var request = Request.post("/foo").header(TRANSFER_ENCODING, "chunked").build();
        if (contentEncoding != null)
            request.getHeader().setValue(CONTENT_ENCODING, contentEncoding);
        request.setBody(new ChunkedBody(connection));
        final var exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }

    private static byte[] gzip(String value) throws IOException {
        final var bos = new ByteArrayOutputStream();
        try (final var gzip = new GZIPOutputStream(bos)) {
            gzip.write(value.getBytes(UTF_8));
        }
        return bos.toByteArray();
    }

    private static byte[] deflate(String value) throws IOException {
        final var bos = new ByteArrayOutputStream();
        try (final var deflate = new DeflaterOutputStream(bos)) {
            deflate.write(value.getBytes(UTF_8));
        }
        return bos.toByteArray();
    }

    private static byte[] concat(byte[] first, byte[] second) {
        final var out = new ByteArrayOutputStream();
        out.writeBytes(first);
        out.writeBytes(second);
        return out.toByteArray();
    }

    /**
     * Encodes the body in three-byte chunks, so it is not complete once the interceptor has looked at
     * the first one.
     */
    private static byte[] chunked(byte[] body) {
        final var out = new ByteArrayOutputStream();
        for (int i = 0; i < body.length; i += 3) {
            final int n = Math.min(3, body.length - i);
            out.writeBytes("%x\r\n".formatted(n).getBytes(US_ASCII));
            out.write(body, i, n);
            out.writeBytes("\r\n".getBytes(US_ASCII));
        }
        out.writeBytes("0\r\n\r\n".getBytes(US_ASCII));
        return out.toByteArray();
    }

    private static @NotNull Exchange getExchangeWithLargeStreamRequest(String body) throws Exception {
        Request request = Request.post("/foo").header("dummy", "foo").build();
        request.setBody(new Body(new ByteArrayInputStream(body.getBytes())));
        Exchange exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }
}
