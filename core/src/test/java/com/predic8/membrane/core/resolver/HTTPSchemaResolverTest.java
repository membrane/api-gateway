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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.predic8.membrane.core.http.Response.ok;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HTTPSchemaResolverTest {

    @Test
    void withLazyClientCreatesClientOnceOnFirstResolve() throws Exception {
        final var client = clientAnswering("<schema/>");
        final var created = new AtomicInteger();

        final var resolver = HTTPSchemaResolver.withLazyClient(() -> {
            created.incrementAndGet();
            return client;
        });
        assertEquals(0, created.get());

        try (final var in = resolver.resolve("http://localhost/a.xsd")) {
            assertEquals("<schema/>", new String(in.readAllBytes(), UTF_8));
        }
        resolver.resolve("http://localhost/b.xsd").close();

        assertEquals(1, created.get());
    }

    private static HttpClient clientAnswering(String body) throws Exception {
        final var client = mock(HttpClient.class);
        doAnswer(invocation -> {
            invocation.<Exchange>getArgument(0).setResponse(ok(body).build());
            return null;
        }).when(client).call(any());
        return client;
    }
}
