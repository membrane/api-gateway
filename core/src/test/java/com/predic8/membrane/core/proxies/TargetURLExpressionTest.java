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
package com.predic8.membrane.core.proxies;

import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.router.*;
import org.junit.jupiter.api.*;

import java.net.*;

import static com.predic8.membrane.core.http.Request.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that expressions in the target URL are evaluated correctly.
 */
class TargetURLExpressionTest {

    private DefaultRouter router;

    @BeforeEach
    void setUp() {
        router = new DefaultRouter();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void targetWithExpression() throws URISyntaxException {
        var exc = get("http://localhost:2000/").buildExchange();
        APIProxy api = new APIProxy() {{
            setTarget(new Target() {{
                setUrl("http://localhost/${1+2}");
            }});
        }};
        var di = new DispatchingInterceptor();
        exc.setProxy(api);
        api.init(router);
        di.handleRequest(exc);
        assertEquals(1, exc.getDestinations().size());
        assertEquals("http://localhost/${1+2}", exc.getDestinations().getFirst());
    }

}