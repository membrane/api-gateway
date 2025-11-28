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

package com.predic8.membrane.core.exchangestore;

import tools.jackson.databind.core.JsonProcessingException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.ExchangeStoreInterceptor;
import com.predic8.membrane.core.interceptor.Interceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.proxies.ServiceProxy;
import com.predic8.membrane.core.proxies.ServiceProxyKey;
import com.predic8.membrane.core.transport.http.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.jose4j.base64url.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Header.CONTENT_TYPE;
import static com.predic8.membrane.core.http.MimeType.TEXT_PLAIN;
import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class ElasticSearchExchangeStoreTest {

    private static HttpRouter gateway;
    private static HttpRouter back;
    private static HttpRouter elasticMock;
    private static ElasticSearchExchangeStore es;

    private static final List<JsonNode> insertedObjects = new ArrayList<>();
    private static String RESPONSE_BODY = """
            {"demo": true}""";
    private String REQUEST_BODY = """
            {"where":"there"}""";

    @BeforeAll
    public static void start() throws IOException {
        initializeElasticSearchMock();
        initializeBackend();
    }

    private static void initializeElasticSearchMock() throws IOException {
        elasticMock = new HttpRouter();
        elasticMock.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3066), null, 0);
        sp.getFlow().add(createBodyLoggingInterceptor());
        sp.getFlow().add(createElasticSearchMockInterceptor());
        elasticMock.add(sp);
        elasticMock.start();
    }

    private static @NotNull LogInterceptor createBodyLoggingInterceptor() {
        LogInterceptor log = new LogInterceptor();
        log.setBody(true);
        return log;
    }

    private static void initializeGateway(boolean addLoggingInterceptors) throws IOException {
        gateway = new HttpRouter();
        gateway.setHotDeploy(false);
        es = new ElasticSearchExchangeStore();
        es.setLocation("http://localhost:3066");
        es.setUpdateIntervalMs(100);
        gateway.setExchangeStore(es);
        int index = 4;
        if (addLoggingInterceptors)
            gateway.getTransport().getFlow().add(index++, createBodyLoggingInterceptor());
        gateway.getTransport().getFlow().add(index++, new ExchangeStoreInterceptor());
        if (addLoggingInterceptors)
            gateway.getTransport().getFlow().add(index++, createBodyLoggingInterceptor());
        gateway.add(new ServiceProxy(new ServiceProxyKey(3064), "localhost", 3065));
        gateway.start();
    }

    private static void initializeBackend() throws IOException {
        back = new HttpRouter();
        back.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
        StaticInterceptor si = new StaticInterceptor();
        si.setSrc(RESPONSE_BODY);
        sp.getFlow().add(si);
        ReturnInterceptor ri = new ReturnInterceptor();
        ri.setStatusCode(200);
        sp.getFlow().add(ri);
        back.add(sp);
        back.start();
    }

    @AfterAll
    public static void done() {
        back.stop();
        elasticMock.stop();
    }

    private static Interceptor createElasticSearchMockInterceptor() {
        ObjectMapper om = new ObjectMapper();
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().isGETRequest()) {
                    exc.setResponse(ok("""
                        {"acknowledged": true}""").build());
                    return RETURN;
                }
                if (exc.getRequest().getMethod().equals("GET") && exc.getRequest().getUri().equals("/membrane/_mapping")) {
                    exc.setResponse(ok("""
                        {"membrane": {"mappings": {"something":true}}}""").build());
                    return RETURN;
                }
                return getOutcome(exc, om);
            }
        };
    }

    private static @NotNull Outcome getOutcome(Exchange exc, ObjectMapper om) {
        if (exc.getRequest().getMethod().equals("POST") && exc.getRequest().getUri().equals("/_bulk")) {
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

    public static List<JsonNode> getInsertedObjectsAndClearList() {
        synchronized (insertedObjects) {
            List<JsonNode> insertedObjects1 = new ArrayList(insertedObjects);
            insertedObjects.clear();
            return insertedObjects1;
        }
    }

    @Test
    public void testWithoutLogging() throws Exception {
        runTest(false);
    }

    @Test
    public void testWithLogging() throws Exception {
        runTest(true);
    }

    private void runTest(boolean addLoggingInterceptors) throws Exception {
        initializeGateway(addLoggingInterceptors);

        try (var client = new HttpClient()) {
            client.call(Request.post("http://localhost:3064")
                    .header(AUTHORIZATION, "Demo")
                    .body(REQUEST_BODY).buildExchange());
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

    @AfterEach
    public void done2() {
        gateway.stop();
    }

    private void waitForExchangeStoreToFlush() {
        while (true) {
            synchronized (es.shortTermMemoryForBatching) {
                int size = es.shortTermMemoryForBatching.size();
                if (size == 0 && !es.updateThreadWorking)
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