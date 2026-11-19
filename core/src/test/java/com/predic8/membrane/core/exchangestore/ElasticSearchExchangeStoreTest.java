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
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.http.MimeType.*;
import static com.predic8.membrane.core.http.Response.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.concurrent.TimeUnit.*;
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
    private final AtomicInteger bulkRequests = new AtomicInteger();
    private final CountDownLatch firstBulkReceived = new CountDownLatch(1);

    /**
     * Makes the backend wait until the store has sent its first bulk request. Read by the backend's
     * request thread.
     */
    private volatile boolean holdBackendUntilFirstBulk;

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
        es.setUpdateIntervalMs(10);
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
        sp.getFlow().add(createHoldInterceptor());
        StaticInterceptor si = new StaticInterceptor();
        si.setSrc(RESPONSE_BODY);
        sp.getFlow().add(si);
        ReturnInterceptor ri = new ReturnInterceptor();
        ri.setStatus(200);
        sp.getFlow().add(ri);
        back.add(sp);
        back.start();
    }

    private Interceptor createHoldInterceptor() {
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (!holdBackendUntilFirstBulk)
                    return CONTINUE;
                try {
                    // A timeout lets the test fail on its bulk request count instead of hanging.
                    //noinspection ResultOfMethodCallIgnored
                    firstBulkReceived.await(10, SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return CONTINUE;
            }
        };
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
            final var lines = new ArrayList<JsonNode>();
            for (String line : exc.getRequest().getBodyAsStringDecoded().split("\n")) {
                try {
                    lines.add(om.readTree(line));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
            // Add the whole bulk request at once, so a reader never sees half of an action/document pair.
            synchronized (insertedObjects) {
                insertedObjects.addAll(lines);
            }
            bulkRequests.incrementAndGet();
            firstBulkReceived.countDown();
            exc.setResponse(ok("{}").build());
            return RETURN;
        }
        exc.setResponse(ok("""
                {}""").build());
        return RETURN;
    }

    private List<JsonNode> getInsertedObjects() {
        synchronized (insertedObjects) {
            return List.copyOf(insertedObjects);
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

    /**
     * The store writes the exchange when the request arrives and again when it completes. Holding the
     * backend until the first write has arrived puts these writes into separate bulk requests.
     */
    @Test
    void exchangeWrittenInSeveralBulkRequests() throws Exception {
        holdBackendUntilFirstBulk = true;
        runTest(false);
        assertTrue(bulkRequests.get() >= 2, "Expected several bulk requests, got " + bulkRequests.get());
    }

    private void runTest(boolean addLoggingInterceptors) throws Exception {
        initializeGateway(addLoggingInterceptors);

        try (var client = new HttpClient()) {
            client.call(Request.post("http://localhost:3064").header(AUTHORIZATION, "Demo").body(REQUEST_BODY).buildExchange());
        }

        final var documents = waitForCompletedExchange();
        assertEquals(1, documents.size());
        final var exchange = documents.sequencedValues().getFirst();

        assertArrayEquals(REQUEST_BODY.getBytes(UTF_8), Base64.decode(exchange.get("request").get("body").textValue()));
        assertArrayEquals(RESPONSE_BODY.getBytes(UTF_8), Base64.decode(exchange.get("response").get("body").textValue()));

        assertEquals("Demo", exchange.get("request").get("header").get(AUTHORIZATION).textValue());
        assertEquals(TEXT_PLAIN, exchange.get("response").get("header").get(CONTENT_TYPE).textValue());

        assertEquals("COMPLETED", exchange.get("status").textValue());
        assertTrue(exchange.get("time").longValue() > 1740000000000L);
        assertTrue(exchange.get("timeReqReceived").longValue() > 1740000000000L);
        assertTrue(exchange.get("timeReqSent").longValue() > 1740000000000L);
        assertTrue(exchange.get("timeResReceived").longValue() > 1740000000000L);
        assertTrue(exchange.get("timeResSent").longValue() > 1740000000000L);
    }

    /**
     * Pairs each bulk action line with the document after it and keeps the last document per
     * {@code _id}, as Elasticsearch does when the same id is indexed again.
     */
    private static SequencedMap<String, JsonNode> documentsById(List<JsonNode> bulkLines) {
        assertEquals(0, bulkLines.size() % 2, "Bulk lines must come in action/document pairs");
        final var documents = new LinkedHashMap<String, JsonNode>();
        for (var i = 0; i < bulkLines.size(); i += 2) {
            final var action = bulkLines.get(i).get("index");
            assertNotNull(action, "Expected an index action, got " + bulkLines.get(i));
            documents.put(action.get("_id").textValue(), bulkLines.get(i + 1));
        }
        return documents;
    }

    /**
     * Waits until the store has written the exchange with status {@code COMPLETED}. The server sends
     * the response to the client before it marks the exchange completed, so the client returning does
     * not mean that the final write has been queued.
     */
    private SequencedMap<String, JsonNode> waitForCompletedExchange() throws InterruptedException {
        final var deadline = System.nanoTime() + SECONDS.toNanos(10);
        while (true) {
            final var documents = documentsById(getInsertedObjects());
            if (documents.values().stream().anyMatch(doc -> "COMPLETED".equals(doc.path("status").asText())))
                return documents;
            if (System.nanoTime() - deadline > 0)
                fail("No COMPLETED exchange written within 10 s, got " + documents);
            //noinspection BusyWait
            Thread.sleep(10);
        }
    }

}