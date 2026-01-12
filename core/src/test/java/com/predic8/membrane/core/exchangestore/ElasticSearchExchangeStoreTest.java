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

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.flow.*;
import com.predic8.membrane.core.interceptor.log.*;
import com.predic8.membrane.core.interceptor.templating.*;
import com.predic8.membrane.core.proxies.*;
import com.predic8.membrane.core.router.*;
import com.predic8.membrane.core.transport.http.*;
import org.jetbrains.annotations.*;
import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static org.junit.jupiter.api.Assertions.*;

class ElasticSearchExchangeStoreTest {

    private static final ObjectMapper om = new ObjectMapper();

    private static final String RESPONSE_BODY = """
            {"demo": true}""";
    private static final String REQUEST_BODY = """
            {"where":"there"}""";

    private TestRouter gateway;
    private TestRouter back;
    private TestRouter elasticMock;
    private ElasticSearchExchangeStore es;

    private final List<JsonNode> insertedObjects = new ArrayList<>();

    @BeforeEach
    public void start() throws IOException {
        initializeElasticSearchMock();
        initializeBackend();
    }

    @AfterEach
    public void done() {
        try {
            back.stop();
        } finally {
            try {
                gateway.stop();
            } finally {
                elasticMock.stop();
            }
        }
    }

    private void initializeElasticSearchMock() throws IOException {
        elasticMock = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3066), null, 0);
        sp.getFlow().add(createBodyLoggingInterceptor());
        sp.getFlow().add(createElasticSearchMockInterceptor());
        elasticMock.add(sp);
        elasticMock.start();
    }

    private @NotNull LogInterceptor createBodyLoggingInterceptor() {
        LogInterceptor log = new LogInterceptor();
        log.setBody(true);
        return log;
    }

    private void initializeGateway(boolean addLoggingInterceptors) throws IOException {
        gateway = new TestRouter();
        es = new ElasticSearchExchangeStore();
        es.setLocation("http://localhost:3066");
        es.setUpdateIntervalMs(100);
        gateway.setExchangeStore(es);
        gateway.add(new ServiceProxy(new ServiceProxyKey(3064), "localhost", 3065));
        gateway.start();
        var global = new GlobalInterceptor();
        if (addLoggingInterceptors) {
            global.getFlow().add(createBodyLoggingInterceptor());
        }
        global.getFlow().add(new ExchangeStoreInterceptor());
    }

    private void initializeBackend() throws IOException {
        back = new TestRouter();
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
        StaticInterceptor si = new StaticInterceptor();
        si.setSrc(RESPONSE_BODY);
        sp.getFlow().add(si);
        ReturnInterceptor ri = new ReturnInterceptor();
        ri.setStatus(200);
        sp.getFlow().add(ri);
        back.add(sp);
        back.start();
    }

    private Interceptor createElasticSearchMockInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().isGETRequest() && exc.getRequest().getUri().equals("/membrane/_mapping")) {
                    exc.setResponse(ok("""
                            {"membrane": {"mappings": {"something":true}}}""").build());
                    return RETURN;
                }
                if (exc.getRequest().isGETRequest()) {
                    exc.setResponse(ok("""
                            {"acknowledged": true}""").build());
                    return RETURN;
                }
                return getOutcome(exc, om);
            }
        };
    }

    private @NotNull Outcome getOutcome(Exchange exc, ObjectMapper om) {
        if (exc.getRequest().isPOSTRequest() && exc.getRequest().getUri().equals("/_bulk")) {
            for (String line : exc.getRequest().getBodyAsStringDecoded().split("\n")) {
                try {
                    JsonNode obj = om.readTree(line);
                    synchronized (insertedObjects) {
                        insertedObjects.add(obj);
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            exc.setResponse(ok("{}").build());
            return RETURN;
        }
        exc.setResponse(ok("""
                {}""").build());
        return RETURN;
    }

    public List<JsonNode> getInsertedObjectsAndClearList() {
        synchronized (insertedObjects) {
            List<JsonNode> insertedObjects1 = new ArrayList<>(insertedObjects);
            insertedObjects.clear();
            return insertedObjects1;
        }
    }

    @Test
    void withoutLogging() throws Exception {
        runTest(false);
    }

    @Test
    void withLogging() throws Exception {
        runTest(true);
    }

    private void runTest(boolean addLoggingInterceptors) throws Exception {
        initializeGateway(addLoggingInterceptors);

        try (var client = new HttpClient()) {
            client.call(Request.post("http://localhost:3064").header(AUTHORIZATION, "Demo").body(REQUEST_BODY).buildExchange());
        }

        waitForExchangeStoreToFlush();

        List<JsonNode> insertedObjects = getInsertedObjectsAndClearList();
        assertEquals(2, insertedObjects.size());
        assertNotNull(insertedObjects.get(0).get("index")); // assert first inserted object is the 'index'

        assertArrayEquals(REQUEST_BODY.getBytes(UTF_8), Base64.decode(insertedObjects.get(1).get("request").get("body").textValue()));
        assertArrayEquals(RESPONSE_BODY.getBytes(UTF_8), Base64.decode(insertedObjects.get(1).get("response").get("body").textValue()));

        assertEquals("Demo", insertedObjects.get(1).get("request").get("header").get(AUTHORIZATION).textValue());
        assertEquals(TEXT_PLAIN, insertedObjects.get(1).get("response").get("header").get(CONTENT_TYPE).textValue());

        assertEquals("COMPLETED", insertedObjects.get(1).get("status").textValue());
        assertTrue(insertedObjects.get(1).get("time").longValue() > 1740000000000L);
        assertTrue(insertedObjects.get(1).get("timeReqReceived").longValue() > 1740000000000L);
        assertTrue(insertedObjects.get(1).get("timeReqSent").longValue() > 1740000000000L);
        assertTrue(insertedObjects.get(1).get("timeResReceived").longValue() > 1740000000000L);
        assertTrue(insertedObjects.get(1).get("timeResSent").longValue() > 1740000000000L);
    }

    private void waitForExchangeStoreToFlush() {
        while (true) {
            synchronized (es.shortTermMemoryForBatching) {
                int size = es.shortTermMemoryForBatching.size();
                if (size == 0 && !es.updateThreadWorking) return;
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