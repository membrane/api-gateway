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
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.FlowController;
import com.predic8.membrane.core.interceptor.LimitInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.exception.ExceptionUtils.throwableOfType;
import static org.junit.jupiter.api.Assertions.*;

class LimitInterceptorTest {

    public static final String SMALL = "small";
    public static final String LARGE = "large message body";
    private static final int LIMIT = 10;
    private static final String NEXT_REQUEST = "GET /next HTTP/1.1\r\n\r\n";
    private static final String NEXT_RESPONSE = "HTTP/1.1 200 OK\r\n\r\n";
    static LimitInterceptor li;

    @BeforeAll
    public static void before() {
        li = new LimitInterceptor();
        li.setMaxBodyLength(LIMIT);
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

    @Test
    void emptyBodyUsesConfiguredLimitForDeclaredLength() throws Exception {
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(5_000_000_000L);
        limit.init(new DummyTestRouter());
        final var exc = Request.get("/foo").buildExchange();
        final var response = Response.ok().build();
        response.getHeader().removeFields(CONTENT_LENGTH);
        response.getHeader().setContentLength(3_000_000_000L);
        response.setBody(new EmptyBody());
        exc.setResponse(response);
        assertEquals(CONTINUE, limit.handleResponse(exc));
        assertEquals(3_000_000_000L, response.getHeader().getContentLength());
        response.getHeader().setContentLength(6_000_000_000L);
        assertEquals(ABORT, limit.handleResponse(exc));
    }

    @Test
    void messageWithoutBodyIsLeftAlone() throws Exception {
        final var exc = Request.get("/foo").buildExchange();
        assertEquals(CONTINUE, li.handleRequest(exc));
        assertFalse(exc.getRequest().getHeader().hasContentLength());
    }

    /**
     * A plain body never reads past its Content-Length, so a declared length within the limit is
     * enough: the body is passed on unread and can still be streamed.
     */
    @Test
    void plainBodyWithinDeclaredLengthIsPassedOnUnread() throws Exception {
        final var connection = new ByteArrayInputStream(concat(SMALL.getBytes(UTF_8), NEXT_REQUEST.getBytes(US_ASCII)));
        final var request = Request.post("/foo").header(CONTENT_LENGTH, String.valueOf(SMALL.length())).build();
        final var body = new Body(connection, SMALL.length());
        request.setBody(body);
        final var exc = new Exchange(null);
        exc.setRequest(request);

        assertEquals(CONTINUE, li.handleRequest(exc));
        assertSame(body, exc.getRequest().getBody());
        assertFalse(body.isRead());
        assertEquals(SMALL.length(), exc.getRequest().getHeader().getContentLength());
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
        assertEquals(NEXT_REQUEST, new String(connection.readAllBytes(), US_ASCII));
    }

    /**
     * Nothing is held in memory on the pass-through path, so a body larger than an array can be is fine.
     */
    @Test
    void declaredLengthAboveLargestArrayWithinLimitIsPassedOn() throws Exception {
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(5_000_000_000L);
        limit.init(new DummyTestRouter());
        final var exc = Request.post("/foo").header(CONTENT_LENGTH, "3000000000").buildExchange();
        final var body = new Body(new ByteArrayInputStream(new byte[0]), 3_000_000_000L);
        exc.getRequest().setBody(body);
        assertEquals(CONTINUE, limit.handleRequest(exc));
        assertSame(body, exc.getRequest().getBody());
    }

    /**
     * The Content-Length header does not bound a chunked body: Transfer-Encoding wins.
     */
    @Test
    void chunkedBodyWithSmallContentLengthHeaderIsLimited() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(LARGE.getBytes(UTF_8))), null);
        exc.getRequest().getHeader().setContentLength(SMALL.length());
        assertFailsWhenStreamed(exc.getRequest(), li.handleRequest(exc));
    }

    /**
     * Only the body's own declared length bounds what is read, not a Content-Length header beside it.
     */
    @Test
    void unframedBodyWithSmallContentLengthHeaderIsLimited() throws Exception {
        final var exc = getExchangeWithLargeStreamRequest(LARGE);
        exc.getRequest().getHeader().setContentLength(SMALL.length());
        assertFailsWhenStreamed(exc.getRequest(), li.handleRequest(exc));
    }

    @Test
    void streamSmall() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(SMALL);
        li.handleRequest(exc);
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void streamLarge() throws Exception {
        final var exc = getExchangeWithLargeStreamRequest(LARGE);
        assertFailsWhenStreamed(exc.getRequest(), li.handleRequest(exc));
    }

    @Test
    void streamLargeFailsWhenRead() throws Exception {
        final var exc = getExchangeWithLargeStreamRequest(LARGE);
        assertEquals(CONTINUE, li.handleRequest(exc));
        assertLimitExceeded(assertThrows(ReadingBodyException.class, () -> exc.getRequest().getBodyAsStringDecoded()));
        assertTrue(exc.getRequest().getBody().hasFailed());
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
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(LARGE.getBytes(UTF_8))), null);
        final var original = exc.getRequest().getBody();
        assertFailsWhenStreamed(exc.getRequest(), li.handleRequest(exc));
        // the rest of the body is still on the connection, which must therefore not be reused
        assertTrue(original.hasFailed());
    }

    @Test
    void chunkedDeflateLarge() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(deflate(LARGE))), "deflate");
        final var original = exc.getRequest().getBody();
        assertFailsWhenStreamed(exc.getRequest(), li.handleRequest(exc));
        assertTrue(original.hasFailed());
    }

    /**
     * A response cannot be rejected cleanly once it is streamed: the transfer is cut off, and the backend
     * connection, which still holds the rest of the body, is closed instead of going back to the pool.
     */
    @Test
    void largeChunkedResponseFailsAndConnectionIsNotReused() throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(LARGE.getBytes(UTF_8)), NEXT_RESPONSE.getBytes(US_ASCII)));
        final var response = Response.ok().header(TRANSFER_ENCODING, "chunked").build();
        final var original = new ChunkedBody(connection);
        response.setBody(original);
        final var exc = Request.get("/foo").buildExchange();
        exc.setResponse(response);
        assertFailsWhenStreamed(response, li.handleResponse(exc));
        assertTrue(original.hasFailed());
        assertFalse(exc.canKeepConnectionAlive());
    }

    /**
     * Retaining a copy, e.g. for a retry, reads the body first, so the limit is hit before anything is
     * written.
     */
    @Test
    void retainedCopyIsLimited() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(gzip(LARGE))), "gzip");
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var out = new CollectingTransferer();
        assertLimitExceeded(assertThrows(ReadingBodyException.class, () -> exc.getRequest().getBody().write(out, true)));
        assertEquals(0, out.content.size());
    }

    @Test
    void retainedCopyWithinLimit() throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(gzip(SMALL)), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc = chunkedExchange(connection, "gzip");
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var out = new CollectingTransferer();
        exc.getRequest().getBody().write(out, true);
        assertEquals(SMALL, out.content.toString(UTF_8));
        assertTrue(out.finished);
        assertEquals(NEXT_REQUEST, new String(connection.readAllBytes(), US_ASCII));
    }

    /**
     * A body nobody consumed, e.g. because a later plugin answered the request, is drained up to the end
     * of the wrapped body, so the connection stays at the next request.
     */
    @Test
    void discardingUnreadBodyDrainsTheOriginal() throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(deflate(SMALL)), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc = chunkedExchange(connection, "deflate");
        assertEquals(CONTINUE, li.handleRequest(exc));
        exc.getRequest().discardBody();
        assertEquals(NEXT_REQUEST, new String(connection.readAllBytes(), US_ASCII));
        assertFalse(exc.getRequest().getBody().hasFailed());
    }

    /**
     * The transfer broke off, e.g. because the receiver went away, so the rest of the body is still on the
     * connection. Discarding the body afterwards must mark it failed rather than throw.
     */
    @Test
    void discardAfterInterruptedTransferFailsTheBody() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(LARGE.getBytes(UTF_8))), null);
        final var original = exc.getRequest().getBody();
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(100);
        limit.init(new DummyTestRouter());
        assertEquals(CONTINUE, limit.handleRequest(exc));
        final var out = new CollectingTransferer() {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                throw new IOException("Connection reset");
            }
        };
        assertThrows(WritingBodyException.class, () -> exc.getRequest().getBody().write(out, false));

        assertDoesNotThrow(() -> exc.getRequest().discardBody());
        assertTrue(exc.getRequest().getBody().hasFailed());
        assertTrue(original.hasFailed());
    }

    /**
     * A plugin after <code>limit</code> replaces a body that is too large. The rest of that body is still on
     * the connection, so the connection must not be reused, although the new body is fine.
     */
    @Test
    void replacingBodyAboveLimitKeepsConnectionFromBeingReused() throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(LARGE.getBytes(UTF_8)), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc = chunkedExchange(connection, null);
        exc.setResponse(Response.ok().build());
        assertEquals(CONTINUE, li.handleRequest(exc));

        exc.getRequest().setBodyContent("x".getBytes(UTF_8));

        assertFalse(exc.canKeepConnectionAlive());
    }

    /**
     * The wrapped body gets exactly one terminal event, as that is what releases or closes the connection
     * it is read from.
     */
    @Test
    void originalBodyCompletesOnceWithinLimit() throws Exception {
        for (final var encoding : new String[]{null, "gzip", "deflate"}) {
            for (final var streamed : new boolean[]{true, false}) {
                final var content = encoding == null ? SMALL.getBytes(UTF_8) : "gzip".equals(encoding) ? gzip(SMALL) : deflate(SMALL);
                final var exc = chunkedExchange(new ByteArrayInputStream(chunked(content)), encoding);
                final var observer = new CountingObserver();
                exc.getRequest().addObserver(observer);
                assertEquals(CONTINUE, li.handleRequest(exc));
                consume(exc.getRequest(), streamed);
                assertEquals(1, observer.completed, "%s, streamed: %s".formatted(encoding, streamed));
                assertEquals(0, observer.failed, "%s, streamed: %s".formatted(encoding, streamed));
            }
        }
    }

    @Test
    void originalBodyFailsOnceAboveLimit() throws Exception {
        for (final var encoding : new String[]{null, "gzip", "deflate"}) {
            for (final var streamed : new boolean[]{true, false}) {
                final var content = encoding == null ? LARGE.getBytes(UTF_8) : "gzip".equals(encoding) ? gzip(LARGE) : deflate(LARGE);
                final var exc = chunkedExchange(new ByteArrayInputStream(chunked(content)), encoding);
                final var observer = new CountingObserver();
                exc.getRequest().addObserver(observer);
                assertEquals(CONTINUE, li.handleRequest(exc));
                assertThrows(ReadingBodyException.class, () -> consume(exc.getRequest(), streamed));
                assertEquals(0, observer.completed, "%s, streamed: %s".formatted(encoding, streamed));
                assertEquals(1, observer.failed, "%s, streamed: %s".formatted(encoding, streamed));
            }
        }
    }

    /**
     * <code>limit</code> passes a chunked body on and measures it while it is read. A plugin after it that
     * reads a body above the limit gets the limit's answer, not a generic body failure.
     */
    @Test
    void bodyAboveLimitReadByALaterPluginIsTooLarge() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked(LARGE.getBytes(UTF_8))), null);
        final var reader = new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exchange) {
                exchange.getRequest().getBodyAsStringDecoded();
                return CONTINUE;
            }
        };

        assertEquals(ABORT, new FlowController(new DummyTestRouter()).invokeRequestHandlers(exc, List.of(li, reader)));

        final var body = exc.getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("Message is too large."), body);
    }

    private static void consume(Message message, boolean streamed) {
        if (streamed)
            message.getBody().write(new CollectingTransferer(), false);
        else
            message.getBody().getContent();
    }

    @Test
    void truncatedChunkedBodyIsAReadFailureNotARejection() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream("5\r\nsm".getBytes(US_ASCII)), null);
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var e = assertThrows(ReadingBodyException.class, () -> exc.getRequest().getBody().write(new CollectingTransferer(), false));
        assertNull(throwableOfType(e, LimitExceededException.class));
        assertTrue(e.isRequestBodyFailure(exc));
    }

    /**
     * The Content-Encoding header is removed when the body is wrapped, yet a failure to decode still names
     * the encoding.
     */
    @Test
    void corruptGzipIsADecodingFailure() throws Exception {
        final var exc = chunkedExchange(new ByteArrayInputStream(chunked("not gzip".getBytes(UTF_8))), "gzip");
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var e = assertThrows(ReadingBodyException.class, () -> exc.getRequest().getBody().write(new CollectingTransferer(), false));
        assertEquals("gzip", throwableOfType(e, DecodingException.class).getContentEncoding());
        assertNull(throwableOfType(e, LimitExceededException.class));
        assertTrue(e.isRequestBodyFailure(exc));
    }

    /**
     * HTTP/1.0 has no chunked coding, so the body ends with the connection.
     */
    @Test
    void responseToHttp10RequestIsNotChunked() throws Exception {
        final var request = Request.get("/foo").build();
        request.setVersion("1.0");
        final var exc = new Exchange(null);
        exc.setRequest(request);
        final var response = Response.ok().header(CONTENT_ENCODING, "gzip").build();
        response.setBody(new Body(new ByteArrayInputStream(gzip(SMALL))));
        exc.setResponse(response);
        assertEquals(CONTINUE, li.handleResponse(exc));
        assertNull(response.getHeader().getFirstValue(TRANSFER_ENCODING));
        assertNull(response.getHeader().getContentEncoding());
        assertFalse(response.getHeader().hasContentLength());
        assertEquals(SMALL, response.getBodyAsStringDecoded());
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
        assertEquals(CONTINUE, limit.handleRequest(exc));
        // The truncated checksum must never be reached: fail once decoded MIME exceeds the limit.
        assertLimitExceeded(assertThrows(ReadingBodyException.class, () -> exc.getRequest().getBodyAsStringDecoded()));
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
        final var header = exc.getRequest().getHeader();
        assertEquals(type, header.getContentType());
        assertNull(header.getContentEncoding());
        // Before, a Content-Length was removed without framing the body otherwise
        assertFalse(header.hasContentLength());
        assertTrue(header.isChunked());
    }

    private static void assertRejected(Exchange exc) {
        assertEquals(ABORT, li.handleRequest(exc));
        assertTrue(exc.getResponse().getBodyAsStringDecoded().contains("Message is too large."));
    }

    /**
     * The message is passed on, and forwarding it stops once more than the limit has been written.
     */
    private static void assertFailsWhenStreamed(Message message, Outcome outcome) {
        assertEquals(CONTINUE, outcome);
        final var out = new CollectingTransferer();
        assertLimitExceeded(assertThrows(ReadingBodyException.class, () -> message.getBody().write(out, false)));
        assertTrue(out.content.size() <= LIMIT);
        assertFalse(out.finished);
        assertTrue(message.getBody().hasFailed());
    }

    private static void assertLimitExceeded(ReadingBodyException e) {
        assertNotNull(throwableOfType(e, LimitExceededException.class), () -> "Not a limit failure: " + e);
    }

    /**
     * The body is passed on decoded and chunked, read up to its last chunk and no further: the bytes after
     * it belong to the next request on the connection. Checked both when the body is streamed and when it
     * is read.
     */
    private static void assertPassesChunked(byte[] body, String contentEncoding) throws Exception {
        final var connection = new ByteArrayInputStream(concat(chunked(body), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc = chunkedExchange(connection, contentEncoding);
        assertEquals(CONTINUE, li.handleRequest(exc));
        final var header = exc.getRequest().getHeader();
        assertTrue(header.isChunked());
        assertNull(header.getContentEncoding());
        assertFalse(header.hasContentLength());
        final var out = new CollectingTransferer();
        exc.getRequest().getBody().write(out, false);
        assertEquals(SMALL, out.content.toString(UTF_8));
        assertTrue(out.finished);
        assertEquals(SMALL.length(), exc.getRequest().getBody().getLength());
        assertEquals(NEXT_REQUEST, new String(connection.readAllBytes(), US_ASCII));
        assertFalse(exc.getRequest().getBody().hasFailed());

        final var connection2 = new ByteArrayInputStream(concat(chunked(body), NEXT_REQUEST.getBytes(US_ASCII)));
        final var exc2 = chunkedExchange(connection2, contentEncoding);
        assertEquals(CONTINUE, li.handleRequest(exc2));
        assertEquals(SMALL, exc2.getRequest().getBodyAsStringDecoded());
        assertEquals(NEXT_REQUEST, new String(connection2.readAllBytes(), US_ASCII));
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

    private static final class CountingObserver extends AbstractMessageObserver {
        int completed;
        int failed;

        @Override
        public void bodyComplete(AbstractBody body) {
            completed++;
        }

        @Override
        public void bodyFailed(ReadingBodyException e) {
            failed++;
        }
    }

    /**
     * Collects the content written, without framing, and whether the transfer was finished.
     */
    private static class CollectingTransferer extends AbstractBodyTransferer {
        final ByteArrayOutputStream content = new ByteArrayOutputStream();
        boolean finished;

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            content.write(bytes, offset, length);
        }

        @Override
        public void write(Chunk chunk) throws IOException {
            content.write(chunk.content());
        }

        @Override
        public void finish(@Nullable Header trailer) {
            finished = true;
        }
    }
}
