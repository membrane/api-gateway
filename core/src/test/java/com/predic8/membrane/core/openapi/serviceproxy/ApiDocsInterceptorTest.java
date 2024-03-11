package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Optional;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiDocsInterceptorTest {

    Router router;

    Exchange exc = new Exchange(null);

    ApiDocsInterceptor interceptor;
    APIProxy rule;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        OpenAPISpec spec = new OpenAPISpec();
        spec.location = "src/test/resources/openapi/specs/fruitshop-api-v2-openapi-3.yml";
        exc.setRequest(new Request.Builder().get("/foo").build());

        rule = createProxy(router, spec);
        router.setExchangeStore(new ForgetfulExchangeStore());

        router.setTransport(new HttpTransport());
        router.add(rule);
        router.init();


        interceptor = new ApiDocsInterceptor();
        interceptor.init(router);

    }

    @AfterEach
    public void tearDown() {
        router.stop();
    }

    @Test
    public void initTest() throws Exception {
        assertEquals(CONTINUE, interceptor.handleRequest(exc));
    }

    @Test
    public void getOpenApiInterceptorTest() {
        assertEquals("OpenAPI", interceptor.getOpenAPIInterceptor(rule).get().getDisplayName());
        assertEquals(Optional.empty(), interceptor.getOpenAPIInterceptor(new APIProxy()));
    }



    @Test
    public void initializeRuleApiSpecsTest() {
        assertEquals(interceptor.getOpenAPIInterceptor(rule).get().getApiProxy().apiRecords, interceptor.initializeRuleApiSpecs());
    }

    @Test
    public void initializeEmptyRuleApiSpecsTest() throws Exception {
        ApiDocsInterceptor adi = new ApiDocsInterceptor();
        adi.init(new Router());
        assertEquals(new HashMap<>(), adi.initializeRuleApiSpecs());
    }

    // See OA PubTest?

    // Accept: html => HTML?

    // => Json? Einträge drin

    // Download YAML ueber Link

    // Kommt Swagger UI
}