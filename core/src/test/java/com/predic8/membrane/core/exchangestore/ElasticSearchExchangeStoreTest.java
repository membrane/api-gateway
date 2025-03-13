package com.predic8.membrane.core.exchangestore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.predic8.membrane.core.http.Response.ok;
import static com.predic8.membrane.core.interceptor.Outcome.RETURN;
import static org.junit.jupiter.api.Assertions.*;

class ElasticSearchExchangeStoreTest {

    private static HttpRouter router;
    private static HttpRouter back;
    private static HttpRouter elasticMock;
    private static ElasticSearchExchangeStore es;

    private static final List<Map<?,?>> insertedObjects = new ArrayList<>();

    @BeforeAll
    public static void start() throws IOException {
        initializeElasticSearchMock();
        initializeBackend();
        initializeGateway();
    }

    private static void initializeElasticSearchMock() throws IOException {
        elasticMock = new HttpRouter();
        elasticMock.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3066), null, 0);
        LogInterceptor log = new LogInterceptor();
        log.setBody(true);
        sp.getInterceptors().add(log);
        sp.getInterceptors().add(createElasticSearchMockInterceptor());
        elasticMock.add(sp);
        elasticMock.start();
    }

    private static void initializeGateway() throws IOException {
        router = new HttpRouter();
        router.setHotDeploy(false);
        es = new ElasticSearchExchangeStore();
        es.setLocation("http://localhost:3066");
        es.setUpdateIntervalMs(100);
        router.setExchangeStore(es);
        router.getTransport().getInterceptors().add(4, new ExchangeStoreInterceptor());
        router.add(new ServiceProxy(new ServiceProxyKey(3064), "localhost", 3065));
        router.start();
    }

    private static void initializeBackend() throws IOException {
        back = new HttpRouter();
        back.setHotDeploy(false);
        ServiceProxy sp = new ServiceProxy(new ServiceProxyKey(3065), null, 0);
        StaticInterceptor si = new StaticInterceptor();
        si.setTextTemplate("""
                {"demo": true}""");
        sp.getInterceptors().add(si);
        ReturnInterceptor ri = new ReturnInterceptor();
        ri.setStatusCode(200);
        sp.getInterceptors().add(ri);
        back.add(sp);
        back.start();
    }

    @AfterAll
    public static void done() {
        router.stop();
        back.stop();
        elasticMock.stop();
    }

    private static Interceptor createElasticSearchMockInterceptor() {
        ObjectMapper om = new ObjectMapper();
        return new AbstractInterceptor() {
            @Override
            public Outcome handleRequest(Exchange exc) {
                if (exc.getRequest().isPUTRequest() && exc.getRequest().getUri().equals("/membrane/")) {
                    exc.setResponse(ok("""
                        {"acknowledged": true}""").build());
                    return RETURN;
                }
                if (exc.getRequest().isGETRequest() && exc.getRequest().getUri().equals("/membrane/_mapping")) {
                    exc.setResponse(ok("""
                        {"membrane": {"mappings": {"something":true}}}""").build());
                    return RETURN;
                }
                if (exc.getRequest().isPOSTRequest() && exc.getRequest().getUri().equals("/_bulk")) {
                    for (String line : exc.getRequest().getBodyAsStringDecoded().split("\n")) {
                        try {
                            Map<?,?> obj = om.readValue(line, Map.class);
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
        };
    }

    public static List<Map<?, ?>> getInsertedObjectsAndClearList() {
        synchronized (insertedObjects) {
            List<Map<?, ?>> insertedObjects1 = new ArrayList(insertedObjects);
            insertedObjects.clear();
            return insertedObjects1;
        }
    }

    @Test
    public void testIt() throws Exception {
        try (var client = new HttpClient()) {
            client.call(Request.post("http://localhost:3064").body("""
                    {"where":"there"}""").buildExchange());
        }

        waitForExchangeStoreToFlush();

        List<Map<?, ?>> insertedObjects = getInsertedObjectsAndClearList();
        assertEquals(2, insertedObjects.size());
        assertNotNull(insertedObjects.get(0).get("index")); // assert first inserted object is the 'index'



        // {"request":{"header":{"X-Forwarded-Host":"localhost:3064","X-Forwarded-Proto":"http","X-Forwarded-For":"127.0.0.1","Host":"localhost:3065","Content-Length":"17"},
        //             "body":"eyJ3aGVyZSI6InRoZXJlIn0=","method":"POST","uri":"/"},
        //  "response":{"header":{"Content-Length":"14","Content-Type":"text/plain"},"body":"","statusCode":200,"statusMessage":"Ok"},
        //              "originalRequestUri":"/","time":1741773085707,"errorMessage":"","status":"COMPLETED","timeReqSent":1741773085721,"timeReqReceived":1741773085707,"timeResSent":1741773085724,
        //              "timeResReceived":1741773085722,"destinations":["http://localhost:3065/"],"remoteAddr":"127.0.0.1","remoteAddrIp":"127.0.0.1",
        //              "rule":{"name":"com.predic8.membrane.core.exchange.snapshots.FakeKey@3f44624a","port":3064},"server":"localhost","id":34271583,"issuer":"interlaken"}
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