package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class OpenAPIWithOpenAPIRefTest {

    OpenAPIInterceptor interceptor;
    OpenAPISpec spec;
    Exchange exc;

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/openAPI-references.yml";

        interceptor = new OpenAPIInterceptor(createProxy(router, spec), router);
        interceptor.init(router);
    }

    @Test
    void testGetReferences() throws Exception {
        exc = new Exchange(null);
        exc.setRequest(new Request.Builder()
                .get("/references/123")
                .header("Accept", "application/json")
                .build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @Test
    void testPostBodyRef() throws Exception {
        exc = new Exchange(null);
        exc.setRequest(new Request.Builder()
                .post("/body-ref")
                .header("Content-Type", "application/json")
                .body("{\"contract\": {\"details\": \"foo\"}}")
                .build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @Test
    void testPostCombinedRef() throws Exception {
        exc = new Exchange(null);
        exc.setRequest(new Request.Builder()
                .post("/combined-ref")
                .header("Content-Type", "application/json")
                .body("{\"contract\": {\"details\": \"foo\"}, \"additionalInfo\": \"bar\"}")
                .build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

    @Test
    void testPostAllRefs() throws Exception {
        exc = new Exchange(null);
        exc.setRequest(new Request.Builder()
                .post("/all-refs?limit=10&rid=123")
                .header("Content-Type", "application/json")
                .body("{\"contract\": {\"details\": \"foo\"}}")
                .build());
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(200, exc.getResponse().getStatusCode());
    }

}
