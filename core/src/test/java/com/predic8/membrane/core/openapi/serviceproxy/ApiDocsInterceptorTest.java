package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.exchangestore.ForgetfulExchangeStore;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.HttpTransport;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiDocsInterceptorTest {

    Router router;

    OpenAPISpec specInfoServers;

    Exchange exc = new Exchange(null);

    ApiDocsInterceptor apiDocsInterceptor;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        specInfoServers = new OpenAPISpec();
        specInfoServers.location = "src/test/resources/openapi/specs/fruitshop-api-v2-openapi-3.yml";
        exc.setRequest(new Request.Builder().get("/foo").build());

        APIProxy rule  = createProxy(router, specInfoServers);
        router.setExchangeStore(new ForgetfulExchangeStore());

        router.setTransport(new HttpTransport());
        router.add(rule);
        router.init();


        apiDocsInterceptor = new ApiDocsInterceptor();
        apiDocsInterceptor.init(router);

    }

    @Test
    public void initTest() throws Exception {
        assertEquals(CONTINUE, apiDocsInterceptor.handleRequest(exc));
    }




}