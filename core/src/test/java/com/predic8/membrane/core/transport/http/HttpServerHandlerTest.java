package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.when;

class HttpServerHandlerTest {

    static Router router;

    @BeforeAll
    static void start() throws Exception {
        router = new Router();
        router.setTransport(new HttpTransport());

        router.getRuleManager().addProxyAndOpenPortIfNew(new APIProxy() {{
            setPort(2000);
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

}