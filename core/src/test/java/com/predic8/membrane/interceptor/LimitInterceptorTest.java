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

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import org.jetbrains.annotations.*;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static org.junit.jupiter.api.Assertions.*;

class LimitInterceptorTest {

    public static final String SMALL = "small";
    public static final String LARGE = "large message body";
    static LimitInterceptor li;

    @BeforeAll
    public static void before() {
        li = new LimitInterceptor();
        li.setMaxBodyLength(10);
        li.setAppliedFlow(REQUEST_FLOW);
    }

    @Test
    void small() {
        assertDoesNotThrow(() -> li.handleRequest(Request.post("/foo").body(SMALL).buildExchange()));
    }

    @Test
    void large() {
        assertThrows(Exception.class, () -> li.handleRequest(Request.post("/foo").body(LARGE).buildExchange()));
    }

    @Test
    void streamSmall() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(SMALL);
        li.handleRequest(exc);
        assertEquals(SMALL, exc.getRequest().getBodyAsStringDecoded());
    }

    @Test
    void streamLarge() throws Exception {
        Exchange exc = getExchangeWithLargeStreamRequest(LARGE);
        li.handleRequest(exc);
        assertThrows(Exception.class, () -> exc.getRequest().readBody());
    }

    @Test
    void streamSmallClosesTheDecodedStream() throws Exception {
        final var request = new CloseTrackingRequest("text/plain", SMALL);
        final var exc = exchangeOf(request);
        li.handleRequest(exc);
        request.readBody();
        assertTrue(request.allStreamsClosed());
    }

    @Test
    void readingAfterTheEndOfTheDecodedStreamReturnsTheEndAgain() throws Exception {
        // The decoder is closed at the end of input; reading once more must not reach it
        final var stream = li.new LengthLimitingStream(new CloseTrackingRequest("text/plain", SMALL).getBodyAsStreamDecoded());
        assertEquals(SMALL, new String(stream.readAllBytes()));
        assertEquals(-1, stream.read());
        assertEquals(-1, stream.read(new byte[1]));
        assertEquals(-1, stream.read(new byte[1], 0, 1));
        assertEquals(0, stream.read(new byte[0]));
        assertEquals(0, stream.read(new byte[1], 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> stream.read(new byte[1], 1, 1));
        assertEquals(0, stream.skip(1));
        assertEquals(0, stream.available());
    }

    @Test
    void streamLargeClosesTheDecodedStream() throws Exception {
        final var request = new CloseTrackingRequest("text/plain", LARGE);
        final var exc = exchangeOf(request);
        li.handleRequest(exc);
        assertThrows(Exception.class, request::readBody);
        assertTrue(request.allStreamsClosed());
    }

    @Test
    void truncatedGzipClosesTheDecodedStream() throws Exception {
        final var limit = new LimitInterceptor();
        limit.setMaxBodyLength(1000);
        final var request = new CloseTrackingRequest("text/plain", "a".repeat(100));
        final var gzip = request.getBody().getContent();
        request.setBody(new Body(Arrays.copyOf(gzip, gzip.length - 8)));
        final var exc = exchangeOf(request);
        limit.handleRequest(exc);
        assertThrows(ReadingBodyException.class, request::readBody);
        assertTrue(request.allStreamsClosed());
    }

    private static Exchange exchangeOf(Request request) {
        final var exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }

    private static @NotNull Exchange getExchangeWithLargeStreamRequest(String body) throws Exception {
        Request request = Request.post("/foo").header("dummy", "foo").build();
        request.setBody(new Body(new ByteArrayInputStream(body.getBytes())));
        Exchange exc = new Exchange(null);
        exc.setRequest(request);
        return exc;
    }
}