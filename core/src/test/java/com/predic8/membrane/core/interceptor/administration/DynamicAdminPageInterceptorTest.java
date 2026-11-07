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

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.balancer.Balancer;
import com.predic8.membrane.core.interceptor.balancer.BalancerUtil;
import com.predic8.membrane.core.interceptor.balancer.LoadBalancingInterceptor;
import com.predic8.membrane.core.proxies.ProxyRule;
import com.predic8.membrane.core.proxies.ProxyRuleKey;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.router.TestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.balancer.Balancer.DEFAULT_NAME;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.UP;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.MANUAL;
import static org.junit.jupiter.api.Assertions.*;

class DynamicAdminPageInterceptorTest {

    private static final String DELETE_VICTIM = "/admin/service-proxy/delete?name=victim%3A3999";

    private TestRouter router;
    private DynamicAdminPageInterceptor interceptor;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        router.init();

        final var victim = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 3999, null), "localhost", 80);
        victim.setName("victim");
        victim.init(router);
        router.getRuleManager().addProxy(victim, MANUAL);

        interceptor = new DynamicAdminPageInterceptor();
        interceptor.init(router);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/service-proxy/save?name=evil&method=*&port=3998&targetHost=attacker.example.com&targetPort=80",
            "/admin/proxy/save?name=evil&port=3998",
            "/admin/service-proxy/delete?name=victim%3A3999",
            "/admin/service-proxy/start?name=victim%3A3999",
            "/admin/proxy/delete?name=victim%3A3999",
            "/admin/clusters/save?name=c1",
            "/admin/node/save?cluster=c1&host=localhost&port=8080",
            "/admin/node/up?cluster=c1&host=localhost&port=8080",
            "/admin/node/takeout?cluster=c1&host=localhost&port=8080",
            "/admin/node/down?cluster=c1&host=localhost&port=8080",
            "/admin/node/delete?cluster=c1&host=localhost&port=8080",
            "/admin/node/reset?cluster=c1&host=localhost&port=8080",
    })
    void stateChangingRequestsRejectGet(String pathQuery) throws Exception {
        final var exc = dispatch(Request.get(pathQuery));
        assertEquals(405, exc.getResponse().getStatusCode());
        assertEquals("POST", exc.getResponse().getHeader().getFirstValue(ALLOW));
        assertNotNull(victim());
    }

    @Test
    void crossSiteGetNavigationDoesNotDeleteProxy() throws Exception {
        dispatch(Request.get(DELETE_VICTIM).header(SEC_FETCH_SITE, "cross-site"));
        assertNotNull(victim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cross-site", "same-site"})
    void crossOriginPostIsForbidden(String secFetchSite) throws Exception {
        final var exc = dispatch(Request.post(DELETE_VICTIM).header(SEC_FETCH_SITE, secFetchSite));
        assertEquals(403, exc.getResponse().getStatusCode());
        assertNotNull(victim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-origin", "none"})
    void sameOriginPostDeletesProxy(String secFetchSite) throws Exception {
        final var exc = dispatch(Request.post(DELETE_VICTIM).header(SEC_FETCH_SITE, secFetchSite));
        assertEquals(200, exc.getResponse().getStatusCode());
        assertNull(victim());
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://attacker.example.com", "null", "not a uri"})
    void postWithForeignOriginIsForbidden(String origin) throws Exception {
        final var exc = dispatch(Request.post(DELETE_VICTIM).header(HOST, "localhost:9000").header(ORIGIN, origin));
        assertEquals(403, exc.getResponse().getStatusCode());
        assertNotNull(victim());
    }

    @Test
    void postWithMatchingOriginDeletesProxy() throws Exception {
        dispatch(Request.post(DELETE_VICTIM).header(HOST, "localhost:9000").header(ORIGIN, "http://localhost:9000"));
        assertNull(victim());
    }

    @Test
    void postWithoutBrowserHeadersDeletesProxy() throws Exception {
        dispatch(Request.post(DELETE_VICTIM));
        assertNull(victim());
    }

    @Test
    void crossSiteGetOfReadOnlyPageIsAllowed() throws Exception {
        final var exc = dispatch(Request.get("/admin/proxy").header(SEC_FETCH_SITE, "cross-site"));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @Test
    void proxyDeleteIsRenderedAsPostForm() throws Exception {
        final var proxy = new ProxyRule(new ProxyRuleKey(3997, null));
        proxy.setName("p");
        proxy.init(router);
        router.getRuleManager().addProxy(proxy, MANUAL);

        final var body = dispatch(Request.get("/admin/proxy")).getResponse().getBodyAsStringDecoded();
        assertTrue(body.contains("<form class=\"mb-post-action\" action=\"../admin/proxy/delete?name=p%3A3997\" method=\"POST\">"), body);
        assertFalse(body.matches("(?s).*href=\"[^\"]*/admin/proxy/delete.*"), body);
    }

    /**
     * The node actions change a node's status. Given an unknown host, they used to add it to the cluster, so the
     * balancer sent traffic there, even when the console was readOnly.
     */
    @ParameterizedTest
    @ValueSource(strings = {"up", "down", "takeout", "reset"})
    void nodeActionsDoNotAddUnknownNodes(String action) throws Exception {
        final var balancer = addBalancer();

        final var exc = dispatch(Request.post("/admin/node/%s?cluster=%s&host=attacker.example.com&port=80".formatted(action, DEFAULT_NAME)));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertTrue(balancer.getAllNodesByCluster(DEFAULT_NAME).isEmpty());
    }

    @Test
    void nodeActionsDoNotAddUnknownClusters() throws Exception {
        final var balancer = addBalancer();

        final var exc = dispatch(Request.post("/admin/node/up?cluster=evil&host=attacker.example.com&port=80"));

        assertEquals(404, exc.getResponse().getStatusCode());
        assertTrue(balancer.getClusters().stream().noneMatch(c -> "evil".equals(c.getName())));
    }

    @Test
    void nodeUpChangesStatusOfExistingNode() throws Exception {
        final var balancer = addBalancer();
        balancer.down(DEFAULT_NAME, "localhost", 8080);

        dispatch(Request.post("/admin/node/up?cluster=%s&host=localhost&port=8080".formatted(DEFAULT_NAME)));

        assertEquals(UP, balancer.getNode(DEFAULT_NAME, "localhost", 8080).getStatus());
    }

    private Balancer addBalancer() {
        final var lb = new LoadBalancingInterceptor();
        lb.setName(DEFAULT_NAME);
        final var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 3996, null), "localhost", 80);
        proxy.getFlow().add(lb);
        proxy.init(router);
        router.getRuleManager().addProxy(proxy, MANUAL);
        return BalancerUtil.lookupBalancer(router, DEFAULT_NAME);
    }

    private Exchange dispatch(Request.Builder builder) throws Exception {
        final var exc = builder.buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        interceptor.handleRequest(exc);
        return exc;
    }

    private Object victim() {
        return RuleUtil.findRuleByIdentifier(router, "victim:3999");
    }
}
