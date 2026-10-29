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

import com.predic8.membrane.core.exchangestore.LimitedMemoryExchangeStore;
import com.predic8.membrane.core.interceptor.rest.QueryParameter;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Request.post;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminRESTInterceptorTest {

    private TestRouter router;
    private LimitedMemoryExchangeStore store;
    private AdminRESTInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        store = new LimitedMemoryExchangeStore();
        router.setExchangeStore(store);
        interceptor = new AdminRESTInterceptor();
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void storedBodyIsServedAsNonExecutableDownload() throws Exception {
        final var exc = post("/foo").contentType("text/html").body("<script>alert(document.cookie)</script>").buildExchange();
        store.snap(exc, REQUEST);

        final var res = interceptor.getRequestBody(bodyParams(exc.getId(), "request"), "");

        assertEquals(200, res.getStatusCode());
        assertEquals("<script>alert(document.cookie)</script>", res.getBodyAsStringDecoded());
        assertEquals("attachment", res.getHeader().getFirstValue(CONTENT_DISPOSITION));
        assertEquals("nosniff", res.getHeader().getFirstValue(X_CONTENT_TYPE_OPTIONS));
        assertEquals("sandbox", res.getHeader().getFirstValue(CONTENT_SECURITY_POLICY));
    }

    private static QueryParameter bodyParams(long id, String direction) {
        final var matcher = Pattern.compile("/admin/rest/exchanges/(-?\\d+)/(response|request)/body")
                .matcher("/admin/rest/exchanges/%d/%s/body".formatted(id, direction));
        assertTrue(matcher.matches());
        return new QueryParameter(Map.of(), matcher);
    }
}
