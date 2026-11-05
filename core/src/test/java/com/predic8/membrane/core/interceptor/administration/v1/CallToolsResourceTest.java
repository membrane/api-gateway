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
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import static com.predic8.membrane.core.interceptor.Interceptor.Flow.REQUEST;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.RESPONSE;
import static com.predic8.membrane.core.proxies.RuleManager.RuleDefinitionSource.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class CallToolsResourceTest {

    private static final int BACKEND_PORT = 3091;
    private static final int GATEWAY_PORT = 3092;
    private static final String API = "/admin/api/v1";
    private static final ObjectMapper om = new ObjectMapper();

    private static HttpServer backend;
    private static final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    private TestRouter router;
    private LimitedMemoryExchangeStore store;
    private ServiceProxy gateway;

    @BeforeAll
    static void startBackend() throws Exception {
        backend = HttpServer.create(new InetSocketAddress("localhost", BACKEND_PORT), 0);
        backend.createContext("/", ex -> {
            final var body = new String(ex.getRequestBody().readAllBytes(), UTF_8);
            received.add("%s %s %s %s".formatted(ex.getRequestMethod(), ex.getRequestURI(), ex.getRequestHeaders().getFirst("X-Tenant"), body));
            final var answer = "{\"echo\":\"%s\"}".formatted(ex.getRequestURI().getPath()).getBytes(UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(201, answer.length);
            ex.getResponseBody().write(answer);
            ex.close();
        });
        backend.start();
    }

    @AfterAll
    static void stopBackend() {
        backend.stop(0);
    }

    @BeforeEach
    void setUp() throws Exception {
        received.clear();
        router = new TestRouter();
        store = new LimitedMemoryExchangeStore();
        router.setExchangeStore(store);
        gateway = new ServiceProxy(new ServiceProxyKey("*", "*", ".*", GATEWAY_PORT, null), "localhost", BACKEND_PORT);
        gateway.setName("gateway");
        router.add(gateway);
        router.start();
    }

    @AfterEach
    void tearDown() {
        router.stop();
    }

    @Test
    void replayToBackendWithChanges() throws Exception {
        final var exc = recorded("/orders?x=1", "{\"a\":1}");

        final var res = send(Request.post(API + "/exchanges/node-a/" + exc.getId() + "/replay").contentType("application/json")
                .body("{\"target\":\"backend\",\"path\":\"/changed\",\"headers\":[{\"name\":\"X-Tenant\",\"value\":\"t2\"}],\"body\":\"new\"}"), false);

        assertEquals(200, res.getStatusCode(), res.getBodyAsStringDecoded());
        final var result = om.readTree(res.getBodyAsStringDecoded());
        assertEquals(201, result.get("statusCode").asInt());
        assertEquals("http://localhost:%d/changed".formatted(BACKEND_PORT), result.get("url").asText());
        assertEquals("json", result.get("body").get("format").asText());
        assertEquals("POST /changed t2 new", received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void replayThroughGatewayKeepsRecordedRequest() throws Exception {
        final var exc = recorded("/orders", "{\"a\":1}");

        final var res = send(Request.post(API + "/exchanges/node-a/" + exc.getId() + "/replay"), false);

        assertEquals(200, res.getStatusCode(), res.getBodyAsStringDecoded());
        assertEquals("http://localhost:%d/orders".formatted(GATEWAY_PORT), om.readTree(res.getBodyAsStringDecoded()).get("url").asText());
        assertEquals("POST /orders acme {\"a\":1}", received.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void replayRejectsInjectedHeaders() throws Exception {
        final var exc = recorded("/orders", "x");
        final var res = send(Request.post(API + "/exchanges/node-a/" + exc.getId() + "/replay").contentType("application/json")
                .body("{\"headers\":[{\"name\":\"X-A\",\"value\":\"a\\r\\nX-B: b\"}]}"), false);
        assertEquals(400, res.getStatusCode());
        assertNull(received.poll());
    }

    @Test
    void replayRejectsAbsoluteUrlAsPath() throws Exception {
        final var exc = recorded("/orders", "x");
        final var res = send(Request.post(API + "/exchanges/node-a/" + exc.getId() + "/replay").contentType("application/json")
                .body("{\"path\":\"http://attacker.example.com/\"}"), false);
        assertEquals(400, res.getStatusCode());
    }

    @Test
    void replayIsForbiddenOnReadOnlyConsole() throws Exception {
        final var exc = recorded("/orders", "x");
        assertEquals(403, send(Request.post(API + "/exchanges/node-a/" + exc.getId() + "/replay"), true).getStatusCode());
        assertNull(received.poll());
    }

    @Test
    void exportsHar() throws Exception {
        final var a = recorded("/a?q=1", "{\"a\":1}");
        final var b = recorded("/b", "x");

        final var res = send(Request.get(API + "/exchanges/export?id=node-a/" + a.getId() + "&id=node-a/" + b.getId()), false);

        assertEquals(200, res.getStatusCode(), res.getBodyAsStringDecoded());
        assertTrue(res.getHeader().getFirstValue("Content-Disposition").startsWith("attachment"));
        final var log = om.readTree(res.getBodyAsStringDecoded()).get("log");
        assertEquals("1.2", log.get("version").asText());
        final var first = log.get("entries").get(0);
        assertEquals("POST", first.get("request").get("method").asText());
        assertEquals("q", first.get("request").get("queryString").get(0).get("name").asText());
        assertEquals("{\"a\":1}", first.get("request").get("postData").get("text").asText());
        assertEquals(201, first.get("response").get("status").asInt());
        assertEquals(2, log.get("entries").size());
    }

    @Test
    void exportNeedsIds() throws Exception {
        assertEquals(400, send(Request.get(API + "/exchanges/export"), false).getStatusCode());
        assertEquals(400, send(Request.get(API + "/exchanges/export?id=garbage"), false).getStatusCode());
    }

    @Test
    void comparesRequests() throws Exception {
        final var a = recorded("/a", "{\"a\":1,\"b\":2}");
        final var b = recorded("/a", "{\"a\":1,\"b\":3}");

        final var res = send(Request.get(API + "/exchanges/diff?left=node-a/%d/request&right=node-a/%d/request".formatted(a.getId(), b.getId())), false);

        assertEquals(200, res.getStatusCode(), res.getBodyAsStringDecoded());
        final var diff = om.readTree(res.getBodyAsStringDecoded());
        assertFalse(diff.get("body").get("identical").asBoolean());
        assertTrue(diff.get("body").get("unifiedDiff").asText().contains("-  \"b\" : 2"), diff.toString());
        assertEquals("json", diff.get("body").get("format").asText());
    }

    @Test
    void comparesHeaders() throws Exception {
        final var a = recorded("/a", "x");
        final var b = recorded("/a", "x", "other");

        final var diff = om.readTree(send(Request.get(API + "/exchanges/diff?left=node-a/%d/request&right=node-a/%d/request".formatted(a.getId(), b.getId())), false)
                .getBodyAsStringDecoded());

        assertEquals("X-Tenant", diff.get("headers").get(0).get("name").asText());
        assertEquals("acme", diff.get("headers").get(0).get("left").asText());
        assertEquals("other", diff.get("headers").get(0).get("right").asText());
        assertTrue(diff.get("body").get("identical").asBoolean());
    }

    @Test
    void diffNeedsValidReferences() throws Exception {
        assertEquals(400, send(Request.get(API + "/exchanges/diff?left=node-a/1/body&right=node-a/1/request"), false).getStatusCode());
        assertEquals(404, send(Request.get(API + "/exchanges/diff?left=node-a/123456/request&right=node-a/123456/request"), false).getStatusCode());
    }

    private Exchange recorded(String path, String body) throws Exception {
        return recorded(path, body, "acme");
    }

    private Exchange recorded(String path, String body, String tenant) throws Exception {
        final var exc = Request.post(path).header("X-Tenant", tenant).contentType("application/json").body(body).buildExchange();
        exc.setProxy(gateway);
        exc.setDestinations(new ArrayList<>(List.of("http://localhost:%d%s".formatted(BACKEND_PORT, path))));
        store.snap(exc, REQUEST);
        exc.setResponse(Response.statusCode(201).contentType("application/json").body("{}").build());
        store.snap(exc, RESPONSE);
        Thread.sleep(2); // distinct start times, so the HAR entries have a stable order
        return exc;
    }

    private Response send(Request.Builder builder, boolean readOnly) throws Exception {
        final var exc = builder.buildExchange();
        exc.getDestinations().add(exc.getRequest().getUri());
        final var api = new AdminApiV1Interceptor();
        api.setInstanceName("node-a");
        api.setReadOnly(readOnly);
        api.init(router);
        api.handleRequest(exc);
        return exc.getResponse();
    }
}
