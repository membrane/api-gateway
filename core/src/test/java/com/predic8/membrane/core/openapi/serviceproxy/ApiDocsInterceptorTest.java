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
import java.util.Map;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ApiDocsInterceptorTest {

    Router router;

    OpenAPISpec specInfoServers;

    Exchange exc = new Exchange(null);

    ApiDocsInterceptor apiDocsInterceptor;
    APIProxy rule;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        specInfoServers = new OpenAPISpec();
        specInfoServers.location = "src/test/resources/openapi/specs/fruitshop-api-v2-openapi-3.yml";
        exc.setRequest(new Request.Builder().get("/foo").build());

        rule = createProxy(router, specInfoServers);
        router.setExchangeStore(new ForgetfulExchangeStore());

        router.setTransport(new HttpTransport());
        router.add(rule);
        router.init();


        apiDocsInterceptor = new ApiDocsInterceptor();
        apiDocsInterceptor.init(router);

    }

    @AfterEach
    public void tearDown() {
        router.stop();
    }

    @Test
    public void initTest() throws Exception {
        assertEquals(CONTINUE, apiDocsInterceptor.handleRequest(exc));
    }

    @Test
    public void getOpenApiInterceptorTest() {
        assertEquals("OpenAPI", apiDocsInterceptor.getOpenAPIInterceptor(rule).getDisplayName());
        assertNull(apiDocsInterceptor.getOpenAPIInterceptor(new APIProxy()));
    }



    @Test
    public void initializeRuleApiSpecsTest() {
        apiDocsInterceptor.initializeRuleApiSpecs();
        Map<String, Map<String, OpenAPIRecord>> map = Map.of(" *:2000", apiDocsInterceptor.getOpenAPIInterceptor(rule).getApiProxy().apiRecords);
        assertEquals(map , apiDocsInterceptor.ruleApiSpecs);
    }

    @Test
    public void initializeEmptyRuleApiSpecsTest() throws Exception {
        ApiDocsInterceptor adi = new ApiDocsInterceptor();
        adi.init(new Router());
        adi.initializeRuleApiSpecs();
        assertEquals(new HashMap<>(), adi.ruleApiSpecs);
    }
}