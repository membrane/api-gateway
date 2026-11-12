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
package com.predic8.membrane.core.interceptor.administration.v1;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeFilterTest {

    @Test
    void emptyFilterMatchesEverything() throws Exception {
        assertTrue(filter(Map.of()).test(exchange(200)));
    }

    @Test
    void exactStatusCode() throws Exception {
        final var f = filter(Map.of("statusCode", List.of("404")));
        assertTrue(f.test(exchange(404)));
        assertFalse(f.test(exchange(400)));
    }

    @Test
    void statusClass() throws Exception {
        final var f = filter(Map.of("statusCode", List.of("5xx")));
        assertTrue(f.test(exchange(503)));
        assertFalse(f.test(exchange(404)));
    }

    @Test
    void rejectsMalformedStatusCode() {
        assertThrows(ApiException.class, () -> filter(Map.of("statusCode", List.of("4x"))));
        assertThrows(ApiException.class, () -> filter(Map.of("statusCode", List.of("600"))));
    }

    @Test
    void headerFilterIsCaseInsensitiveSubstring() throws Exception {
        final var f = filter(Map.of("header", List.of("x-tenant:ACME")));
        assertTrue(f.test(exchange(200)));
        assertFalse(filter(Map.of("header", List.of("X-Tenant:other"))).test(exchange(200)));
    }

    @Test
    void headerFilterNeedsNameAndValue() {
        assertThrows(ApiException.class, () -> filter(Map.of("header", List.of("novalue"))));
    }

    @Test
    void bodyFilter() throws Exception {
        assertTrue(filter(Map.of("body", List.of("HELLO"))).test(exchange(200)));
        assertFalse(filter(Map.of("body", List.of("bye"))).test(exchange(200)));
    }

    @Test
    void timeRange() throws Exception {
        final var exc = exchange(200);
        assertTrue(filter(Map.of("from", List.of("2000-01-01T00:00:00Z"))).test(exc));
        assertFalse(filter(Map.of("to", List.of("2000-01-01T00:00:00Z"))).test(exc));
        assertThrows(ApiException.class, () -> filter(Map.of("from", List.of("yesterday"))));
    }

    @Test
    void textSearchesPath() throws Exception {
        assertTrue(filter(Map.of("q", List.of("ORDERS"))).test(exchange(200)));
    }

    @Test
    void persistentStoresCannotUseNewFilters() {
        assertFalse(filter(Map.of("statusCode", List.of("404"), "method", List.of("GET"))).needsInMemoryFiltering());
        assertTrue(filter(Map.of("statusCode", List.of("4xx"))).needsInMemoryFiltering());
        assertTrue(filter(Map.of("body", List.of("x"))).needsInMemoryFiltering());
    }

    private static ExchangeFilter filter(Map<String, List<String>> query) {
        return ExchangeFilter.of(new ApiRequest(null, List.of(), query), false);
    }

    private static Exchange exchange(int status) throws Exception {
        final var exc = Request.post("/orders").header("X-Tenant", "acme").body("hello").buildExchange();
        exc.setResponse(Response.statusCode(status).build());
        return exc;
    }
}
