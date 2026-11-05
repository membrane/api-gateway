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

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.exchangestore.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.interceptor.balancer.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.*;
import static com.predic8.membrane.core.interceptor.balancer.Balancer.*;
import static com.predic8.membrane.core.interceptor.balancer.Node.Status.*;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminApiV1InterceptorTest {

    private static final String API = "/admin/api/v1";
    private static final String VICTIM = API + "/apis/node-a/victim%3A3999";
    private static final ObjectMapper om = new ObjectMapper();

    private static final OpenAPIValidator SPEC = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(
            OpenAPITestUtils.parseOpenAPI(AdminApiV1InterceptorTest.class.getResourceAsStream(
                    "/com/predic8/membrane/core/interceptor/administration/api/admin-api-v1.yaml")),
            new OpenAPISpec()));

    private TestRouter router;
    private LimitedMemoryExchangeStore store;
    private ServiceProxy victim;

    @BeforeEach
    void setUp() {
        router = new TestRouter();
        store = new LimitedMemoryExchangeStore();
        router.setExchangeStore(store);
        router.init();

        victim = new ServiceProxy(new ServiceProxyKey("*", "GET", "/shop", 3999, null), "backend.example.com", 8080);
        victim.setName("victim");
        victim.init(router);
        router.getRuleManager().addProxy(victim, MANUAL);
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void ignoresRequestsOutsideOfTheApi() throws Exception {
        final var exc = Request.get("/admin/rest/proxies").buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        assertEquals(com.predic8.membrane.core.interceptor.Outcome.CONTINUE, api(false).handleRequest(exc));
        assertNull(exc.getResponse());
    }

    @Test
    void instancesReportsConfiguredName() throws Exception {
        final var json = json(get(API + "/instances"));
        assertEquals("node-a", json.get("self").asText());
        assertEquals("up", json.get("instances").get(0).get("status").asText());
        assertFalse(json.get("instances").get(0).get("readOnly").asBoolean());
    }

    @Test
    void unknownEndpointIsProblemJson() throws Exception {
        final var res = sendUnvalidated(Request.get(API + "/nope"), false).getResponse();
        assertEquals(404, res.getStatusCode());
        assertEquals("application/problem+json", res.getHeader().getContentType());
        assertEquals(404, json(res).get("status").asInt());
    }

    @Test
    void wrongMethodListsAllowedMethods() throws Exception {
        final var res = sendUnvalidated(Request.put(API + "/apis/node-a/victim%3A3999"), false).getResponse();
        assertEquals(405, res.getStatusCode());
        assertEquals("DELETE, GET", res.getHeader().getFirstValue(ALLOW));
    }

    @Test
    void plannedEndpointsAnswerNotImplemented() throws Exception {
        assertEquals(501, get(API + "/logs").getStatusCode());
    }

    @Test
    void responsesAreNotCached() throws Exception {
        assertEquals("no-store", get(API + "/instances").getHeader().getFirstValue(CACHE_CONTROL));
    }

    @Test
    void listsApisWithMatchAndTarget() throws Exception {
        final var apis = json(get(API + "/apis")).get("apis");
        assertEquals(1, apis.size());
        final var api = apis.get(0);
        assertEquals("node-a", api.get("instance").asText());
        assertEquals("victim:3999", api.get("apiId").asText());
        assertEquals("serviceProxy", api.get("type").asText());
        assertEquals(3999, api.get("match").get("port").asInt());
        assertEquals("GET", api.get("match").get("method").asText());
        assertEquals("/shop", api.get("match").get("path").asText());
        assertEquals("backend.example.com", api.get("target").get("host").asText());
        assertEquals(8080, api.get("target").get("port").asInt());
    }

    @Test
    void filtersApisByType() throws Exception {
        assertEquals(0, json(get(API + "/apis?type=api,soapProxy")).get("total").asInt());
        assertEquals(1, json(get(API + "/apis?type=serviceProxy")).get("total").asInt());
        assertEquals(400, get(API + "/apis?type=bogus").getStatusCode());
    }

    @Test
    void rejectsInvalidPaging() throws Exception {
        assertEquals(400, get(API + "/apis?limit=0").getStatusCode());
        assertEquals(400, get(API + "/apis?offset=x").getStatusCode());
    }

    @Test
    void apiOfOtherInstanceIsNotFound() throws Exception {
        assertEquals(404, get(API + "/apis/node-b/victim%3A3999").getStatusCode());
    }

    @Test
    void deletesApi() throws Exception {
        assertEquals(204, send(Request.delete(VICTIM), false).getStatusCode());
        assertNull(RuleUtil.findRuleByIdentifier(router, "victim:3999"));
    }

    @Test
    void readOnlyConsoleRejectsDelete() throws Exception {
        final var res = send(Request.delete(VICTIM), true);
        assertEquals(403, res.getStatusCode());
        assertNotNull(RuleUtil.findRuleByIdentifier(router, "victim:3999"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cross-site", "same-site"})
    void crossOriginWritesAreForbidden(String secFetchSite) throws Exception {
        final var res = send(Request.delete(VICTIM).header(SEC_FETCH_SITE, secFetchSite), false);
        assertEquals(403, res.getStatusCode());
        assertNotNull(RuleUtil.findRuleByIdentifier(router, "victim:3999"));
    }

    @Test
    void crossOriginReadsAreAllowed() throws Exception {
        assertEquals(200, send(Request.get(API + "/apis").header(SEC_FETCH_SITE, "cross-site"), false).getStatusCode());
    }

    @Test
    void flowOfApi() throws Exception {
        assertTrue(json(get(VICTIM + "/flow")).get("flow").isArray());
    }

    @Test
    void anonymousPrincipalOfReadOnlyConsoleMayStillControlNodes() throws Exception {
        final var me = json(send(Request.get(API + "/auth/me"), true));
        assertFalse(me.get("authenticated").asBoolean());
        assertTrue(me.get("readOnly").asBoolean());
        assertTrue(me.get("canControlNodes").asBoolean());
        assertEquals("[\"nodeOperator\",\"viewer\"]", me.get("roles").toString());
    }

    @Test
    void listsExchangesNewestFirst() throws Exception {
        final var first = snap("/a", 200);
        final var second = snap("/b", 404);

        final var page = json(get(API + "/exchanges"));

        assertEquals(2, page.get("total").asInt());
        assertEquals(second.getId(), page.get("exchanges").get(0).get("id").asLong());
        assertEquals(first.getId(), page.get("exchanges").get(1).get("id").asLong());
        assertEquals("node-a", page.get("exchanges").get(0).get("instance").asText());
        assertTrue(page.get("lastModified").has("node-a"));
    }

    @Test
    void filtersExchangesByStatusClass() throws Exception {
        snap("/a", 200);
        final var notFound = snap("/b", 404);

        final var page = json(get(API + "/exchanges?statusCode=4xx"));

        assertEquals(1, page.get("total").asInt());
        assertEquals(notFound.getId(), page.get("exchanges").get(0).get("id").asLong());
    }

    @Test
    void pagesExchanges() throws Exception {
        for (int i = 0; i < 5; i++)
            snap("/" + i, 200);

        final var page = json(get(API + "/exchanges?offset=4&limit=2"));

        assertEquals(5, page.get("total").asInt());
        assertEquals(1, page.get("exchanges").size());
    }

    @Test
    void waitForModificationGivesUpAfterTimeout() throws Exception {
        snap("/a", 200);
        final var lastModified = json(get(API + "/exchanges")).get("lastModified").toString();

        final var start = System.currentTimeMillis();
        final var res = get(API + "/exchanges?timeout=1&waitForModification=" + java.net.URLEncoder.encode(lastModified, java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(200, res.getStatusCode());
        assertTrue(System.currentTimeMillis() - start >= 900);
    }

    @Test
    void exchangeDetailAndHeaders() throws Exception {
        final var exc = snap("/a", 200);
        final var base = API + "/exchanges/node-a/" + exc.getId();

        assertEquals("/a", json(get(base)).get("path").asText());
        final var headers = json(get(base + "/response/headers"));
        assertTrue(headers.get("startLine").asText().startsWith("HTTP/1.1 200"));
    }

    @Test
    void unknownExchangeIsNotFound() throws Exception {
        assertEquals(404, get(API + "/exchanges/node-a/123456789").getStatusCode());
        assertEquals(404, get(API + "/exchanges/node-a/abc").getStatusCode());
    }

    @Test
    void bodyIsServedAsNonExecutableDownload() throws Exception {
        final var exc = Request.post("/foo").contentType("text/html").body("<script>alert(1)</script>").buildExchange();
        store.snap(exc, REQUEST);

        final var res = get(API + "/exchanges/node-a/" + exc.getId() + "/request/body");

        assertEquals(200, res.getStatusCode());
        assertEquals("attachment", res.getHeader().getFirstValue(CONTENT_DISPOSITION));
        assertEquals("nosniff", res.getHeader().getFirstValue(X_CONTENT_TYPE_OPTIONS));
        assertEquals("sandbox", res.getHeader().getFirstValue(CONTENT_SECURITY_POLICY));
    }

    @Test
    void formattedBodyPrettyPrintsJson() throws Exception {
        final var exc = Request.post("/foo").contentType("application/json").body("{\"a\":1}").buildExchange();
        store.snap(exc, REQUEST);

        final var body = json(get(API + "/exchanges/node-a/" + exc.getId() + "/request/body/formatted"));

        assertEquals("json", body.get("format").asText());
        assertTrue(body.get("text").asText().contains("\n"));
        assertFalse(body.get("truncated").asBoolean());
    }

    @Test
    void curlIsPlainText() throws Exception {
        final var exc = snap("/a", 200);
        final var res = get(API + "/exchanges/node-a/" + exc.getId() + "/curl");
        assertEquals(200, res.getStatusCode());
        assertTrue(res.getBodyAsStringDecoded().startsWith("curl "));
        assertEquals("nosniff", res.getHeader().getFirstValue(X_CONTENT_TYPE_OPTIONS));
    }

    @Test
    void storeReportsLimitedMemoryCapabilities() throws Exception {
        final var s = json(get(API + "/exchanges/store")).get("stores").get(0);
        assertEquals("limitedMemory", s.get("type").asText());
        assertTrue(s.get("capabilities").get("waitForModification").asBoolean());
    }

    @Test
    void readsExchangesFromFileStore(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        final var fileStore = new FileExchangeStore();
        fileStore.setDir(dir.toString());
        fileStore.init(router);
        router.setExchangeStore(fileStore);
        final var exc = Request.post("/orders").contentType("text/plain").body("hello").buildExchange();
        exc.setProxy(victim);
        fileStore.snap(exc, REQUEST);
        exc.setResponse(Response.statusCode(201).body("ok").build());
        fileStore.snap(exc, RESPONSE);

        final var page = json(get(API + "/exchanges"));
        assertEquals(1, page.get("total").asInt());
        final var id = page.get("exchanges").get(0).get("id").asLong();
        assertEquals("victim:3999", page.get("exchanges").get(0).get("apiId").asText());

        assertEquals("hello", get(API + "/exchanges/node-a/" + id + "/request/body").getBodyAsStringDecoded());
        assertEquals(501, get(API + "/exchanges?body=hello").getStatusCode());
        assertEquals("file", json(get(API + "/exchanges/store")).get("stores").get(0).get("type").asText());
    }

    @Test
    void facetsListValuesPresentInStore() throws Exception {
        snap("/a", 404);
        assertEquals("[404]", json(get(API + "/exchanges/facets")).get("statusCode").toString());
    }

    @Test
    void statisticsAndSystem() throws Exception {
        assertEquals("victim:3999", json(get(API + "/statistics/apis")).get("apis").get(0).get("apiId").asText());
        assertTrue(json(get(API + "/system")).get("instances").get(0).get("heap").get("used").asLong() > 0);
        assertTrue(json(get(API + "/streams")).get("streams").isArray());
    }

    @Test
    void readOnlyConsoleMayChangeNodeState() throws Exception {
        final var balancer = addBalancer();
        balancer.down(DEFAULT_NAME, "localhost", 8080);

        final var res = send(Request.post(node("localhost:8080") + "/up"), true);

        assertEquals(200, res.getStatusCode());
        assertEquals(UP, balancer.getNode(DEFAULT_NAME, "localhost", 8080).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"up", "down", "takeout", "reset"})
    void nodeActionsDoNotAddUnknownNodes(String action) throws Exception {
        final var balancer = addBalancer();

        final var res = send(Request.post(node("attacker.example.com:80") + "/" + action), false);

        assertEquals(404, res.getStatusCode());
        assertFalse(balancer.hasNode(DEFAULT_NAME, "attacker.example.com", 80));
    }

    @Test
    void readOnlyConsoleRejectsNodeDeletion() throws Exception {
        final var balancer = addBalancer();
        assertEquals(403, send(Request.delete(node("localhost:8080")), true).getStatusCode());
        assertTrue(balancer.hasNode(DEFAULT_NAME, "localhost", 8080));
    }

    @Test
    void createsNode() throws Exception {
        final var balancer = addBalancer();

        final var res = send(Request.post(API + "/balancers/node-a/Default/clusters/Default/nodes")
                .contentType("application/json").body("{\"host\":\"other\",\"port\":81}"), false);

        assertEquals(201, res.getStatusCode());
        assertTrue(balancer.hasNode(DEFAULT_NAME, "other", 81));
    }

    @Test
    void rejectsNodeWithInvalidPort() throws Exception {
        addBalancer();
        final var res = send(Request.post(API + "/balancers/node-a/Default/clusters/Default/nodes")
                .contentType("application/json").body("{\"host\":\"other\",\"port\":0}"), false);
        assertEquals(400, res.getStatusCode());
    }

    @Test
    void listsBalancers() throws Exception {
        addBalancer();
        final var b = json(get(API + "/balancers")).get("balancers").get(0);
        assertEquals(DEFAULT_NAME, b.get("name").asText());
        assertEquals("localhost", b.get("clusters").get(0).get("nodes").get(0).get("host").asText());
    }

    private static String node(String hostPort) {
        return API + "/balancers/node-a/Default/clusters/Default/nodes/" + hostPort;
    }

    private Balancer addBalancer() {
        final var lb = new LoadBalancingInterceptor();
        lb.setName(DEFAULT_NAME);
        final var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 3996, null), "localhost", 80);
        proxy.getFlow().add(lb);
        proxy.init(router);
        router.getRuleManager().addProxy(proxy, MANUAL);
        final var balancer = BalancerUtil.lookupBalancer(router, DEFAULT_NAME);
        balancer.up(DEFAULT_NAME, "localhost", 8080);
        return balancer;
    }

    private Exchange snap(String path, int status) throws Exception {
        final var exc = Request.get(path).buildExchange();
        exc.setProxy(victim);
        store.snap(exc, REQUEST);
        exc.setResponse(Response.statusCode(status).body("ok").build());
        store.snap(exc, RESPONSE);
        Thread.sleep(2); // distinct timestamps for sorting
        return exc;
    }

    private Response get(String uri) throws Exception {
        return send(Request.get(uri), false);
    }

    /**
     * Sends a request and checks that a JSON response conforms to the OpenAPI document of the admin API.
     */
    private Response send(Request.Builder builder, boolean readOnly) throws Exception {
        final var exc = sendUnvalidated(builder, readOnly);
        final var contentType = exc.getResponse().getHeader().getContentType();
        if (contentType != null && contentType.contains("json")) {
            final var errors = SPEC.validateResponse(Utils.getOpenapiValidatorRequest(exc), Utils.getOpenapiValidatorResponse(exc));
            assertEquals(0, errors.size(), () -> "%s %s does not conform to admin-api-v1.yaml: %s\n%s".formatted(
                    exc.getRequest().getMethod(), exc.getRequest().getUri(), errors, exc.getResponse().getBodyAsStringDecoded()));
        }
        return exc.getResponse();
    }

    /**
     * For requests the OpenAPI document does not describe, like unknown paths or methods.
     */
    private Exchange sendUnvalidated(Request.Builder builder, boolean readOnly) throws Exception {
        final var exc = builder.buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        api(readOnly).handleRequest(exc);
        return exc;
    }

    private AdminApiV1Interceptor api(boolean readOnly) {
        final var api = new AdminApiV1Interceptor();
        api.setInstanceName("node-a");
        api.setReadOnly(readOnly);
        api.init(router);
        return api;
    }

    private static JsonNode json(Response res) throws Exception {
        return om.readTree(res.getBodyAsStringDecoded());
    }
}
