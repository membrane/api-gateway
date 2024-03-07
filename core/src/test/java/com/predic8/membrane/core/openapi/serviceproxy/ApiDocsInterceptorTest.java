package com.predic8.membrane.core.openapi.serviceproxy;

import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.util.URIFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.predic8.membrane.core.openapi.util.TestUtils.createProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiDocsInterceptorTest {

    Router router;

    OpenAPISpec specInfoServers;
    OpenAPISpec specInfo3Servers;

    Exchange exc = new Exchange(null);
    OpenAPIInterceptor interceptor1Server;
    OpenAPIInterceptor interceptor3Server;

    @BeforeEach
    public void setUp() throws Exception {
        router = new Router();
        router.setUriFactory(new URIFactory());

        specInfoServers = new OpenAPISpec();
        specInfoServers.location = "src/test/resources/openapi/specs/info-servers.yml";

        specInfo3Servers = new OpenAPISpec();
        specInfo3Servers.location = "src/test/resources/openapi/specs/info-3-servers.yml";


        exc.setRequest(new Request.Builder().method("GET").build());

        interceptor1Server = new OpenAPIInterceptor(createProxy(router, specInfoServers));
        interceptor1Server.init(router);
        interceptor3Server = new OpenAPIInterceptor(createProxy(router, specInfo3Servers));
        interceptor3Server.init(router);
    }

    @Test
    public void getMatchingBasePathOneServer() {
        exc.getRequest().setUri("/base/v2/foo");
        assertEquals("/base/v2", interceptor1Server.getMatchingBasePath(exc));
    }


}