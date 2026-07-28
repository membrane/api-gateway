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

package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class LokiExchangeStoreTest {

    private static final ObjectMapper om = new ObjectMapper();

    private static final String REQUEST_BODY = """
            {"where":"there"}""";
    private static final String RESPONSE_BODY = """
            {"demo": true}""";

    private static final int LOKI_MOCK_PORT = 3076;
    private static final int GATEWAY_PORT = 3074;
    private static final int BACKEND_PORT = 3075;

    private TestRouter gateway;
    private TestRouter back;
    private TestRouter lokiMock;
    private LokiExchangeStore store;

    private final List<JsonNode> pushes = new ArrayList<>();
    private final List<String> orgIds = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        initializeLokiMock();
        initializeBackend();
    }

    @AfterEach
    void done() {
        try {
            back.stop();
        } finally {
            try {
                gateway.stop();
            } finally {
                lokiMock.stop();
            }
        }
    }

    private void initializeLokiMock() throws IOException {
        lokiMock = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(LOKI_MOCK_PORT), null, 0);
        sp.getFlow().add(new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().isPOSTRequest() && exc.getRequest().getUri().equals("/loki/api/v1/push")) {
                    try {
                        synchronized (pushes) {
                            pushes.add(om.readTree(exc.getRequest().getBodyAsStringDecoded()));
                            orgIds.add(exc.getRequest().getHeader().getFirstValue("X-Scope-OrgID"));
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    exc.setResponse(ResponseBuilder.newInstance().status(204, "No Content").build());
                    return RETURN;
                }
                exc.setResponse(ok("{}").build());
                return RETURN;
            }
        });
        lokiMock.add(sp);
        lokiMock.start();
    }

    private void initializeBackend() throws IOException {
        back = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(BACKEND_PORT), null, 0);
        StaticInterceptor si = new StaticInterceptor();
        si.setSrc(RESPONSE_BODY);
        sp.getFlow().add(si);
        ReturnInterceptor ri = new ReturnInterceptor();
        ri.setStatus(200);
        sp.getFlow().add(ri);
        back.add(sp);
        back.start();
    }

    private void initializeGateway(String orgId) throws IOException {
        gateway = new TestRouter();
        store = new LokiExchangeStore();
        store.setUrl("http://localhost:" + LOKI_MOCK_PORT);
        store.setJob("test-job");
        store.setOrgId(orgId);
        store.setUpdateIntervalMs(100);
        gateway.setExchangeStore(store);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(GATEWAY_PORT), "localhost", BACKEND_PORT);
        sp.setName("demo-api");
        gateway.add(sp);
        gateway.start();
    }

    private void callGateway() throws Exception {
        try (var client = new HttpClient()) {
            client.call(Request.post("http://localhost:" + GATEWAY_PORT)
                    .header(AUTHORIZATION, "Demo").body(REQUEST_BODY).buildExchange());
        }
    }

    @Test
    void pushesOneCompletedExchangePerStream() throws Exception {
        initializeGateway(null);
        callGateway();
        waitForExchangeStoreToFlush();

        List<JsonNode> pushes = getPushesAndClear();
        assertEquals(1, pushes.size(), "expected exactly one push, not one per exchange state");

        JsonNode streams = pushes.getFirst().get("streams");
        assertEquals(1, streams.size());

        JsonNode stream = streams.get(0);
        assertEquals("test-job", stream.get("stream").get("job").textValue());
        assertEquals("demo-api", stream.get("stream").get("api").textValue());

        JsonNode values = stream.get("values");
        assertEquals(1, values.size(), "a completed exchange must produce exactly one log line");

        JsonNode entry = values.get(0);
        assertTrue(entry.get(0).textValue().matches("\\d{19}"), "timestamp must be epoch nanoseconds as a string");

        JsonNode line = om.readTree(entry.get(1).textValue());
        assertEquals("COMPLETED", line.get("status").textValue());
        assertEquals("demo-api", line.get("rule").get("name").textValue());
        assertEquals("Demo", line.get("request").get("header").get(AUTHORIZATION).textValue());
        assertArrayEquals(REQUEST_BODY.getBytes(UTF_8), Base64.decode(line.get("request").get("body").textValue()));
        assertArrayEquals(RESPONSE_BODY.getBytes(UTF_8), Base64.decode(line.get("response").get("body").textValue()));
        assertEquals(200, line.get("response").get("statusCode").intValue());
    }

    @Test
    void sendsOrgIdHeaderWhenConfigured() throws Exception {
        initializeGateway("team-a");
        callGateway();
        waitForExchangeStoreToFlush();

        getPushesAndClear();
        assertEquals(List.of("team-a"), orgIds);
    }

    @Test
    void survivesUnreachableLoki() throws Exception {
        initializeGateway(null);
        store.setUrl("http://localhost:1"); // nothing listening
        callGateway();
        waitForExchangeStoreToFlush();

        // The update thread must still be alive, so a later exchange reaches a Loki that is back up.
        store.setUrl("http://localhost:" + LOKI_MOCK_PORT);
        callGateway();
        waitForExchangeStoreToFlush();

        assertEquals(1, getPushesAndClear().size());
    }

    private List<JsonNode> getPushesAndClear() {
        synchronized (pushes) {
            List<JsonNode> copy = new ArrayList<>(pushes);
            pushes.clear();
            return copy;
        }
    }

    private void waitForExchangeStoreToFlush() {
        while (true) {
            synchronized (store.shortTermMemoryForBatching) {
                if (store.shortTermMemoryForBatching.isEmpty() && !store.updateThreadWorking)
                    return;
            }
            try {
                //noinspection BusyWait
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
