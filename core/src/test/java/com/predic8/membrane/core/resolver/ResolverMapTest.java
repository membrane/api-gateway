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

package com.predic8.membrane.core.resolver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Set;

import static com.predic8.membrane.core.resolver.HTTPSchemaResolverTest.clientAnswering;
import static com.predic8.membrane.core.util.ThreadTestUtil.assertNoTimerThreadStartedSince;
import static com.predic8.membrane.core.util.ThreadTestUtil.liveTimerThreads;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ResolverMapTest {

    @Test
    void urlWithoutPathAndRelativePath() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo?a=foo");
        assertEquals("http://localhost:2000/foo?a=foo", s);
    }

    @Test
    void schemeInsideQueryIsNotTreatedAsChildScheme() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo?a=http://dummy");
        assertEquals("http://localhost:2000/foo?a=http://dummy", s);
    }

    @Test
    void childWithOwnSchemeReplacesParent() {
        var s = ResolverMap.combine("http://localhost:2000/foo", "http://other:3000/bar");
        assertEquals("http://other:3000/bar", s);
    }

    @Test
    void relativeChildResolvesAgainstParentDirectory() {
        var s = ResolverMap.combine("http://localhost:2000/a/b.xml", "c.xml");
        assertEquals("http://localhost:2000/a/c.xml", s);
    }

    @Test
    void absoluteChildReplacesParentPath() {
        var s = ResolverMap.combine("http://localhost:2000/a/b.xml", "/c.xml");
        assertEquals("http://localhost:2000/c.xml", s);
    }

    @Test
    void emptyParentReturnsChild() {
        var s = ResolverMap.combine("", "/foo");
        assertEquals("/foo", s);
    }

    @Test
    void classpathParentWithRelativeChild() {
        var s = ResolverMap.combine("classpath:/a/b.xml", "c.xml");
        assertEquals("classpath:/a/c.xml", s);
    }

    @Test
    void internalParentWithRelativeChild() {
        var s = ResolverMap.combine("internal:/a/b.xml", "c.xml");
        assertEquals("internal:/a/c.xml", s);
    }

    @Test
    void moreThanTwoLocationsAreFoldedLeftToRight() {
        var s = ResolverMap.combine("http://localhost:2000", "/a/", "b.xml");
        assertEquals("http://localhost:2000/a/b.xml", s);
    }

    @Test
    void spaceInChildIsEncoded() {
        var s = ResolverMap.combine("http://localhost:2000", "/foo bar");
        assertEquals("http://localhost:2000/foo%20bar", s);
    }

    /**
     * #3483: the no-arg ResolverMap must not create its HTTP client, and with it a timer thread, unless an HTTP URL is
     * resolved.
     */
    @Test
    void noArgResolverMapStartsNoTimerThread() throws Exception {
        final var before = liveTimerThreads();

        try (final var ignored = new ResolverMap().resolve("classpath:/customer.xml")) {
            final var started = liveTimerThreads();
            started.removeAll(before);
            assertEquals(Set.of(), started);
        }
    }

    /**
     * #3483: the no-arg ResolverMap uses a client per HTTP resolve and closes it before resolve() returns, so no timer
     * thread outlives the call.
     */
    @Test
    void httpResolveLeavesNoTimerThread() throws Exception {
        final var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            final var body = "<schema/>".getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (final var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            final var before = liveTimerThreads();

            try (final var in = new ResolverMap().resolve("http://localhost:%d/a.xsd".formatted(server.getAddress().getPort()))) {
                assertEquals("<schema/>", new String(in.readAllBytes(), UTF_8));
            }

            assertNoTimerThreadStartedSince(before);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolveLeavesCallerOwnedClientOpen() throws Exception {
        final var client = clientAnswering("<schema/>");
        new ResolverMap(client, null).resolve("http://localhost/a.xsd").close();

        verify(client, never()).close();
        verify(client, never()).closeImmediately();
    }
}
