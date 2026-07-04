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

package com.predic8.membrane.core.interceptor.sqlinjection;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.router.DefaultRouter;
import com.predic8.membrane.core.util.ConfigurationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.http.MimeType.APPLICATION_JSON;
import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.interceptor.sqlinjection.SqlInjectionProtectionInterceptor.OnDetect;
import static com.predic8.membrane.core.interceptor.sqlinjection.SqlInjectionProtectionInterceptor.X_PROTECTION;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the interceptor concerns: flow handling, the block/warn decision and the rejection response.
 * Detection itself is covered by {@link SqlInjectionProtectionTest}.
 */
class SqlInjectionProtectionInterceptorTest {

    private static SqlInjectionProtectionInterceptor interceptor;

    @BeforeAll
    static void setUp() {
        interceptor = new SqlInjectionProtectionInterceptor();
        interceptor.init(new DefaultRouter());
    }

    @Test
    void maliciousRequestBlockedWith400() throws Exception {
        Exchange exc = get("/search?q=%27%20UNION%20SELECT%20pw%20FROM%20users").buildExchange();
        assertEquals(ABORT, interceptor.handleRequest(exc));
        assertEquals(400, exc.getResponse().getStatusCode());
        assertNotNull(exc.getResponse().getHeader().getFirstValue(X_PROTECTION));
    }

    @Test
    void maliciousResponseBlockedWith502() throws Exception {
        Exchange exc = get("/users").buildExchange();
        exc.setResponse(Response.ok().contentType(APPLICATION_JSON)
                .body("{\"error\":\"near information_schema.tables: syntax error\"}").build());
        assertEquals(ABORT, interceptor.handleResponse(exc));
        assertEquals(502, exc.getResponse().getStatusCode());
        assertNotNull(exc.getResponse().getHeader().getFirstValue(X_PROTECTION));
    }

    @Test
    void benignRequestContinues() throws Exception {
        Exchange exc = get("/search?q=garden+furniture&page=2").buildExchange();
        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    void benignResponseContinues() throws Exception {
        Exchange exc = get("/users").buildExchange();
        exc.setResponse(Response.ok().contentType(APPLICATION_JSON)
                .body("{\"users\":[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]}").build());
        assertEquals(CONTINUE, interceptor.handleResponse(exc));
    }

    @Test
    void warnModeDoesNotBlock() throws Exception {
        SqlInjectionProtectionInterceptor warn = new SqlInjectionProtectionInterceptor();
        warn.setOnDetect(OnDetect.WARN);
        warn.init(new DefaultRouter());

        Exchange exc = get("/search?q=%27%20UNION%20SELECT%20pw%20FROM%20users").buildExchange();
        assertEquals(CONTINUE, warn.handleRequest(exc));
    }

    @Test
    void invalidLevelRejected() {
        SqlInjectionProtectionInterceptor i = new SqlInjectionProtectionInterceptor();
        i.setLevel(5);
        assertThrows(ConfigurationException.class, () -> i.init(new DefaultRouter()));
    }
}
