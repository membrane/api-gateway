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
package com.predic8.membrane.core.interceptor.administration;

import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static com.predic8.membrane.core.http.Header.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminConsoleInterceptorTest {

    private TestRouter router;
    private AdminConsoleInterceptor console;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        router.init();
        console = new AdminConsoleInterceptor();
        console.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    /**
     * The new console has no inline script, so a strict policy costs nothing and limits the damage of any markup that
     * slips through escaping.
     */
    @ParameterizedTest
    @ValueSource(strings = {"/console/", "/console/index.html", "/console/assets/index.js"})
    void consoleResponsesCarryContentSecurityPolicy(String path) throws Exception {
        final var exc = handle(path);

        final var csp = exc.getResponse().getHeader().getFirstValue(CONTENT_SECURITY_POLICY);
        assertNotNull(csp);
        assertTrue(csp.contains("script-src 'self'"), csp);
        assertFalse(csp.contains("unsafe-inline"), csp);
        assertTrue(csp.contains("frame-ancestors 'none'"), csp);
        assertEquals("nosniff", exc.getResponse().getHeader().getFirstValue(X_CONTENT_TYPE_OPTIONS));
    }

    /**
     * The previous console relies on inline script, so the strict policy would break it.
     */
    @Test
    void previousConsoleIsNotRestricted() throws Exception {
        assertNull(handle("/admin").getResponse().getHeader().getFirstValue(CONTENT_SECURITY_POLICY));
    }

    private Exchange handle(String path) throws Exception {
        final var exc = Request.get(path).buildExchange();
        exc.getDestinations().add(path);
        console.handleRequest(exc);
        assertNotNull(exc.getResponse(), path);
        return exc;
    }
}
