package com.predic8.membrane.core.transport.http;

import com.predic8.membrane.core.HttpRouter;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.config.Path;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.interceptor.flow.ResponseInterceptor;
import com.predic8.membrane.core.interceptor.flow.ReturnInterceptor;
import com.predic8.membrane.core.interceptor.groovy.GroovyInterceptor;
import com.predic8.membrane.core.interceptor.templating.StaticInterceptor;
import com.predic8.membrane.core.openapi.serviceproxy.APIProxy;
import com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.predic8.membrane.core.openapi.serviceproxy.OpenAPISpec.YesNoOpenAPIOption.YES;

public class ConnectionKeepAliveTest {

    Router gateway;
    Router backend;

    @BeforeEach
    void setUp() throws Exception {
        gateway = getGateway();
        backend = getBackend();
    }

    @AfterEach
    public void tearDown() throws Exception {
        gateway.shutdown();
        backend.shutdown();
    }

    @Test
    void foo() throws Exception {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet req1 = new HttpGet("http://localhost:2000/health");

            try (CloseableHttpResponse res = client.execute(req1)) {
                EntityUtils.consume(res.getEntity());
            }

            try (CloseableHttpResponse res = client.execute(req1)) {
                EntityUtils.consume(res.getEntity());
            }
        }
    }

    @Test
    void bar() throws Exception {
        HttpClient client = new HttpClient();
        Exchange exc = Request.get("http://localhost:2000/health").buildExchange();
        Exchange exc2 = Request.get("http://localhost:2000/health").buildExchange();
        client.call(exc);
        client.call(exc);
    }

    private @NotNull Router getGateway() throws Exception {
        HttpRouter router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getOpenApiProxy());
        router.setBaseLocation("src/test/resources/");
        router.init();
        return router;
    }

    private static @NotNull Router getBackend() throws Exception {
        HttpRouter router = new HttpRouter();
        router.getRuleManager().addProxyAndOpenPortIfNew(getHealthProxy());
        router.getRuleManager().addProxyAndOpenPortIfNew(getFooProxy());
        router.init();
        return router;
    }

    private @NotNull APIProxy getOpenApiProxy() {
        APIProxy openApiProxy = new APIProxy();
        openApiProxy.setPort(2000);
        openApiProxy.setSpecs(List.of(new OpenAPISpec() {{
            setLocation("health.yaml");
            setValidateRequests(YES);
            setValidationDetails(YES);
            setValidateResponses(YES);
            setValidateSecurity(YES);
        }}));
        return openApiProxy;
    }

    private static @NotNull APIProxy getFooProxy() {
        APIProxy fooProxy = new APIProxy();
        fooProxy.setPort(3000);
        fooProxy.setPath(new Path(false, "/foo"));
        fooProxy.setFlow(List.of(
                new ResponseInterceptor() {{
                    setFlow(List.of(
                            new StaticInterceptor() {{
                                setContentType("application/json");
                                setSrc("{\"status\": \"ok\"}");
                            }})
                    );
                }}, new ReturnInterceptor()
        ));
        return fooProxy;
    }

    private static @NotNull APIProxy getHealthProxy() {
        APIProxy healthProxy = new APIProxy();
        healthProxy.setPort(3000);
        healthProxy.setPath(new Path(false, "/health"));
        healthProxy.setFlow(List.of(new GroovyInterceptor() {{
            setSrc("""
                    exchange.setResponse(Response.ok("No Matching Rule matched.").build())
                    exchange.getResponse().getHeader().add("Transfer-Encoding", "chunked")
                    exchange.getResponse().getHeader().removeFields("Content-length")
                    exchange.getResponse().setStatusCode(400)
                    sleep(100)
                    return RETURN
                    """);
        }}));
        return healthProxy;
    }

}
