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

package com.predic8.membrane.core.interceptor.cache;

import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.cache.CacheInterceptor.InMemoryStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.test.TestAppender.captureLog;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheInterceptorTest {

    private CacheInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new CacheInterceptor();
        interceptor.setStore(new InMemoryStore());
    }

    @Test
    void uncacheableRequestWarningDoesNotLogHeaderValues() throws Exception {
        final var exc = get("/foo?token=secret-query")
                .header("Authorization", "Bearer secret-token")
                .header("Unsupported", "value")
                .buildExchange();
        exc.getDestinations().add("http://localhost/foo");

        final var log = captureLog(() -> interceptor.handleRequest(exc));

        assertTrue(log.contains("'Unsupported' header"), log.getMessages().toString());
        assertFalse(log.contains("secret"), log.getMessages().toString());
    }

    @Test
    void uncacheableResponseWarningDoesNotLogHeaderValues() throws Exception {
        final var exc = get("/foo").buildExchange();
        exc.getDestinations().add("http://localhost/foo");
        exc.setResponse(Response.ok().header("Set-Cookie", "SESSION=secret-cookie").header("Unsupported", "value").build());

        final var log = captureLog(() -> interceptor.handleResponse(exc));

        assertTrue(log.contains("'Unsupported' header"), log.getMessages().toString());
        assertFalse(log.contains("secret"), log.getMessages().toString());
    }
}
