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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.proxies.ProxyRule;
import com.predic8.membrane.core.proxies.ProxyRuleKey;
import com.predic8.membrane.core.proxies.STOMPProxy;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.http.Request.get;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.MANUAL;
import static com.predic8.membrane.test.TestUtil.assembleExchange;
import static org.junit.jupiter.api.Assertions.*;

class RuleMatchingInterceptorTest {

    private TestRouter router;
    private APIProxy admin;
    private APIProxy apiAdmin;
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

        apiAdmin = new APIProxy();
        apiAdmin.setName("api-admin");
        apiAdmin.setPath(new Path(false, "/api/admin"));
        apiAdmin.init(router);
        router.getRuleManager().addProxy(apiAdmin, MANUAL);

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

    @ParameterizedTest
    @ValueSource(strings = {
            "/%61dmin",
            "/adm%69n",
            "/%61%64%6d%69%6e",
            "/%61%64%6D%69%6E",
            "/%61dmin/x",
    })
    @DisplayName("Percent-encoded unreserved characters are decoded before routing, so they cannot dodge a more specific API (RFC 3986 6.2.2.2)")
    void encodedUnreservedCharactersDoNotBypassMoreSpecificApi(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(admin, exc.getProxy());
        assertTrue(exc.getRequest().getUri().startsWith("/admin"), exc.getRequest().getUri());
    }

    @ParameterizedTest
    @CsvSource({
            "/file%2etxt,     /file.txt",
            "/%7euser,        /~user",
            "/a%2Db%5Fc,      /a-b_c",
            "/%30%39,         /09",
            "/a%2fb,          /a%2fb",
            "/a%3bb,          /a%3bb",
            "/a%25b,          /a%25b",
            "/a%3fb,          /a%3fb",
            "/a%23b,          /a%23b",
            "/a%20b,          /a%20b",
            "/x?q=%61,        /x?q=%61",
    })
    @DisplayName("Only percent-encoded unreserved characters in the path are decoded, reserved and other characters and the query stay encoded")
    void onlyUnreservedCharactersInPathAreDecoded(String uri, String expectedUri) throws Exception {
        final var exc = get(uri).buildExchange();

        interceptor.handleRequest(exc);

        assertEquals(expectedUri, exc.getRequest().getUri());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/%6%31dmin",
            "/%%361dmin",
            "/%6%31%6%34min",
            "/x/%2%45%2%45/admin",
            "/a%2%46..%2%46admin",
            "/%zz/admin",
            "/admin/%6",
            "/admin%",
            "/admin%4?q=1",
    })
    @DisplayName("A path with a malformed percent-escape is rejected, since decoding around it could create a new escape like %61 or %2E")
    void malformedPercentEscapeIsRejected(String uri) {
        // The HTTP parser keeps the target as sent, so set it unparsed
        final var exc = new Request.Builder().method("GET").uri(uri).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
        assertNull(exc.getProxy());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/x/..;/admin",
            "/x/..;foo/admin",
            "/x/..;a=b;c=d/admin",
            "/x/%2e%2e;/admin",
            "/x/.%2E;/admin",
            "/x/.;/admin",
            "/x/..;",
    })
    @DisplayName("A dot-segment with path parameters is rejected, since servlet containers strip the parameters and resolve the dots")
    void dotSegmentWithPathParametersIsRejected(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin;jsessionid=1",
            "/x/a;b=c/y",
            "/x/...;/y",
            "/x/..a;/y",
            "/x?p=/..;/admin",
    })
    @DisplayName("Path parameters on other segments and in the query are accepted")
    void pathParametersOnOtherSegmentsAreAccepted(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api;x/admin",
            "/api;jsessionid=1/admin/orders",
            "/api;a=b;c=d/admin",
            "/api/admin;x",
    })
    @DisplayName("Path parameters are ignored for routing, since servlet containers strip them, but are forwarded unchanged")
    void pathParametersDoNotBypassMoreSpecificApi(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(apiAdmin, exc.getProxy());
        assertEquals(uri, exc.getRequest().getUri());
    }

    @ParameterizedTest
    @CsvSource({
            "//admin,               admin",
            "///admin,              admin",
            "//admin?x=//y,         admin",
            "/api//admin,           api-admin",
            "//api///admin/orders,  api-admin",
    })
    @DisplayName("Duplicate slashes are merged for routing, since many backends merge them, but are forwarded unchanged")
    void duplicateSlashesDoNotBypassMoreSpecificApi(String uri, String expectedApi) {
        // Request.get() would parse "//admin" as a URL with the host "admin", the HTTP parser keeps the target as sent
        final var exc = new Request.Builder().method("GET").uri(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertEquals(expectedApi, exc.getProxy().getName());
        assertEquals(uri, exc.getRequest().getUri());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/;x/admin",
            "/;/admin",
            "/api/;x/admin",
    })
    @DisplayName("An empty segment with path parameters before another segment is rejected, since servlet containers turn it into //")
    void emptySegmentWithPathParametersIsRejected(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/;jsessionid=1",
            "/app/;jsessionid=1",
            "/app/;jsessionid=1?a=b",
    })
    @DisplayName("Path parameters on a trailing empty segment, as produced by servlet URL rewriting, are accepted")
    void trailingEmptySegmentWithPathParametersIsAccepted(String uri) throws Exception {
        final var exc = get(uri).buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    @DisplayName("An encoded slash is not decoded")
    void encodedSlashIsKept() throws Exception {
        var exc = get("/asd%2f..%2fadmin").buildExchange();

        interceptor.handleRequest(exc);

        assertSame(catchAll, exc.getProxy());
        assertEquals("/asd%2f..%2fadmin", exc.getRequest().getUri());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api\\admin",
            "/admin\\",
            "/api\\..\\admin",
            "/x\\y?q=1",
    })
    @DisplayName("A backslash in the path is rejected, since some servers treat it like / and RFC 9112 3.2 forbids correcting it")
    void backslashInPathIsRejected(String uri) {
        // The HTTP parser keeps the target as sent, so set it unparsed
        final var exc = new Request.Builder().method("GET").uri(uri).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost/api\\admin",
            "http://localhost/admin\\?q=1",
    })
    @DisplayName("A backslash in the path of an absolute-form target is rejected, too")
    void backslashInPathOfAbsoluteFormIsRejected(String uri) throws Exception {
        final var exc = assembleExchange("localhost", "GET", uri, "1.1", 80, "127.0.0.1");

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("A backslash in the query is accepted, since browsers send it unencoded there")
    void backslashInQueryIsAccepted() {
        final var exc = new Request.Builder().method("GET").uri("/admin?q=a\\b").buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(admin, exc.getProxy());
        assertEquals("/admin?q=a\\b", exc.getRequest().getUri());
    }

    @Test
    @DisplayName("An encoded backslash is not decoded, it is data like an encoded slash")
    void encodedBackslashIsKept() throws Exception {
        final var exc = get("/api%5Cadmin").buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(catchAll, exc.getProxy());
        assertEquals("/api%5Cadmin", exc.getRequest().getUri());
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

    @ParameterizedTest
    @ValueSource(strings = {
            "http://protected.example/bad%zz/admin",
            "http://protected.example/bad%2/admin",
            "http://protected.example:99999/admin",
            "http://:80/admin",
    })
    @DisplayName("A malformed absolute-form target is rejected with 400")
    void malformedAbsoluteFormIsRejected(String uri) throws Exception {
        final var exc = assembleExchange("protected.example", "GET", uri, "1.1", 80, "127.0.0.1");

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "admin",
            "admin/x",
            "./admin",
            "x/../admin",
            "x:/admin",
            "http:/admin",
            "https:admin",
            "ws://example.com/admin",
            "foo:/a/../admin",
            "*",
    })
    @DisplayName("A request target that is neither a path nor an absolute http(s) URI is rejected, since the backend would get a path that was not used for routing")
    void targetThatIsNotAPathIsRejected(String uri) {
        // The HTTP parser keeps the target as sent, so set it unparsed
        final var exc = new Request.Builder().method("GET").uri(uri).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
        assertNull(exc.getProxy());
    }

    @Test
    @DisplayName("The asterisk-form is accepted for OPTIONS")
    void asteriskFormIsAcceptedForOptions() {
        final var exc = new Request.Builder().method("OPTIONS").uri("*").buildExchange();

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(catchAll, exc.getProxy());
    }

    @Test
    @DisplayName("The authority-form of CONNECT is accepted by a forward proxy")
    void connectIsAcceptedByForwardProxy() throws Exception {
        final var proxyRule = new ProxyRule(new ProxyRuleKey(3013));
        proxyRule.init(router);
        router.getRuleManager().addProxy(proxyRule, MANUAL);
        final var exc = assembleExchange("example.com:443", "CONNECT", "example.com:443", "1.1", 3013, "127.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(proxyRule, exc.getProxy());
        assertEquals("example.com:443", exc.getRequest().getUri());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com:443",
            "/admin",
    })
    @DisplayName("CONNECT is rejected unless a forward proxy gets it, since an API would open a tunnel that bypasses the flow of every API")
    void connectToApiIsRejected(String target) {
        final var exc = new Request.Builder().method("CONNECT").uri(target).buildExchange();

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("A STOMP CONNECT frame, which has no request target, is routed to a STOMP proxy")
    void stompConnectIsAccepted() throws Exception {
        final var stompProxy = new STOMPProxy();
        stompProxy.init(router);
        router.getRuleManager().addProxy(stompProxy, MANUAL);
        final var exc = assembleExchange("localhost", "CONNECT", "", "STOMP", 80, "127.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(stompProxy, exc.getProxy());
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

    @ParameterizedTest
    @ValueSource(strings = {
            "http://my_service/x",
            "http://example.com/search?q={x}",
            "http://example.com/a|b",
            "http://example.com/bad%zz",
    })
    @DisplayName("A forward proxy takes an absolute URI that cannot be parsed for routing, since it does not route by path")
    void proxyRuleAcceptsUnparsableAbsoluteUri(String uri) throws Exception {
        final var proxyRule = new ProxyRule(new ProxyRuleKey(3013));
        proxyRule.init(router);
        router.getRuleManager().addProxy(proxyRule, MANUAL);
        final var exc = assembleExchange("example.com", "GET", uri, "1.1", 3013, "127.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(proxyRule, exc.getProxy());
        assertEquals(uri, exc.getRequest().getUri());
    }

    @Test
    @DisplayName("A forward proxy does not take a malformed origin-form target")
    void proxyRuleRejectsMalformedOriginForm() throws Exception {
        final var proxyRule = new ProxyRule(new ProxyRuleKey(3013));
        proxyRule.init(router);
        router.getRuleManager().addProxy(proxyRule, MANUAL);
        final var exc = assembleExchange("example.com", "GET", "/bad%zz", "1.1", 3013, "127.0.0.1");

        assertEquals(ABORT, interceptor.handleRequest(exc));

        assertEquals(400, exc.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("A forward proxy accepts a backslash in the path, since it does not route by path")
    void proxyRuleAcceptsBackslashInPath() throws Exception {
        final var proxyRule = new ProxyRule(new ProxyRuleKey(3013));
        proxyRule.init(router);
        router.getRuleManager().addProxy(proxyRule, MANUAL);
        final var exc = assembleExchange("example.com", "GET", "http://example.com/a\\b", "1.1", 3013, "127.0.0.1");

        assertEquals(CONTINUE, interceptor.handleRequest(exc));

        assertSame(proxyRule, exc.getProxy());
        assertEquals("http://example.com/a\\b", exc.getRequest().getUri());
    }
}
