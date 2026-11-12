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
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.administration.*;
import com.predic8.membrane.core.openapi.*;
import com.predic8.membrane.core.openapi.serviceproxy.*;
import com.predic8.membrane.core.openapi.util.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.util.ConfigurationException;
import com.predic8.membrane.core.util.URIFactory;
import com.predic8.membrane.test.TestAppender;
import org.junit.jupiter.api.*;

import java.net.*;
import java.net.http.*;
import java.util.*;

import static com.predic8.membrane.test.TestAppender.captureLog;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Two Membrane instances with their own exchange stores; node-a lists node-b as a peer.
 */
class AggregatorTest {

    private static final int PEER_PORT = 3071;
    private static final int CONSOLE_PORT = 3072;
    private static final int PEER_API_PORT = 3073;
    private static final int UNUSED_PORT = 3079;
    private static final String CONSOLE = "http://localhost:%d/admin/api/v1".formatted(CONSOLE_PORT);
    private static final ObjectMapper om = new ObjectMapper();
    private static final HttpClient http = HttpClient.newHttpClient();

    private static final OpenAPIValidator SPEC = new OpenAPIValidator(new URIFactory(), new OpenAPIRecord(
            OpenAPITestUtils.parseOpenAPI(AggregatorTest.class.getResourceAsStream(
                    "/com/predic8/membrane/core/interceptor/administration/api/admin-api-v1.yaml")),
            new OpenAPISpec()));

    private static TestRouter peer;
    private static TestRouter console;
    private static LimitedMemoryExchangeStore peerStore;
    private static LimitedMemoryExchangeStore consoleStore;
    private static long a1, b1, a2;

    @BeforeAll
    static void setUp() throws Exception {
        peerStore = new LimitedMemoryExchangeStore();
        final var peerConsole = new AdminConsoleInterceptor();
        peerConsole.setInstanceName("node-b");
        peerConsole.setApiKey("secret");
        peer = router(peerStore, peerConsole, PEER_PORT);
        final var victim = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", PEER_API_PORT, null), "localhost", 80);
        victim.setName("victim");
        peer.add(victim);
        peer.start();

        consoleStore = new LimitedMemoryExchangeStore();
        final var aggregating = new AdminConsoleInterceptor();
        aggregating.setInstanceName("node-a");
        aggregating.setPeers(List.of(peer("http://localhost:" + PEER_PORT, "secret"), peer("http://localhost:" + UNUSED_PORT, null)));
        console = router(consoleStore, aggregating, CONSOLE_PORT);
        console.start();

        a1 = snap(consoleStore, "/a1");
        b1 = snap(peerStore, "/b1");
        a2 = snap(consoleStore, "/a2");
    }

    @AfterAll
    static void tearDown() {
        console.stop();
        peer.stop();
    }

    @Test
    void instancesListsPeersAndTheirState() throws Exception {
        final var json = getJson("/instances");
        assertEquals("node-a", json.get("self").asText());
        final var byName = new HashMap<String, JsonNode>();
        json.get("instances").forEach(i -> byName.put(i.get("name").asText(), i));
        assertEquals("up", byName.get("node-a").get("status").asText());
        assertTrue(byName.get("node-a").get("self").asBoolean());
        assertEquals("up", byName.get("node-b").get("status").asText());
        assertFalse(byName.get("node-b").get("self").asBoolean());
        assertEquals("http://localhost:" + PEER_PORT, byName.get("node-b").get("url").asText());
        assertEquals("down", byName.get("http://localhost:" + UNUSED_PORT).get("status").asText());
    }

    @Test
    void exchangesAreMergedNewestFirst() throws Exception {
        final var page = getJson("/exchanges");
        final var exchanges = page.get("exchanges");
        final var ids = new ArrayList<String>();
        exchanges.forEach(e -> ids.add(e.get("instance").asText() + ":" + e.get("path").asText()));
        assertEquals(List.of("node-a:/a2", "node-b:/b1", "node-a:/a1"), ids.subList(0, 3));
        assertEquals(3, page.get("total").asInt());
        assertTrue(page.get("lastModified").has("node-a"));
        assertTrue(page.get("lastModified").has("node-b"));
        assertEquals("down", status(page, "http://localhost:" + UNUSED_PORT));
        assertEquals("ok", status(page, "node-b"));
    }

    @Test
    void pagesAcrossInstances() throws Exception {
        final var page = getJson("/exchanges?offset=1&limit=1");
        assertEquals(1, page.get("exchanges").size());
        assertEquals(b1, page.get("exchanges").get(0).get("id").asLong());
        assertEquals("node-b", page.get("exchanges").get(0).get("instance").asText());
    }

    @Test
    void limitsMergedWindow() throws Exception {
        assertEquals(400, get("/exchanges?offset=990&limit=20").statusCode());
    }

    @Test
    void filtersByInstance() throws Exception {
        final var page = getJson("/exchanges?instance=node-b");
        assertEquals(1, page.get("total").asInt());
        assertEquals("node-b", page.get("exchanges").get(0).get("instance").asText());
    }

    @Test
    void exchangeOfPeerIsForwarded() throws Exception {
        assertEquals("/b1", getJson("/exchanges/node-b/" + b1).get("path").asText());
        assertEquals("/a2", getJson("/exchanges/node-a/" + a2).get("path").asText());
    }

    @Test
    void bodyOfPeerKeepsItsSafetyHeaders() throws Exception {
        final var res = get("/exchanges/node-b/" + b1 + "/response/body");
        assertEquals(200, res.statusCode());
        assertEquals("nosniff", res.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("sandbox", res.headers().firstValue("Content-Security-Policy").orElse(null));
        assertEquals("response of /b1", res.body());
    }

    @Test
    void unknownInstanceIsNotFound() throws Exception {
        assertEquals(404, get("/exchanges/node-x/1").statusCode());
    }

    @Test
    void apisOfAllInstances() throws Exception {
        final var instances = new HashSet<String>();
        getJson("/apis").get("apis").forEach(a -> instances.add(a.get("instance").asText()));
        assertEquals(Set.of("node-a", "node-b"), instances);
    }

    @Test
    void systemOfAllInstances() throws Exception {
        assertEquals(2, getJson("/system").get("instances").size());
    }

    @Test
    void facetsAreUnited() throws Exception {
        assertEquals("[\"node-a\",\"node-b\"]", getJson("/exchanges/facets").get("instance").toString());
    }

    @Test
    void peerRequiresApiKey() throws Exception {
        final var res = http.send(HttpRequest.newBuilder(URI.create("http://localhost:%d/admin/api/v1/instances".formatted(PEER_PORT))).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, res.statusCode());
    }

    @Test
    void writesAreForwardedToThePeer() throws Exception {
        final var victimId = URLEncoder.encode("victim:" + PEER_API_PORT, UTF_8);
        final var res = http.send(HttpRequest.newBuilder(URI.create(CONSOLE + "/apis/node-b/" + victimId + "/flow")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
    }

    @Test
    void exportsHarAcrossInstances() throws Exception {
        final var res = get("/exchanges/export?id=node-a/%d&id=node-b/%d".formatted(a1, b1));
        assertEquals(200, res.statusCode(), res.body());
        final var entries = om.readTree(res.body()).get("log").get("entries");
        assertEquals(2, entries.size());
        assertEquals("node-a", entries.get(0).get("_instance").asText());
        assertEquals("node-b", entries.get(1).get("_instance").asText());
        assertEquals("response of /b1", entries.get(1).get("response").get("content").get("text").asText());
    }

    @Test
    void comparesMessagesOfDifferentInstances() throws Exception {
        final var diff = getJson("/exchanges/diff?left=node-a/%d/response&right=node-b/%d/response".formatted(a1, b1));
        assertTrue(diff.get("body").get("unifiedDiff").asText().contains("-response of /a1\n+response of /b1\n"), diff.toString());
    }

    @Test
    void invalidPeerUrlIsAConfigurationError() {
        final var api = new AdminApiV1Interceptor();
        api.setPeers(List.of(peer("node-b:9000", null)));
        assertThrows(ConfigurationException.class, () -> api.init(new TestRouter()));
    }

    /**
     * A key sent over plain HTTP gets a warning, unless the connection is protected otherwise, e.g. by a TLS sidecar.
     */
    @Test
    void apiKeyOverPlainHttpIsWarnedAboutUnlessDisabled() throws Exception {
        assertTrue(initWithPeer(peer("http://node-b.example.com:9000", "key")).contains("sent over plain HTTP"));
        assertFalse(initWithPeer(peer("https://node-b.example.com:9000", "key")).contains("sent over plain HTTP"));

        final var protectedPeer = peer("http://node-b.example.com:9000", "key");
        protectedPeer.setWarnOnPlainHttp(false);
        assertFalse(initWithPeer(protectedPeer).contains("sent over plain HTTP"));
    }

    private static TestAppender initWithPeer(AdminConsolePeer peer) throws Exception {
        final var api = new AdminApiV1Interceptor();
        api.setPeers(List.of(peer));
        return captureLog(() -> {
            api.init(new TestRouter());
            return null;
        });
    }

    @Test
    void hugeOffsetIsRejectedWithoutOverflow() throws Exception {
        assertEquals(400, get("/exchanges?offset=2147483647&limit=1").statusCode());
    }

    @Test
    void sortIsValidatedAlsoWhenOnlyPeersAreSelected() throws Exception {
        assertEquals(400, get("/exchanges?instance=node-b&sort=bogus").statusCode());
        assertEquals(400, get("/exchanges?instance=node-b&order=up").statusCode());
    }

    @Test
    void longPollOnPeersOnlyIsNotEndedByThisInstance() throws Exception {
        final var lastModified = getJson("/exchanges?instance=node-b").get("lastModified").toString();
        final var start = System.currentTimeMillis();
        final var res = get("/exchanges?instance=node-b&timeout=1&waitForModification=" + URLEncoder.encode(lastModified, UTF_8));
        assertEquals(200, res.statusCode());
        assertTrue(System.currentTimeMillis() - start >= 900);
    }

    @Test
    void peerUrlNeedsHostAndNoFragment() {
        for (final var url : List.of("http:///node-b", "http://node-b:9000/#x", "ftp://node-b")) {
            final var api = new AdminApiV1Interceptor();
            api.setPeers(List.of(peer(url, null)));
            assertThrows(ConfigurationException.class, () -> api.init(new TestRouter()), url);
        }
    }

    @Test
    void duplicateInstanceNamesAreAConfigurationError() {
        final var a = peer("http://localhost:1", null);
        a.setName("node-x");
        final var b = peer("http://localhost:2", null);
        b.setName("node-x");
        final var api = new AdminApiV1Interceptor();
        api.setPeers(List.of(a, b));
        assertThrows(ConfigurationException.class, () -> api.init(new TestRouter()));

        final var self = peer("http://localhost:1", null);
        self.setName("node-a");
        final var api2 = new AdminApiV1Interceptor();
        api2.setInstanceName("node-a");
        api2.setPeers(List.of(self));
        assertThrows(ConfigurationException.class, () -> api2.init(new TestRouter()));
    }

    @Test
    void comparatorSortsPathsCaseSensitivelyAndNamesCaseInsensitively() {
        final var upper = om.createObjectNode().put("path", "/B").put("name", "B");
        final var lower = om.createObjectNode().put("path", "/a").put("name", "a");
        final var list = new ArrayList<JsonNode>(List.of(lower, upper));
        list.sort(Aggregator.comparator("path", false));
        assertEquals(List.of(upper, lower), list);
        list.sort(Aggregator.comparator("name", false));
        assertEquals(List.of(lower, upper), list);
    }

    @Test
    void comparatorSortsTimesWithDifferentPrecision() {
        final var early = om.createObjectNode().put("time", "2026-11-05T10:00:00Z");
        final var late = om.createObjectNode().put("time", "2026-11-05T10:00:00.5Z");
        final var list = new ArrayList<JsonNode>(List.of(late, early));
        list.sort(Aggregator.comparator("time", false));
        assertEquals(List.of(early, late), list);
    }

    private static String status(JsonNode page, String instance) {
        for (final var i : page.get("instances"))
            if (instance.equals(i.get("instance").asText()))
                return i.get("status").asText();
        return null;
    }

    private static JsonNode getJson(String pathAndQuery) throws Exception {
        final var res = get(pathAndQuery);
        assertEquals(200, res.statusCode(), res.body());
        final var errors = SPEC.validateResponse(
                com.predic8.membrane.core.openapi.model.Request.get().path("/admin/api/v1" + pathAndQuery.replaceFirst("\\?.*", "")),
                com.predic8.membrane.core.openapi.model.Response.statusCode(res.statusCode())
                        .mediaType(res.headers().firstValue("Content-Type").orElseThrow())
                        .body(res.body()));
        assertEquals(0, errors.size(), () -> "%s does not conform to admin-api-v1.yaml: %s\n%s".formatted(pathAndQuery, errors, res.body()));
        return om.readTree(res.body());
    }

    private static HttpResponse<String> get(String pathAndQuery) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(CONSOLE + pathAndQuery)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static TestRouter router(ExchangeStore store, AdminConsoleInterceptor adminConsole, int port) throws Exception {
        final var router = new TestRouter();
        router.setExchangeStore(store);
        final var proxy = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", port, null), "localhost", 80);
        proxy.setName("admin");
        proxy.getFlow().add(adminConsole);
        router.add(proxy);
        return router;
    }

    private static AdminConsolePeer peer(String url, String apiKey) {
        final var p = new AdminConsolePeer();
        p.setUrl(url);
        p.setApiKey(apiKey);
        return p;
    }

    private static long snap(LimitedMemoryExchangeStore store, String path) throws Exception {
        final Exchange exc = Request.get(path).buildExchange();
        exc.setProxy(new ServiceProxy(new ServiceProxyKey("*", "*", ".*", 1, null), "backend.example.com", 80));
        store.snap(exc, REQUEST);
        exc.setResponse(Response.ok().contentType("text/plain").body("response of " + path).build());
        store.snap(exc, RESPONSE);
        Thread.sleep(5);
        return exc.getId();
    }
}
