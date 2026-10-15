/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.router.DummyTestRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.put;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.JSONPATH;
import static com.predic8.membrane.core.lang.ExchangeExpression.Language.SPEL;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyInterceptorTest {

    private IdempotencyInterceptor i;

    @BeforeEach
    void setup() {
        i = new IdempotencyInterceptor();
        i.setLanguage(JSONPATH);
        i.setKey("$.id");
        i.init(new DummyTestRouter());
    }

    @Test
    void newUniqueIdTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"abc123\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void duplicateIdTest() throws URISyntaxException {
        Exchange firstExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        Exchange secondExchange = put("").body("{\"id\": \"abc456\"}").contentType(APPLICATION_JSON).buildExchange();
        assertEquals(CONTINUE, i.handleRequest(firstExchange));
        assertEquals(ABORT, i.handleRequest(secondExchange));
        assertEquals(409, secondExchange.getResponse().getStatusCode());
    }

    @Test
    void productionShowsDetailForDuplicate() throws Exception {
        final var pi = new IdempotencyInterceptor();
        pi.setLanguage(JSONPATH);
        pi.setKey("$.id");
        pi.init(DummyTestRouter.productionRouter());

        assertEquals(CONTINUE, pi.handleRequest(put("").body("{\"id\": \"abc789\"}").contentType(APPLICATION_JSON).buildExchange()));
        final var duplicate = put("").body("{\"id\": \"abc789\"}").contentType(APPLICATION_JSON).buildExchange();
        assertEquals(ABORT, pi.handleRequest(duplicate));

        assertEquals(409, duplicate.getResponse().getStatusCode());
        final var json = new ObjectMapper().readTree(duplicate.getResponse().getBodyAsStringDecoded());
        assertEquals("Idempotency key has already been processed.", json.get("detail").asText());
    }

    @Test
    void uniqueIdsTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"789\"}").contentType(APPLICATION_JSON).buildExchange()));
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"id\": \"987\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void whenIdIsMissingTest() throws URISyntaxException {
        assertEquals(CONTINUE, i.handleRequest(put("").body("{\"msg\": \"Hello World!\"}").contentType(APPLICATION_JSON).buildExchange()));
    }

    @Test
    void concurrentDuplicatesOnlyOnePasses() throws Exception {
        final var threads = 8;
        try (final var executor = Executors.newFixedThreadPool(threads)) {
            for (var round = 0; round < 200; round++) {
                final var key = "c" + round;
                final var start = new CountDownLatch(1);
                final var calls = new ArrayList<Callable<Outcome>>();
                for (var t = 0; t < threads; t++) {
                    final var exc = put("").body("{\"id\": \"%s\"}".formatted(key)).contentType(APPLICATION_JSON).buildExchange();
                    calls.add(() -> {
                        start.await();
                        return i.handleRequest(exc);
                    });
                }
                final var futures = calls.stream().map(executor::submit).toList();
                start.countDown();
                var passed = 0;
                for (final var future : futures) {
                    if (future.get() == CONTINUE)
                        passed++;
                }
                assertEquals(1, passed, "Requests with key %s that passed".formatted(key));
            }
        }
    }

    @Test
    void defaultLanguageTest() {
        assertEquals(SPEL, new IdempotencyInterceptor().getLanguage());
    }
}
