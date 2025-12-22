package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.log.LogInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpServerHandlerTest {

    static Router router;

    HttpClient client;

    @BeforeEach
    void setUp() {
        client = new HttpClient();
    }

    @BeforeAll
    static void start() throws Exception {
        router = new Router();
        router.setTransport(new HttpTransport());

        router.getRuleManager().addProxyAndOpenPortIfNew(new APIProxy() {{
            setPort(2000);
            getFlow().add(new LogInterceptor());
            getFlow().add(new ReturnInterceptor());
        }});

        router.init();
        router.start();
    }

    @AfterAll
    static void shutdown() {
        router.stop();
    }

    @Test
    void postEmptyBody_shouldReturn200() {
        when()
            .post("http://localhost:2000")
        .then()
            .statusCode(200);
    }

    @Test
    void init() throws Exception {
        Exchange exc = Request.post("http://localhost:200").buildExchange();
        client.initializeRequest(exc,"http://localhost:2000");
        client.call(exc);
        assertEquals(200, exc.getResponse().getStatusCode());
    }

}