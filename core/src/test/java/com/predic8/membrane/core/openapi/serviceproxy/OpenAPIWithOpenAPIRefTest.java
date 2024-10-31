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

    Exchange exc = new Exchange(null);

    @BeforeEach
    public void setUp() throws Exception {
        Router router = new Router();
        router.setUriFactory(new URIFactory());

        spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/openAPI-references.yml";

        exc.setRequest(new Request.Builder().method("POST").body("{\"name\": \"Alice\"}").build());

        interceptor = new OpenAPIInterceptor(createProxy(router, spec), router);
        interceptor.init(router);
    }

    @Test
    void simple() throws Exception {
        exc.getRequest().setUri("/refs");
        assertEquals(Outcome.CONTINUE, interceptor.handleRequest(exc));
        assertEquals(206, exc.getResponse().getStatusCode());
    }

}
