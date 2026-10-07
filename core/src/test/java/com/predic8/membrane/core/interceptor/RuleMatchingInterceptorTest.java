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

package com.predic8.membrane.core.interceptor;

import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.ProxyRule;
import com.predic8.membrane.core.proxies.ProxyRuleKey;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.MANUAL;
import static com.predic8.membrane.test.TestUtil.assembleExchange;
import static org.junit.jupiter.api.Assertions.*;

class RuleMatchingInterceptorTest {

    private TestRouter router;
    private APIProxy admin;
    private APIProxy catchAll;
    private RuleMatchingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        router.init();

        admin = new APIProxy();
        admin.setName("admin");
        admin.setPath(new Path(false, "/admin"));
        admin.init(router);
        router.getRuleManager().addProxy(admin, MANUAL);

        catchAll = new APIProxy();
        catchAll.setName("catch-all");
        catchAll.init(router);
        router.getRuleManager().addProxy(catchAll, MANUAL);

        interceptor = new RuleMatchingInterceptor();
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin",
            "/./admin",
            "/asd/../admin",
            "/../admin",
            "/asd/%2e%2e/admin",
            "/asd/%2E%2e/admin",
            "/asd/.%2e/admin",
            "/asd/%2e./admin",
            "/%2e/admin",
            "/admin/x/..",
    })
    @DisplayName("Dot-segments are resolved before routing, so they cannot dodge a more specific API")
    void dotSegmentsDoNotBypassMoreSpecificApi(String uri) throws Exception {
        var exc = get(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(admin, exc.getProxy());
        assertTrue(exc.getRequest().getUri().startsWith("/admin"), exc.getRequest().getUri());
    }

    @Test
    @DisplayName("The request URI is rewritten to the path used for routing, so the flow and the backend see the same path")
    void requestUriIsCanonicalized() throws Exception {
        var exc = get("/asd/../admin/x/./y").buildExchange();

        interceptor.handleRequest(exc);

        assertEquals("/admin/x/y", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("Dot-segments in the query are left alone")
    void queryIsNotTouched() throws Exception {
        var exc = get("/asd/../admin?next=/x/../y&p=%2e%2e").buildExchange();

        interceptor.handleRequest(exc);

        assertEquals("/admin?next=/x/../y&p=%2e%2e", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("Percent-encoded dots inside a segment are not decoded")
    void encodedDotInsideSegmentIsKept() throws Exception {
        var exc = get("/file%2etxt").buildExchange();

        interceptor.handleRequest(exc);

        assertEquals("/file%2etxt", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("An encoded slash is not decoded")
    void encodedSlashIsKept() throws Exception {
        var exc = get("/asd%2f..%2fadmin").buildExchange();

        interceptor.handleRequest(exc);

        assertSame(catchAll, exc.getProxy());
        assertEquals("/asd%2f..%2fadmin", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("A path without dot-segments is unchanged")
    void plainPathIsUnchanged() throws Exception {
        var exc = get("/foo/bar?a=b").buildExchange();

        interceptor.handleRequest(exc);

        assertSame(catchAll, exc.getProxy());
        assertEquals("/foo/bar?a=b", exc.getRequest().getUri());
    }

    @ParameterizedTest
    @CsvSource({
            "http://localhost:2000/admin,       /admin",
            "HTTP://localhost/asd/../admin?x=1, /admin?x=1",
    })
    @DisplayName("An absolute-form target is routed by its path, so it cannot dodge a more specific API")
    void absoluteFormDoesNotBypassMoreSpecificApi(String uri, String expectedUri) throws Exception {
        var exc = assembleExchange("localhost", "GET", uri, "1.1", 80, "127.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(admin, exc.getProxy());
        assertEquals(expectedUri, exc.getRequest().getUri());
    }

    @Test
    @DisplayName("An absolute-form target without a path becomes /")
    void absoluteFormWithoutPath() throws Exception {
        var exc = assembleExchange("localhost", "GET", "https://localhost", "1.1", 80, "127.0.0.1");

        interceptor.handleRequest(exc);

        assertSame(catchAll, exc.getProxy());
        assertEquals("/", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("A forward proxy keeps the absolute URI it received")
    void proxyRuleKeepsAbsoluteUri() throws Exception {
        var proxyRule = new ProxyRule(new ProxyRuleKey(3013));
        proxyRule.init(router);
        router.getRuleManager().addProxy(proxyRule, MANUAL);
        var exc = assembleExchange("example.com", "GET", "http://example.com/a/../b", "1.1", 3013, "127.0.0.1");

        interceptor.handleRequest(exc);

        assertSame(proxyRule, exc.getProxy());
        assertEquals("http://example.com/a/../b", exc.getRequest().getUri());
    }
}
